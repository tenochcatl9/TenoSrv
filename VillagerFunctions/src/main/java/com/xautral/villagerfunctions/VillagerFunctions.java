package com.xautral.villagerfunctions;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.MobGoals;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class VillagerFunctions extends JavaPlugin implements Listener, TabCompleter {

    private final Map<UUID, Villager> editingVillagers = new HashMap<>();
    private final Map<UUID, Integer> editingPages = new HashMap<>();
    private final Map<UUID, List<MerchantRecipe>> editingRecipes = new HashMap<>();
    private final Map<UUID, Integer> editingTradeIndex = new HashMap<>();

    private boolean villagerAttackEnabled = false;
    private final Set<Villager.Profession> enabledProfessions = new HashSet<>();
    private final Map<UUID, Goal<Villager>> removedAvoidGoals = new HashMap<>();
    private final Set<UUID> switchingViews = new HashSet<>();
    private final Map<UUID, Long> fletcherCooldowns = new HashMap<>();
    private final Map<UUID, Long> fangsCooldowns = new HashMap<>();
    private final Map<UUID, Long> supportCooldowns = new HashMap<>();
    private final Map<UUID, Long> attackCooldowns = new HashMap<>();
    private final Map<UUID, Long> potionCooldowns = new HashMap<>();
    private NamespacedKey buttonKey;
    private NamespacedKey tradeIndexKey;

    @Override
    public void onEnable() {
        buttonKey = new NamespacedKey(this, "action");
        tradeIndexKey = new NamespacedKey(this, "trade_index");

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("villagerattack").setExecutor(this);
        getCommand("villagerattack").setTabCompleter(this);

        startVillagerAttackTask();
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (label.equalsIgnoreCase("villagerattack")) {
            if (!player.hasPermission("xautral.op")) return true;
            if (args.length < 1) {
                player.sendMessage(color("&eUso: /villagerattack <on|off|weaponsmith|alchemist|fletcher|toolsmith|librarian>"));
                return true;
            }
            
            switch (args[0].toLowerCase()) {
                case "on":
                    villagerAttackEnabled = true;
                    player.sendMessage(color("&aAtaque de aldeanos ACTIVADO."));
                    break;
                case "off":
                    villagerAttackEnabled = false;
                    player.sendMessage(color("&cAtaque de aldeanos DESACTIVADO."));
                    break;
                case "weaponsmith":
                    toggleProfessionAttack(player, Villager.Profession.WEAPONSMITH);
                    break;
                case "alchemist":
                    toggleProfessionAttack(player, Villager.Profession.CLERIC);
                    break;
                case "fletcher":
                    toggleProfessionAttack(player, Villager.Profession.FLETCHER);
                    break;
                case "toolsmith":
                    toggleProfessionAttack(player, Villager.Profession.TOOLSMITH);
                    break;
                case "librarian":
                    toggleProfessionAttack(player, Villager.Profession.LIBRARIAN);
                    break;
                default:
                    player.sendMessage(color("&eOpción no válida. Usa: on, off, weaponsmith, alchemist, fletcher, toolsmith, librarian"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("on", "off", "weaponsmith", "alchemist", "fletcher", "toolsmith", "librarian").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void toggleProfessionAttack(Player player, Villager.Profession profession) {
        if (enabledProfessions.contains(profession)) {
            enabledProfessions.remove(profession);
            player.sendMessage(color("&c" + profession.toString() + " attack DESACTIVADO."));
        } else {
            enabledProfessions.add(profession);
            player.sendMessage(color("&a" + profession.toString() + " attack ACTIVADO."));
        }
    }

    private void startVillagerAttackTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (Villager v : world.getEntitiesByClass(Villager.class)) {
                        if (v.isDead() || !v.isValid()) {
                            removedAvoidGoals.remove(v.getUniqueId());
                            continue;
                        }
                        if (villagerAttackEnabled) {
                            updateVillagerCombat(v);
                        } else {
                            restoreFleeGoals(v);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void updateVillagerCombat(Villager v) {
        removeFleeGoals(v);

        LivingEntity target = findTarget(v);
        if (target == null) {
            v.getPathfinder().stopPathfinding();
            return;
        }

        // Force target setting and awareness
        v.setTarget(target);
        v.setAware(true);
        
        double dist = v.getLocation().distance(target.getLocation());
        Villager.Profession prof = v.getProfession();

        // Check if this profession is enabled for attacks
        if (!enabledProfessions.isEmpty() && !enabledProfessions.contains(prof)) {
            return;
        }

        // Keep a safe distance: 5 blocks normally, 10 for ranged professions
        boolean ranged = prof == Villager.Profession.FLETCHER || prof == Villager.Profession.LIBRARIAN;
        double keepDist = ranged ? 10.0 : 5.0;
        double backOffDist = keepDist * 0.5;
        double speed = 0.6;

        if (dist > keepDist) {
            v.getPathfinder().moveTo(target.getLocation(), speed);
        } else if (dist < backOffDist) {
            Vector away = v.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
            Location awayLoc = v.getLocation().clone().add(away.multiply(keepDist));
            v.getPathfinder().moveTo(awayLoc, speed);
        } else {
            v.getPathfinder().stopPathfinding();
        }

        if (prof == Villager.Profession.WEAPONSMITH) {
            if (dist < 4 && !isOnCooldown(attackCooldowns, v.getUniqueId(), 1000)) {
                performWeaponSmithAttack(v, target);
                startCooldown(attackCooldowns, v.getUniqueId());
            }
        } else if (prof == Villager.Profession.CLERIC) {
            if (dist < 15 && dist > 4 && !isOnCooldown(potionCooldowns, v.getUniqueId(), 1000)) {
                throwPotion(v, target);
                startCooldown(potionCooldowns, v.getUniqueId());
            }
            if (!isOnCooldown(supportCooldowns, v.getUniqueId(), 1000)) {
                healNearbyVillagers(v);
                startCooldown(supportCooldowns, v.getUniqueId());
            }
        } else if (prof == Villager.Profession.LIBRARIAN) {
            if (dist < 15 && !isOnCooldown(fangsCooldowns, v.getUniqueId(), 1000)) {
                summonFangsLine(v, target);
                startCooldown(fangsCooldowns, v.getUniqueId());
            }
            if (!isOnCooldown(supportCooldowns, v.getUniqueId(), 1000)) {
                if (tryReloadAtWorkstation(v, Material.LECTERN, Material.BOOKSHELF)) {
                    enchantNearby(v);
                    startCooldown(supportCooldowns, v.getUniqueId());
                }
            }
        } else if (prof == Villager.Profession.FLETCHER) {
            if (dist < 20 && !isOnCooldown(fletcherCooldowns, v.getUniqueId(), 1000)) {
                shootArrow(v, target);
                startCooldown(fletcherCooldowns, v.getUniqueId());
            }
        } else if (prof == Villager.Profession.TOOLSMITH) {
            if (dist < 4 && !isOnCooldown(attackCooldowns, v.getUniqueId(), 1000)) {
                performToolSmithAttack(v, target);
                startCooldown(attackCooldowns, v.getUniqueId());
            }
            if (!isOnCooldown(supportCooldowns, v.getUniqueId(), 3000)) {
                if (tryReloadAtWorkstation(v, Material.SMITHING_TABLE, Material.GRINDSTONE, Material.CRAFTING_TABLE)) {
                    distributeArmor(v);
                    startCooldown(supportCooldowns, v.getUniqueId());
                }
            }
            if (Math.random() < 0.12) useShield(v);
        } else {
            // Nitwit, unemployed and the rest hit normally
            if (dist < 3 && !isOnCooldown(attackCooldowns, v.getUniqueId(), 1000)) {
                performMeleeAttack(v, target, null, 4.0);
                startCooldown(attackCooldowns, v.getUniqueId());
            }
        }
    }

    private boolean isOnCooldown(Map<UUID, Long> cooldowns, UUID id, long millis) {
        Long last = cooldowns.get(id);
        return last != null && System.currentTimeMillis() - last < millis;
    }

    private void startCooldown(Map<UUID, Long> cooldowns, UUID id) {
        cooldowns.put(id, System.currentTimeMillis());
    }

    private boolean tryReloadAtWorkstation(Villager v, Material... workBlocks) {
        Location ws = findNearestWorkstation(v, 8, workBlocks);
        if (ws == null) return false;
        if (ws.distance(v.getLocation()) > 3) {
            v.getPathfinder().moveTo(ws, 0.8);
            return false;
        }
        return true;
    }

    private Location findNearestWorkstation(Villager v, int radius, Material... workBlocks) {
        Set<Material> set = new HashSet<>(Arrays.asList(workBlocks));
        Location base = v.getLocation();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = base.clone().add(dx, dy, dz).getBlock();
                    if (set.contains(b.getType())) return b.getLocation();
                }
            }
        }
        return null;
    }

    private void removeFleeGoals(Villager v) {
        if (removedAvoidGoals.containsKey(v.getUniqueId())) return;
        MobGoals mobGoals = Bukkit.getMobGoals();
        try {
            for (Goal<Villager> goal : mobGoals.getAllGoals(v)) {
                NamespacedKey key = goal.getKey().getNamespacedKey();
                String name = key.getKey().toLowerCase(Locale.ROOT);
                if (name.contains("avoid") || name.contains("flee")) {
                    mobGoals.removeGoal(v, goal);
                    removedAvoidGoals.put(v.getUniqueId(), goal);
                }
            }
        } catch (Exception ignored) {}
    }

    private void restoreFleeGoals(Villager v) {
        Goal<Villager> goal = removedAvoidGoals.remove(v.getUniqueId());
        if (goal == null) return;
        try {
            Bukkit.getMobGoals().addGoal(v, 1, goal);
        } catch (Exception ignored) {}
    }

    private LivingEntity findTarget(Villager v) {
        return v.getNearbyEntities(20, 8, 20).stream()
                .filter(e -> e instanceof Zombie || e instanceof Skeleton || e instanceof Pillager || 
                          e instanceof Vindicator || e instanceof Player || e instanceof Ravager)
                .filter(e -> !(e instanceof Player) || !((Player) e).hasPermission("xautral.op"))
                .map(e -> (LivingEntity) e)
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(v.getLocation())))
                .orElse(null);
    }

    private void performMeleeAttack(Villager v, LivingEntity target, Material weapon, double damage) {
        v.swingMainHand();
        target.damage(damage, v);
        if (weapon != null) {
            v.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        }
    }

    private void performWeaponSmithAttack(Villager v, LivingEntity target) {
        v.swingMainHand();
        target.damage(12.0, v);
        
        // Sweep damage to nearby hostiles
        v.getNearbyEntities(3, 3, 3).stream()
                .filter(e -> e instanceof Monster || e instanceof Player)
                .filter(e -> !(e instanceof Player) || !((Player) e).hasPermission("xautral.op"))
                .filter(e -> e != target)
                .forEach(e -> ((LivingEntity) e).damage(6.0, v));
        
        // Iron sword particles and sound effects
        v.getWorld().spawnParticle(Particle.SWEEP_ATTACK, v.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.0);
        v.getWorld().spawnParticle(Particle.CRIT, v.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
        v.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.9f);
        v.getWorld().playSound(v.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
    }

    private void performToolSmithAttack(Villager v, LivingEntity target) {
        v.swingMainHand();
        target.damage(8.0, v);
        
        // Stone sword particles and sound effects
        v.getWorld().spawnParticle(Particle.SWEEP_ATTACK, v.getLocation().add(0, 1, 0), 3, 0.4, 0.4, 0.4, 0.0);
        v.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
    }

    private void healNearbyVillagers(Villager v) {
        v.getNearbyEntities(8, 4, 8).stream()
                .filter(e -> e instanceof Villager && e != v)
                .map(e -> (Villager) e)
                .forEach(other -> {
                    other.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1));
                    other.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 0));
                    other.getWorld().spawnParticle(Particle.HEART, other.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.0);
                    other.getWorld().playSound(other.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f);
                });
    }

    private void shootArrow(Villager v, LivingEntity target) {
        v.swingMainHand();
        Arrow arrow = v.launchProjectile(Arrow.class);
        arrow.setDamage(4.0);
        arrow.setCritical(true);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        
        // Accurate aim at the target's eyes with gravity compensation
        Vector direction = target.getEyeLocation().toVector().subtract(v.getEyeLocation().toVector());
        double dist = direction.length();
        direction.normalize().add(new Vector(0, Math.min(0.2, dist * 0.06), 0)).normalize().multiply(3.0);
        arrow.setVelocity(direction);
        
        v.getWorld().playSound(v.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.0f);
        v.getWorld().spawnParticle(Particle.CRIT, v.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.1);
    }

    private void summonFangsLine(Villager v, LivingEntity target) {
        Location start = v.getLocation();
        Vector dir = target.getLocation().toVector().subtract(start.toVector()).normalize();
        List<Location> spots = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            spots.add(start.clone().add(dir.clone().multiply(i * 2)));
        }

        // Cancel if an ally villager stands in the fang line to avoid friendly fire
        for (Location loc : spots) {
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 2, 2, 2)) {
                if (e instanceof Villager && e != v) return;
            }
        }

        for (Location loc : spots) {
            EvokerFangs fangs = (EvokerFangs) v.getWorld().spawnEntity(loc, EntityType.EVOKER_FANGS);
            fangs.setOwner(v);
        }
        
        v.getWorld().playSound(v.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.0f);
        v.getWorld().spawnParticle(Particle.WITCH, v.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.0);
    }

    private void useShield(Villager v) {
        // Shield defense effect
        v.getWorld().playSound(v.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
        v.getWorld().spawnParticle(Particle.CRIT, v.getLocation().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0.1);
        v.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 1));
    }

    private void throwPotion(Villager v, LivingEntity target) {
        v.swingMainHand();
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1), true);
        potion.setItemMeta(meta);
        ThrownPotion thrown = v.launchProjectile(ThrownPotion.class);
        thrown.setItem(potion);
        Vector dir = target.getLocation().toVector().subtract(v.getLocation().toVector()).normalize().multiply(0.5);
        thrown.setVelocity(dir);
    }

    private void enchantNearby(Villager v) {
        v.getNearbyEntities(6, 3, 6).stream()
                .filter(e -> e instanceof Villager && e != v)
                .map(e -> (Villager) e)
                .forEach(other -> {
                    other.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1));
                    other.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
                    other.getWorld().spawnParticle(Particle.ENCHANT, other.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.0);
                    other.getWorld().playSound(other.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
                });
    }

    private void distributeArmor(Villager v) {
        v.getNearbyEntities(5, 3, 5).stream()
                .filter(e -> e instanceof Villager && e != v)
                .map(e -> (Villager) e)
                .forEach(other -> {
                    other.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1));
                });
    }

    @EventHandler
    public void onVillagerHurt(EntityDamageByEntityEvent event) {
        if (!villagerAttackEnabled) return;
        if (!(event.getEntity() instanceof Villager villager)) return;
        Entity damager = event.getDamager();
        if (damager instanceof LivingEntity living) {
            villager.setTarget(living);
            villager.setAware(true);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!villagerAttackEnabled) return;
        if (event.getEntity() instanceof Villager) {
            Villager villager = (Villager) event.getEntity();
            
            // Make villagers aggressive towards monsters and hostile entities
            if (event.getTarget() instanceof Monster || event.getTarget() instanceof Player) {
                event.setCancelled(false);
                villager.setAware(true);
                // Force villager to target the enemy
                villager.setTarget((LivingEntity) event.getTarget());
            }
            
            // Override fear behavior - prevent villagers from fleeing
            if (event.getReason() == EntityTargetEvent.TargetReason.FORGOT_TARGET ||
                event.getReason() == EntityTargetEvent.TargetReason.CLOSEST_PLAYER ||
                event.getReason() == EntityTargetEvent.TargetReason.TEMPT) {
                // Keep targeting enemies instead of forgetting or fleeing
                LivingEntity currentTarget = villager.getTarget();
                if (currentTarget instanceof Monster || currentTarget instanceof Player) {
                    event.setCancelled(true);
                    villager.setTarget(currentTarget);
                }
            }
            
            // Prevent villagers from targeting non-hostile entities when in attack mode
            if (event.getTarget() != null && !(event.getTarget() instanceof Monster) && !(event.getTarget() instanceof Player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (!event.getPlayer().isSneaking() || !event.getPlayer().hasPermission("xautral.op")) return;
        event.setCancelled(true);
        UUID pid = event.getPlayer().getUniqueId();
        editingVillagers.put(pid, villager);
        if (!editingRecipes.containsKey(pid)) editingRecipes.put(pid, new ArrayList<>(villager.getRecipes()));
        event.getPlayer().openInventory(buildTradeList(pid, villager));
    }

    private Inventory buildTradeList(UUID pid, Villager villager) {
        if (villager == null) return Bukkit.createInventory(null, 54, color("&8Trades"));
        int page = editingPages.getOrDefault(pid, 0);
        List<MerchantRecipe> recipes = editingRecipes.getOrDefault(pid, new ArrayList<>());
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Trades: " + villager.getName()));
        for (int i = 0; i < 36; i++) {
            int idx = (page * 36) + i;
            if (idx < recipes.size()) inv.setItem(i, createRecipeIcon(recipes.get(idx), idx));
        }
        fillUI(inv);
        inv.setItem(49, createButton(Material.EMERALD, "&aAñadir Nuevo Trade", "add"));
        inv.setItem(51, createButton(Material.GREEN_STAINED_GLASS_PANE, "&6&lGUARDAR TODO", "save_all"));
        inv.setItem(53, createButton(Material.RED_STAINED_GLASS_PANE, "&cCancelar", "exit"));
        return inv;
    }

    private Inventory buildSingleTradeEditor(UUID pid, int index) {
        editingTradeIndex.put(pid, index);
        List<MerchantRecipe> recipes = editingRecipes.getOrDefault(pid, new ArrayList<>());
        MerchantRecipe recipe = (index >= 0 && index < recipes.size()) ? recipes.get(index) : null;
        Inventory inv = Bukkit.createInventory(null, 27, color("&8Editando Trade #" + (index + 1)));
        for (int i = 0; i < 27; i++) inv.setItem(i, createButton(Material.GRAY_STAINED_GLASS_PANE, " ", "none"));
        inv.setItem(10, recipe != null && recipe.getIngredients().size() > 0 ? recipe.getIngredients().get(0) : null);
        inv.setItem(11, recipe != null && recipe.getIngredients().size() > 1 ? recipe.getIngredients().get(1) : null);
        inv.setItem(13, createButton(Material.ARROW, "&eSolicita -> Entrega", "none"));
        inv.setItem(16, recipe != null ? recipe.getResult() : null);
        inv.setItem(22, createButton(Material.CHEST, "&aConfirmar este trade", "confirm_trade"));
        inv.setItem(26, createButton(Material.BARRIER, "&cVolver", "back"));
        return inv;
    }

    private void openEditorView(Player player, Inventory inv) {
        UUID pid = player.getUniqueId();
        switchingViews.add(pid);
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline() && editingVillagers.containsKey(pid)) {
                player.openInventory(inv);
            } else {
                switchingViews.remove(pid);
            }
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID pid = player.getUniqueId();
        if (!editingVillagers.containsKey(pid)) return;
        ItemStack item = event.getCurrentItem();
        String action = getAction(item);
        if (action != null && !action.equals("none")) {
            event.setCancelled(true);
            handleUIAction(player, action, event);
            return;
        }
        if (event.getView().getTitle().contains("Editando Trade")) {
            int slot = event.getRawSlot();
            if (slot >= 0 && slot < 27 && slot != 10 && slot != 11 && slot != 16) event.setCancelled(true);
        } else if (event.getRawSlot() < 54) {
            event.setCancelled(true);
            Integer idx = getTradeIndex(item);
            if (idx != null) {
                List<MerchantRecipe> recipes = editingRecipes.get(pid);
                if (recipes == null) return;
                if (event.getClick() == ClickType.RIGHT) {
                    if (idx >= 0 && idx < recipes.size()) recipes.remove((int) idx);
                    openEditorView(player, buildTradeList(pid, editingVillagers.get(pid)));
                } else {
                    openEditorView(player, buildSingleTradeEditor(pid, idx));
                }
            }
        }
    }

    private void handleUIAction(Player player, String action, InventoryClickEvent event) {
        UUID pid = player.getUniqueId();
        Villager villager = editingVillagers.get(pid);
        if (villager == null) return;
        switch (action) {
            case "add" -> {
                List<MerchantRecipe> recipes = editingRecipes.getOrDefault(pid, new ArrayList<>());
                recipes.add(new MerchantRecipe(new ItemStack(Material.STONE), 999));
                editingRecipes.put(pid, recipes);
                openEditorView(player, buildTradeList(pid, villager));
            }
            case "save_all" -> saveVillager(player);
            case "exit" -> closeEditor(player);
            case "back" -> openEditorView(player, buildTradeList(pid, villager));
            case "confirm_trade" -> saveCurrentTrade(player, event.getInventory());
        }
    }

    private void saveCurrentTrade(Player player, Inventory inv) {
        UUID pid = player.getUniqueId();
        int idx = editingTradeIndex.getOrDefault(pid, -1);
        if (idx < 0) return;
        ItemStack in1 = inv.getItem(10); ItemStack in2 = inv.getItem(11); ItemStack res = inv.getItem(16);
        if (res == null || res.getType() == Material.AIR) return;
        MerchantRecipe recipe = new MerchantRecipe(res, 999);
        if (in1 != null) recipe.addIngredient(in1);
        if (in2 != null) recipe.addIngredient(in2);
        List<MerchantRecipe> recipes = editingRecipes.get(pid);
        if (recipes != null && idx < recipes.size()) recipes.set(idx, recipe);
        openEditorView(player, buildTradeList(pid, editingVillagers.get(pid)));
    }

    private void saveVillager(Player player) {
        UUID pid = player.getUniqueId();
        Villager v = editingVillagers.get(pid);
        List<MerchantRecipe> recipes = editingRecipes.get(pid);
        if (v == null || recipes == null) return;
        v.setRecipes(recipes);
        player.sendMessage(color("&a¡Trades guardados!"));
        closeEditor(player);
    }

    private void closeEditor(Player player) {
        UUID pid = player.getUniqueId();
        editingVillagers.remove(pid); editingRecipes.remove(pid); editingPages.remove(pid); editingTradeIndex.remove(pid);
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) player.closeInventory();
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID pid = player.getUniqueId();

        // When switching between editor views the old inventory closes; keep editing state.
        if (switchingViews.remove(pid)) return;

        // Clean up editing state when the player actually leaves the editor
        if (editingVillagers.containsKey(pid)) {
            editingVillagers.remove(pid);
            editingRecipes.remove(pid);
            editingPages.remove(pid);
            editingTradeIndex.remove(pid);
        }
    }

    private ItemStack createRecipeIcon(MerchantRecipe r, int idx) {
        ItemStack item = r.getResult().clone();
        ItemMeta m = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Trade #" + (idx + 1)));
        for (ItemStack ing : r.getIngredients()) lore.add(color("&c- " + ing.getAmount() + "x " + ing.getType()));
        lore.add(color("&eClick: Editar | Click Derecho: Borrar"));
        m.setLore(lore);
        m.getPersistentDataContainer().set(tradeIndexKey, PersistentDataType.INTEGER, idx);
        item.setItemMeta(m);
        return item;
    }

    private ItemStack createButton(Material mat, String name, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(color(name));
        m.getPersistentDataContainer().set(buttonKey, PersistentDataType.STRING, action);
        item.setItemMeta(m);
        return item;
    }

    private String getAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(buttonKey, PersistentDataType.STRING);
    }

    private Integer getTradeIndex(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(tradeIndexKey, PersistentDataType.INTEGER);
    }

    private void fillUI(Inventory inv) {
        for (int i = 36; i < 45; i++) inv.setItem(i, createButton(Material.BLACK_STAINED_GLASS_PANE, " ", "none"));
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
}

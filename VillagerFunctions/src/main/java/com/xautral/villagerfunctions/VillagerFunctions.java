package com.xautral.villagerfunctions;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
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

public class VillagerFunctions extends JavaPlugin implements Listener {

    private final Map<UUID, Villager> editingVillagers = new HashMap<>();
    private final Map<UUID, Integer> editingPages = new HashMap<>();
    private final Map<UUID, List<MerchantRecipe>> editingRecipes = new HashMap<>();
    private final Map<UUID, Integer> editingTradeIndex = new HashMap<>();

    private boolean villagerAttackEnabled = false;
    private NamespacedKey buttonKey;
    private NamespacedKey tradeIndexKey;

    @Override
    public void onEnable() {
        buttonKey = new NamespacedKey(this, "action");
        tradeIndexKey = new NamespacedKey(this, "trade_index");

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("villagerattack").setExecutor(this);

        startVillagerAttackTask();
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (label.equalsIgnoreCase("villagerattack")) {
            if (!player.hasPermission("xautral.op")) return true;
            if (args.length < 1) return true;
            villagerAttackEnabled = args[0].equalsIgnoreCase("on");
            player.sendMessage(color(villagerAttackEnabled ? "&aAtaque de aldeanos ACTIVADO." : "&cAtaque de aldeanos DESACTIVADO."));
        }
        return true;
    }

    private void startVillagerAttackTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!villagerAttackEnabled) return;
                for (World world : Bukkit.getWorlds()) {
                    for (Villager v : world.getEntitiesByClass(Villager.class)) {
                        updateVillagerCombat(v);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void updateVillagerCombat(Villager v) {
        LivingEntity target = findTarget(v);
        if (target == null) return;

        v.setTarget(target);
        double dist = v.getLocation().distance(target.getLocation());
        Villager.Profession prof = v.getProfession();

        if (prof == Villager.Profession.WEAPONSMITH) {
            if (dist < 3) performMeleeAttack(v, target, Material.IRON_AXE, 10.0);
        } else if (prof == Villager.Profession.CLERIC) {
            if (dist < 10 && dist > 4 && Math.random() < 0.2) throwPotion(v, target);
        } else if (prof == Villager.Profession.LIBRARIAN) {
            if (dist < 12 && Math.random() < 0.1) summonFangs(v, target);
            if (Math.random() < 0.05) enchantNearby(v);
        } else if (prof == Villager.Profession.TOOLSMITH) {
            if (dist < 3) performMeleeAttack(v, target, Material.STONE_SWORD, 7.0);
            if (Math.random() < 0.05) distributeArmor(v);
        } else {
            if (dist < 2) performMeleeAttack(v, target, null, 4.0);
        }
    }

    private LivingEntity findTarget(Villager v) {
        return (LivingEntity) v.getNearbyEntities(12, 5, 12).stream()
                .filter(e -> e instanceof Zombie || e instanceof Skeleton || e instanceof Pillager || e instanceof Vindicator)
                .findFirst().orElse(null);
    }

    private void performMeleeAttack(Villager v, LivingEntity target, Material weapon, double damage) {
        v.swingMainHand();
        target.damage(damage, v);
        if (weapon != null) {
            v.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        }
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

    private void summonFangs(Villager v, LivingEntity target) {
        Location start = v.getLocation();
        Vector dir = target.getLocation().toVector().subtract(start.toVector()).normalize();
        for (int i = 1; i <= 7; i++) {
            Location loc = start.clone().add(dir.clone().multiply(i));
            v.getWorld().spawnEntity(loc, EntityType.EVOKER_FANGS);
        }
    }

    private void enchantNearby(Villager v) {
        v.getNearbyEntities(5, 3, 5).stream()
                .filter(e -> e instanceof Villager)
                .map(e -> (Villager) e)
                .filter(other -> other.getProfession() == Villager.Profession.WEAPONSMITH)
                .forEach(ws -> {
                    ws.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1));
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
    public void onEntityTarget(EntityTargetEvent event) {
        if (!villagerAttackEnabled) return;
        if (event.getEntity() instanceof Villager && event.getTarget() instanceof Monster) {
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (!event.getPlayer().isSneaking() || !event.getPlayer().hasPermission("xautral.op")) return;
        event.setCancelled(true);
        openVillagerTradeList(event.getPlayer(), villager);
    }

    private void openVillagerTradeList(Player player, Villager villager) {
        UUID pid = player.getUniqueId();
        editingVillagers.put(pid, villager);
        if (!editingRecipes.containsKey(pid)) editingRecipes.put(pid, new ArrayList<>(villager.getRecipes()));
        int page = editingPages.getOrDefault(pid, 0);
        List<MerchantRecipe> recipes = editingRecipes.get(pid);
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Trades: " + villager.getName()));
        for (int i = 0; i < 36; i++) {
            int idx = (page * 36) + i;
            if (idx < recipes.size()) inv.setItem(i, createRecipeIcon(recipes.get(idx), idx));
        }
        fillUI(inv);
        inv.setItem(49, createButton(Material.EMERALD, "&aAñadir Nuevo Trade", "add"));
        inv.setItem(51, createButton(Material.GREEN_STAINED_GLASS_PANE, "&6&lGUARDAR TODO", "save_all"));
        inv.setItem(53, createButton(Material.RED_STAINED_GLASS_PANE, "&cCancelar", "exit"));
        player.openInventory(inv);
    }

    private void openSingleTradeEditor(Player player, int index) {
        editingTradeIndex.put(player.getUniqueId(), index);
        List<MerchantRecipe> recipes = editingRecipes.get(player.getUniqueId());
        MerchantRecipe recipe = (index >= 0 && index < recipes.size()) ? recipes.get(index) : null;
        Inventory inv = Bukkit.createInventory(null, 27, color("&8Editando Trade #" + (index + 1)));
        for (int i = 0; i < 27; i++) inv.setItem(i, createButton(Material.GRAY_STAINED_GLASS_PANE, " ", "none"));
        inv.setItem(10, recipe != null && recipe.getIngredients().size() > 0 ? recipe.getIngredients().get(0) : null);
        inv.setItem(11, recipe != null && recipe.getIngredients().size() > 1 ? recipe.getIngredients().get(1) : null);
        inv.setItem(13, createButton(Material.ARROW, "&eSolicita -> Entrega", "none"));
        inv.setItem(16, recipe != null ? recipe.getResult() : null);
        inv.setItem(22, createButton(Material.CHEST, "&aConfirmar este trade", "confirm_trade"));
        inv.setItem(26, createButton(Material.BARRIER, "&cVolver", "back"));
        player.openInventory(inv);
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
                if (event.getClick() == ClickType.RIGHT) {
                    editingRecipes.get(pid).remove((int)idx);
                    openVillagerTradeList(player, editingVillagers.get(pid));
                } else {
                    openSingleTradeEditor(player, idx);
                }
            }
        }
    }

    private void handleUIAction(Player player, String action, InventoryClickEvent event) {
        UUID pid = player.getUniqueId();
        switch (action) {
            case "add" -> {
                editingRecipes.get(pid).add(new MerchantRecipe(new ItemStack(Material.STONE), 999));
                openVillagerTradeList(player, editingVillagers.get(pid));
            }
            case "save_all" -> saveVillager(player);
            case "exit" -> closeEditor(player);
            case "back" -> openVillagerTradeList(player, editingVillagers.get(pid));
            case "confirm_trade" -> saveCurrentTrade(player, event.getInventory());
        }
    }

    private void saveCurrentTrade(Player player, Inventory inv) {
        UUID pid = player.getUniqueId();
        int idx = editingTradeIndex.get(pid);
        ItemStack in1 = inv.getItem(10); ItemStack in2 = inv.getItem(11); ItemStack res = inv.getItem(16);
        if (res == null || res.getType() == Material.AIR) return;
        MerchantRecipe recipe = new MerchantRecipe(res, 999);
        if (in1 != null) recipe.addIngredient(in1);
        if (in2 != null) recipe.addIngredient(in2);
        editingRecipes.get(pid).set(idx, recipe);
        openVillagerTradeList(player, editingVillagers.get(pid));
    }

    private void saveVillager(Player player) {
        Villager v = editingVillagers.get(player.getUniqueId());
        v.setRecipes(editingRecipes.get(player.getUniqueId()));
        player.sendMessage(color("&a¡Trades guardados!"));
        closeEditor(player);
    }

    private void closeEditor(Player player) {
        UUID pid = player.getUniqueId();
        editingVillagers.remove(pid); editingRecipes.remove(pid); editingPages.remove(pid);
        player.closeInventory();
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

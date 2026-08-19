package com.skillweapons.skillweaponsplugin;

import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SkillWeaponsPlugin extends JavaPlugin implements Listener, TabCompleter {

    private Map<UUID, Long> crossbowCooldowns = new HashMap<>();
    private static final long CROSSBOW_COOLDOWN = 20000; // 20 segundos
    private static final long SWEEP_COOLDOWN = 3500;
    private static final long KILO_COOLDOWN = 2500;
    private static final long SPADE_COOLDOWN = 15000;
    private static final long ELEKTROFOLLOW_COOLDOWN = 5000;
    private static final long HELLISH_ALERT_DURATION = 2000;
    private static final long AIRDASH_COOLDOWN = 3000;
    private static final int TELEKINESIS_MAX_DARTS = 50;
    private static final Particle.DustOptions OMNIPRESENCY_DUST =
        new Particle.DustOptions(Color.fromRGB(80, 255, 40), 1.2f);
    private static final Particle.DustOptions BLACK_SPIRAL_DUST =
        new Particle.DustOptions(Color.fromRGB(5, 5, 5), 1.0f);
    private Map<UUID, Long> sweepChargeStarts = new HashMap<>();
    private Map<UUID, Integer> hellishShieldHits = new HashMap<>();
    private Set<UUID> hellishAlerts = new HashSet<>();
    private Map<UUID, BossBar> hellishAlertBars = new HashMap<>();
    private Map<UUID, List<SmallFireball>> hellishFireballs = new HashMap<>();
    private Map<UUID, LivingEntity> hellishTargets = new HashMap<>();
    private Map<UUID, Long> hellishFireImmunityEnds = new HashMap<>();
    private Set<UUID> airdashPlayers = new HashSet<>();
    private Map<UUID, Integer> airdashCharges = new HashMap<>();
    private Map<UUID, List<Arrow>> telekinesisDarts = new HashMap<>();
    private Map<UUID, Integer> telekinesisSpawned = new HashMap<>();
    private Set<UUID> telekinesisCancelled = new HashSet<>();
    private Map<UUID, LivingEntity> telekinesisTargets = new HashMap<>();
    private Map<UUID, Location> telekinesisFallbackTargets = new HashMap<>();
    private Map<UUID, Map<String, Long>> skillCooldownEnds = new HashMap<>();
    private Map<UUID, Map<String, Long>> skillCooldownDurations = new HashMap<>();
    private Map<UUID, Map<String, BossBar>> skillCooldownBars = new HashMap<>();
    
    // Persistent Data Keys para guardar skills en los items
    private NamespacedKey BEAM_KEY;
    private NamespacedKey BEAM_BOOK_KEY;
    private NamespacedKey BLACK_SPIRAL_KEY;
    private NamespacedKey BLACK_SPIRAL_BOOK_KEY;
    private NamespacedKey SWEEP_KEY;
    private NamespacedKey KILO_KEY;
    private NamespacedKey TELEKINESIS_KEY;
    private NamespacedKey SPADE_KEY;
    private NamespacedKey ELEKTROFOLLOW_KEY;
    private NamespacedKey TRAJECTORY_KEY;
    private NamespacedKey HELLISH_DOMINO_KEY;
    private NamespacedKey OMNIPRESENCY_KEY;
    private NamespacedKey AIRDASH_KEY;
    private NamespacedKey SWEEP_BOOK_KEY;
    private NamespacedKey KILO_BOOK_KEY;
    private NamespacedKey TELEKINESIS_BOOK_KEY;
    private NamespacedKey SPADE_BOOK_KEY;
    private NamespacedKey ELEKTROFOLLOW_BOOK_KEY;
    private NamespacedKey TRAJECTORY_BOOK_KEY;
    private NamespacedKey HELLISH_DOMINO_BOOK_KEY;
    private NamespacedKey OMNIPRESENCY_BOOK_KEY;
    private NamespacedKey AIRDASH_BOOK_KEY;
    private NamespacedKey LOW_C_KEY;
    private NamespacedKey ANTI_KINETIC_KEY;
    private NamespacedKey LOW_C_BOOK_KEY;
    private NamespacedKey ANTI_KINETIC_BOOK_KEY;
    private NamespacedKey ELYTRA_SHULKER_KEY;
    private NamespacedKey ELYTRA_SHULKER_BOOK_KEY;

    @Override
    public void onEnable() {
        getLogger().info("SkillWeaponsPlugin habilitado!");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("sw").setTabCompleter(this);
        
        // Inicializar Persistent Data Keys
        BEAM_KEY = new NamespacedKey(this, "beam_skill");
        BEAM_BOOK_KEY = new NamespacedKey(this, "beam_skill_book");
        BLACK_SPIRAL_KEY = new NamespacedKey(this, "black_spiral_skill");
        BLACK_SPIRAL_BOOK_KEY = new NamespacedKey(this, "black_spiral_skill_book");
        SWEEP_KEY = new NamespacedKey(this, "sweep_defense_skill");
        KILO_KEY = new NamespacedKey(this, "kilo_skill");
        TELEKINESIS_KEY = new NamespacedKey(this, "telekinesis_skill");
        SPADE_KEY = new NamespacedKey(this, "spade_skill");
        ELEKTROFOLLOW_KEY = new NamespacedKey(this, "elektrofollow_skill");
        TRAJECTORY_KEY = new NamespacedKey(this, "trajectory_skill");
        HELLISH_DOMINO_KEY = new NamespacedKey(this, "hellish_domino_skill");
        OMNIPRESENCY_KEY = new NamespacedKey(this, "omnipresency_skill");
        AIRDASH_KEY = new NamespacedKey(this, "airdash_skill");
        SWEEP_BOOK_KEY = new NamespacedKey(this, "sweep_defense_skill_book");
        KILO_BOOK_KEY = new NamespacedKey(this, "kilo_skill_book");
        TELEKINESIS_BOOK_KEY = new NamespacedKey(this, "telekinesis_skill_book");
        SPADE_BOOK_KEY = new NamespacedKey(this, "spade_skill_book");
        ELEKTROFOLLOW_BOOK_KEY = new NamespacedKey(this, "elektrofollow_skill_book");
        TRAJECTORY_BOOK_KEY = new NamespacedKey(this, "trajectory_skill_book");
        HELLISH_DOMINO_BOOK_KEY = new NamespacedKey(this, "hellish_domino_skill_book");
        OMNIPRESENCY_BOOK_KEY = new NamespacedKey(this, "omnipresency_skill_book");
        AIRDASH_BOOK_KEY = new NamespacedKey(this, "airdash_skill_book");
        LOW_C_KEY = new NamespacedKey(this, "low_c_skill");
        ANTI_KINETIC_KEY = new NamespacedKey(this, "anti_kinetic_skill");
        LOW_C_BOOK_KEY = new NamespacedKey(this, "low_c_skill_book");
        ANTI_KINETIC_BOOK_KEY = new NamespacedKey(this, "anti_kinetic_skill_book");
        ELYTRA_SHULKER_KEY = new NamespacedKey(this, "elytra_shulker_skill");
        ELYTRA_SHULKER_BOOK_KEY = new NamespacedKey(this, "elytra_shulker_skill_book");
        startCooldownBarTask();
        
        // Generar libros de skills en estructuras existentes
        generateSkillBooksInStructures();
    }

    @Override
    public void onDisable() {
        getLogger().info("SkillWeaponsPlugin deshabilitado!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sw") || command.getName().equalsIgnoreCase("skillweapons") || command.getName().equalsIgnoreCase("skillw")) {
            if (!sender.hasPermission("skillweapons.admin")) {
                sender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage("Este comando solo puede ser usado por jugadores.");
                return true;
            }

            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Uso: /sw give [beam|black-spiral|sweep|kilo|telekinesis|spade|elektrofollow|trajectory|hellishdomino|omnipresency|airdash|low-c|anti-kinetic]");
                return true;
            }

            if (args[0].equalsIgnoreCase("give")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Uso: /sw give [enchant]");
                    return true;
                }
                giveSkillBook(player, args[1]);
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "Opción no válida. Usa: give");
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("sw") || command.getName().equalsIgnoreCase("skillweapons") || command.getName().equalsIgnoreCase("skillw")) {
            if (args.length == 1) {
                return Arrays.asList("give");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return Arrays.asList("beam", "black-spiral", "sweep", "kilo", "telekinesis", "spade", "elektrofollow", "trajectory", "hellishdomino", "omnipresency", "airdash", "low-c", "anti-kinetic", "elytra-shulker");
            }
        }
        return null;
    }

    private void giveSkillBook(Player player, String enchantType) {
        ItemStack skillBook = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = skillBook.getItemMeta();

        if (enchantType.equalsIgnoreCase("laser_crossbow") || enchantType.equalsIgnoreCase("beam")) {
            meta.setDisplayName(ChatColor.RED + "Libro de Skill: Beam");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Aplica el encantamiento Beam");
            lore.add(ChatColor.GRAY + "a tu ballesta actual.");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Combinalo con una ballesta en un yunque.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(BEAM_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("black-spiral") || enchantType.equalsIgnoreCase("blackspiral")) {
            meta.setDisplayName(ChatColor.BLACK + "Libro de Skill: Black Spiral");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Reemplaza el disparo por un rastro negro.",
                ChatColor.GRAY + "Invoca un anillo de balas de shulker.",
                "",
                ChatColor.YELLOW + "Combinalo con una ballesta en un yunque."
            ));
            meta.getPersistentDataContainer().set(BLACK_SPIRAL_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("sweep") || enchantType.equalsIgnoreCase("sweep_defense")) {
            meta.setDisplayName(ChatColor.GREEN + "Libro de Skill: Sweep Defense");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Carga un ataque de barrido con una azada.",
                ChatColor.GRAY + "Puede aturdir escudos.",
                "",
                ChatColor.YELLOW + "Combinalo con una azada en un yunque."
            ));
            meta.getPersistentDataContainer().set(SWEEP_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("kilo")) {
            meta.setDisplayName(ChatColor.GOLD + "Libro de Skill: Kilo");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Aumenta el daño al caer desde altura.",
                ChatColor.GRAY + "Potenciador de ataque bajo.",
                "",
                ChatColor.YELLOW + "Combinalo con un hacha en un yunque."
            ));
            meta.getPersistentDataContainer().set(KILO_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("telekinesis")) {
            meta.setDisplayName(ChatColor.AQUA + "Libro de Skill: Telekinesis");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Crea flechas teledirigidas al impactar.",
                ChatColor.GRAY + "Las flechas avanzan cada vez más rápido.",
                "",
                ChatColor.YELLOW + "Combinalo con un arco en un yunque."
            ));
            meta.getPersistentDataContainer().set(TELEKINESIS_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("spade")) {
            meta.setDisplayName(ChatColor.DARK_GREEN + "Libro de Skill: Spade");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Golpea a jugadores en el aire para impulsarte.",
                ChatColor.GRAY + "Crea trazos de barrido dañinos.",
                "",
                ChatColor.YELLOW + "Combinalo con una espada en un yunque."
            ));
            meta.getPersistentDataContainer().set(SPADE_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("elektrofollow")) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Libro de Skill: Elektrofollow");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "El tridente invoca colmillos de evocador.",
                ChatColor.GRAY + "Los colmillos siguen caminos hacia enemigos cercanos.",
                "",
                ChatColor.YELLOW + "Combinalo con un tridente en un yunque."
            ));
            meta.getPersistentDataContainer().set(ELEKTROFOLLOW_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("trajectory")) {
            meta.setDisplayName(ChatColor.WHITE + "Libro de Skill: Trajectory");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Muestra la trayectoria al apuntar.",
                ChatColor.GRAY + "Potencia las flechas disparadas.",
                "",
                ChatColor.YELLOW + "Compatible con cualquier objeto en un yunque."
            ));
            meta.getPersistentDataContainer().set(TRAJECTORY_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("hellishdomino")) {
            meta.setDisplayName(ChatColor.RED + "Libro de Skill: Hellish Domino");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Skill exclusiva para escudos.",
                ChatColor.GRAY + "Tras bloquear varios golpes, alerta y explota.",
                "",
                ChatColor.YELLOW + "Combinalo con un escudo en un yunque."
            ));
            meta.getPersistentDataContainer().set(HELLISH_DOMINO_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("omnipresency")) {
            meta.setDisplayName(ChatColor.GREEN + "Libro de Skill: Omnipresency");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Las flechas dejan particulas radioactivas.",
                ChatColor.GRAY + "Intercambian tu posicion con el objetivo.",
                "",
                ChatColor.YELLOW + "Compatible con cualquier objeto en un yunque."
            ));
            meta.getPersistentDataContainer().set(OMNIPRESENCY_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("airdash")) {
            meta.setDisplayName(ChatColor.AQUA + "Libro de Skill: Airdash");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Skill para escudos.",
                ChatColor.GRAY + "Click derecho en el aire para impulsarte.",
                "",
                ChatColor.YELLOW + "Combinalo con un escudo en un yunque."
            ));
            meta.getPersistentDataContainer().set(AIRDASH_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("low-c") || enchantType.equalsIgnoreCase("lowc")) {
            meta.setDisplayName(ChatColor.BLUE + "Libro de Skill: Low-C");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Reduce cooldowns en un segundo.", "", ChatColor.YELLOW + "Combinalo con pantalones o casco en un yunque."));
            meta.getPersistentDataContainer().set(LOW_C_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("anti-kinetic") || enchantType.equalsIgnoreCase("antikinetic")) {
            meta.setDisplayName(ChatColor.RED + "Libro de Skill: Anti-Kinetic");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Convierte daño cinetico en empuje.", "", ChatColor.YELLOW + "Combinalo con un casco en un yunque."));
            meta.getPersistentDataContainer().set(ANTI_KINETIC_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else if (enchantType.equalsIgnoreCase("elytra-shulker")) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "Libro de Skill: Elytra Shulker");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Al golpear planeando, dispara 10 balas de shulker.", "", ChatColor.YELLOW + "Combinalo con una elytra en un yunque."));
            meta.getPersistentDataContainer().set(ELYTRA_SHULKER_BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        } else {
            player.sendMessage(ChatColor.RED + "Skill no válida. Usa una skill disponible en el tab-complete.");
            return;
        }

        skillBook.setItemMeta(meta);
        
        player.getInventory().addItem(skillBook);
        player.sendMessage(ChatColor.GREEN + "Has recibido el libro de skill: " + enchantType);
    }

    private void generateSkillBooksInStructures() {
        // Este método genera libros de skills en cofres de estructuras
        // Se ejecutará cuando se detecte la generación de nuevas estructuras
        getLogger().info("Sistema de generación de libros en estructuras iniciado.");
    }

    private void startCooldownBarTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (UUID uuid : new ArrayList<>(skillCooldownEnds.keySet())) {
                    Map<String, Long> playerEnds = skillCooldownEnds.get(uuid);
                    Map<String, Long> playerDurations = skillCooldownDurations.get(uuid);
                    Map<String, BossBar> playerBars = skillCooldownBars.get(uuid);
                    if (playerEnds == null || playerBars == null) continue;
                    Player player = Bukkit.getPlayer(uuid);
                    for (String skillName : new ArrayList<>(playerEnds.keySet())) {
                        Long end = playerEnds.get(skillName);
                        BossBar bar = playerBars.get(skillName);
                        long duration = playerDurations.getOrDefault(skillName, 1L);
                        if (end == null || bar == null) continue;
                        long total = Math.max(1L, end - now + 1L);
                        double progress = Math.max(0.0, Math.min(1.0, total / (double) duration));
                        if (player == null || !player.isOnline() || end <= now) {
                            bar.removeAll();
                            playerEnds.remove(skillName);
                            playerBars.remove(skillName);
                            playerDurations.remove(skillName);
                            if ("Airdash".equals(skillName)) airdashCharges.put(uuid, 2);
                        } else {
                            bar.setProgress(progress);
                        }
                    }
                    if (playerEnds.isEmpty()) {
                        skillCooldownEnds.remove(uuid);
                        skillCooldownDurations.remove(uuid);
                        skillCooldownBars.remove(uuid);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private boolean isOnCooldown(Player player, String skillName) {
        Map<String, Long> playerEnds = skillCooldownEnds.get(player.getUniqueId());
        Long end = playerEnds == null ? null : playerEnds.get(skillName);
        if (end == null || end <= System.currentTimeMillis()) return false;
        player.sendMessage(ChatColor.RED + skillName + " está en cooldown.");
        return true;
    }

    private void startCooldown(Player player, String skillName, long duration) {
        UUID uuid = player.getUniqueId();
        Map<String, BossBar> playerBars = skillCooldownBars.computeIfAbsent(uuid, key -> new HashMap<>());
        Map<String, Long> playerEnds = skillCooldownEnds.computeIfAbsent(uuid, key -> new HashMap<>());
        Map<String, Long> playerDurations = skillCooldownDurations.computeIfAbsent(uuid, key -> new HashMap<>());
        BossBar oldBar = playerBars.get(skillName);
        if (oldBar != null) oldBar.removeAll();
        BossBar bar = Bukkit.createBossBar(ChatColor.YELLOW + skillName + " - cooldown", BarColor.YELLOW, BarStyle.SOLID);
        bar.addPlayer(player);
        bar.setProgress(1.0);
        playerBars.put(skillName, bar);
        long adjustedDuration = hasLowC(player) ? Math.max(1000L, duration - 1000L) : duration;
        playerEnds.put(skillName, System.currentTimeMillis() + adjustedDuration);
        playerDurations.put(skillName, adjustedDuration);
    }

    private boolean hasSkill(Player player, NamespacedKey key) {
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return true;
        }
        return false;
    }

    private boolean hasLowC(Player player) {
        return hasSkill(player, LOW_C_KEY);
    }

    private boolean isArmorItem(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
            name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || material == Material.ELYTRA;
    }

    private void applyBeamSkill(ItemStack crossbow) {
        ItemMeta meta = crossbow.getItemMeta();
        if (meta == null) return;

        // Aplicar skill usando Persistent Data (más robusto frente a yunques)
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(BEAM_KEY, PersistentDataType.STRING, "true");

        // Aplicar un encantamiento personalizado mediante lore
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Beam");
        lore.add(ChatColor.GRAY + "Dispara un rayo láser explosivo");
        lore.add(ChatColor.GRAY + "Crea esfera de balas de shulker");
        meta.setLore(lore);
        
        // Añadir un encantamiento visual
        meta.addEnchant(Enchantment.MULTISHOT, 1, true);
        meta.addEnchant(Enchantment.PIERCING, 1, true);
        crossbow.setItemMeta(meta);

    }

    private void applyBlackSpiralSkill(ItemStack crossbow) {
        ItemMeta meta = crossbow.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(BLACK_SPIRAL_KEY, PersistentDataType.STRING, "true");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Black Spiral");
        lore.add(ChatColor.GRAY + "Rastro negro y anillo de shulker");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.PIERCING, 1, true);
        crossbow.setItemMeta(meta);
    }

    private void applySweepSkill(ItemStack hoe) {
        ItemMeta meta = hoe.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(SWEEP_KEY, PersistentDataType.STRING, "true");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Sweep Defense");
        lore.add(ChatColor.GRAY + "Carga ataques de barrido");
        lore.add(ChatColor.GRAY + "Aturde escudos");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 1, true);
        hoe.setItemMeta(meta);
    }

    private void applyKiloSkill(ItemStack axe) {
        ItemMeta meta = axe.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(KILO_KEY, PersistentDataType.STRING, "true");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Kilo");
        lore.add(ChatColor.GRAY + "Mas altura, mas dano al caer");
        lore.add(ChatColor.GRAY + "Potenciador de ataque bajo");
        meta.setLore(lore);
        axe.setItemMeta(meta);
    }

    private void applyTelekinesisSkill(ItemStack bow) {
        ItemMeta meta = bow.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(TELEKINESIS_KEY, PersistentDataType.STRING, "true");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Telekinesis");
        lore.add(ChatColor.GRAY + "Flechas teledirigidas");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.POWER, 1, true);
        bow.setItemMeta(meta);
    }

    private void applySpadeSkill(ItemStack sword) {
        ItemMeta meta = sword.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(SPADE_KEY, PersistentDataType.STRING, "true");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Spade");
        lore.add(ChatColor.GRAY + "Impulso y trazos de barrido");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.SHARPNESS, 1, true);
        sword.setItemMeta(meta);
    }

    private void applyElektrofollowSkill(ItemStack trident) {
        ItemMeta meta = trident.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(ELEKTROFOLLOW_KEY, PersistentDataType.STRING, "true");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Elektrofollow");
        lore.add(ChatColor.GRAY + "Colmillos de evocador encadenados");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.IMPALING, 1, true);
        trident.setItemMeta(meta);
    }

    private void applyTrajectorySkill(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(TRAJECTORY_KEY, PersistentDataType.STRING, "true");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Trajectory");
        lore.add(ChatColor.GRAY + "Trayectoria visible y flechas potenciadas");
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void applyOmnipresencySkill(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(OMNIPRESENCY_KEY, PersistentDataType.STRING, "true");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Omnipresency");
        lore.add(ChatColor.GRAY + "Intercambio de posiciones y particulas verdes");
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void applyAirdashSkill(ItemStack shield) {
        ItemMeta meta = shield.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(AIRDASH_KEY, PersistentDataType.STRING, "true");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Airdash");
        lore.add(ChatColor.GRAY + "Impulso aereo y golpe de aterrizaje");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.FEATHER_FALLING, 1, true);
        shield.setItemMeta(meta);
    }

    private void applyHellishDominoSkill(ItemStack shield) {
        ItemMeta meta = shield.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(HELLISH_DOMINO_KEY, PersistentDataType.STRING, "true");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_PURPLE + "Skill: Hellish Domino");
        lore.add(ChatColor.GRAY + "Contraataque infernal al bloquear");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        shield.setItemMeta(meta);
    }

    private void applyLowCSkill(ItemStack armor) {
        ItemMeta meta = armor.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(LOW_C_KEY, PersistentDataType.STRING, "true");
        meta.setLore(appendLore(meta, ChatColor.DARK_PURPLE + "Skill: Low-C", ChatColor.GRAY + "Cooldowns reducidos un segundo"));
        armor.setItemMeta(meta);
    }

    private void applyAntiKineticSkill(ItemStack helmet) {
        ItemMeta meta = helmet.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(ANTI_KINETIC_KEY, PersistentDataType.STRING, "true");
        meta.setLore(appendLore(meta, ChatColor.DARK_PURPLE + "Skill: Anti-Kinetic", ChatColor.GRAY + "Proteccion contra daño cinetico"));
        helmet.setItemMeta(meta);
    }

    private void applyElytraShulkerSkill(ItemStack elytra) {
        ItemMeta meta = elytra.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(ELYTRA_SHULKER_KEY, PersistentDataType.STRING, "true");
        meta.setLore(appendLore(meta, ChatColor.DARK_PURPLE + "Skill: Elytra Shulker", ChatColor.GRAY + "Diez balas teledirigidas al golpear"));
        elytra.setItemMeta(meta);
    }

    private List<String> appendLore(ItemMeta meta, String... lines) {
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.addAll(Arrays.asList(lines));
        return lore;
    }

    @EventHandler
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.getType().name().endsWith("_HOE")) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(SWEEP_KEY, PersistentDataType.STRING)) return;
        sweepChargeStarts.putIfAbsent(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onSkillDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof LivingEntity) {
            Player player = (Player) event.getDamager();
            if (player.isGliding() && hasSkill(player, ELYTRA_SHULKER_KEY)) {
                for (int index = 0; index < 10; index++) {
                    Location spawn = player.getLocation().add(0, 1, 0);
                    ShulkerBullet bullet = player.getWorld().spawn(spawn, ShulkerBullet.class);
                    bullet.setTarget(event.getEntity());
                    bullet.setCustomName("ELYTRA_SHULKER_BULLET");
                    bullet.setCustomNameVisible(false);
                }
            }
        }
        if (event.getDamager() instanceof EvokerFangs && event.getEntity() instanceof LivingEntity) {
            LivingEntity target = (LivingEntity) event.getEntity();
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 24, 0.4, 0.6, 0.4, 0.15);
            return;
        }
        if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity) {
            Player defender = (Player) event.getEntity();
            ItemStack shield = getHellishShield(defender);
            if (shield != null && defender.isBlocking()) {
                int hits = hellishShieldHits.merge(defender.getUniqueId(), 1, Integer::sum);
                if (hits >= 3 && hellishAlerts.add(defender.getUniqueId())) {
                    startHellishAlert(defender);
                }
            }
        }
        if (!(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer data = meta.getPersistentDataContainer();

        if (data.has(SWEEP_KEY, PersistentDataType.STRING)) {
            Long chargeStart = sweepChargeStarts.remove(player.getUniqueId());
            if (chargeStart == null || System.currentTimeMillis() - chargeStart < 350L) return;
            if (isOnCooldown(player, "Sweep Defense")) return;
            startCooldown(player, "Sweep Defense", SWEEP_COOLDOWN);
            event.setDamage(event.getDamage() * 5.0);
            Location center = event.getEntity().getLocation();
            player.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.8f);
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0, 1, 0), 3, 0.7, 0.5, 0.7, 0.0);
            pushEntity(event.getEntity(), player, 4.0);
            for (Entity nearby : center.getWorld().getNearbyEntities(center, 2.8, 1.4, 2.8)) {
                if (nearby == event.getEntity() || nearby == player || !(nearby instanceof LivingEntity)) continue;
                LivingEntity victim = (LivingEntity) nearby;
                victim.damage(event.getDamage() * 2.5, player);
                victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1, 0), 2, 0.5, 0.4, 0.5, 0.0);
                pushEntity(victim, player, 4.0);
                if (nearby instanceof Player && ((Player) nearby).isBlocking()) {
                    ((Player) nearby).setCooldown(Material.SHIELD, 100);
                }
            }
            if (event.getEntity() instanceof Player && ((Player) event.getEntity()).isBlocking()) {
                ((Player) event.getEntity()).setCooldown(Material.SHIELD, 100);
            }
        } else if (data.has(SPADE_KEY, PersistentDataType.STRING)) {
            if (isOnCooldown(player, "Spade")) return;
            startCooldown(player, "Spade", SPADE_COOLDOWN);
            createSpadeTraces(player, event.getEntity());
        } else if (data.has(KILO_KEY, PersistentDataType.STRING) && player.getFallDistance() > 1.0f) {
            if (isOnCooldown(player, "Kilo")) return;
            double multiplier = 1.0 + Math.min(1.75, player.getFallDistance() * 0.10);
            event.setDamage(event.getDamage() * multiplier);
            player.setFallDistance(0.0f);
            startCooldown(player, "Kilo", KILO_COOLDOWN);
        }
    }

    private ItemStack getHellishShield(Player player) {
        for (ItemStack item : new ItemStack[] {player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand()}) {
            if (item == null || item.getType() != Material.SHIELD) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(HELLISH_DOMINO_KEY, PersistentDataType.STRING)) return item;
        }
        return null;
    }

    private void startHellishAlert(Player player) {
        UUID id = player.getUniqueId();
        BossBar bar = Bukkit.createBossBar(ChatColor.RED + "HELLISH DOMINO: ALEJATE", BarColor.RED, BarStyle.SOLID);
        bar.addPlayer(player);
        hellishAlertBars.put(id, bar);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!player.isOnline() || ticks++ >= 40) {
                    if (player.isOnline()) unleashHellishDomino(player);
                    bar.removeAll();
                    hellishAlertBars.remove(id);
                    hellishShieldHits.remove(id);
                    hellishAlerts.remove(id);
                    cancel();
                    return;
                }
                bar.setProgress(Math.max(0.0, 1.0 - ticks / 40.0));
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void pushEntity(Entity target, Player source, double strength) {
        Vector direction = target.getLocation().toVector().subtract(source.getLocation().toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 0.01) direction = source.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.01) return;
        target.setVelocity(direction.normalize().multiply(strength).setY(0.65));
    }

    private void createSpadeTraces(Player attacker, Entity originalTarget) {
        EntityType targetType = originalTarget.getType();
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity nearby : attacker.getWorld().getNearbyEntities(originalTarget.getLocation(), 12, 8, 12)) {
            if (nearby instanceof LivingEntity && nearby != attacker && nearby.getType() == targetType) {
                targets.add((LivingEntity) nearby);
            }
        }
        new BukkitRunnable() {
            int ticks = 0;
            Set<UUID> damaged = new HashSet<>();

            @Override
            public void run() {
                if (!attacker.isOnline() || ticks++ >= 8) {
                    cancel();
                    return;
                }
                Location origin = attacker.getLocation().clone().add(0, 1, 0);
                for (LivingEntity target : targets) {
                    if (!target.isValid()) continue;
                    Vector path = target.getLocation().add(0, 1, 0).toVector().subtract(origin.toVector());
                    double distance = path.length();
                    if (distance < 0.5) continue;
                    Vector step = path.normalize();
                    int points = Math.min(20, Math.max(1, (int) (distance / 0.7)));
                    for (int index = 1; index <= points; index++) {
                        Location trace = origin.clone().add(step.clone().multiply(index * distance / points));
                        attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, trace, 1, 0.15, 0.15, 0.15, 0);
                        for (Entity nearby : attacker.getWorld().getNearbyEntities(trace, 0.65, 0.7, 0.65)) {
                            if (!(nearby instanceof LivingEntity) || nearby == attacker || nearby.getType() != targetType) continue;
                            if (damaged.add(nearby.getUniqueId())) {
                                ((LivingEntity) nearby).damage(5.0, attacker);
                                pushEntity(nearby, attacker, 1.8);
                            }
                        }
                    }
                }
                attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, origin, 1, 0.2, 0.2, 0.2, 0);
                attacker.getWorld().playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    @EventHandler
    public void onSweepInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item == null || !item.getType().name().endsWith("_HOE")) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(SWEEP_KEY, PersistentDataType.STRING)) return;
        sweepChargeStarts.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        event.getPlayer().sendMessage(ChatColor.GREEN + "Sweep Defense cargando...");
        event.setCancelled(true);
    }

    @EventHandler
    public void onAirdashInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack shield = getAirdashShield(player);
        if (shield == null || player.isOnGround()) return;
        int charges = airdashCharges.getOrDefault(player.getUniqueId(), 2);
        if (charges <= 0) {
            if (isOnCooldown(player, "Airdash")) return;
            charges = 2;
        }
        charges--;
        airdashCharges.put(player.getUniqueId(), charges);
        if (charges == 0) startCooldown(player, "Airdash", AIRDASH_COOLDOWN);
        player.swingOffHand();
        player.playSound(player.getLocation(), Sound.ITEM_SPEAR_LUNGE_2, 0.5f, 1.0f);
        Vector direction = player.getLocation().getDirection().normalize().multiply(1.6);
        player.setVelocity(direction.setY(Math.max(0.25, direction.getY() + 0.2)));
        airdashPlayers.add(player.getUniqueId());
        for (int index = 0; index < 10; index++) {
            Location particleLocation = player.getLocation().clone().add(direction.clone().normalize().multiply(index * 0.25));
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, particleLocation, 1, 0.12, 0.12, 0.12, 0);
        }
        event.setCancelled(true);
    }

    private ItemStack getAirdashShield(Player player) {
        List<ItemStack> candidates = new ArrayList<>();
        candidates.add(player.getInventory().getItemInMainHand());
        candidates.add(player.getInventory().getItemInOffHand());
        for (ItemStack item : candidates) {
            if (item == null || item.getType() != Material.SHIELD) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(AIRDASH_KEY, PersistentDataType.STRING)) return item;
        }
        return null;
    }

    @EventHandler
    public void onAirdashLanding(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!airdashPlayers.contains(player.getUniqueId())) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            airdashPlayers.remove(player.getUniqueId());
            Location impact = player.getLocation();
            for (Entity nearby : player.getWorld().getNearbyEntities(impact, 3.5, 1.5, 3.5)) {
                if (nearby == player || !(nearby instanceof LivingEntity)) continue;
                pushEntity(nearby, player, 3.0);
            }
            player.getWorld().spawnParticle(Particle.EXPLOSION, impact, 8, 0.6, 0.2, 0.6, 0.1);
            player.getWorld().playSound(impact, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
        }
    }

    @EventHandler
    public void onAntiKinetic(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getCause() != EntityDamageEvent.DamageCause.FLY_INTO_WALL) return;
        Player player = (Player) event.getEntity();
        if (!hasSkill(player, ANTI_KINETIC_KEY)) return;
        double strength = Math.min(8.0, Math.max(1.0, event.getDamage() * 0.35));
        event.setCancelled(true);
        Location impact = player.getLocation().clone();
        double radius = Math.min(8.0, 2.5 + event.getDamage() * 0.15);
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 12) {
            for (double distance = 0.5; distance <= radius; distance += 0.5) {
                Location marker = impact.clone().add(Math.cos(angle) * distance, 0.15, Math.sin(angle) * distance);
                player.getWorld().spawnParticle(Particle.DUST, marker, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 80, 20), 0.8f));
            }
        }
        Material impactMaterial = player.getWorld().getBlockAt(impact).getType();
        if (impactMaterial.isBlock()) {
            player.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, impact, 30, 0.7, 0.2, 0.7, 0.1, impactMaterial.createBlockData());
        }
        player.getWorld().playSound(impact, Sound.ITEM_MACE_SMASH_AIR, 1.0f, 1.0f);
        for (Entity nearby : player.getWorld().getNearbyEntities(impact, radius, 2.0, radius)) {
            if (nearby == player || !(nearby instanceof LivingEntity)) continue;
            pushEntity(nearby, player, strength);
        }
        player.getWorld().spawnParticle(Particle.EXPLOSION, impact, 10, 0.5, 0.5, 0.5, 0.1);
    }

    @EventHandler
    public void onHellishFireImmunity(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FIRE &&
            cause != EntityDamageEvent.DamageCause.FIRE_TICK &&
            cause != EntityDamageEvent.DamageCause.LAVA &&
            cause != EntityDamageEvent.DamageCause.HOT_FLOOR) return;
        Player player = (Player) event.getEntity();
        Long ends = hellishFireImmunityEnds.get(player.getUniqueId());
        if (ends == null) return;
        if (ends > System.currentTimeMillis()) {
            event.setCancelled(true);
            player.setFireTicks(0);
        } else {
            hellishFireImmunityEnds.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onTrajectoryAim(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(TRAJECTORY_KEY, PersistentDataType.STRING)) return;
        Location point = event.getPlayer().getEyeLocation().clone();
        Vector direction = point.getDirection().normalize();
        for (int step = 1; step <= 24; step++) {
            point.add(direction.clone().multiply(0.45));
            event.getPlayer().getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
            direction.setY(direction.getY() - 0.015);
        }
    }

    @EventHandler
    public void onOmnipresencyShot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow)) return;
        Player player = (Player) event.getEntity();
        ItemStack weapon = event.getBow();
        ItemMeta meta = weapon == null ? null : weapon.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(OMNIPRESENCY_KEY, PersistentDataType.STRING)) return;
        Arrow arrow = (Arrow) event.getProjectile();
        arrow.setCustomName("OMNIPRESENCY_ARROW");
        arrow.setCustomNameVisible(false);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!arrow.isValid()) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(), 3, 0.12, 0.12, 0.12, 0.0, OMNIPRESENCY_DUST);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    @EventHandler
    public void onOmnipresencyHit(ProjectileHitEvent event) {
        if (!"OMNIPRESENCY_ARROW".equals(event.getEntity().getCustomName()) || !(event.getHitEntity() instanceof Entity)) return;
        Projectile arrowEntity = event.getEntity();
        Entity target = event.getHitEntity();
        if (!(arrowEntity.getShooter() instanceof Player)) return;
        Player shooter = (Player) arrowEntity.getShooter();
        Location shooterLocation = shooter.getLocation().clone();
        Location targetLocation = target.getLocation().clone();
        shooter.teleport(targetLocation);
        target.teleport(shooterLocation);
        target.getWorld().spawnParticle(Particle.DUST, targetLocation.add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.0, OMNIPRESENCY_DUST);
        target.getWorld().playSound(targetLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
        arrowEntity.remove();
    }

    @EventHandler
    public void onElektrofollowLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident)) return;
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player)) return;
        Player player = (Player) projectile.getShooter();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.TRIDENT) item = player.getInventory().getItemInOffHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(ELEKTROFOLLOW_KEY, PersistentDataType.STRING)) return;
        if (isOnCooldown(player, "Elektrofollow")) {
            return;
        }
        projectile.setCustomName("ELEKTROFOLLOW_TRIDENT");
        projectile.setCustomNameVisible(false);
        startCooldown(player, "Elektrofollow", ELEKTROFOLLOW_COOLDOWN);
    }

    @EventHandler
    public void onTelekinesisTargetDeath(EntityDeathEvent event) {
        for (Map.Entry<UUID, LivingEntity> entry : new ArrayList<>(telekinesisTargets.entrySet())) {
            if (entry.getValue() != event.getEntity()) continue;
            List<Arrow> darts = telekinesisDarts.remove(entry.getKey());
            if (darts != null) darts.forEach(arrow -> {
                if (arrow.isValid()) arrow.remove();
            });
            telekinesisTargets.remove(entry.getKey());
            telekinesisFallbackTargets.remove(entry.getKey());
            telekinesisCancelled.add(entry.getKey());
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack weapon = event.getInventory().getItem(0);
        ItemStack book = event.getInventory().getItem(1);

        if (weapon == null || book == null || book.getType() != Material.ENCHANTED_BOOK) return;

        ItemMeta bookMeta = book.getItemMeta();
        if (bookMeta == null) return;

        PersistentDataContainer bookData = bookMeta.getPersistentDataContainer();
        boolean beamBook = bookData.has(BEAM_BOOK_KEY, PersistentDataType.BYTE);
        boolean blackSpiralBook = bookData.has(BLACK_SPIRAL_BOOK_KEY, PersistentDataType.BYTE);
        boolean sweepBook = bookData.has(SWEEP_BOOK_KEY, PersistentDataType.BYTE);
        boolean kiloBook = bookData.has(KILO_BOOK_KEY, PersistentDataType.BYTE);
        boolean telekinesisBook = bookData.has(TELEKINESIS_BOOK_KEY, PersistentDataType.BYTE);
        boolean spadeBook = bookData.has(SPADE_BOOK_KEY, PersistentDataType.BYTE);
        boolean elektrofollowBook = bookData.has(ELEKTROFOLLOW_BOOK_KEY, PersistentDataType.BYTE);
        boolean trajectoryBook = bookData.has(TRAJECTORY_BOOK_KEY, PersistentDataType.BYTE);
        boolean hellishDominoBook = bookData.has(HELLISH_DOMINO_BOOK_KEY, PersistentDataType.BYTE);
        boolean omnipresencyBook = bookData.has(OMNIPRESENCY_BOOK_KEY, PersistentDataType.BYTE);
        boolean airdashBook = bookData.has(AIRDASH_BOOK_KEY, PersistentDataType.BYTE);
        boolean lowCBook = bookData.has(LOW_C_BOOK_KEY, PersistentDataType.BYTE);
        boolean antiKineticBook = bookData.has(ANTI_KINETIC_BOOK_KEY, PersistentDataType.BYTE);
        boolean elytraShulkerBook = bookData.has(ELYTRA_SHULKER_BOOK_KEY, PersistentDataType.BYTE);
        ItemStack result = weapon.clone();

        if (beamBook && weapon.getType() == Material.CROSSBOW) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(BEAM_KEY, PersistentDataType.STRING)) {
                applyBeamSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (blackSpiralBook && weapon.getType() == Material.CROSSBOW) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(BLACK_SPIRAL_KEY, PersistentDataType.STRING)) {
                applyBlackSpiralSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (sweepBook && weapon.getType().name().endsWith("_HOE")) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(SWEEP_KEY, PersistentDataType.STRING)) {
                applySweepSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (kiloBook && weapon.getType().name().endsWith("_AXE")) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(KILO_KEY, PersistentDataType.STRING)) {
                applyKiloSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (telekinesisBook && (weapon.getType() == Material.BOW || weapon.getType() == Material.CROSSBOW)) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(TELEKINESIS_KEY, PersistentDataType.STRING)) {
                applyTelekinesisSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (spadeBook && weapon.getType().name().endsWith("_SWORD")) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(SPADE_KEY, PersistentDataType.STRING)) {
                applySpadeSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (elektrofollowBook && weapon.getType() == Material.TRIDENT) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(ELEKTROFOLLOW_KEY, PersistentDataType.STRING)) {
                applyElektrofollowSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (trajectoryBook) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(TRAJECTORY_KEY, PersistentDataType.STRING)) {
                applyTrajectorySkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (hellishDominoBook && weapon.getType() == Material.SHIELD) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(HELLISH_DOMINO_KEY, PersistentDataType.STRING)) {
                applyHellishDominoSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (omnipresencyBook) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(OMNIPRESENCY_KEY, PersistentDataType.STRING)) {
                applyOmnipresencySkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (airdashBook && weapon.getType() == Material.SHIELD) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(AIRDASH_KEY, PersistentDataType.STRING)) {
                applyAirdashSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (lowCBook && isArmorItem(weapon.getType())) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(LOW_C_KEY, PersistentDataType.STRING)) {
                applyLowCSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (antiKineticBook && weapon.getType().name().endsWith("_HELMET")) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(ANTI_KINETIC_KEY, PersistentDataType.STRING)) {
                applyAntiKineticSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        } else if (elytraShulkerBook && weapon.getType() == Material.ELYTRA) {
            ItemMeta weaponMeta = weapon.getItemMeta();
            if (weaponMeta != null && !weaponMeta.getPersistentDataContainer().has(ELYTRA_SHULKER_KEY, PersistentDataType.STRING)) {
                applyElytraShulkerSkill(result);
                event.setResult(result);
                event.getInventory().setRepairCost(1);
            }
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        ItemStack bow = event.getBow();

        if (bow == null) return;

        ItemMeta meta = bow.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if ((bow.getType() == Material.BOW || bow.getType() == Material.CROSSBOW) &&
            container.has(TELEKINESIS_KEY, PersistentDataType.STRING)) {
            if (event.getProjectile() instanceof Arrow) {
                Arrow arrow = (Arrow) event.getProjectile();
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.setCustomName("TELEKINESIS_ARROW");
                arrow.setCustomNameVisible(false);
                startTelekinesisFlight(arrow);
            }
            if (!container.has(BEAM_KEY, PersistentDataType.STRING)) return;
        }

        if ((bow.getType() == Material.BOW || bow.getType() == Material.CROSSBOW) &&
            container.has(TRAJECTORY_KEY, PersistentDataType.STRING) && event.getProjectile() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getProjectile();
            arrow.setDamage(arrow.getDamage() * 1.5);
            arrow.setVelocity(arrow.getVelocity().multiply(1.35));
        }

        if (bow.getType() == Material.CROSSBOW && container.has(BLACK_SPIRAL_KEY, PersistentDataType.STRING) &&
            event.getProjectile() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getProjectile();
            arrow.setDamage(0.0);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setCustomName("BLACK_SPIRAL_ARROW");
            arrow.setCustomNameVisible(false);
            startBlackSpiralTrail(arrow);
            return;
        }

        if (!container.has(BEAM_KEY, PersistentDataType.STRING)) return;

        // Verificar cooldown
        long currentTime = System.currentTimeMillis();
        if (isOnCooldown(player, "Beam")) return;

        // Marcar la flecha como flecha láser
        if (event.getProjectile() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getProjectile();
            if (projectile instanceof AbstractArrow) ((AbstractArrow) projectile).setDamage(0.0);
            projectile.setCustomName("LASER_ARROW");
            projectile.setCustomNameVisible(false);
        }

        crossbowCooldowns.put(player.getUniqueId(), currentTime);
        startCooldown(player, "Beam", CROSSBOW_COOLDOWN);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();

        if ("BLACK_SPIRAL_ARROW".equals(projectile.getCustomName())) {
            createBlackSpiral(projectile, event.getHitEntity());
            projectile.remove();
            return;
        }

        if ("HELLISH_FIREBALL".equals(projectile.getCustomName())) {
            UUID ownerId = projectile.getShooter() instanceof Player
                ? ((Player) projectile.getShooter()).getUniqueId() : null;
            if (ownerId != null && event.getHitEntity() instanceof LivingEntity) {
                hellishTargets.put(ownerId, (LivingEntity) event.getHitEntity());
            }
            Location hit = projectile.getLocation();
            hit.getWorld().spawnParticle(Particle.EXPLOSION, hit, 4, 0.2, 0.2, 0.2, 0.05);
            hit.getWorld().playSound(hit, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
            projectile.remove();
            return;
        }

        if ("ELEKTROFOLLOW_TRIDENT".equals(projectile.getCustomName())) {
            createElektrofollowPaths(projectile, event.getHitEntity());
            return;
        }

        if ("TELEKINESIS_ARROW".equals(projectile.getCustomName())) {
            handleTelekinesisImpact(projectile, event.getHitEntity());
            projectile.remove();
            return;
        }

        if ("TELEKINESIS_DART".equals(projectile.getCustomName())) {
            Location hitLocation = projectile.getLocation();
            World world = hitLocation.getWorld();
            world.spawnParticle(Particle.CRIT, hitLocation, 14, 0.25, 0.25, 0.25, 0.1);
            world.playSound(hitLocation, event.getHitEntity() == null
                ? Sound.ENTITY_ARROW_HIT : Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.2f);
            projectile.remove();
            return;
        }
        
        if (!"LASER_ARROW".equals(projectile.getCustomName())) return;

        // Crear efecto de explosión de balas de shulker
        Location hitLoc = event.getEntity().getLocation();
        World world = hitLoc.getWorld();
        if (event.getHitEntity() instanceof LivingEntity && projectile.getShooter() instanceof Player) {
            ((LivingEntity) event.getHitEntity()).damage(20.0, (Player) projectile.getShooter());
        }

        // Efecto visual del rayo láser
        world.spawnParticle(Particle.END_ROD, hitLoc, 50, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.EXPLOSION, hitLoc, 10, 0.5, 0.5, 0.5, 0.1);
        world.playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        // Generar esfera de balas de shulker
        new BukkitRunnable() {
            int count = 0;
            final double radius = 3.0;
            final int bulletsPerLayer = 12;
            final int layers = 3;

            @Override
            public void run() {
                if (count >= layers) {
                    this.cancel();
                    return;
                }

                double currentRadius = radius * ((count + 1) / (double) layers);
                
                for (int i = 0; i < bulletsPerLayer; i++) {
                    double angle = (2 * Math.PI * i) / bulletsPerLayer;
                    double x = hitLoc.getX() + currentRadius * Math.cos(angle);
                    double y = hitLoc.getY() + (count * 1.5) - 1.5;
                    double z = hitLoc.getZ() + currentRadius * Math.sin(angle);
                    
                    Location bulletLoc = new Location(world, x, y, z);
                    ShulkerBullet bullet = world.spawn(bulletLoc, ShulkerBullet.class);
                    
                    // Hacer que las balas apunten hacia el centro de la explosión
                    Vector direction = hitLoc.toVector().subtract(bulletLoc.toVector()).normalize();
                    // Usar el método correcto para establecer dirección
                    bullet.setVelocity(direction.multiply(0.5));
                    
                    // Marcar la bala para que no cause daño excesivo
                    bullet.setCustomName("SHULKER_EXPLOSION");
                    bullet.setCustomNameVisible(false);
                }

                count++;
            }
        }.runTaskTimer(this, 0L, 5L); // Cada 5 ticks (0.25 segundos)

        // Eliminar las balas de shulker después de un tiempo
        new BukkitRunnable() {
            @Override
            public void run() {
                world.getEntitiesByClass(ShulkerBullet.class).stream()
                    .filter(bullet -> bullet.getCustomName() != null && bullet.getCustomName().equals("SHULKER_EXPLOSION"))
                    .forEach(Entity::remove);
            }
        }.runTaskLater(this, 100L); // 5 segundos
    }

    private void startTelekinesisFlight(Arrow initial) {
        UUID id = initial.getUniqueId();
        List<Arrow> darts = new ArrayList<>();
        telekinesisDarts.put(id, darts);
        Vector flightDirection = initial.getVelocity().clone();
        if (flightDirection.lengthSquared() < 0.01) flightDirection = new Vector(0, 0, 1);
        Vector baseDirection = flightDirection.normalize();

        new BukkitRunnable() {
            int ticks;
            double speed = 0.1;

            @Override
            public void run() {
                LivingEntity target = telekinesisTargets.get(id);
                if (telekinesisCancelled.contains(id)) {
                    darts.forEach(arrow -> {
                        if (arrow.isValid()) arrow.remove();
                    });
                    telekinesisDarts.remove(id);
                    telekinesisSpawned.remove(id);
                    telekinesisCancelled.remove(id);
                    cancel();
                    return;
                }
                if (target != null && target.isDead()) {
                    darts.forEach(arrow -> {
                        if (arrow.isValid()) arrow.remove();
                    });
                    telekinesisDarts.remove(id);
                    telekinesisSpawned.remove(id);
                    telekinesisTargets.remove(id);
                    telekinesisFallbackTargets.remove(id);
                    cancel();
                    return;
                }
                Location targetLocation = target != null && target.isValid()
                    ? target.getLocation().add(0, 0.8, 0)
                    : telekinesisFallbackTargets.get(id);

                if (!initial.isValid() && targetLocation == null) {
                    targetLocation = initial.getLocation().clone().add(baseDirection.clone().multiply(12));
                    telekinesisFallbackTargets.put(id, targetLocation);
                }

                if (initial.isValid() && ticks % 2 == 0 &&
                    telekinesisSpawned.getOrDefault(id, 0) < TELEKINESIS_MAX_DARTS) {
                    spawnTelekinesisDart(initial.getLocation(), baseDirection, darts);
                }

                darts.removeIf(arrow -> !arrow.isValid());
                if (targetLocation != null) {
                    speed = Math.min(4.0, speed + 0.1);
                    for (Arrow dart : darts) {
                        Vector direction = targetLocation.toVector().subtract(dart.getLocation().toVector());
                        if (direction.lengthSquared() < 0.01) continue;
                        dart.setVelocity(direction.normalize().multiply(speed));
                    }
                }

                ticks++;
                if (ticks >= 120 || (!initial.isValid() && darts.isEmpty())) {
                    darts.forEach(arrow -> {
                        if (arrow.isValid()) arrow.remove();
                    });
                    telekinesisDarts.remove(id);
                    telekinesisSpawned.remove(id);
                    telekinesisTargets.remove(id);
                    telekinesisFallbackTargets.remove(id);
                    cancel();
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private void handleTelekinesisImpact(Projectile source, Entity hitEntity) {
        UUID id = source.getUniqueId();
        List<Arrow> darts = telekinesisDarts.computeIfAbsent(id, key -> new ArrayList<>());
        if (hitEntity instanceof LivingEntity) {
            telekinesisTargets.put(id, (LivingEntity) hitEntity);
        } else {
            Vector direction = source.getVelocity().clone();
            if (direction.lengthSquared() < 0.01) direction = new Vector(0, 0, 1);
            telekinesisFallbackTargets.put(id, source.getLocation().clone().add(direction.normalize().multiply(12)));
        }
        // Las flechas restantes se generan progresivamente durante el vuelo inicial.
    }

    private void spawnTelekinesisDart(Location location, Vector direction, List<Arrow> darts) {
        Vector normalized = direction.clone();
        if (normalized.lengthSquared() < 0.01) normalized = new Vector(0, 0, 1);
        Arrow dart = location.getWorld().spawnArrow(location, normalized.normalize(), 0.1f, 0.0f);
        dart.setGravity(false);
        dart.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        dart.setDamage(4.0);
        dart.setCustomName("TELEKINESIS_DART");
        dart.setCustomNameVisible(false);
        darts.add(dart);
        UUID initialId = null;
        for (Map.Entry<UUID, List<Arrow>> entry : telekinesisDarts.entrySet()) {
            if (entry.getValue() == darts) {
                initialId = entry.getKey();
                break;
            }
        }
        if (initialId != null) {
            telekinesisSpawned.put(initialId, telekinesisSpawned.getOrDefault(initialId, 0) + 1);
        }
    }

    private void createElektrofollowPaths(Projectile projectile, Entity hitEntity) {
        Location origin = projectile.getLocation();
        World world = origin.getWorld();
        LivingEntity owner = projectile.getShooter() instanceof LivingEntity
            ? (LivingEntity) projectile.getShooter() : null;
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity nearby : world.getNearbyEntities(origin, 12, 8, 12)) {
            if (!(nearby instanceof LivingEntity) || nearby == owner || nearby == hitEntity) continue;
            targets.add((LivingEntity) nearby);
        }
        if (hitEntity instanceof LivingEntity) targets.add(0, (LivingEntity) hitEntity);
        for (LivingEntity target : targets) {
            Location end = target.getLocation();
            Vector path = end.toVector().subtract(origin.toVector());
            double distance = path.length();
            if (distance < 0.5) continue;
            Vector step = path.normalize();
            int points = Math.min(24, Math.max(1, (int) (distance / 0.8)));
            for (int index = 1; index <= points; index++) {
                Location fangLocation = origin.clone().add(step.clone().multiply(index * distance / points));
                EvokerFangs fangs = world.spawn(fangLocation, EvokerFangs.class);
                fangs.setOwner(owner);
                fangs.setAttackDelay(Math.min(20, index));
            }
        }
        world.spawnParticle(Particle.ELECTRIC_SPARK, origin, 30, 0.5, 0.5, 0.5, 0.15);
        world.playSound(origin, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.2f, 0.8f);
    }

    private void startBlackSpiralTrail(Arrow arrow) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!arrow.isValid()) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(), 4,
                    0.06, 0.06, 0.06, 0.0, BLACK_SPIRAL_DUST);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void createBlackSpiral(Projectile projectile, Entity hitEntity) {
        Location center = projectile.getLocation();
        World world = center.getWorld();
        LivingEntity target = hitEntity instanceof LivingEntity ? (LivingEntity) hitEntity : null;

        if (target == null) {
            double nearest = 10.0 * 10.0;
            for (Entity nearby : world.getNearbyEntities(center, 10, 10, 10)) {
                if (!(nearby instanceof LivingEntity) || nearby == projectile.getShooter()) continue;
                double distance = nearby.getLocation().distanceSquared(center);
                if (distance < nearest) {
                    nearest = distance;
                    target = (LivingEntity) nearby;
                }
            }
        }

        for (int index = 0; index < 48; index++) {
            double angle = index * (Math.PI * 2.0 / 48.0);
            double radius = 1.6;
            Location ringPoint = center.clone().add(Math.cos(angle) * radius, 0.8, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, ringPoint, 2, 0.05, 0.05, 0.05, 0.0, BLACK_SPIRAL_DUST);
            if (target != null) spawnBlackSpiralBullet(ringPoint, target);
        }

        for (int index = 0; index < 32; index++) {
            double progress = index / 31.0;
            double angle = progress * Math.PI * 6.0;
            double radius = 0.25 + progress * 1.5;
            Location spiralPoint = center.clone().add(
                Math.cos(angle) * radius,
                0.2 + progress * 1.2,
                Math.sin(angle) * radius
            );
            world.spawnParticle(Particle.DUST, spiralPoint, 3, 0.04, 0.04, 0.04, 0.0, BLACK_SPIRAL_DUST);
            if (target != null) spawnBlackSpiralBullet(spiralPoint, target);
        }
        world.playSound(center, Sound.ENTITY_SHULKER_SHOOT, 1.5f, 0.6f);
    }

    private void spawnBlackSpiralBullet(Location location, LivingEntity target) {
        ShulkerBullet bullet = location.getWorld().spawn(location, ShulkerBullet.class);
        bullet.setTarget(target);
        bullet.setCustomName("BLACK_SPIRAL_BULLET");
        bullet.setCustomNameVisible(false);
    }

    private void unleashHellishDomino(Player player) {
        UUID ownerId = player.getUniqueId();
        hellishFireImmunityEnds.put(ownerId, System.currentTimeMillis() + 5000L);
        List<SmallFireball> fireballs = new ArrayList<>();
        hellishFireballs.put(ownerId, fireballs);
        Location center = player.getLocation().add(0, 1.2, 0);
        int count = 100;
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int index = 0; index < count; index++) {
            double y = 1.0 - (index / (double) (count - 1)) * 2.0;
            double radius = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = goldenAngle * index;
            Vector direction = new Vector(Math.cos(theta) * radius, y, Math.sin(theta) * radius).normalize();
            SmallFireball fireball = player.getWorld().spawn(center.clone().add(direction.clone().multiply(0.8)), SmallFireball.class);
            fireball.setDirection(direction);
            fireball.setCustomName("HELLISH_FIREBALL");
            fireball.setCustomNameVisible(false);
            fireballs.add(fireball);
        }
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                LivingEntity target = hellishTargets.get(ownerId);
                if (!player.isOnline() || ticks++ >= 100 || fireballs.stream().noneMatch(Entity::isValid)) {
                    fireballs.forEach(ball -> {
                        if (ball.isValid()) ball.remove();
                    });
                    hellishFireballs.remove(ownerId);
                    hellishTargets.remove(ownerId);
                    cancel();
                    return;
                }
                if (target != null && target.isValid()) {
                    for (SmallFireball fireball : fireballs) {
                        if (!fireball.isValid()) continue;
                        Vector direction = target.getLocation().add(0, 0.8, 0).toVector()
                            .subtract(fireball.getLocation().toVector());
                        if (direction.lengthSquared() > 0.01) fireball.setDirection(direction.normalize());
                    }
                }
            }
        }.runTaskTimer(this, 1L, 1L);
        player.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.6f);
    }
}
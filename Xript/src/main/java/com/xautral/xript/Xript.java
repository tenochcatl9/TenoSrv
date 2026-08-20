package com.xautral.xript;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.*;

public class Xript extends JavaPlugin implements Listener {

    private static final PotionEffectType[] EFFECTS = {
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS, PotionEffectType.HUNGER, PotionEffectType.BLINDNESS,
            PotionEffectType.LEVITATION, PotionEffectType.MINING_FATIGUE, PotionEffectType.GLOWING,
            PotionEffectType.DARKNESS, PotionEffectType.NAUSEA, PotionEffectType.UNLUCK
    };
    private static final PotionEffectType[] BUFFS = {
            PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.REGENERATION,
            PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE, PotionEffectType.INVISIBILITY,
            PotionEffectType.SLOW_FALLING, PotionEffectType.JUMP_BOOST, PotionEffectType.HASTE, PotionEffectType.LUCK
    };
    private static final EntityType[] PROJECTILES = {
            EntityType.ARROW, EntityType.SNOWBALL, EntityType.EGG, EntityType.FIREBALL,
            EntityType.SMALL_FIREBALL, EntityType.WITHER_SKULL, EntityType.LLAMA_SPIT,
            EntityType.TRIDENT, EntityType.SHULKER_BULLET
    };
    private static final String[] PATTERNS = {"LINE", "RING", "FAN"};
    private static final Particle[] PARTICLES = {
            Particle.FLAME, Particle.CRIT, Particle.ENCHANT, Particle.HEART,
            Particle.ENTITY_EFFECT, Particle.TOTEM_OF_UNDYING, Particle.SOUL_FIRE_FLAME,
            Particle.ELECTRIC_SPARK, Particle.CLOUD, Particle.SOUL, Particle.CAMPFIRE_COSY_SMOKE
    };

    private NamespacedKey actionKey;
    private NamespacedKey xriptKey;

    private final Map<UUID, EditSession> sessions = new HashMap<>();
    private final Map<UUID, String> pendingInputs = new HashMap<>();
    private final Set<UUID> preserve = new HashSet<>();
    private final Map<UUID, Long> riptideCooldown = new HashMap<>();
    private final Map<UUID, Integer> riptideCharging = new HashMap<>();
    private final Map<UUID, UUID> homing = new HashMap<>();
    private final Map<UUID, BowState> bowTrack = new HashMap<>();
    private final Map<UUID, Integer> enchantPages = new HashMap<>();

    @Override
    public void onEnable() {
        actionKey = new NamespacedKey(this, "action");
        xriptKey = new NamespacedKey(this, "xript");
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String cmdName : List.of("ie", "xript")) {
            PluginCommand cmd = getCommand(cmdName);
            if (cmd != null) cmd.setExecutor(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("xautral.op")) return true;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(color("&cPon un ítem en tu mano."));
            return true;
        }
        EditSession s = new EditSession();
        s.item = hand.clone();
        s.original = hand.clone();
        XriptConfig existing = readXript(hand);
        if (existing != null) s.xript = existing;
        sessions.put(player.getUniqueId(), s);
        openEditor(player, s);
        return true;
    }

    // ---------------- GUI ----------------

    private void openEditor(Player player, EditSession s) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Editor de Ítems"));
        for (int i = 0; i < 54; i++) inv.setItem(i, button(Material.BLACK_STAINED_GLASS_PANE, " ", "none"));

        ItemStack preview = s.item.clone();
        ItemMeta pm = preview.getItemMeta();
        List<String> plore = pm.getLore() == null ? new ArrayList<>() : new ArrayList<>(pm.getLore());
        plore.add(color("&7&o— ítem en edición —"));
        pm.setLore(plore);
        preview.setItemMeta(pm);
        inv.setItem(4, preview);

        inv.setItem(20, button(Material.NAME_TAG, color("&6&lCambiar Nombre"),
                List.of(color("&7Escribe el nuevo nombre en el chat")), "name"));
        inv.setItem(21, button(Material.PAPER, color("&6&lCambiar Lore"),
                List.of(color("&7Escribe las líneas separadas por &f|")), "lore"));
        inv.setItem(22, button(Material.BOOK, color("&6&lXript"),
                List.of(color("&7Atributos especiales del ítem")), "xript"));
        inv.setItem(23, button(Material.GHAST_TEAR, color("&6&lCustom Model"),
                List.of(color("&7Escribe el número de custom model en el chat")), "custommodel"));
        inv.setItem(24, button(Material.ITEM_FRAME, color("&6&lItem Model"),
                List.of(color("&7Escribe el resource location (ej: &fminecraft:wooden_sword&7)")), "itemmodel"));

        Material unbreak = s.item.getItemMeta().isUnbreakable() ? Material.LIME_DYE : Material.GRAY_DYE;
        inv.setItem(25, toggle(unbreak, "Irrompible", "unbreakable", s.item.getItemMeta().isUnbreakable()));
        inv.setItem(26, toggle(Material.BARRIER, "Ocultar Atributos", "hideattr",
                s.item.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)));

        inv.setItem(45, button(Material.ENCHANTED_BOOK, color("&6&lEncantamientos"),
                List.of(color("&7Clic para añadir encantamientos con niveles")), "enchants"));

        inv.setItem(48, button(Material.ORANGE_DYE, color("&6&lReiniciar"),
                List.of(color("&7Vuelve al ítem original")), "reset"));
        inv.setItem(49, button(Material.LIME_DYE, color("&a&lGUARDAR"), Collections.emptyList(), "save"));
        inv.setItem(53, button(Material.RED_DYE, color("&c&lCANCELAR"), Collections.emptyList(), "cancel"));

        switchTo(player, inv);
    }

    private void openXript(Player player, EditSession s) {
        XriptConfig c = s.xript;
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Xript"));
        for (int i = 0; i < 54; i++) inv.setItem(i, button(Material.BLACK_STAINED_GLASS_PANE, " ", "none"));

        inv.setItem(4, button(Material.NETHER_STAR, color("&b&lCONFIGURACIÓN XRIPT"),
                List.of(color("&7Cada atributo se cambia con clic"), color("&7El lore se actualiza al guardar"))));

        inv.setItem(10, xriptButton(Material.POTION, "Al Dañar: Efecto", "eff",
                c.dmgEffect == null ? "Ninguno" : c.dmgEffect.getKey().getKey()));
        inv.setItem(11, xriptButton(Material.ARROW, "Al Dañar: Proyectil", "proj",
                c.dmgProjectile == null ? "Ninguno" : c.dmgProjectile.name()));
        inv.setItem(12, xriptButton(Material.TNT, "Al Dañar: Nº Proyectiles", "projcount", String.valueOf(c.dmgProjectileCount)));
        inv.setItem(13, xriptButton(Material.COMPASS, "Al Dañar: Patrón", "pattern", c.dmgPattern));
        inv.setItem(14, xriptToggle(Material.FLINT_AND_STEEL, "Al Dañar: Fuego", "dmgfire", c.dmgFire));
        inv.setItem(15, xriptToggle(Material.SLIME_BLOCK, "Al Dañar: Lanzar al aire", "dmglaunch", c.dmgLaunch));

        inv.setItem(16, xriptToggle(Material.TRIDENT, "Click Der: Riptide", "riptide", c.rcRiptide));
        inv.setItem(17, xriptButton(Material.FIREWORK_STAR, "Click Der: Partícula", "particle",
                c.rcParticle == null ? "Ninguna" : c.rcParticle.getKey().getKey()));
        inv.setItem(18, xriptButton(Material.SNOWBALL, "Click Der: Proyectil", "rcproj",
                c.rcProjectile == null ? "Ninguno" : c.rcProjectile.name()));
        inv.setItem(19, xriptButton(Material.TNT, "Click Der: Nº Proyectiles", "rcprojcount", String.valueOf(c.rcProjectileCount)));
        inv.setItem(20, xriptButton(Material.EVOKER_SPAWN_EGG, "Click Der: Fangos (distancia)", "fangs",
                c.rcFangsRange == 0 ? "Off" : String.valueOf(c.rcFangsRange)));
        inv.setItem(21, xriptToggle(Material.ENDER_PEARL, "Click Der: Proyectiles guiados", "guided", c.rcGuided));

        inv.setItem(28, xriptButton(Material.FEATHER, "Daño Máx por Vel. Vertical", "vv", String.valueOf(c.maxVvDamage)));
        inv.setItem(29, xriptButton(Material.SUGAR, "Daño Máx por Vel. Horizontal", "vh", String.valueOf(c.maxVhDamage)));
        inv.setItem(30, xriptButton(Material.RABBIT_FOOT, "Salto: Impulso", "jump", String.valueOf(c.jumpBoost)));
        inv.setItem(31, xriptToggle(Material.ENDER_EYE, "Ocultar lore de Xript", "hidelore", c.hideLore));
        inv.setItem(32, xriptButton(Material.FIREWORK_ROCKET, "Al Matar: Explosión", "kille", String.valueOf(c.killExplosion)));
        inv.setItem(33, xriptButton(Material.DIAMOND_SWORD, "Al Matar: Efecto", "killeff",
                c.killEffect == null ? "Ninguno" : c.killEffect.getKey().getKey()));
        inv.setItem(34, xriptButton(Material.SHIELD, "Al Agacharse: Efecto", "sneakeff",
                c.sneakEffect == null ? "Ninguno" : c.sneakEffect.getKey().getKey()));
        inv.setItem(35, xriptButton(Material.GLOWSTONE_DUST, "Al Agacharse: Partícula", "sneakp",
                c.sneakParticle == null ? "Ninguna" : c.sneakParticle.getKey().getKey()));

        if (isBow(s.item)) {
            inv.setItem(25, button(Material.BOW, color("&6&lConfig Arco"),
                    List.of(color("&7Opciones especiales de arco/crossbow")), "bow"));
        }

        inv.setItem(48, button(Material.ARROW, color("&7Volver"),
                List.of(color("&7Vuelve al editor")), "back"));
        inv.setItem(49, button(Material.LIME_DYE, color("&a&lGUARDAR"), Collections.emptyList(), "save"));
        inv.setItem(53, button(Material.RED_DYE, color("&c&lCANCELAR"), Collections.emptyList(), "cancel"));

        switchTo(player, inv);
    }

    private void openBowGui(Player player, EditSession s) {
        XriptConfig c = s.xript;
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Xript Arco"));
        for (int i = 0; i < 54; i++) inv.setItem(i, button(Material.BLACK_STAINED_GLASS_PANE, " ", "none"));

        inv.setItem(4, button(Material.BOW, color("&b&lCONFIGURACIÓN ARCO"),
                List.of(color("&7Clic normal: cambia el valor"),
                        color("&7Clic central (rueda): escribe el valor en el chat"))));

        inv.setItem(10, xriptButton(Material.ARROW, "Reemplazar flecha: Proyectil", "bowproj",
                c.bowProjectile == null ? "Ninguno (flecha)" : c.bowProjectile.name()));
        inv.setItem(11, xriptButton(Material.FIREWORK_STAR, "Estela: Partícula", "bowpart",
                c.bowTrailParticle == null ? "Ninguna" : c.bowTrailParticle.getKey().getKey()));
        inv.setItem(12, xriptButton(Material.SNOWBALL, "Estela: Proyectil", "bowtproj",
                c.bowTrailProjectile == null ? "Ninguno" : c.bowTrailProjectile.name()));
        inv.setItem(13, xriptButton(Material.TNT, "Estela: Nº proyectiles", "bowtcount", String.valueOf(c.bowTrailProjectileCount)));
        inv.setItem(14, xriptButton(Material.EVOKER_SPAWN_EGG, "Estela: Fangos (dist.)", "bowtfangs",
                c.bowTrailFangs == 0 ? "Off" : String.valueOf(c.bowTrailFangs)));
        inv.setItem(15, xriptButton(Material.COMPASS, "Estela: Patrón", "bowtpat", c.bowTrailPattern));

        inv.setItem(19, xriptButton(Material.LEVER, "Al golpear: Filtro", "bowfilter",
                bowFilterName(c.bowHitFilter)));
        inv.setItem(20, xriptButton(Material.NETHER_STAR, "Al golpear: Efecto", "bowmode",
                bowModeName(c.bowHitMode)));
        inv.setItem(21, xriptButton(Material.TNT, "Al golpear: Cantidad", "bowcount", String.valueOf(c.bowHitCount)));
        inv.setItem(22, xriptButton(Material.SNOWBALL, "Al golpear: Proyectil", "bowhitproj",
                c.bowHitProjectile == null ? "Ninguno" : c.bowHitProjectile.name()));

        inv.setItem(48, button(Material.ARROW, color("&7Volver"),
                List.of(color("&7Vuelve a Xript")), "back"));
        inv.setItem(49, button(Material.LIME_DYE, color("&a&lGUARDAR"), Collections.emptyList(), "save"));
        inv.setItem(53, button(Material.RED_DYE, color("&c&lCANCELAR"), Collections.emptyList(), "cancel"));

        switchTo(player, inv);
    }

    private void switchTo(Player player, Inventory inv) {
        UUID pid = player.getUniqueId();
        preserve.add(pid);
        player.openInventory(inv);
        preserve.remove(pid);
    }

    private void openEnchantGui(Player player, EditSession s, int page) {
        UUID pid = player.getUniqueId();
        List<Enchantment> all = Arrays.stream(Enchantment.values())
                .sorted(Comparator.comparing(en -> en.getKey().getKey()))
                .toList();
        int pages = Math.max(1, (int) Math.ceil(all.size() / 45.0));
        page = Math.max(1, Math.min(page, pages));
        enchantPages.put(pid, page);

        Inventory inv = Bukkit.createInventory(null, 54, color("&8Encantamientos"));
        for (int i = 0; i < 54; i++) inv.setItem(i, button(Material.BLACK_STAINED_GLASS_PANE, " ", "none"));

        int start = (page - 1) * 45;
        for (int i = 0; i < 45 && start + i < all.size(); i++) {
            inv.setItem(i, enchantButton(s, all.get(start + i)));
        }

        inv.setItem(45, button(Material.ARROW, color("&7◀ Anterior"), List.of(), "eprev"));
        inv.setItem(46, button(Material.BOOK, color("&fPágina &b" + page + "&f/&b" + pages), List.of(), "none"));
        inv.setItem(47, button(Material.ARROW, color("&7Siguiente ▶"), List.of(), "enext"));
        inv.setItem(49, button(Material.ORANGE_DYE, color("&6&lVolver"),
                List.of(color("&7Vuelve al editor")), "back"));
        inv.setItem(53, button(Material.RED_DYE, color("&c&lCANCELAR"), List.of(), "cancel"));

        switchTo(player, inv);
    }

    private ItemStack enchantButton(EditSession s, Enchantment en) {
        int level = s.item.getItemMeta().getEnchantLevel(en);
        ItemStack item = new ItemStack(level > 0 ? Material.ENCHANTED_BOOK : Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&e" + en.getKey().getKey()));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Nivel: &f" + (level > 0 ? level : "Sin encantar") + " &8(max " + en.getMaxLevel() + ")"));
        lore.add(color("&7Clic izq: +1 nivel"));
        lore.add(color("&7Clic der: -1 nivel"));
        lore.add(color("&7Rueda: escribir nivel"));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "ench_" + en.getKey().toString());
        item.setItemMeta(meta);
        return item;
    }

    private void handleEnchantAction(Player player, EditSession s, String action, ClickType click) {
        UUID pid = player.getUniqueId();
        if (action.startsWith("ench_")) {
            String keyStr = action.substring(5);
            Enchantment en = Enchantment.getByKey(NamespacedKey.fromString(keyStr));
            if (en == null) return;
            if (click == ClickType.MIDDLE) {
                pendingInputs.put(pid, "ench:" + en.getKey().toString());
                String finalMsg = "&eEscribe el nivel del encantamiento:";
                Bukkit.getScheduler().runTask(this, () -> {
                    player.closeInventory();
                    player.sendMessage(color(finalMsg));
                });
                return;
            }
            ItemMeta meta = s.item.getItemMeta();
            int level = meta.getEnchantLevel(en);
            if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                level--;
                if (level <= 0) meta.removeEnchant(en);
                else meta.addEnchant(en, level, true);
            } else {
                level++;
                if (level > en.getMaxLevel()) level = en.getMaxLevel();
                meta.addEnchant(en, level, true);
            }
            s.item.setItemMeta(meta);
            openEnchantGui(player, s, enchantPages.getOrDefault(pid, 1));
            return;
        }
        switch (action) {
            case "eprev" -> openEnchantGui(player, s, enchantPages.getOrDefault(pid, 1) - 1);
            case "enext" -> openEnchantGui(player, s, enchantPages.getOrDefault(pid, 1) + 1);
            case "back" -> openEditor(player, s);
            case "save" -> {
                saveToHand(player, s);
                sessions.remove(pid);
                player.closeInventory();
                player.sendMessage(color("&aÍtem guardado."));
            }
            case "cancel" -> {
                sessions.remove(pid);
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID pid = player.getUniqueId();
        EditSession s = sessions.get(pid);
        if (s == null) return;
        event.setCancelled(true);

        String title = event.getView().getTitle();
        String action = getAction(event.getCurrentItem());
        if (action == null || action.equals("none")) return;

        if (title.contains("Encantamientos")) {
            handleEnchantAction(player, s, action, event.getClick());
        } else if (title.contains("Arco")) {
            handleBowAction(player, s, action, event.getClick() == ClickType.MIDDLE);
        } else if (title.contains("Xript")) {
            handleXriptAction(player, s, action, event.getClick() == ClickType.MIDDLE);
        } else {
            handleEditorAction(player, s, action);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            UUID pid = player.getUniqueId();
            if (preserve.contains(pid) || pendingInputs.containsKey(pid)) return;
            sessions.remove(pid);
        }
    }

    // ---------------- Editor actions ----------------

    private void handleEditorAction(Player player, EditSession s, String action) {
        UUID pid = player.getUniqueId();
        switch (action) {
            case "unbreakable" -> {
                ItemMeta meta = s.item.getItemMeta();
                meta.setUnbreakable(!meta.isUnbreakable());
                s.item.setItemMeta(meta);
                player.sendMessage(color("&aIrrompible actualizado."));
                openEditor(player, s);
            }
            case "reset" -> {
                s.item = s.original.clone();
                player.sendMessage(color("&7Ítem reiniciado."));
                openEditor(player, s);
            }
            case "xript" -> openXript(player, s);
            case "enchants" -> openEnchantGui(player, s, 1);
            case "hideattr" -> {
                ItemMeta meta = s.item.getItemMeta();
                if (meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)) meta.removeItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                else meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                s.item.setItemMeta(meta);
                player.sendMessage(color("&aAtributos ocultos actualizado."));
                openEditor(player, s);
            }
            case "name", "lore", "custommodel", "itemmodel" -> {
                pendingInputs.put(pid, action);
                String msg = switch (action) {
                    case "name" -> "&eEscribe el nuevo nombre en el chat:";
                    case "lore" -> "&eEscribe el lore (líneas separadas por &f|&e):";
                    case "custommodel" -> "&eEscribe el número de custom model (0 para quitar):";
                    default -> "&eEscribe el resource location del item model:";
                };
                String finalMsg = msg;
                Bukkit.getScheduler().runTask(this, () -> {
                    player.closeInventory();
                    player.sendMessage(color(finalMsg));
                });
            }
            case "save" -> {
                saveToHand(player, s);
                sessions.remove(pid);
                player.closeInventory();
                player.sendMessage(color("&aÍtem guardado."));
            }
            case "cancel" -> {
                sessions.remove(pid);
                player.closeInventory();
            }
        }
    }

    private void handleXriptAction(Player player, EditSession s, String action, boolean middle) {
        XriptConfig c = s.xript;
        UUID pid = player.getUniqueId();
        if (middle && isTypableXript(action)) {
            promptTypeValue(player, "xript:" + action);
            return;
        }
        switch (action) {
            case "eff" -> c.dmgEffect = cycle(c.dmgEffect, EFFECTS);
            case "proj" -> c.dmgProjectile = cycle(c.dmgProjectile, PROJECTILES);
            case "projcount" -> c.dmgProjectileCount = cycleInt(c.dmgProjectileCount, 8);
            case "pattern" -> c.dmgPattern = PATTERNS[cycleIdx(PATTERNS, c.dmgPattern)];
            case "dmgfire" -> c.dmgFire = !c.dmgFire;
            case "dmglaunch" -> c.dmgLaunch = !c.dmgLaunch;
            case "riptide" -> c.rcRiptide = !c.rcRiptide;
            case "particle" -> c.rcParticle = cycle(c.rcParticle, PARTICLES);
            case "rcproj" -> c.rcProjectile = cycle(c.rcProjectile, PROJECTILES);
            case "rcprojcount" -> c.rcProjectileCount = cycleInt(c.rcProjectileCount, 8);
            case "fangs" -> c.rcFangsRange = cycleInt(c.rcFangsRange, 10);
            case "guided" -> c.rcGuided = !c.rcGuided;
            case "vv" -> c.maxVvDamage = cycleInt(c.maxVvDamage, 20);
            case "vh" -> c.maxVhDamage = cycleInt(c.maxVhDamage, 20);
            case "jump" -> c.jumpBoost = cycleInt(c.jumpBoost, 5);
            case "kille" -> c.killExplosion = cycleInt(c.killExplosion, 5);
            case "killeff" -> c.killEffect = cycle(c.killEffect, BUFFS);
            case "sneakeff" -> c.sneakEffect = cycle(c.sneakEffect, BUFFS);
            case "sneakp" -> c.sneakParticle = cycle(c.sneakParticle, PARTICLES);
            case "hidelore" -> c.hideLore = !c.hideLore;
            case "bow" -> openBowGui(player, s);
            case "back" -> openEditor(player, s);
            case "save" -> {
                saveToHand(player, s);
                sessions.remove(pid);
                player.closeInventory();
                player.sendMessage(color("&aÍtem guardado."));
            }
            case "cancel" -> {
                sessions.remove(pid);
                player.closeInventory();
            }
        }
        if (!action.equals("back") && !action.equals("save") && !action.equals("cancel") && !action.equals("bow")) {
            openXript(player, s);
        }
    }

    private void handleBowAction(Player player, EditSession s, String action, boolean middle) {
        XriptConfig c = s.xript;
        UUID pid = player.getUniqueId();
        if (middle && isTypableBow(action)) {
            promptTypeValue(player, "bow:" + action);
            return;
        }
        switch (action) {
            case "bowproj" -> c.bowProjectile = cycle(c.bowProjectile, PROJECTILES);
            case "bowpart" -> c.bowTrailParticle = cycle(c.bowTrailParticle, PARTICLES);
            case "bowtproj" -> c.bowTrailProjectile = cycle(c.bowTrailProjectile, PROJECTILES);
            case "bowtcount" -> c.bowTrailProjectileCount = cycleInt(c.bowTrailProjectileCount, 8);
            case "bowtfangs" -> c.bowTrailFangs = cycleInt(c.bowTrailFangs, 10);
            case "bowtpat" -> c.bowTrailPattern = PATTERNS[cycleIdx(PATTERNS, c.bowTrailPattern)];
            case "bowfilter" -> c.bowHitFilter = cycleInt(c.bowHitFilter, 2);
            case "bowmode" -> c.bowHitMode = cycleInt(c.bowHitMode, 3);
            case "bowcount" -> c.bowHitCount = cycleInt(c.bowHitCount, 8);
            case "bowhitproj" -> c.bowHitProjectile = cycle(c.bowHitProjectile, PROJECTILES);
            case "back" -> openXript(player, s);
            case "save" -> {
                saveToHand(player, s);
                sessions.remove(pid);
                player.closeInventory();
                player.sendMessage(color("&aÍtem guardado."));
            }
            case "cancel" -> {
                sessions.remove(pid);
                player.closeInventory();
            }
        }
        if (!action.equals("back") && !action.equals("save") && !action.equals("cancel")) {
            openBowGui(player, s);
        }
    }

    private boolean isTypableXript(String action) {
        return switch (action) {
            case "eff", "proj", "projcount", "pattern", "particle", "rcproj", "rcprojcount",
                 "fangs", "vv", "vh", "jump", "kille", "killeff", "sneakeff", "sneakp" -> true;
            default -> false;
        };
    }

    private boolean isTypableBow(String action) {
        return switch (action) {
            case "bowproj", "bowpart", "bowtproj", "bowtcount", "bowtfangs", "bowtpat", "bowfilter", "bowmode", "bowcount", "bowhitproj" -> true;
            default -> false;
        };
    }

    private void promptTypeValue(Player player, String field) {
        UUID pid = player.getUniqueId();
        pendingInputs.put(pid, field);
        String finalMsg = "&eEscribe el valor en el chat (sin máximo):";
        Bukkit.getScheduler().runTask(this, () -> {
            player.closeInventory();
            player.sendMessage(color(finalMsg));
        });
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID pid = player.getUniqueId();
        String field = pendingInputs.remove(pid);
        if (field == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        Bukkit.getScheduler().runTask(this, () -> {
            EditSession s = sessions.get(pid);
            if (s == null) return;
            applyInput(player, s, field, input);
            if (!sessions.containsKey(pid)) return;
            if (field.startsWith("ench:")) openEnchantGui(player, s, enchantPages.getOrDefault(pid, 1));
            else if (field.startsWith("bow:")) openBowGui(player, s);
            else if (field.startsWith("xript:")) openXript(player, s);
            else openEditor(player, s);
        });
    }

    private void applyInput(Player player, EditSession s, String field, String input) {
        if (field.startsWith("xript:")) {
            applyXriptTyped(player, s.xript, field.substring(6), input);
            return;
        }
        if (field.startsWith("bow:")) {
            applyBowTyped(player, s.xript, field.substring(4), input);
            return;
        }
        if (field.startsWith("ench:")) {
            Enchantment en = Enchantment.getByKey(NamespacedKey.fromString(field.substring(5)));
            if (en == null) return;
            try {
                int v = Integer.parseInt(input);
                ItemMeta em = s.item.getItemMeta();
                if (v <= 0) em.removeEnchant(en);
                else em.addEnchant(en, v, true);
                s.item.setItemMeta(em);
                player.sendMessage(color("&aNivel establecido: &f" + v));
            } catch (NumberFormatException e) {
                player.sendMessage(color("&cNúmero inválido."));
            }
            return;
        }
        ItemMeta meta = s.item.getItemMeta();
        switch (field) {
            case "name" -> {
                meta.setDisplayName(color(input));
                player.sendMessage(color("&aNombre establecido."));
            }
            case "lore" -> {
                List<String> lore = new ArrayList<>();
                for (String line : input.split("\\|")) lore.add(color(line.trim()));
                meta.setLore(lore);
                player.sendMessage(color("&aLore establecido."));
            }
            case "custommodel" -> {
                try {
                    int model = Integer.parseInt(input);
                    meta.setCustomModelData(model <= 0 ? null : model);
                    player.sendMessage(color("&aCustom model establecido: &f" + model));
                } catch (NumberFormatException e) {
                    player.sendMessage(color("&cNúmero inválido."));
                    return;
                }
            }
            case "itemmodel" -> {
                try {
                    meta.setItemModel(NamespacedKey.fromString(input));
                    player.sendMessage(color("&aItem model establecido: &f" + input));
                } catch (Exception e) {
                    player.sendMessage(color("&cResource location inválido."));
                    return;
                }
            }
        }
        s.item.setItemMeta(meta);
    }

    private void applyXriptTyped(Player player, XriptConfig c, String action, String input) {
        switch (action) {
            case "eff", "killeff", "sneakeff" -> {
                PotionEffectType t = resolveEffect(input);
                if (t == null) { player.sendMessage(color("&cEfecto no encontrado.")); return; }
                if (action.equals("eff")) c.dmgEffect = t;
                else if (action.equals("killeff")) c.killEffect = t;
                else c.sneakEffect = t;
            }
            case "proj", "rcproj" -> {
                EntityType t = resolveEntity(input);
                if (t == null) { player.sendMessage(color("&cEntidad no encontrada.")); return; }
                if (action.equals("proj")) c.dmgProjectile = t;
                else c.rcProjectile = t;
            }
            case "particle", "sneakp" -> {
                Particle p = resolveParticle(input);
                if (p == null) { player.sendMessage(color("&cPartícula no encontrada.")); return; }
                if (action.equals("particle")) c.rcParticle = p;
                else c.sneakParticle = p;
            }
            case "pattern" -> c.dmgPattern = input.toUpperCase(Locale.ROOT);
            default -> {
                try {
                    int v = Integer.parseInt(input);
                    setNumericXript(c, action, v);
                } catch (NumberFormatException e) {
                    player.sendMessage(color("&cNúmero inválido."));
                    return;
                }
            }
        }
        player.sendMessage(color("&aValor establecido."));
    }

    private void applyBowTyped(Player player, XriptConfig c, String action, String input) {
        switch (action) {
            case "bowproj" -> {
                EntityType t = resolveEntity(input);
                if (t == null) { player.sendMessage(color("&cEntidad no encontrada.")); return; }
                c.bowProjectile = t;
            }
            case "bowpart" -> {
                Particle p = resolveParticle(input);
                if (p == null) { player.sendMessage(color("&cPartícula no encontrada.")); return; }
                c.bowTrailParticle = p;
            }
            case "bowtproj" -> {
                EntityType t = resolveEntity(input);
                if (t == null) { player.sendMessage(color("&cEntidad no encontrada.")); return; }
                c.bowTrailProjectile = t;
            }
            case "bowhitproj" -> {
                EntityType t = resolveEntity(input);
                if (t == null) { player.sendMessage(color("&cEntidad no encontrada.")); return; }
                c.bowHitProjectile = t;
            }
            case "bowfilter", "bowmode", "bowtcount", "bowtfangs", "bowcount" -> {
                try {
                    int v = Integer.parseInt(input);
                    if (action.equals("bowfilter")) c.bowHitFilter = Math.max(0, v);
                    else if (action.equals("bowmode")) c.bowHitMode = Math.max(0, v);
                    else if (action.equals("bowtcount")) c.bowTrailProjectileCount = Math.max(1, v);
                    else if (action.equals("bowtfangs")) c.bowTrailFangs = Math.max(0, v);
                    else c.bowHitCount = Math.max(1, v);
                } catch (NumberFormatException e) {
                    player.sendMessage(color("&cNúmero inválido."));
                    return;
                }
            }
            case "bowtpat" -> c.bowTrailPattern = input.toUpperCase(Locale.ROOT);
        }
        player.sendMessage(color("&aValor establecido."));
    }

    private void setNumericXript(XriptConfig c, String action, int v) {
        switch (action) {
            case "projcount" -> c.dmgProjectileCount = Math.max(1, v);
            case "rcprojcount" -> c.rcProjectileCount = Math.max(1, v);
            case "fangs" -> c.rcFangsRange = Math.max(0, v);
            case "vv" -> c.maxVvDamage = Math.max(0, v);
            case "vh" -> c.maxVhDamage = Math.max(0, v);
            case "jump" -> c.jumpBoost = Math.max(0, v);
            case "kille" -> c.killExplosion = Math.max(0, v);
        }
    }

    private PotionEffectType resolveEffect(String input) {
        String lower = input.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        for (PotionEffectType t : EFFECTS) if (t.getKey().getKey().equals(lower)) return t;
        for (PotionEffectType t : BUFFS) if (t.getKey().getKey().equals(lower)) return t;
        try {
            return PotionEffectType.getByKey(NamespacedKey.minecraft(lower));
        } catch (Exception e) {
            return null;
        }
    }

    private EntityType resolveEntity(String input) {
        try {
            return EntityType.valueOf(input.toUpperCase(Locale.ROOT).replace(" ", "_"));
        } catch (Exception e) {
            return null;
        }
    }

    private Particle resolveParticle(String input) {
        try {
            return Particle.valueOf(input.toUpperCase(Locale.ROOT).replace(" ", "_"));
        } catch (Exception e) {
            return null;
        }
    }

    private String bowFilterName(int f) {
        return switch (f) {
            case 0 -> "Solo entidad";
            case 1 -> "Entidad+bloque";
            case 2 -> "Solo bloque";
            default -> String.valueOf(f);
        };
    }

    private String bowModeName(int m) {
        return switch (m) {
            case 0 -> "Ninguno";
            case 1 -> "Esfera";
            case 2 -> "Mob cercano";
            case 3 -> "Fuegos artificiales";
            default -> String.valueOf(m);
        };
    }

    // ---------------- Save ----------------

    private void saveToHand(Player player, EditSession s) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(color("&cYa no tienes el ítem en la mano."));
            return;
        }
        ItemStack result = s.item.clone();
        ItemMeta meta = result.getItemMeta();
        XriptConfig c = s.xript;
        if (c.enabled) {
            meta.getPersistentDataContainer().set(xriptKey, PersistentDataType.STRING, serializeXript(c));
            if (!c.hideLore) {
                List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
                if (lore.isEmpty() || !lore.get(lore.size() - 1).isEmpty()) lore.add("");
                lore.addAll(xriptLore(c));
                meta.setLore(lore);
            }
        } else {
            meta.getPersistentDataContainer().remove(xriptKey);
        }
        result.setItemMeta(meta);
        player.getInventory().setItemInMainHand(result);
    }

    private List<String> xriptLore(XriptConfig c) {
        List<String> lines = new ArrayList<>();
        lines.add(color("&6&l✶ XRIPT"));
        if (c.dmgEffect != null) lines.add(color("&7Al dañar: Efecto &f" + c.dmgEffect.getKey().getKey()));
        if (c.dmgProjectile != null) {
            lines.add(color("&7Al dañar: Proyectil &f" + c.dmgProjectile.name() + " &7x" + c.dmgProjectileCount + " (&f" + c.dmgPattern + "&7)"));
        }
        if (c.dmgFire) lines.add(color("&7Al dañar: &fFuego"));
        if (c.dmgLaunch) lines.add(color("&7Al dañar: &fLanza al aire"));
        if (c.rcRiptide) lines.add(color("&7Click Der: &fRiptide"));
        if (c.rcParticle != null) lines.add(color("&7Click Der: Partícula &f" + c.rcParticle.getKey().getKey()));
        if (c.rcProjectile != null) {
            lines.add(color("&7Click Der: Proyectil &f" + c.rcProjectile.name() + " &7x" + c.rcProjectileCount));
        }
        if (c.rcFangsRange > 0) lines.add(color("&7Click Der: Fangos &f" + c.rcFangsRange + " bloques"));
        if (c.rcGuided) lines.add(color("&7Click Der: Proyectiles &fguiados"));
        if (c.maxVvDamage > 0) lines.add(color("&7Daño máx vel. vertical: &f" + c.maxVvDamage));
        if (c.maxVhDamage > 0) lines.add(color("&7Daño máx vel. horizontal: &f" + c.maxVhDamage));
        if (c.jumpBoost > 0) lines.add(color("&7Salto: Impulso &f" + c.jumpBoost));
        if (c.killExplosion > 0) lines.add(color("&7Al matar: Explosión &f" + c.killExplosion));
        if (c.killEffect != null) lines.add(color("&7Al matar: Efecto &f" + c.killEffect.getKey().getKey()));
        if (c.sneakEffect != null) lines.add(color("&7Al agacharse: Efecto &f" + c.sneakEffect.getKey().getKey()));
        if (c.sneakParticle != null) lines.add(color("&7Al agacharse: Partícula &f" + c.sneakParticle.getKey().getKey()));
        if (c.bowProjectile != null) lines.add(color("&7Arco: Flecha → &f" + c.bowProjectile.name()));
        if (c.bowTrailParticle != null) lines.add(color("&7Arco: Estela partícula &f" + c.bowTrailParticle.getKey().getKey()));
        if (c.bowTrailProjectile != null) {
            lines.add(color("&7Arco: Estela proyectil &f" + c.bowTrailProjectile.name() + " &7x" + c.bowTrailProjectileCount + " (" + c.bowTrailPattern + ")"));
        }
        if (c.bowTrailFangs > 0) lines.add(color("&7Arco: Estela fangos &f" + c.bowTrailFangs));
        if (c.bowHitMode > 0) {
            lines.add(color("&7Arco: Al golpear &f" + bowModeName(c.bowHitMode) + " &7x" + c.bowHitCount + " (" + bowFilterName(c.bowHitFilter) + ")"));
        }
        if (c.bowHitProjectile != null) lines.add(color("&7Arco: Proyectil al golpear &f" + c.bowHitProjectile.name()));
        return lines;
    }

    // ---------------- Effects ----------------

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        XriptConfig c = readXript(item);
        if (c == null || !c.enabled) return;

        Vector vel = p.getVelocity();
        double vy = Math.abs(vel.getY());
        double vh = Math.hypot(vel.getX(), vel.getZ());
        if (c.maxVvDamage > 0 && vy > 0.3) {
            event.setDamage(event.getDamage() + c.maxVvDamage * Math.min(1, vy / 1.5));
        }
        if (c.maxVhDamage > 0 && vh > 0.3) {
            event.setDamage(event.getDamage() + c.maxVhDamage * Math.min(1, vh / 1.5));
        }

        if (event.getEntity() instanceof LivingEntity target) {
            if (c.dmgEffect != null) {
                target.addPotionEffect(new PotionEffect(c.dmgEffect, 60, 1));
            }
            if (c.dmgFire) {
                target.setFireTicks(100);
            }
            if (c.dmgLaunch) {
                target.setVelocity(target.getVelocity().add(new Vector(0, 1.2, 0)));
            }
            if (c.dmgProjectile != null && c.dmgProjectileCount > 0) {
                Location aim = target.getLocation().clone().add(0, 1, 0);
                summonProjectiles(p, c.dmgProjectile, c.dmgProjectileCount, c.dmgPattern, aim, null);
            }
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        DamageSource source = event.getDamageSource();
        Entity killer = source.getCausingEntity();
        if (!(killer instanceof Player p)) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        XriptConfig c = readXript(item);
        if (c == null || !c.enabled) return;

        LivingEntity dead = event.getEntity();
        if (c.killExplosion > 0) {
            dead.getWorld().createExplosion(dead.getLocation(), c.killExplosion, false, false, p);
        }
        if (c.killEffect != null) {
            p.addPotionEffect(new PotionEffect(c.killEffect, 120, 1));
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player p = event.getPlayer();
        XriptConfig c = readXript(p.getInventory().getItemInMainHand());
        if (c == null || !c.enabled) return;
        if (c.sneakEffect != null) {
            p.addPotionEffect(new PotionEffect(c.sneakEffect, 60, 1));
        }
        if (c.sneakParticle != null) {
            p.getWorld().spawnParticle(c.sneakParticle, p.getLocation().clone().add(0, 1, 0), 15, 0.4, 0.4, 0.4, 0.05);
        }
    }

    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        Player p = event.getPlayer();
        XriptConfig c = readXript(p.getInventory().getItemInMainHand());
        if (c == null || !c.enabled) return;
        if (c.jumpBoost > 0) {
            p.setVelocity(p.getVelocity().add(new Vector(0, 0.12 * c.jumpBoost, 0)));
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 10, 0.2, 0, 0.2, 0.02);
        }
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile proj = event.getEntity();
        if (!(proj.getShooter() instanceof Player p)) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!isBow(item)) return;
        XriptConfig c = readXript(item);
        if (c == null || !c.enabled) return;
        if (c.bowProjectile != null) {
            event.setCancelled(true);
            Vector vel = proj.getVelocity();
            Entity replacement = p.getWorld().spawnEntity(p.getEyeLocation(), c.bowProjectile);
            replacement.setVelocity(vel);
            if (replacement instanceof Projectile rep) rep.setShooter(p);
            bowTrack.put(replacement.getUniqueId(), new BowState(p.getUniqueId(), c));
        } else {
            bowTrack.put(proj.getUniqueId(), new BowState(p.getUniqueId(), c));
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        BowState bs = bowTrack.remove(proj.getUniqueId());
        if (bs == null) return;
        XriptConfig c = bs.config;
        boolean entityHit = event.getHitEntity() != null;
        boolean blockHit = event.getHitBlock() != null;
        if (c.bowHitFilter == 0 && !entityHit) return;
        if (c.bowHitFilter == 2 && !blockHit) return;
        Location hit = proj.getLocation();
        if (event.getHitEntity() != null) hit = event.getHitEntity().getLocation().clone().add(0, 1, 0);
        applyBowHit(proj, c, hit);
    }

    private void applyBowHit(Projectile proj, XriptConfig c, Location hit) {
        if (c.bowHitMode == 0) return;
        EntityType type = c.bowHitProjectile != null ? c.bowHitProjectile
                : (c.bowProjectile != null ? c.bowProjectile : EntityType.ARROW);
        int count = Math.max(1, c.bowHitCount);
        ProjectileSource shooter = proj.getShooter();
        World w = hit.getWorld();
        switch (c.bowHitMode) {
            case 1 -> {
                double phi = Math.PI * (3 - Math.sqrt(5));
                for (int i = 0; i < count; i++) {
                    double y = count == 1 ? 0 : 1 - (i / (double) (count - 1)) * 2;
                    double r = Math.sqrt(Math.max(0, 1 - y * y));
                    double theta = phi * i;
                    Vector dir = new Vector(Math.cos(theta) * r, y, Math.sin(theta) * r).normalize();
                    spawnHitProjectile(w, hit.clone().add(dir.clone().multiply(0.6)), dir, type, shooter);
                }
            }
            case 2 -> {
                List<LivingEntity> near = w.getNearbyEntities(hit, 12, 12, 12).stream()
                        .filter(e -> e instanceof LivingEntity && e != proj.getShooter() && !(e instanceof Player))
                        .map(e -> (LivingEntity) e)
                        .limit(count)
                        .toList();
                for (LivingEntity target : near) {
                    Vector dir = target.getEyeLocation().toVector().subtract(hit.toVector()).normalize();
                    spawnHitProjectile(w, hit.clone().add(dir.clone().multiply(0.6)), dir, type, shooter);
                }
            }
            case 3 -> {
                Random r = new Random();
                for (int i = 0; i < count; i++) {
                    Vector dir = new Vector(r.nextGaussian(), Math.abs(r.nextGaussian()) + 0.3, r.nextGaussian()).normalize();
                    spawnHitProjectile(w, hit.clone().add(dir.clone().multiply(0.6)), dir, type, shooter);
                }
            }
        }
    }

    private void spawnHitProjectile(World w, Location from, Vector dir, EntityType type, ProjectileSource shooter) {
        Entity proj = w.spawnEntity(from, type);
        proj.setVelocity(dir.multiply(2.2));
        if (proj instanceof Projectile projectile && shooter != null) projectile.setShooter(shooter);
    }

    private void spawnTrailProjectile(Location loc, Vector dir, EntityType type, Player owner) {
        Entity t = loc.getWorld().spawnEntity(loc, type);
        t.setVelocity(dir.multiply(0.8));
        if (t instanceof Projectile tp && owner != null) tp.setShooter(owner);
    }

    private boolean isBow(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        return m == Material.BOW || m == Material.CROSSBOW;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        XriptConfig c = readXript(item);
        if (c == null || !c.enabled) return;

        Player p = event.getPlayer();
        if (c.rcRiptide) {
            startRiptide(p);
            return;
        }
        applyRightClickEffects(p, c);
    }

    private void applyRightClickEffects(Player p, XriptConfig c) {
        if (c.rcParticle != null) {
            p.getWorld().spawnParticle(c.rcParticle, p.getEyeLocation(), 30, 0.5, 0.5, 0.5, 0.1);
        }
        if (c.rcProjectile != null && c.rcProjectileCount > 0) {
            Entity homingTarget = c.rcGuided ? p.getTargetEntity(30) : null;
            summonProjectiles(p, c.rcProjectile, c.rcProjectileCount, "FAN", null, homingTarget);
        }
        if (c.rcFangsRange > 0) {
            summonFangs(p, c.rcFangsRange);
        }
    }

    private void startRiptide(Player p) {
        UUID pid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long ready = riptideCooldown.getOrDefault(pid, 0L);
        if (now < ready) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            p.sendMessage(color("&7Riptide en enfriamiento: &f" + ((ready - now) / 1000 + 1) + "s"));
            return;
        }
        riptideCharging.put(pid, 24);
        p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1f);
        p.sendMessage(color("&b¡Riptide cargando...!"));
    }

    private void launchRiptide(Player p) {
        UUID pid = p.getUniqueId();
        XriptConfig c = readXript(p.getInventory().getItemInMainHand());
        if (c == null || !c.enabled) return;
        Vector dir = p.getLocation().getDirection().normalize();
        p.setVelocity(dir.multiply(4.0));
        p.setFallDistance(0);
        p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1f);
        p.setSwimming(true);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) p.setSwimming(false);
        }, 30L);
        riptideCooldown.put(pid, System.currentTimeMillis() + 7000);
        if (c.rcParticle != null) {
            p.getWorld().spawnParticle(c.rcParticle, p.getEyeLocation(), 30, 0.5, 0.5, 0.5, 0.1);
        }
        if (c.rcProjectile != null && c.rcProjectileCount > 0) {
            Entity homingTarget = c.rcGuided ? p.getTargetEntity(30) : null;
            summonProjectiles(p, c.rcProjectile, c.rcProjectileCount, "FAN", null, homingTarget);
        }
        if (c.rcFangsRange > 0) {
            summonFangs(p, c.rcFangsRange);
        }
    }

    private void summonFangs(Player p, int range) {
        Vector horizontal = p.getLocation().getDirection();
        horizontal.setY(0);
        if (horizontal.lengthSquared() < 0.01) horizontal = new Vector(1, 0, 0);
        else horizontal.normalize();
        for (int i = 1; i <= range; i++) {
            Location loc = p.getLocation().clone().add(horizontal.clone().multiply(i)).add(0, 0.5, 0);
            EvokerFangs fangs = (EvokerFangs) p.getWorld().spawnEntity(loc, EntityType.EVOKER_FANGS);
            fangs.setOwner(p);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1f, 1f);
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Integer>> chargeIt = riptideCharging.entrySet().iterator();
        while (chargeIt.hasNext()) {
            Map.Entry<UUID, Integer> e = chargeIt.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) { chargeIt.remove(); continue; }
            int left = e.getValue();
            p.setVelocity(new Vector(0, -0.05, 0));
            p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation().clone().add(0, 1, 0), 8, 0.4, 0.6, 0.4, 0.1);
            if (left % 12 == 0) {
                p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, 1f, 1f);
            }
            if (left <= 0) {
                chargeIt.remove();
                launchRiptide(p);
            } else {
                e.setValue(left - 1);
            }
        }

        Iterator<Map.Entry<UUID, UUID>> homeIt = homing.entrySet().iterator();
        while (homeIt.hasNext()) {
            Map.Entry<UUID, UUID> e = homeIt.next();
            Entity proj = Bukkit.getEntity(e.getKey());
            Entity target = Bukkit.getEntity(e.getValue());
            if (proj == null || proj.isDead() || target == null || target.isDead()) {
                homeIt.remove();
                continue;
            }
            Vector dir = target.getLocation().toVector().subtract(proj.getLocation().toVector()).normalize();
            double speed = proj.getVelocity().length();
            if (speed < 0.1) speed = 1.5;
            proj.setVelocity(dir.multiply(speed));
        }

        Iterator<Map.Entry<UUID, BowState>> bowIt = bowTrack.entrySet().iterator();
        while (bowIt.hasNext()) {
            Map.Entry<UUID, BowState> e = bowIt.next();
            Entity proj = Bukkit.getEntity(e.getKey());
            if (proj == null || proj.isDead() || !proj.isValid()) { bowIt.remove(); continue; }
            BowState bs = e.getValue();
            XriptConfig c = bs.config;
            Location loc = proj.getLocation();
            if (proj instanceof AbstractArrow arrow && arrow.isInBlock()) {
                bowIt.remove();
                continue;
            }
            if (c.bowTrailParticle != null) {
                proj.getWorld().spawnParticle(c.bowTrailParticle, loc, 3, 0.15, 0.15, 0.15, 0.01);
            }
            if (c.bowTrailProjectile != null) {
                Player owner = Bukkit.getPlayer(bs.shooter);
                Vector travel = proj.getVelocity().clone().normalize();
                if (travel.lengthSquared() < 0.01) travel = new Vector(0, -1, 0);
                int count = Math.max(1, c.bowTrailProjectileCount);
                switch (c.bowTrailPattern) {
                    case "RING" -> {
                        for (int i = 0; i < count; i++) {
                            double a = Math.PI * 2 * i / count;
                            Vector dir = new Vector(Math.cos(a), 0, Math.sin(a)).normalize();
                            spawnTrailProjectile(loc, dir, c.bowTrailProjectile, owner);
                        }
                    }
                    case "LINE" -> {
                        for (int i = 0; i < count; i++) {
                            spawnTrailProjectile(loc, travel.clone(), c.bowTrailProjectile, owner);
                        }
                    }
                    default -> {
                        for (int i = 0; i < count; i++) {
                            double spread = (i - (count - 1) / 2.0) * 0.3;
                            Vector dir = rotateY(travel.clone(), spread);
                            spawnTrailProjectile(loc, dir, c.bowTrailProjectile, owner);
                        }
                    }
                }
            }
            if (c.bowTrailFangs > 0) {
                bs.fangCounter++;
                if (bs.fangCounter >= 2) {
                    bs.fangCounter = 0;
                    Location fangLoc = loc.clone().add(0, -0.3, 0);
                    EvokerFangs f = (EvokerFangs) proj.getWorld().spawnEntity(fangLoc, EntityType.EVOKER_FANGS);
                    Player owner = Bukkit.getPlayer(bs.shooter);
                    if (owner != null) f.setOwner(owner);
                }
            }
        }
    }

    private void summonProjectiles(Player p, EntityType type, int count, String pattern, Location aim, Entity homingTarget) {
        World w = p.getWorld();
        Location loc = p.getEyeLocation();
        Vector base = aim != null
                ? aim.toVector().subtract(loc.toVector()).normalize()
                : p.getLocation().getDirection().normalize();
        switch (pattern) {
            case "RING" -> {
                for (int i = 0; i < count; i++) {
                    double a = Math.PI * 2 * i / count;
                    Vector dir = new Vector(Math.cos(a), 0, Math.sin(a)).normalize();
                    launch(w, loc, type, dir, p, homingTarget);
                }
            }
            case "FAN" -> {
                for (int i = 0; i < count; i++) {
                    double spread = (i - (count - 1) / 2.0) * 0.35;
                    Vector dir = rotateY(base.clone(), spread);
                    launch(w, loc, type, dir, p, homingTarget);
                }
            }
            default -> {
                for (int i = 0; i < count; i++) {
                    launch(w, loc, type, base.clone(), p, homingTarget);
                }
            }
        }
    }

    private void launch(World w, Location loc, EntityType type, Vector dir, Player p, Entity homingTarget) {
        Entity proj = w.spawnEntity(loc.clone().add(dir.clone().multiply(0.5)), type);
        proj.setVelocity(dir.multiply(1.8));
        if (proj instanceof Projectile projectile) projectile.setShooter(p);
        if (homingTarget != null) homing.put(proj.getUniqueId(), homingTarget.getUniqueId());
    }

    private Vector rotateY(Vector v, double rad) {
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return v.setX(x).setZ(z);
    }

    // ---------------- Persistence ----------------

    private String serializeXript(XriptConfig c) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("enabled", c.enabled);
        y.set("dmgEffect", c.dmgEffect == null ? null : c.dmgEffect.getKey().getKey());
        y.set("dmgProjectile", c.dmgProjectile == null ? null : c.dmgProjectile.name());
        y.set("dmgProjectileCount", c.dmgProjectileCount);
        y.set("dmgPattern", c.dmgPattern);
        y.set("dmgFire", c.dmgFire);
        y.set("dmgLaunch", c.dmgLaunch);
        y.set("rcRiptide", c.rcRiptide);
        y.set("rcParticle", c.rcParticle == null ? null : c.rcParticle.getKey().getKey());
        y.set("rcProjectile", c.rcProjectile == null ? null : c.rcProjectile.name());
        y.set("rcProjectileCount", c.rcProjectileCount);
        y.set("rcFangsRange", c.rcFangsRange);
        y.set("rcGuided", c.rcGuided);
        y.set("maxVvDamage", c.maxVvDamage);
        y.set("maxVhDamage", c.maxVhDamage);
        y.set("jumpBoost", c.jumpBoost);
        y.set("killExplosion", c.killExplosion);
        y.set("killEffect", c.killEffect == null ? null : c.killEffect.getKey().getKey());
        y.set("sneakEffect", c.sneakEffect == null ? null : c.sneakEffect.getKey().getKey());
        y.set("sneakParticle", c.sneakParticle == null ? null : c.sneakParticle.getKey().getKey());
        y.set("hideLore", c.hideLore);
        y.set("bowProjectile", c.bowProjectile == null ? null : c.bowProjectile.name());
        y.set("bowTrailParticle", c.bowTrailParticle == null ? null : c.bowTrailParticle.getKey().getKey());
        y.set("bowTrailProjectile", c.bowTrailProjectile == null ? null : c.bowTrailProjectile.name());
        y.set("bowTrailProjectileCount", c.bowTrailProjectileCount);
        y.set("bowTrailFangs", c.bowTrailFangs);
        y.set("bowTrailPattern", c.bowTrailPattern);
        y.set("bowHitProjectile", c.bowHitProjectile == null ? null : c.bowHitProjectile.name());
        y.set("bowHitFilter", c.bowHitFilter);
        y.set("bowHitMode", c.bowHitMode);
        y.set("bowHitCount", c.bowHitCount);
        return y.saveToString();
    }

    private XriptConfig readXript(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String data = item.getItemMeta().getPersistentDataContainer().get(xriptKey, PersistentDataType.STRING);
        if (data == null) return null;
        return deserializeXript(data);
    }

    private XriptConfig deserializeXript(String data) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(data);
        } catch (Exception e) {
            return null;
        }
        XriptConfig c = new XriptConfig();
        try {
            String eName = y.getString("dmgEffect");
            c.dmgEffect = eName == null ? null : PotionEffectType.getByKey(NamespacedKey.minecraft(eName));
            String pName = y.getString("dmgProjectile");
            c.dmgProjectile = pName == null ? null : EntityType.valueOf(pName);
            c.dmgProjectileCount = y.getInt("dmgProjectileCount", 1);
            c.dmgPattern = y.getString("dmgPattern", "LINE");
            c.rcRiptide = y.getBoolean("rcRiptide");
            String paName = y.getString("rcParticle");
            c.rcParticle = paName == null ? null : Particle.valueOf(paName.toUpperCase(Locale.ROOT));
            String rpName = y.getString("rcProjectile");
            c.rcProjectile = rpName == null ? null : EntityType.valueOf(rpName);
            c.rcProjectileCount = y.getInt("rcProjectileCount", 1);
            c.rcFangsRange = y.getInt("rcFangsRange");
            c.rcGuided = y.getBoolean("rcGuided");
            c.maxVvDamage = y.getInt("maxVvDamage");
            c.maxVhDamage = y.getInt("maxVhDamage");
            c.jumpBoost = y.getInt("jumpBoost");
            c.killExplosion = y.getInt("killExplosion");
            String keName = y.getString("killEffect");
            c.killEffect = keName == null ? null : PotionEffectType.getByKey(NamespacedKey.minecraft(keName));
            String seName = y.getString("sneakEffect");
            c.sneakEffect = seName == null ? null : PotionEffectType.getByKey(NamespacedKey.minecraft(seName));
            String spName = y.getString("sneakParticle");
            c.sneakParticle = spName == null ? null : Particle.valueOf(spName.toUpperCase(Locale.ROOT));
            c.hideLore = y.getBoolean("hideLore");
            String bpName = y.getString("bowProjectile");
            c.bowProjectile = bpName == null ? null : EntityType.valueOf(bpName);
            String bpaName = y.getString("bowTrailParticle");
            c.bowTrailParticle = bpaName == null ? null : Particle.valueOf(bpaName.toUpperCase(Locale.ROOT));
            String btpName = y.getString("bowTrailProjectile");
            c.bowTrailProjectile = btpName == null ? null : EntityType.valueOf(btpName);
            c.bowTrailProjectileCount = y.getInt("bowTrailProjectileCount", 1);
            c.bowTrailFangs = y.getInt("bowTrailFangs");
            c.bowTrailPattern = y.getString("bowTrailPattern", "FAN");
            String bhpName = y.getString("bowHitProjectile");
            c.bowHitProjectile = bhpName == null ? null : EntityType.valueOf(bhpName);
            c.bowHitFilter = y.getInt("bowHitFilter");
            c.bowHitMode = y.getInt("bowHitMode");
            c.bowHitCount = y.getInt("bowHitCount", 1);
        } catch (Exception e) {
            return null;
        }
        return c;
    }

    // ---------------- Helpers ----------------

    private ItemStack button(Material mat, String name, List<String> lore) {
        return button(mat, name, lore, "none");
    }

    private ItemStack button(Material mat, String name, String action) {
        return button(mat, name, null, action);
    }

    private ItemStack button(Material mat, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack toggle(Material mat, String name, String action, boolean state) {
        ItemStack item = new ItemStack(state ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color((state ? "&a" : "&7") + name + (state ? " &a(ON)" : " &7(OFF)")));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack xriptButton(Material mat, String name, String action, String value) {
        return button(mat, color("&e&l" + name), List.of(color("&7Valor: &f" + value)), action);
    }

    private ItemStack xriptToggle(Material mat, String name, String action, boolean state) {
        ItemStack item = new ItemStack(state ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color((state ? "&a" : "&7") + name + (state ? " &a(ON)" : " &7(OFF)")));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private String getAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private <T> T cycle(T current, T[] arr) {
        return arr[cycleIdx(arr, current)];
    }

    private <T> int cycleIdx(T[] arr, T current) {
        if (current == null) return 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(current)) return (i + 1) % arr.length;
        }
        return 0;
    }

    private int cycleInt(int current, int max) {
        return (current + 1) % (max + 1);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static class EditSession {
        ItemStack item;
        ItemStack original;
        XriptConfig xript = new XriptConfig();
    }

    private static class XriptConfig {
        boolean enabled = true;
        PotionEffectType dmgEffect;
        EntityType dmgProjectile;
        int dmgProjectileCount = 1;
        String dmgPattern = "LINE";
        boolean dmgFire;
        boolean dmgLaunch;
        boolean rcRiptide;
        Particle rcParticle;
        EntityType rcProjectile;
        int rcProjectileCount = 1;
        int rcFangsRange;
        boolean rcGuided;
        int maxVvDamage;
        int maxVhDamage;
        int jumpBoost;
        int killExplosion;
        PotionEffectType killEffect;
        PotionEffectType sneakEffect;
        Particle sneakParticle;
        boolean hideLore;
        EntityType bowProjectile;
        Particle bowTrailParticle;
        EntityType bowTrailProjectile;
        int bowTrailProjectileCount = 1;
        int bowTrailFangs;
        String bowTrailPattern = "FAN";
        EntityType bowHitProjectile;
        int bowHitFilter;
        int bowHitMode;
        int bowHitCount = 1;
    }

    private static class BowState {
        UUID shooter;
        XriptConfig config;
        int fangCounter;
        BowState(UUID shooter, XriptConfig config) {
            this.shooter = shooter;
            this.config = config;
        }
    }
}
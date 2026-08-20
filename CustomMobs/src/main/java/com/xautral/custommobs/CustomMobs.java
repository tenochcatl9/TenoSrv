package com.xautral.custommobs;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CustomMobs extends JavaPlugin implements Listener, TabCompleter {

    private static final int[] INPUT_SLOTS = {0, 10, 11, 12, 13, 15, 16, 20, 22};

    private NamespacedKey actionKey;
    private NamespacedKey eggDataKey;
    private File templatesFile;
    private YamlConfiguration templates;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> pendingNames = new HashSet<>();

    @Override
    public void onEnable() {
        actionKey = new NamespacedKey(this, "action");
        eggDataKey = new NamespacedKey(this, "eggdata");
        loadTemplates();
        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("custommobs");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("xautral.op")) return true;

        if (args.length == 0) {
            openGUI(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "save" -> {
                if (args.length < 2) {
                    player.sendMessage(color("&eUso: /custommobs save <nombre>"));
                    return true;
                }
                saveTemplate(player, args[1].toLowerCase());
            }
            case "saved" -> {
                if (args.length < 2) {
                    player.sendMessage(color("&eUso: /custommobs saved <nombre>"));
                    return true;
                }
                loadTemplate(player, args[1].toLowerCase());
            }
            case "summon" -> {
                Session s = sessions.get(player.getUniqueId());
                if (s == null || s.type == null) {
                    player.sendMessage(color("&cPrimero configura un mob."));
                    return true;
                }
                spawnMob(player, s);
                player.sendMessage(color("&aMob invocado: &f" + s.type));
            }
            case "export" -> {
                Session s = sessions.get(player.getUniqueId());
                if (s == null || s.type == null) {
                    player.sendMessage(color("&cPrimero configura un mob."));
                    return true;
                }
                exportEgg(player, s);
            }
            default -> openGUI(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("save", "saved", "summon", "export").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("saved"))) {
            var section = templates.getConfigurationSection("templates");
            if (section == null) return Collections.emptyList();
            return section.getKeys(false).stream()
                    .filter(k -> k.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void loadTemplates() {
        templatesFile = new File(getDataFolder(), "templates.yml");
        templates = YamlConfiguration.loadConfiguration(templatesFile);
    }

    private void saveTemplate(Player player, String name) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.type == null) {
            player.sendMessage(color("&cPrimero configura un mob."));
            return;
        }
        templates.set("templates." + name, serializeSession(s));
        try {
            templates.save(templatesFile);
            player.sendMessage(color("&aPlantilla guardada: &f" + name));
        } catch (IOException e) {
            player.sendMessage(color("&cNo se pudo guardar la plantilla."));
        }
    }

    private void loadTemplate(Player player, String name) {
        String data = templates.getString("templates." + name);
        if (data == null) {
            player.sendMessage(color("&cNo existe la plantilla: &f" + name));
            return;
        }
        Session s = deserializeSession(data);
        if (s == null) {
            player.sendMessage(color("&cPlantilla inválida."));
            return;
        }
        sessions.put(player.getUniqueId(), s);
        openGUI(player);
        player.sendMessage(color("&aPlantilla cargada: &f" + name));
    }

    private void openGUI(Player player) {
        UUID pid = player.getUniqueId();
        Session s = sessions.computeIfAbsent(pid, k -> new Session());
        Inventory inv = Bukkit.createInventory(null, 54, color("&8CustomMobs"));
        for (int i = 0; i < 54; i++) inv.setItem(i, button(Material.BLACK_STAINED_GLASS_PANE, " ", "none"));

        inv.setItem(0, s.egg);
        inv.setItem(1, button(Material.GRAY_STAINED_GLASS_PANE, "&7Huevo de Spawn", "none"));
        inv.setItem(4, infoItem(s));

        inv.setItem(9, button(Material.GRAY_STAINED_GLASS_PANE, "&7Armadura", "none"));
        inv.setItem(10, s.helmet);
        inv.setItem(11, s.chest);
        inv.setItem(12, s.legs);
        inv.setItem(13, s.boots);

        inv.setItem(14, button(Material.GRAY_STAINED_GLASS_PANE, "&7Mano / Off-hand", "none"));
        inv.setItem(15, s.mainHand);
        inv.setItem(16, s.offHand);

        inv.setItem(19, button(Material.GRAY_STAINED_GLASS_PANE, "&7Silla", "none"));
        inv.setItem(20, s.saddle);
        inv.setItem(21, button(Material.GRAY_STAINED_GLASS_PANE, "&7Cuerpo", "none"));
        inv.setItem(22, s.body);

        inv.setItem(27, toggleItem("Persistente", s.persistent, "toggle_persistent"));
        inv.setItem(28, toggleItem("Silencioso", s.silent, "toggle_silent"));
        inv.setItem(29, toggleItem("Sin IA", s.noAI, "toggle_noai"));
        inv.setItem(30, toggleItem("Bebé", s.baby, "toggle_baby"));
        inv.setItem(31, toggleItem("Glow", s.glowing, "toggle_glow"));

        inv.setItem(32, nameItem(s));

        inv.setItem(33, dropChanceItem(s));

        inv.setItem(48, button(Material.LIGHT_BLUE_DYE, "&b&lEXPORTAR HUEVO", "export"));
        inv.setItem(49, button(Material.LIME_DYE, "&a&lINVOCAR", "apply"));
        inv.setItem(53, button(Material.RED_DYE, "&c&lCANCELAR", "cancel"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID pid = player.getUniqueId();
        if (!sessions.containsKey(pid)) return;
        if (!event.getView().getTitle().contains("CustomMobs")) return;

        Session s = sessions.get(pid);
        int slot = event.getRawSlot();
        ItemStack current = event.getCurrentItem();
        String action = getAction(current);

        if (action != null && !action.equals("none")) {
            event.setCancelled(true);
            handleAction(player, action);
            return;
        }

        if (slot >= 0 && slot < 54) {
            boolean input = false;
            for (int i : INPUT_SLOTS) {
                if (i == slot) { input = true; break; }
            }
            if (!input) event.setCancelled(true);
        }

        Inventory inv = event.getView().getTopInventory();
        Bukkit.getScheduler().runTask(this, () -> {
            if (!sessions.containsKey(pid) || player.getOpenInventory().getTopInventory() != inv) return;
            Session s2 = sessions.get(pid);
            s2.egg = inv.getItem(0);
            s2.helmet = inv.getItem(10);
            s2.chest = inv.getItem(11);
            s2.legs = inv.getItem(12);
            s2.boots = inv.getItem(13);
            s2.mainHand = inv.getItem(15);
            s2.offHand = inv.getItem(16);
            s2.saddle = inv.getItem(20);
            s2.body = inv.getItem(22);
            s2.type = eggType(s2.egg);
            inv.setItem(4, infoItem(s2));
        });
    }

    private void handleAction(Player player, String action) {
        UUID pid = player.getUniqueId();
        Session s = sessions.get(pid);
        if (s == null) return;

        switch (action) {
            case "apply" -> {
                applySession(player, s);
                return;
            }
            case "export" -> {
                exportEgg(player, s);
                return;
            }
            case "cancel" -> {
                sessions.remove(pid);
                player.closeInventory();
                return;
            }
            case "toggle_persistent" -> s.persistent = !s.persistent;
            case "toggle_silent" -> s.silent = !s.silent;
            case "toggle_noai" -> s.noAI = !s.noAI;
            case "toggle_baby" -> s.baby = !s.baby;
            case "toggle_glow" -> s.glowing = !s.glowing;
            case "drop" -> s.dropChance = cycleDropChance(s.dropChance);
            case "name" -> {
                pendingNames.add(pid);
                player.closeInventory();
                player.sendMessage(color("&eEscribe el nombre del mob en el chat (puedes usar & códigos de color):"));
                return;
            }
            default -> { return; }
        }

        Inventory inv = player.getOpenInventory().getTopInventory();
        if (inv == null) return;
        inv.setItem(27, toggleItem("Persistente", s.persistent, "toggle_persistent"));
        inv.setItem(28, toggleItem("Silencioso", s.silent, "toggle_silent"));
        inv.setItem(29, toggleItem("Sin IA", s.noAI, "toggle_noai"));
        inv.setItem(30, toggleItem("Bebé", s.baby, "toggle_baby"));
        inv.setItem(31, toggleItem("Glow", s.glowing, "toggle_glow"));
        inv.setItem(32, nameItem(s));
        inv.setItem(33, dropChanceItem(s));
        inv.setItem(4, infoItem(s));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID pid = player.getUniqueId();
        if (!pendingNames.remove(pid)) return;
        event.setCancelled(true);
        String name = event.getMessage().trim();
        Bukkit.getScheduler().runTask(this, () -> {
            Session s = sessions.get(pid);
            if (s == null) return;
            if (name.isEmpty()) {
                s.name = null;
                player.sendMessage(color("&7Nombre eliminado."));
            } else {
                s.name = name;
                player.sendMessage(color("&aNombre establecido: &f" + color(name)));
            }
            openGUI(player);
        });
    }

    private void applySession(Player player, Session s) {
        if (s.type == null) {
            player.sendMessage(color("&cPon un huevo de spawn primero."));
            return;
        }
        spawnMob(player, s);
        sessions.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(color("&aMob personalizado generado: &f" + s.type));
    }

    private void spawnMob(Player player, Session s) {
        if (s.type == null) return;
        Location loc = player.getLocation().clone()
                .add(player.getLocation().getDirection().multiply(2)).add(0, 1, 0);
        Entity e = player.getWorld().spawnEntity(loc, s.type);

        if (e instanceof LivingEntity le) {
            le.setAI(!s.noAI);
            EntityEquipment eq = le.getEquipment();
            if (eq != null) {
                eq.setItem(EquipmentSlot.HEAD, orAir(s.helmet));
                eq.setItem(EquipmentSlot.CHEST, orAir(s.chest));
                eq.setItem(EquipmentSlot.LEGS, orAir(s.legs));
                eq.setItem(EquipmentSlot.FEET, orAir(s.boots));
                eq.setItem(EquipmentSlot.HAND, orAir(s.mainHand));
                eq.setItem(EquipmentSlot.OFF_HAND, orAir(s.offHand));
                eq.setItem(EquipmentSlot.BODY, orAir(s.body));
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    eq.setDropChance(slot, s.dropChance);
                }
            }
        }

        e.setPersistent(s.persistent);
        e.setSilent(s.silent);
        e.setGlowing(s.glowing);
        if (s.baby && e instanceof Ageable ageable) ageable.setBaby();
        if (s.name != null && !s.name.isEmpty()) {
            e.setCustomName(color(s.name));
            e.setCustomNameVisible(true);
        }
        if (s.saddle != null && s.saddle.getType() != Material.AIR) {
            if (e instanceof AbstractHorse horse) {
                horse.getInventory().setSaddle(s.saddle);
            } else if (e instanceof Steerable steerable) {
                steerable.setSaddle(true);
            }
        }
    }

    private void exportEgg(Player player, Session s) {
        if (s.type == null) return;
        Material mat = spawnEggMaterial(s);
        if (mat == null) {
            player.sendMessage(color("&cEste mob no tiene huevo de spawn."));
            return;
        }
        ItemStack egg = new ItemStack(mat);
        ItemMeta meta = egg.getItemMeta();
        meta.setDisplayName(color("&6&lHuevo: " + s.type.name()));
        meta.getPersistentDataContainer().set(eggDataKey, PersistentDataType.STRING, serializeSession(s));
        egg.setItemMeta(meta);
        player.getInventory().addItem(egg);
        player.sendMessage(color("&aHuevo exportado a tu inventario."));
    }

    private Material spawnEggMaterial(Session s) {
        if (s.egg != null && s.egg.getType().name().endsWith("_SPAWN_EGG")) return s.egg.getType();
        try {
            return Material.valueOf(s.type.name() + "_SPAWN_EGG");
        } catch (Exception e) {
            return null;
        }
    }

    @EventHandler
    public void onEggUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return;
        String data = item.getItemMeta().getPersistentDataContainer().get(eggDataKey, PersistentDataType.STRING);
        if (data == null) return;
        event.setCancelled(true);
        Session s = deserializeSession(data);
        if (s == null) {
            event.getPlayer().sendMessage(color("&cHuevo de mob inválido."));
            return;
        }
        spawnMob(event.getPlayer(), s);
        item.setAmount(item.getAmount() - 1);
        event.getPlayer().sendMessage(color("&aMob generado: &f" + s.type));
    }

    private String serializeSession(Session s) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("type", s.type == null ? null : s.type.name());
        y.set("persistent", s.persistent);
        y.set("silent", s.silent);
        y.set("noai", s.noAI);
        y.set("baby", s.baby);
        y.set("glow", s.glowing);
        y.set("name", s.name);
        y.set("drop", (double) s.dropChance);
        y.set("helmet", s.helmet);
        y.set("chest", s.chest);
        y.set("legs", s.legs);
        y.set("boots", s.boots);
        y.set("mainhand", s.mainHand);
        y.set("offhand", s.offHand);
        y.set("saddle", s.saddle);
        y.set("body", s.body);
        return y.saveToString();
    }

    private Session deserializeSession(String data) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(data);
        } catch (Exception e) {
            return null;
        }
        Session s = new Session();
        try {
            String typeName = y.getString("type");
            s.type = typeName == null ? null : EntityType.valueOf(typeName);
        } catch (Exception e) {
            return null;
        }
        s.persistent = y.getBoolean("persistent");
        s.silent = y.getBoolean("silent");
        s.noAI = y.getBoolean("noai");
        s.baby = y.getBoolean("baby");
        s.glowing = y.getBoolean("glow");
        s.dropChance = (float) y.getDouble("drop", 0.5);
        s.name = y.getString("name");
        s.helmet = y.getItemStack("helmet");
        s.chest = y.getItemStack("chest");
        s.legs = y.getItemStack("legs");
        s.boots = y.getItemStack("boots");
        s.mainHand = y.getItemStack("mainhand");
        s.offHand = y.getItemStack("offhand");
        s.saddle = y.getItemStack("saddle");
        s.body = y.getItemStack("body");
        return s;
    }

    private ItemStack infoItem(Session s) {
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Tipo: &f" + (s.type == null ? "Ninguno" : s.type.name())));
        lore.add(color("&7Drop: &f" + Math.round(s.dropChance * 100) + "%"));
        lore.add(color("&7Persistente: &f" + (s.persistent ? "Sí" : "No")));
        lore.add(color("&7Silencioso: &f" + (s.silent ? "Sí" : "No")));
        lore.add(color("&7Sin IA: &f" + (s.noAI ? "Sí" : "No")));
        lore.add(color("&7Bebé: &f" + (s.baby ? "Sí" : "No")));
        lore.add(color("&7Glow: &f" + (s.glowing ? "Sí" : "No")));
        lore.add(color("&7Nombre: &f" + (s.name == null ? "Ninguno" : color(s.name))));
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&6&lConfiguración"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack toggleItem(String name, boolean state, String action) {
        ItemStack item = new ItemStack(state ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color((state ? "&a" : "&7") + name + (state ? " &a(ON)" : " &7(OFF)")));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack dropChanceItem(Session s) {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&e&lDrop de equipo: &f" + Math.round(s.dropChance * 100) + "%"));
        meta.setLore(List.of(color("&7Clic para cambiar la probabilidad")));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "drop");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack nameItem(Session s) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&6&lNombre del mob"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Actual: &f" + (s.name == null ? "Ninguno" : color(s.name))));
        lore.add(color("&7Clic para cambiarlo escribiendo en el chat"));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "name");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material mat, String name, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private String getAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private EntityType eggType(ItemStack egg) {
        if (egg == null) return null;
        String name = egg.getType().name();
        if (!name.endsWith("_SPAWN_EGG")) return null;
        try {
            return EntityType.valueOf(name.replace("_SPAWN_EGG", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private float cycleDropChance(float current) {
        if (current < 0.001f) return 0.25f;
        if (current < 0.26f) return 0.5f;
        if (current < 0.51f) return 0.75f;
        if (current < 0.76f) return 1.0f;
        return 0.0f;
    }

    private ItemStack orAir(ItemStack i) {
        return i == null ? new ItemStack(Material.AIR) : i;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            sessions.remove(player.getUniqueId());
        }
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private static class Session {
        EntityType type;
        ItemStack egg;
        ItemStack helmet, chest, legs, boots;
        ItemStack mainHand, offHand, saddle, body;
        boolean persistent, silent, noAI, baby, glowing;
        float dropChance = 0.5f;
        String name;
    }
}
package com.xautral.xautralfunctions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class XautralFunctions extends JavaPlugin implements Listener, org.bukkit.command.CommandExecutor, org.bukkit.command.TabCompleter {
    private static final long DEFAULT_TEMPO_MICROS = 500000L;
    private static final List<Sound> INSTRUMENTS = Arrays.asList(
        Sound.BLOCK_NOTE_BLOCK_HARP, Sound.BLOCK_NOTE_BLOCK_BASS, Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
        Sound.BLOCK_NOTE_BLOCK_SNARE, Sound.BLOCK_NOTE_BLOCK_HAT, Sound.BLOCK_NOTE_BLOCK_GUITAR,
        Sound.BLOCK_NOTE_BLOCK_FLUTE, Sound.BLOCK_NOTE_BLOCK_BELL, Sound.BLOCK_NOTE_BLOCK_CHIME,
        Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE,
        Sound.BLOCK_NOTE_BLOCK_COW_BELL, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO,
        Sound.BLOCK_NOTE_BLOCK_BIT, Sound.BLOCK_NOTE_BLOCK_PLING, Sound.BLOCK_NOTE_BLOCK_BANJO,
        Sound.BLOCK_NOTE_BLOCK_TRUMPET, Sound.BLOCK_NOTE_BLOCK_TRUMPET_EXPOSED,
        Sound.BLOCK_NOTE_BLOCK_TRUMPET_WEATHERED, Sound.BLOCK_NOTE_BLOCK_TRUMPET_OXIDIZED
    );

    private final Map<UUID, UUID> pendingTrades = new HashMap<>();
    private final Map<UUID, TradeSession> trades = new HashMap<>();
    private final Map<UUID, MidiPlayer> players = new HashMap<>();
    private final Map<UUID, Villager> editingVillagers = new HashMap<>();
    private final Map<UUID, Integer> editingPages = new HashMap<>();
    private final Map<UUID, List<MerchantRecipe>> editingRecipes = new HashMap<>();
    
    // Sistema de detección de lag
    private Map<UUID, Integer> lagWarnings = new HashMap<>();
    private Map<UUID, Long> lastLagWarning = new HashMap<>();
    private boolean villagerAttackEnabled = false;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("xautral").setExecutor(this);
        getCommand("xautral").setTabCompleter(this);
        
        // Iniciar task de monitoreo de lag
        startLagMonitorTask();
        
        // Iniciar task de ataque de aldeanos
        startVillagerAttackTask();
    }

    @Override
    public void onDisable() {
        players.values().forEach(MidiPlayer::stop);
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        
        if (args.length >= 1 && args[0].equalsIgnoreCase("composer")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
                stopMidi(player);
            } else if (args.length >= 2) {
                playMidi(player, args[1], args.length >= 3 && args[2].equalsIgnoreCase("loop"));
            } else {
                player.sendMessage(color("&eUso: /xautral composer <archivo> [loop]"));
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("trade")) {
            handleTradeCommand(player, args);
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("villagerattack")) {
            if (!player.hasPermission("xautral.op")) {
                player.sendMessage(color("&cNo tienes permiso para usar este comando."));
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("on")) {
                villagerAttackEnabled = true;
                player.sendMessage(color("&aAldeanos ahora pueden atacar a no-muertos y pillagers."));
            } else if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
                villagerAttackEnabled = false;
                player.sendMessage(color("&cAldeanos ya no pueden atacar a no-muertos y pillagers."));
            } else {
                player.sendMessage(color("&eUso: /xautral villagerattack <on|off>"));
            }
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("tps")) {
            if (!player.hasPermission("xautral.op")) {
                player.sendMessage(color("&cNo tienes permiso para usar este comando."));
                return true;
            }
            double tps = Bukkit.getServer().getTPS()[0];
            String tpsStatus = tps >= 19.5 ? "&aEstable" : tps >= 17.0 ? "&eModerado" : tps >= 15.0 ? "&cLag leve" : "&4Lag severo";
            player.sendMessage(color("&eTPS actual: " + String.format("%.2f", tps) + " - " + tpsStatus));
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("checkip")) {
            if (!player.hasPermission("xautral.op")) {
                player.sendMessage(color("&cNo tienes permiso para usar este comando."));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(color("&eUso: /xautral checkip <texto>"));
                return true;
            }
            String textToCheck = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            String obfuscated = obfuscateSponsorship(textToCheck);
            if (!obfuscated.equals(textToCheck)) {
                player.sendMessage(color("&cEl texto contiene patrones de publicidad detectados."));
                player.sendMessage(color("&eTexto original: " + textToCheck));
                player.sendMessage(color("&eTexto ofuscado: " + obfuscated));
            } else {
                player.sendMessage(color("&aEl texto no contiene patrones de publicidad conocidos."));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
                                      String alias, String[] args) {
        if (args.length == 1) return List.of("composer", "trade", "villagerattack", "tps", "checkip");
        if (args.length == 2 && args[0].equalsIgnoreCase("composer")) {
            List<String> values = new ArrayList<>(midiCompositions());
            values.add("stop");
            return values;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("composer")) return List.of("loop");
        if (args.length == 2 && args[0].equalsIgnoreCase("trade")) return List.of("request", "accept", "reject", "confirm");
        if (args.length == 3 && args[0].equalsIgnoreCase("trade") && args[1].equalsIgnoreCase("request")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("villagerattack")) return List.of("on", "off");
        return List.of();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        
        // Sistema de anti-patrocinio - ofuscar links e IPs
        String obfuscatedMessage = obfuscateSponsorship(message);
        
        if (!obfuscatedMessage.equals(message)) {
            event.setMessage(obfuscatedMessage);
            event.getPlayer().sendMessage(color("&c⚠ Tu mensaje ha sido ofuscado por contener publicidad no permitida."));
            
            // Notificar a administradores sobre el intento de patrocinio
            notifyAdminsAboutSponsorship(event.getPlayer(), message);
        } else {
            event.setMessage(color(message));
        }
    }

    private String obfuscateSponsorship(String message) {
        String result = message;
        
        // Patrones de IPs (IPv4) - más estricto
        String ipPattern = "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b";
        result = result.replaceAll(ipPattern, "&k&l[IP OFUSCADA]&r");
        
        // Patrones de dominios comunes
        String[] domains = {
            "\\.com", "\\.net", "\\.org", "\\.io", "\\.gg", "\\.xyz", 
            "\\.tk", "\\.ml", "\\.cf", "\\.ga", "\\.us", "\\.eu",
            "\\.es", "\\.mx", "\\.ar", "\\.cl", "\\.co", "\\.pe",
            "\\.tv", "\\.pro", "\\.dev", "\\.app", "\\.site", "\\.online"
        };
        
        for (String domain : domains) {
            // Ofuscar dominios completos
            result = result.replaceAll("(?i)([a-zA-Z0-9-]+)" + domain, "&k&l[SITIO OFUSCADO]&r");
        }
        
        // Patrones de protocolos
        result = result.replaceAll("(?i)(https?://|ftp://|ws://|wss://)", "&k&l[PROTOCOLO OFUSCADO]&r");
        
        // Patrones de "play." (común en servidores)
        result = result.replaceAll("(?i)play\\.", "&k&l[SERVIDOR OFUSCADO]&r");
        
        // Patrones de "mc." (común en servidores)
        result = result.replaceAll("(?i)mc\\.", "&k&l[SERVIDOR OFUSCADO]&r");
        
        // Patrones de puerto (:25565, etc)
        result = result.replaceAll(":[0-9]{1,5}", "&k&l[PUERTO OFUSCADO]&r");
        
        // Patrones de IP con espacios (anti-bypass)
        result = result.replaceAll("(?i)(\\d)[\\s._-]+(\\d)[\\s._-]+(\\d)[\\s._-]+(\\d)", "&k&l[IP OFUSCADA]&r");
        
        // Patrones de "join" seguido de posibles IPs
        result = result.replaceAll("(?i)join\\s+[a-zA-Z0-9.-]+", "&k&l[SERVIDOR OFUSCADO]&r");
        
        return result;
    }

    private void notifyAdminsAboutSponsorship(Player player, String originalMessage) {
        Bukkit.getScheduler().runTask(this, () -> {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("xautral.op")) {
                    onlinePlayer.sendMessage(color("&4⚠ ALERTA DE PATROCINIO &4⚠"));
                    onlinePlayer.sendMessage(color("&cJugador: " + player.getName()));
                    onlinePlayer.sendMessage(color("&cMensaje original: " + originalMessage));
                    onlinePlayer.sendMessage(color("&ePor favor investiga este intento de publicidad."));
                }
            }
        });
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        event.setMessage(color(event.getMessage()));
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        event.setCommand(color(event.getCommand()));
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        if (!event.getPlayer().isSneaking()) return;
        if (!event.getPlayer().hasPermission("xautral.op")) return;
        
        event.setCancelled(true);
        Villager villager = (Villager) event.getRightClicked();
        Player player = event.getPlayer();
        
        openVillagerTradeUI(player, villager);
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!villagerAttackEnabled) return;
        if (!(event.getEntity() instanceof Villager)) return;
        
        Entity target = event.getTarget();
        if (target == null) return;
        
        // Permitir que aldeanos ataquen a no-muertos y pillagers
        if (target instanceof Zombie || target instanceof Skeleton || target instanceof Pillager) {
            event.setCancelled(false); // Permitir el ataque
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!villagerAttackEnabled) return;
        
        // Cuando un aldeano ataca a un hostil, asegurar que cause daño
        if (event.getDamager() instanceof Villager) {
            Villager villager = (Villager) event.getDamager();
            if (event.getEntity() instanceof Zombie || event.getEntity() instanceof Skeleton || event.getEntity() instanceof Pillager) {
                // Aumentar el daño del aldeano significativamente
                event.setDamage(8.0); // Daño considerable
                
                // Llamar a otros aldeanos cercanos para que ataquen también
                callNearbyVillagers(villager, (LivingEntity) event.getEntity());
            }
        }
    }

    private void callNearbyVillagers(Villager attacker, LivingEntity target) {
        for (Entity nearby : attacker.getNearbyEntities(16, 8, 16)) {
            if (nearby instanceof Villager && nearby != attacker) {
                Villager nearbyVillager = (Villager) nearby;
                // Hacer que otros aldeanos también ataquen al mismo objetivo
                nearbyVillager.setTarget(target);
            }
        }
    }

    // Task para hacer que aldeanos ataquen activamente a hostiles cercanos
    private void startVillagerAttackTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!villagerAttackEnabled) return;
                
                for (World world : Bukkit.getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (!(entity instanceof Villager)) continue;
                        
                        Villager villager = (Villager) entity;
                        // Buscar hostiles cercanos
                        for (Entity nearby : villager.getNearbyEntities(16, 8, 16)) {
                            if (nearby instanceof Zombie || nearby instanceof Skeleton || nearby instanceof Pillager) {
                                LivingEntity hostile = (LivingEntity) nearby;
                                // Hacer que el aldeano ataque al hostil
                                villager.setTarget(hostile);
                                
                                // Si el hostil está cerca, hacer que el aldeano lo ataque físicamente
                                if (villager.getLocation().distance(hostile.getLocation()) < 3) {
                                    // Simular ataque del aldeano
                                    hostile.damage(8.0, villager);
                                }
                                break; // Solo atacar a un objetivo a la vez
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Cada segundo (20 ticks) para respuestas más rápidas
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        
        if (!editingVillagers.containsKey(playerId)) return;
        
        // Cancelar TODOS los clicks en el inventario de edición
        event.setCancelled(true);
        
        // Prevenir cualquier tipo de interacción con items
        event.setResult(org.bukkit.event.Event.Result.DENY);
        
        // Solo procesar clicks en slots válidos del inventario principal
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) return;
        if (event.getSlot() < 0 || event.getSlot() >= 54) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        
        String displayName = meta.getDisplayName();
        if (displayName == null || displayName.isEmpty()) return;
        
        Villager villager = editingVillagers.get(playerId);
        List<MerchantRecipe> recipes = editingRecipes.get(playerId);
        int page = editingPages.getOrDefault(playerId, 0);
        
        // Verificar si es un botón de control (slots 45-53)
        if (event.getSlot() >= 45) {
            if (displayName.contains("Siguiente Página")) {
                int maxPage = (recipes.size() - 1) / 36;
                if (page < maxPage) {
                    editingPages.put(playerId, page + 1);
                    Bukkit.getScheduler().runTaskLater(this, () -> openVillagerTradeUI(player, villager), 1L);
                }
            } else if (displayName.contains("Página Anterior")) {
                if (page > 0) {
                    editingPages.put(playerId, page - 1);
                    Bukkit.getScheduler().runTaskLater(this, () -> openVillagerTradeUI(player, villager), 1L);
                }
            } else if (displayName.contains("Agregar Trade")) {
                if (recipes.size() < 54) {
                    recipes.add(createEmptyRecipe());
                    Bukkit.getScheduler().runTaskLater(this, () -> openVillagerTradeUI(player, villager), 1L);
                } else {
                    player.sendMessage(color("&cMáximo de trades alcanzado (54)."));
                }
            } else if (displayName.contains("Guardar Cambios")) {
                saveVillagerTrades(villager, recipes);
                editingVillagers.remove(playerId);
                editingPages.remove(playerId);
                editingRecipes.remove(playerId);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    player.closeInventory();
                    player.sendMessage(color("&aTrades del aldeano guardados exitosamente."));
                }, 1L);
            } else if (displayName.contains("Cancelar")) {
                editingVillagers.remove(playerId);
                editingPages.remove(playerId);
                editingRecipes.remove(playerId);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    player.closeInventory();
                    player.sendMessage(color("&cEdición cancelada."));
                }, 1L);
            }
        } else {
            // Slots de trades (0-36)
            if (displayName.contains("Eliminar")) {
                int recipeIndex = getRecipeIndexFromItem(clicked, page);
                if (recipeIndex >= 0 && recipeIndex < recipes.size()) {
                    recipes.remove(recipeIndex);
                    Bukkit.getScheduler().runTaskLater(this, () -> openVillagerTradeUI(player, villager), 1L);
                }
            } else if (displayName.contains("Editar")) {
                int recipeIndex = getRecipeIndexFromItem(clicked, page);
                if (recipeIndex >= 0 && recipeIndex < recipes.size()) {
                    // Aquí se podría expandir para edición detallada
                    player.sendMessage(color("&eSistema de edición detallada en desarrollo."));
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        
        if (editingVillagers.containsKey(playerId)) {
            // Cancelar cualquier arrastre de items en el inventario de edición
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Prevenir movimiento de items entre inventarios durante edición
        for (UUID playerId : editingVillagers.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                if (event.getDestination().equals(player.getOpenInventory().getTopInventory()) ||
                    event.getSource().equals(player.getOpenInventory().getTopInventory())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void openVillagerTradeUI(Player player, Villager villager) {
        UUID playerId = player.getUniqueId();
        
        if (!editingRecipes.containsKey(playerId)) {
            editingRecipes.put(playerId, new ArrayList<>(villager.getRecipes()));
        }
        
        List<MerchantRecipe> recipes = editingRecipes.get(playerId);
        int page = editingPages.getOrDefault(playerId, 0);
        
        Inventory ui = Bukkit.createInventory(null, 54, color("&eEditor de Trades - " + villager.getName()));
        
        int startIndex = page * 36;
        int endIndex = Math.min(startIndex + 36, recipes.size());
        
        for (int i = 0; i < 36; i++) {
            int recipeIndex = startIndex + i;
            if (recipeIndex < recipes.size()) {
                MerchantRecipe recipe = recipes.get(recipeIndex);
                ItemStack displayItem = createRecipeDisplayItem(recipe, recipeIndex);
                ui.setItem(i, displayItem);
            }
        }
        
        ItemStack nextPage = createButtonItem(Material.ARROW, color("&aSiguiente Página"), Arrays.asList(
            color("&7Ir a la siguiente página de trades")
        ));
        ItemStack prevPage = createButtonItem(Material.ARROW, color("&aPágina Anterior"), Arrays.asList(
            color("&7Ir a la página anterior de trades")
        ));
        ItemStack addTrade = createButtonItem(Material.EMERALD, color("&aAgregar Trade"), Arrays.asList(
            color("&7Agregar un nuevo trade al aldeano")
        ));
        ItemStack save = createButtonItem(Material.GREEN_WOOL, color("&aGuardar Cambios"), Arrays.asList(
            color("&7Guardar y aplicar los cambios al aldeano")
        ));
        ItemStack cancel = createButtonItem(Material.RED_WOOL, color("&cCancelar"), Arrays.asList(
            color("&7Cancelar la edición sin guardar")
        ));
        
        ui.setItem(45, prevPage);
        ui.setItem(46, nextPage);
        ui.setItem(49, addTrade);
        ui.setItem(51, save);
        ui.setItem(53, cancel);
        
        player.openInventory(ui);
    }

    private ItemStack createRecipeDisplayItem(MerchantRecipe recipe, int index) {
        ItemStack result = recipe.getResult().clone();
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(color("&eTrade #" + (index + 1)));
            lore.add(color("&7Resultado: " + result.getType().name() + " x" + result.getAmount()));
            lore.add("");
            lore.add(color("&7Coste:"));
            
            for (ItemStack ingredient : recipe.getIngredients()) {
                if (ingredient != null && ingredient.getType() != Material.AIR) {
                    lore.add(color("&7- " + ingredient.getAmount() + "x " + ingredient.getType().name()));
                }
            }
            
            lore.add("");
            lore.add(color("&6Máx usos: " + recipe.getMaxUses()));
            lore.add("");
            lore.add(color("&eClick izquierdo: Editar"));
            lore.add(color("&eClick derecho: Eliminar"));
            
            meta.setLore(lore);
            // Hacer el item inmovible
            meta.setUnbreakable(true);
            result.setItemMeta(meta);
        }
        return result;
    }

    private ItemStack createButtonItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            // Hacer el item inmovible usando flags
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private MerchantRecipe createEmptyRecipe() {
        ItemStack result = new ItemStack(Material.STONE);
        ItemStack ingredient = new ItemStack(Material.EMERALD, 1);
        
        MerchantRecipe recipe = new MerchantRecipe(result, 10);
        recipe.addIngredient(ingredient);
        recipe.setExperienceReward(false);
        recipe.setMaxUses(9999);
        
        return recipe;
    }

    private void saveVillagerTrades(Villager villager, List<MerchantRecipe> recipes) {
        try {
            villager.setRecipes(recipes);
        } catch (Exception e) {
            getLogger().warning("Error al guardar trades del aldeano: " + e.getMessage());
        }
    }

    private int getRecipeIndexFromItem(ItemStack item, int page) {
        if (item.getItemMeta() == null || item.getItemMeta().getLore() == null) return -1;
        
        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("Trade #")) {
                try {
                    String numberStr = line.replace("Trade #", "").replace("§e", "").replace("§7", "").trim();
                    int tradeNumber = Integer.parseInt(numberStr);
                    return tradeNumber - 1;
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private void startLagMonitorTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double[] tps = Bukkit.getServer().getTPS();
                double currentTPS = tps[0];
                
                // Detectar lag severo (TPS < 15.0)
                if (currentTPS < 15.0) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.hasPermission("xautral.op")) {
                            UUID playerId = player.getUniqueId();
                            long now = System.currentTimeMillis();
                            
                            // Evitar spam de advertencias (mínimo 30 segundos entre advertencias)
                            if (lastLagWarning.containsKey(playerId) && (now - lastLagWarning.get(playerId)) < 30000) {
                                continue;
                            }
                            
                            int warnings = lagWarnings.getOrDefault(playerId, 0) + 1;
                            lagWarnings.put(playerId, warnings);
                            lastLagWarning.put(playerId, now);
                            
                            player.sendMessage(color("&4⚠ ALERTA DE LAG &4⚠"));
                            player.sendMessage(color("&cTPS actual: " + String.format("%.2f", currentTPS)));
                            player.sendMessage(color("&cAdvertencias de lag acumuladas: " + warnings));
                            
                            if (warnings >= 5) {
                                player.sendMessage(color("&ePor favor verifica la causa del lag."));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 200L); // Cada 10 segundos (200 ticks)
    }

    private File musicFolder() {
        File folder = new File(new File(getServer().getPluginsFolder(), "XautralFunctions"), "music");
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    private List<String> midiCompositions() {
        File[] files = musicFolder().listFiles((dir, name) -> name.toLowerCase().endsWith(".mid"));
        if (files == null) return List.of();
        return Arrays.stream(files)
            .map(file -> file.getName().substring(0, file.getName().length() - 4))
            .sorted()
            .toList();
    }

    private void playMidi(Player requester, String name, boolean loop) {
        File file = new File(musicFolder(), name + ".mid");
        if (!file.isFile()) {
            requester.sendMessage(color("&cNo existe &e" + name + ".mid &cen plugins/XautralFunctions/music/."));
            return;
        }
        stopMidi(requester);
        try {
            MidiPlayer midiPlayer = new MidiPlayer(requester, MidiComposition.read(file), loop);
            players.put(requester.getUniqueId(), midiPlayer);
            midiPlayer.start();
            requester.sendMessage(color("&aReproduciendo MIDI real: &e" + file.getName() + (loop ? " &7(loop)" : "")));
        } catch (IOException | InvalidMidiDataException exception) {
            requester.sendMessage(color("&cNo se pudo leer el MIDI: " + exception.getMessage()));
        }
    }

    private void stopMidi(Player player) {
        MidiPlayer midiPlayer = players.remove(player.getUniqueId());
        if (midiPlayer != null) {
            midiPlayer.stop();
            player.sendMessage(color("&7Reproduccion MIDI detenida."));
        }
    }

    private void handleTradeCommand(Player player, String[] args) {
        if (args[1].equalsIgnoreCase("request")) {
            if (args.length < 3) {
                player.sendMessage(color("&cUso: /xautral trade request <jugador>"));
                return;
            }
            sendTradeRequest(player, args[2]);
        } else if (args[1].equalsIgnoreCase("accept")) {
            UUID requesterId = pendingTrades.remove(player.getUniqueId());
            Player requester = requesterId == null ? null : Bukkit.getPlayer(requesterId);
            if (requester == null || !requester.isOnline()) {
                player.sendMessage(color("&cNo tienes una solicitud valida."));
                return;
            }
            TradeSession trade = new TradeSession(requester, player);
            trades.put(requesterId, trade);
            trades.put(player.getUniqueId(), trade);
            requester.sendMessage(color("&aTrade aceptado. Usa &e/xautral trade confirm &apara confirmar."));
            player.sendMessage(color("&aTrade aceptado. Usa &e/xautral trade confirm &apara confirmar."));
        } else if (args[1].equalsIgnoreCase("reject")) {
            UUID requesterId = pendingTrades.remove(player.getUniqueId());
            if (requesterId != null) {
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester != null) requester.sendMessage(color("&cTu solicitud de trade fue rechazada."));
            }
        } else if (args[1].equalsIgnoreCase("confirm")) {
            TradeSession trade = trades.get(player.getUniqueId());
            if (trade == null) player.sendMessage(color("&cNo tienes un trade activo."));
            else trade.confirm(player);
        } else {
            sendTradeRequest(player, args[1]);
        }
    }

    private void sendTradeRequest(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || target == player) {
            player.sendMessage(color("&cJugador no encontrado."));
            return;
        }
        pendingTrades.put(target.getUniqueId(), player.getUniqueId());
        player.sendMessage(color("&aSolicitud enviada a &e" + target.getName() + "&a."));
        target.sendMessage(color("&e" + player.getName() + " &ate propone un trade. Usa &e/xautral trade accept &ao &e/xautral trade reject&a."));
    }

    private class MidiPlayer {
        private final Player requester;
        private final MidiComposition composition;
        private final boolean loop;
        private BukkitRunnable task;
        private long startNanos;
        private int eventIndex;

        private MidiPlayer(Player requester, MidiComposition composition, boolean loop) {
            this.requester = requester;
            this.composition = composition;
            this.loop = loop;
        }

        private void start() {
            startNanos = System.nanoTime();
            eventIndex = 0;
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!requester.isOnline()) {
                        stop();
                        players.remove(requester.getUniqueId());
                        return;
                    }
                    long elapsedMicros = (System.nanoTime() - startNanos) / 1000L;
                    while (eventIndex < composition.events.size() && composition.events.get(eventIndex).micros <= elapsedMicros) {
                        MidiNote note = composition.events.get(eventIndex++);
                        float pitch = (float) (0.5 * Math.pow(2.0, (note.midiPitch - 60) / 12.0));
                        float volume = Math.max(0.1f, Math.min(1.0f, note.velocity / 127.0f));
                        for (Player listener : Bukkit.getOnlinePlayers()) {
                            listener.playSound(listener.getLocation(), note.sound, volume, pitch);
                        }
                    }
                    if (eventIndex >= composition.events.size() && elapsedMicros >= composition.durationMicros) {
                        if (loop) {
                            startNanos = System.nanoTime();
                            eventIndex = 0;
                        } else {
                            stop();
                            players.remove(requester.getUniqueId());
                        }
                    }
                }
            };
            task.runTaskTimer(XautralFunctions.this, 0L, 1L);
        }

        private void stop() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }

    private static class MidiComposition {
        private final List<MidiNote> events;
        private final long durationMicros;

        private MidiComposition(List<MidiNote> events, long durationMicros) {
            this.events = events;
            this.durationMicros = durationMicros;
        }

        private static MidiComposition read(File file) throws IOException, InvalidMidiDataException {
            Sequence sequence = MidiSystem.getSequence(file);
            List<TempoChange> tempos = new ArrayList<>();
            tempos.add(new TempoChange(0, DEFAULT_TEMPO_MICROS));
            List<RawNote> rawNotes = new ArrayList<>();
            long lastTick = 0;
            for (Track track : sequence.getTracks()) {
                for (int index = 0; index < track.size(); index++) {
                    MidiEvent event = track.get(index);
                    lastTick = Math.max(lastTick, event.getTick());
                    MidiMessage message = event.getMessage();
                    if (message instanceof MetaMessage && ((MetaMessage) message).getType() == 0x51) {
                        byte[] data = ((MetaMessage) message).getData();
                        if (data.length >= 3) {
                            long micros = ((data[0] & 0xFFL) << 16) | ((data[1] & 0xFFL) << 8) | (data[2] & 0xFFL);
                            tempos.add(new TempoChange(event.getTick(), micros));
                        }
                    } else if (message instanceof ShortMessage) {
                        ShortMessage shortMessage = (ShortMessage) message;
                        if (shortMessage.getCommand() == ShortMessage.NOTE_ON && shortMessage.getData2() > 0) {
                            Sound sound = soundForChannel(shortMessage.getChannel(), shortMessage.getData1());
                            rawNotes.add(new RawNote(event.getTick(), shortMessage.getData1(), shortMessage.getData2(), sound));
                        }
                    }
                }
            }
            tempos.sort(Comparator.comparingLong(tempo -> tempo.tick));
            rawNotes.sort(Comparator.comparingLong(note -> note.tick));
            int resolution = sequence.getResolution();
            List<MidiNote> notes = new ArrayList<>();
            for (RawNote raw : rawNotes) {
                notes.add(new MidiNote(microsAt(raw.tick, resolution, tempos), raw.midiPitch, raw.velocity, raw.sound));
            }
            long duration = microsAt(lastTick, resolution, tempos) + 100000L;
            return new MidiComposition(notes, duration);
        }

        private static long microsAt(long tick, int resolution, List<TempoChange> tempos) {
            long micros = 0;
            long previousTick = 0;
            long tempo = DEFAULT_TEMPO_MICROS;
            for (TempoChange change : tempos) {
                if (change.tick > tick) break;
                micros += (change.tick - previousTick) * tempo / resolution;
                previousTick = change.tick;
                tempo = change.microsPerQuarter;
            }
            return micros + (tick - previousTick) * tempo / resolution;
        }

        private static Sound soundForChannel(int channel, int midiPitch) {
            if (channel == 9) {
                if (midiPitch < 40) return Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
                if (midiPitch < 50) return Sound.BLOCK_NOTE_BLOCK_SNARE;
                return Sound.BLOCK_NOTE_BLOCK_HAT;
            }
            return INSTRUMENTS.get(channel % INSTRUMENTS.size());
        }
    }

    private static class TempoChange {
        private final long tick;
        private final long microsPerQuarter;
        private TempoChange(long tick, long microsPerQuarter) { this.tick = tick; this.microsPerQuarter = microsPerQuarter; }
    }

    private static class RawNote {
        private final long tick;
        private final int midiPitch;
        private final int velocity;
        private final Sound sound;
        private RawNote(long tick, int midiPitch, int velocity, Sound sound) { this.tick = tick; this.midiPitch = midiPitch; this.velocity = velocity; this.sound = sound; }
    }

    private static class MidiNote {
        private final long micros;
        private final int midiPitch;
        private final int velocity;
        private final Sound sound;
        private MidiNote(long micros, int midiPitch, int velocity, Sound sound) { this.micros = micros; this.midiPitch = midiPitch; this.velocity = velocity; this.sound = sound; }
    }

    private class TradeSession {
        private final Player first;
        private final Player second;
        private final org.bukkit.inventory.ItemStack firstOffer;
        private final org.bukkit.inventory.ItemStack secondOffer;
        private final Map<UUID, Boolean> confirmed = new HashMap<>();
        private TradeSession(Player first, Player second) { this.first = first; this.second = second; firstOffer = copy(first); secondOffer = copy(second); }
        private org.bukkit.inventory.ItemStack copy(Player player) { return player.getInventory().getItemInMainHand().clone(); }
        private void confirm(Player player) {
            confirmed.put(player.getUniqueId(), true);
            if (confirmed.size() < 2) { player.sendMessage(color("&eConfirmado. Esperando al otro jugador.")); return; }
            first.getInventory().setItemInMainHand(secondOffer.clone());
            second.getInventory().setItemInMainHand(firstOffer.clone());
            first.sendMessage(color("&aTrade completado."));
            second.sendMessage(color("&aTrade completado."));
            trades.remove(first.getUniqueId());
            trades.remove(second.getUniqueId());
        }
    }
}

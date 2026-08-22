package com.permadeath.permadeathplugin;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class PermaDeathPlugin extends JavaPlugin implements Listener, TabCompleter {

    private boolean permadeathEnabled = false;
    private boolean stormAutoActivate = false;
    private boolean manualPermadeath = false;
    private boolean stormWasActive = false;
    private Map<UUID, Location> deathLocations = new HashMap<>();
    private Map<UUID, String> deathCauses = new HashMap<>();
    private Map<UUID, Long> deathTimes = new HashMap<>();
    private Map<UUID, String> deathPlayerNames = new HashMap<>(); // Guardar nombres de jugadores que murieron
    private Set<UUID> revivedOffline = new HashSet<>(); // Jugadores revividos con el libro mientras estaban offline

    @Override
    public void onEnable() {
        getLogger().info("PermaDeathPlugin habilitado!");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("permaedeath").setTabCompleter(this);
        
        // Cargar configuración
        loadConfig();

        // Iniciar task de detección de tormenta y action bar
        startStormTask();
    }

    @Override
    public void onDisable() {
        getLogger().info("PermaDeathPlugin deshabilitado!");
        saveConfig();
    }

    private void loadConfig() {
        getConfig().options().copyDefaults(true);
        saveConfig();
        manualPermadeath = getConfig().getBoolean("permadeath-enabled", false);
        stormAutoActivate = getConfig().getBoolean("storm-auto-activate", false);
        updatePermadeathState();
    }

    private void saveConfigSettings() {
        getConfig().set("permadeath-enabled", manualPermadeath);
        getConfig().set("storm-auto-activate", stormAutoActivate);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("permaedeath")) {
            // Verificar si es admin (OP o tiene permiso)
            if (!sender.isOp() && !sender.hasPermission("permadeath.admin")) {
                sender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage("Este comando solo puede ser usado por jugadores.");
                return true;
            }

            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Uso: /permaedeath <on|off>");
                return true;
            }

            if (args[0].equalsIgnoreCase("on")) {
                activatePermadeath(player);
                return true;
            } else if (args[0].equalsIgnoreCase("off")) {
                deactivatePermadeath(player);
                return true;
            } else if (args[0].equalsIgnoreCase("storm")) {
                toggleStormAutoActivate(player);
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "Opción no válida. Usa: on, off, o storm");
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("permaedeath")) {
            if (args.length == 1) {
                return Arrays.asList("on", "off", "storm");
            }
        }
        return null;
    }

    private void activatePermadeath(Player player) {
        manualPermadeath = true;
        updatePermadeathState();
        saveConfigSettings();
        
        // Reproducir sonido de caballo esqueleto muriendo
        player.playSound(player.getLocation(), Sound.ENTITY_SKELETON_HORSE_DEATH, 1.0f, 1.0f);
        
        // Mostrar mensaje "Permadeath: ON"
        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Permadeath: ON");
        
        getLogger().info("Permadeath activado por " + player.getName());
    }

    private void deactivatePermadeath(Player player) {
        manualPermadeath = false;
        updatePermadeathState();
        saveConfigSettings();
        
        player.sendMessage(ChatColor.GREEN + "Permadeath desactivado");
        
        getLogger().info("Permadeath desactivado por " + player.getName());
    }

    private void toggleStormAutoActivate(Player player) {
        stormAutoActivate = !stormAutoActivate;
        updatePermadeathState();
        saveConfigSettings();
        
        if (stormAutoActivate) {
            player.sendMessage(ChatColor.YELLOW + "Activación automática por tormenta: ON");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Activación automática por tormenta: OFF");
        }
    }

    private boolean isThunderStorm(World world) {
        return world.hasStorm() && world.isThundering();
    }

    private boolean anyStormActive() {
        for (World w : Bukkit.getWorlds()) {
            if (isThunderStorm(w)) return true;
        }
        return false;
    }

    private World getStormWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (isThunderStorm(w)) return w;
        }
        return null;
    }

    private void updatePermadeathState() {
        permadeathEnabled = manualPermadeath || (stormAutoActivate && anyStormActive());
    }

    private void playSoundToAll(Sound sound, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, 1.0f, pitch);
        }
    }

    private void checkStormStates() {
        if (!stormAutoActivate) return;
        boolean nowActive = anyStormActive();
        if (nowActive && !stormWasActive) {
            World w = getStormWorld();
            Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "PERMADEATH: ON") ;
            Bukkit.broadcastMessage(ChatColor.YELLOW + "¡Tormenta eléctrica detectada! Permadeath activado automáticamente.");
            getLogger().info("Permadeath activado por tormenta eléctrica en " + (w != null ? w.getName() : "?"));
            playSoundToAll(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.0f);
        } else if (!nowActive && stormWasActive) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "PERMADEATH: OFF");
            getLogger().info("Permadeath desactivado al terminar la tormenta eléctrica.");
            playSoundToAll(Sound.ENTITY_EVOKER_CAST_SPELL, 0.0f);
            playSoundToAll(Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f);
            playSoundToAll(Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2.0f);
            playSoundToAll(Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.5f);
            playSoundToAll(Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.7f);
        }
        stormWasActive = nowActive;
        updatePermadeathState();
    }

    private void startStormTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkStormStates();

                World w = getStormWorld();
                if (w == null) return;
                String message = ChatColor.GRAY + "Permadeath activado por tormenta - Tiempo restante: " + formatRemaining(w.getThunderDuration());

                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message)
                    );
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Cada segundo
    }

    private String formatRemaining(long ticks) {
        long secs = Math.max(0, ticks / 20);
        long days = secs / 86400;
        long hours = (secs % 86400) / 3600;
        long minutes = (secs % 3600) / 60;
        long seconds = secs % 60;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m " + seconds + "s";
        if (hours > 0) return hours + "h " + minutes + "m " + seconds + "s";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!permadeathEnabled) return;

        // El inventario se transfiere manualmente al cofre; las drops vanilla se descartan.
        event.setKeepInventory(true);
        event.getDrops().clear();
        
        Player player = event.getEntity();
        Location deathLoc = player.getLocation();
        String deathCause = event.getDeathMessage();
        
        // Guardar información de la muerte
        deathLocations.put(player.getUniqueId(), deathLoc);
        deathCauses.put(player.getUniqueId(), deathCause);
        deathTimes.put(player.getUniqueId(), System.currentTimeMillis());
        deathPlayerNames.put(player.getUniqueId(), player.getName());
        
        // El sonido se reproduce solo para jugadores cercanos a la muerte.
        deathLoc.getWorld().playSound(deathLoc, Sound.ITEM_TRIDENT_THUNDER, 9.0f, 0.0f);
        deathLoc.getWorld().playSound(deathLoc, Sound.ITEM_TRIDENT_THUNDER, 9.0f, 2.0f);
        
        // Mostrar coordenadas en el chat
        String coords = String.format("X: %d, Y: %d, Z: %d, Mundo: %s", 
            deathLoc.getBlockX(), deathLoc.getBlockY(), deathLoc.getBlockZ(), deathLoc.getWorld().getName());
        Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " ha muerto en: " + coords);
        
        // Escribir log de muerte
        writeDeathLog(player, deathLoc, deathCause);
        
        // Transferir inventario a cofre
        transferInventoryToChest(player, deathLoc);
        
        // Cambiar a modo espectador
        player.setGameMode(GameMode.SPECTATOR);
        
        // Forzar respawn y devolver la cámara EXACTAMENTE al punto de muerte
        // (posición de los ojos, con el mismo yaw/pitch), en lugar del punto de aparición.
        // Se teletransporta a la ubicación de pies: la cámara del espectador se sitúa a la altura de los ojos.
        final Location spectateLoc = deathLoc.clone();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            
            // Respawnear para salir de la pantalla de muerte (esto lo manda al spawn)
            player.spigot().respawn();
            
            // Volver al modo espectador y teletransportar a la ubicación exacta de muerte
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(spectateLoc);
        }, 1L);
        
        // Generar frases según causa de muerte
        String deathMessage = getDeathMessage(deathCause);
        
        // Programar kick del servidor
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                String kickMessage = ChatColor.RED + "" + ChatColor.BOLD + "Has muerto en modo Permadeath!\n\n" +
                    ChatColor.WHITE + "Causa: " + deathCause + "\n" +
                    ChatColor.WHITE + "Coordenadas: " + coords + "\n" +
                    ChatColor.WHITE + "Hora: " + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "\n\n" +
                    ChatColor.GRAY + "" + ChatColor.ITALIC + deathMessage + "\n\n" +
                    ChatColor.YELLOW + "Tu inventario ha sido transferido a un cofre con tu cabeza.\n" +
                    ChatColor.YELLOW + "Usa el libro de revivir para volver al juego.";
                
                player.kickPlayer(kickMessage);
            }
        }, 100L); // 5 segundos después de la muerte
    }

    private void writeDeathLog(Player player, Location loc, String cause) {
        File logsFolder = new File(getDataFolder(), "death_logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String fileName = dateFormat.format(new Date()) + "_" + player.getName() + ".txt";
        File logFile = new File(logsFolder, fileName);
        
        try (FileWriter writer = new FileWriter(logFile)) {
            writer.write("=== LOG DE MUERTE ===\n");
            writer.write("Jugador: " + player.getName() + "\n");
            writer.write("UUID: " + player.getUniqueId() + "\n");
            writer.write("Fecha: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n");
            writer.write("Causa: " + cause + "\n");
            writer.write("Coordenadas: X=" + loc.getBlockX() + ", Y=" + loc.getBlockY() + ", Z=" + loc.getBlockZ() + "\n");
            writer.write("Mundo: " + loc.getWorld().getName() + "\n");
            writer.write("Pitch: " + loc.getPitch() + "\n");
            writer.write("Yaw: " + loc.getYaw() + "\n");
            writer.write("\n=== INVENTARIO ===\n");
            
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null) {
                    writer.write(item.getType().name() + " x" + item.getAmount() + "\n");
                }
            }
            
            writer.write("\n=== ARMADURA ===\n");
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor != null) {
                    writer.write(armor.getType().name() + "\n");
                }
            }
            
        } catch (IOException e) {
            getLogger().severe("Error al escribir log de muerte: " + e.getMessage());
        }
    }

    private String getDeathMessage(String deathCause) {
        String lowerCause = deathCause.toLowerCase();
        
        if (lowerCause.contains("fell") || lowerCause.contains("caída") || lowerCause.contains("hit the ground")) {
            return "La gravedad no perdona a nadie...";
        } else if (lowerCause.contains("lava") || lowerCause.contains("fire") || lowerCause.contains("quemad")) {
            return "El fuego consume todo a su paso...";
        } else if (lowerCause.contains("drowned") || lowerCause.contains("agua")) {
            return "Las profundidades eran demasiado oscuras...";
        } else if (lowerCause.contains("zombie") || lowerCause.contains("skeleton") || lowerCause.contains("spider") || lowerCause.contains("creeper")) {
            return "Las criaturas de la noche reclamaron tu alma...";
        } else if (lowerCause.contains("starved") || lowerCause.contains("hambre")) {
            return "El hambre es un enemigo silencioso...";
        } else if (lowerCause.contains("wither") || lowerCause.contains("ender dragon") || lowerCause.contains("boss")) {
            return "Los grandes jefes no perdonan...";
        } else if (lowerCause.contains("player") || lowerCause.contains("jugador")) {
            return "La traición acecha en cada esquina...";
        } else if (lowerCause.contains("void") || lowerCause.contains("vacío")) {
            return "El vacío te reclamó para siempre...";
        } else if (lowerCause.contains("magic") || lowerCause.contains("poción")) {
            return "La magia tiene un precio alto...";
        } else {
            return "Tu viaje ha terminado, pero tu legado permanece...";
        }
    }

    private void transferInventoryToChest(Player player, Location deathLoc) {
        // Crear cofre en la ubicación de muerte
        Location chestLoc = deathLoc.clone().add(0, 1, 0);
        chestLoc.getBlock().setType(Material.CHEST);
        
        // Poner cabeza del jugador encima del cofre
        Location headLoc = chestLoc.clone().add(0, 1, 0);
        headLoc.getBlock().setType(Material.PLAYER_HEAD);
        
        org.bukkit.block.Skull skull = (org.bukkit.block.Skull) headLoc.getBlock().getState();
        skull.setOwningPlayer(player);
        skull.update();
        
        // Determinar si necesitamos cofre doble
        int totalItems = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) totalItems++;
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null) totalItems++;
        }
        
        boolean needsDoubleChest = totalItems > 27;
        
        if (needsDoubleChest) {
            // Crear cofre doble
            Location secondChestLoc = chestLoc.clone().add(1, 0, 0);
            secondChestLoc.getBlock().setType(Material.CHEST);
        }

        // Obtener el estado después de colocar ambos cofres para recibir un inventario de 54 slots.
        org.bukkit.block.Chest chest = (org.bukkit.block.Chest) chestLoc.getBlock().getState();
        
        // Transferir items al cofre
        org.bukkit.inventory.Inventory chestInventory = chest.getInventory();
        int slot = 0;
        
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && slot < chestInventory.getSize()) {
                chestInventory.setItem(slot, item);
                slot++;
            }
        }
        
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && slot < chestInventory.getSize()) {
                chestInventory.setItem(slot, armor);
                slot++;
            }
        }
        
        // Crear libro de revivir
        ItemStack reviveBook = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) reviveBook.getItemMeta();
        
        bookMeta.setTitle(ChatColor.RED + "Libro de Revivir - " + player.getName());
        bookMeta.setAuthor("PermaDeath System");
        
        List<String> pages = new ArrayList<>();
        pages.add(ChatColor.RED + "=== LIBRO DE REVIVIR ===\n\n" +
            ChatColor.WHITE + "Jugador: " + player.getName() + "\n" +
            ChatColor.WHITE + "Clic derecho para revivir a " + player.getName() + "\n" +
            ChatColor.WHITE + "Serás teletransportado a donde murió.\n\n" +
            ChatColor.YELLOW + "¡Úsalo con cuidado!");
        
        bookMeta.setPages(pages);
        reviveBook.setItemMeta(bookMeta);
        
        // Poner libro en el primer slot disponible
        if (slot < chestInventory.getSize()) {
            chestInventory.setItem(slot, reviveBook);
        }
        
        // Limpiar inventario del jugador
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasItem() || !event.getAction().equals(org.bukkit.event.block.Action.RIGHT_CLICK_AIR) && 
            !event.getAction().equals(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return;
        
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null || !meta.getTitle().contains("Libro de Revivir")) return;
        
        Player reviver = event.getPlayer();
        
        // Obtener el nombre del jugador que murió desde el título del libro
        String deadPlayerName = null;
        UUID deadPlayerUUID = null;
        
        if (meta.getTitle().contains("Libro de Revivir - ")) {
            deadPlayerName = meta.getTitle().replace(ChatColor.RED + "Libro de Revivir - ", "");
            
            // Buscar el UUID del jugador que murió por nombre
            for (Map.Entry<UUID, String> entry : deathPlayerNames.entrySet()) {
                if (entry.getValue().equals(deadPlayerName)) {
                    deadPlayerUUID = entry.getKey();
                    break;
                }
            }
        }
        
        if (deadPlayerUUID == null || !deathLocations.containsKey(deadPlayerUUID)) {
            reviver.sendMessage(ChatColor.RED + "Este libro de revivir ya no es válido o el jugador no tiene una muerte registrada.");
            return;
        }
        
        // Revivir al jugador (funciona sin importar el modo permadeath)
        Location deathLoc = deathLocations.get(deadPlayerUUID);
        
        // Consumir el libro
        item.setAmount(item.getAmount() - 1);
        
        // Reproducir sonidos de end_portal.spawn (pitch 1, 2, 0) usando comandos
        Location reviveLoc = reviver.getLocation();
        String reviveCoords = String.format("%d %d %d", reviveLoc.getBlockX(), reviveLoc.getBlockY(), reviveLoc.getBlockZ());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
            "playsound minecraft:block.end_portal.spawn master @a " + reviveCoords + " 99 1 1");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
            "playsound minecraft:block.end_portal.spawn master @a " + reviveCoords + " 99 2 1");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
            "playsound minecraft:block.end_portal.spawn master @a " + reviveCoords + " 99 0 1");
        
        // Si el jugador que murió está online, revivirlo
        Player deadPlayer = Bukkit.getPlayer(deadPlayerUUID);
        if (deadPlayer != null && deadPlayer.isOnline()) {
            deadPlayer.setGameMode(GameMode.SURVIVAL);
            deadPlayer.teleport(deathLoc);
            deadPlayer.sendMessage(ChatColor.GREEN + "¡Has sido revivido por " + reviver.getName() + "! Has sido teletransportado a donde moriste.");

            // Mostrar coordenadas en el chat con mensaje de quién revivió a quién
            String coords = String.format("X: %d, Y: %d, Z: %d, Mundo: %s",
                deathLoc.getBlockX(), deathLoc.getBlockY(), deathLoc.getBlockZ(), deathLoc.getWorld().getName());
            Bukkit.broadcastMessage(ChatColor.GREEN + deadPlayerName + " ha sido revivido en: " + coords);
            Bukkit.broadcastMessage(ChatColor.AQUA + reviver.getName() + " revivió a " + deadPlayerName);

            // Limpiar datos de muerte
            revivedOffline.remove(deadPlayerUUID);
            deathLocations.remove(deadPlayerUUID);
            deathCauses.remove(deadPlayerUUID);
            deathTimes.remove(deadPlayerUUID);
            deathPlayerNames.remove(deadPlayerUUID);
        } else {
            // Si no está online, se conserva la ubicación de muerte para revivirlo al conectarse
            revivedOffline.add(deadPlayerUUID);
            reviver.sendMessage(ChatColor.YELLOW + "El jugador " + deadPlayerName + " no está online. Cuando se conecte aparecerá donde murió, en modo supervivencia.");
            Bukkit.broadcastMessage(ChatColor.AQUA + reviver.getName() + " usó el libro de revivir para " + deadPlayerName + " (aparecerá al conectarse)");
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Si fue revivido con el libro mientras estaba offline, aparece donde murió en supervivencia
        if (revivedOffline.contains(playerUUID) && deathLocations.containsKey(playerUUID)) {
            Location deathLoc = deathLocations.get(playerUUID);

            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(deathLoc);
            player.sendMessage(ChatColor.GREEN + "Has aparecido donde moriste, en modo supervivencia.");

            revivedOffline.remove(playerUUID);
            deathLocations.remove(playerUUID);
            deathCauses.remove(playerUUID);
            deathTimes.remove(playerUUID);
            deathPlayerNames.remove(playerUUID);
            return;
        }

        // Si murió y fue expulsado (sin revivir), reaparece en su punto de aparición como espectador
        if (deathLocations.containsKey(playerUUID)) {
            Location spawn = player.getBedSpawnLocation();
            if (spawn == null) {
                spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
            }

            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(spawn);
            player.sendMessage(ChatColor.RED + "Estás muerto en modo Permadeath.");
            player.sendMessage(ChatColor.YELLOW + "Necesitas que alguien use tu libro de revivir para volver al juego.");
        }
    }
}
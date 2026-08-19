package com.minifunc.minifuncplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

public class MiniFuncPlugin extends JavaPlugin implements Listener, org.bukkit.command.CommandExecutor, org.bukkit.command.TabCompleter {
    private static final String TITLE = ChatColor.DARK_GRAY + "MiniFunc Composer";
    private static final int STEP_COUNT = 32;
    private static final int TEMPO_START = 32;
    private static final int TEMPO_COUNT = 8;
    private static final int PALETTE_START = 40;
    private static final int PALETTE_SIZE = 8;
    private static final int PLAY_SLOT = 48;
    private static final int STOP_SLOT = 49;
    private static final int LOOP_SLOT = 50;
    private static final int PAGE_SLOT = 51;
    private static final List<Sound> NOTE_SOUNDS = Arrays.asList(
        Sound.BLOCK_NOTE_BLOCK_HARP, Sound.BLOCK_NOTE_BLOCK_BASS, Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
        Sound.BLOCK_NOTE_BLOCK_SNARE, Sound.BLOCK_NOTE_BLOCK_HAT, Sound.BLOCK_NOTE_BLOCK_GUITAR,
        Sound.BLOCK_NOTE_BLOCK_FLUTE, Sound.BLOCK_NOTE_BLOCK_BELL, Sound.BLOCK_NOTE_BLOCK_CHIME,
        Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_COW_BELL,
        Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, Sound.BLOCK_NOTE_BLOCK_BIT, Sound.BLOCK_NOTE_BLOCK_PLING,
        Sound.BLOCK_NOTE_BLOCK_BANJO, Sound.BLOCK_NOTE_BLOCK_TRUMPET, Sound.BLOCK_NOTE_BLOCK_TRUMPET_EXPOSED,
        Sound.BLOCK_NOTE_BLOCK_TRUMPET_WEATHERED, Sound.BLOCK_NOTE_BLOCK_TRUMPET_OXIDIZED
    );

    private NamespacedKey noteSoundKey;
    private NamespacedKey notePitchKey;
    private NamespacedKey paletteSoundKey;
    private final Map<UUID, ComposerSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> pendingTrades = new HashMap<>();
    private final Map<UUID, TradeSession> trades = new HashMap<>();

    @Override
    public void onEnable() {
        noteSoundKey = new NamespacedKey(this, "note_sound");
        notePitchKey = new NamespacedKey(this, "note_pitch");
        paletteSoundKey = new NamespacedKey(this, "palette_sound");
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("minifunc").setExecutor(this);
        getCommand("minifunc").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        sessions.values().forEach(ComposerSession::stop);
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player) || !command.getName().equalsIgnoreCase("minifunc")) return true;
        if (args.length >= 1 && args[0].equalsIgnoreCase("composer")) {
            String composition = args.length >= 2 ? args[1] : null;
            openComposer((Player) sender, composition);
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("trade")) {
            handleTradeCommand((Player) sender, args);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
                                      String alias, String[] args) {
        if (args.length == 1) return List.of("composer", "trade");
        if (args.length == 2 && args[0].equalsIgnoreCase("composer")) return midiCompositions();
        if (args.length == 2 && args[0].equalsIgnoreCase("trade")) return List.of("request", "accept", "reject", "confirm");
        if (args.length == 3 && args[0].equalsIgnoreCase("trade") && args[1].equalsIgnoreCase("request")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        event.setMessage(ChatColor.translateAlternateColorCodes('&', event.getMessage()));
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        event.setMessage(color(event.getMessage()));
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        event.setCommand(color(event.getCommand()));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void handleTradeCommand(Player player, String[] args) {
        if (args[1].equalsIgnoreCase("request")) {
            if (args.length < 3) {
                player.sendMessage(color("&cUso: /minifunc trade request <jugador>"));
                return;
            }
            sendTradeRequest(player, args[2]);
            return;
        }
        if (args[1].equalsIgnoreCase("accept")) {
            UUID requesterId = pendingTrades.remove(player.getUniqueId());
            if (requesterId == null) {
                player.sendMessage(color("&cNo tienes solicitudes de trade pendientes."));
                return;
            }
            Player requester = Bukkit.getPlayer(requesterId);
            if (requester == null || !requester.isOnline()) {
                player.sendMessage(color("&cEl jugador ya no está conectado."));
                return;
            }
            TradeSession trade = new TradeSession(requester, player);
            trades.put(requesterId, trade);
            trades.put(player.getUniqueId(), trade);
            requester.sendMessage(color("&aTrade aceptado. Usa &e/minifunc trade confirm &apara confirmar."));
            player.sendMessage(color("&aTrade aceptado. Usa &e/minifunc trade confirm &apara confirmar."));
            return;
        }
        if (args[1].equalsIgnoreCase("reject")) {
            UUID requesterId = pendingTrades.remove(player.getUniqueId());
            if (requesterId != null) {
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester != null) requester.sendMessage(color("&cTu solicitud de trade fue rechazada."));
            }
            player.sendMessage(color("&7Solicitud rechazada."));
            return;
        }
        if (args[1].equalsIgnoreCase("confirm")) {
            TradeSession trade = trades.get(player.getUniqueId());
            if (trade == null) {
                player.sendMessage(color("&cNo tienes un trade activo."));
                return;
            }
            trade.confirm(player);
            return;
        }
        sendTradeRequest(player, args[1]);
    }

    private void sendTradeRequest(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || target == player) {
            player.sendMessage(color("&cJugador no encontrado."));
            return;
        }
        pendingTrades.put(target.getUniqueId(), player.getUniqueId());
        player.sendMessage(color("&aSolicitud enviada a &e" + target.getName() + "&a."));
        target.sendMessage(color("&e" + player.getName() + " &ate propone un trade. Usa &e/minifunc trade accept &apara aceptar o &e/minifunc trade reject &apara rechazar."));
    }

    private List<String> midiCompositions() {
        File[] files = musicFolder().listFiles((dir, name) -> name.toLowerCase().endsWith(".mid"));
        if (files == null) return List.of();
        return Arrays.stream(files).map(file -> file.getName().substring(0, file.getName().length() - 4)).toList();
    }

    private File musicFolder() {
        File folder = new File(new File(getServer().getPluginsFolder(), "MiniFuncPlugin"), "music");
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    private void openComposer(Player player, String composition) {
        ComposerSession session = sessions.get(player.getUniqueId());
        Inventory inventory;
        if (session == null) {
            ComposerHolder holder = new ComposerHolder();
            inventory = Bukkit.createInventory(holder, 54, TITLE);
            holder.inventory = inventory;
            session = new ComposerSession(player, inventory);
            sessions.put(player.getUniqueId(), session);
        } else {
            inventory = session.inventory;
        }
        if (composition != null && !loadMidi(composition, session)) return;
        render(inventory, session);
        player.openInventory(inventory);
    }

    private boolean loadMidi(String composition, ComposerSession session) {
        File midi = new File(musicFolder(), composition + ".mid");
        if (!midi.isFile()) {
            session.player.sendMessage(color("&cNo existe &e" + composition + ".mid &cen plugins/MiniFuncPlugin/music/."));
            return false;
        }
        try {
            Sequence sequence = MidiSystem.getSequence(midi);
            List<MidiNote> midiNotes = new ArrayList<>();
            for (Track track : sequence.getTracks()) {
                for (int index = 0; index < track.size(); index++) {
                    MidiEvent event = track.get(index);
                    MidiMessage message = event.getMessage();
                    if (!(message instanceof ShortMessage)) continue;
                    ShortMessage shortMessage = (ShortMessage) message;
                    if (shortMessage.getCommand() == ShortMessage.NOTE_ON && shortMessage.getData2() > 0) {
                        midiNotes.add(new MidiNote(event.getTick(), shortMessage.getData1(), shortMessage.getChannel()));
                    }
                }
            }
            midiNotes.sort(java.util.Comparator.comparingLong(note -> note.tick));
            Arrays.fill(session.notes, null);
            for (int index = 0; index < Math.min(STEP_COUNT, midiNotes.size()); index++) {
                MidiNote note = midiNotes.get(index);
                Sound sound = NOTE_SOUNDS.get(note.channel % NOTE_SOUNDS.size());
                int pitch = Math.max(0, Math.min(24, note.midiPitch - 48));
                session.notes[index] = noteItem(sound, pitch);
            }
            session.player.sendMessage(color("&aComposicion cargada: &e" + midi.getName() + " &7(" + Math.min(STEP_COUNT, midiNotes.size()) + " notas)"));
            return true;
        } catch (IOException | InvalidMidiDataException exception) {
            session.player.sendMessage(color("&cNo se pudo leer el MIDI: " + exception.getMessage()));
            return false;
        }
    }

    private static class MidiNote {
        private final long tick;
        private final int midiPitch;
        private final int channel;

        private MidiNote(long tick, int midiPitch, int channel) {
            this.tick = tick;
            this.midiPitch = midiPitch;
            this.channel = channel;
        }
    }

    private void render(Inventory inventory, ComposerSession session) {
        inventory.clear();
        for (int slot = 0; slot < STEP_COUNT; slot++) {
            ItemStack note = session.notes[slot];
            inventory.setItem(slot, note == null ? item(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + "Paso " + (slot + 1)) : note);
        }
        for (int slot = 0; slot < TEMPO_COUNT; slot++) {
            boolean active = session.playing && slot == session.activeStep / 4;
            inventory.setItem(TEMPO_START + slot, item(active ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                active ? ChatColor.GREEN + "Ritmo activo" : ChatColor.GRAY + "Ritmo " + (slot + 1)));
        }
        int pageStart = session.palettePage * PALETTE_SIZE;
        for (int index = 0; index < PALETTE_SIZE; index++) {
            int soundIndex = pageStart + index;
            if (soundIndex >= NOTE_SOUNDS.size()) break;
            inventory.setItem(PALETTE_START + index, paletteItem(NOTE_SOUNDS.get(soundIndex)));
        }
        inventory.setItem(PLAY_SLOT, item(Material.LIME_CONCRETE, ChatColor.GREEN + "Reproducir"));
        inventory.setItem(STOP_SLOT, item(Material.RED_CONCRETE, ChatColor.RED + "Detener"));
        inventory.setItem(LOOP_SLOT, item(Material.REPEATER, ChatColor.YELLOW + "Loop: " + (session.loop ? "ON" : "OFF")));
        int pages = (NOTE_SOUNDS.size() + PALETTE_SIZE - 1) / PALETTE_SIZE;
        inventory.setItem(PAGE_SLOT, item(Material.CLOCK, ChatColor.AQUA + "Instrumentos " + (session.palettePage + 1) + " / " + pages));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ComposerHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ComposerSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (slot >= PALETTE_START && slot < PALETTE_START + PALETTE_SIZE) {
            int soundIndex = session.palettePage * PALETTE_SIZE + slot - PALETTE_START;
            if (soundIndex >= NOTE_SOUNDS.size()) return;
            if (event.isShiftClick()) {
                session.nextPalettePage();
                render(event.getView().getTopInventory(), session);
                return;
            }
            session.selectedSound = NOTE_SOUNDS.get(soundIndex);
            player.sendMessage(ChatColor.GRAY + "Instrumento seleccionado: " + session.selectedSound.name());
        } else if (slot < STEP_COUNT) {
            if (event.getClick() == ClickType.MIDDLE) {
                session.notes[slot] = null;
            } else if (event.isRightClick() && session.notes[slot] != null) {
                changePitch(session.notes[slot], 1);
            } else if (session.selectedSound != null) {
                session.notes[slot] = noteItem(session.selectedSound, 12);
            }
            render(event.getView().getTopInventory(), session);
        } else if (slot >= TEMPO_START && slot < TEMPO_START + TEMPO_COUNT) {
            if (event.isRightClick()) session.stop();
            else if (event.isShiftClick()) {
                session.loop = !session.loop;
                render(event.getView().getTopInventory(), session);
            } else session.start();
        } else if (slot == PLAY_SLOT) {
            session.start();
        } else if (slot == STOP_SLOT) {
            session.stop();
        } else if (slot == LOOP_SLOT) {
            session.loop = !session.loop;
            render(event.getView().getTopInventory(), session);
        } else if (slot == PAGE_SLOT) {
            session.nextPalettePage();
            render(event.getView().getTopInventory(), session);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ComposerHolder)) return;
        // La sesión continúa reproduciendo y queda disponible al reabrir el compositor.
    }

    private ItemStack paletteItem(Sound sound) {
        ItemStack item = item(Material.NOTE_BLOCK, ChatColor.AQUA + sound.name());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(paletteSoundKey, PersistentDataType.STRING, sound.name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack noteItem(Sound sound, int pitch) {
        ItemStack item = item(Material.NOTE_BLOCK, ChatColor.WHITE + sound.name());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(noteSoundKey, PersistentDataType.STRING, sound.name());
        meta.getPersistentDataContainer().set(notePitchKey, PersistentDataType.INTEGER, pitch);
        meta.setLore(List.of(ChatColor.GRAY + "Pitch: " + pitch,
            ChatColor.YELLOW + "Click derecho: subir pitch",
            ChatColor.RED + "Rueda: eliminar nota"));
        item.setItemMeta(meta);
        return item;
    }

    private void changePitch(ItemStack item, int delta) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        int pitch = meta.getPersistentDataContainer().getOrDefault(notePitchKey, PersistentDataType.INTEGER, 12);
        pitch = (pitch + delta) % 25;
        meta.getPersistentDataContainer().set(notePitchKey, PersistentDataType.INTEGER, pitch);
        meta.setLore(List.of(ChatColor.GRAY + "Pitch: " + pitch,
            ChatColor.YELLOW + "Click derecho: subir pitch",
            ChatColor.RED + "Rueda: eliminar nota"));
        item.setItemMeta(meta);
    }

    private ItemStack item(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private class ComposerSession {
        private final Player player;
        private final Inventory inventory;
        private final ItemStack[] notes = new ItemStack[STEP_COUNT];
        private Sound selectedSound = NOTE_SOUNDS.get(0);
        private boolean loop;
        private int palettePage;
        private int activeStep;
        private boolean playing;
        private BukkitRunnable task;

        private ComposerSession(Player player, Inventory inventory) {
            this.player = player;
            this.inventory = inventory;
        }

        private void start() {
            stop();
            playing = true;
            activeStep = 0;
            render(inventory, this);
            task = new BukkitRunnable() {
                int step;

                @Override
                public void run() {
                    if (!player.isOnline()) {
                        stop();
                        return;
                    }
                    activeStep = step;
                    render(inventory, ComposerSession.this);
                    ItemStack note = notes[step];
                    if (note != null) {
                        ItemMeta meta = note.getItemMeta();
                        Sound sound = Sound.valueOf(meta.getPersistentDataContainer().get(noteSoundKey, PersistentDataType.STRING));
                        int pitch = meta.getPersistentDataContainer().getOrDefault(notePitchKey, PersistentDataType.INTEGER, 12);
                        float soundPitch = (float) (0.5 * Math.pow(2.0, (pitch - 12) / 12.0));
                        for (Player listener : Bukkit.getOnlinePlayers()) {
                            listener.playSound(listener.getLocation(), sound, 1.0f, soundPitch);
                        }
                    }
                    step++;
                    if (step >= STEP_COUNT) {
                        if (loop) step = 0;
                        else {
                            stop();
                            return;
                        }
                    }
                }
            };
            task.runTaskTimer(MiniFuncPlugin.this, 0L, 4L);
        }

        private void stop() {
            if (task != null) {
                task.cancel();
                task = null;
            }
            playing = false;
            activeStep = 0;
            render(inventory, this);
        }

        private void nextPalettePage() {
            int pages = (NOTE_SOUNDS.size() + PALETTE_SIZE - 1) / PALETTE_SIZE;
            palettePage = (palettePage + 1) % pages;
        }
    }

    private class TradeSession {
        private final Player first;
        private final Player second;
        private final ItemStack firstOffer;
        private final ItemStack secondOffer;
        private final Map<UUID, Boolean> confirmations = new HashMap<>();

        private TradeSession(Player first, Player second) {
            this.first = first;
            this.second = second;
            this.firstOffer = copyHand(first);
            this.secondOffer = copyHand(second);
            first.sendMessage(color("&7Tu oferta es tu objeto de la mano principal: &f" + itemName(firstOffer)));
            second.sendMessage(color("&7Tu oferta es tu objeto de la mano principal: &f" + itemName(secondOffer)));
        }

        private ItemStack copyHand(Player player) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            return hand == null || hand.getType() == Material.AIR ? new ItemStack(Material.AIR) : hand.clone();
        }

        private String itemName(ItemStack item) {
            return item.getType() == Material.AIR ? "nada" : item.getType().name() + " x" + item.getAmount();
        }

        private void confirm(Player player) {
            if (!matches(player, player == first ? firstOffer : secondOffer)) {
                player.sendMessage(color("&cTu oferta cambió. El trade fue cancelado por seguridad."));
                close();
                return;
            }
            confirmations.put(player.getUniqueId(), true);
            player.sendMessage(color("&eHas confirmado. Esperando al otro jugador..."));
            Player other = player == first ? second : first;
            other.sendMessage(color("&e" + player.getName() + " confirmó el trade."));
            if (confirmations.size() == 2) complete();
        }

        private boolean matches(Player player, ItemStack expected) {
            ItemStack current = player.getInventory().getItemInMainHand();
            if (current == null) current = new ItemStack(Material.AIR);
            return current.isSimilar(expected) && current.getAmount() == expected.getAmount();
        }

        private void complete() {
            first.getInventory().setItemInMainHand(secondOffer.clone());
            second.getInventory().setItemInMainHand(firstOffer.clone());
            first.sendMessage(color("&aTrade completado."));
            second.sendMessage(color("&aTrade completado."));
            close();
        }

        private void close() {
            trades.remove(first.getUniqueId());
            trades.remove(second.getUniqueId());
        }
    }

    private static class ComposerHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}

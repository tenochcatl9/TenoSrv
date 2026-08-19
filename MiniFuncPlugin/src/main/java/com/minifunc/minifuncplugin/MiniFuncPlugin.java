package com.minifunc.minifuncplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
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

public class MiniFuncPlugin extends JavaPlugin implements Listener, org.bukkit.command.CommandExecutor, org.bukkit.command.TabCompleter {
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

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("minifunc").setExecutor(this);
        getCommand("minifunc").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        players.values().forEach(MidiPlayer::stop);
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player) || !command.getName().equalsIgnoreCase("minifunc")) return true;
        Player player = (Player) sender;
        if (args.length >= 1 && args[0].equalsIgnoreCase("composer")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
                stopMidi(player);
            } else if (args.length >= 2) {
                playMidi(player, args[1], args.length >= 3 && args[2].equalsIgnoreCase("loop"));
            } else {
                player.sendMessage(color("&eUso: /minifunc composer <archivo> [loop]"));
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("trade")) {
            handleTradeCommand(player, args);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
                                      String alias, String[] args) {
        if (args.length == 1) return List.of("composer", "trade");
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
        return List.of();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        event.setMessage(color(event.getMessage()));
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

    private File musicFolder() {
        File folder = new File(new File(getServer().getPluginsFolder(), "MiniFuncPlugin"), "music");
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
            requester.sendMessage(color("&cNo existe &e" + name + ".mid &cen plugins/MiniFuncPlugin/music/."));
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
                player.sendMessage(color("&cUso: /minifunc trade request <jugador>"));
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
            requester.sendMessage(color("&aTrade aceptado. Usa &e/minifunc trade confirm &apara confirmar."));
            player.sendMessage(color("&aTrade aceptado. Usa &e/minifunc trade confirm &apara confirmar."));
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
        target.sendMessage(color("&e" + player.getName() + " &ate propone un trade. Usa &e/minifunc trade accept &ao &e/minifunc trade reject&a."));
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
            task.runTaskTimer(MiniFuncPlugin.this, 0L, 1L);
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

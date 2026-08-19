package com.xautral.xautralfunctions;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.sound.midi.*;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class XautralFunctions extends JavaPlugin implements Listener, TabCompleter {
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
    private final Map<UUID, MidiPlayer> midiPlayers = new HashMap<>();

    // Patrones para censura
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(?i)(?:https?://)?(?:[a-z0-9-]+\\.)+(?:me|gg|com|net|org|es|tk|cf|ga|gq|ml|xyz|io|lat|info|biz|site|online|tech|shop|store)|" +
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b|" +
            "\\b(?:playit|aternos|tcp|ngrok|localhost|127\\.0\\.0\\.1)\\b"
    );

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("xautral").setExecutor(this);
        getCommand("xautral").setTabCompleter(this);
        startLagMonitorTask();
        getLogger().info("XautralFunctions mejorado activado.");
    }

    @Override
    public void onDisable() {
        midiPlayers.values().forEach(MidiPlayer::stop);
        midiPlayers.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length >= 1 && args[0].equalsIgnoreCase("composer")) {
            handleComposerCommand(player, args);
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("trade")) {
            handleTradeCommand(player, args);
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("tps")) {
            player.sendMessage(color("&eTPS actual: " + String.format("%.2f", Bukkit.getServer().getTPS()[0])));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("composer", "trade", "tps").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("composer")) {
                List<String> list = new ArrayList<>(List.of("stop", "loop"));
                File musicDir = new File(getDataFolder(), "music");
                if (musicDir.exists() && musicDir.isDirectory()) {
                    File[] files = musicDir.listFiles((dir, name) -> name.endsWith(".mid"));
                    if (files != null) {
                        for (File f : files) list.add(f.getName().replace(".mid", ""));
                    }
                }
                return list.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("trade")) {
                return List.of("request", "accept").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trade") && args[1].equalsIgnoreCase("request")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void handleComposerCommand(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
            stopMidi(player);
            player.sendMessage(color("&cMúsica detenida."));
        } else if (args.length >= 2) {
            boolean loop = args.length >= 3 && args[2].equalsIgnoreCase("loop");
            playMidi(player, args[1], loop);
            player.sendMessage(color("&aReproduciendo: &f" + args[1] + (loop ? " (Bucle)" : "")));
        }
    }

    private void handleTradeCommand(Player p, String[] args) {
        if (args.length < 2) return;
        if (args[1].equalsIgnoreCase("request") && args.length >= 3) {
            Player t = Bukkit.getPlayer(args[2]);
            if (t != null) {
                pendingTrades.put(t.getUniqueId(), p.getUniqueId());
                p.sendMessage(color("&aSolicitud enviada a " + t.getName()));
                t.sendMessage(color("&e" + p.getName() + " quiere tradear. Usa &6/xautral trade accept"));
            }
        } else if (args[1].equalsIgnoreCase("accept")) {
            UUID rid = pendingTrades.remove(p.getUniqueId());
            if (rid == null) {
                p.sendMessage(color("&cNo tienes solicitudes pendientes."));
                return;
            }
            Player r = Bukkit.getPlayer(rid);
            if (r != null) {
                TradeSession ts = new TradeSession(r, p);
                trades.put(rid, ts);
                trades.put(p.getUniqueId(), ts);
                p.sendMessage(color("&aTrade iniciado con " + r.getName()));
                r.sendMessage(color("&a" + p.getName() + " aceptó el trade."));
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String original = event.getMessage();
        String filtered = XauFunctions.obfuscateLinks(original);
        
        if (!original.equals(filtered)) {
            event.getPlayer().sendMessage(color("&c[Filtro] Evita compartir enlaces o IPs sospechosas."));
        }
        
        event.setMessage(color(filtered));
    }

    private void startLagMonitorTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getTPS()[0] < 16.0) {
                    Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("xautral.op"))
                            .forEach(p -> p.sendMessage(color("&c⚠ Servidor con LAG detectado (TPS: " + String.format("%.1f", Bukkit.getTPS()[0]) + ")")));
                }
            }
        }.runTaskTimer(this, 0L, 600L);
    }

    private void stopMidi(Player p) {
        MidiPlayer mp = midiPlayers.remove(p.getUniqueId());
        if (mp != null) mp.stop();
    }

    private void playMidi(Player r, String n, boolean l) {
        File f = new File(new File(getDataFolder(), "music"), n + ".mid");
        if (!f.exists()) return;
        try {
            stopMidi(r);
            MidiPlayer mp = new MidiPlayer(r, MidiComposition.read(f), l);
            midiPlayers.put(r.getUniqueId(), mp);
            mp.start();
        } catch (Exception ignored) {}
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    // Clase interna para utilidades de filtrado
    public static class XauFunctions {
        public static String obfuscateLinks(String input) {
            Matcher m = LINK_PATTERN.matcher(input);
            StringBuilder sb = new StringBuilder();
            int last = 0;
            while (m.find()) {
                sb.append(input, last, m.start());
                String found = m.group();
                sb.append(obfuscate(found));
                last = m.end();
            }
            sb.append(input.substring(last));
            return sb.toString();
        }

        private static String obfuscate(String s) {
            if (s.length() <= 3) return "***";
            return s.charAt(0) + "***" + s.charAt(s.length() - 1);
        }
    }

    private class MidiPlayer {
        private final Player req; private final MidiComposition comp; private final boolean loop;
        private BukkitRunnable task; private long startNanos; private int idx;
        private MidiPlayer(Player r, MidiComposition c, boolean l) { this.req = r; this.comp = c; this.loop = l; }
        private void start() {
            startNanos = System.nanoTime(); idx = 0;
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    long el = (System.nanoTime() - startNanos) / 1000L;
                    while (idx < comp.events.size() && comp.events.get(idx).micros <= el) {
                        MidiNote n = comp.events.get(idx++);
                        float p = (float) (0.5 * Math.pow(2.0, (n.pitch - 60) / 12.0));
                        Bukkit.getOnlinePlayers().forEach(l -> l.playSound(l.getLocation(), n.sound, 1f, p));
                    }
                    if (idx >= comp.events.size()) { if (loop) { startNanos = System.nanoTime(); idx = 0; } else stop(); }
                }
            };
            task.runTaskTimer(XautralFunctions.this, 0, 1);
        }
        private void stop() { if (task != null) task.cancel(); }
    }

    private static class MidiComposition {
        private final List<MidiNote> events;
        private MidiComposition(List<MidiNote> e) { this.events = e; }
        private static MidiComposition read(File f) throws Exception {
            Sequence s = MidiSystem.getSequence(f);
            List<MidiNote> notes = new ArrayList<>();
            for (Track t : s.getTracks()) {
                for (int i = 0; i < t.size(); i++) {
                    MidiMessage m = t.get(i).getMessage();
                    if (m instanceof ShortMessage sm && sm.getCommand() == ShortMessage.NOTE_ON) {
                        notes.add(new MidiNote(t.get(i).getTick() * 500, sm.getData1(), INSTRUMENTS.get(sm.getChannel() % INSTRUMENTS.size())));
                    }
                }
            }
            return new MidiComposition(notes);
        }
    }
    private record MidiNote(long micros, int pitch, Sound sound) {}
    private class TradeSession {
        private final Player f, s; private final ItemStack fo, so;
        private TradeSession(Player a, Player b) { f=a; s=b; fo=f.getInventory().getItemInMainHand().clone(); so=s.getInventory().getItemInMainHand().clone(); }
    }
}

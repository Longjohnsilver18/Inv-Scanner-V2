package de.invscanner;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * 1. Namen holen: Tab-Liste + die Namensvorschläge, die der Server für "/invsee " liefert
 *    (damit auch Spieler auf anderen Teilservern des Netzwerks erfasst werden).
 * 2. Pro Spieler "/invsee Name" senden. Das Fenster wird per Mixin abgefangen und unsichtbar
 *    gelesen (Fallback: sichtbares Fenster, das danach geschlossen wird).
 */
public final class ScanEngine {
    enum Phase { IDLE, NAMES, SEND, WAIT_OPEN, WAIT_READ }

    public static final Map<String, Map<Item, Integer>> DATA = new LinkedHashMap<>();
    public static final Set<String> FAILED = new LinkedHashSet<>();
    public static int total;
    public static long finishedAt;
    public static boolean interrupted;
    public static String notice = "";
    public static String lastInfo = "";

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.\\-]{1,32}");
    private static final String[] PREFIXES = buildPrefixes();

    private static volatile Phase phase = Phase.IDLE;
    private static final List<String> queue = new ArrayList<>();
    private static final Map<String, String> found = new LinkedHashMap<>();
    private static CompletableFuture<Suggestions> pending;
    private static int prefixIdx, pendingTicks, timeouts, tabCount, sugCount;
    private static int index, sinceSend, waitTicks, readTicks;
    /** Nachbildung des Vanilla-Spam-Zählers: +20 pro Befehl, -1 pro Tick, Kick bei über 200. */
    private static int spam;
    private static boolean resumeMode;
    private static String current = "";
    private static long lastHash;
    private static ScreenHandler capHandler;
    private static int capSync;

    private static String[] buildPrefixes() {
        List<String> l = new ArrayList<>();
        l.add("");
        for (char c = 'a'; c <= 'z'; c++) l.add(String.valueOf(c));
        for (char c = '0'; c <= '9'; c++) l.add(String.valueOf(c));
        l.add("_");
        return l.toArray(new String[0]);
    }

    public static boolean running() {
        return phase != Phase.IDLE;
    }

    public static boolean canResume() {
        return interrupted && !running();
    }

    public static int remainingEstimate() {
        return Math.max(0, total - DATA.size() - FAILED.size());
    }

    // ------------------------------------------------------------------ Start / Abbruch

    public static boolean start(MinecraftClient mc, boolean resume) {
        if (running() || mc.player == null || mc.getNetworkHandler() == null) return false;
        resumeMode = resume;
        if (!resume) {
            DATA.clear();
            FAILED.clear();
        }
        interrupted = false;
        notice = "";
        found.clear();
        for (String n : mc.getNetworkHandler().getCommandSource().getPlayerNames()) addName(n);
        tabCount = found.size();
        sugCount = 0;
        prefixIdx = 0;
        timeouts = 0;
        pending = null;
        capHandler = null;
        sinceSend = 1000;
        spam = 0;
        phase = Phase.NAMES;
        return true;
    }

    /** Sendet einen Befehl (ohne führenden Schrägstrich). Standard: unsigniert, damit es keine Signatur-Kicks gibt. */
    public static void sendCommand(MinecraftClient mc, String command) {
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        if (nh == null) return;
        if (Config.data.unsigned) nh.sendPacket(new CommandExecutionC2SPacket(command));
        else nh.sendChatCommand(command);
    }

    public static void cancel(MinecraftClient mc) {
        if (phase == Phase.IDLE) return;
        if (phase == Phase.WAIT_READ) closeCurrent(mc);
        capHandler = null;
        pending = null;
        phase = Phase.IDLE;
        interrupted = true;
        finishedAt = System.currentTimeMillis();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("Inv Scanner: abgebrochen – " + DATA.size()
                    + " Spieler gescannt. Mit Ä → „Fortsetzen“ geht es weiter."), false);
        }
    }

    private static void addName(String n) {
        if (n != null && NAME.matcher(n).matches()) found.putIfAbsent(n.toLowerCase(Locale.ROOT), n);
    }

    private static boolean containsIgnoreCase(Collection<String> c, String n) {
        for (String s : c) if (s.equalsIgnoreCase(n)) return true;
        return false;
    }

    private static boolean isInvScreen(Screen s) {
        return s instanceof HandledScreen<?> h && !(h.getScreenHandler() instanceof PlayerScreenHandler);
    }

    // ------------------------------------------------------------------ Tick

    public static void tick(MinecraftClient mc) {
        if (phase == Phase.IDLE) return;
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        if (mc.player == null || nh == null) {
            onConnectionLost();
            return;
        }
        sinceSend++;
        if (spam > 0) spam--;
        switch (phase) {
            case NAMES -> tickNames(mc);
            case SEND -> {
                if (mc.currentScreen != null) return; // pausiert, solange ein Fenster offen ist
                boolean budgetOk = Config.data.tempo == 0 || spam + 20 <= 160;
                if (sinceSend >= Config.tempoTicks() && budgetOk) {
                    current = queue.get(index);
                    sendCommand(mc, Config.data.command + " " + current);
                    spam += 20;
                    sinceSend = 0;
                    waitTicks = 0;
                    capHandler = null;
                    phase = Phase.WAIT_OPEN;
                    mc.player.sendMessage(Text.literal("Inv Scanner: " + (DATA.size() + FAILED.size() + 1) + "/" + total
                            + "  " + current + "   (Ä = Abbruch)"), true);
                }
            }
            case WAIT_OPEN -> {
                if (isInvScreen(mc.currentScreen)) {
                    phase = Phase.WAIT_READ;
                    readTicks = 0;
                    lastHash = -1;
                } else if (++waitTicks > 100) {
                    FAILED.add(current);
                    advance(mc);
                }
            }
            case WAIT_READ -> {
                ScreenHandler sh = currentHandler(mc);
                if (sh == null) {
                    FAILED.add(current);
                    advance(mc);
                    return;
                }
                long hs = hash(mc, sh);
                readTicks++;
                boolean stable = hs == lastHash && (hs != 0 || readTicks >= 6);
                if ((readTicks >= 2 && stable) || readTicks >= 12) {
                    DATA.put(current, read(mc, sh));
                    closeCurrent(mc);
                    advance(mc);
                } else {
                    lastHash = hs;
                }
            }
            default -> {
            }
        }
    }

    private static void onConnectionLost() {
        phase = Phase.IDLE;
        capHandler = null;
        pending = null;
        interrupted = true;
        finishedAt = System.currentTimeMillis();
        if (Config.data.tempo < Config.TEMPO_TICKS.length - 1) {
            Config.data.tempo++;
            Config.save();
            notice = "Verbindung war weg – Tempo jetzt: " + Config.TEMPO_NAMES[Config.data.tempo] + ". „Fortsetzen“ macht weiter.";
        } else {
            notice = "Verbindung war weg. „Fortsetzen“ macht weiter.";
        }
    }

    // ------------------------------------------------------------------ Namen holen

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CompletableFuture<Suggestions> request(MinecraftClient mc, String prefix) {
        try {
            ClientPlayNetworkHandler nh = mc.getNetworkHandler();
            CommandDispatcher d = nh.getCommandDispatcher();
            String input = Config.data.command + " " + prefix;
            ParseResults pr = d.parse(input, nh.getCommandSource());
            return d.getCompletionSuggestions(pr, input.length());
        } catch (Throwable t) {
            return null;
        }
    }

    private static void tickNames(MinecraftClient mc) {
        for (int n = 0; n < 8; n++) {
            if (pending == null) {
                if (prefixIdx >= PREFIXES.length) {
                    finishNames(mc);
                    return;
                }
                pending = request(mc, PREFIXES[prefixIdx]);
                pendingTicks = 0;
                if (pending == null) {
                    prefixIdx = PREFIXES.length;
                    continue;
                }
            }
            if (pending.isDone()) {
                int before = found.size();
                try {
                    Suggestions s = pending.getNow(null);
                    if (s != null) for (Suggestion g : s.getList()) addName(g.getText());
                } catch (Throwable ignored) {
                }
                sugCount += found.size() - before;
                pending = null;
                prefixIdx++;
            } else {
                if (++pendingTicks > 60) {
                    pending.cancel(false);
                    pending = null;
                    timeouts++;
                    if (prefixIdx == 0 || timeouts >= 2) prefixIdx = PREFIXES.length;
                    else prefixIdx++;
                }
                break;
            }
        }
        if (sinceSend % 10 == 0 && mc.player != null) {
            mc.player.sendMessage(Text.literal("Inv Scanner: Spielerliste wird geladen ("
                    + Math.min(prefixIdx, PREFIXES.length) + "/" + PREFIXES.length + ")  (Ä = Abbruch)"), true);
        }
    }

    private static void finishNames(MinecraftClient mc) {
        String me = mc.player.getName().getString();
        queue.clear();
        for (String n : found.values()) {
            if (n.equalsIgnoreCase(me)) continue;
            if (resumeMode && (containsIgnoreCase(DATA.keySet(), n) || containsIgnoreCase(FAILED, n))) continue;
            queue.add(n);
        }
        lastInfo = found.size() + " Namen gefunden (Tab-Liste " + tabCount + ", Server-Vorschläge +" + sugCount + ")";
        if (queue.isEmpty()) {
            phase = Phase.IDLE;
            mc.player.sendMessage(Text.literal("Inv Scanner: keine (weiteren) Spieler gefunden. " + lastInfo), false);
            return;
        }
        total = DATA.size() + FAILED.size() + queue.size();
        index = 0;
        sinceSend = 1000;
        phase = Phase.SEND;
    }

    // ------------------------------------------------------------------ Fenster abfangen / lesen

    /** Wird vom Mixin aufgerufen. true = Fenster nicht anzeigen, Inhalt unsichtbar lesen. */
    public static boolean captureOpen(OpenScreenS2CPacket packet) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (phase != Phase.WAIT_OPEN || mc.player == null || !mc.isOnThread()) return false;
        try {
            ScreenHandlerType<?> type = packet.getScreenHandlerType();
            if (type == null) return false;
            ScreenHandler h = type.create(packet.getSyncId(), mc.player.getInventory());
            if (h == null) return false;
            mc.player.currentScreenHandler = h;
            capHandler = h;
            capSync = packet.getSyncId();
            phase = Phase.WAIT_READ;
            readTicks = 0;
            lastHash = -1;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static ScreenHandler currentHandler(MinecraftClient mc) {
        if (capHandler != null) return capHandler;
        Screen s = mc.currentScreen;
        return isInvScreen(s) ? ((HandledScreen<?>) s).getScreenHandler() : null;
    }

    private static void closeCurrent(MinecraftClient mc) {
        if (mc.player == null) return;
        if (capHandler != null) {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(capSync));
            }
            if (mc.player.currentScreenHandler == capHandler) {
                mc.player.currentScreenHandler = mc.player.playerScreenHandler;
            }
            capHandler = null;
        } else if (isInvScreen(mc.currentScreen)) {
            mc.player.closeHandledScreen();
        }
    }

    private static void advance(MinecraftClient mc) {
        index++;
        if (index < queue.size()) {
            phase = Phase.SEND;
            return;
        }
        phase = Phase.IDLE;
        interrupted = false;
        finishedAt = System.currentTimeMillis();
        String msg = "Inv Scanner: fertig – " + DATA.size() + " Spieler gescannt";
        if (!FAILED.isEmpty()) {
            List<String> f = new ArrayList<>(FAILED);
            msg += ", nicht erreichbar (" + f.size() + "): " + String.join(", ", f.subList(0, Math.min(8, f.size())))
                    + (f.size() > 8 ? " ..." : "");
        }
        mc.player.sendMessage(Text.literal(msg), false);
        if (mc.currentScreen == null) mc.setScreen(new ResultsScreen());
        else mc.player.sendMessage(Text.literal("Ergebnisse: Ä drücken → „Letzte Daten“."), false);
    }

    private static long hash(MinecraftClient mc, ScreenHandler sh) {
        long h = 0;
        for (Slot slot : sh.slots) {
            if (slot.inventory == mc.player.getInventory()) continue;
            ItemStack st = slot.getStack();
            if (!st.isEmpty()) h = h * 31 + System.identityHashCode(st.getItem()) * 7L + st.getCount();
        }
        return h;
    }

    private static Map<Item, Integer> read(MinecraftClient mc, ScreenHandler sh) {
        Map<Item, Integer> m = new HashMap<>();
        for (Slot slot : sh.slots) {
            if (slot.inventory == mc.player.getInventory()) continue; // eigenes Inventar überspringen
            ItemStack st = slot.getStack();
            if (st.isEmpty()) continue;
            m.merge(st.getItem(), st.getCount(), Integer::sum);
            if (Config.data.containers) {
                ContainerComponent cc = st.get(DataComponentTypes.CONTAINER);
                if (cc != null) {
                    for (ItemStack in : cc.iterateNonEmpty()) {
                        m.merge(in.getItem(), in.getCount() * st.getCount(), Integer::sum);
                    }
                }
            }
        }
        return m;
    }

    private ScanEngine() {}
}

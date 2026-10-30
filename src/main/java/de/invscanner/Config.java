package de.invscanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Config {
    public static class RuleData {
        String item = "minecraft:diamond";
        int mode = 0;
        int amount = 1;
        int prio = 2;
    }

    public static class Data {
        List<RuleData> rules = new ArrayList<>();
        int version = 0;
        int tempo = 1;
        boolean unsigned = true;
        boolean containers = true;
        boolean matchAll = false;
        String command = "invsee";
    }

    public static final int MAX_RULES = 6;
    /** Mindestabstand zwischen zwei Befehlen in Ticks (20 Ticks = 1 s). */
    public static final int[] TEMPO_TICKS = {4, 5, 22, 40, 80};
    public static final String[] TEMPO_NAMES = {"Turbo (nur Ops)", "Auto (schnell, spam-sicher)", "Normal (1,1 s)",
            "Langsam (2 s)", "Vorsichtig (4 s)"};
    public static final String[] TEMPO_SHORT = {"Turbo", "Auto", "Normal", "Langsam", "Vorsichtig"};

    public static Data data = new Data();
    public static final List<Rule> RULES = new ArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("invscanner.json");
    }

    public static int tempoTicks() {
        return TEMPO_TICKS[Math.floorMod(data.tempo, TEMPO_TICKS.length)];
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Data d = GSON.fromJson(Files.readString(p), Data.class);
                if (d != null) data = d;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (data.command == null || data.command.isBlank()) data.command = "invsee";
        data.command = data.command.trim();
        if (data.command.startsWith("/")) data.command = data.command.substring(1);
        if (data.version < 3) {
            data.tempo = 1;
            data.unsigned = true;
            data.version = 3;
        }
        data.tempo = Math.floorMod(data.tempo, TEMPO_TICKS.length);
        RULES.clear();
        if (data.rules != null) {
            for (RuleData rd : data.rules) {
                Identifier id = Identifier.tryParse(rd.item);
                if (id == null) continue;
                Item item = Registries.ITEM.get(id);
                if (item == Items.AIR) continue;
                Rule r = new Rule(item);
                r.mode = Math.floorMod(rd.mode, 4);
                r.amount = Math.max(0, rd.amount);
                r.prio = Math.min(3, Math.max(1, rd.prio));
                RULES.add(r);
            }
        }
    }

    public static void save() {
        try {
            data.rules = new ArrayList<>();
            for (Rule r : RULES) {
                RuleData rd = new RuleData();
                rd.item = Registries.ITEM.getId(r.item).toString();
                rd.mode = r.mode;
                rd.amount = r.amount;
                rd.prio = r.prio;
                data.rules.add(rd);
            }
            Files.writeString(path(), GSON.toJson(data));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Config() {}
}

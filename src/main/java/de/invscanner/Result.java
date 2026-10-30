package de.invscanner;

import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record Result(String name, List<Hit> hits, int score, int topPrio) {
    public record Hit(Item item, int count, int prio) {}

    public int totalCount() {
        int t = 0;
        for (Hit h : hits) t += h.count();
        return t;
    }

    /** Wertet die gespeicherten Scan-Daten gegen die Regeln aus. */
    public static List<Result> evaluate(List<Rule> rules, boolean matchAll) {
        List<Result> out = new ArrayList<>();
        if (rules.isEmpty()) return out;
        for (Map.Entry<String, Map<Item, Integer>> e : ScanEngine.DATA.entrySet()) {
            List<Hit> hits = new ArrayList<>();
            int score = 0, top = 0;
            for (Rule r : rules) {
                int c = e.getValue().getOrDefault(r.item, 0);
                if (r.matches(c)) {
                    hits.add(new Hit(r.item, c, r.prio));
                    score += r.weight();
                    top = Math.max(top, r.prio);
                }
            }
            boolean ok = matchAll ? hits.size() == rules.size() : !hits.isEmpty();
            if (!ok) continue;
            hits.sort((a, b) -> a.prio() != b.prio() ? Integer.compare(b.prio(), a.prio()) : Integer.compare(b.count(), a.count()));
            out.add(new Result(e.getKey(), hits, score, top));
        }
        out.sort((a, b) -> {
            if (a.score() != b.score()) return Integer.compare(b.score(), a.score());
            if (a.totalCount() != b.totalCount()) return Integer.compare(b.totalCount(), a.totalCount());
            return a.name().compareToIgnoreCase(b.name());
        });
        return out;
    }
}

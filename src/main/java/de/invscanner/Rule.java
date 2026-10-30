package de.invscanner;

import net.minecraft.item.Item;

/** Eine Suchregel: Item + Mengenbedingung + Priorität. */
public final class Rule {
    public static final String[] MODES = {"Egal", "Größer", "Kleiner", "Gleich"};
    public static final String[] PRIOS = {"", "Niedrig", "Mittel", "Hoch"};

    public Item item;
    public int mode = 0;   // 0 egal, 1 größer, 2 kleiner, 3 gleich
    public int amount = 1;
    public int prio = 2;   // 1 niedrig, 2 mittel, 3 hoch

    public Rule(Item item) {
        this.item = item;
    }

    /** Das Item muss immer mindestens 1x vorhanden sein. */
    public boolean matches(int count) {
        if (count <= 0) return false;
        return switch (mode) {
            case 1 -> count > amount;
            case 2 -> count < amount;
            case 3 -> count == amount;
            default -> true;
        };
    }

    public int weight() {
        return prio == 3 ? 100 : prio == 2 ? 10 : 1;
    }

    public int prioColor() {
        return prio == 3 ? 0xFFFFC83D : prio == 2 ? 0xFF63B3FF : 0xFF8FA9D4;
    }
}

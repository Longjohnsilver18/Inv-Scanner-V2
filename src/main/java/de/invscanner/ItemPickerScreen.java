package de.invscanner;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.Consumer;

public class ItemPickerScreen extends BaseScreen {
    static final int COLS = 14, ROWS = 6, CELL = 24, PER = COLS * ROWS;

    private static List<Item> ALL;
    private static final Map<Item, String> DISPLAY = new HashMap<>();
    private static final Map<Item, String> SEARCH = new HashMap<>();

    private final Screen parent;
    private final Consumer<Item> onPick;
    private TextFieldWidget search;
    private String query = "";
    private int page = 0;
    private final List<ItemCell> cells = new ArrayList<>();
    private List<Item> filtered = new ArrayList<>();

    public ItemPickerScreen(Screen parent, Consumer<Item> onPick) {
        super("ITEM AUSWÄHLEN", "Suchen, dann anklicken", 380, 252);
        this.parent = parent;
        this.onPick = onPick;
    }

    private static List<Item> all() {
        if (ALL == null) {
            ALL = new ArrayList<>();
            for (Item it : Registries.ITEM) {
                if (it == Items.AIR) continue;
                String name = it.getName().getString();
                DISPLAY.put(it, name);
                SEARCH.put(it, (name + " " + Registries.ITEM.getId(it).getPath().replace('_', ' ')).toLowerCase(Locale.ROOT));
                ALL.add(it);
            }
            ALL.sort((a, b) -> DISPLAY.get(a).compareToIgnoreCase(DISPLAY.get(b)));
        }
        return ALL;
    }

    @Override
    protected void build() {
        cells.clear();
        search = new TextFieldWidget(textRenderer, left + 14, top + 30, pw - 28, 18, Text.literal("Suche"));
        search.setPlaceholder(Text.literal("Item suchen (z. B. Diamant, netherite, shulker) ..."));
        search.setMaxLength(40);
        search.setText(query);
        search.setChangedListener(s -> {
            query = s;
            page = 0;
            refilter();
        });
        add(search);

        int gx = left + (pw - COLS * CELL) / 2, gy = top + 54;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                ItemCell cell = add(new ItemCell(gx + c * CELL, gy + r * CELL, CELL, b -> {
                    ItemCell ic = (ItemCell) b;
                    if (ic.item != null) {
                        onPick.accept(ic.item);
                        client.setScreen(parent);
                    }
                }));
                cells.add(cell);
            }
        }
        add(new BlueButton(left + 14, top + 226, 34, 20, "<", b -> turn(-1)));
        add(new BlueButton(left + 52, top + 226, 34, 20, ">", b -> turn(1)));
        add(new BlueButton(left + pw - 14 - 110, top + 226, 110, 20, "Abbrechen", b -> close()));
        refilter();
    }

    @Override
    protected void afterInit() {
        setInitialFocus(search);
    }

    private int pages() {
        return Math.max(1, (filtered.size() + PER - 1) / PER);
    }

    private void turn(int d) {
        page = Math.floorMod(page + d, pages());
        refilter();
    }

    private void refilter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        filtered = new ArrayList<>();
        for (Item it : all()) {
            if (q.isEmpty() || SEARCH.get(it).contains(q)) filtered.add(it);
        }
        page = Math.min(page, pages() - 1);
        for (int i = 0; i < cells.size(); i++) {
            int idx = page * PER + i;
            cells.get(i).setItem(idx < filtered.size() ? filtered.get(idx) : null);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    protected void draw(DrawContext c, int mx, int my, float delta) {
        String hover = null;
        for (ItemCell cell : cells) {
            if (cell.visible && cell.item != null && cell.isHovered()) {
                hover = DISPLAY.get(cell.item) + "   (" + Registries.ITEM.getId(cell.item) + ")";
            }
        }
        if (hover != null) {
            Ui.fit(c, textRenderer, hover, left + 14, top + 203, pw - 28, Ui.TEXT);
        } else {
            Ui.smallLeft(c, textRenderer, "Klick auf ein Item, um es zur Suche hinzuzufügen.", left + 14, top + 204, Ui.MUTED, 0.8f);
        }
        Ui.smallLeft(c, textRenderer, "Seite " + (page + 1) + "/" + pages() + "  ·  " + filtered.size() + " Items",
                left + 92, top + 233, Ui.MUTED, 0.85f);
    }
}

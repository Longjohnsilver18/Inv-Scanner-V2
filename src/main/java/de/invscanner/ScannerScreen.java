package de.invscanner;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

public class ScannerScreen extends BaseScreen {
    private String status = "";

    public ScannerScreen() {
        super("INV SCANNER", "Ä öffnet  ·  ESC schließt", 380, 252);
    }

    @Override
    protected void build() {
        List<Rule> rules = Config.RULES;
        for (int i = 0; i < rules.size(); i++) {
            final Rule r = rules.get(i);
            int y = top + 42 + i * 22;
            add(new BlueButton(left + 174, y, 50, 18, Rule.MODES[r.mode], b -> {
                r.mode = (r.mode + 1) % 4;
                clearAndInit();
            }));
            TextFieldWidget f = new TextFieldWidget(textRenderer, left + 228, y, 44, 18, Text.literal("Menge"));
            f.setMaxLength(6);
            f.setTextPredicate(s -> s.matches("\\d{0,6}"));
            f.setText(String.valueOf(r.amount));
            f.setChangedListener(s -> r.amount = s.isEmpty() ? 0 : Integer.parseInt(s));
            f.visible = r.mode != 0;
            add(f);
            int pf = r.prio == 3 ? 0xFF8A6410 : r.prio == 2 ? Ui.BTN : 0xFF39475F;
            add(new BlueButton(left + 276, y, 56, 18, Rule.PRIOS[r.prio], b -> {
                r.prio = r.prio % 3 + 1;
                clearAndInit();
            }, pf));
            add(new BlueButton(left + 338, y, 28, 18, "X", b -> {
                Config.RULES.remove(r);
                clearAndInit();
            }, 0xFF9B2335));
        }

        int by = top + 180;
        add(new BlueButton(left + 14, by, 112, 18, "+ Item suchen", b -> {
            if (Config.RULES.size() >= Config.MAX_RULES) {
                status = "Maximal " + Config.MAX_RULES + " Items.";
                return;
            }
            client.setScreen(new ItemPickerScreen(this, this::addItem));
        }).primary());
        add(new BlueButton(left + 134, by, 112, 18, "Item aus Hand", b -> handItem()));
        add(new BlueButton(left + 254, by, 112, 18, "Liste leeren", b -> {
            Config.RULES.clear();
            clearAndInit();
        }));

        int oy = top + 202;
        add(new BlueButton(left + 14, oy, 84, 18,
                Config.data.matchAll ? "Treffer: ALLE" : "Treffer: mind. 1", b -> {
            Config.data.matchAll = !Config.data.matchAll;
            clearAndInit();
        }));
        add(new BlueButton(left + 102, oy, 84, 18,
                "Tempo: " + Config.TEMPO_SHORT[Math.floorMod(Config.data.tempo, Config.TEMPO_SHORT.length)], b -> {
            Config.data.tempo = (Config.data.tempo + 1) % Config.TEMPO_SHORT.length;
            status = Config.TEMPO_NAMES[Config.data.tempo];
            clearAndInit();
        }));
        add(new BlueButton(left + 190, oy, 84, 18,
                "Shulker: " + (Config.data.containers ? "Ja" : "Nein"), b -> {
            Config.data.containers = !Config.data.containers;
            clearAndInit();
        }));
        add(new BlueButton(left + 278, oy, 88, 18,
                "Befehl: " + (Config.data.unsigned ? "unsigniert" : "normal"), b -> {
            Config.data.unsigned = !Config.data.unsigned;
            status = Config.data.unsigned ? "Befehle ohne Chat-Signatur (empfohlen)" : "Befehle wie im Chat (signiert)";
            clearAndInit();
        }));

        int sy = top + 226;
        add(new BlueButton(left + 14, sy, 128, 20, "SCAN STARTEN", b -> startScan(false)).primary());
        BlueButton resume = add(new BlueButton(left + 146, sy, 100, 20,
                "Fortsetzen (" + ScanEngine.remainingEstimate() + ")", b -> startScan(true)));
        resume.active = ScanEngine.canResume();
        BlueButton last = add(new BlueButton(left + 250, sy, 116, 20,
                "Letzte Daten (" + ScanEngine.DATA.size() + ")", b -> client.setScreen(new ResultsScreen())));
        last.active = !ScanEngine.DATA.isEmpty();
    }

    private void addItem(Item item) {
        for (Rule r : Config.RULES) {
            if (r.item == item) {
                status = "Das Item ist schon in der Liste.";
                return;
            }
        }
        if (Config.RULES.size() >= Config.MAX_RULES) {
            status = "Maximal " + Config.MAX_RULES + " Items.";
            return;
        }
        Config.RULES.add(new Rule(item));
        status = "";
    }

    private void handItem() {
        if (client.player == null) return;
        ItemStack st = client.player.getMainHandStack();
        if (st.isEmpty()) {
            status = "Du hältst nichts in der Hand.";
        } else {
            addItem(st.getItem());
        }
        clearAndInit();
    }

    private void startScan(boolean resume) {
        if (Config.RULES.isEmpty()) {
            status = "Füge zuerst mindestens ein Item hinzu.";
            return;
        }
        Config.save();
        if (!ScanEngine.start(client, resume)) {
            status = "Scan konnte nicht gestartet werden.";
            return;
        }
        client.setScreen(null);
    }

    @Override
    public void removed() {
        Config.save();
    }

    @Override
    protected void drawBelow(DrawContext c, int mx, int my, float delta) {
        Ui.smallLeft(c, textRenderer, "ITEM", left + 16, top + 31, Ui.MUTED, 0.7f);
        Ui.smallLeft(c, textRenderer, "MENGE", left + 176, top + 31, Ui.MUTED, 0.7f);
        Ui.smallLeft(c, textRenderer, "PRIORITÄT", left + 278, top + 31, Ui.MUTED, 0.7f);
        if (!status.isEmpty()) Ui.smallRight(c, textRenderer, status, left + pw - 14, top + 170, Ui.WARN, 0.8f);
        else if (!ScanEngine.notice.isEmpty()) Ui.fit(c, textRenderer, ScanEngine.notice, left + 14, top + 170, pw - 28, Ui.WARN);
        else if (!ScanEngine.lastInfo.isEmpty()) Ui.smallLeft(c, textRenderer, "Letzter Scan: " + ScanEngine.lastInfo, left + 14, top + 170, Ui.MUTED, 0.75f);

        List<Rule> rules = Config.RULES;
        for (int i = 0; i < rules.size(); i++) {
            Rule r = rules.get(i);
            int y = top + 42 + i * 22;
            c.fill(left + 10, y - 2, left + pw - 10, y + 20, i % 2 == 0 ? 0x4408173A : 0x330B1C3F);
            c.fill(left + 10, y - 2, left + 13, y + 20, r.prioColor());
            c.drawItem(new ItemStack(r.item), left + 17, y + 1);
            Ui.fit(c, textRenderer, r.item.getName().getString(), left + 37, y + 5, 132, Ui.TEXT);
            if (r.mode == 0) Ui.smallLeft(c, textRenderer, "beliebig", left + 232, y + 5, Ui.MUTED, 0.8f);
        }
        if (rules.isEmpty()) {
            Ui.small(c, textRenderer, "Noch keine Items – füge unten welche hinzu.", width / 2, top + 100, Ui.MUTED, 1f);
            Ui.small(c, textRenderer, "Mengen: Größer / Kleiner / Gleich / Egal.  Kleiner zählt nur Spieler, die das Item haben.",
                    width / 2, top + 116, Ui.MUTED, 0.65f);
        }
    }
}

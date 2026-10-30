package de.invscanner;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ResultsScreen extends BaseScreen {
    static final int PER = 7;

    private final List<Result> results;
    private int page = 0;
    private String status = "";

    public ResultsScreen() {
        super("ERGEBNISSE", null, 380, 252);
        this.results = Result.evaluate(Config.RULES, Config.data.matchAll);
    }

    private int pages() {
        return Math.max(1, (results.size() + PER - 1) / PER);
    }

    @Override
    protected void build() {
        page = Math.min(page, pages() - 1);
        for (int i = 0; i < PER; i++) {
            int idx = page * PER + i;
            if (idx >= results.size()) break;
            final String name = results.get(idx).name();
            int y = top + 42 + i * 24;
            add(new BlueButton(left + 314, y + 2, 52, 18, "Öffnen", b -> open(name)).primary());
        }
        add(new BlueButton(left + 14, top + 226, 32, 20, "<", b -> {
            page = Math.floorMod(page - 1, pages());
            clearAndInit();
        }));
        add(new BlueButton(left + 50, top + 226, 32, 20, ">", b -> {
            page = Math.floorMod(page + 1, pages());
            clearAndInit();
        }));
        BlueButton rescan = add(new BlueButton(left + 190, top + 226, 86, 20, "Neu scannen", b -> {
            if (ScanEngine.start(client, false)) client.setScreen(null);
            else status = "Scan konnte nicht gestartet werden.";
        }));
        rescan.active = !Config.RULES.isEmpty();
        add(new BlueButton(left + 280, top + 226, 86, 20, "Zurück", b -> client.setScreen(new ScannerScreen())));
    }

    private void open(String name) {
        if (client.getNetworkHandler() == null) return;
        client.setScreen(null);
        ScanEngine.sendCommand(client, Config.data.command + " " + name);
    }

    @Override
    protected void drawBelow(DrawContext c, int mx, int my, float delta) {
        String info = results.size() + " Treffer  ·  " + ScanEngine.DATA.size() + " von " + ScanEngine.total + " Spielern gescannt";
        if (!ScanEngine.FAILED.isEmpty()) info += "  ·  " + ScanEngine.FAILED.size() + " nicht erreichbar";
        if (ScanEngine.finishedAt > 0) info += "  ·  " + new SimpleDateFormat("HH:mm:ss").format(new Date(ScanEngine.finishedAt));
        Ui.smallLeft(c, textRenderer, info, left + 14, top + 30, Ui.MUTED, 0.8f);
        if (!status.isEmpty()) Ui.smallRight(c, textRenderer, status, left + pw - 14, top + 214, Ui.WARN, 0.8f);

        for (int i = 0; i < PER; i++) {
            int idx = page * PER + i;
            if (idx >= results.size()) break;
            Result res = results.get(idx);
            int y = top + 42 + i * 24;
            c.fill(left + 10, y, left + pw - 10, y + 22, i % 2 == 0 ? 0x4408173A : 0x330B1C3F);
            int pc = res.topPrio() == 3 ? 0xFFFFC83D : res.topPrio() == 2 ? 0xFF63B3FF : 0xFF8FA9D4;
            c.fill(left + 10, y, left + 13, y + 22, pc);
            Ui.fit(c, textRenderer, res.name(), left + 20, y + 7, 108, Ui.TEXT);
            int hx = left + 134;
            for (int k = 0; k < Math.min(3, res.hits().size()); k++) {
                Result.Hit h = res.hits().get(k);
                c.drawItem(new ItemStack(h.item()), hx, y + 3);
                int col = h.prio() == 3 ? Ui.WARN : h.prio() == 2 ? Ui.BORDER_HI : Ui.MUTED;
                Ui.smallLeft(c, textRenderer, Ui.fmt(h.count()), hx + 18, y + 7, col, 0.85f);
                hx += 58;
            }
            if (res.hits().size() > 3) {
                Ui.smallLeft(c, textRenderer, "+" + (res.hits().size() - 3), hx, y + 7, Ui.MUTED, 0.8f);
            }
        }
        if (results.isEmpty()) {
            boolean none = ScanEngine.DATA.isEmpty();
            Ui.small(c, textRenderer, none ? "Noch kein Scan durchgeführt." : "Kein Spieler erfüllt die Regeln.",
                    width / 2, top + 110, Ui.TEXT, 1f);
            Ui.small(c, textRenderer, none ? "Starte einen Scan im Hauptfenster."
                            : "Ändere die Regeln über „Zurück“ – die Daten bleiben gespeichert.",
                    width / 2, top + 126, Ui.MUTED, 0.75f);
        }
        Ui.smallLeft(c, textRenderer, "Seite " + (page + 1) + "/" + pages(), left + 90, top + 233, Ui.MUTED, 0.85f);
    }
}

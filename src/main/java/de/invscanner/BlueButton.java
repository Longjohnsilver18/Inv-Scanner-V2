package de.invscanner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class BlueButton extends ButtonWidget {
    public boolean primary = false;
    public int fill;

    public BlueButton(int x, int y, int w, int h, String label, PressAction action) {
        this(x, y, w, h, label, action, Ui.BTN);
    }

    public BlueButton(int x, int y, int w, int h, String label, PressAction action, int fill) {
        super(x, y, w, h, Text.literal(label), action, DEFAULT_NARRATION_SUPPLIER);
        this.fill = fill;
    }

    public BlueButton primary() {
        primary = true;
        return this;
    }

    @Override
    public void renderWidget(DrawContext c, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = width, h = height;
        boolean hot = active && isHovered();
        int base = primary ? 0xFF2F7BE0 : fill;
        int top = Ui.shade(base, hot ? 1.5f : 1.3f);
        int bot = Ui.shade(base, hot ? 1.05f : 0.78f);
        c.fill(x, y, x + w, y + h, hot ? Ui.BORDER_HI : (primary ? Ui.ACCENT : Ui.BORDER));
        c.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, bot);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, 0x30FFFFFF);
        if (!active) c.fill(x, y, x + w, y + h, 0x99000000);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int tw = Math.max(1, tr.getWidth(getMessage()));
        float sc = Math.min(1f, (w - 4f) / tw);
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x + w / 2f, y + h / 2f);
        m.scale(sc, sc);
        c.drawText(tr, getMessage(), -tw / 2, -4, active ? Ui.WHITE : 0xFF8A8A8A, true);
        m.popMatrix();
    }
}

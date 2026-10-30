package de.invscanner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

public final class Ui {
    public static final int BG_TOP = 0xFF13264D;
    public static final int BG_BOT = 0xFF0A1430;
    public static final int BORDER = 0xFF2F6BD0;
    public static final int BORDER_HI = 0xFF63B3FF;
    public static final int ACCENT = 0xFF4DA3FF;
    public static final int BTN = 0xFF2457B8;
    public static final int TEXT = 0xFFE8F1FF;
    public static final int MUTED = 0xFF8FA9D4;
    public static final int GOOD = 0xFF5CFF8A;
    public static final int BAD = 0xFFFF7B7B;
    public static final int WARN = 0xFFFFC83D;
    public static final int WHITE = 0xFFFFFFFF;

    public static int shade(int argb, float f) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        r = Math.min(255, Math.max(0, Math.round(r * f)));
        g = Math.min(255, Math.max(0, Math.round(g * f)));
        b = Math.min(255, Math.max(0, Math.round(b * f)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alpha(int rgb, float a) {
        return (Math.round(Math.min(1f, Math.max(0f, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static String fmt(long n) {
        return String.format(Locale.GERMANY, "%,d", n);
    }

    public static void outline(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y + 1, x + 1, y + h - 1, col);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, col);
    }

    /** Schlichtes blaues Fenster mit Titelleiste. */
    public static void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x66000000);
        c.fill(x, y, x + w, y + h, BORDER);
        c.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, BG_TOP, BG_BOT);
        c.fillGradient(x + 1, y + 1, x + w - 1, y + 23, 0xFF21488F, 0xFF173469);
        c.fill(x + 1, y + 23, x + w - 1, y + 24, BORDER_HI);
    }

    public static void title(DrawContext c, TextRenderer tr, String text, int x, int y) {
        c.drawTextWithShadow(tr, Text.literal(text).formatted(Formatting.BOLD), x, y, WHITE);
    }

    public static void card(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, 0x55050B1A);
        outline(c, x, y, w, h, 0xFF22447F);
    }

    public static void smallLeft(DrawContext c, TextRenderer tr, String text, int x, int y, int color, float scale) {
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawText(tr, text, 0, 0, color, false);
        m.popMatrix();
    }

    public static void small(DrawContext c, TextRenderer tr, String text, int cx, int y, int color, float scale) {
        smallLeft(c, tr, text, Math.round(cx - tr.getWidth(text) * scale / 2f), y, color, scale);
    }

    public static void smallRight(DrawContext c, TextRenderer tr, String text, int rightX, int y, int color, float scale) {
        smallLeft(c, tr, text, Math.round(rightX - tr.getWidth(text) * scale), y, color, scale);
    }

    /** Text links, wird bei Platzmangel verkleinert statt abgeschnitten. */
    public static void fit(DrawContext c, TextRenderer tr, String text, int x, int y, int maxW, int color) {
        int w = Math.max(1, tr.getWidth(text));
        float s = Math.min(1f, maxW / (float) w);
        smallLeft(c, tr, text, x, y + Math.round((1f - s) * 4f), color, s);
    }

    public static void sound(SoundEvent e, float pitch, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(e, pitch, volume));
    }

    public static void ping() {
        sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f, 0.8f);
    }

    private Ui() {}
}

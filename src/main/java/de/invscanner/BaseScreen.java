package de.invscanner;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseScreen extends Screen {
    protected final int pw, ph;
    protected int left, top;
    private final List<ClickableWidget> widgets = new ArrayList<>();
    private final String heading;
    private final String hint;

    protected BaseScreen(String title, String hint, int pw, int ph) {
        super(Text.literal(title));
        this.heading = title;
        this.hint = hint;
        this.pw = pw;
        this.ph = ph;
    }

    protected <T extends ClickableWidget> T add(T w) {
        widgets.add(w);
        return w;
    }

    @Override
    protected void init() {
        widgets.clear();
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        build();
        for (ClickableWidget w : widgets) addDrawableChild(w);
        afterInit();
    }

    protected abstract void build();

    /** Wird nach dem Anlegen aller Widgets aufgerufen (z. B. für den Fokus). */
    protected void afterInit() {
    }

    /** Zeichnet unter den Buttons (Zeilenhintergründe usw.). */
    protected void drawBelow(DrawContext c, int mx, int my, float delta) {
    }

    /** Zeichnet über den Buttons. */
    protected void draw(DrawContext c, int mx, int my, float delta) {
    }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        c.fillGradient(0, 0, width, height, 0xC0050B1A, 0xE0020610);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c, mx, my, delta);
        Ui.panel(c, left, top, pw, ph);
        Ui.title(c, textRenderer, heading, left + 12, top + 8);
        if (hint != null) Ui.smallRight(c, textRenderer, hint, left + pw - 12, top + 9, Ui.MUTED, 0.75f);
        drawBelow(c, mx, my, delta);
        for (ClickableWidget w : widgets) w.render(c, mx, my, delta);
        draw(c, mx, my, delta);
    }
}

package de.invscanner;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Ein Feld im Item-Raster. */
public class ItemCell extends ButtonWidget {
    public Item item;
    private ItemStack stack = ItemStack.EMPTY;

    public ItemCell(int x, int y, int size, PressAction action) {
        super(x, y, size, size, Text.literal(""), action, DEFAULT_NARRATION_SUPPLIER);
    }

    public void setItem(Item item) {
        this.item = item;
        this.stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        this.visible = item != null;
        this.active = item != null;
    }

    @Override
    public void renderWidget(DrawContext c, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = width, h = height;
        boolean hot = isHovered();
        c.fill(x, y, x + w, y + h, hot ? Ui.BORDER_HI : 0xFF1E3B73);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, hot ? 0xFF2A5AB0 : 0xFF0F1E40);
        c.drawItem(stack, x + (w - 16) / 2, y + (h - 16) / 2);
    }
}

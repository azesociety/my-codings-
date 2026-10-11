package dev.lungetest;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A movable, non-pausing panel; header dragging translates the actual hitboxes. */
public abstract class AzeiumScreen extends Screen {
    private int offsetX, offsetY;
    private boolean dragging;
    protected AzeiumScreen(Component title) { super(title); }
    protected abstract int windowWidth();
    protected abstract int windowHeight();
    protected int windowLeft() { return Math.clamp((width-windowWidth())/2+offsetX, 0, Math.max(0,width-windowWidth())); }
    protected int windowTop() { return Math.clamp((height-windowHeight())/2+offsetY, 0, Math.max(0,height-windowHeight())); }
    public boolean allowsMovement() {
        return !(getFocused() instanceof EditBox edit && edit.isFocused()) && !capturingBind();
    }
    protected abstract boolean capturingBind();
    @Override public void removed() { MenuMovement.restoreMovementKeys(minecraft); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        for (var child : children()) if (child instanceof UiGlass.Hinted hinted && child instanceof AbstractWidget widget)
            hinted.hint().draw(graphics, mouseX, mouseY, widget.visible && widget.isMouseOver(mouseX, mouseY));
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.x() >= windowLeft() && event.x() < windowLeft()+windowWidth()
                && event.y() >= windowTop() && event.y() < windowTop()+32) {
            boolean control = children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .anyMatch(widget -> widget.isMouseOver(event.x(), event.y()));
            if (!control) { dragging = true; setFocused(null); return true; }
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!handled) setFocused(null);
        return handled;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging || event.button() != 0) return super.mouseDragged(event, dx, dy);
        int oldX=windowLeft(), oldY=windowTop();
        int newX=Math.clamp(oldX+(int)Math.round(dx),0,Math.max(0,width-windowWidth()));
        int newY=Math.clamp(oldY+(int)Math.round(dy),0,Math.max(0,height-windowHeight()));
        offsetX=newX-(width-windowWidth())/2; offsetY=newY-(height-windowHeight())/2;
        for (var child:children()) if (child instanceof AbstractWidget widget) {
            widget.setX(widget.getX()+newX-oldX); widget.setY(widget.getY()+newY-oldY);
        }
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == 0) { dragging=false; return true; }
        return super.mouseReleased(event);
    }
}

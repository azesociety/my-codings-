package dev.lungetest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Quiet rounded input styling with the same typeface as the menu. */
public final class UiTextField extends EditBox {
    public UiTextField(Font font,int x,int y,int width,int height,Component label) {
        super(font,x,y,width,height,label);
        setBordered(false);
        addFormatter((value,cursor) -> UiTypography.text(value).getVisualOrderText());
    }
    @Override public void renderWidget(GuiGraphics graphics,int x,int y,float delta) {
        UiGlass.gradient(graphics,getX()-3,getY()-2,getWidth()+6,getHeight()+4,6,
                ThemeManager.alpha(ThemeManager.accent(),isFocused()?115:30),0x254B5B70);
        UiGlass.rounded(graphics,getX()-2,getY()-1,getWidth()+4,getHeight()+2,5,
                ThemeManager.alpha(UiGlass.surfaceBottom(),220));
        setTextColor(ThemeManager.text());
        super.renderWidget(graphics,x,y,delta);
    }
}

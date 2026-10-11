package dev.lungetest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Smooth Open Sans typography confined to Azeium screens. */
public final class UiTypography {
    private static final FontDescription FONT=new FontDescription.Resource(Identifier.fromNamespaceAndPath("lunge_test_client","ui"));
    public static Component text(String value) { return Component.literal(value).withStyle(style -> style.withFont(FONT)); }
    public static Component text(Component value) { return value.copy().withStyle(style -> style.withFont(FONT)); }
    public static int width(Font font,String text) { return font.width(text(text)); }
    public static int width(Font font,Component text) { return font.width(text(text)); }
    public static void draw(GuiGraphics g,Font font,String value,int x,int y,int color,boolean shadow) { g.drawString(font,text(value),x,y,color,false); }
    public static void center(GuiGraphics g,Font font,String value,int x,int y,int color) { g.drawString(font,text(value),x-font.width(text(value))/2,y,color,false); }
    public static void center(GuiGraphics g,Font font,Component value,int x,int y,int color) { g.drawString(font,text(value),x-font.width(text(value))/2,y,color,false); }
    public static String fit(Font font,String value,int width) {
        int end=value.length();
        while(end>0 && width(font,value.substring(0,end))>width) end=value.offsetByCodePoints(end,-1);
        return value.substring(0,end);
    }
}

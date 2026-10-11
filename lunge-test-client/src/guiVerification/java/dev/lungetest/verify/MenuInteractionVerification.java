package dev.lungetest.verify;

import dev.lungetest.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.phys.Vec2;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

/** Drags real widget hitboxes, checks movement bindings/gates, and isolates physical keyboard input. */
public final class MenuInteractionVerification {
    private static int checks;
    public static void verify(Minecraft client) {
        var oldScreen=client.screen; var oldPlayer=client.player; var oldLevel=client.level;
        boolean active=client.isWindowActive();
        try {
            var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(Unsafe)f.get(null);
            var player=(CombatAssistVerification.CombatPlayer)unsafe.allocateInstance(CombatAssistVerification.CombatPlayer.class);
            player.alive=true;
            client.player=player;
            client.level=(CombatAssistVerification.CombatLevel)unsafe.allocateInstance(CombatAssistVerification.CombatLevel.class);
            client.setWindowActive(true);
            require(client.font.width(UiTypography.text("WWWiii")) != client.font.width("WWWiii"), "Bundled smooth font loaded with distinct glyph metrics");
            for (AzeiumScreen screen : new AzeiumScreen[]{new LungeTestScreen(),new ClientSettingsScreen(null,ClientSettingsScreen.Page.CLIENT)}) {
                client.setScreen(screen);screen.init(900,600);
                var widgets=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
                var anchor=widgets.getFirst();int x=anchor.getX(),y=anchor.getY();
                // Left side of the title above the first row is an empty drag handle on both screens.
                double dragX=screen instanceof LungeTestScreen ? (900-540)/2+20 : (900-536)/2+24;
                double dragY=screen instanceof LungeTestScreen ? (600-310)/2+10 : (600-280)/2+10;
                var event=new MouseButtonEvent(dragX,dragY,new MouseButtonInfo(0,0));
                require(screen.mouseClicked(event,false),"Header accepts drag");
                require(screen.mouseDragged(new MouseButtonEvent(dragX+45,dragY+30,event.buttonInfo()),45,30),"Dragging is handled");
                require(anchor.getX()==x+45 && anchor.getY()==y+30,"Drag translates actual widget hitboxes");
                screen.mouseDragged(new MouseButtonEvent(10000,10000,event.buttonInfo()),10000,10000);
                for (var widget:widgets) require(widget.getX()>=0 && widget.getY()>=0 && widget.getX()+widget.getWidth()<=900 && widget.getY()+widget.getHeight()<=600,"Drag keeps controls in bounds");
                screen.mouseReleased(event);
                require(MenuMovement.read(client,key -> key==GLFW.GLFW_KEY_W).keyPresses().forward(),"Configured W movement works with menu open");
                var diagonal=MenuMovement.read(client,key -> key==GLFW.GLFW_KEY_W || key==GLFW.GLFW_KEY_D);
                require(Math.abs(diagonal.moveVector().lengthSquared()-1)<.0001,"Diagonal movement is normalized");
                require(MenuMovement.read(client,key -> key==GLFW.GLFW_KEY_W || key==GLFW.GLFW_KEY_S).moveVector().equals(Vec2.ZERO),"Opposing directions cancel");
                var action=MenuMovement.read(client,key -> key==GLFW.GLFW_KEY_SPACE || key==GLFW.GLFW_KEY_LEFT_SHIFT || key==GLFW.GLFW_KEY_LEFT_CONTROL);
                require(action.keyPresses().jump() && action.keyPresses().shift() && action.keyPresses().sprint(),"Jump crouch sprint bindings are included");
                var originalKey=com.mojang.blaze3d.platform.InputConstants.getKey(client.options.keyUp.saveString());
                client.options.keyUp.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_I));
                try {
                    require(MenuMovement.read(client,key -> key==GLFW.GLFW_KEY_I).keyPresses().forward(),"Remapped movement binding is respected");
                    require(!MenuMovement.read(client,key -> key==GLFW.GLFW_KEY_W).keyPresses().forward(),"Old movement key is not hardcoded");
                } finally { client.options.keyUp.setKey(originalKey); }
                client.setWindowActive(false);
                require(MenuMovement.read(client,key -> true).moveVector().equals(Vec2.ZERO),"Focus loss stops movement");client.setWindowActive(true);
                if(screen instanceof LungeTestScreen main) main.selectModule(AzeiumModules.Module.PLAYER_HIGHLIGHTS);
                else ((ClientSettingsScreen)screen).show(ClientSettingsScreen.Page.THEMES);
                var edit=screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                screen.setFocused(edit);edit.setFocused(true);
                require(MenuMovement.read(client,key -> true).moveVector().equals(Vec2.ZERO),"Typing suppresses movement");
                var keyboard=new net.minecraft.client.player.KeyboardInput(client.options);
                client.options.keyUp.setDown(true);
                keyboard.tick();
                require(keyboard.getMoveVector().equals(Vec2.ZERO),"Actual KeyboardInput mixin suppresses held movement while typing");
                client.options.keyUp.setDown(false);
                screen.setFocused(null);edit.setFocused(false);
                require(MenuMovement.read(client,key -> key==GLFW.GLFW_KEY_W).keyPresses().forward(),"Movement resumes after typing");
                screen.init(320,240);
                for(var child:screen.children())if(child instanceof AbstractWidget widget) require(widget.getX()>=0 && widget.getY()>=0 && widget.getX()+widget.getWidth()<=320 && widget.getY()+widget.getHeight()<=240,"Resize reclamps dragged panel and widgets");
            }
            var captureScreen=new ClientSettingsScreen(null,ClientSettingsScreen.Page.CLIENT);
            client.setScreen(captureScreen);
            captureScreen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast).filter(button -> button.getMessage().getString().startsWith("GUI bind:"))
                    .findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER,0,0));
            require(MenuMovement.read(client,key -> true).moveVector().equals(Vec2.ZERO),"Binding capture suppresses movement");
            captureScreen.handleCapturedKey(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));
            client.setScreen(new TitleScreen());
            require(MenuMovement.read(client,key -> true)==null,"Other screens retain vanilla input");
            player.alive=false;client.setScreen(new LungeTestScreen());
            require(MenuMovement.read(client,key -> true).moveVector().equals(Vec2.ZERO),"Dead player cannot move through menu");
            LungeTestClient.LOG.info("MENU_INTERACTION_VERIFIED: {} checks; actual dragging/hitboxes/clamping/resize, movement bindings, diagonal normalization, typing/focus/death/other-screen guards",checks);
        } catch(ReflectiveOperationException exception){throw new AssertionError(exception);}
        finally { client.player=oldPlayer;client.level=oldLevel;client.setWindowActive(active);client.setScreen(oldScreen); }
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}

package mrtjp.projectred.fabrication.engine.zones;
import mrtjp.projectred.fabrication.engine.EngineContext;

import codechicken.lib.colour.EnumColour;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Scale;
import codechicken.lib.vec.Transformation;
import codechicken.lib.vec.Translation;
import codechicken.lib.vec.uv.IconTransformation;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class HotKeyInteractionZone implements InteractionZone {

    private final Cuboid6 bounds;
    private final int keyCode;
    private final Runnable keyAction;
    private final int backgroundColor;
    private final ZoneText text;
    private final Supplier<Object> icon; //Supplier<TextureAtlasSprite> //TODO Find better way to make this server-safe

    private final Transformation blockTransform;

    private HotKeyInteractionZone(Builder builder) {
        this.bounds = builder.bounds;
        this.keyCode = builder.keyCode;
        this.keyAction = builder.keyAction;
        this.backgroundColor = builder.backgroundColor;
        this.text = builder.text;
        this.icon = builder.icon;

        // Transform that takes a full cuboid block and moves it to bounds
        this.blockTransform = new Scale(bounds.max.copy().subtract(bounds.min)).with(new Translation(bounds.min));
    }

    public Cuboid6 getBounds() {
        return bounds;
    }

    public void onLeftClick() {
        keyAction.run();
    }

    public void onRightClick() {
    }

    public void buildToolTip(List<ZoneText> tooltip) {
        String keyName = Character.toString((char) keyCode);
        tooltip.add(ZoneText.literal("[" + keyName + "] ").append(text).withStyle(EngineContext.UNIFORM_GRAY));
    }

    public boolean canRespondToKey(int glfwKeyCode, int glfwFlags) {
        return glfwKeyCode == keyCode;
    }

    public boolean onKeyPressed(int glfwKeyCode, int glfwFlags) {
        if (glfwKeyCode == keyCode) {
            keyAction.run();
            return true;
        }
        return false;
    }

    public static class Builder {

        private Cuboid6 bounds = Cuboid6.full;
        private int keyCode = -1;
        private Runnable keyAction = () -> {};
        private int backgroundColor = EnumColour.GRAY.rgba(64);
        private ZoneText text = ZoneText.nullToEmpty("//TODO label");
        private Supplier<Object> icon = () -> null;

        public Builder bounds(Cuboid6 bounds) {
            this.bounds = bounds;
            return this;
        }

        public Builder keyCode(int keyCode) {
            this.keyCode = keyCode;
            return this;
        }

        public Builder keyAction(Runnable keyAction) {
            this.keyAction = keyAction;
            return this;
        }

        public Builder backgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public Builder text(ZoneText text) {
            this.text = text;
            return this;
        }

        // Supplier<TextureAtlastSprite>
        public Builder icon(Supplier<Object> icon) {
            this.icon = icon;
            return this;
        }

        public InteractionZone build() {
            return new HotKeyInteractionZone(this);
        }
    }

 public int highlightColor(){return backgroundColor;}
 public Object icon(){return icon.get();}

}

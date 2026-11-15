package mrtjp.projectred.fabrication.engine.zones;
import mrtjp.projectred.fabrication.engine.EngineContext;

import codechicken.lib.colour.EnumColour;
import codechicken.lib.vec.Cuboid6;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SimpleInteractionZone implements InteractionZone {

    private final Supplier<Cuboid6> boundsSupplier;
    private final Runnable leftClickAction;
    private final Runnable rightClickAction;
    private final Consumer<List<ZoneText>> tooltipBuilder;
    private final int highlightColor;
    private final int boundingBoxColor;
    private final double boundingBoxLineWidth;
    private final Supplier<ZoneText> textSupplier;

    private SimpleInteractionZone(Builder builder) {
        this.boundsSupplier = builder.boundsSupplier;
        this.leftClickAction = builder.leftClickAction;
        this.rightClickAction = builder.rightClickAction;
        this.tooltipBuilder = builder.tooltipBuilder;
        this.highlightColor = builder.highlightColor;
        this.boundingBoxColor = builder.boundingBoxColor;
        this.boundingBoxLineWidth = builder.boundingBoxLineWidth;
        this.textSupplier = builder.textSupplier;
    }

    public Cuboid6 getBounds() {
        return boundsSupplier.get();
    }

    public void onLeftClick() {
        leftClickAction.run();
    }

    public void onRightClick() {
        rightClickAction.run();
    }

    public void buildToolTip(List<ZoneText> tooltip) {
        tooltipBuilder.accept(tooltip);
    }

    public boolean canRespondToKey(int glfwKeyCode, int glfwFlags) {
        return false;
    }

    public boolean onKeyPressed(int glfwKeyCode, int glfwFlags) {
        return false;
    }

    public static class Builder {

        private Supplier<Cuboid6> boundsSupplier = () -> Cuboid6.full;
        private Runnable leftClickAction = () -> {};
        private Runnable rightClickAction = () -> {};
        private Consumer<List<ZoneText>> tooltipBuilder = (list) -> {};
        private int highlightColor = EnumColour.LIGHT_BLUE.rgba();
        private int boundingBoxColor = EnumColour.WHITE.rgba();
        private double boundingBoxLineWidth = 4.0;
        private Supplier<ZoneText> textSupplier = () -> null;

        public Builder bounds(Supplier<Cuboid6> boundsSupplier) {
            this.boundsSupplier = boundsSupplier;
            return this;
        }

        public Builder bounds(Cuboid6 bounds) {
            this.boundsSupplier = () -> bounds;
            return this;
        }

        public Builder leftClickAction(Runnable leftClickAction) {
            this.leftClickAction = leftClickAction;
            return this;
        }

        public Builder rightClickAction(Runnable rightClickAction) {
            this.rightClickAction = rightClickAction;
            return this;
        }

        public Builder tooltip(Consumer<List<ZoneText>> tooltipBuilder) {
            this.tooltipBuilder = tooltipBuilder;
            return this;
        }

        public Builder tooltip(ZoneText tooltip) {
            this.tooltipBuilder = (list) -> list.add(tooltip);
            return this;
        }

        public Builder tooltip(Supplier<ZoneText> tooltipSupplier) {
            this.tooltipBuilder = (list) -> list.add(tooltipSupplier.get());
            return this;
        }

        public Builder highlightColor(int rgba) {
            this.highlightColor = rgba;
            return this;
        }

        public Builder boundingBoxColor(int rgba) {
            this.boundingBoxColor = rgba;
            return this;
        }

        public Builder boundingBoxLineWidth(double width) {
            this.boundingBoxLineWidth = width;
            return this;
        }

        public Builder text(Supplier<ZoneText> textSupplier) {
            this.textSupplier = textSupplier;
            return this;
        }

        public Builder text(ZoneText text) {
            this.textSupplier = () -> text;
            return this;
        }

        public SimpleInteractionZone build() {
            return new SimpleInteractionZone(this);
        }
    }

 public int highlightColor(){return highlightColor;}
 public int outlineColor(){return boundingBoxColor;}
 public double lineWidth(){return boundingBoxLineWidth;}
 public ZoneText label(){return textSupplier.get();}

}

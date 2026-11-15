package mrtjp.projectred.fabrication.engine.zones;
import net.minecraft.util.text.*;
public final class ZoneText {
    private final ITextComponent value;
    private ZoneText(ITextComponent value){this.value=value;}
    public static ZoneText literal(String text){return new ZoneText(new TextComponentString(text));}
    public static ZoneText nullToEmpty(String text){return literal(text==null?"":text);}
    public static ZoneText translatable(String key,Object... args){
        Object[] converted=args.clone();
        for(int i=0;i<converted.length;i++)if(converted[i] instanceof ZoneText text)converted[i]=text.value;
        return new ZoneText(new TextComponentTranslation(key,converted));
    }
    public ZoneText append(ZoneText other){value.appendSibling(other.value);return this;}
    public ZoneText withStyle(Style style){value.setStyle(style.createShallowCopy());return this;}
    public ZoneText withStyle(TextFormatting format){value.getStyle().setColor(format);return this;}
    public String getString(){return value.getFormattedText();}
}

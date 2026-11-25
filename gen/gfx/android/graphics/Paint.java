package android.graphics;
public class Paint { public static final int ANTI_ALIAS_FLAG=1; public enum Style{FILL,STROKE,FILL_AND_STROKE} public enum Align{LEFT,CENTER,RIGHT}
 public Style style=Style.FILL; public int color; public float sw=1, ts=12; public Align al=Align.LEFT;
 public Paint(int f){} public Paint(){} public void setStyle(Style s){style=s;} public void setColor(int c){color=c;} public void setStrokeWidth(float w){sw=w;} public void setTextSize(float s){ts=s;} public void setTextAlign(Align a){al=a;} public float measureText(String s){return s.length()*ts*0.55f;} public float getTextSize(){return ts;} public void setAlpha(int a){color=(color&0xFFFFFF)|(a<<24);} public void setTypeface(Object o){} }

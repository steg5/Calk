package android.graphics;
public class Paint {
  public static final int ANTI_ALIAS_FLAG = 1;
  public enum Style { FILL, STROKE, FILL_AND_STROKE }
  public enum Align { LEFT, CENTER, RIGHT }
  public enum Join { MITER, ROUND, BEVEL }
  public enum Cap { BUTT, ROUND, SQUARE }
  public static class FontMetrics { public float ascent, descent; }
  public Paint(int f) {}
  public void setColor(int c) {} public void setShader(Shader s) {} public void setAlpha(int a) {} public void setStyle(Style s) {} public void setStrokeWidth(float w) {} public void setTextSize(float s) {}
  public void setTextAlign(Align a) {} public void setTypeface(Typeface t) {} public void setStrokeJoin(Join j) {} public void setStrokeCap(Cap c) {}
  public float measureText(String s) { return 0; } public float getTextSize() { return 0; } public FontMetrics getFontMetrics() { return null; }
  public Object setXfermode(Xfermode x) { return null; }
}

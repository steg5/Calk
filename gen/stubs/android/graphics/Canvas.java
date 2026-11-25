package android.graphics;
public class Canvas {
  public Canvas() {} public Canvas(Bitmap b) {} public void drawBitmap(Bitmap b, float x, float y, Paint p) {}
  public void drawColor(int c) {} public void translate(float x, float y) {} public void scale(float x, float y) {} public void scale(float x, float y, float px, float py) {} public void drawRoundRect(RectF r, float a, float b, Paint p) {} public void drawPath(Path p, Paint q) {}
  public boolean clipPath(Path p) { return true; } public void drawRect(float l, float t, float r, float b, Paint p) {}
  public int save() { return 0; } public int saveLayer(float a,float b,float c,float d,Paint p) { return 0; } public void restore() {} public void rotate(float d, float x, float y) {}
  public void drawText(String s, float x, float y, Paint p) {} public void drawCircle(float x, float y, float r, Paint p) {}
  public void drawArc(RectF r, float s, float w, boolean c, Paint p) {} public void drawOval(RectF r, Paint p) {}
  public boolean clipRect(float l, float t, float r, float b) { return true; } public void drawBitmap(Bitmap b, Rect s, RectF d, Paint p) {}
  public void drawLine(float a, float b, float c, float d, Paint p) {}
    public int saveLayerAlpha(float l, float t, float r, float b, int a, int flags) { return 0; }
}

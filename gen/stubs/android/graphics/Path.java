package android.graphics;
public class Path { public enum Direction { CW, CCW }
  public void reset() {} public void addRoundRect(RectF r, float rx, float ry, Direction d) {} public void addOval(RectF r, Direction d) {} public void addRoundRect(RectF r, float[] radii, Direction d) {} public void addCircle(float x, float y, float r, Direction d) {}
  public void quadTo(float a, float b, float c, float d) {} public void moveTo(float x, float y) {} public void lineTo(float x, float y) {} public void close() {} public void cubicTo(float a, float b, float c, float d, float e, float f) {} }

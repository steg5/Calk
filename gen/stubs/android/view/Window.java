package android.view;
public abstract class Window {
  public void setBackgroundDrawable(android.graphics.drawable.Drawable d) {}
  public void setLayout(int w, int h) {}
  public void setSoftInputMode(int m) {}
  public void addFlags(int f) {}
  public View getDecorView() { return null; }
}

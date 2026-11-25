package android.view;
public class View {
  public static final int GONE = 8, VISIBLE = 0;
  public static final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY=1, SYSTEM_UI_FLAG_HIDE_NAVIGATION=2, SYSTEM_UI_FLAG_FULLSCREEN=4, SYSTEM_UI_FLAG_LAYOUT_STABLE=8, SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION=16, SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN=32;
  public interface OnClickListener { void onClick(View v); }
  public interface OnLongClickListener { boolean onLongClick(View v); }
  public void setOnLongClickListener(OnLongClickListener l) {}
  public View(android.content.Context c) {}
  public void setBackground(android.graphics.drawable.Drawable d) {}
  public void setBackgroundColor(int c) {}
  public void setOnClickListener(OnClickListener l) {}
  public void invalidate() {}
  public void startAnimation(android.view.animation.Animation a) {}
  public void setFocusableInTouchMode(boolean b) {}
  public boolean requestFocus() { return true; }
  public void setLayoutParams(ViewGroup.LayoutParams p) {}
  public ViewGroup.LayoutParams getLayoutParams() { return null; }
  public void bringToFront() {}
  public void postInvalidateDelayed(long d) {}
  public static final int FOCUS_DOWN = 130;
  public boolean post(Runnable r) { return true; }
  public int getWidth() { return 0; }
  public int getHeight() { return 0; }
  public void setAlpha(float a) {}
  public void setEnabled(boolean e) {}
  public void setVisibility(int v) {}
  public void setPadding(int l, int t, int r, int b) {}
  public void setStateListAnimator(android.animation.StateListAnimator a) {}
  public void setSystemUiVisibility(int v) {}
  protected void onDraw(android.graphics.Canvas c) {}
  public boolean onTouchEvent(MotionEvent e) { return false; }
}

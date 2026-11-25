package android.app;
public class Dialog implements android.content.DialogInterface {
  public Dialog(android.content.Context c, int t) {}
  public void setContentView(android.view.View v) {}
  public void setCancelable(boolean b) {}
  public android.view.Window getWindow() { return null; }
  public void show() {}
  public void dismiss() {}
  public boolean isShowing() { return false; }
  public void setOnDismissListener(android.content.DialogInterface.OnDismissListener l) {}
}

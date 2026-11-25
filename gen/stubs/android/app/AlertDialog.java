package android.app;
public class AlertDialog extends Dialog {
  public AlertDialog(android.content.Context c) { super(c, 0); }
  public static class Builder {
    public Builder(android.content.Context c) {}
    public Builder setTitle(CharSequence t) { return this; }
    public Builder setPositiveButton(CharSequence t, android.content.DialogInterface.OnClickListener l) { return this; }
    public Builder setNegativeButton(CharSequence t, android.content.DialogInterface.OnClickListener l) { return this; }
    public AlertDialog show() { return null; }
  }
}

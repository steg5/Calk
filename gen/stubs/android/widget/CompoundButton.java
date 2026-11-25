package android.widget;
public abstract class CompoundButton extends Button {
  public CompoundButton(android.content.Context c) { super(c); }
  public void setChecked(boolean b) {}
  public void setOnCheckedChangeListener(OnCheckedChangeListener l) {}
  public interface OnCheckedChangeListener { void onCheckedChanged(CompoundButton b, boolean on); }
}

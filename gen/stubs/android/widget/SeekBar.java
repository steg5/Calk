package android.widget;
public class SeekBar extends android.view.View {
  public SeekBar(android.content.Context c) { super(c); }
  public void setMax(int m) {}
  public void setProgress(int p) {}
  public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {}
  public interface OnSeekBarChangeListener { void onProgressChanged(SeekBar s, int p, boolean u); void onStartTrackingTouch(SeekBar s); void onStopTrackingTouch(SeekBar s); }
}

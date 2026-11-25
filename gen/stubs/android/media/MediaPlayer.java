package android.media;
public class MediaPlayer {
  public interface OnCompletionListener { void onCompletion(MediaPlayer mp); }
  public static MediaPlayer create(android.content.Context c, int id) { return new MediaPlayer(); }
  public void setDataSource(String p) throws java.io.IOException {} public void prepare() throws java.io.IOException {}
  public void setOnCompletionListener(OnCompletionListener l) {} public void start() {} public void release() {}
  public void pause() {} public boolean isPlaying() { return false; } public void setLooping(boolean b) {} public void setVolume(float l, float r) {}
  public int getDuration() { return 0; } }

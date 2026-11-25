package android.media;
public class MediaRecorder {
  public static class AudioSource { public static final int MIC = 1; }
  public static class OutputFormat { public static final int AMR_NB = 3; }
  public static class AudioEncoder { public static final int AMR_NB = 1; }
  public void setAudioSource(int s) {} public void setOutputFormat(int f) {} public void setAudioEncoder(int e) {}
  public void setOutputFile(String p) {} public void setMaxDuration(int ms) {}
  public void prepare() throws java.io.IOException {} public void start() {} public void stop() {} public void release() {} }

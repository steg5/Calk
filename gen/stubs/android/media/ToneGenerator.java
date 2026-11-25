package android.media;
public class ToneGenerator { public static final int TONE_PROP_BEEP = 24, TONE_PROP_ACK = 25, TONE_PROP_NACK = 26, TONE_PROP_PROMPT = 27, TONE_PROP_BEEP2 = 28; public ToneGenerator(int s, int v) {} public boolean startTone(int t, int ms) { return true; } public void release() {} }

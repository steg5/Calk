package android.graphics;
public class Bitmap {
  public enum CompressFormat { JPEG, PNG }
  public enum Config { ARGB_8888 }
  public static Bitmap createBitmap(int w, int h, Config c) { return null; }
  public int getWidth() { return 0; } public int getHeight() { return 0; }
  public static Bitmap createBitmap(Bitmap s, int x, int y, int w, int h) { return null; }
  public static Bitmap createScaledBitmap(Bitmap s, int w, int h, boolean f) { return null; }
  public boolean compress(CompressFormat f, int q, java.io.OutputStream o) { return true; }
}

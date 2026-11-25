package android.app;
public class Activity extends android.content.Context {
  public static final int RESULT_OK = -1;
  protected void onCreate(android.os.Bundle b) {}
  protected void onDestroy() {}
  protected void onPause() {}
  protected void onResume() {}
  public void finish() {}
  public boolean isTaskRoot() { return true; }
  public void finishAffinity() {}
  public void onBackPressed() {}
  public void onWindowFocusChanged(boolean f) {}
  protected void onActivityResult(int a, int b, android.content.Intent i) {}
  public void setContentView(android.view.View v) {}
  public android.view.Window getWindow() { return null; }
  public void startActivity(android.content.Intent i) {}
  public boolean isFinishing() { return false; }
  public void startActivityForResult(android.content.Intent i, int c) {}
  public android.content.SharedPreferences getSharedPreferences(String n, int m) { return null; }
  public android.content.res.Resources getResources() { return null; }
  public Object getSystemService(String n) { return null; }
  public android.content.Context getApplicationContext() { return null; }
  public android.content.ContentResolver getContentResolver() { return null; }
  public java.io.File getCacheDir() { return null; }
  public android.content.pm.PackageManager getPackageManager() { return null; }
  public int checkSelfPermission(String p) { return 0; }
  public void requestPermissions(String[] p, int c) {}
  public void onRequestPermissionsResult(int r, String[] p, int[] g) {}
}

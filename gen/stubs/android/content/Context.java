package android.content;
public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public static final String WIFI_SERVICE = "wifi", VIBRATOR_SERVICE = "vibrator";
    public abstract java.io.File getCacheDir();
    public abstract android.content.pm.PackageManager getPackageManager();
    public abstract android.content.SharedPreferences getSharedPreferences(String n, int m);
    public abstract android.content.res.Resources getResources();
    public abstract Object getSystemService(String n);
    public abstract Context getApplicationContext();
    public String getPackageName() { return ""; }
    public abstract android.content.ContentResolver getContentResolver();
}

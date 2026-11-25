import base64, sys, os

VERSION_CODE = int(sys.argv[1]) if len(sys.argv) > 1 else 1
VERSION_NAME = sys.argv[2] if len(sys.argv) > 2 else "1.0"
FNAME = "Gryntsiv_Games_v%d" % VERSION_CODE
PKG = "com.example.gryntsivgames"
PKGDIR = "app/src/main/java/com/example/gryntsivgames"
IND = " " * 10

ks = open("ks1.b64").read().strip()
icon = base64.b64encode(open("ic_small.png", "rb").read()).decode()

out = []
def L(s=""):
    out.append(IND + s if s else "")

def heredoc(path, text):
    L("cat > %s <<'EOF'" % path)
    for line in text.rstrip("\n").split("\n"):
        L(line)
    L("EOF")
    L()

head = """name: Gryntsiv_Games

on:
  workflow_dispatch:
  push:
    paths:
      - '.github/workflows/Gryntsiv_Games.yml'
      - '.github/workflows/Gryntsiv_Uno.yml'
      - '.github/workflows/Gryntsiv_Sea.yml'
      - '.github/workflows/Gryntsiv_Durak.yml'

jobs:
  uno:
    uses: ./.github/workflows/Gryntsiv_Uno.yml
  sea:
    uses: ./.github/workflows/Gryntsiv_Sea.yml
  durak:
    uses: ./.github/workflows/Gryntsiv_Durak.yml
  build:
    needs: [uno, sea, durak]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.5'

      - name: Write project
        run: |
"""

L("mkdir -p %s %s/uno app/src/main/res/drawable app/src/main/res/raw" % (PKGDIR, PKGDIR))
L("echo '%s' | base64 -d > debug.keystore" % ks)
L()

heredoc("settings.gradle", """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = 'GryntsivGames'
include ':app'
""")

heredoc("build.gradle", """plugins {
    id 'com.android.application' version '8.2.2' apply false
}
""")

heredoc("gradle.properties", """org.gradle.jvmargs=-Xmx2g
android.useAndroidX=false
""")

heredoc("app/build.gradle", """plugins {
    id 'com.android.application'
}
android {
    namespace '%s'
    compileSdk 34
    defaultConfig {
        applicationId "%s"
        minSdk 23
        targetSdk 26
        versionCode %d
        versionName "%s"
    }
    signingConfigs {
        debug {
            storeFile file("${rootDir}/debug.keystore")
            storePassword 'android'
            keyAlias 'androiddebugkey'
            keyPassword 'android'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    lint {
        abortOnError false
        checkReleaseBuilds false
    }
}
tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
""" % (PKG, PKG, VERSION_CODE, VERSION_NAME))

heredoc("app/src/main/AndroidManifest.xml", """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE"/>
    <uses-permission android:name="android.permission.VIBRATE"/>
    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-feature android:name="android.hardware.microphone" android:required="false"/>
    <application
        android:label="Gryntsiv Games"
        android:icon="@drawable/ic_launcher"
        android:allowBackup="false"
        android:usesCleartextTraffic="true"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout|uiMode"
            android:windowSoftInputMode="stateHidden|adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        <activity
            android:name=".uno.MainActivity"
            android:exported="false"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout|uiMode"
            android:windowSoftInputMode="stateHidden|adjustResize"/>
        <activity
            android:name=".durak.DurakActivity"
            android:exported="false"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout|uiMode"
            android:windowSoftInputMode="stateHidden|adjustResize"/>
        <activity
            android:name=".sea.SeaActivity"
            android:exported="false"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout|uiMode"
            android:windowSoftInputMode="stateHidden|adjustResize"/>
    </application>
</manifest>
""")

hub_uno = ["Cfg", "Card", "Theme", "CardPainter", "Bg", "Avatars", "Snd", "IconView", "BaseActivity"]
uno_only = ["Engine", "GState", "Link", "Relay", "Discovery", "Client", "Host", "VoiceHelper", "TableView", "MainActivity"]
sea_order = ["Scheme", "Link", "Client", "Relay", "Discovery", "Net", "Match", "Board", "Bot", "Painter", "PlaceView", "BattleView", "SeaActivity"]

import io, tarfile, gzip

def pack(items):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w") as tf:
        for rel, src in items:
            data = open(src, "rb").read()
            ti = tarfile.TarInfo(rel)
            ti.size = len(data)
            ti.mtime = 0
            tf.addfile(ti, io.BytesIO(data))
    return base64.b64encode(gzip.compress(buf.getvalue(), 9, mtime=0)).decode()

def emit_pack(lines, b64, dest):
    lines.append(IND + "base64 -d <<'EOF' | tar xz -C %s" % dest)
    for i in range(0, len(b64), 100):
        lines.append(IND + b64[i:i + 100])
    lines.append(IND + "EOF")

hub_items = [("MainActivity.java", "hub/MainActivity.java")] + [("uno/%s.java" % n, "unosrc/%s.java" % n) for n in hub_uno]
b64 = pack(hub_items)
L("base64 -d <<'EOF' | tar xz -C %s" % PKGDIR)
for i in range(0, len(b64), 100):
    L(b64[i:i + 100])
L("EOF")

L("echo '%s' | base64 -d > app/src/main/res/drawable/ic_launcher.png" % icon)
L("if [ -d uno_sounds ]; then cp uno_sounds/*.mp3 app/src/main/res/raw/ || true; fi")

tail = """
      - uses: actions/download-artifact@v4
        with:
          pattern: src-*
          merge-multiple: true
          path: app/src/main

      - name: Build
        run: gradle assembleDebug --no-daemon

      - name: Rename APK
        run: cp app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/debug/Gryntsiv_Games_v%d.apk

      - uses: actions/upload-artifact@v4
        with:
          name: Gryntsiv_Games_v%d
          path: app/build/outputs/apk/debug/Gryntsiv_Games_v%d.apk
""" % (VERSION_CODE, VERSION_CODE, VERSION_CODE)
text = head + "\n".join(out) + "\n" + tail
open(FNAME + ".yml", "w", encoding="utf-8").write(text)
print(FNAME + ".yml", len(text))

def game_file(fname, wfname, art, pkgsub, items, sfx, extra_cp):
    lines = []
    lines.append("name: " + wfname)
    lines.append("")
    lines.append("on:")
    lines.append("  workflow_call:")
    lines.append("")
    lines.append("jobs:")
    lines.append("  src:")
    lines.append("    runs-on: ubuntu-latest")
    lines.append("    steps:")
    lines.append("      - uses: actions/checkout@v4")
    lines.append("")
    lines.append("      - name: Write sources")
    lines.append("        run: |")
    dest = "out/java/com/example/gryntsivgames" + pkgsub
    lines.append(IND + "mkdir -p %s out/res/raw" % dest)
    emit_pack(lines, pack(items), dest)
    for f, prefix in sfx:
        for fn in sorted(os.listdir(f)):
            lines.append(IND + "echo '%s' | base64 -d > out/res/raw/%s%s" % (base64.b64encode(open(f + "/" + fn, "rb").read()).decode(), prefix, fn))
    for c in extra_cp:
        lines.append(IND + c)
    lines.append("")
    lines.append("      - uses: actions/upload-artifact@v4")
    lines.append("        with:")
    lines.append("          name: " + art)
    lines.append("          path: out")
    txt = "\n".join(lines) + "\n"
    open(fname, "w", encoding="utf-8").write(txt)
    print(fname, len(txt))

game_file("Gryntsiv_Uno_v%d.yml" % VERSION_CODE, "Gryntsiv_Uno", "src-uno", "/uno",
          [("%s.java" % n, "unosrc/%s.java" % n) for n in uno_only], [("../uno/sfx", "sfx_")], [])
cps = []
for nm in ("miss", "hit", "destroy"):
    for ex in ("wav", "mp3", "ogg"):
        cps.append("if [ -f Battleship/%s.%s ]; then cp Battleship/%s.%s out/res/raw/%s.%s; fi" % (nm, ex, nm, ex, nm, ex))
game_file("Gryntsiv_Sea_v%d.yml" % VERSION_CODE, "Gryntsiv_Sea", "src-sea", "/sea",
          [("%s.java" % n, "src/%s.java" % n) for n in sea_order], [("sfx", "bs_")], cps)

durak_order = ["Link", "Client", "Relay", "Discovery", "DuCards", "DState", "DEngine", "DBot", "DHost", "DuTable", "DurakActivity"]
game_file("Gryntsiv_Durak_v%d.yml" % VERSION_CODE, "Gryntsiv_Durak", "src-durak", "/durak",
          [("%s.java" % n, "dsrc/%s.java" % n) for n in durak_order], [], [])

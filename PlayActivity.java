package com.mini.engine;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PlayActivity extends Activity {
    Game g;
    GameView v;
    File testDir;
    VideoLayer vl;

    static byte[] readAll(InputStream i) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = i.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            String dir = getIntent().getStringExtra("dir");
            String json;
            Game.Src src;
            String prefName;
            if (dir != null) {
                testDir = new File(dir);
                final File ad = new File(dir, "assets");
                prefName = "test_" + testDir.getName();
                try (InputStream i = new FileInputStream(new File(dir, "game.json"))) {
                    json = new String(readAll(i), "UTF-8");
                }
                src = new Game.Src() {
                    public InputStream open(String n) throws IOException {
                        return new FileInputStream(new File(ad, n));
                    }

                    public File file(String n) throws IOException {
                        return new File(ad, n);
                    }
                };
            } else {
                prefName = "save";
                final AssetManager am = getAssets();
                final File cache = getCacheDir();
                File[] old = cache.listFiles();
                if (old != null) for (File f : old) if (f.getName().startsWith("a_")) f.delete();
                try (InputStream i = am.open("game.json")) {
                    json = new String(readAll(i), "UTF-8");
                }
                src = new Game.Src() {
                    public InputStream open(String n) throws IOException {
                        return am.open("a/" + n);
                    }

                    public File file(String n) throws IOException {
                        File f = new File(cache, "a_" + n);
                        if (!f.exists()) {
                            try (InputStream i = am.open("a/" + n); FileOutputStream o = new FileOutputStream(f)) {
                                byte[] buf = new byte[16384];
                                int k;
                                while ((k = i.read(buf)) > 0) o.write(buf, 0, k);
                            }
                        }
                        return f;
                    }
                };
            }
            g = Game.fromJson(json, src, true);
            final SharedPreferences sp = getSharedPreferences(prefName, MODE_PRIVATE);
            g.store = new Game.Store() {
                public String get(String k) {
                    return sp.getString(k, null);
                }

                public void put(String k, String val) {
                    sp.edit().putString(k, val).apply();
                }

                public void clear() {
                    sp.edit().clear().apply();
                }
            };
            g.host = new Game.Host() {
                public void setOrientation(boolean land) {
                    setRequestedOrientation(land ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                }

                public void quit() {
                    if (g != null) g.autoSave();
                    finishAndRemoveTask();
                    android.os.Process.killProcess(android.os.Process.myPid());
                }

                public void wake() {
                    if (v != null) v.postInvalidate();
                }
            };
            setRequestedOrientation(g.landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            v = new GameView(this, g, false);
            FrameLayout root = new FrameLayout(this);
            root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            vl = new VideoLayer(this);
            root.addView(vl, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            g.layer = vl;
            setContentView(root);
            String sn = getIntent().getStringExtra("scene");
            if (sn != null) {
                Game.Scene sc = g.sceneByName(sn);
                if (sc != null) g.startId = sc.id;
            }
            g.start();
        } catch (Exception e) {
            finish();
        }
    }

    // En modo prueba guarda una imagen de la última partida (sirve para elegir posiciones visualmente)
    void saveSnapshot() {
        if (testDir == null || g == null) return;
        try {
            Bitmap bm = g.snapshot(960);
            try (FileOutputStream o = new FileOutputStream(new File(testDir, "last.png"))) {
                bm.compress(Bitmap.CompressFormat.PNG, 90, o);
            }
        } catch (Throwable t) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (g != null) {
            g.autoSave();
            g.pauseAudio();
            if (vl != null) vl.pauseAll();
            saveSnapshot();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (g != null) g.resumeAudio();
        if (vl != null) vl.resumeAll();
        if (v != null) v.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vl != null) vl.releaseAll();
        if (g != null) g.release();
    }
}

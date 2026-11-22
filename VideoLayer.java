package com.mini.engine;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Build;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import java.util.HashMap;

// Capa que reproduce los nodos Video (mp4, webm VP8/VP9, mkv) encima del juego.
// No consume toques: pasan al GameView que está debajo.
public class VideoLayer extends FrameLayout implements Game.Layer {
    static class H {
        Game game;
        Game.Node n;
        TextureView tv;
        volatile int playToken;
        MediaPlayer mp;
        Surface sf;
        String res = "";
        boolean ready, wantPlay, hidden, backwards, seeking, loopApplied;
        float volumeApplied = -1f;
        float reverseMs, reverseWait;
        int nextSeek = -1; // -2 solicita el último cuadro; la duración llega al preparar.
    }

    final HashMap<Game.Node, H> map = new HashMap<Game.Node, H>();
    final RectF r = new RectF();
    boolean paused;
    int sceneSerial = -1;
    static final float REVERSE_INTERVAL = 1f / 12f;

    public VideoLayer(Context c) {
        super(c);
        setClickable(false);
        setFocusable(false);
    }

    @Override
    public void sync(Game g) {
        if (sceneSerial != g.sceneSerial) {
            releaseAll();
            sceneSerial = g.sceneSerial;
        }
        if (!g.hasVideo) return;

        for (int i = 0; i < g.nodes.size(); i++) {
            Game.Node n = g.nodes.get(i);
            if (!n.type.equals("Video")) continue;
            H h = map.get(n);
            if (h == null) {
                h = make(n, g);
                map.put(n, h);
                h.res = n.res;
                h.wantPlay = n.autoplay;
                open(h, g);
            }
            command(h, g);
            place(h, g);
            stepReverse(h, g.curDt);
        }
    }

    H make(Game.Node n, Game g) {
        final H h = new H();
        h.game = g;
        h.n = n;
        h.tv = new TextureView(getContext());
        h.tv.setOpaque(false);
        h.tv.setClickable(false);
        h.tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int hh) {
                h.sf = new Surface(st);
                try {
                    if (h.mp != null) h.mp.setSurface(h.sf);
                    tryStart(h);
                } catch (Exception e) {
                }
            }

            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int hh) {
            }

            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                pausePlayer(h);
                try {
                    if (h.mp != null) h.mp.setSurface(null);
                } catch (Exception e) {
                }
                if (h.sf != null) h.sf.release();
                h.sf = null;
                return true;
            }

            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
        addView(h.tv, new FrameLayout.LayoutParams(1, 1));
        return h;
    }

    void open(final H h, Game g) {
        closePlayer(h);
        if (h.res == null || h.res.isEmpty()) return;
        try {
            final MediaPlayer mp = new MediaPlayer();
            h.mp = mp;
            h.ready = false;
            mp.setDataSource(g.src.file(h.res).getPath());
            if (h.sf != null) mp.setSurface(h.sf);
            mp.setOnPreparedListener(p -> {
                if (p != h.mp) return;
                h.ready = true;
                applyOptions(h);
                flushSeek(h);
                tryStart(h);
            });
            mp.setOnSeekCompleteListener(p -> {
                if (p != h.mp) return;
                h.seeking = false;
                flushSeek(h);
                tryStart(h);
            });
            watchCompletion(h);
            mp.setOnErrorListener((p, what, extra) -> {
                // Formato no soportado en este equipo
                if (p == h.mp) closePlayer(h);
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            closePlayer(h);
        }
    }

    void watchCompletion(final H h) {
        final MediaPlayer player = h.mp;
        final int token = h.playToken;
        if (player == null) return;
        player.setOnCompletionListener(p -> {
            if (p == player && player == h.mp && token == h.playToken
                    && !h.n.loop && !h.backwards) {
                h.wantPlay = false;
                h.game.videoEnded(h.n, token);
            }
        });
    }

    void tryStart(H h) {
        if (!h.ready || h.seeking || !h.wantPlay || h.backwards || paused || h.hidden || h.sf == null) return;
        try {
            if (!h.mp.isPlaying()) h.mp.start();
        } catch (Exception ignored) { }
    }

    void pausePlayer(H h) {
        try {
            if (h.ready && h.mp.isPlaying()) h.mp.pause();
        } catch (Exception ignored) { }
    }

    void applyOptions(H h) {
        if (!h.ready) return;
        try {
            boolean loop = !h.backwards && h.n.loop;
            if (loop != h.loopApplied) {
                h.mp.setLooping(loop);
                h.loopApplied = loop;
            }
            if (h.n.vol != h.volumeApplied) {
                h.mp.setVolume(h.n.vol, h.n.vol);
                h.volumeApplied = h.n.vol;
            }
        } catch (Exception ignored) { }
    }

    void flushSeek(H h) {
        if (!h.ready || h.seeking || h.nextSeek == -1) return;
        try {
            int duration = h.mp.getDuration();
            int end = Math.max(0, duration - 1);
            int when = h.nextSeek == -2 ? end : Math.min(h.nextSeek, end);
            h.nextSeek = -1;
            h.reverseMs = when;
            h.seeking = true;
            if (Build.VERSION.SDK_INT >= 26) h.mp.seekTo(when, MediaPlayer.SEEK_CLOSEST);
            else h.mp.seekTo(when);
        } catch (Exception ignored) {
            h.nextSeek = -1;
            h.seeking = false;
        }
    }

    void stepReverse(H h, float dt) {
        if (!h.backwards || !h.wantPlay || !h.ready || h.hidden || paused || h.sf == null) return;
        h.reverseWait -= dt;
        // Nunca se apilan búsquedas: se deja terminar la anterior antes de pedir otro cuadro.
        if (h.seeking || h.reverseWait > 0f) return;
        if (h.reverseMs <= 0f) {
            if (!h.n.loop) {
                h.wantPlay = false;
                h.game.videoEnded(h.n, h.playToken);
                return;
            }
            h.nextSeek = -2;
        } else {
            h.nextSeek = Math.max(0, Math.round(h.reverseMs - REVERSE_INTERVAL * 1000f));
        }
        h.reverseWait = REVERSE_INTERVAL;
        flushSeek(h);
    }

    void command(H h, Game g) {
        Game.Node n = h.n;
        String cmd = n.vcmd;
        n.vcmd = "";
        if (!n.res.equals(h.res) || cmd.equals("reload")) {
            h.res = n.res;
            h.playToken = n.playToken;
            h.wantPlay = n.autoplay;
            h.backwards = false;
            open(h, g);
        }
        switch (cmd) {
            case "play": case "restart": {
                h.playToken = n.playToken;
                watchCompletion(h);
                boolean changed = h.backwards != n.backwards;
                h.backwards = n.backwards;
                h.wantPlay = true;
                if (h.backwards) {
                    pausePlayer(h);
                    if (changed || cmd.equals("restart") || h.reverseMs <= 0f) {
                        h.nextSeek = -2;
                        h.reverseWait = REVERSE_INTERVAL;
                    }
                } else if (cmd.equals("restart")) h.nextSeek = 0;
                break;
            }
            case "pause": case "stop":
                h.wantPlay = false;
                pausePlayer(h);
                if (cmd.equals("stop")) h.nextSeek = 0;
                break;
            default: break;
        }
        if (n.vseek >= 0f) h.nextSeek = (int) (n.vseek * 1000f);
        n.vseek = -1f;
        applyOptions(h);
        flushSeek(h);
        if (cmd.equals("play") || cmd.equals("restart")) tryStart(h);
    }

    void place(H h, Game g) {
        Game.Node n = h.n;
        boolean show = n.wvis && n.wa > 0.003f && !paused;
        if (show == h.hidden) {
            h.hidden = !show;
            if (show) tryStart(h);
            else pausePlayer(h);
        }
        h.tv.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        if (!show) return;
        r.set(n.wx, n.wy, n.wx + n.w, n.wy + n.h);
        if (g.useCam && !g.snap && !n.fixed) g.camM.mapRect(r);
        int w = Math.max(1, Math.round((r.right - r.left) * g.sx));
        int hh = Math.max(1, Math.round((r.bottom - r.top) * g.sy));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.tv.getLayoutParams();
        if (lp.width != w || lp.height != hh) {
            lp.width = w;
            lp.height = hh;
            h.tv.setLayoutParams(lp);
        }
        h.tv.setTranslationX(r.left * g.sx + g.ox);
        h.tv.setTranslationY(r.top * g.sy + g.oy);
        h.tv.setAlpha(Math.max(0f, Math.min(1f, n.wa)));
    }

    void closePlayer(H h) {
        if (h.mp != null) {
            try {
                h.mp.release();
            } catch (Exception e) {
            }
            h.mp = null;
        }
        h.ready = h.seeking = h.loopApplied = false;
        h.volumeApplied = -1f;
        h.nextSeek = -1;
        h.reverseMs = h.reverseWait = 0f;
    }

    void drop(H h) {
        if (h == null) return;
        closePlayer(h);
        removeView(h.tv);
    }

    public void pauseAll() {
        paused = true;
        for (H h : map.values()) {
            h.hidden = true;
            pausePlayer(h);
        }
    }

    public void resumeAll() {
        paused = false;
    }

    public void releaseAll() {
        for (H h : map.values()) drop(h);
        map.clear();
    }
}

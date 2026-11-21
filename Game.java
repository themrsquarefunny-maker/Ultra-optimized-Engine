package com.mini.engine;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.media.MediaPlayer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class Game {
    public interface Src {
        InputStream open(String name) throws IOException;
        File file(String name) throws IOException;
    }

    public interface Store {
        String get(String k);
        void put(String k, String v);
        void clear();
    }

    public interface Host {
        void setOrientation(boolean landscape);

        default void quit() {
        }
    }

    public interface Layer {
        void sync(Game g);
    }

    public static final String[] TYPES = {"Image", "Button", "ImageButton", "Label", "Color", "Sound", "AnimatedSprite", "Video", "Camera2D", "Group", "Anchor", "Grab", "Collision", "Limiter"};

    // ================= Valores de enum =================
    public static class EnumVal {
        public final String type, name;
        public final int idx;

        EnumVal(String t, String n, int i) {
            type = t;
            name = n;
            idx = i;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // ================= Bloques =================
    public static class Blk {
        public String t;
        public HashMap<String, String> f = new HashMap<String, String>();
        public ArrayList<Blk> body = new ArrayList<Blk>();
        public ArrayList<Blk> els = new ArrayList<Blk>();
        String last, ck, cta;
        ArrayList<String> ctk;
        float acc;
        boolean overlapping;
        Task task;

        String paramSpec;
        ArrayList<Parameter> parameters;

        public Blk(String t) {
            this.t = t;
        }

        public String s(String k) {
            String v = f.get(k);
            return v == null ? "" : v;
        }

        public boolean b(String k) {
            return "true".equals(f.get(k));
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("t", t);
            JSONObject fo = new JSONObject();
            for (Map.Entry<String, String> e : f.entrySet()) fo.put(e.getKey(), e.getValue());
            o.put("f", fo);
            if (!body.isEmpty()) o.put("b", arr(body));
            if (!els.isEmpty()) o.put("e", arr(els));
            return o;
        }

        static JSONArray arr(ArrayList<Blk> l) throws Exception {
            JSONArray a = new JSONArray();
            for (Blk b : l) a.put(b.toJson());
            return a;
        }

        static ArrayList<Blk> list(JSONArray a) throws Exception {
            ArrayList<Blk> l = new ArrayList<Blk>();
            if (a != null) for (int i = 0; i < a.length(); i++) l.add(fromJson(a.getJSONObject(i)));
            return l;
        }

        static Blk fromJson(JSONObject o) throws Exception {
            Blk b = new Blk(o.getString("t"));
            JSONObject fo = o.optJSONObject("f");
            if (fo != null) {
                Iterator<String> it = fo.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    b.f.put(k, fo.getString(k));
                }
            }
            b.body = list(o.optJSONArray("b"));
            b.els = list(o.optJSONArray("e"));
            return b;
        }
    }

    // ================= Nodos =================
    public static class Node {
        public int id;
        public String name = "", type = "", res = "", snd = "", text = "", follow = "";
        public int parent = -1;
        public boolean inherit = true, visible = true, loop, autoplay, fit = true, fixed;
        public boolean current, lim, dim = true, swiped;
        public int swPid = -1, tch = -1;
        public boolean touchAlways, smart, smartMaj, bold, italic, shadow, wrap = true, vdone;
        public int smartRes = 32, align = 1, valign = 1, color = 0xFFFFFFFF, ocolor = 0xFF000000;
        public float outline = 0f, vol = 1f, vseek = -1f;
        public String font = "", vcmd = "";
        TextPaint tpF, tpO;
        StaticLayout lay, layO;
        String layKey;
        int layW;
        public float x, y, w = 200, h = 200, alpha = 1f, fps = 8f, tsize = 36f;
        public float zoom = 1f, smooth = 0f, ll = 0, lt = 0, lr = 1280, lb = 720;
        public ArrayList<String> frames = new ArrayList<String>();
        public ArrayList<Blk> script = new ArrayList<Blk>();
        // tiempo de ejecución
        public float wx, wy, wa;
        public boolean wvis, playing, preview, pressed, backwards;
        ArrayList<VisualCopy> visualCopies;
        boolean literalText;
        public int pid = -1, frame;
        public float ft;
        public HashMap<String, Object> vars = new HashMap<String, Object>();
        // rotación, ancla y agarre
        public float rot, rot0, limL = -1f, limR = -1f;
        public String anchor = "", gtarget = "", gmode = "arrastrar";
        public boolean norender, rotd, collisionEnabled = true;
        Matrix tm;
        float gx, gy, ga, gacc, grot;
        Node par;
        boolean inOrder, mpWas;
        MediaPlayer mp;
        String mpName;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("type", type);
            o.put("parent", parent);
            o.put("inh", inherit);
            o.put("vis", visible);
            o.put("loop", loop);
            o.put("auto", autoplay);
            o.put("fit", fit);
            o.put("fixed", fixed);
            o.put("x", x);
            o.put("y", y);
            o.put("w", w);
            o.put("h", h);
            o.put("a", alpha);
            o.put("fps", fps);
            o.put("ts", tsize);
            o.put("res", res);
            o.put("snd", snd);
            o.put("text", text);
            o.put("zoom", zoom);
            o.put("smooth", smooth);
            o.put("follow", follow);
            o.put("cur", current);
            o.put("lim", lim);
            o.put("ll", ll);
            o.put("lt", lt);
            o.put("lr", lr);
            o.put("lb", lb);
            o.put("ta", touchAlways);
            o.put("dk", dim);
            o.put("sm", smart);
            o.put("smr", smartRes);
            o.put("smm", smartMaj);
            o.put("bold", bold);
            o.put("ital", italic);
            o.put("shad", shadow);
            o.put("wrap", wrap);
            o.put("al", align);
            o.put("vl", valign);
            o.put("col", color);
            o.put("oc", ocolor);
            o.put("ow", outline);
            o.put("vol", vol);
            o.put("font", font);
            if (!collisionEnabled) o.put("collisionEnabled", false);
            if (rot != 0f) o.put("rot", rot);
            if (!anchor.isEmpty()) o.put("anc", anchor);
            if (!gtarget.isEmpty()) o.put("gt", gtarget);
            if (!gmode.equals("arrastrar")) o.put("gm", gmode);
            if (limL >= 0f) o.put("lL", limL);
            if (limR >= 0f) o.put("lR", limR);
            JSONArray fr = new JSONArray();
            for (String s : frames) fr.put(s);
            o.put("fr", fr);
            o.put("script", Blk.arr(script));
            return o;
        }

        static Node fromJson(JSONObject o) throws Exception {
            Node n = new Node();
            n.id = o.getInt("id");
            n.name = o.optString("name", "Nodo");
            n.type = o.optString("type", "Image");
            n.parent = o.optInt("parent", -1);
            n.inherit = o.optBoolean("inh", true);
            n.visible = o.optBoolean("vis", true);
            n.loop = o.optBoolean("loop", false);
            n.autoplay = o.optBoolean("auto", false);
            n.fit = o.optBoolean("fit", true);
            n.fixed = o.optBoolean("fixed", false);
            n.x = (float) o.optDouble("x", 0);
            n.y = (float) o.optDouble("y", 0);
            n.w = (float) o.optDouble("w", 200);
            n.h = (float) o.optDouble("h", 200);
            n.alpha = (float) o.optDouble("a", 1);
            n.fps = (float) o.optDouble("fps", 8);
            n.tsize = (float) o.optDouble("ts", 36);
            n.res = o.optString("res", "");
            n.snd = o.optString("snd", "");
            n.text = o.optString("text", "");
            n.zoom = (float) o.optDouble("zoom", 1);
            n.smooth = (float) o.optDouble("smooth", 0);
            n.follow = o.optString("follow", "");
            n.current = o.optBoolean("cur", false);
            n.lim = o.optBoolean("lim", false);
            n.ll = (float) o.optDouble("ll", 0);
            n.lt = (float) o.optDouble("lt", 0);
            n.lr = (float) o.optDouble("lr", 1280);
            n.lb = (float) o.optDouble("lb", 720);
            n.touchAlways = o.optBoolean("ta", false);
            n.dim = o.optBoolean("dk", true);
            n.smart = o.optBoolean("sm", false);
            n.smartRes = o.optInt("smr", 32);
            n.smartMaj = o.optBoolean("smm", false);
            n.bold = o.optBoolean("bold", false);
            n.italic = o.optBoolean("ital", false);
            n.shadow = o.optBoolean("shad", false);
            n.wrap = o.optBoolean("wrap", true);
            n.align = o.optInt("al", 1);
            n.valign = o.optInt("vl", 1);
            n.color = o.optInt("col", 0xFFFFFFFF);
            n.rot = (float) o.optDouble("rot", 0);
            n.anchor = o.optString("anc", "");
            n.gtarget = o.optString("gt", "");
            n.gmode = o.optString("gm", "arrastrar");
            n.limL = (float) o.optDouble("lL", -1);
            n.limR = (float) o.optDouble("lR", -1);
            n.ocolor = o.optInt("oc", 0xFF000000);
            n.outline = (float) o.optDouble("ow", 0);
            n.vol = (float) o.optDouble("vol", 1);
            n.font = o.optString("font", "");
            n.collisionEnabled = o.optBoolean("collisionEnabled", true);
            JSONArray fr = o.optJSONArray("fr");
            if (fr != null) for (int i = 0; i < fr.length(); i++) n.frames.add(fr.getString(i));
            n.script = Blk.list(o.optJSONArray("script"));
            if (n.type.equals("Save")) convertLegacySave(n, o.optJSONArray("sv"));
            return n;
        }

        // Los nodos Save de la versión 2 se convierten en un nodo invisible con bloques equivalentes
        static void convertLegacySave(Node n, JSONArray sa) {
            n.type = "Image";
            n.visible = false;
            Blk st = new Blk("start");
            StringBuilder names = new StringBuilder();
            if (sa != null) {
                for (int i = 0; i < sa.length(); i++) {
                    String[] q = sa.optString(i, "").split(":", 3);
                    if (q.length < 2 || q[0].isEmpty()) continue;
                    Blk v = new Blk("var");
                    v.f.put("name", q[0]);
                    v.f.put("vtype", q[1]);
                    v.f.put("scope", "global");
                    v.f.put("val", q.length > 2 ? q[2] : "");
                    st.body.add(v);
                    if (names.length() > 0) names.append(',');
                    names.append(q[0]);
                }
            }
            if (names.length() > 0) {
                Blk rv = new Blk("recv");
                rv.f.put("msg", "guardar_progreso");
                Blk sv = new Blk("save");
                sv.f.put("names", names.toString());
                rv.body.add(sv);
                n.script.add(0, rv);
                n.script.add(0, st);
            }
        }
    }

    public static class Scene {
        public int id;
        public String name;
        public ArrayList<Node> nodes = new ArrayList<Node>();
        String raw;
    }

    static class VisualCopy {
        final Node picture; // null = reutiliza el estado visual del original
        final float dx, dy;
        VisualCopy(Node picture, float dx, float dy) {
            this.picture = picture;
            this.dx = dx;
            this.dy = dy;
        }
    }

    // Posición relativa en el lienzo original de un PNG ya recortado fuera del motor.
    static final class AssetOrder {
        static final int MAX_PIXELS = 16777216, MAX_SIDE = 8192;
        final int sourceWidth, sourceHeight, x, y, width, height;
        final float left, top, right, bottom;

        AssetOrder(JSONObject data) throws Exception {
            sourceWidth = exactInt(data, "sourceWidth"); sourceHeight = exactInt(data, "sourceHeight");
            x = exactInt(data, "x"); y = exactInt(data, "y");
            width = exactInt(data, "width"); height = exactInt(data, "height");
            if (sourceWidth < 1 || sourceHeight < 1 || sourceWidth > MAX_SIDE || sourceHeight > MAX_SIDE
                    || (long) sourceWidth * sourceHeight > MAX_PIXELS || x < 0 || y < 0
                    || width < 1 || height < 1 || x > sourceWidth - width || y > sourceHeight - height) {
                throw new IllegalArgumentException("Coordenadas de imagen no válidas");
            }
            left = x / (float) sourceWidth; top = y / (float) sourceHeight;
            right = (x + width) / (float) sourceWidth; bottom = (y + height) / (float) sourceHeight;
        }

        private static int exactInt(JSONObject data, String key) throws Exception {
            Object value = data.get(key);
            if (value instanceof Number) {
                Number n = (Number) value;
                int i = n.intValue();
                if (n.doubleValue() == i) return i;
            }
            throw new IllegalArgumentException("Coordenada no entera: " + key);
        }

        JSONObject toJson() throws Exception {
            return new JSONObject().put("sourceWidth", sourceWidth).put("sourceHeight", sourceHeight)
                    .put("x", x).put("y", y).put("width", width).put("height", height);
        }
    }

    static class Mask {
        int w, h;
        boolean[] b;
    }

    static class Tw {
        Node n;
        int kind;
        float[] from = new float[2], to = new float[2];
        boolean[] has = new boolean[2];
        float t, d;
        String easing;

        void apply(float k) {
            if ("entrada/salida".equals(easing)) k = k * k * (3f - 2f * k);
            else if ("salida suave".equals(easing)) k = k * (2f - k);
            if (kind == 0) {
                n.alpha = from[0] + (to[0] - from[0]) * k;
            } else if (kind == 1) {
                if (has[0]) n.x = from[0] + (to[0] - from[0]) * k;
                if (has[1]) n.y = from[1] + (to[1] - from[1]) * k;
            } else if (kind == 3) {
                n.rot = from[0] + (to[0] - from[0]) * k;
            } else {
                if (has[0]) n.w = from[0] + (to[0] - from[0]) * k;
                if (has[1]) n.h = from[1] + (to[1] - from[1]) * k;
            }
        }
    }

    static final String FN_VOID = "Sin retorno (void)", FN_VALUE = "Devuelve un valor",
            FN_COMPAT = "Automático (compatibilidad)";

    static boolean callOption(Blk call, String flag, String field) {
        return call.f.containsKey(flag) ? call.b(flag) : !call.s(field).trim().isEmpty();
    }

    static class Frame {
        ArrayList<Blk> list;
        int idx, kind, rem;
        boolean chain, fn;
        Blk blk;
        Node prev;
        HashMap<String, Object> loc;
        String rv = "";
    }

    // alcance: 0 escena, 1 solo el nodo, 2 nodo e hijos, 3 global
    static class Msg {
        String m;
        Node from;
        int sc;
    }

    static class Task {
        Node self;
        ArrayList<Frame> stack = new ArrayList<Frame>();
        float wait;
        boolean done, fresh;
        HashMap<Blk, float[]> tm;
        float carry;
        boolean wk;
        int fns;
    }

    static class Hold {
        Node n;
        Blk b;
        int pid;
        Task task;
    }

    // ================= Estado =================
    public ArrayList<Scene> scenes = new ArrayList<Scene>();
    public Scene curScene;
    public ArrayList<Node> nodes = new ArrayList<Node>();
    public ArrayList<Node> order = new ArrayList<Node>();
    public boolean landscape = true, autosave = true;
    public String stretch = "keep", viewMode = "viewport";
    public int vw = 1280, vh = 720, vwL = 1280, vhL = 720, vwP = 720, vhP = 1280;
    public int bg = 0xFF202020, nextId = 1, nextSceneId = 1, startId = 1;
    public Src src;
    public Store store;
    public Host host;
    public boolean edit;
    public Node sel;
    public float ox, oy, sx = 1, sy = 1;
    float editorZoom = 1f, editorPanX, editorPanY;
    float drawLeft, drawTop, drawRight, drawBottom;

    HashMap<String, Object> gvars = new HashMap<String, Object>();
    HashMap<String, Object> svars = new HashMap<String, Object>();
    HashMap<String, String[]> enums = new HashMap<String, String[]>();
    HashSet<String> persist = new HashSet<String>();
    HashMap<String, Node> nameMap;
    boolean hasPoll, hasCollisionEvents, orientChanged, useCam, snap, camSnap = true, hasVideo;
    static final int MAX_SCENE_CLONES = 1024;
    int sceneClones;
    final ArrayList<Node> pendingClones = new ArrayList<Node>();
    final HashSet<String> pendingCloneNames = new HashSet<String>();
    final float[] collisionA = new float[8], collisionB = new float[8];
    public Layer layer;
    java.util.Random rnd = new java.util.Random();
    int swipeState = -1;
    final float[] ptx = new float[16], pty = new float[16];
    final boolean[] pton = new boolean[16];
    public int sceneSerial;
    public static final int EDIT_MOVE = 0, EDIT_SIZE = 1, EDIT_ROTATE = 2;
    public int editMode = EDIT_MOVE;
    public final Node autoload = new Node();
    boolean autoloadStarted;
    int visualCopyCount;
    static final int MAX_VISUAL_COPIES = 256;
    String pendingScene;
    Node cam;
    float camX, camY, camZ = 1f;
    Matrix camM = new Matrix();
    ArrayDeque<Msg> msgs = new ArrayDeque<Msg>();
    ArrayList<Msg> gq = new ArrayList<Msg>();
    float curDt;
    double clk;
    Task curTask;
    ArrayList<Node> grabs = new ArrayList<Node>();
    HashSet<Blk> infRun = new HashSet<Blk>();
    final float[] fp = new float[2];
    final Matrix hm = new Matrix();
    float mx, my;
    HashMap<Blk, float[]> tclk = new HashMap<Blk, float[]>();
    HashMap<String, ArrayList<Object[]>> fnMap;
    ArrayList<Tw> tws = new ArrayList<Tw>();
    ArrayList<Hold> holds = new ArrayList<Hold>();
    ArrayList<Task> tasks = new ArrayList<Task>();
    HashMap<String, Bitmap> cache = new HashMap<String, Bitmap>();
    final HashMap<String, AssetOrder> assetOrders = new HashMap<String, AssetOrder>();
    HashMap<String, Bitmap> thumbs = new HashMap<String, Bitmap>();
    HashMap<String, Mask> masks = new HashMap<String, Mask>();
    HashMap<String, Typeface> tfCache = new HashMap<String, Typeface>();
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    RectF rf = new RectF();
    private final RectF editRect = new RectF(), maskRect = new RectF();

    public Game(Src s) {
        src = s;
        autoload.id = -2;
        autoload.name = "Autoload";
        autoload.type = "Autoload";
        autoload.visible = false;
        Scene sc = new Scene();
        sc.id = nextSceneId++;
        sc.name = "Escena 1";
        scenes.add(sc);
        startId = sc.id;
        curScene = sc;
        nodes = sc.nodes;
    }

    public void applyOrientation(boolean land) {
        landscape = land;
        vw = land ? vwL : vwP;
        vh = land ? vhL : vhP;
    }

    // ================= JSON =================
    public String toJson() throws Exception {
        return toJson(null);
    }

    public String toJson(Set<Integer> keep) throws Exception {
        return toJson(keep, null);
    }

    String toJson(Set<Integer> keep, Set<String> used) throws Exception {
        JSONObject o = new JSONObject();
        o.put("land", landscape);
        o.put("st", stretch);
        o.put("vm", viewMode);
        o.put("vwL", vwL);
        o.put("vhL", vhL);
        o.put("vwP", vwP);
        o.put("vhP", vhP);
        o.put("bg", bg);
        o.put("asave", autosave);
        o.put("next", nextId);
        o.put("nextScene", nextSceneId);
        o.put("start", startId);
        JSONArray sa = new JSONArray();
        for (Scene s : scenes) {
            if (keep != null && !keep.contains(s.id)) continue;
            JSONObject so = new JSONObject();
            so.put("id", s.id);
            so.put("name", s.name);
            JSONArray na = new JSONArray();
            for (Node n : s.nodes) na.put(n.toJson());
            so.put("nodes", na);
            sa.put(so);
        }
        o.put("scenes", sa);
        if (!assetOrders.isEmpty()) {
            JSONObject orders = new JSONObject();
            for (Map.Entry<String, AssetOrder> entry : assetOrders.entrySet()) {
                if (used == null || used.contains(entry.getKey())) orders.put(entry.getKey(), entry.getValue().toJson());
            }
            if (orders.length() > 0) o.put("assetOrders", orders);
        }
        if (!autoload.script.isEmpty()) o.put("autoload", Blk.arr(autoload.script));
        LinkedHashMap<String, String[]> en = new LinkedHashMap<String, String[]>();
        LinkedHashSet<String> ps = new LinkedHashSet<String>();
        collectDecls(en, ps, keep);
        JSONObject eo = new JSONObject();
        for (Map.Entry<String, String[]> e : en.entrySet()) {
            JSONArray v = new JSONArray();
            for (String x : e.getValue()) v.put(x);
            eo.put(e.getKey(), v);
        }
        o.put("enums", eo);
        JSONArray pa = new JSONArray();
        for (String s : ps) pa.put(s);
        o.put("persist", pa);
        return o.toString();
    }

    static ArrayList<Node> parseNodes(JSONArray a) throws Exception {
        ArrayList<Node> l = new ArrayList<Node>();
        if (a != null) for (int i = 0; i < a.length(); i++) l.add(Node.fromJson(a.getJSONObject(i)));
        return l;
    }

    public static Game fromJson(String json, Src s, boolean runtime) throws Exception {
        JSONObject o = new JSONObject(json);
        Game g = new Game(s);
        g.autoload.script = Blk.list(o.optJSONArray("autoload"));
        JSONObject orders = o.optJSONObject("assetOrders");
        if (orders != null) {
            Iterator<String> names = orders.keys();
            while (names.hasNext()) {
                String name = names.next();
                g.assetOrders.put(name, new AssetOrder(orders.getJSONObject(name)));
            }
        }
        g.scenes.clear();
        g.landscape = o.optBoolean("land", true);
        g.stretch = o.optString("st", "keep");
        g.viewMode = o.optString("vm", "viewport");
        g.autosave = o.optBoolean("asave", true);
        g.bg = o.optInt("bg", 0xFF202020);
        if (o.has("vwL")) {
            g.vwL = Math.max(16, o.optInt("vwL", 1280));
            g.vhL = Math.max(16, o.optInt("vhL", 720));
            g.vwP = Math.max(16, o.optInt("vwP", 720));
            g.vhP = Math.max(16, o.optInt("vhP", 1280));
        } else {
            int w = Math.max(16, o.optInt("vw", 1280)), h = Math.max(16, o.optInt("vh", 720));
            if (g.landscape) {
                g.vwL = w;
                g.vhL = h;
                g.vwP = h;
                g.vhP = w;
            } else {
                g.vwP = w;
                g.vhP = h;
                g.vwL = h;
                g.vhL = w;
            }
        }
        g.applyOrientation(g.landscape);
        JSONArray sa = o.optJSONArray("scenes");
        if (sa != null) {
            for (int i = 0; i < sa.length(); i++) {
                JSONObject so = sa.getJSONObject(i);
                Scene sc = new Scene();
                sc.id = so.getInt("id");
                sc.name = so.optString("name", "Escena " + (i + 1));
                if (runtime) sc.raw = so.toString();
                else sc.nodes = parseNodes(so.optJSONArray("nodes"));
                g.scenes.add(sc);
            }
        } else {
            Scene sc = new Scene();
            sc.id = 1;
            sc.name = "Escena 1";
            JSONArray na = o.optJSONArray("nodes");
            if (runtime) {
                JSONObject so = new JSONObject();
                so.put("nodes", na == null ? new JSONArray() : na);
                sc.raw = so.toString();
            } else {
                sc.nodes = parseNodes(na);
            }
            g.scenes.add(sc);
        }
        if (g.scenes.isEmpty()) {
            Scene sc = new Scene();
            sc.id = 1;
            sc.name = "Escena 1";
            g.scenes.add(sc);
        }
        int maxScene = 0, maxNode = 0;
        for (Scene sc : g.scenes) {
            maxScene = Math.max(maxScene, sc.id);
            for (Node n : sc.nodes) maxNode = Math.max(maxNode, n.id);
        }
        g.nextSceneId = Math.max(o.optInt("nextScene", 1), maxScene + 1);
        g.nextId = Math.max(o.optInt("next", 1), maxNode + 1);
        g.startId = o.optInt("start", g.scenes.get(0).id);
        if (g.sceneById(g.startId) == null) g.startId = g.scenes.get(0).id;
        g.curScene = g.sceneById(g.startId);
        g.nodes = g.curScene.nodes;
        JSONObject eo = o.optJSONObject("enums");
        if (eo != null) {
            Iterator<String> it = eo.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONArray va = eo.getJSONArray(k);
                String[] vs = new String[va.length()];
                for (int i = 0; i < vs.length; i++) vs[i] = va.getString(i);
                g.enums.put(k, vs);
            }
        }
        JSONArray pa = o.optJSONArray("persist");
        if (pa != null) for (int i = 0; i < pa.length(); i++) g.persist.add(pa.getString(i));
        if (!runtime) g.rebuild();
        return g;
    }

    public static Game fromJson(String json, Src s) throws Exception {
        return fromJson(json, s, false);
    }

    // ================= Escenas =================
    public Scene sceneById(int id) {
        for (Scene s : scenes) if (s.id == id) return s;
        return null;
    }

    public Scene sceneByName(String n) {
        for (Scene s : scenes) if (s.name.equals(n)) return s;
        return null;
    }

    String uniqueSceneName(String base) {
        String nm = base;
        int k = 2;
        while (sceneByName(nm) != null) nm = base + " " + (k++);
        return nm;
    }

    public ArrayList<Node> allNodes() {
        return allNodes(null);
    }

    public ArrayList<Node> allNodes(Set<Integer> keep) {
        ArrayList<Node> l = new ArrayList<Node>();
        for (Scene s : scenes) if (keep == null || keep.contains(s.id)) l.addAll(s.nodes);
        if (!autoload.script.isEmpty()) l.add(autoload);
        return l;
    }

    // Escenas a las que se puede llegar desde la inicial (el exportador omite las demás)
    public HashSet<Integer> reachableScenes() {
        HashSet<Integer> keep = new HashSet<Integer>();
        ArrayList<Scene> q = new ArrayList<Scene>();
        Scene st = sceneById(startId);
        if (st == null) st = scenes.get(0);
        keep.add(st.id);
        q.add(st);
        sceneRefs(autoload.script, q, keep);
        for (int i = 0; i < q.size(); i++) for (Node n : q.get(i).nodes) sceneRefs(n.script, q, keep);
        return keep;
    }

    void sceneRefs(ArrayList<Blk> l, ArrayList<Scene> q, HashSet<Integer> keep) {
        for (Blk b : l) {
            if (b.t.equals("scene")) {
                Scene s = sceneByName(b.s("scene"));
                if (s != null && keep.add(s.id)) q.add(s);
            }
            sceneRefs(b.body, q, keep);
            sceneRefs(b.els, q, keep);
        }
    }

    public Scene addScene(String name) {
        Scene s = new Scene();
        s.id = nextSceneId++;
        s.name = uniqueSceneName(name);
        scenes.add(s);
        return s;
    }

    public Scene duplicateScene(Scene source, String name) throws Exception {
        return copyToScene(source.nodes, name);
    }

    public Scene nodeToScene(Node root, String name, boolean move) throws Exception {
        ArrayList<Node> branch = new ArrayList<Node>();
        for (Node n : nodes) if (isDesc(root, n)) branch.add(n);
        if (branch.isEmpty()) throw new IllegalArgumentException("El nodo no está en esta escena");
        compute();
        Scene scene = copyToScene(branch, name);
        Node copy = scene.nodes.get(branch.indexOf(root));
        copy.x = root.wx;
        copy.y = root.wy;
        if (move) remove(root);
        return scene;
    }

    Scene copyToScene(ArrayList<Node> originals, String name) throws Exception {
        ArrayList<Node> copy = new ArrayList<Node>();
        for (Node n : originals) copy.add(Node.fromJson(n.toJson()));
        HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
        for (Node n : copy) {
            int nid = nextId++;
            map.put(n.id, nid);
            n.id = nid;
        }
        for (Node n : copy) {
            if (n.parent >= 0) {
                Integer np = map.get(n.parent);
                n.parent = np == null ? -1 : np;
            }
        }
        Scene s = new Scene();
        s.id = nextSceneId++;
        s.name = uniqueSceneName(name);
        s.nodes = copy;
        scenes.add(s);
        return s;
    }

    public boolean removeScene(Scene s) {
        if (scenes.size() <= 1) return false;
        scenes.remove(s);
        if (startId == s.id) startId = scenes.get(0).id;
        if (curScene == s) openScene(scenes.get(0));
        return true;
    }

    // Abre una escena en el editor
    public void openScene(Scene s) {
        curScene = s;
        nodes = s.nodes;
        sel = null;
        rebuild();
    }

    public void renameSceneRefs(String from, String to) {
        for (Node n : allNodes()) renameWalk(n.script, from, to);
    }

    void renameWalk(ArrayList<Blk> l, String from, String to) {
        for (Blk b : l) {
            if (b.t.equals("scene") && b.s("scene").equals(from)) b.f.put("scene", to);
            renameWalk(b.body, from, to);
            renameWalk(b.els, from, to);
        }
    }

    // Declaraciones del proyecto: enums definidos y variables que se guardan
    public void collectDecls(LinkedHashMap<String, String[]> en, LinkedHashSet<String> ps) {
        collectDecls(en, ps, null);
    }

    public void collectDecls(LinkedHashMap<String, String[]> en, LinkedHashSet<String> ps, Set<Integer> keep) {
        for (Node n : allNodes(keep)) declWalk(n.script, en, ps);
    }

    void declWalk(ArrayList<Blk> l, LinkedHashMap<String, String[]> en, LinkedHashSet<String> ps) {
        for (Blk b : l) {
            if (b.t.equals("enum") && !b.s("name").trim().isEmpty()) {
                ArrayList<String> vs = new ArrayList<String>();
                for (String x : b.s("values").split(",")) if (!x.trim().isEmpty()) vs.add(x.trim());
                en.put(b.s("name").trim(), vs.toArray(new String[0]));
            }
            if (b.t.equals("save")) {
                for (String x : b.s("names").split(",")) if (!x.trim().isEmpty()) ps.add(x.trim());
            }
            declWalk(b.body, en, ps);
            declWalk(b.els, en, ps);
        }
    }

    // ================= Árbol =================
    public Node byId(int id) {
        for (Node n : nodes) if (n.id == id) return n;
        return null;
    }

    public Node byName(String s) {
        if (nameMap != null) return nameMap.get(s);
        for (Node n : nodes) if (n.name.equals(s)) return n;
        return null;
    }

    public boolean isDesc(Node anc, Node c) {
        int guard = 0;
        for (Node q = c; q != null && guard < 500; guard++) {
            if (q == anc) return true;
            q = q.parent >= 0 ? byId(q.parent) : null;
        }
        return false;
    }

    public Node add(String type) {
        Node n = new Node();
        n.id = nextId++;
        n.type = type;
        int c = 1;
        String nm = type + c;
        while (byName(nm) != null) {
            c++;
            nm = type + c;
        }
        n.name = nm;
        if (type.equals("Button")) {
            n.w = 300;
            n.h = 100;
            n.text = "Botón";
        } else if (type.equals("Sound")) {
            n.w = 90;
            n.h = 90;
        } else if (type.equals("Camera2D")) {
            n.w = 60;
            n.h = 60;
            n.lr = vw;
            n.lb = vh;
            boolean any = false;
            for (Node q : nodes) if (q.type.equals("Camera2D")) any = true;
            n.current = !any;
        } else if (type.equals("AnimatedSprite")) {
            n.loop = true;
            n.autoplay = true;
        } else if (type.equals("Label")) {
            n.w = 400;
            n.h = 100;
            n.text = "Texto";
            n.tsize = 40;
        } else if (type.equals("Video")) {
            n.w = 640;
            n.h = 360;
            n.autoplay = true;
        } else if (type.equals("Color")) {
            n.w = 300;
            n.h = 300;
            n.color = 0xFF4CAF50;
        } else if (type.equals("Anchor")) {
            n.w = 40;
            n.h = 40;
        } else if (type.equals("Limiter")) {
            n.w = 480;
            n.h = 320;
        } else if (type.equals("Grab") || type.equals("Collision")) {
            n.w = 160;
            n.h = 160;
        }
        n.x = vw / 2f - n.w / 2f;
        n.y = vh / 2f - n.h / 2f;
        nodes.add(n);
        rebuild();
        return n;
    }

    void queueClone(Blk b, Node source, Node self) {
        if (edit || source == null || source == autoload || sceneClones >= MAX_SCENE_CLONES) return;
        Node clone;
        try {
            // Copia los ajustes actuales y los bloques, pero no el estado de ejecución.
            clone = Node.fromJson(source.toJson());
        } catch (Exception e) {
            return;
        }
        String base = b.s("name").trim();
        if (base.isEmpty() || base.equals("self")) base = source.name + "_copia";
        String name = base;
        for (int i = 2; byName(name) != null || pendingCloneNames.contains(name); i++) name = base + "_" + i;
        clone.name = name;
        clone.id = nextId++;
        clone.current = false; // La copia de una cámara no reemplaza a la cámara activa.
        if (!b.s("x").trim().isEmpty()) clone.x = (float) num(val(b.s("x"), self));
        if (!b.s("y").trim().isEmpty()) clone.y = (float) num(val(b.s("y"), self));
        pendingClones.add(clone);
        pendingCloneNames.add(name);
        sceneClones++;
    }

    void addVisualCopy(Blk block, Node source, Node self) {
        if (edit || source == null || visualCopyCount >= MAX_VISUAL_COPIES) return;
        switch (source.type) {
            case "Image": case "ImageButton": case "AnimatedSprite":
            case "Color": case "Button": case "Label": break;
            default: return;
        }
        float dx = (float) num(val(block.s("x"), self)), dy = (float) num(val(block.s("y"), self));
        if (!Float.isFinite(dx) || !Float.isFinite(dy)) return;
        Node picture = block.b("share") ? null : captureVisual(source);
        if (source.visualCopies == null) source.visualCopies = new ArrayList<VisualCopy>();
        source.visualCopies.add(new VisualCopy(picture, dx, dy));
        visualCopyCount++;
    }

    // Captura una pose sin serializar scripts, copiar bitmaps ni retener una lista de fotogramas.
    Node captureVisual(Node source) {
        compute();
        Node picture = new Node();
        picture.type = source.type;
        picture.wx = source.wx;
        picture.wy = source.wy;
        picture.w = source.w;
        picture.h = source.h;
        picture.wa = source.wa;
        picture.wvis = source.wvis;
        picture.rotd = source.rotd;
        if (source.rotd) picture.tm = new Matrix(source.tm);
        picture.res = source.res;
        picture.pressed = source.pressed;
        picture.dim = source.dim;
        picture.color = source.color;
        if (source.type.equals("AnimatedSprite")) {
            picture.type = "Image";
            picture.res = source.frames.isEmpty() ? "" : source.frames.get(Math.min(source.frame, source.frames.size() - 1));
        } else if (source.type.equals("Button") || source.type.equals("Label")) {
            picture.text = source.type.equals("Label") ? labelText(source) : source.text;
            picture.literalText = true;
            picture.font = source.font;
            picture.tsize = source.tsize;
            picture.bold = source.bold;
            picture.italic = source.italic;
            picture.shadow = source.shadow;
            picture.wrap = source.wrap;
            picture.align = source.align;
            picture.valign = source.valign;
            picture.outline = source.outline;
            picture.ocolor = source.ocolor;
        }
        return picture;
    }

    void activateClones() {
        if (pendingClones.isEmpty()) return;
        int first = nodes.size();
        nodes.addAll(pendingClones);
        pendingClones.clear();
        pendingCloneNames.clear();
        fnMap = null;
        swipeState = -1;
        for (int i = first; i < nodes.size(); i++) initRuntimeNode(nodes.get(i));
        rebuild();
        compute();
        startNodes(first);
    }

    public void remove(Node n) {
        ArrayList<Node> del = new ArrayList<Node>();
        for (Node q : nodes) if (isDesc(n, q)) del.add(q);
        for (Node q : del) {
            if (q.mp != null) {
                try {
                    q.mp.release();
                } catch (Exception e) {
                }
            }
            nodes.remove(q);
        }
        if (sel != null && del.contains(sel)) sel = null;
        rebuild();
    }

    public void reparent(Node n, int np) {
        Node pn = np >= 0 ? byId(np) : null;
        compute();
        if (n.inherit) {
            if (pn != null) {
                n.x = n.wx - pn.wx;
                n.y = n.wy - pn.wy;
            } else if (n.parent >= 0) {
                n.x = n.wx;
                n.y = n.wy;
            }
        }
        n.parent = np;
        rebuild();
        compute();
    }

    public void rebuild() {
        order.clear();
        for (Node n : nodes) {
            n.inOrder = false;
            n.par = n.parent >= 0 ? byId(n.parent) : null;
        }
        addOrder(-1);
    }

    void addOrder(int pid) {
        for (Node n : nodes) {
            if (n.inOrder) continue;
            boolean root = pid == -1 ? (n.parent == -1 || n.par == null) : n.parent == pid;
            if (!root) continue;
            n.inOrder = true;
            order.add(n);
            addOrder(n.id);
        }
    }

    // Posición global antes del giro, incluso si aún no se ha dibujado este fotograma.
    static float origin(Node n, boolean horizontal) {
        float v = 0f;
        while (n != null && n.inOrder) {
            v += horizontal ? n.x : n.y;
            n = n.inherit ? n.par : null;
        }
        return v;
    }

    static boolean inheritsPositionFrom(Node node, Node ancestor) {
        for (Node n = node; n != null && n.inOrder; n = n.inherit ? n.par : null) {
            if (n == ancestor) return true;
        }
        return false;
    }

    // Limita al hijo directo en los ejes del padre; la rotación heredada no cambia esos ejes.
    boolean constrain(Node n) {
        Node box = n.par;
        if (edit || box == null || !box.type.equals("Limiter")) return false;
        float ox = n.inherit ? 0f : origin(box, true), oy = n.inherit ? 0f : origin(box, false);
        float bw = Math.max(0f, box.w), bh = Math.max(0f, box.h);
        float oldX = n.x, oldY = n.y;
        if (n.rot % 360f == 0f) {
            n.w = Math.max(0f, Math.min(n.w, bw));
            n.h = Math.max(0f, Math.min(n.h, bh));
            n.x = ox + Math.max(0f, Math.min(n.x - ox, bw - n.w));
            n.y = oy + Math.max(0f, Math.min(n.y - oy, bh - n.h));
        } else {
            double radians = Math.toRadians(n.rot);
            float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
            n.w = Math.max(0f, n.w);
            n.h = Math.max(0f, n.h);
            float ew = Math.abs(c) * n.w + Math.abs(s) * n.h;
            float eh = Math.abs(s) * n.w + Math.abs(c) * n.h;
            if (ew > bw + 0.0001f || eh > bh + 0.0001f) {
                float scale = Math.min(ew == 0f ? 1f : bw / ew, eh == 0f ? 1f : bh / eh);
                n.w *= scale;
                n.h *= scale;
                ew *= scale;
                eh *= scale;
            }
            float lx = n.x - ox + n.w / 2f, ly = n.y - oy + n.h / 2f;
            Node anchor = n.anchor.isEmpty() ? null : byName(n.anchor);
            boolean external = anchor != null && !inheritsPositionFrom(anchor, n);
            float cx = lx, cy = ly;
            if (anchor != null) {
                float ax = origin(anchor, true) - origin(box, true) + anchor.w / 2f;
                float ay = origin(anchor, false) - origin(box, false) + anchor.h / 2f;
                cx = ax + c * (lx - ax) - s * (ly - ay);
                cy = ay + s * (lx - ax) + c * (ly - ay);
            }
            float dx = Math.max(ew / 2f, Math.min(cx, bw - ew / 2f)) - cx;
            float dy = Math.max(eh / 2f, Math.min(cy, bh - eh / 2f)) - cy;
            n.x += external ? c * dx + s * dy : dx;
            n.y += external ? -s * dx + c * dy : dy;
        }
        return Math.abs(n.x - oldX) > 0.0001f || Math.abs(n.y - oldY) > 0.0001f;
    }

    void compute() {
        for (int i = 0; i < order.size(); i++) {
            Node n = order.get(i);
            Node pn = n.par;
            boolean inh = pn != null && n.inherit;
            constrain(n);
            n.wx = inh ? pn.wx + n.x : n.x;
            n.wy = inh ? pn.wy + n.y : n.y;
            n.wa = inh ? pn.wa * n.alpha : n.alpha;
            n.wvis = (!inh || pn.wvis) && n.visible && !n.norender;
            Matrix pm = inh && pn.rotd ? pn.tm : null;
            if (n.rot == 0f && pm == null) {
                n.rotd = false;
                continue;
            }
            if (n.tm == null) n.tm = new Matrix();
            if (pm != null) n.tm.set(pm);
            else n.tm.reset();
            if (n.rot != 0f) {
                Node an = n.anchor.isEmpty() ? null : byName(n.anchor);
                n.tm.preRotate(n.rot, an != null ? origin(an, true) + an.w / 2f : n.wx + n.w / 2f,
                        an != null ? origin(an, false) + an.h / 2f : n.wy + n.h / 2f);
            }
            n.rotd = true;
        }
    }

    // Esquinas en coordenadas de escena, incluyendo el giro heredado.
    static void collisionCorners(Node n, float[] points) {
        points[0] = points[6] = n.wx;
        points[1] = points[3] = n.wy;
        points[2] = points[4] = n.wx + n.w;
        points[5] = points[7] = n.wy + n.h;
        if (n.rotd) n.tm.mapPoints(points);
    }

    // Dos rectángulos se separan si sus proyecciones no se solapan en un eje de un lado.
    static boolean separates(float[] a, float[] b) {
        for (int i = 0; i < 4; i += 2) {
            float x = a[i + 3] - a[i + 1], y = a[i] - a[i + 2];
            float minA = Float.POSITIVE_INFINITY, maxA = Float.NEGATIVE_INFINITY;
            float minB = Float.POSITIVE_INFINITY, maxB = Float.NEGATIVE_INFINITY;
            for (int j = 0; j < 8; j += 2) {
                float pa = a[j] * x + a[j + 1] * y, pb = b[j] * x + b[j + 1] * y;
                minA = Math.min(minA, pa);
                maxA = Math.max(maxA, pa);
                minB = Math.min(minB, pb);
                maxB = Math.max(maxB, pb);
            }
            if (maxA <= minB || maxB <= minA) return true;
        }
        return false;
    }

    boolean overlaps(Node a, Node b) {
        if (a == null || b == null || a == b || !a.collisionEnabled || !b.collisionEnabled || !a.wvis || !b.wvis || !a.inOrder || !b.inOrder) return false;
        if (a.w <= 0 || a.h <= 0 || b.w <= 0 || b.h <= 0) return false;
        if (cam != null && viewMode.equals("camera") && a.fixed != b.fixed) return false;
        if (!a.rotd && !b.rotd) {
            return a.wx < b.wx + b.w && b.wx < a.wx + a.w && a.wy < b.wy + b.h && b.wy < a.wy + a.h;
        }
        collisionCorners(a, collisionA);
        collisionCorners(b, collisionB);
        return !separates(collisionA, collisionB) && !separates(collisionB, collisionA);
    }

    static boolean collisionEvent(String type) {
        return type.equals("collision") || type.equals("collisionExit") || type.equals("collisionStay");
    }

    void pollCollisions() {
        compute();
        ArrayList<Object[]> hits = null;
        for (Node n : nodes) {
            if (!n.type.equals("Collision")) continue;
            for (Blk b : n.script) {
                if (!collisionEvent(b.t)) continue;
                boolean touching = overlaps(n, tgt(b, n));
                boolean fire = b.t.equals("collisionExit") ? !touching && b.overlapping
                        : touching && (b.t.equals("collisionStay") ? b.task == null || b.task.done : !b.overlapping);
                if (fire) {
                    if (hits == null) hits = new ArrayList<Object[]>();
                    hits.add(new Object[]{b, n});
                }
                b.overlapping = touching;
            }
        }
        // Ejecutar después de medir todos los pares: un evento puede mover otros nodos.
        if (hits != null) for (Object[] hit : hits) {
            if (pendingScene != null) break;
            Blk b = (Blk) hit[0];
            Task task = spawn(b.body, (Node) hit[1]);
            if (b.t.equals("collisionStay")) b.task = task;
        }
    }

    // ================= Información del proyecto =================
    public HashSet<String> usedAssets() {
        return usedAssets(null);
    }

    public HashSet<String> usedAssets(Set<Integer> keep) {
        HashSet<String> s = new HashSet<String>();
        for (Node n : allNodes(keep)) {
            if (!n.font.isEmpty()) s.add(n.font);
            if (!n.res.isEmpty()) s.add(n.res);
            if (!n.snd.isEmpty()) s.add(n.snd);
            s.addAll(n.frames);
            collect(n.script, s);
        }
        return s;
    }

    void collect(ArrayList<Blk> l, HashSet<String> s) {
        for (Blk b : l) {
            String a = b.s("asset");
            if (!a.isEmpty()) s.add(a);
            collect(b.body, s);
            collect(b.els, s);
        }
    }

    public HashMap<String, float[]> imageSizes() {
        return imageSizes(null);
    }

    public HashMap<String, float[]> imageSizes(Set<Integer> keep) {
        HashMap<String, float[]> m = new HashMap<String, float[]>();
        ArrayList<Node> all = allNodes(keep);
        for (Node n : all) {
            if (!n.res.isEmpty()) putSize(m, n.res, n.w, n.h);
            for (String s : n.frames) putSize(m, s, n.w, n.h);
        }
        for (Node n : all) markUnknown(n.script, m);
        for (Map.Entry<String, AssetOrder> entry : assetOrders.entrySet()) {
            float[] visible = m.get(entry.getKey());
            if (visible == null || visible[0] <= 0f || visible[1] <= 0f) continue;
            AssetOrder crop = entry.getValue();
            visible[0] *= crop.width / (float) crop.sourceWidth;
            visible[1] *= crop.height / (float) crop.sourceHeight;
        }
        return m;
    }

    static void putSize(HashMap<String, float[]> m, String k, float w, float h) {
        float[] v = m.get(k);
        if (v == null) {
            m.put(k, new float[]{w, h});
        } else {
            v[0] = Math.max(v[0], w);
            v[1] = Math.max(v[1], h);
        }
    }

    void markUnknown(ArrayList<Blk> l, HashMap<String, float[]> m) {
        for (Blk b : l) {
            if (b.t.equals("setimg") && !b.s("asset").isEmpty()) m.put(b.s("asset"), new float[]{-1, -1});
            markUnknown(b.body, m);
            markUnknown(b.els, m);
        }
    }

    public boolean hasSizeTween() {
        return hasSizeTween(null);
    }

    public boolean hasSizeTween(Set<Integer> keep) {
        for (Node n : allNodes(keep)) if (sizeTween(n.script)) return true;
        return false;
    }

    boolean sizeTween(ArrayList<Blk> l) {
        for (Blk b : l) {
            if (b.t.equals("tween") && b.s("what").equals("tamaño")) return true;
            if (sizeTween(b.body) || sizeTween(b.els)) return true;
        }
        return false;
    }

    // ================= Recursos =================
    public Bitmap img(String name) {
        if (name == null || name.isEmpty()) return null;
        if (cache.containsKey(name)) return cache.get(name);
        Bitmap b = null;
        try (InputStream i = src.open(name)) {
            b = BitmapFactory.decodeStream(i);
        } catch (Exception e) {
        }
        cache.put(name, b);
        return b;
    }

    public void flush() {
        cache.clear();
        masks.clear();
        tfCache.clear();
    }

    static String visualImage(Node n) {
        if (n.type.equals("AnimatedSprite")) {
            return n.frames.isEmpty() ? "" : n.frames.get(Math.max(0, Math.min(n.frame, n.frames.size() - 1)));
        }
        return n.type.equals("Image") || n.type.equals("ImageButton") ? n.res : "";
    }

    void imageBounds(Node n, String asset, float x, float y, RectF out) {
        AssetOrder order = assetOrders.get(asset);
        if (order == null) out.set(x, y, x + n.w, y + n.h);
        else out.set(x + n.w * order.left, y + n.h * order.top,
                x + n.w * order.right, y + n.h * order.bottom);
    }

    void editBounds(Node n, float x, float y, RectF out) {
        imageBounds(n, assetOrders.isEmpty() ? "" : visualImage(n), x, y, out);
    }

    void setEditPosition(Node n, boolean horizontal, float value) {
        if (!Float.isFinite(value)) return;
        editBounds(n, n.x, n.y, editRect);
        if (horizontal) n.x += value - editRect.left;
        else n.y += value - editRect.top;
    }

    void setEditSize(Node n, boolean horizontal, float value) {
        if (!Float.isFinite(value)) return;
        editBounds(n, n.x, n.y, editRect);
        if (editRect.isEmpty()) return;
        if (horizontal) resizeEditEdges(n, 0, 0, Math.max(1f, value) - editRect.width(), 0);
        else resizeEditEdges(n, 0, 0, 0, Math.max(1f, value) - editRect.height());
    }

    // Convierte los desplazamientos de los lados visibles a desplazamientos del lienzo original.
    void resizeEditEdges(Node n, float left, float top, float right, float bottom) {
        float oldW = n.w, oldH = n.h;
        if (left != 0f || right != 0f) {
            float dw = (right - left) * (oldW / editRect.width());
            n.x += left - ((editRect.left - n.x) / oldW) * dw;
            n.w += dw;
        }
        if (top != 0f || bottom != 0f) {
            float dh = (bottom - top) * (oldH / editRect.height());
            n.y += top - ((editRect.top - n.y) / oldH) * dh;
            n.h += dh;
        }
    }

    public void fitNode(Node n) {
        if (n.type.equals("Video")) {
            int[] d = vdims(n.res);
            if (d != null) {
                n.w = d[0];
                n.h = d[1];
            }
            return;
        }
        String a = n.type.equals("AnimatedSprite") ? (n.frames.isEmpty() ? "" : n.frames.get(0)) : n.res;
        AssetOrder order = assetOrders.get(a);
        if (order != null) {
            n.w = order.sourceWidth;
            n.h = order.sourceHeight;
            return;
        }
        Bitmap b = img(a);
        if (b != null) {
            n.w = b.getWidth();
            n.h = b.getHeight();
        }
    }

    int[] vdims(String name) {
        if (name == null || name.isEmpty()) return null;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(src.file(name).getPath());
            int w = Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h = Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            String rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (rot != null && (rot.equals("90") || rot.equals("270"))) {
                int t = w;
                w = h;
                h = t;
            }
            return new int[]{w, h};
        } catch (Exception e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception e2) {
            }
        }
    }

    Bitmap vthumb(String name) {
        if (name == null || name.isEmpty()) return null;
        if (thumbs.containsKey(name)) return thumbs.get(name);
        Bitmap b = null;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(src.file(name).getPath());
            b = r.getFrameAtTime(0);
            if (b != null && b.getWidth() > 480) b = Bitmap.createScaledBitmap(b, 480, Math.max(1, b.getHeight() * 480 / b.getWidth()), true);
        } catch (Exception e) {
            b = null;
        } finally {
            try {
                r.release();
            } catch (Exception e2) {
            }
        }
        thumbs.put(name, b);
        return b;
    }

    public void playSound(Node n) {
        if (n.snd == null || n.snd.isEmpty()) return;
        try {
            if (n.mp == null || !n.snd.equals(n.mpName)) {
                if (n.mp != null) n.mp.release();
                n.mp = new MediaPlayer();
                n.mp.setDataSource(src.file(n.snd).getPath());
                n.mp.prepare();
                n.mpName = n.snd;
            }
            n.mp.setLooping(n.loop);
            n.mp.seekTo(0);
            n.mp.start();
        } catch (Exception e) {
            try {
                if (n.mp != null) n.mp.release();
            } catch (Exception e2) {
            }
            n.mp = null;
        }
    }

    public void stopSound(Node n) {
        try {
            if (n.mp != null && n.mp.isPlaying()) {
                n.mp.pause();
                n.mp.seekTo(0);
            }
        } catch (Exception e) {
        }
    }

    public void pauseAudio() {
        for (Node n : nodes) {
            try {
                if (n.mp != null && n.mp.isPlaying()) {
                    n.mp.pause();
                    n.mpWas = true;
                }
            } catch (Exception e) {
            }
        }
    }

    public void resumeAudio() {
        for (Node n : nodes) {
            try {
                if (n.mpWas && n.mp != null) n.mp.start();
            } catch (Exception e) {
            }
            n.mpWas = false;
        }
    }

    public void release() {
        for (Node n : nodes) {
            if (n.mp != null) {
                try {
                    n.mp.release();
                } catch (Exception e) {
                }
                n.mp = null;
            }
        }
    }

    // ================= Variables =================
    static String enc(Object o) {
        if (o instanceof Long) return "i:" + o;
        if (o instanceof Double) return "f:" + o;
        if (o instanceof Boolean) return "b:" + o;
        if (o instanceof EnumVal) return "e:" + ((EnumVal) o).type + ":" + ((EnumVal) o).name;
        return "s:" + o;
    }

    Object dec(String raw) {
        if (raw == null || raw.length() < 2) return null;
        String v = raw.substring(2);
        try {
            switch (raw.charAt(0)) {
                case 'i': return Long.valueOf(v);
                case 'f': return Double.valueOf(v);
                case 'b': return Boolean.valueOf(v);
                case 'e': {
                    String[] q = v.split(":", 2);
                    String[] vals = enums.get(q[0]);
                    if (vals == null || q.length < 2) return null;
                    for (int i = 0; i < vals.length; i++) if (vals[i].equals(q[1])) return new EnumVal(q[0], q[1], i);
                    return null;
                }
                default: return v;
            }
        } catch (Exception e) {
            return null;
        }
    }

    Object castEnum(String type, Object v) {
        String[] vals = enums.get(type);
        if (vals == null || vals.length == 0) return String.valueOf(v);
        String s = String.valueOf(v);
        int d = s.lastIndexOf('.');
        if (d >= 0) s = s.substring(d + 1);
        for (int i = 0; i < vals.length; i++) if (vals[i].equals(s)) return new EnumVal(type, s, i);
        return new EnumVal(type, vals[0], 0);
    }

    Object castType(String type, Object v) {
        switch (type) {
            case "int": return Long.valueOf((long) num(v));
            case "float": return Double.valueOf(num(v));
            case "bool": return Boolean.valueOf(truthy(v));
            case "string": return String.valueOf(v);
            default: return castEnum(type, v);
        }
    }

    Object castLike(Object cur, Object v) {
        if (cur instanceof Long) return Long.valueOf((long) num(v));
        if (cur instanceof Double) return Double.valueOf(num(v));
        if (cur instanceof Boolean) return Boolean.valueOf(truthy(v));
        if (cur instanceof EnumVal) return castEnum(((EnumVal) cur).type, v);
        return String.valueOf(v);
    }

    Frame fnFrame() {
        Task t = curTask;
        if (t == null || t.fns == 0) return null;
        for (int i = t.stack.size() - 1; i >= 0; i--) if (t.stack.get(i).fn) return t.stack.get(i);
        return null;
    }

    Object getVar(String name, Node self) {
        Frame fr = fnFrame();
        if (fr != null && fr.loc != null && fr.loc.containsKey(name)) return fr.loc.get(name);
        if (self != null && self.vars.containsKey(name)) return self.vars.get(name);
        if (svars.containsKey(name)) return svars.get(name);
        return gvars.get(name);
    }

    void putVar(String name, Object v, Node self) {
        Frame fr = fnFrame();
        if (fr != null && fr.loc != null && fr.loc.containsKey(name)) {
            fr.loc.put(name, v);
            return;
        }
        if (self != null && self.vars.containsKey(name)) self.vars.put(name, v);
        else if (svars.containsKey(name)) svars.put(name, v);
        else gvars.put(name, v);
    }

    public void autoSave() {
        if (!autosave || store == null) return;
        for (String nm : persist) {
            Object v = svars.containsKey(nm) ? svars.get(nm) : gvars.get(nm);
            if (v != null) store.put(nm, enc(v));
        }
    }

    // ================= Valores y lógica =================
    static double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof Boolean) return ((Boolean) o) ? 1 : 0;
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    static boolean truthy(Object o) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).doubleValue() != 0;
        String s = String.valueOf(o);
        return !s.isEmpty() && !s.equals("false");
    }

    static boolean isInt(String s) {
        int n = s.length();
        int i = s.charAt(0) == '-' ? 1 : 0;
        if (i == n) return false;
        for (; i < n; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    static boolean isDec(String s) {
        int n = s.length();
        int i = s.charAt(0) == '-' ? 1 : 0;
        boolean dot = false, dig = false;
        for (; i < n; i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (dot) return false;
                dot = true;
            } else if (c >= '0' && c <= '9') {
                dig = true;
            } else {
                return false;
            }
        }
        return dot && dig;
    }

    Object prop(Node n, String pr) {
        switch (pr) {
            case "x": return Double.valueOf(n.x);
            case "y": return Double.valueOf(n.y);
            case "w": return Double.valueOf(n.w);
            case "h": return Double.valueOf(n.h);
            case "alpha":
            case "modulate": return Double.valueOf(n.alpha);
            case "visible": return Boolean.valueOf(n.visible);
            case "text": return n.text;
            case "fps": return Double.valueOf(n.fps);
            case "frame": return Long.valueOf(n.frame);
            case "zoom": return Double.valueOf(n.zoom);
            case "rot": return Double.valueOf(n.rot);
            default: return null;
        }
    }

    Object val(String s, Node self) {
        s = s.trim();
        if (s.isEmpty()) return "";
        if (s.equals("true")) return Boolean.TRUE;
        if (s.equals("false")) return Boolean.FALSE;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        try {
            if (isInt(s)) return Long.valueOf(s);
            if (isDec(s)) return Double.valueOf(s);
        } catch (Exception e) {
        }
        Object gv = getVar(s, self);
        if (gv != null) return gv;
        int d = s.indexOf('.');
        if (d > 0) {
            String nn = s.substring(0, d), rest = s.substring(d + 1);
            String[] ev = enums.get(nn);
            Object ov = getVar(nn, self);
            if (ov instanceof EnumVal) {
                if (rest.equals("indice") || rest.equals("índice")) return Long.valueOf(((EnumVal) ov).idx);
                if (rest.equals("nombre")) return ((EnumVal) ov).name;
            }
            if (ev != null && rest.equals("cantidad")) return Long.valueOf(ev.length);
            if (ev != null) {
                for (int i = 0; i < ev.length; i++) if (ev[i].equals(rest)) return new EnumVal(nn, rest, i);
            }
            Node n = nn.equals("self") ? self : byName(nn);
            if (n != null) {
                Object v = prop(n, rest);
                if (v == null) v = n.vars.get(rest);
                if (v != null) return v;
            }
        }
        return s;
    }

    static float pf0(String s, float d) {
        try {
            return Float.parseFloat(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return d;
        }
    }

    boolean hasExtra(Blk b) {
        return pf0(b.s("nc"), 0) > 0 || !b.s("prob").trim().isEmpty() || !b.s("touch").isEmpty()
                || pf0(b.s("tsec"), 0) > 0;
    }

    boolean probPass(Blk b) {
        String ps = b.s("prob").trim();
        if (ps.isEmpty()) return true;
        return rnd.nextFloat() < pf0(ps, 1f);
    }

    // Condiciones (primera + las extra unidas con Y) y "tocar"; sin probabilidad
    boolean condCore(Blk b, Node self) {
        boolean res = true;
        if (b.f.containsKey("expr")) {
            String ex = b.s("expr");
            res = ex.trim().isEmpty() ? hasExtra(b) : evalExpr(b, self);
        } else {
            if (!(b.s("l").trim().isEmpty() && b.s("r").trim().isEmpty() && hasExtra(b))) {
                res = cond1(b.s("l"), b.s("op"), b.s("r"), b.b("not"), self);
            }
            int nc = (int) pf0(b.s("nc"), 0);
            for (int i = 1; i <= nc && res; i++) {
                String k = "c" + i + "_";
                String l = b.s(k + "l"), r = b.s(k + "r");
                if (l.trim().isEmpty() && r.trim().isEmpty()) continue;
                res = cond1(l, b.s(k + "op"), r, b.b(k + "n"), self);
            }
        }
        String tc = b.s("touch");
        if (res && tc.equals("hold")) res = self != null && self.pressed;
        else if (res && tc.equals("swipe")) res = self != null && self.swiped;
        else if (res && tc.equals("in")) res = fingerIn(self);
        else if (res && tc.equals("out")) res = !fingerIn(self);
        return res;
    }

    // ¿Hay algún dedo tocando la pantalla justo encima de este nodo?
    // ¿el punto (ya con la cámara aplicada) cae dentro del nodo? Si está rotado se lleva a su espacio.
    boolean inNode(Node n, float px, float py) {
        if (n.rotd) {
            if (!n.tm.invert(hm)) return false;
            fp[0] = px;
            fp[1] = py;
            hm.mapPoints(fp);
            px = fp[0];
            py = fp[1];
        }
        mx = px;
        my = py;
        return px >= n.wx && px <= n.wx + n.w && py >= n.wy && py <= n.wy + n.h;
    }

    boolean fingerIn(Node n) {
        if (n == null || !n.wvis || (n.wa <= 0.003f && !n.touchAlways)) return false;
        for (int i = 0; i < 16; i++) {
            if (!pton[i]) continue;
            float vx = ptx[i], vy = pty[i];
            boolean c = useCam && !n.fixed;
            float px = c ? (vx - vw / 2f) / camZ + camX : vx;
            float py = c ? (vy - vh / 2f) / camZ + camY : vy;
            if (inNode(n, px, py) && (!n.smart || maskHit(n, mx, my))) return true;
        }
        return false;
    }

    boolean cond(Blk b, Node self) {
        return condCore(b, self) && probPass(b);
    }

    // 0 = falso, 1 = verdadero, 2 = esperando a que pase el tiempo.
    // Cada Si / Sino, si lleva su propio contador: no se mezcla con esperas ni con otros Si.
    int condState(Task t, Blk b) {
        float ts = tsecOf(b, t.self);
        if (ts <= 0) return cond(b, t.self) ? 1 : 0;
        probLive = false;
        boolean ok = condCore(b, t.self);
        probLive = true;
        if (b.s("tm").startsWith("reloj")) {
            // reloj propio: no detiene el script; sirve en eventos que se repiten
            if (!ok) {
                tclk.remove(b);
                return 0;
            }
            float[] c = tclk.get(b);
            if (c == null) {
                c = new float[]{(float) clk};
                tclk.put(b, c);
            }
            if (clk - c[0] < ts) return 0;
            tclk.remove(b);
            return probPass(b) ? 1 : 0;
        }
        if (!ok) {
            if (t.tm != null) t.tm.remove(b);
            return 0;
        }
        if (t.tm == null) t.tm = new HashMap<Blk, float[]>();
        float[] e = t.tm.get(b);
        if (e == null) {
            e = new float[1];
            t.tm.put(b, e);
        } else {
            e[0] += curDt;
        }
        if (e[0] >= ts) {
            t.tm.remove(b);
            return probPass(b) ? 1 : 0;
        }
        return 2;
    }

    // ---- Condición escrita en un solo campo: y / o / no, paréntesis y = != < > <= >=
    ArrayList<String> ct;
    int ci;
    Node cself;
    boolean probLive = true;
    final ArrayList<Object[]> layerOps = new ArrayList<Object[]>();

    // Conserva las comas decimales de por(0,5) y prob(0,5).
    static boolean separatorComma(String s, int i) {
        return s.charAt(i) == ',' && (i + 1 == s.length() || !Character.isDigit(s.charAt(i + 1))
                || (i > 0 && !Character.isDigit(s.charAt(i - 1)) && "(+ -\t".indexOf(s.charAt(i - 1)) < 0));
    }

    static ArrayList<String> tokens(String s) {
        ArrayList<String> r = new ArrayList<String>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"') {
                int j = i + 1;
                while (j < n && s.charAt(j) != '"') {
                    if (s.charAt(j++) == '\\' && j < n) j++;
                }
                r.add(s.substring(i, Math.min(j + 1, n)));
                i = j + 1;
            } else if (separatorComma(s, i)) {
                r.add(",");
                i++;
            } else if ("=!<>&|()".indexOf(c) >= 0) {
                int j = i + 1;
                if (j < n && " == != <= >= && || ".contains(" " + c + s.charAt(j) + " ")) j++;
                r.add(s.substring(i, j));
                i = j;
            } else {
                int j = i;
                while (j < n && !Character.isWhitespace(s.charAt(j)) && "=!<>&|()\"".indexOf(s.charAt(j)) < 0 && !separatorComma(s, j)) j++;
                r.add(s.substring(i, j));
                i = j;
            }
        }
        return r;
    }

    String pk() {
        return ci < ct.size() ? ct.get(ci) : null;
    }

    static boolean isOr(String t) {
        return "o".equals(t) || "or".equals(t) || "||".equals(t);
    }

    static boolean isAnd(String t) {
        return "y".equals(t) || "and".equals(t) || "&&".equals(t);
    }

    static boolean isCmp(String t) {
        return "=".equals(t) || "==".equals(t) || "!=".equals(t) || "<".equals(t) || ">".equals(t)
                || "<=".equals(t) || ">=".equals(t);
    }

    ArrayList<String> toks(Blk b) {
        String ex = b.s("expr");
        if (b.ctk == null || !ex.equals(b.ck)) {
            b.ck = ex;
            b.ctk = tokens(ex);
            b.cta = null;
            for (int i = 0; i + 2 < b.ctk.size(); i++) {
                if (b.ctk.get(i).equals("por") && b.ctk.get(i + 1).equals("(")) {
                    b.cta = b.ctk.get(i + 2);
                    break;
                }
            }
        }
        return b.ctk;
    }

    float argF(String a) {
        Object o = val(a, cself);
        return o instanceof Number ? ((Number) o).floatValue() : pf0(String.valueOf(o), 0f);
    }

    // Segundos que debe mantenerse la condición: por(X) escrito en la condición (o el valor antiguo)
    float tsecOf(Blk b, Node self) {
        cself = self;
        if (b.f.containsKey("expr")) {
            toks(b);
            if (b.cta != null) return argF(b.cta);
        }
        return pf0(b.s("tsec"), 0);
    }

    boolean evalExpr(Blk b, Node self) {
        try {
            ct = toks(b);
            ci = 0;
            cself = self;
            collisionGeometry = false;
            boolean r = pOr();
            return ci >= ct.size() && r;
        } catch (RuntimeException e) {
            return false;
        }
    }

    boolean pOr() {
        boolean r = pAnd();
        while (isOr(pk())) {
            ci++;
            boolean q = pAnd();
            r = r || q;
        }
        return r;
    }

    boolean pAnd() {
        boolean r = pNot();
        while (isAnd(pk())) {
            ci++;
            boolean q = pNot();
            r = r && q;
        }
        return r;
    }

    boolean pNot() {
        String t = pk();
        if ("!".equals(t) || "not".equals(t) || "no".equals(t)) {
            String nx = ci + 1 < ct.size() ? ct.get(ci + 1) : null;
            if (t.equals("!") || (nx != null && !isCmp(nx) && !isAnd(nx) && !isOr(nx) && !nx.equals(")"))) {
                ci++;
                return !pNot();
            }
        }
        return pCmp();
    }

    boolean collisionGeometry;

    Node collisionNode(String token) {
        if (token.startsWith("\"")) {
            if (token.indexOf('\\') < 0) return byName(token.substring(1, token.length() - 1));
            try {
                return byName(String.valueOf(new JSONTokener(token).nextValue()));
            } catch (Exception e) {
                return null;
            }
        }
        return token.equals("self") ? cself : byName(token);
    }

    boolean pCmp() {
        String t = pk();
        if (t == null) throw new IllegalStateException();
        ci++;
        if (t.equals("(")) {
            boolean r = pOr();
            if (!")".equals(pk())) throw new IllegalStateException();
            ci++;
            return r;
        }
        if ("(".equals(pk())) {
            ci++;
            String a = pk();
            if (a == null || a.equals(")")) throw new IllegalStateException();
            ci++;
            if (t.equals("colisiona")) {
                if (!",".equals(pk())) throw new IllegalStateException();
                ci++;
                String b = pk();
                if (b == null || ")".equals(b)) throw new IllegalStateException();
                ci++;
                if (!")".equals(pk())) throw new IllegalStateException();
                ci++;
                Node first = collisionNode(a), second = collisionNode(b);
                if (first == null || second == null || (!first.type.equals("Collision") && !second.type.equals("Collision"))) return false;
                if (!collisionGeometry) {
                    compute();
                    collisionGeometry = true;
                }
                return overlaps(first, second);
            }
            if (!")".equals(pk())) throw new IllegalStateException();
            ci++;
            if (t.equals("por")) return true;
            if (t.equals("prob")) return !probLive || rnd.nextFloat() < argF(a);
            throw new IllegalStateException();
        }
        if (!isCmp(pk()) && getVar(t, cself) == null) {
            if (t.equals("pulsado")) return cself != null && cself.pressed;
            if (t.equals("dedo_dentro")) return fingerIn(cself);
            if (t.equals("dedo_fuera")) return !fingerIn(cself);
        }
        Object l = val(t, cself);
        if (isCmp(pk())) {
            String op = ct.get(ci++);
            String rt = pk();
            if (rt == null) throw new IllegalStateException();
            ci++;
            return cmp(l, op, val(rt, cself));
        }
        return truthy(l);
    }

    static boolean cmp(Object l, String op, Object r) {
        if (l instanceof EnumVal && r instanceof EnumVal && ((EnumVal) l).type.equals(((EnumVal) r).type)) {
            l = Long.valueOf(((EnumVal) l).idx);
            r = Long.valueOf(((EnumVal) r).idx);
        }
        if (l instanceof Number && r instanceof Number) {
            double x = ((Number) l).doubleValue(), y = ((Number) r).doubleValue();
            switch (op) {
                case "<": return x < y;
                case ">": return x > y;
                case "<=": return x <= y;
                case ">=": return x >= y;
                case "!=": return x != y;
                default: return x == y;
            }
        }
        boolean eq = String.valueOf(l).equals(String.valueOf(r));
        if (op.equals("!=")) return !eq;
        return (op.equals("=") || op.equals("==") || op.isEmpty()) && eq;
    }

    boolean cond1(String ls, String op, String rs, boolean not, Node self) {
        Object l = val(ls, self);
        boolean res;
        if (rs.trim().isEmpty()) {
            res = truthy(l);
        } else {
            res = cmp(l, op, val(rs, self));
        }
        return not ? !res : res;
    }

    // Libera de la memoria las imágenes que ya nadie dibuja (se vuelven a cargar solas al renderizar de nuevo)
    void dropRes(Node n) {
        ArrayList<String> rs = new ArrayList<String>(n.frames);
        rs.add(n.res);
        for (String r : rs) {
            if (r.isEmpty()) continue;
            boolean used = false;
            for (Node q : nodes) {
                if (q != n && !q.norender && (r.equals(q.res) || q.frames.contains(r))) {
                    used = true;
                    break;
                }
            }
            if (!used) cache.remove(r);
        }
    }

    Node tgt(Blk b, Node self) {
        String n = b.s("node").trim();
        if (n.isEmpty() || n.equals("self")) return self;
        return byName(n);
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    void setOrient(boolean land) {
        if (land == landscape) return;
        applyOrientation(land);
        if (host != null) host.setOrientation(land);
        orientChanged = true;
    }

    void exec(Blk b, Node self) {
        Node t = tgt(b, self);
        if (b.f.containsKey("node") && t == null && !b.t.equals("funcExists")) return;
        switch (b.t) {
            case "send": {
                Msg m = new Msg();
                m.m = b.s("msg");
                m.from = self;
                String sc = b.s("scope");
                m.sc = sc.equals("nodo") ? 1 : sc.startsWith("jer") ? 2 : sc.equals("global") ? 3 : 0;
                msgs.add(m);
                break;
            }
            case "var": {
                String name = b.s("name").trim();
                if (name.isEmpty()) break;
                String type = b.s("vtype");
                Object v = castType(type, val(b.s("val"), self));
                if (store != null && persist.contains(name)) {
                    Object st = dec(store.get(name));
                    if (st != null) v = castType(type, st);
                }
                String sc = b.s("scope");
                if (sc.equals("local")) {
                    Frame fr = fnFrame();
                    if (fr != null) {
                        if (fr.loc == null) fr.loc = new HashMap<String, Object>();
                        fr.loc.put(name, v);
                    } else svars.put(name, v);
                } else if (sc.equals("nodo")) self.vars.put(name, v);
                else if (sc.equals("escena")) svars.put(name, v);
                else gvars.put(name, v);
                break;
            }
            case "setvar": {
                String name = b.s("name").trim();
                Object cur = getVar(name, self);
                if (cur != null) putVar(name, castLike(cur, val(b.s("val"), self)), self);
                break;
            }
            case "math": {
                String name = b.s("name").trim();
                Object cur = getVar(name, self);
                if (cur == null) cur = Long.valueOf(0);
                Object v = val(b.s("val"), self);
                double x = num(cur), y = num(v), r;
                switch (b.s("op")) {
                    case "-": r = x - y; break;
                    case "*": r = x * y; break;
                    case "/": r = y == 0 ? 0 : x / y; break;
                    default: r = x + y;
                }
                if (cur instanceof Long && v instanceof Long) putVar(name, Long.valueOf((long) r), self);
                else putVar(name, Double.valueOf(r), self);
                break;
            }
            case "enumnext": {
                String name = b.s("name").trim();
                Object cur = getVar(name, self);
                String et = b.s("enum").trim();
                String[] ev0 = enums.get(et);
                if (!(cur instanceof EnumVal) && ev0 != null && ev0.length > 0) cur = new EnumVal(et, ev0[0], 0);
                if (cur instanceof EnumVal) {
                    EnumVal e = (EnumVal) cur;
                    String[] vals = enums.get(e.type);
                    if (vals != null && vals.length > 0) {
                        String dr = b.s("dir");
                        int i;
                        if (dr.equals("primero")) {
                            i = 0;
                        } else if (dr.equals("último")) {
                            i = vals.length - 1;
                        } else if (dr.equals("aleatorio")) {
                            i = rnd.nextInt(vals.length);
                        } else if (dr.equals("ir a")) {
                            Object tv = castEnum(e.type, val(b.s("to"), self));
                            i = tv instanceof EnumVal ? ((EnumVal) tv).idx : e.idx;
                        } else {
                            i = e.idx + (dr.equals("anterior") ? -1 : 1);
                            if (b.b("wrap")) i = (i % vals.length + vals.length) % vals.length;
                            else i = Math.max(0, Math.min(vals.length - 1, i));
                        }
                        putVar(name, new EnumVal(e.type, vals[i], i), self);
                    }
                }
                break;
            }
            case "save":
                if (store != null) {
                    for (String nm : b.s("names").split(",")) {
                        nm = nm.trim();
                        if (nm.isEmpty()) continue;
                        Object v = getVar(nm, self);
                        if (v != null) store.put(nm, enc(v));
                    }
                }
                break;
            case "clearsave":
                if (store != null) store.clear();
                break;
            case "scene":
                if (sceneByName(b.s("scene")) != null) pendingScene = b.s("scene");
                break;
            case "orientset":
                setOrient(!b.s("mode").equals("vertical"));
                break;
            case "viewmode":
                viewMode = b.s("mode").equals("viewport") ? "viewport" : "camera";
                camSnap = true;
                break;
            case "camset":
                if (t.type.equals("Camera2D")) {
                    String what = b.s("what");
                    if (what.equals("zoom")) t.zoom = Math.max(0.05f, (float) num(val(b.s("val"), self)));
                    else if (what.equals("seguir a")) t.follow = b.s("val").trim();
                    else if (what.equals("suavizado")) t.smooth = Math.max(0f, (float) num(val(b.s("val"), self)));
                    else if (what.equals("activar")) {
                        cam = t;
                        camSnap = true;
                    }
                }
                break;
            case "clone":
                queueClone(b, t, self);
                break;
            case "visualClone":
                addVisualCopy(b, t, self);
                break;
            case "clearVisualClones":
                if (t != null && t.visualCopies != null) {
                    visualCopyCount -= t.visualCopies.size();
                    t.visualCopies = null;
                }
                break;
            case "funcExists": {
                String result = b.s("ret").trim();
                if (!result.isEmpty()) putVar(result, findFn(b.s("name").trim(), t, true) != null, self);
                break;
            }
            case "collisionEnable":
                if (t != null && t.type.equals("Collision")) t.collisionEnabled = b.b("enabled");
                break;
            case "setimg":
                t.res = b.s("asset");
                if (t.fit) fitNode(t);
                constrain(t);
                break;
            case "setsnd":
                t.snd = b.s("asset");
                break;
            case "play": {
                String loop = b.s("loop");
                if (loop.equals("activar") || loop.equals("desactivar")) t.loop = loop.equals("activar");
                boolean reverse = b.s("dir").equals("reversa"), restart = b.b("restart");
                if (t.type.equals("Sound")) playSound(t);
                else if (t.type.equals("AnimatedSprite")) {
                    if (restart || (reverse && t.frame == 0)) t.frame = reverse ? Math.max(0, t.frames.size() - 1) : 0;
                    if (restart || t.backwards != reverse) t.ft = 0f;
                    t.backwards = reverse;
                    t.playing = true;
                } else if (t.type.equals("Video")) {
                    t.backwards = reverse;
                    if (restart) t.vseek = -1f;
                    t.vcmd = restart ? "restart" : "play";
                }
                break;
            }
            case "stop":
                if (t.type.equals("Sound")) stopSound(t);
                else if (t.type.equals("AnimatedSprite")) t.playing = false;
                else if (t.type.equals("Video")) {
                    t.vcmd = "stop";
                    t.vseek = -1f;
                }
                break;
            case "vpause":
                if (t.type.equals("Video")) t.vcmd = "pause";
                break;
            case "vseek":
                if (t.type.equals("Video")) {
                    t.vseek = Math.max(0f, (float) num(val(b.s("secs"), self)));
                }
                break;
            case "vvol":
                t.vol = clamp01((float) num(val(b.s("val"), self)));
                break;
            case "setvideo":
                if (t.type.equals("Video")) {
                    t.res = b.s("asset");
                    t.vseek = -1f;
                    t.vcmd = "reload";
                }
                break;
            case "breakloop":
                if (t.type.equals("AnimatedSprite") || t.type.equals("Video")) t.loop = false;
                break;
            case "quit":
                if (!edit && host != null) host.quit();
                break;
            case "mod":
                t.alpha = clamp01((float) num(val(b.s("val"), self)));
                if (b.f.containsKey("touch")) t.touchAlways = b.b("touch");
                break;
            case "layer":
                layerOps.add(new Object[]{t, b.s("mode")});
                break;
            case "vis":
                t.visible = truthy(val(b.s("val"), self));
                break;
            case "stoprender":
                t.norender = true;
                dropRes(t);
                // VideoLayer pausa la salida sin perder la intención de reproducción.
                break;
            case "startrender":
                t.norender = false;
                break;
            case "pos":
                if (!b.s("x").trim().isEmpty()) t.x = (float) num(val(b.s("x"), self));
                if (!b.s("y").trim().isEmpty()) t.y = (float) num(val(b.s("y"), self));
                constrain(t);
                break;
            case "text":
                t.text = String.valueOf(val(b.s("val"), self));
                break;
            case "tween": {
                Tw w = new Tw();
                w.n = t;
                w.d = (float) num(val(b.s("secs"), self));
                w.easing = b.s("ease");
                String what = b.s("what");
                if (what.equals("modulate")) {
                    w.kind = 0;
                    w.from[0] = t.alpha;
                    w.to[0] = clamp01((float) num(val(b.s("x"), self)));
                } else if (what.equals("rotación")) {
                    if (b.s("x").trim().isEmpty()) break;
                    w.kind = 3;
                    w.from[0] = t.rot;
                    w.to[0] = (float) num(val(b.s("x"), self));
                } else {
                    boolean mv = what.equals("mover");
                    w.kind = mv ? 1 : 2;
                    w.from[0] = mv ? t.x : t.w;
                    w.from[1] = mv ? t.y : t.h;
                    w.has[0] = !b.s("x").trim().isEmpty();
                    w.has[1] = !b.s("y").trim().isEmpty();
                    w.to[0] = (float) num(val(b.s("x"), self));
                    w.to[1] = (float) num(val(b.s("y"), self));
                }
                for (int i = tws.size() - 1; i >= 0; i--) {
                    if (tws.get(i).n == t && tws.get(i).kind == w.kind) tws.remove(i);
                }
                if (what.equals("modulate") && b.f.containsKey("touch")) t.touchAlways = b.b("touch");
                if (w.d <= 0) applyTween(w, 1f);
                else tws.add(w);
                break;
            }
            default:
                break;
        }
    }

    void applyTween(Tw tween, float progress) {
        tween.apply(progress);
        if (tween.kind != 0) constrain(tween.n);
    }

    // ================= Ejecutor de scripts (con esperas y bucles) =================
    void push(Task t, ArrayList<Blk> list, int kind, int rem, Blk blk) {
        Frame f = new Frame();
        f.list = list;
        f.kind = kind;
        f.rem = rem;
        f.blk = blk;
        t.stack.add(f);
    }

    Task spawn(ArrayList<Blk> list, Node self) {
        Task t = new Task();
        t.self = self;
        t.fresh = true;
        push(t, list, 0, 0, null);
        tasks.add(t);
        step(t);
        return t;
    }

    void step(Task t) {
        Task pv = curTask;
        curTask = t;
        try {
            step0(t);
        } finally {
            curTask = pv;
        }
    }

    Frame pop(Task t) {
        Frame f = t.stack.remove(t.stack.size() - 1);
        if (f.fn) {
            t.self = f.prev;
            t.fns--;
        }
        return f;
    }

    Frame pushFn(Task t, Blk fb, HashMap<String, Object> loc, Node prev, String rv) {
        push(t, fb.body, 0, 0, fb);
        Frame f = t.stack.get(t.stack.size() - 1);
        f.fn = true;
        f.loc = loc;
        f.prev = prev;
        f.rv = rv;
        t.fns++;
        return f;
    }

    Object[] findFn(String name, Node self, boolean exact) {
        if (name.isEmpty() || (exact && self == null)) return null;
        if (fnMap == null) {
            fnMap = new HashMap<String, ArrayList<Object[]>>();
            for (Node n : nodes) {
                for (Blk e : n.script) {
                    String k = e.t.equals("func") ? e.s("name").trim() : "";
                    if (k.isEmpty()) continue;
                    ArrayList<Object[]> l = fnMap.get(k);
                    if (l == null) fnMap.put(k, l = new ArrayList<Object[]>());
                    l.add(new Object[]{e, n});
                }
            }
        }
        ArrayList<Object[]> l = fnMap.get(name);
        if (l == null) return null;
        for (Object[] o : l) if (o[1] == self) return o;
        return exact ? null : l.get(0);
    }

    // separa por comas, sin cortar dentro de comillas o paréntesis
    static ArrayList<String> splitArgs(String s) {
        ArrayList<String> r = new ArrayList<String>();
        int dp = 0, st = 0;
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') q = !q;
            else if (!q && c == '(') dp++;
            else if (!q && c == ')') dp--;
            else if (!q && dp == 0 && c == ',') {
                r.add(s.substring(st, i).trim());
                st = i + 1;
            }
        }
        r.add(s.substring(st).trim());
        return r;
    }

    static final class Parameter {
        final int index;
        final String name, defaultValue;
        Parameter(int index, String name, String defaultValue) {
            this.index = index;
            this.name = name;
            this.defaultValue = defaultValue;
        }
    }

    static ArrayList<Parameter> parameters(Blk fn) {
        String spec = fn.s("params");
        if (fn.parameters != null && spec.equals(fn.paramSpec)) return fn.parameters;
        fn.paramSpec = spec;
        fn.parameters = new ArrayList<Parameter>();
        if (spec.trim().isEmpty()) return fn.parameters;
        int index = 0;
        for (String field : splitArgs(spec)) {
            int eq = field.indexOf('=');
            String name = (eq < 0 ? field : field.substring(0, eq)).trim();
            if (!name.isEmpty()) fn.parameters.add(new Parameter(index, name, eq < 0 ? "" : field.substring(eq + 1).trim()));
            index++;
        }
        return fn.parameters;
    }

    HashMap<String, Object> bind(Blk fb, Blk call, Node caller) {
        ArrayList<Parameter> ps = parameters(fb);
        if (ps.isEmpty()) return null;
        HashMap<String, Object> loc = new HashMap<String, Object>();
        ArrayList<String> args = callOption(call, "passArgs", "args") ? splitArgs(call.s("args")) : null;
        for (Parameter p : ps) {
            String value = args != null && p.index < args.size() && !args.get(p.index).isEmpty()
                    ? args.get(p.index) : p.defaultValue;
            loc.put(p.name, value.isEmpty() ? Long.valueOf(0) : val(value, caller));
        }
        return loc;
    }

    // Avanzar / Velocidad / Rotar: 1 = terminó, 2 = sigue (se vuelve a llamar en el siguiente fotograma)
    int moveStep(Task t, Blk b) {
        Node n = tgt(b, t.self);
        if (n == null) return 1;
        boolean sp = b.t.equals("speed"), rt = b.t.equals("rotate"), to = rt && b.s("mode").startsWith("ir");
        boolean inf = sp && b.b("inf");
        float secs = (float) num(val(b.s("secs"), t.self));
        float[] e = t.tm == null ? null : t.tm.get(b);
        if (e == null) {
            if (secs <= 0 && !sp) {
                float d = (float) num(val(b.s(rt ? "deg" : "dist"), t.self));
                if (rt) {
                    n.rot = to ? d : n.rot + d;
                    constrain(n);
                } else move(n, b, d, t.self);
                return 1;
            }
            if (inf && !infRun.add(b)) return 1;
            if (t.tm == null) t.tm = new HashMap<Blk, float[]>();
            t.tm.put(b, e = new float[2]);
            e[1] = n.rot;
        }
        String cx = sp ? b.s("expr").trim() : "";
        boolean end = !cx.isEmpty() && !evalExpr(b, t.self);
        if (!end) {
            float el0 = e[0];
            e[0] += curDt;
            if (sp) {
                float v0 = (float) num(val(b.s("v0"), t.self)), v1 = (float) num(val(b.s("v1"), t.self));
                float ramp = (float) num(val(b.s("ramp"), t.self));
                float va = ramp > 0 ? v0 + (v1 - v0) * Math.min(1f, el0 / ramp) : v1;
                float vb = ramp > 0 ? v0 + (v1 - v0) * Math.min(1f, e[0] / ramp) : v1;
                end = move(n, b, (va + vb) / 2f * curDt, t.self)
                        || (!inf && (secs > 0 ? e[0] >= secs : cx.isEmpty() && e[0] >= Math.max(ramp, 1f)));
            } else if (rt) {
                float p1 = Math.min(1f, e[0] / secs), d = (float) num(val(b.s("deg"), t.self));
                n.rot = to ? e[1] + (d - e[1]) * p1 : e[1] + d * p1;
                constrain(n);
                end = p1 >= 1f;
            } else {
                float p0 = Math.min(1f, el0 / secs), p1 = Math.min(1f, e[0] / secs);
                end = move(n, b, (float) num(val(b.s("dist"), t.self)) * (p1 - p0), t.self) || p1 >= 1f;
            }
        }
        if (end) {
            t.tm.remove(b);
            if (inf) infRun.remove(b);
        }
        return end ? 1 : 2;
    }

    // Mueve el nodo; con antirebote devuelve true si chocó con el borde (el bloque debe detenerse del todo)
    boolean move(Node n, Blk b, float d, Node self) {
        float ux = 1f, uy = 0f;
        switch (b.s("dir")) {
            case "izquierda": ux = -1f; break;
            case "arriba": ux = 0f; uy = -1f; break;
            case "abajo": ux = 0f; uy = 1f; break;
            case "ángulo": {
                double a = Math.toRadians(num(val(b.s("ang"), self)));
                ux = (float) Math.cos(a);
                uy = (float) Math.sin(a);
                break;
            }
            default: break;
        }
        n.x += ux * d;
        n.y += uy * d;
        boolean anti = b.b("anti");
        if (n.par != null && n.par.type.equals("Limiter")) return constrain(n) && anti;
        if (!anti && !b.b("edge")) return false;
        float ox = n.x, oy = n.y;
        float mw = n.par != null ? n.par.w : vw, mh = n.par != null ? n.par.h : vh;
        n.x = Math.max(0f, Math.min(n.x, mw - n.w));
        n.y = Math.max(0f, Math.min(n.y, mh - n.h));
        return anti && (n.x != ox || n.y != oy);
    }

    void step0(Task t) {
        int budget = 3000;
        while (!t.done && t.wait <= 0f && budget-- > 0) {
            if (t.stack.isEmpty()) {
                t.done = true;
                break;
            }
            Frame f = t.stack.get(t.stack.size() - 1);
            if (f.idx >= f.list.size()) {
                boolean again = false;
                if (f.kind == 1) {
                    f.rem--;
                    again = f.rem > 0;
                } else if (f.kind == 2) {
                    again = true;
                } else if (f.kind == 3) {
                    again = cond(f.blk, t.self);
                }
                if (again) {
                    f.idx = 0;
                    f.chain = false;
                } else {
                    pop(t);
                }
                continue;
            }
            Blk b = f.list.get(f.idx++);
            switch (b.t) {
                case "if": {
                    int st = condState(t, b);
                    if (st == 2) {
                        f.idx--;
                        t.wait = 0.0001f;
                        break;
                    }
                    f.chain = st == 1;
                    if (f.chain) push(t, b.body, 0, 0, null);
                    break;
                }
                case "elif":
                    if (!f.chain) {
                        int st = condState(t, b);
                        if (st == 2) {
                            f.idx--;
                            t.wait = 0.0001f;
                            break;
                        }
                        if (st == 1) {
                            f.chain = true;
                            push(t, b.body, 0, 0, null);
                        }
                    }
                    break;
                case "breakrep": {
                    int k = -1;
                    for (int q = t.stack.size() - 1; q >= 0 && !t.stack.get(q).fn; q--) {
                        if (t.stack.get(q).kind == 1) {
                            k = q;
                            break;
                        }
                    }
                    if (k >= 0) while (t.stack.size() > k) pop(t);
                    break;
                }
                case "else":
                    if (!f.chain) push(t, b.body, 0, 0, null);
                    f.chain = true;
                    break;
                case "match": {
                    Object mv = val(b.s("val"), t.self);
                    ArrayList<Blk> pick = null, def = null;
                    for (Blk c : b.body) {
                        if (c.t.equals("case")) {
                            if (pick == null && caseHit(mv, c.s("val"), t.self)) pick = c.body;
                        } else if (c.t.equals("default")) {
                            def = c.body;
                        }
                    }
                    if (pick == null) pick = def;
                    if (pick != null) push(t, pick, 0, 0, null);
                    break;
                }
                case "call":
                case "callNode": {
                    boolean exact = b.t.equals("callNode");
                    Object[] fd = findFn(b.s("name").trim(), exact ? tgt(b, t.self) : t.self, exact);
                    if (fd == null || t.stack.size() > 40) break;
                    Blk fb = (Blk) fd[0];
                    Node fnode = (Node) fd[1];
                    HashMap<String, Object> loc = bind(fb, b, t.self);
                    if (b.b("par")) {
                        Task nt = new Task();
                        nt.self = fnode;
                        nt.fresh = true;
                        pushFn(nt, fb, loc, fnode, "");
                        tasks.add(nt);
                        step(nt);
                    } else {
                        String target = !FN_VOID.equals(fb.s("returnMode")) && callOption(b, "saveResult", "ret")
                                ? b.s("ret").trim() : "";
                        pushFn(t, fb, loc, t.self, target);
                        t.self = fnode;
                    }
                    break;
                }
                case "return":
                case "exitFunction": {
                    Frame fn = fnFrame();
                    if (fn == null && b.t.equals("exitFunction")) break;
                    String mode = fn == null ? "" : fn.blk.s("returnMode");
                    boolean legacy = !FN_VOID.equals(mode) && !FN_VALUE.equals(mode);
                    String result = b.s("val");
                    Object rv = !b.t.equals("return") || FN_VOID.equals(mode) || result.trim().isEmpty()
                            ? null : val(result, t.self);
                    while (!t.stack.isEmpty()) {
                        Frame x = pop(t);
                        if (x.fn) {
                            if (rv != null && !x.rv.isEmpty()) putVar(x.rv, rv, t.self);
                            break;
                        }
                    }
                    if (rv != null && legacy) svars.put("retorno", rv);
                    break;
                }
                case "advance":
                case "speed":
                case "rotate":
                    if (moveStep(t, b) == 2) {
                        f.idx--;
                        t.wait = 0.0001f;
                    }
                    break;
                case "wait":
                    t.wait = Math.max(0.0001f, (float) num(val(b.s("secs"), t.self)) + t.carry);
                    t.carry = 0f;
                    t.wk = true;
                    break;
                case "repeat": {
                    int n = (int) num(val(b.s("n"), t.self));
                    if (n > 0) push(t, b.body, 1, n, b);
                    break;
                }
                case "forever":
                    push(t, b.body, 2, 0, b);
                    break;
                case "while":
                    if (cond(b, t.self)) push(t, b.body, 3, 0, b);
                    break;
                case "break":
                    while (!t.stack.isEmpty()) {
                        Frame x = pop(t);
                        if (x.kind != 0 || x.fn) break;
                    }
                    break;
                default:
                    exec(b, t.self);
                    f.chain = false;
                    if (pendingScene != null) t.done = true;
            }
        }
        if (!t.done && t.wait <= 0f && budget <= 0) t.wait = 0.0001f;
    }

    boolean caseHit(Object v, String list, Node self) {
        for (String p : list.split(",")) {
            p = p.trim();
            if (!p.isEmpty() && cmp(v, "=", val(p, self))) return true;
        }
        return false;
    }

    // Capas: subir / bajar entre hermanos (se dibuja después = más arriba)
    public void layer(Node n, String mode) {
        boolean up = mode.equals("subir") || mode.equals("al frente");
        int i = nodes.indexOf(n), pos = i, st = up ? 1 : -1;
        if (i < 0) return;
        for (int j = i + st; j >= 0 && j < nodes.size(); j += st) {
            if (nodes.get(j).parent != n.parent) continue;
            pos = j;
            if (!mode.startsWith("al ")) break;
        }
        if (pos == i) return;
        nodes.remove(i);
        nodes.add(pos, n);
        rebuild();
    }

    static boolean under(Node n, Node root) {
        for (Node p = n; p != null; p = p.par) if (p == root) return true;
        return false;
    }

    void pump() {
        int guard = 0;
        while (!msgs.isEmpty() && guard++ < 500) {
            Msg m = msgs.poll();
            String nm = m.m.trim();
            if (m.sc == 3 && pendingScene != null) gq.add(m);
            for (Node n : nodes) {
                if (m.from != null && ((m.sc == 1 && n != m.from) || (m.sc == 2 && !under(n, m.from)))) continue;
                for (int i = 0; i < n.script.size(); i++) {
                    Blk b = n.script.get(i);
                    if (b.t.equals("recv") && b.s("msg").trim().equals(nm)) spawn(b.body, n);
                }
            }
        }
    }

    // ================= Escenas y ciclo de juego =================
    public void start() {
        gvars.clear();
        autoloadStarted = false;
        autoload.vars.clear();
        Scene s = sceneById(startId);
        if (s == null && !scenes.isEmpty()) s = scenes.get(0);
        if (s != null) enterScene(s);
    }

    void enterScene(Scene sc) {
        swipeState = -1;
        release();
        curScene = sc;
        if (!edit && sc.raw != null) {
            try {
                nodes = parseNodes(new JSONObject(sc.raw).optJSONArray("nodes"));
            } catch (Exception e) {
                nodes = new ArrayList<Node>();
            }
        } else {
            nodes = edit ? sc.nodes : new ArrayList<Node>(sc.nodes);
        }
        if (!edit && !autoload.script.isEmpty()) nodes.add(0, autoload);
        pendingClones.clear();
        pendingCloneNames.clear();
        sceneClones = 0;
        visualCopyCount = 0;
        upAll();
        svars.clear();
        msgs.clear();
        for (Msg m : gq) {
            m.from = null;
            msgs.add(m);
        }
        gq.clear();
        tclk.clear();
        fnMap = null;
        grabs.clear();
        infRun.clear();
        tws.clear();
        holds.clear();
        layerOps.clear();
        tasks.clear();
        cache.clear();
        pendingScene = null;
        rebuild();
        nameMap = new HashMap<String, Node>();
        hasPoll = false;
        hasCollisionEvents = false;
        cam = null;
        camSnap = true;
        hasVideo = false;
        sceneSerial++;
        for (Node n : nodes) {
            nextId = Math.max(nextId, n.id + 1);
            initRuntimeNode(n);
        }
        compute();
        startNodes(0);
        pump();
    }

    void initRuntimeNode(Node n) {
        if (n != autoload || !nameMap.containsKey(n.name)) nameMap.put(n.name, n);
        if (n.type.equals("Video")) hasVideo = true;
        n.playing = n.autoplay;
        n.backwards = false;
        n.visualCopies = null;
        n.rot0 = n.rot;
        n.norender = false;
        n.frame = 0;
        n.ft = 0;
        n.pressed = false;
        n.pid = -1;
        if (n.type.equals("Camera2D") && (cam == null || (n.current && !cam.current))) cam = n;
        for (Blk b : n.script) {
            b.last = null;
            b.acc = 0;
            b.task = null;
            b.overlapping = false;
            if (b.t.equals("frame") || b.t.equals("change") || b.t.equals("every")) hasPoll = true;
            if (n.type.equals("Collision") && collisionEvent(b.t)) hasCollisionEvents = true;
        }
    }

    void startNodes(int first) {
        for (int i = first; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (n.type.equals("Grab") && !n.anchor.isEmpty()) grabTarget(n).anchor = n.anchor;
        }
        for (int i = first; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (n.type.equals("Sound") && n.autoplay) playSound(n);
        }
        for (int i = first; i < nodes.size() && pendingScene == null; i++) {
            Node n = nodes.get(i);
            if (n == autoload) {
                if (autoloadStarted) continue;
                autoloadStarted = true;
            }
            for (Blk b : n.script) {
                if (b.t.equals("start")) spawn(b.body, n);
                if (pendingScene != null) break;
            }
        }
    }

    public boolean active() {
        if (edit || hasPoll || hasCollisionEvents || hasVideo || !pendingClones.isEmpty()
                || pendingScene != null || orientChanged || useCam) return true;
        if (!tasks.isEmpty() || !tws.isEmpty() || !holds.isEmpty() || !msgs.isEmpty() || !grabs.isEmpty()) return true;
        for (Node n : nodes) {
            if (n.playing && n.type.equals("AnimatedSprite") && !n.frames.isEmpty()) return true;
        }
        return false;
    }

    void animate(float dt) {
        for (int k = 0; k < nodes.size(); k++) {
            Node n = nodes.get(k);
            if (!n.type.equals("AnimatedSprite") || n.frames.isEmpty() || n.norender) continue;
            boolean on = edit ? n.preview : n.playing;
            if (!on) continue;
            float spf = 1f / Math.max(0.1f, Math.min(240f, n.fps));
            n.ft += dt;
            while (n.ft >= spf) {
                n.ft -= spf;
                n.frame += n.backwards ? -1 : 1;
                if (n.frame < 0 || n.frame >= n.frames.size()) {
                    if (n.loop) {
                        n.frame = n.backwards ? n.frames.size() - 1 : 0;
                    } else {
                        n.frame = n.backwards ? 0 : n.frames.size() - 1;
                        n.playing = false;
                        n.preview = false;
                        n.ft = 0;
                        break;
                    }
                }
            }
        }
    }

    void updateCamera(float dt) {
        useCam = !edit && viewMode.equals("camera") && cam != null;
        if (!useCam) return;
        compute();
        float tx = cam.wx + cam.w / 2f, ty = cam.wy + cam.h / 2f;
        if (!cam.follow.isEmpty()) {
            Node f = byName(cam.follow);
            if (f != null) {
                tx = f.wx + f.w / 2f;
                ty = f.wy + f.h / 2f;
            }
        }
        if (camSnap || cam.smooth <= 0f) {
            camX = tx;
            camY = ty;
            camSnap = false;
        } else {
            float k = 1f - (float) Math.exp(-cam.smooth * dt);
            camX += (tx - camX) * k;
            camY += (ty - camY) * k;
        }
        camZ = Math.max(0.05f, cam.zoom);
        if (cam.lim) {
            float hw = vw / (2f * camZ), hh = vh / (2f * camZ);
            camX = (cam.lr - cam.ll < 2 * hw) ? (cam.ll + cam.lr) / 2f : Math.max(cam.ll + hw, Math.min(cam.lr - hw, camX));
            camY = (cam.lb - cam.lt < 2 * hh) ? (cam.lt + cam.lb) / 2f : Math.max(cam.lt + hh, Math.min(cam.lb - hh, camY));
        }
    }

    public void update(float dt) {
        animate(dt);
        if (edit) return;
        curDt = dt;
        clk += dt;
        if (pendingScene == null) activateClones();
        if (!layerOps.isEmpty()) {
            for (Object[] o : layerOps) layer((Node) o[0], (String) o[1]);
            layerOps.clear();
        }
        if (hasVideo) {
            for (int a = 0; a < nodes.size(); a++) {
                Node n = nodes.get(a);
                if (!n.vdone) continue;
                n.vdone = false;
                for (int i = 0; i < n.script.size(); i++) {
                    Blk b = n.script.get(i);
                    if (b.t.equals("vend")) spawn(b.body, n);
                }
            }
        }
        for (int i = tws.size() - 1; i >= 0; i--) {
            Tw t = tws.get(i);
            t.t += dt;
            float k = t.d <= 0 ? 1f : Math.min(1f, t.t / t.d);
            applyTween(t, k);
            if (k >= 1f) tws.remove(i);
        }
        for (int i = holds.size() - 1; i >= 0; i--) {
            Hold h = holds.get(i);
            if (h.n.pid == h.pid) {
                if (h.task == null || h.task.done) h.task = spawn(h.b.body, h.n);
            } else {
                if (h.task != null) h.task.done = true;
                spawn(h.b.els, h.n);
                holds.remove(i);
            }
        }
        if (hasPoll) {
            for (int a = 0; a < nodes.size(); a++) {
                Node n = nodes.get(a);
                for (int i = 0; i < n.script.size(); i++) {
                    Blk b = n.script.get(i);
                    if (b.t.equals("frame")) {
                        if (b.task == null || b.task.done) b.task = spawn(b.body, n);
                    } else if (b.t.equals("every")) {
                        double iv = num(val(b.s("secs"), n));
                        b.acc += dt;
                        if (iv > 0.0001 && b.acc >= iv) {
                            b.acc = 0;
                            if (b.task == null || b.task.done) b.task = spawn(b.body, n);
                        }
                    } else if (b.t.equals("change")) {
                        Node t = tgt(b, n);
                        if (t != null) {
                            String cur = String.valueOf(prop(t, b.s("prop")));
                            if (b.last == null) {
                                b.last = cur;
                            } else if (!cur.equals(b.last)) {
                                b.last = cur;
                                spawn(b.body, n);
                            }
                        }
                    }
                }
            }
        }
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            if (t.done) continue;
            if (t.fresh) {
                t.fresh = false;
                continue;
            }
            if (t.wait > 0f) {
                t.wait -= dt;
                if (t.wait > 0f) continue;
                if (t.wk) {
                    t.carry = Math.max(t.wait, -0.1f);
                    t.wk = false;
                }
                t.wait = 0f;
            }
            step(t);
        }
        for (int i = tasks.size() - 1; i >= 0; i--) if (tasks.get(i).done) tasks.remove(i);
        if (hasCollisionEvents && pendingScene == null) pollCollisions();
        pump();
        if (orientChanged) {
            orientChanged = false;
            for (int a = 0; a < nodes.size(); a++) {
                Node n = nodes.get(a);
                for (int i = 0; i < n.script.size(); i++) {
                    Blk b = n.script.get(i);
                    if (b.t.equals("orient")) spawn(b.body, n);
                }
            }
        }
        if (pendingScene != null) {
            String nm = pendingScene;
            pendingScene = null;
            Scene sc = sceneByName(nm);
            if (sc != null) enterScene(sc);
        }
        updateCamera(dt);
    }

    // ================= Entrada =================
    boolean touchable(Node n) {
        if (n.type.equals("Button") || n.type.equals("ImageButton") || n.type.equals("Grab")) return true;
        if (n.tch < 0) n.tch = wantsTouch(n.script) ? 1 : 0;
        return n.tch == 1;
    }

    boolean wantsTouch(ArrayList<Blk> l) {
        for (Blk b : l) {
            if (b.t.equals("press") || b.t.equals("hold")) return true;
            if ((b.t.equals("if") || b.t.equals("elif") || b.t.equals("while")) && !b.s("touch").isEmpty()) return true;
            if (wantsTouch(b.body) || wantsTouch(b.els)) return true;
        }
        return false;
    }

    boolean scanSwipe(ArrayList<Blk> l) {
        for (Blk b : l) {
            if ("swipe".equals(b.s("touch"))) return true;
            if (scanSwipe(b.body) || scanSwipe(b.els)) return true;
        }
        return false;
    }

    // Dedo que se desliza por encima de un nodo (condición "Pasar dedo")
    public void move(int pid, float vx, float vy) {
        if (pid >= 0 && pid < 16) {
            ptx[pid] = vx;
            pty[pid] = vy;
            pton[pid] = true;
        }
        for (int i = grabs.size() - 1; i >= 0; i--) if (grabs.get(i).pid == pid) grabMove(grabs.get(i), vx, vy);
        if (swipeState < 0) {
            swipeState = 0;
            for (Node n : nodes) if (scanSwipe(n.script)) swipeState = 1;
        }
        if (swipeState == 0) return;
        Node h = hit(vx, vy, true);
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (n.swPid == pid && n != h) {
                n.swPid = -1;
                n.swiped = false;
            }
        }
        if (h != null && h.swPid != pid) {
            h.swPid = pid;
            h.swiped = true;
        }
    }

    public float toVx(float px) {
        return (px - ox) / sx;
    }

    public float toVy(float py) {
        return (py - oy) / sy;
    }

    Node grabTarget(Node g) {
        Node t = g.gtarget.isEmpty() ? g.par : byName(g.gtarget);
        return t != null ? t : g;
    }

    float grabAngle(Node g, Node t) {
        Node an = g.anchor.isEmpty() ? null : byName(g.anchor);
        float px = an != null ? an.wx + an.w / 2f : t.wx + t.w / 2f;
        float py = an != null ? an.wy + an.h / 2f : t.wy + t.h / 2f;
        return (float) Math.toDegrees(Math.atan2(g.gy - py, g.gx - px));
    }

    void grabStart(Node g, float vx, float vy) {
        Node t = grabTarget(g);
        g.gx = useCam && !g.fixed ? (vx - vw / 2f) / camZ + camX : vx;
        g.gy = useCam && !g.fixed ? (vy - vh / 2f) / camZ + camY : vy;
        g.gacc = 0f;
        g.grot = t.rot;
        g.ga = grabAngle(g, t);
        if (!grabs.contains(g)) grabs.add(g);
    }

    // arrastrar: el objeto sigue al dedo. rotar: gira alrededor del ancla según hacia dónde jalas
    void grabMove(Node g, float vx, float vy) {
        Node t = grabTarget(g);
        float cx = useCam && !g.fixed ? (vx - vw / 2f) / camZ + camX : vx;
        float cy = useCam && !g.fixed ? (vy - vh / 2f) / camZ + camY : vy;
        float dx = cx - g.gx, dy = cy - g.gy;
        g.gx = cx;
        g.gy = cy;
        if (g.gmode.equals("rotar")) {
            float a = grabAngle(g, t), d = a - g.ga;
            if (d > 180f) d -= 360f;
            else if (d < -180f) d += 360f;
            g.ga = a;
            g.gacc += d;
            float r = g.grot + g.gacc;
            float lo = g.limL >= 0f ? t.rot0 - g.limL : -1e9f, hi = g.limR >= 0f ? t.rot0 + g.limR : 1e9f;
            if (r < lo) {
                r = lo;
                g.gacc = lo - g.grot;
            } else if (r > hi) {
                r = hi;
                g.gacc = hi - g.grot;
            }
            t.rot = r;
        } else {
            t.x += dx;
            t.y += dy;
        }
        constrain(t);
    }

    public Node hit(float vx, float vy, boolean onlyTouchable) {
        float cx = useCam ? (vx - vw / 2f) / camZ + camX : vx;
        float cy = useCam ? (vy - vh / 2f) / camZ + camY : vy;
        for (int i = order.size() - 1; i >= 0; i--) {
            Node n = order.get(i);
            if (!n.wvis) continue;
            if (edit && n.type.equals("Sound")) continue;
            if (onlyTouchable && ((n.wa <= 0.003f && !n.touchAlways) || !touchable(n))) continue;
            float px = (useCam && !n.fixed) ? cx : vx;
            float py = (useCam && !n.fixed) ? cy : vy;
            if (inNode(n, px, py)) {
                if (edit && !assetOrders.isEmpty()) {
                    editBounds(n, n.wx, n.wy, editRect);
                    if (!editRect.contains(mx, my)) continue;
                }
                if (onlyTouchable && n.smart && !maskHit(n, mx, my)) continue;
                return n;
            }
        }
        return null;
    }

    public void down(int pid, float vx, float vy) {
        if (pid >= 0 && pid < 16) {
            ptx[pid] = vx;
            pty[pid] = vy;
            pton[pid] = true;
        }
        Node n = hit(vx, vy, true);
        if (n == null) return;
        n.pressed = true;
        n.pid = pid;
        if (n.type.equals("Grab")) grabStart(n, vx, vy);
        for (int i = 0; i < n.script.size(); i++) {
            Blk b = n.script.get(i);
            if (b.t.equals("press")) {
                spawn(b.body, n);
            } else if (b.t.equals("hold")) {
                Hold h = new Hold();
                h.n = n;
                h.b = b;
                h.pid = pid;
                holds.add(h);
            }
        }
    }

    public void up(int pid) {
        if (pid >= 0 && pid < 16) pton[pid] = false;
        for (Node n : nodes) {
            if (n.pid == pid) {
                n.pid = -1;
                n.pressed = false;
                grabs.remove(n);
            }
            if (n.swPid == pid) {
                n.swPid = -1;
                n.swiped = false;
            }
        }
    }

    public void upAll() {
        for (int i = 0; i < 16; i++) pton[i] = false;
        grabs.clear();
        for (Node n : nodes) {
            n.pid = -1;
            n.pressed = false;
            n.swPid = -1;
            n.swiped = false;
        }
    }

    // ================= Toque inteligente (máscara de bits) =================
    String maskImage(Node n) {
        if (n.type.equals("AnimatedSprite")) return n.frames.isEmpty() ? "" : n.frames.get(Math.min(n.frame, n.frames.size() - 1));
        return n.res;
    }

    Mask mask(String name, int res, boolean maj) {
        String key = name + "|" + res + "|" + maj;
        if (masks.containsKey(key)) return masks.get(key);
        Bitmap bm = img(name);
        Mask m = null;
        if (bm != null) {
            int bw = bm.getWidth(), bh = bm.getHeight();
            m = new Mask();
            m.w = bw >= bh ? res : Math.max(1, Math.round(res * (float) bw / bh));
            m.h = bh >= bw ? res : Math.max(1, Math.round(res * (float) bh / bw));
            m.b = new boolean[m.w * m.h];
            boolean alpha = bm.hasAlpha();
            for (int cy = 0; cy < m.h; cy++) {
                for (int cx = 0; cx < m.w; cx++) {
                    int x0 = cx * bw / m.w, x1 = Math.max(x0 + 1, (cx + 1) * bw / m.w);
                    int y0 = cy * bh / m.h, y1 = Math.max(y0 + 1, (cy + 1) * bh / m.h);
                    int stx = Math.max(1, (x1 - x0) / 6), sty = Math.max(1, (y1 - y0) / 6);
                    int tot = 0, on = 0;
                    for (int y = y0; y < y1; y += sty) {
                        for (int x = x0; x < x1; x += stx) {
                            tot++;
                            if (!alpha || (bm.getPixel(Math.min(x, bw - 1), Math.min(y, bh - 1)) >>> 24) > 25) on++;
                        }
                    }
                    m.b[cy * m.w + cx] = maj ? on * 2 >= tot : on > 0;
                }
            }
        }
        masks.put(key, m);
        return m;
    }

    boolean maskHit(Node n, float px, float py) {
        String name = maskImage(n);
        if (name.isEmpty()) return true;
        Mask m = mask(name, n.smartRes, n.smartMaj);
        if (m == null) return true;
        imageBounds(n, name, n.wx, n.wy, maskRect);
        if (!maskRect.contains(px, py)) return false;
        int cx = (int) ((px - maskRect.left) / maskRect.width() * m.w);
        int cy = (int) ((py - maskRect.top) / maskRect.height() * m.h);
        cx = Math.max(0, Math.min(m.w - 1, cx));
        cy = Math.max(0, Math.min(m.h - 1, cy));
        return m.b[cy * m.w + cx];
    }

    // 8 manijas redondas: 4 esquinas y 4 lados (0 = arriba izq., sentido horario)
    static final int[] HC = {0, 1, 2, 2, 2, 1, 0, 0}, HR = {0, 0, 0, 1, 2, 2, 2, 1};

    float hx(RectF box, int i) {
        return box.left + box.width() * HC[i] / 2f;
    }

    float hy(RectF box, int i) {
        return box.top + box.height() * HR[i] / 2f;
    }

    void drawHandles(Canvas c, RectF box) {
        float k = 1f / Math.max(0.1f, sx);
        for (int i = 0; i < 8; i++) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(hx(box, i), hy(box, i), 11f * k, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f * k);
            p.setColor(0xFF00AAFF);
            c.drawCircle(hx(box, i), hy(box, i), 11f * k, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    public int handleAt(Node n, float vx, float vy, float tol) {
        if (n.type.equals("Sound")) return -1;
        editBounds(n, n.wx, n.wy, editRect);
        if (editRect.isEmpty()) return -1;
        if (n.rotd) {
            if (!n.tm.invert(hm)) return -1;
            fp[0] = vx;
            fp[1] = vy;
            hm.mapPoints(fp);
            vx = fp[0];
            vy = fp[1];
        }
        int best = -1;
        float bd = tol * tol;
        for (int i = 0; i < 8; i++) {
            float dx = vx - hx(editRect, i), dy = vy - hy(editRect, i);
            float d = dx * dx + dy * dy;
            if (d <= bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    public void resizeBy(Node n, int h, float dx, float dy) {
        editBounds(n, n.x, n.y, editRect);
        if (editRect.isEmpty()) return;
        if (n.rotd) {
            if (!n.tm.invert(hm)) return;
            fp[0] = dx;
            fp[1] = dy;
            hm.mapVectors(fp);
            dx = fp[0];
            dy = fp[1];
        }
        int col = HC[h], row = HR[h];
        float left = col == 0 ? dx : 0f, right = col == 2 ? dx : 0f;
        float top = row == 0 ? dy : 0f, bottom = row == 2 ? dy : 0f;
        float min = assetOrders.containsKey(visualImage(n)) ? 1f : 4f;
        if (editRect.width() + right - left < Math.min(min, editRect.width())) left = right = 0f;
        if (editRect.height() + bottom - top < Math.min(min, editRect.height())) top = bottom = 0f;
        if (left == 0f && right == 0f && top == 0f && bottom == 0f) return;

        float x = n.x, y = n.y, w = n.w, height = n.h;
        resizeEditEdges(n, left, top, right, bottom);
        if (n.rot != 0f) {
            Node anchor = n.anchor.isEmpty() ? null : byName(n.anchor);
            if (anchor == null || inheritsPositionFrom(anchor, n)) {
                // Compensa el desplazamiento del pivote para que el lado opuesto quede fijo.
                float px = n.x - x, py = n.y - y;
                if (anchor == null || anchor == n) {
                    px += (n.w - w) / 2f;
                    py += (n.h - height) / 2f;
                }
                double a = Math.toRadians(n.rot);
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                n.x += (c - 1f) * px - s * py;
                n.y += s * px + (c - 1f) * py;
            }
        }
        n.fit = false;
    }

    Matrix inheritedMatrix(Node n) {
        return n.inherit && n.par != null && n.par.rotd ? n.par.tm : null;
    }

    void editPivot(Node n, float[] out) {
        Node anchor = n.anchor.isEmpty() ? null : byName(n.anchor);
        if (anchor == null) anchor = n;
        out[0] = origin(anchor, true) + anchor.w / 2f;
        out[1] = origin(anchor, false) + anchor.h / 2f;
        Matrix parent = inheritedMatrix(n);
        if (parent != null) parent.mapPoints(out);
    }

    void dragEdit(Node n, float x0, float y0, float x1, float y1) {
        if (editMode == EDIT_ROTATE) {
            editPivot(n, fp);
            float ax = x0 - fp[0], ay = y0 - fp[1], bx = x1 - fp[0], by = y1 - fp[1];
            if (ax * ax + ay * ay < 1f || bx * bx + by * by < 1f) return;
            n.rot += (float) Math.toDegrees(Math.atan2(ax * by - ay * bx, ax * bx + ay * by));
        } else if (editMode == EDIT_MOVE) {
            Matrix transform = inheritedMatrix(n);
            Node anchor = n.anchor.isEmpty() ? null : byName(n.anchor);
            if (n.rotd && anchor != null && !inheritsPositionFrom(anchor, n)) transform = n.tm;
            fp[0] = x1 - x0;
            fp[1] = y1 - y0;
            if (transform != null && transform.invert(hm)) hm.mapVectors(fp);
            n.x += fp[0];
            n.y += fp[1];
        }
    }

    void drawMask(Canvas c, Node n) {
        String name = maskImage(n);
        if (name.isEmpty()) return;
        Mask m = mask(name, n.smartRes, n.smartMaj);
        if (m == null) return;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x6600FF00);
        imageBounds(n, name, n.wx, n.wy, maskRect);
        float cw = maskRect.width() / m.w, ch = maskRect.height() / m.h;
        for (int cy = 0; cy < m.h; cy++) {
            for (int cx = 0; cx < m.w; cx++) {
                if (m.b[cy * m.w + cx]) c.drawRect(maskRect.left + cx * cw, maskRect.top + cy * ch,
                        maskRect.left + (cx + 1) * cw, maskRect.top + (cy + 1) * ch, p);
            }
        }
    }

    // ================= Texto y fuentes =================
    Typeface fontFor(String font, boolean bold, boolean italic) {
        String key = font + "|" + bold + italic;
        Typeface t = tfCache.get(key);
        if (t != null) return t;
        Typeface base = Typeface.DEFAULT;
        if (!font.isEmpty()) {
            try {
                base = Typeface.createFromFile(src.file(font));
            } catch (Exception e) {
                base = Typeface.DEFAULT;
            }
        }
        int st = (bold ? Typeface.BOLD : 0) | (italic ? Typeface.ITALIC : 0);
        t = st == 0 ? base : Typeface.create(base, st);
        tfCache.put(key, t);
        return t;
    }

    // Reemplaza {variable} por su valor
    String labelText(Node n) {
        String t = n.text;
        if (n.literalText) return t;
        if (edit || t.indexOf('{') < 0) return t;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < t.length()) {
            char ch = t.charAt(i);
            if (ch == '{') {
                int j = t.indexOf('}', i);
                if (j > i) {
                    String nm = t.substring(i + 1, j).trim();
                    Object v = getVar(nm, n);
                    if (v == null && nm.indexOf('.') > 0) {
                        Object o = val(nm, n);
                        if (!nm.equals(String.valueOf(o))) v = o;
                    }
                    sb.append(v != null ? String.valueOf(v) : "{" + nm + "}");
                    i = j + 1;
                    continue;
                }
            }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }

    void drawLabel(Canvas c, Node n, int a) {
        String txt = labelText(n);
        int w = Math.max(1, Math.round(n.w));
        String key = txt + "|" + w + "|" + n.tsize + "|" + n.font + "|" + n.bold + n.italic + "|" + n.align + "|"
                + n.color + "|" + n.outline + "|" + n.ocolor + "|" + n.shadow + "|" + n.wrap;
        if (n.lay == null || !key.equals(n.layKey)) {
            n.layKey = key;
            TextPaint pf = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            pf.setTextSize(n.tsize);
            pf.setTypeface(fontFor(n.font, n.bold, n.italic));
            pf.setColor(n.color);
            if (n.shadow) pf.setShadowLayer(Math.max(2f, n.tsize / 12f), n.tsize / 14f, n.tsize / 14f, 0xAA000000);
            Layout.Alignment al = n.align == 0 ? Layout.Alignment.ALIGN_NORMAL
                    : n.align == 2 ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_CENTER;
            int lw = w;
            if (!n.wrap) lw = Math.max(1, (int) Math.ceil(Layout.getDesiredWidth(txt, pf)));
            n.layW = lw;
            n.tpF = pf;
            n.lay = new StaticLayout(txt, pf, lw, al, 1f, 0f, false);
            if (n.outline > 0f) {
                TextPaint po = new TextPaint(pf);
                po.setStyle(Paint.Style.STROKE);
                po.setStrokeWidth(n.outline);
                po.setStrokeJoin(Paint.Join.ROUND);
                po.setColor(n.ocolor);
                po.clearShadowLayer();
                n.tpO = po;
                n.layO = new StaticLayout(txt, po, lw, al, 1f, 0f, false);
            } else {
                n.tpO = null;
                n.layO = null;
            }
        }
        float th = n.lay.getHeight();
        float oy = n.valign == 1 ? (n.h - th) / 2f : n.valign == 2 ? n.h - th : 0f;
        float ox = 0f;
        if (!n.wrap) ox = n.align == 1 ? (n.w - n.layW) / 2f : n.align == 2 ? n.w - n.layW : 0f;
        c.save();
        c.translate(n.wx + ox, n.wy + oy);
        if (n.layO != null) {
            n.tpO.setAlpha(a);
            n.layO.draw(c);
        }
        n.tpF.setAlpha(a);
        n.lay.draw(c);
        c.restore();
    }

    // ================= Estado de la última partida (para elegir posiciones) =================
    public String stateJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("scene", curScene.name);
        JSONArray a = new JSONArray();
        for (Node n : nodes) {
            JSONObject j = new JSONObject();
            j.put("id", n.id);
            j.put("x", n.x);
            j.put("y", n.y);
            j.put("w", n.w);
            j.put("h", n.h);
            j.put("a", n.alpha);
            j.put("vis", n.visible);
            j.put("res", n.res);
            j.put("text", n.text);
            j.put("frame", n.frame);
            a.put(j);
        }
        o.put("nodes", a);
        return o.toString();
    }

    public void applyState(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        if (!o.optString("scene", "").equals(curScene.name)) return;
        JSONArray a = o.optJSONArray("nodes");
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.getJSONObject(i);
            Node n = byId(j.getInt("id"));
            if (n == null) continue;
            n.x = (float) j.optDouble("x", n.x);
            n.y = (float) j.optDouble("y", n.y);
            n.w = (float) j.optDouble("w", n.w);
            n.h = (float) j.optDouble("h", n.h);
            n.alpha = (float) j.optDouble("a", n.alpha);
            n.visible = j.optBoolean("vis", n.visible);
            n.res = j.optString("res", n.res);
            n.text = j.optString("text", n.text);
            n.frame = j.optInt("frame", n.frame);
        }
    }

    // ================= Dibujo =================
    boolean editorWorkspace() {
        return edit && !snap;
    }

    void setEditorZoom(float zoom) {
        float next = Math.max(0.125f, Math.min(8f, zoom));
        editorPanX *= next / editorZoom;
        editorPanY *= next / editorZoom;
        editorZoom = next;
    }

    void computeMap(int sw, int sh) {
        String m = edit ? "keep" : stretch;
        float ax = sw / (float) vw, ay = sh / (float) vh;
        if (m.equals("stretch")) {
            sx = ax;
            sy = ay;
        } else {
            float s = m.equals("fill") ? Math.max(ax, ay) : Math.min(ax, ay);
            sx = s;
            sy = s;
        }
        boolean workspace = editorWorkspace();
        if (workspace) {
            sx *= editorZoom;
            sy *= editorZoom;
        }
        ox = (sw - vw * sx) / 2f + (workspace ? editorPanX : 0f);
        oy = (sh - vh * sy) / 2f + (workspace ? editorPanY : 0f);
        drawLeft = workspace ? -ox / sx : 0f;
        drawTop = workspace ? -oy / sy : 0f;
        drawRight = workspace ? (sw - ox) / sx : vw;
        drawBottom = workspace ? (sh - oy) / sy : vh;
    }

    public void draw(Canvas c, int sw, int sh) {
        computeMap(sw, sh);
        boolean workspace = editorWorkspace();
        c.drawColor(workspace ? 0xFF272B33 : 0xFF000000);
        c.save();
        c.translate(ox, oy);
        c.scale(sx, sy);
        if (workspace) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(bg);
            c.drawRect(0, 0, vw, vh, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f / Math.max(0.01f, sx));
            p.setColor(0xFF798391);
            c.drawRect(0, 0, vw, vh, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            c.clipRect(0, 0, vw, vh);
            c.drawColor(bg);
        }
        compute();
        boolean uc = useCam && !snap;
        if (uc) {
            camM.reset();
            camM.postTranslate(-camX, -camY);
            camM.postScale(camZ, camZ);
            camM.postTranslate(vw / 2f, vh / 2f);
        }
        for (int i = 0; i < order.size(); i++) {
            Node n = order.get(i);
            if (uc && !n.fixed) {
                c.save();
                c.concat(camM);
                drawWithCopies(c, n, false);
                c.restore();
            } else {
                drawWithCopies(c, n, true);
            }
        }
        if (edit && sel != null && sel.inOrder) {
            editBounds(sel, sel.wx, sel.wy, editRect);
            if (!editRect.isEmpty()) {
                c.save();
                if (sel.rotd) c.concat(sel.tm);
                if (sel.smart) {
                    c.save();
                    c.clipRect(editRect);
                    drawMask(c, sel);
                    c.restore();
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f / Math.max(0.1f, sx));
                p.setColor(0xFFFF00FF);
                c.drawRect(editRect, p);
                p.setStyle(Paint.Style.FILL);
                if (editMode == EDIT_SIZE && !sel.type.equals("Sound")) drawHandles(c, editRect);
                c.restore();
                if (editMode == EDIT_ROTATE) {
                    editPivot(sel, fp);
                    c.drawCircle(fp[0], fp[1], 7f / Math.max(0.1f, sx), p);
                }
            }
        }
        c.restore();
    }

    // Imagen de la escena en coordenadas del viewport (sin cámara), para elegir posiciones visualmente
    public Bitmap snapshot(int maxW) {
        float k = Math.min(1f, maxW / (float) vw);
        int w = Math.max(1, Math.round(vw * k)), h = Math.max(1, Math.round(vh * k));
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Node s0 = sel;
        String st = stretch;
        float lastX = ox, lastY = oy, lastSx = sx, lastSy = sy;
        sel = null;
        stretch = "keep";
        snap = true;
        try {
            draw(c, w, h);
        } finally {
            snap = false;
            stretch = st;
            sel = s0;
            ox = lastX; oy = lastY; sx = lastSx; sy = lastSy;
        }
        return bm;
    }

    void placeholder(Canvas c, Node n, int a) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0xFF888888);
        p.setAlpha(a);
        c.drawRect(rf, p);
        p.setStyle(Paint.Style.FILL);
        tp.setColor(0xFFAAAAAA);
        tp.setAlpha(a);
        tp.setTextSize(Math.max(14f, Math.min(n.h * 0.3f, 36f)));
        tp.setTextAlign(Paint.Align.CENTER);
        String label = n.name;
        if (n.type.equals("Group")) label = "▣ " + n.name;
        c.drawText(label, rf.centerX(), rf.centerY(), tp);
    }

    void drawWithCopies(Canvas canvas, Node n, boolean cull) {
        drawNode(canvas, n, cull);
        if (n.visualCopies == null) return;
        for (int i = 0; i < n.visualCopies.size(); i++) {
            VisualCopy copy = n.visualCopies.get(i);
            Node picture = copy.picture == null ? n : copy.picture;
            if (!picture.wvis || picture.wa <= 0.003f) continue;
            if (cull && !picture.rotd && (picture.wx + copy.dx > vw || picture.wy + copy.dy > vh
                    || picture.wx + copy.dx + picture.w < 0 || picture.wy + copy.dy + picture.h < 0)) continue;
            canvas.save();
            canvas.translate(copy.dx, copy.dy);
            drawNode(canvas, picture, false);
            canvas.restore();
        }
    }

    void drawNode(Canvas c, Node n, boolean cull) {
        if (n.rotd && n.wvis && n.wa > 0.003f) {
            c.save();
            c.concat(n.tm);
            drawBody(c, n, false);
            c.restore();
        } else {
            drawBody(c, n, cull);
        }
    }

    void drawBody(Canvas c, Node n, boolean cull) {
        if (!n.wvis || n.wa <= 0.003f) return;
        if (cull && (n.wx > drawRight || n.wy > drawBottom || n.wx + n.w < drawLeft || n.wy + n.h < drawTop)) return;
        int a = Math.max(0, Math.min(255, (int) (n.wa * 255)));
        rf.set(n.wx, n.wy, n.wx + n.w, n.wy + n.h);
        p.setStyle(Paint.Style.FILL);
        switch (n.type) {
            case "Image":
            case "ImageButton": {
                Bitmap b = img(n.res);
                int aa = (n.type.equals("ImageButton") && n.pressed && n.dim) ? a * 7 / 10 : a;
                if (b != null) {
                    p.setAlpha(aa);
                    if (!assetOrders.isEmpty()) imageBounds(n, n.res, n.wx, n.wy, rf);
                    c.drawBitmap(b, null, rf, p);
                } else if (edit) {
                    placeholder(c, n, a);
                }
                break;
            }
            case "AnimatedSprite": {
                String asset = visualImage(n);
                Bitmap b = img(asset);
                if (b != null) {
                    p.setAlpha(a);
                    if (!assetOrders.isEmpty()) imageBounds(n, asset, n.wx, n.wy, rf);
                    c.drawBitmap(b, null, rf, p);
                } else if (edit) {
                    placeholder(c, n, a);
                }
                break;
            }
            case "Button": {
                p.setColor(n.pressed ? 0xFF3A3A3A : 0xFF5A5A5A);
                p.setAlpha(a);
                float r = Math.min(n.w, n.h) * 0.2f;
                c.drawRoundRect(rf, r, r, p);
                tp.setColor(0xFFFFFFFF);
                tp.setAlpha(a);
                tp.setTextSize(n.tsize);
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setTypeface(fontFor(n.font, n.bold, n.italic));
                c.drawText(n.text, rf.centerX(), rf.centerY() - (tp.ascent() + tp.descent()) / 2f, tp);
                break;
            }
            case "Label":
                drawLabel(c, n, a);
                break;
            case "Video":
                if (edit && !snap) {
                    Bitmap tb = vthumb(n.res);
                    if (tb != null) {
                        p.setAlpha(a);
                        c.drawBitmap(tb, null, rf, p);
                    } else {
                        placeholder(c, n, a);
                    }
                }
                break;
            case "Color":
                p.setColor(n.color);
                p.setAlpha(((n.color >>> 24) * a) / 255);
                c.drawRect(rf, p);
                break;
            case "Anchor":
                if (edit && !snap) {
                    float ax = rf.centerX(), ay = rf.centerY(), ar = Math.min(n.w, n.h) / 2f;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(3f);
                    p.setColor(0xFFFFAA00);
                    c.drawCircle(ax, ay, ar * 0.6f, p);
                    c.drawLine(ax - ar, ay, ax + ar, ay, p);
                    c.drawLine(ax, ay - ar, ax, ay + ar, p);
                    p.setStyle(Paint.Style.FILL);
                }
                break;
            case "Limiter":
            case "Collision":
            case "Grab":
                if (edit && !snap) placeholder(c, n, a);
                break;
            case "Sound":
                break;
            case "Group":
                if (edit && !snap) placeholder(c, n, a);
                break;
            case "Camera2D":
                if (edit && !snap) {
                    float z = Math.max(0.05f, n.zoom);
                    float cx = rf.centerX(), cy = rf.centerY();
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f);
                    p.setColor(0xFF00CCFF);
                    p.setAlpha(a);
                    c.drawRect(cx - vw / (2f * z), cy - vh / (2f * z), cx + vw / (2f * z), cy + vh / (2f * z), p);
                    p.setStyle(Paint.Style.FILL);
                    placeholder(c, n, a);
                }
                break;
            default:
                break;
        }
    }
}

package com.mini.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class EditorActivity extends Activity {
    interface Str {
        void set(String s);
    }

    interface Pick {
        void got(String s);
    }

    interface Xy {
        void got(float x, float y);
    }

    static final int NUM = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
            | InputType.TYPE_NUMBER_FLAG_SIGNED;
    static final int TXT = InputType.TYPE_CLASS_TEXT;
    static final int MP = ViewGroup.LayoutParams.MATCH_PARENT;
    static final String[] PROPS = {"x", "y", "w", "h", "modulate", "visible", "text", "fps", "frame", "zoom", "rot"};
    static final String[] BASE_TYPES = {"int", "float", "bool", "string"};

    // tipo, etiqueta, E (evento) / S (bloque), campos "clave:etiqueta:tipo[:opciones]"
    // tipos: T texto, N número, V valor (con selector), R variable (crear o elegir), Q variable existente,
    //        M mensaje, B sí/no, G nodo, I imagen, A audio, C escena, Y tipo de variable,
    //        L lista de variables, E lista de valores de enum, O opciones, H destino de colisión
    static final String[][] SPEC = {
        {"start", "Al iniciar", "E"},
        {"press", "Al presionar", "E"},
        {"hold", "Al mantener presionado", "E"},
        {"recv", "Recibir mensaje", "E", "msg:Mensaje:M"},
        {"frame", "Cada fotograma", "E"},
        {"collision", "Al colisionar", "E", "node:Nodo con el que colisiona:H"},
        {"collisionExit", "Al dejar de colisionar", "E", "node:Nodo con el que colisiona:H"},
        {"collisionStay", "Mientras colisiona", "E", "node:Nodo con el que colisiona:H"},
        {"every", "Cada X segundos", "E", "secs:Segundos:N"},
        {"change", "Al cambiar una propiedad", "E", "node:Nodo:G", "prop:Propiedad:O:visible|modulate|x|y|w|h|text|rot"},
        {"orient", "Al cambiar de orientación", "E"},
        {"func", "Función (evento)", "E", "name:Nombre de la función:T", "params:Parámetros (opcional, separados por coma):T",
            "returnMode:Tipo de retorno:O:" + Game.FN_VOID + "|" + Game.FN_VALUE + "|" + Game.FN_COMPAT},
        {"send", "Transmitir mensaje", "S", "msg:Mensaje:M", "scope:Alcance:O:escena|nodo|jerarquía|global"},
        {"call", "Invocar función", "S", "name:Función:F", "args:Valores (separados por coma):T",
            "par:Ejecutar en paralelo (sin esperar a que termine):B", "ret:Guardar lo que devuelva en la variable:Q"},
        {"callNode", "Invocar función de nodo", "S", "node:Nodo que tiene la función:G", "name:Función:F",
            "args:Valores (separados por coma):T", "par:Ejecutar en paralelo (sin esperar a que termine):B",
            "ret:Guardar lo que devuelva en la variable:Q"},
        {"funcExists", "Comprobar función de nodo", "S", "node:Nodo que tiene la función:G", "name:Función:F",
            "ret:Guardar verdadero o falso en la variable:Q"},
        {"return", "Devolver valor", "S", "val:Valor a devolver:V"},
        {"exitFunction", "Salir de la función (sin devolver)", "S"},
        {"if", "Si", "S", "tm:Contar el tiempo de por(X):O:esperar|reloj propio", "expr:Condición:T"},
        {"elif", "Sino, Si", "S", "tm:Contar el tiempo de por(X):O:esperar|reloj propio", "expr:Condición:T"},
        {"else", "Sino", "S"},
        {"while", "Repetir mientras", "S", "expr:Condición:T"},
        {"repeat", "Repetir (veces)", "S", "n:Veces:V"},
        {"forever", "Repetir siempre", "S"},
        {"break", "Romper (salir del bucle)", "S"},
        {"breakrep", "Romper el bloque Repetir (veces)", "S"},
        {"breakloop", "Romper loop (animación o video)", "S", "node:Nodo (AnimatedSprite o Video):G"},
        {"wait", "Esperar (segundos)", "S", "secs:Segundos:N"},
        {"var", "Variable (crear)", "S", "name:Nombre:R", "vtype:Tipo:Y",
            "scope:Alcance:O:global|escena|nodo|local", "val:Valor inicial:V"},
        {"setvar", "Cambiar valor de variable", "S", "name:Variable:Q", "val:Nuevo valor:V"},
        {"math", "Operar variable", "S", "name:Variable:Q", "op:Operación:O:+|-|*|/", "val:Valor:V"},
        {"enum", "Enum (definir)", "S", "name:Nombre del enum:T", "values:Valores:E"},
        {"enumnext", "Fase de Enum (subir, bajar, ir a…)", "S", "enum:Enum (seleccionar):Z", "name:Variable enum:Q",
            "dir:Dirección:O:siguiente|anterior|primero|último|aleatorio|ir a", "to:Fase destino (solo para 'ir a'):V",
            "wrap:Volver al inicio al llegar al final:B"},
        {"match", "Coincidir (según un valor)", "S", "val:Valor a comparar (variable, enum…):V"},
        {"case", "Caso (dentro de Coincidir)", "S", "val:Valor o valores (separados por coma):V"},
        {"default", "Por defecto (dentro de Coincidir)", "S"},
        {"layer", "Capa del nodo (subir / bajar)", "S", "node:Nodo:G", "mode:Acción:O:subir|bajar|al frente|al fondo"},
        {"save", "Guardar variables", "S", "names:Variables a guardar:L"},
        {"clearsave", "Borrar progreso guardado", "S"},
        {"scene", "Cambiar de escena", "S", "scene:Escena:C"},
        {"orientset", "Cambiar orientación", "S", "mode:Orientación:O:horizontal|vertical"},
        {"viewmode", "Usar viewport o cámara", "S", "mode:Modo:O:viewport|camera2D"},
        {"camset", "Configurar cámara", "S", "node:Cámara (Camera2D):G",
            "what:Qué cambia:O:zoom|seguir a|suavizado|activar", "val:Valor (nombre del nodo si es seguir a):V"},
        {"setimg", "Cambiar imagen", "S", "node:Nodo:G", "asset:Imagen:I"},
        {"setsnd", "Cambiar sonido", "S", "node:Nodo (Sound):G", "asset:Sonido:A"},
        {"play", "Reproducir sonido, video o animación", "S", "node:Nodo:G:Sound|AnimatedSprite|Video",
            "dir:Dirección de video o animación:O:normal|reversa", "restart:Reiniciar desde el extremo:B",
            "loop:Bucle:O:del nodo|activar|desactivar"},
        {"stop", "Detener sonido, video o animación", "S", "node:Nodo:G:Sound|AnimatedSprite|Video"},
        {"quit", "Cerrar juego (se cierra por completo)", "S"},
        {"mod", "Modulate (alpha 0.0 a 1.0)", "S", "node:Nodo:G", "val:Valor decimal:N",
                "touch:Permitir tocar aunque la transparencia sea máxima (alpha 0):B"},
        {"vis", "Visibilidad", "S", "node:Nodo:G", "val:Visible:O:true|false"},
        {"pos", "Mover a (vacío = no cambia)", "S", "node:Nodo:G", "x:X:V", "y:Y:V"},
        {"clone", "Clonar nodo", "S", "node:Nodo a clonar:G", "name:Nombre de la copia (opcional):T",
            "x:X (vacío = igual al original):V", "y:Y (vacío = igual al original):V"},
        {"visualClone", "Clonar visualización de nodo", "S", "node:Nodo visual:G:Image|Button|ImageButton|Label|Color|AnimatedSprite",
            "x:Desplazamiento X:V", "y:Desplazamiento Y:V", "share:Compartir cambios visuales del original:B"},
        {"clearVisualClones", "Quitar copias visuales", "S", "node:Nodo original:G:Image|Button|ImageButton|Label|Color|AnimatedSprite"},
        {"advance", "Avanzar píxeles", "S", "node:Nodo:G", "dir:Dirección:O:derecha|izquierda|arriba|abajo|ángulo",
            "ang:Ángulo en grados (solo 'ángulo': 0 = derecha, 90 = abajo):V", "dist:Píxeles a avanzar:V",
            "secs:Segundos (0 = al instante):V", "edge:No salir de los bordes:B",
            "anti:Antirebote (al llegar al borde se detiene del todo):B"},
        {"rotate", "Rotar", "S", "node:Nodo:G", "mode:Modo:O:sumar|ir a", "deg:Grados (positivo = horario):V",
            "secs:Segundos (0 = al instante):V"},
        {"stoprender", "Dejar de renderizar", "S", "node:Nodo:G"},
        {"startrender", "Renderizar de nuevo", "S", "node:Nodo:G"},
        {"collisionEnable", "Activar o desactivar colisión", "S", "node:Nodo (Collision):G:Collision", "enabled:Colisión:O:true|false"},
        {"speed", "Velocidad (inicial → final)", "S", "node:Nodo:G", "dir:Dirección:O:derecha|izquierda|arriba|abajo|ángulo",
            "ang:Ángulo en grados (solo 'ángulo'):V", "v0:Velocidad inicial (píxeles por segundo):V",
            "v1:Velocidad final (píxeles por segundo):V", "ramp:Segundos para pasar de la inicial a la final:V",
            "secs:Duración máxima en segundos (0 = mientras se cumpla la condición):V",
            "edge:No salir de los bordes:B", "anti:Antirebote (al llegar al borde se detiene del todo):B",
            "inf:Tiempo ilimitado (sigue hasta que la condición deje de cumplirse):B", "expr:Mientras:T"},
        {"text", "Cambiar texto", "S", "node:Nodo (Button):G", "val:Texto:V"},
        {"tween", "Cambio progresivo", "S", "node:Nodo:G", "what:Qué cambia:O:modulate|mover|tamaño|rotación",
            "x:Valor, X o ángulo final en grados:V", "y:Y (mover o tamaño):V", "secs:Segundos:N",
            "ease:Curva de cambio:O:lineal|entrada/salida|salida suave", "touch:Permitir tocar aunque la transparencia sea máxima (solo modulate):B"},
        {"vend", "Al terminar el video", "E"},
        {"vpause", "Pausar video", "S", "node:Nodo (Video):G:Video"},
        {"vseek", "Ir a un segundo del video", "S", "node:Nodo (Video):G:Video", "secs:Segundos:V"},
        {"vvol", "Volumen del video (0.0 a 1.0)", "S", "node:Nodo (Video):G:Video", "val:Volumen:V"},
        {"setvideo", "Cambiar video", "S", "node:Nodo (Video):G:Video", "asset:Video (mp4, webm, mkv):W"},
    };

    Game g;
    GameView pv;
    String pid;
    File dir, adir, jf, iconFile;
    Game.Node sel, scriptOwner;
    static final int IMPORT_FONT = 4, IMPORT_ORDERS = 5;
    boolean importingOrders;
    int fontNodeId = -1;
    LinearLayout list, props;
    TextView sceneLabel, tIconInfo;
    File exported;
    String exportName = "juego";

    // ================= Utilidades de interfaz =================
    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    TextView label(String t) {
        TextView x = new TextView(this);
        x.setText(t);
        x.setTextSize(13);
        x.setPadding(0, dp(10), 0, 0);
        return x;
    }

    TextView title(String t) {
        TextView x = new TextView(this);
        x.setText(t);
        x.setTextSize(17);
        x.setPadding(0, dp(16), 0, dp(4));
        x.setTypeface(null, android.graphics.Typeface.BOLD);
        return x;
    }

    Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setOnClickListener(l);
        return b;
    }

    LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        return s;
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    EditText field(LinearLayout p, String lab, String val, int type, final Str cb) {
        p.addView(label(lab));
        EditText e = new EditText(this);
        e.setInputType(type);
        e.setText(val);
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                cb.set(s.toString());
            }
        });
        p.addView(e);
        return e;
    }

    CheckBox check(LinearLayout p, String lab, boolean v, final Str cb) {
        CheckBox c = new CheckBox(this);
        c.setText(lab);
        c.setChecked(v);
        c.setOnCheckedChangeListener((b, on) -> cb.set(on ? "true" : "false"));
        p.addView(c);
        return c;
    }

    static float pf(String s, float def) {
        try {
            return Float.parseFloat(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return def;
        }
    }

    static String fmt1(float v) {
        String s = String.format(Locale.US, "%.3f", v);
        while (s.endsWith("0") && !s.endsWith(".0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    static String trimf(float v) {
        if (v == Math.rint(v)) return String.valueOf((int) v);
        return String.format(Locale.US, "%.1f", v);
    }

    byte[] readBytes(File f) throws IOException {
        try (InputStream i = new FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = i.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }

    String readText(File f) throws IOException {
        return new String(readBytes(f), "UTF-8");
    }

    boolean save() {
        android.util.AtomicFile project = new android.util.AtomicFile(jf);
        FileOutputStream out = null;
        try {
            byte[] data = g.toJson().getBytes("UTF-8");
            out = project.startWrite();
            out.write(data);
            project.finishWrite(out);
            return true;
        } catch (Exception e) {
            if (out != null) project.failWrite(out);
            Toast.makeText(this, "No pude guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    // ================= Ciclo de vida =================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null) fontNodeId = b.getInt("fontNodeId", -1);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF121212));
        pid = getIntent().getStringExtra("pid");
        if (pid == null) pid = "p_v1";
        dir = new File(getFilesDir(), "projects/" + pid);
        adir = new File(dir, "assets");
        adir.mkdirs();
        jf = new File(dir, "game.json");
        iconFile = new File(dir, "icon.png");
        try {
            File nf = new File(dir, "name.txt");
            if (nf.exists()) exportName = readText(nf).trim();
        } catch (Exception e) {
        }
        Game.Src src = new Game.Src() {
            public InputStream open(String n) throws IOException {
                return new FileInputStream(new File(adir, n));
            }

            public File file(String n) throws IOException {
                return new File(adir, n);
            }
        };
        g = null;
        try (InputStream saved = new android.util.AtomicFile(jf).openRead()) {
            g = Game.fromJson(new String(Exporter.readAll(saved), "UTF-8"), src);
        } catch (Exception e) {
            g = null;
        }
        if (g == null) g = new Game(src);

        LinearLayout root = vbox();
        root.setBackgroundColor(0xFF121212);
        pv = new GameView(this, g, true);
        g.editMode = Math.max(Game.EDIT_MOVE, Math.min(Game.EDIT_ROTATE,
                getPreferences(MODE_PRIVATE).getInt("herramienta", Game.EDIT_MOVE)));
        pv.l = new GameView.L() {
            public void sel(Game.Node n) {
                select(n);
            }

            public void moved(Game.Node n) {
                rebuildProps();
            }
        };
        root.addView(pv, new LinearLayout.LayoutParams(MP, dp(230)));
        LinearLayout zoomPanel = new LinearLayout(this);
        zoomPanel.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView zoomLabel = label("Zoom: 100%");
        zoomPanel.addView(btn("−", x -> changeEditorZoom(g.editorZoom / 1.25f, false, zoomLabel)));
        zoomPanel.addView(btn("+", x -> changeEditorZoom(g.editorZoom * 1.25f, false, zoomLabel)));
        zoomPanel.addView(btn("Centrar", x -> changeEditorZoom(1f, true, zoomLabel)));
        zoomPanel.addView(zoomLabel);
        root.addView(zoomPanel);

        ScrollView sv = new ScrollView(this);
        LinearLayout col = vbox();
        col.setPadding(dp(12), dp(8), dp(12), dp(24));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(MP, 0, 1));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout bar = new LinearLayout(this);
        hs.addView(bar);
        bar.addView(btn("＋ Nodo", x -> addNode()));
        bar.addView(btn("Escenas", x -> scenesDialog()));
        bar.addView(btn("Autoload", x -> openScript(g.autoload)));
        final String[] tools = {"Movimiento", "Tamaño", "Rotación"};
        bar.addView(btn("Editar: " + tools[g.editMode], x -> new AlertDialog.Builder(this)
                .setTitle("Herramienta de edición visual").setSingleChoiceItems(tools, g.editMode, (d, which) -> {
                    g.editMode = which;
                    getPreferences(MODE_PRIVATE).edit().putInt("herramienta", which).apply();
                    ((Button) x).setText("Editar: " + tools[which]);
                    if (which == Game.EDIT_SIZE) toast("Selecciona el nodo y arrastra sus tiradores");
                    else if (which == Game.EDIT_ROTATE) toast("Arrastra el nodo alrededor del pivote marcado");
                    pv.invalidate();
                    d.dismiss();
                }).setNegativeButton("Cancelar", null).show()));
        bar.addView(btn("Ajustes", x -> settings()));
        bar.addView(btn("Assets", x -> assetsDialog()));
        bar.addView(btn("▶ Probar", x -> test(null)));
        bar.addView(btn("Exportar APK", x -> exportDialog()));
        bar.addView(btn("Guardar", x -> {
            save();
            toast("Guardado");
        }));
        col.addView(hs);
        sceneLabel = title("");
        col.addView(sceneLabel);
        list = vbox();
        col.addView(list);
        props = vbox();
        col.addView(props);
        setContentView(root);
        updateSceneLabel();
        rebuildList();
        rebuildProps();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("fontNodeId", fontNodeId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
        g.release();
    }

    // ================= Escenas =================
    void updateSceneLabel() {
        if (sceneLabel != null) {
            sceneLabel.setText("Escena: " + g.curScene.name + (g.curScene.id == g.startId ? "   ★ inicial" : ""));
        }
    }

    void refreshAll() {
        sel = null;
        g.sel = null;
        rebuildList();
        rebuildProps();
        updateSceneLabel();
    }

    String[] sceneNames() {
        String[] r = new String[g.scenes.size()];
        for (int i = 0; i < r.length; i++) r[i] = g.scenes.get(i).name;
        return r;
    }

    void scenesDialog() {
        ArrayList<String> items = new ArrayList<String>();
        for (Game.Scene s : g.scenes) {
            items.add((s == g.curScene ? "● " : "    ") + (s.id == g.startId ? "★ " : "") + s.name);
        }
        new AlertDialog.Builder(this).setTitle("Escenas  (● editando, ★ inicial)")
                .setItems(items.toArray(new String[0]), (d, i) -> sceneMenu(g.scenes.get(i)))
                .setNeutralButton("＋ Nueva escena", (d, w) -> inputDialog("Nombre de la escena", s -> {
                    Game.Scene n = g.addScene(s);
                    g.openScene(n);
                    refreshAll();
                }))
                .setNegativeButton("Cerrar", null).show();
    }

    void sceneMenu(final Game.Scene sc) {
        String[] items = {"Abrir en el editor", "Probar desde esta escena", "Renombrar", "Duplicar",
            "Marcar como inicial", "Eliminar"};
        new AlertDialog.Builder(this).setTitle(sc.name).setItems(items, (d, w) -> {
            if (w == 0) {
                g.openScene(sc);
                refreshAll();
            } else if (w == 1) {
                test(sc.name);
            } else if (w == 2) {
                inputDialog("Nuevo nombre", s -> {
                    String old = sc.name;
                    String nn = s.trim();
                    Game.Scene o = g.sceneByName(nn);
                    if (nn.isEmpty() || (o != null && o != sc)) {
                        toast("Ese nombre ya existe");
                        return;
                    }
                    sc.name = nn;
                    g.renameSceneRefs(old, nn);
                    updateSceneLabel();
                });
            } else if (w == 3) {
                try {
                    Game.Scene c = g.duplicateScene(sc, sc.name + " copia");
                    g.openScene(c);
                    refreshAll();
                } catch (Exception e) {
                    toast("Error: " + e.getMessage());
                }
            } else if (w == 4) {
                g.startId = sc.id;
                updateSceneLabel();
            } else {
                new AlertDialog.Builder(this).setTitle("¿Eliminar la escena " + sc.name + "?")
                        .setPositiveButton("Eliminar", (d2, w2) -> {
                            if (!g.removeScene(sc)) toast("Debe existir al menos una escena");
                            else refreshAll();
                        }).setNegativeButton("Cancelar", null).show();
            }
        }).show();
    }

    // ================= Árbol de nodos =================
    void select(Game.Node n) {
        if (n == sel) return;
        sel = n;
        g.sel = n;
        rebuildList();
        rebuildProps();
    }

    void addNode() {
        new AlertDialog.Builder(this).setTitle("Agregar nodo")
                .setItems(Game.TYPES, (d, i) -> {
                    Game.Node n = g.add(Game.TYPES[i]);
                    sel = null;
                    select(n);
                }).show();
    }

    void rebuildList() {
        list.removeAllViews();
        if (g.nodes.isEmpty()) {
            list.addView(label("Vacío. Toca ＋ Nodo para empezar."));
            return;
        }
        addRows(-1, 0);
    }

    void addRows(int parentId, int depth) {
        for (final Game.Node n : g.nodes) {
            boolean root = parentId == -1 ? (n.parent == -1 || g.byId(n.parent) == null) : n.parent == parentId;
            if (!root) continue;
            TextView t = new TextView(this);
            StringBuilder ind = new StringBuilder();
            for (int i = 0; i < depth; i++) ind.append("      ");
            t.setText(ind.toString() + (depth > 0 ? "└ " : "") + n.name + "  (" + n.type + ")");
            t.setTextSize(15);
            t.setPadding(dp(8), dp(10), dp(8), dp(10));
            if (n == sel) t.setBackgroundColor(0x3300AAFF);
            t.setOnClickListener(x -> select(n));
            list.addView(t);
            addRows(n.id, depth + 1);
        }
    }

    // ================= Propiedades =================
    void rebuildProps() {
        props.removeAllViews();
        final Game.Node n = sel;
        if (n == null) {
            props.addView(label("Toca un nodo de la lista o de la escena para editarlo."));
            return;
        }
        props.addView(title(n.name + "   [" + n.type + "]"));

        field(props, "Nombre del nodo", n.name, TXT, s -> {
            String t = s.trim();
            Game.Node o = g.byName(t);
            if (!t.isEmpty() && (o == null || o == n)) {
                n.name = t;
                rebuildList();
            }
        });

        // Padre
        props.addView(label("Padre"));
        final ArrayList<Game.Node> cands = new ArrayList<Game.Node>();
        ArrayList<String> names = new ArrayList<String>();
        names.add("(ninguno)");
        int idx = 0;
        for (Game.Node c : g.nodes) {
            if (g.isDesc(n, c)) continue;
            cands.add(c);
            names.add(c.name);
            if (c.id == n.parent) idx = cands.size();
        }
        Spinner sp = spinner(names.toArray(new String[0]));
        sp.setSelection(idx, false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                int np = pos == 0 ? -1 : cands.get(pos - 1).id;
                if (np != n.parent) {
                    g.reparent(n, np);
                    props.post(() -> {
                        rebuildList();
                        rebuildProps();
                    });
                }
            }

            public void onNothingSelected(AdapterView<?> a) {
            }
        });
        props.addView(sp);
        check(props, "Seguir al padre (posición, visibilidad y modulate)", n.inherit, s -> n.inherit = s.equals("true"));
        check(props, "Visible", n.visible, s -> n.visible = s.equals("true"));
        if (!n.type.equals("Camera2D") && !n.type.equals("Sound")) {
            check(props, "Fijo a la pantalla (interfaz, ignora la cámara)", n.fixed, s -> n.fixed = s.equals("true"));
        }

        // Modulate
        props.addView(label("Modulate (alpha de 0.0 a 1.0)"));
        final EditText ea = new EditText(this);
        ea.setInputType(NUM);
        ea.setText(fmt1(n.alpha));
        final SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(Math.round(n.alpha * 100));
        final boolean[] lock = new boolean[1];
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                n.alpha = p / 100f;
                lock[0] = true;
                ea.setText(fmt1(n.alpha));
                lock[0] = false;
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        ea.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                if (lock[0]) return;
                try {
                    float v = Float.parseFloat(s.toString().trim());
                    v = Math.max(0f, Math.min(1f, v));
                    n.alpha = v;
                    sb.setProgress(Math.round(v * 100));
                } catch (Exception e) {
                }
            }
        });
        props.addView(sb);
        props.addView(ea);

        // Posición y tamaño
        final CheckBox[] fitBox = new CheckBox[1];
        boolean showGeo = !n.type.equals("Sound");
        if (!showGeo) {
            for (Game.Node q : g.nodes) if (q.parent == n.id) showGeo = true;
            if (!showGeo) props.addView(label("Nodo invisible. La posición y el tamaño solo se usan si este nodo es padre de otros."));
        }
        RectF shown = new RectF();
        g.editBounds(n, n.x, n.y, shown);
        if (showGeo && !shown.isEmpty()) {
            field(props, "Posición X", trimf(shown.left), NUM, s -> g.setEditPosition(n, true, pf(s, Float.NaN)));
            field(props, "Posición Y", trimf(shown.top), NUM, s -> g.setEditPosition(n, false, pf(s, Float.NaN)));
            if (!n.type.equals("Sound")) {
                field(props, "Ancho", trimf(shown.width()), NUM, s -> {
                    float value = pf(s, Float.NaN);
                    if (!Float.isFinite(value)) return;
                    g.setEditSize(n, true, value);
                    if (fitBox[0] != null && fitBox[0].isChecked()) fitBox[0].setChecked(false);
                });
                field(props, "Alto", trimf(shown.height()), NUM, s -> {
                    float value = pf(s, Float.NaN);
                    if (!Float.isFinite(value)) return;
                    g.setEditSize(n, false, value);
                    if (fitBox[0] != null && fitBox[0].isChecked()) fitBox[0].setChecked(false);
                });
            }
        }

        if (!n.type.equals("Sound") && !n.type.equals("Camera2D") && !n.type.equals("Anchor")) {
            field(props, "Rotación (grados, positivo = horario)", trimf(n.rot), NUM, s -> n.rot = pf(s, n.rot));
            nodePicker("Ancla de rotación (nodo Anchor; vacío = centro del nodo)", "Anchor", n.anchor, s -> n.anchor = s);
        }

        // Según el tipo
        if (n.type.equals("Image") || n.type.equals("ImageButton")) {
            props.addView(label("Recurso: " + (n.res.isEmpty() ? "(ninguno)" : n.res)));
            check(props, "Toque inteligente (solo donde se dibuja la imagen, ignora transparencias)", n.smart, s -> n.smart = s.equals("true"));
            field(props, "Precisión del toque (8 a 256, menor = más rápido)", trimf(n.smartRes), NUM,
                    s -> n.smartRes = Math.max(8, Math.min(256, (int) pf(s, n.smartRes))));
            if (n.type.equals("ImageButton")) {
                check(props, "Oscurecer al tocar", n.dim, s -> n.dim = s.equals("true"));
            }
            if (g.assetOrders.containsKey(n.res)) props.addView(label("Imagen recortada externamente: coordenadas importadas."));
            props.addView(btn("Elegir imagen", x -> chooseAsset(true, s -> {
                n.res = s;
                if (n.fit) g.fitNode(n);
                rebuildProps();
            })));
            fitBox[0] = check(props, "Ajustar el nodo al tamaño de la imagen", n.fit, s -> {
                n.fit = s.equals("true");
                if (n.fit) {
                    g.fitNode(n);
                    props.post(() -> rebuildProps());
                }
            });
        } else if (n.type.equals("Button") || n.type.equals("Label")) {
            field(props, "Texto", n.text, TXT, s -> n.text = s);
            field(props, "Tamaño del texto", trimf(n.tsize), NUM, s -> n.tsize = Math.max(4f, pf(s, n.tsize)));
            if (n.type.equals("Label")) {
                props.addView(label("Fuente: " + (n.font.isEmpty() ? "Predeterminada" : n.font)));
                props.addView(btn("Agregar fuente (.ttf o .otf)", x -> chooseFont(n)));
                nodeColor(n, "Color del texto");
            }
        } else if (n.type.equals("Sound")) {
            props.addView(label("Recurso: " + (n.snd.isEmpty() ? "(ninguno)" : n.snd)));
            props.addView(btn("Elegir sonido", x -> chooseAsset(false, s -> {
                n.snd = s;
                rebuildProps();
            })));
            check(props, "Repetir (bucle)", n.loop, s -> n.loop = s.equals("true"));
            check(props, "Reproducir al iniciar", n.autoplay, s -> n.autoplay = s.equals("true"));
            props.addView(btn("▶ Probar sonido", x -> g.playSound(n)));
            props.addView(btn("■ Detener", x -> g.stopSound(n)));
        } else if (n.type.equals("AnimatedSprite")) {
            field(props, "FPS (cuadros por segundo)", trimf(n.fps), NUM, s -> n.fps = Math.max(0.1f, pf(s, n.fps)));
            check(props, "Repetir animación", n.loop, s -> n.loop = s.equals("true"));
            check(props, "Reproducir al iniciar el juego", n.autoplay, s -> n.autoplay = s.equals("true"));
            fitBox[0] = check(props, "Ajustar el nodo al tamaño del primer cuadro", n.fit, s -> {
                n.fit = s.equals("true");
                if (n.fit) {
                    g.fitNode(n);
                    props.post(() -> rebuildProps());
                }
            });
            props.addView(label("Cuadros (" + n.frames.size() + ")"));
            for (int i = 0; i < n.frames.size(); i++) {
                TextView t = new TextView(this);
                t.setText((i + 1) + ".  " + n.frames.get(i));
                props.addView(t);
            }
            props.addView(btn("＋ Agregar imagen", x -> chooseAsset(true, s -> {
                n.frames.add(s);
                if (n.frames.size() == 1 && n.fit) g.fitNode(n);
                rebuildProps();
            })));
            props.addView(btn("Quitar el último cuadro", x -> {
                if (!n.frames.isEmpty()) n.frames.remove(n.frames.size() - 1);
                n.frame = 0;
                rebuildProps();
            }));
            props.addView(btn(n.preview ? "■ Detener vista previa" : "▶ Reproducir vista previa", x -> {
                n.preview = !n.preview;
                n.frame = 0;
                n.ft = 0;
                rebuildProps();
            }));
            props.addView(label("La vista previa solo se ve aquí; no afecta al juego."));
        } else if (n.type.equals("Group")) {
            props.addView(label("Grupo: no se dibuja. Pon nodos dentro (eligiéndolo como Padre) para moverlos, ocultarlos o cambiar su modulate todos a la vez."));
        } else if (n.type.equals("Video")) {
            props.addView(label("Video: " + (n.res.isEmpty() ? "(ninguno)" : n.res)));
            props.addView(btn("Elegir video (mp4, webm, mkv)", x -> chooseVideo(s -> {
                n.res = s;
                if (n.fit) g.fitNode(n);
                rebuildProps();
            })));
            fitBox[0] = check(props, "Ajustar el nodo al tamaño del video", n.fit, s -> {
                n.fit = s.equals("true");
                if (n.fit) {
                    g.fitNode(n);
                    props.post(() -> rebuildProps());
                }
            });
            check(props, "Repetir (bucle)", n.loop, s -> n.loop = s.equals("true"));
            check(props, "Reproducir al iniciar", n.autoplay, s -> n.autoplay = s.equals("true"));
            field(props, "Volumen (0.0 a 1.0)", fmt1(n.vol), NUM, s -> n.vol = Math.max(0f, Math.min(1f, pf(s, n.vol))));
            props.addView(label("El video se ve al probar el juego, no en el editor. Se dibuja encima de los demás nodos."));
        } else if (n.type.equals("Camera2D")) {
            props.addView(label("El centro de este nodo es el centro de la cámara."));
            field(props, "Zoom (1.0 = normal)", fmt1(n.zoom), NUM, s -> n.zoom = Math.max(0.05f, pf(s, n.zoom)));
            field(props, "Suavizado (0 = sin suavizado, 5 = suave)", fmt1(n.smooth), NUM,
                    s -> n.smooth = Math.max(0f, pf(s, n.smooth)));
            check(props, "Cámara activa al iniciar la escena", n.current, s -> n.current = s.equals("true"));
            props.addView(label("Seguir a"));
            final ArrayList<String> fn = new ArrayList<String>();
            fn.add("(ninguno)");
            int fi = 0;
            for (Game.Node c : g.nodes) {
                if (c == n || c.type.equals("Camera2D")) continue;
                fn.add(c.name);
                if (c.name.equals(n.follow)) fi = fn.size() - 1;
            }
            Spinner sf = spinner(fn.toArray(new String[0]));
            sf.setSelection(fi, false);
            sf.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                    n.follow = pos == 0 ? "" : fn.get(pos);
                }

                public void onNothingSelected(AdapterView<?> a) {
                }
            });
            props.addView(sf);
            check(props, "Limitar el movimiento de la cámara", n.lim, s -> n.lim = s.equals("true"));
            field(props, "Límite izquierdo", trimf(n.ll), NUM, s -> n.ll = pf(s, n.ll));
            field(props, "Límite de arriba", trimf(n.lt), NUM, s -> n.lt = pf(s, n.lt));
            field(props, "Límite derecho", trimf(n.lr), NUM, s -> n.lr = pf(s, n.lr));
            field(props, "Límite de abajo", trimf(n.lb), NUM, s -> n.lb = pf(s, n.lb));
            props.addView(label("La cámara solo se usa si el modo de vista del proyecto es Camera2D "
                    + "(en Ajustes) o con el bloque Usar viewport o cámara."));
        }

        if (n.type.equals("Color")) {
            nodeColor(n, "Color del recuadro");
            props.addView(label("Se ve completo con modulate 1.0; menos modulate lo vuelve transparente."));
        } else if (n.type.equals("Anchor")) {
            props.addView(label("Punto de giro. En otro nodo (o en un Agarre) elígelo como Ancla: el objeto rotará alrededor de este punto. "
                    + "Solo se ve en el editor."));
        } else if (n.type.equals("Limiter")) {
            props.addView(label("Caja invisible. Asigna este nodo como padre del objeto que quieras limitar. "
                    + "En juego, el hijo directo no saldrá de la caja al moverse, cambiar de tamaño o rotar. "
                    + "Si no cabe, se reduce para ajustarlo. Sin 'Seguir al padre', se usan los ejes globales."));
        } else if (n.type.equals("Collision")) {
            check(props, "Colisión activa al iniciar", n.collisionEnabled, s -> n.collisionEnabled = s.equals("true"));
            props.addView(label("Área invisible que detecta solapamientos sin bloquear el movimiento. "
                    + "Usa sus eventos de entrada, permanencia y salida, o elige 'Colisión entre nodos' en una condición."));
        } else if (n.type.equals("Grab")) {
            props.addView(label("Área que se agarra con el dedo. Si es hijo de un objeto, lo agarra a él."));
            nodePicker("Objeto a agarrar (vacío = el padre de este nodo)", null, n.gtarget, s -> n.gtarget = s);
            props.addView(label("Al agarrar"));
            Spinner sm = spinner(new String[]{"arrastrar y soltar", "rotar jalando izquierda / derecha"});
            sm.setSelection(n.gmode.equals("rotar") ? 1 : 0, false);
            sm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                    n.gmode = pos == 1 ? "rotar" : "arrastrar";
                }

                public void onNothingSelected(AdapterView<?> a) {
                }
            });
            props.addView(sm);
            nodePicker("Ancla del giro (nodo Anchor; vacío = centro del objeto)", "Anchor", n.anchor, s -> n.anchor = s);
            field(props, "Límite de giro a la izquierda (grados; vacío = sin límite)", n.limL < 0 ? "" : trimf(n.limL), NUM,
                    s -> n.limL = s.trim().isEmpty() ? -1f : Math.max(0f, pf(s, 0f)));
            field(props, "Límite de giro a la derecha (grados; vacío = sin límite)", n.limR < 0 ? "" : trimf(n.limR), NUM,
                    s -> n.limR = s.trim().isEmpty() ? -1f : Math.max(0f, pf(s, 0f)));
            props.addView(label("Los límites se miden desde la rotación inicial del objeto. 0 = bloquea ese lado."));
        }

        LinearLayout lr = new LinearLayout(this);
        for (final String[] m : new String[][]{{"▲ Subir", "subir"}, {"▼ Bajar", "bajar"}}) {
            lr.addView(btn(m[0], x -> {
                g.layer(n, m[1]);
                rebuildList();
                pv.invalidate();
            }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        props.addView(label("Capa (orden de dibujo entre nodos del mismo padre)"));
        props.addView(lr);
        props.addView(title("Lógica"));
        props.addView(btn("Script de bloques (" + n.script.size() + ")", x -> openScript(n)));
        props.addView(btn("Convertir nodo en escena", x -> new AlertDialog.Builder(this)
                .setTitle("Convertir «" + n.name + "» en escena")
                .setMessage("Incluye este nodo y sus hijos. Los nodos externos no se trasladan.")
                .setPositiveButton("Copiar", (d, w) -> convertNode(n, false))
                .setNeutralButton("Mover", (d, w) -> convertNode(n, true))
                .setNegativeButton("Cancelar", null).show()));
        props.addView(btn("Eliminar nodo", x -> new AlertDialog.Builder(this)
                .setTitle("¿Eliminar " + n.name + "?")
                .setMessage("También se eliminan sus hijos.")
                .setPositiveButton("Eliminar", (d, w) -> {
                    g.remove(n);
                    sel = null;
                    g.sel = null;
                    rebuildList();
                    rebuildProps();
                }).setNegativeButton("Cancelar", null).show()));
    }

    void convertNode(Game.Node node, boolean move) {
        try {
            Game.Scene scene = g.nodeToScene(node, node.name, move);
            g.openScene(scene);
            save();
            refreshAll();
            pv.invalidate();
            toast("Escena creada: " + scene.name);
        } catch (Exception e) {
            toast("No pude crear la escena: " + e.getMessage());
        }
    }

    // Selector de un nodo (por tipo; type == null = cualquiera, y "(padre)" como valor vacío)
    void nodePicker(String lab, String type, String cur, final Str set) {
        props.addView(label(lab));
        final ArrayList<String> l = new ArrayList<String>();
        l.add(type == null ? "(padre)" : "(ninguno)");
        int si = 0;
        for (Game.Node c : g.nodes) {
            if (type != null && !c.type.equals(type)) continue;
            l.add(c.name);
            if (c.name.equals(cur)) si = l.size() - 1;
        }
        Spinner sp = spinner(l.toArray(new String[0]));
        sp.setSelection(si, false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                set.set(pos == 0 ? "" : l.get(pos));
            }

            public void onNothingSelected(AdapterView<?> a) {
            }
        });
        props.addView(sp);
    }

    void nodeColor(Game.Node n, String title) {
        final Button swatch = btn(title, x -> colorDialog(n.color, s -> {
            n.color = 0xFF000000 | Integer.parseInt(s, 16);
            x.setBackgroundColor(n.color);
            ((Button) x).setTextColor(colorContrast(n.color));
            pv.invalidate();
        }));
        swatch.setBackgroundColor(n.color);
        swatch.setTextColor(colorContrast(n.color));
        props.addView(swatch);
    }

    static int colorContrast(int color) {
        int light = 299 * ((color >> 16) & 255) + 587 * ((color >> 8) & 255) + 114 * (color & 255);
        return light >= 128000 ? 0xFF000000 : 0xFFFFFFFF;
    }

    // Selector de color: rojo, verde, azul o código hexadecimal RRGGBB
    void colorDialog(int cur, final Str ok) {
        final int[] c = {cur & 0xFFFFFF};
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        final View sw = new View(this);
        v.addView(sw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        final EditText hx = new EditText(this);
        hx.setInputType(TXT);
        final SeekBar[] sb = new SeekBar[3];
        final Runnable upd = () -> {
            sw.setBackgroundColor(0xFF000000 | c[0]);
            String h = String.format(Locale.ROOT, "%06X", c[0]);
            if (!hx.getText().toString().equals(h)) hx.setText(h);
            for (int i = 0; i < 3; i++) sb[i].setProgress((c[0] >> (16 - 8 * i)) & 255);
        };
        String[] nm = {"Rojo", "Verde", "Azul"};
        for (int i = 0; i < 3; i++) {
            final int sh = 16 - 8 * i;
            v.addView(label(nm[i]));
            sb[i] = new SeekBar(this);
            sb[i].setMax(255);
            sb[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean user) {
                    if (!user) return;
                    c[0] = (c[0] & ~(255 << sh)) | (p << sh);
                    sw.setBackgroundColor(0xFF000000 | c[0]);
                    String h = String.format(Locale.ROOT, "%06X", c[0]);
                    if (!hx.getText().toString().equals(h)) hx.setText(h);
                }

                public void onStartTrackingTouch(SeekBar s) {
                }

                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            v.addView(sb[i]);
        }
        v.addView(label("Código hexadecimal (RRGGBB)"));
        hx.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence q, int a1, int b1, int d1) {
            }

            public void onTextChanged(CharSequence q, int a1, int b1, int d1) {
            }

            public void afterTextChanged(Editable q) {
                if (q.length() != 6) return;
                try {
                    int n = Integer.parseInt(q.toString(), 16);
                    if (n != c[0]) {
                        c[0] = n;
                        upd.run();
                    }
                } catch (Exception e) {
                }
            }
        });
        v.addView(hx);
        upd.run();
        new AlertDialog.Builder(this).setTitle("Color").setView(v)
                .setPositiveButton("Aceptar", (d, w) -> ok.set(String.format(Locale.ROOT, "%06X", c[0])))
                .setNegativeButton("Cancelar", null).show();
    }

    // ================= Assets =================
    static boolean isImg(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp")
                || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    static boolean isSnd(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.endsWith(".ogg") || n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a")
                || n.endsWith(".aac");
    }

    static boolean isVid(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mkv") || n.endsWith(".3gp");
    }

    String[] videoAssets() {
        ArrayList<String> l = new ArrayList<String>();
        File[] fs = adir.listFiles();
        if (fs != null) for (File f : fs) if (isVid(f.getName())) l.add(f.getName());
        Collections.sort(l);
        return l.toArray(new String[0]);
    }

    void chooseVideo(final Pick cb) {
        final String[] names = videoAssets();
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Elegir video (mp4, webm VP8/VP9, mkv)");
        if (names.length == 0) b.setMessage("No hay videos. Usa Importar y elige un .mp4, .webm o .mkv.");
        else b.setItems(names, (d, i) -> cb.got(names[i]));
        b.setNeutralButton("Importar…", (d, i) -> startImport());
        b.setNegativeButton("Cancelar", null).show();
    }

    String[] assets(boolean image) {
        ArrayList<String> l = new ArrayList<String>();
        File[] fs = adir.listFiles();
        if (fs != null) for (File f : fs) if (image ? isImg(f.getName()) : isSnd(f.getName())) l.add(f.getName());
        Collections.sort(l);
        return l.toArray(new String[0]);
    }

    void chooseAsset(boolean image, final Pick cb) {
        final String[] names = assets(image);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(image ? "Elegir imagen" : "Elegir sonido");
        if (names.length == 0) b.setMessage("No hay assets de este tipo. Usa Importar.");
        else b.setItems(names, (d, i) -> cb.got(names[i]));
        b.setNeutralButton("Importar…", (d, i) -> startImport());
        b.setNegativeButton("Cancelar", null).show();
    }

    static boolean isFont(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }

    void chooseFont(Game.Node node) {
        ArrayList<String> names = new ArrayList<String>();
        File[] files = adir.listFiles();
        if (files != null) for (File f : files) if (isFont(f.getName())) names.add(f.getName());
        Collections.sort(names);
        names.add(0, "Predeterminada");
        new AlertDialog.Builder(this).setTitle("Fuente del texto")
                .setItems(names.toArray(new String[0]), (d, i) -> setFont(node, i == 0 ? "" : names.get(i)))
                .setNeutralButton("Importar fuente…", (d, w) -> {
                    fontNodeId = node.id;
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    // Algunos proveedores Android catalogan las fuentes como archivos genéricos.
                    pick.setType("*/*");
                    startActivityForResult(pick, IMPORT_FONT);
                }).setNegativeButton("Cancelar", null).show();
    }

    void setFont(Game.Node node, String name) {
        node.font = name;
        save();
        rebuildProps();
        pv.invalidate();
    }

    void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, 1);
    }

    void changeEditorZoom(float zoom, boolean center, TextView status) {
        g.setEditorZoom(zoom);
        if (center) g.editorPanX = g.editorPanY = 0f;
        status.setText("Zoom: " + Math.round(g.editorZoom * 100f) + "%");
        pv.invalidate();
    }

    void assetsDialog() {
        if (importingOrders) {
            toast("Espera a que termine la importación de órdenes");
            return;
        }
        final ArrayList<String> all = new ArrayList<String>();
        File[] fs = adir.listFiles();
        if (fs != null) for (File f : fs) all.add(f.getName());
        Collections.sort(all);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Assets del proyecto");
        if (all.isEmpty()) {
            b.setMessage("Aún no hay assets. Importa imágenes, sonidos, videos y fuentes (.ttf, .otf).");
        } else {
            b.setItems(all.toArray(new String[0]), (d, i) -> {
                final String nm = all.get(i);
                new AlertDialog.Builder(this).setTitle("¿Eliminar " + nm + "?")
                        .setPositiveButton("Eliminar", (d2, w) -> {
                            if (new File(adir, nm).delete()) {
                                g.assetOrders.remove(nm);
                                save();
                                g.flush();
                                pv.invalidate();
                            }
                        }).setNegativeButton("Cancelar", null).show();
            });
        }
        b.setNeutralButton("Importar…", (d, i) -> startImport());
        b.setPositiveButton("Importar órdenes", (d, i) -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            startActivityForResult(pick, IMPORT_ORDERS);
        });
        b.setNegativeButton("Cerrar", null).show();
    }

    String nameOf(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception e) {
        }
        return "asset";
    }

    void importOne(Uri u) throws IOException {
        importAsset(u, nameOf(u));
    }

    File importAsset(Uri u, String name) throws IOException {
        String nm = name.replaceAll("[^A-Za-z0-9._-]", "_");
        File f = new File(adir, nm);
        int k = 1;
        while (f.exists()) f = new File(adir, (k++) + "_" + nm);
        try (InputStream i = getContentResolver().openInputStream(u); OutputStream o = new FileOutputStream(f)) {
            if (i == null) throw new IOException("No pude abrir el archivo");
            byte[] buf = new byte[16384];
            int n;
            while ((n = i.read(buf)) != -1) o.write(buf, 0, n);
        } catch (IOException e) {
            f.delete();
            throw e;
        }
        return f;
    }

    // Recorta la imagen elegida en un cuadrado de 192 x 192 y la guarda como ícono del proyecto
    void makeIcon(Uri u) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream i = getContentResolver().openInputStream(u)) {
            BitmapFactory.decodeStream(i, null, o);
        }
        int sample = 1;
        while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= 384) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        Bitmap src;
        try (InputStream i = getContentResolver().openInputStream(u)) {
            src = BitmapFactory.decodeStream(i, null, o);
        }
        if (src == null) throw new IOException("No pude leer la imagen");
        int sz = Math.min(src.getWidth(), src.getHeight());
        Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - sz) / 2, (src.getHeight() - sz) / 2, sz, sz);
        Bitmap ic = Bitmap.createScaledBitmap(sq, 192, 192, true);
        try (FileOutputStream f = new FileOutputStream(iconFile)) {
            ic.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        int requestedNode = fontNodeId;
        if (req == IMPORT_FONT) fontNodeId = -1;
        if (res != RESULT_OK || d == null) return;
        try {
            if (req == IMPORT_ORDERS && d.getData() != null) {
                importOrders(d.getData());
            } else if (req == IMPORT_FONT && d.getData() != null) {
                String name = nameOf(d.getData());
                if (!isFont(name)) throw new IOException("Elige un archivo .ttf o .otf");
                for (Game.Node node : g.allNodes()) if (node.id == requestedNode && node.type.equals("Label")) {
                    File font = importAsset(d.getData(), name);
                    try {
                        android.graphics.Typeface.createFromFile(font);
                    } catch (RuntimeException invalid) {
                        font.delete();
                        throw new IOException("No pude leer la fuente");
                    }
                    setFont(node, font.getName());
                    toast("Fuente agregada");
                    break;
                }
            } else if (req == 1) {
                int cnt = 0;
                ClipData cd = d.getClipData();
                if (cd != null) {
                    for (int i = 0; i < cd.getItemCount(); i++) {
                        importOne(cd.getItemAt(i).getUri());
                        cnt++;
                    }
                } else if (d.getData() != null) {
                    importOne(d.getData());
                    cnt++;
                }
                g.flush();
                toast("Importados: " + cnt);
            } else if (req == 2 && d.getData() != null && exported != null) {
                try (InputStream i = new FileInputStream(exported);
                     OutputStream o = getContentResolver().openOutputStream(d.getData())) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
                }
                Toast.makeText(this, "APK guardado ✔ (" + (exported.length() / 1024) + " KB)", Toast.LENGTH_LONG).show();
            } else if (req == 3 && d.getData() != null) {
                makeIcon(d.getData());
                if (tIconInfo != null) tIconInfo.setText("Ícono personalizado ✔");
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void importOrders(Uri uri) {
        if (importingOrders) return;
        importingOrders = true;
        toast("Importando órdenes…");
        new Thread(() -> {
            try {
                LinkedHashMap<String, Game.AssetOrder> loaded = AssetOrdersImport.load(
                        getContentResolver().openInputStream(uri), adir);
                runOnUiThread(() -> {
                    importingOrders = false;
                    if (isDestroyed() || isFinishing()) {
                        for (String name : loaded.keySet()) new File(adir, name).delete();
                        return;
                    }
                    g.assetOrders.putAll(loaded);
                    if (!save()) {
                        for (String name : loaded.keySet()) {
                            g.assetOrders.remove(name);
                            new File(adir, name).delete();
                        }
                        return;
                    }
                    rebuildProps();
                    pv.invalidate();
                    toast("Imágenes y órdenes importadas: " + loaded.size());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    importingOrders = false;
                    if (!isDestroyed()) toast("No pude importar las órdenes: " + e.getMessage());
                });
            }
        }, "Importar ordenes").start();
    }

    // ================= Ajustes del proyecto =================
    void settings() {
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        v.addView(label("Orientación inicial"));
        final Spinner so = spinner(new String[]{"Horizontal", "Vertical (portátil)"});
        so.setSelection(g.landscape ? 0 : 1);
        v.addView(so);
        v.addView(label("Modo de pantalla"));
        final Spinner sm = spinner(new String[]{"Mantener (con barras negras)", "Estirar", "Rellenar (recorta)"});
        sm.setSelection(g.stretch.equals("stretch") ? 1 : g.stretch.equals("fill") ? 2 : 0);
        v.addView(sm);
        v.addView(label("Modo de vista"));
        final Spinner sv2 = spinner(new String[]{"Viewport (sin cámara)", "Camera2D"});
        sv2.setSelection(g.viewMode.equals("camera") ? 1 : 0);
        v.addView(sv2);
        v.addView(label("Viewport horizontal (con el teclado o elige uno)"));
        final String[] presets = {"Personalizado", "1280 x 720", "1920 x 1080", "640 x 360", "960 x 540"};
        final Spinner spr = spinner(presets);
        v.addView(spr);
        final EditText ew = new EditText(this);
        ew.setInputType(InputType.TYPE_CLASS_NUMBER);
        ew.setHint("Ancho horizontal");
        ew.setText(String.valueOf(g.vwL));
        final EditText eh = new EditText(this);
        eh.setInputType(InputType.TYPE_CLASS_NUMBER);
        eh.setHint("Alto horizontal");
        eh.setText(String.valueOf(g.vhL));
        v.addView(ew);
        v.addView(eh);
        v.addView(label("Viewport vertical"));
        final EditText pw = new EditText(this);
        pw.setInputType(InputType.TYPE_CLASS_NUMBER);
        pw.setHint("Ancho vertical");
        pw.setText(String.valueOf(g.vwP));
        final EditText ph = new EditText(this);
        ph.setInputType(InputType.TYPE_CLASS_NUMBER);
        ph.setHint("Alto vertical");
        ph.setText(String.valueOf(g.vhP));
        v.addView(pw);
        v.addView(ph);
        spr.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View vv, int pos, long id) {
                if (pos == 0) return;
                String[] p = presets[pos].split(" x ");
                ew.setText(p[0]);
                eh.setText(p[1]);
                pw.setText(p[1]);
                ph.setText(p[0]);
            }

            public void onNothingSelected(AdapterView<?> a) {
            }
        });
        v.addView(label("Color de fondo (hexadecimal RRGGBB)"));
        final EditText ec = new EditText(this);
        ec.setText(String.format(Locale.US, "%06X", g.bg & 0xFFFFFF));
        v.addView(ec);
        final CheckBox ca = check(v, "Guardar variables automáticamente al cerrar el juego", g.autosave, s -> {
        });
        ScrollView sc = new ScrollView(this);
        sc.addView(v);
        new AlertDialog.Builder(this).setTitle("Ajustes del proyecto").setView(sc)
                .setPositiveButton("Aplicar", (d, w) -> {
                    int mi = sm.getSelectedItemPosition();
                    g.stretch = mi == 1 ? "stretch" : mi == 2 ? "fill" : "keep";
                    g.viewMode = sv2.getSelectedItemPosition() == 1 ? "camera" : "viewport";
                    g.autosave = ca.isChecked();
                    try {
                        g.vwL = Math.max(64, Math.min(8192, Integer.parseInt(ew.getText().toString().trim())));
                        g.vhL = Math.max(64, Math.min(8192, Integer.parseInt(eh.getText().toString().trim())));
                        g.vwP = Math.max(64, Math.min(8192, Integer.parseInt(pw.getText().toString().trim())));
                        g.vhP = Math.max(64, Math.min(8192, Integer.parseInt(ph.getText().toString().trim())));
                    } catch (Exception e) {
                    }
                    try {
                        g.bg = 0xFF000000 | Integer.parseInt(ec.getText().toString().trim().replace("#", ""), 16);
                    } catch (Exception e) {
                    }
                    g.applyOrientation(so.getSelectedItemPosition() == 0);
                    save();
                }).setNegativeButton("Cancelar", null).show();
    }

    // ================= Probar y exportar =================
    void test(String sceneName) {
        save();
        Intent i = new Intent(this, PlayActivity.class);
        i.putExtra("dir", dir.getPath());
        if (sceneName != null) i.putExtra("scene", sceneName);
        startActivity(i);
    }

    void exportDialog() {
        save();
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        v.addView(label("Nombre de la app"));
        final EditText en = new EditText(this);
        en.setText(exportName);
        v.addView(en);
        v.addView(label("Versión"));
        final EditText ev = new EditText(this);
        ev.setText("1.0");
        ev.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        v.addView(ev);
        v.addView(label("Paquete: com.[marca].[juego]  (vacío = automático)"));
        final android.content.SharedPreferences sp0 = getSharedPreferences("exp", 0);
        LinearLayout pr = new LinearLayout(this);
        final EditText eb = new EditText(this), ej = new EditText(this);
        eb.setHint("marca");
        ej.setHint("juego");
        eb.setInputType(TXT);
        ej.setInputType(TXT);
        eb.setText(sp0.getString("marca", ""));
        ej.setText(sp0.getString("juego", ""));
        pr.addView(eb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        pr.addView(ej, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        v.addView(pr);
        final TextView pkv = new TextView(this);
        v.addView(pkv);
        final Runnable upd = () -> {
            String k = makePkg(eb.getText().toString(), ej.getText().toString(), en.getText().toString());
            pkv.setText("→ " + (k.isEmpty() ? "automático" : k));
        };
        TextWatcher tw = new TextWatcher() {
            public void beforeTextChanged(CharSequence q, int a1, int c, int d) {
            }

            public void onTextChanged(CharSequence q, int a1, int c, int d) {
            }

            public void afterTextChanged(Editable q) {
                upd.run();
            }
        };
        eb.addTextChangedListener(tw);
        ej.addTextChangedListener(tw);
        en.addTextChangedListener(tw);
        upd.run();
        v.addView(label("Ícono de la app"));
        tIconInfo = new TextView(this);
        tIconInfo.setText(iconFile.exists() ? "Ícono personalizado ✔" : "Ícono por defecto");
        v.addView(tIconInfo);
        v.addView(btn("Elegir ícono (una imagen)", x -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            startActivityForResult(i, 3);
        }));
        v.addView(btn("Quitar ícono", x -> {
            iconFile.delete();
            tIconInfo.setText("Ícono por defecto");
        }));
        v.addView(label("Imágenes del APK"));
        final Spinner si = spinner(new String[]{"Optimizar (WebP, más liviano)",
            "Optimizar sin pérdida (pixel art)", "No tocar las imágenes"});
        v.addView(si);
        v.addView(label("Android: 7.0 (API 24) hasta la última versión (fijo)"));
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        new AlertDialog.Builder(this).setTitle("Exportar APK").setView(sv)
                .setPositiveButton("Exportar", (d, w) -> {
                    String nm = en.getText().toString().trim();
                    String vr = ev.getText().toString().trim();
                    if (nm.isEmpty()) nm = "Juego";
                    if (vr.isEmpty()) vr = "1.0";
                    String pk = makePkg(eb.getText().toString(), ej.getText().toString(), nm);
                    sp0.edit().putString("marca", eb.getText().toString().trim())
                            .putString("juego", ej.getText().toString().trim()).apply();
                    doExport(nm, vr, si.getSelectedItemPosition(), pk);
                }).setNegativeButton("Cancelar", null).show();
    }

    static String seg(String t) {
        t = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (!t.isEmpty() && !Character.isLetter(t.charAt(0))) t = "a" + t;
        return t.length() > 30 ? t.substring(0, 30) : t;
    }

    // com.marca.juego (vacío = automático)
    static String makePkg(String marca, String juego, String app) {
        String m = seg(marca), j = seg(juego);
        if (m.isEmpty() && j.isEmpty()) return "";
        if (m.isEmpty()) m = "app";
        if (j.isEmpty()) j = seg(app);
        if (j.isEmpty()) j = "juego";
        return "com." + m + "." + j;
    }

    void doExport(final String name, final String ver, final int imgMode, final String pkg) {
        exportName = name;
        final Set<String> used = g.usedAssets();
        final String json;
        final byte[] icon;
        try {
            json = g.toJson(null, used);
            icon = iconFile.exists() ? readBytes(iconFile) : null;
        } catch (Exception e) {
            toast("Error: " + e.getMessage());
            return;
        }
        final HashMap<String, float[]> sizes = g.imageSizes();
        final boolean noDown = g.hasSizeTween();
        toast("Exportando…");
        new Thread(() -> {
            try {
                File out = new File(getCacheDir(), "export.apk");
                Exporter.build(this, json, adir, used, sizes, noDown, imgMode, icon, name, ver, pkg, out);
                exported = out;
                runOnUiThread(() -> {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/vnd.android.package-archive");
                    i.putExtra(Intent.EXTRA_TITLE, name.replaceAll("[^A-Za-z0-9._-]", "_") + ".apk");
                    startActivityForResult(i, 2);
                });
            } catch (Throwable e) {
                final String m = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> Toast.makeText(this, "Error al exportar: " + m, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ================= Selectores rápidos (sin escribir) =================
    void collectVars(ArrayList<Game.Blk> l, TreeSet<String> vars, TreeSet<String> msgs) {
        for (Game.Blk b : l) {
            if (b.t.equals("var") && !b.s("name").trim().isEmpty()) vars.add(b.s("name").trim());
            if ((b.t.equals("send") || b.t.equals("recv")) && !b.s("msg").trim().isEmpty()) msgs.add(b.s("msg").trim());
            collectVars(b.body, vars, msgs);
            collectVars(b.els, vars, msgs);
        }
    }

    TreeSet<String> variableNames() {
        TreeSet<String> v = new TreeSet<String>();
        TreeSet<String> m = new TreeSet<String>();
        for (Game.Node n : g.allNodes()) {
            collectVars(n.script, v, m);
            for (Game.Blk e : n.script) {
                if (!e.t.equals("func")) continue;
                for (Game.Parameter parameter : Game.parameters(e)) v.add(parameter.name);
            }
        }
        return v;
    }

    TreeSet<String> funcNames(Iterable<Game.Node> nodes) {
        TreeSet<String> r = new TreeSet<String>();
        for (Game.Node n : nodes) {
            for (Game.Blk e : n.script) {
                if (e.t.equals("func") && !e.s("name").trim().isEmpty()) r.add(e.s("name").trim());
            }
        }
        return r;
    }

    TreeSet<String> messageNames() {
        TreeSet<String> v = new TreeSet<String>();
        TreeSet<String> m = new TreeSet<String>();
        for (Game.Node n : g.allNodes()) collectVars(n.script, v, m);
        return m;
    }

    String[] enumNames() {
        LinkedHashMap<String, String[]> en = new LinkedHashMap<String, String[]>();
        g.collectDecls(en, new LinkedHashSet<String>());
        return en.keySet().toArray(new String[0]);
    }

    // Fases de un enum con su número, y cómo usarlas en condiciones
    void enumInfo(String name) {
        if (name.equals("(ninguno)")) name = "";
        LinkedHashMap<String, String[]> en = new LinkedHashMap<String, String[]>();
        g.collectDecls(en, new LinkedHashSet<String>());
        StringBuilder sb = new StringBuilder();
        for (String k : en.keySet()) {
            if (!name.isEmpty() && !k.equals(name)) continue;
            String[] vs = en.get(k);
            sb.append(k).append("  (").append(vs.length).append(" fases)\n");
            for (int i = 0; i < vs.length; i++) sb.append("   ").append(i).append("   ").append(vs[i]).append('\n');
            sb.append('\n');
        }
        if (sb.length() == 0) sb.append("Aún no hay enums. Crea uno con el bloque Enum (definir).\n\n");
        sb.append("En condiciones puedes usar:\nfase = Nombre.Valor\nfase < Nombre.Valor\nfase.indice   fase.nombre\nNombre.cantidad");
        new AlertDialog.Builder(this).setTitle("Información de enum").setMessage(sb.toString())
                .setPositiveButton("Cerrar", null).show();
    }

    // Coincidir: un caso por cada fase del enum elegido
    void casesFromEnum(final ArrayList<Game.Blk> target, final Runnable ref) {
        final LinkedHashMap<String, String[]> en = new LinkedHashMap<String, String[]>();
        g.collectDecls(en, new LinkedHashSet<String>());
        if (en.isEmpty()) {
            toast("Aún no hay enums: crea uno con el bloque Enum");
            return;
        }
        final String[] nm = en.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Elegir enum").setItems(nm, (d, i) -> {
            for (String v : en.get(nm[i])) {
                Game.Blk c = newBlk("case");
                c.f.put("val", nm[i] + "." + v);
                target.add(c);
            }
            ref.run();
        }).show();
    }

    String[] typeNames() {
        LinkedHashMap<String, String[]> en = new LinkedHashMap<String, String[]>();
        LinkedHashSet<String> ps = new LinkedHashSet<String>();
        g.collectDecls(en, ps);
        ArrayList<String> l = new ArrayList<String>();
        for (String s : BASE_TYPES) l.add(s);
        l.addAll(en.keySet());
        return l.toArray(new String[0]);
    }

    void inputDialog(String title, final Str cb) {
        final EditText e = new EditText(this);
        e.setInputType(TXT);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(10), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(MP, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("Aceptar", (d, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty()) cb.set(s);
                }).setNegativeButton("Cancelar", null).show();
    }

    void listChooser(String title, TreeSet<String> items, final String newLabel, final EditText target) {
        final ArrayList<String> l = new ArrayList<String>(items);
        if (newLabel != null) l.add(newLabel);
        if (l.isEmpty()) {
            toast("Aún no hay variables: crea una con el bloque Variable");
            return;
        }
        final int newIdx = newLabel != null ? l.size() - 1 : -1;
        new AlertDialog.Builder(this).setTitle(title).setItems(l.toArray(new String[0]), (d, i) -> {
            if (i == newIdx) inputDialog(newLabel.replace("＋ ", ""), s -> target.setText(s));
            else target.setText(l.get(i));
        }).show();
    }

    void varChooser(EditText target, boolean allowNew) {
        listChooser("Elegir variable", variableNames(), allowNew ? "＋ Nueva variable…" : null, target);
    }

    void msgChooser(EditText target) {
        listChooser("Elegir mensaje", messageNames(), "＋ Nuevo mensaje…", target);
    }

    void propChooser(final EditText target) {
        final String[] nn = nodeNames();
        new AlertDialog.Builder(this).setTitle("Elegir nodo").setItems(nn, (d, i) -> {
            final String node = nn[i];
            new AlertDialog.Builder(this).setTitle("Propiedad de " + node).setItems(PROPS,
                    (d2, k) -> target.setText(node + "." + PROPS[k])).show();
        }).show();
    }

    void enumChooser(final EditText target) {
        final LinkedHashMap<String, String[]> en = new LinkedHashMap<String, String[]>();
        LinkedHashSet<String> ps = new LinkedHashSet<String>();
        g.collectDecls(en, ps);
        if (en.isEmpty()) {
            toast("Aún no hay enums: crea uno con el bloque Enum");
            return;
        }
        final ArrayList<String> names = new ArrayList<String>(en.keySet());
        new AlertDialog.Builder(this).setTitle("Elegir enum").setItems(names.toArray(new String[0]), (d, i) -> {
            final String nm = names.get(i);
            final String[] vals = en.get(nm);
            new AlertDialog.Builder(this).setTitle(nm).setItems(vals,
                    (d2, k) -> target.setText(nm + "." + vals[k])).show();
        }).show();
    }

    void pickValue(final EditText e, String kind) {
        if (kind.equals("R")) {
            varChooser(e, true);
            return;
        }
        if (kind.equals("Q")) {
            varChooser(e, false);
            return;
        }
        if (kind.equals("M")) {
            msgChooser(e);
            return;
        }
        if (kind.equals("F")) {
            ArrayList<Game.Node> owners = new ArrayList<Game.Node>(g.nodes);
            owners.add(g.autoload);
            pickFunction(e, owners);
            return;
        }
        final String[] cats = {"Una variable", "Propiedad de un nodo (x, y, visible…)", "Valor de un enum",
            "true", "false"};
        new AlertDialog.Builder(this).setTitle("Insertar").setItems(cats, (d, i) -> {
            if (i == 0) varChooser(e, false);
            else if (i == 1) propChooser(e);
            else if (i == 2) enumChooser(e);
            else e.setText(i == 3 ? "true" : "false");
        }).show();
    }

    void pickFunction(EditText field, Iterable<Game.Node> nodes) {
        TreeSet<String> names = funcNames(nodes);
        if (names.isEmpty()) toast("No hay funciones en el nodo o escena seleccionados");
        else listChooser("Elegir función", names, null, field);
    }

    // Editor de listas separadas por coma: variables a guardar (L) o valores de un enum (E)
    View listEditor(final String[] holder, final boolean fromVars) {
        if (!fromVars) return enumEditor(holder);
        LinearLayout box = vbox();
        final TextView tv = new TextView(this);
        tv.setTextSize(15);
        tv.setPadding(0, dp(6), 0, dp(6));
        tv.setText(holder[0].isEmpty() ? "(vacío)" : holder[0]);
        box.addView(tv);
        box.addView(btn(fromVars ? "＋ Agregar variable" : "＋ Agregar valor", x -> {
            if (fromVars) {
                final String[] items = variableNames().toArray(new String[0]);
                if (items.length == 0) {
                    toast("Primero crea variables con el bloque Variable");
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Elegir variable").setItems(items, (d, i) -> {
                    holder[0] = addItem(holder[0], items[i]);
                    tv.setText(holder[0]);
                }).show();
            } else {
                inputDialog("Nuevo valor", s -> {
                    holder[0] = addItem(holder[0], s.replaceAll("[^A-Za-z0-9_]", "_"));
                    tv.setText(holder[0]);
                });
            }
        }));
        box.addView(btn("Quitar el último", x -> {
            int k = holder[0].lastIndexOf(',');
            holder[0] = k < 0 ? "" : holder[0].substring(0, k);
            tv.setText(holder[0].isEmpty() ? "(vacío)" : holder[0]);
        }));
        return box;
    }

    static String addItem(String cur, String item) {
        for (String s : cur.split(",")) if (s.equals(item)) return cur;
        return cur.isEmpty() ? item : cur + "," + item;
    }

    // ================= Elegir posición visualmente =================
    class PickView extends View {
        Bitmap bm;
        float gw, gh, gx, gy, dx, dy;
        TextView info;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF r = new RectF();
        final Bitmap nb;
        final Game.AssetOrder imageOrder;
        final RectF cropped = new RectF();

        PickView(Context c, Bitmap bm, float gw, float gh, float x, float y, TextView info, Bitmap nb, Game.AssetOrder imageOrder) {
            super(c);
            this.bm = bm;
            this.gw = gw;
            this.gh = gh;
            this.gx = x;
            this.gy = y;
            this.info = info;
            this.nb = nb;
            this.imageOrder = imageOrder;
            upd();
        }

        RectF croppedPreview(float s, float ox, float oy) {
            cropped.set(ox + (gx + gw * imageOrder.left) * s, oy + (gy + gh * imageOrder.top) * s,
                    ox + (gx + gw * imageOrder.right) * s, oy + (gy + gh * imageOrder.bottom) * s);
            return cropped;
        }

        float scale() {
            return Math.min(getWidth() / (float) g.vw, getHeight() / (float) g.vh);
        }

        float offX() {
            return (getWidth() - g.vw * scale()) / 2f;
        }

        float offY() {
            return (getHeight() - g.vh * scale()) / 2f;
        }

        void upd() {
            info.setText("X: " + Math.round(gx) + "    Y: " + Math.round(gy));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(0xFF111111);
            float s = scale(), ox = offX(), oy = offY();
            r.set(ox, oy, ox + g.vw * s, oy + g.vh * s);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF202020);
            c.drawRect(r, p);
            c.drawBitmap(bm, null, r, null);
            r.set(ox + gx * s, oy + gy * s, ox + (gx + gw) * s, oy + (gy + gh) * s);
            if (nb != null) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                if (imageOrder == null) c.drawBitmap(nb, null, r, p);
                else c.drawBitmap(nb, null, croppedPreview(s, ox, oy), p);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x66FF00FF);
                c.drawRect(r, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(0xFFFFFFFF);
            c.drawRect(r, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float s = scale();
            float vx = (e.getX() - offX()) / s, vy = (e.getY() - offY()) / s;
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                if (vx >= gx && vx <= gx + gw && vy >= gy && vy <= gy + gh) {
                    dx = vx - gx;
                    dy = vy - gy;
                } else {
                    dx = gw / 2f;
                    dy = gh / 2f;
                    gx = vx - dx;
                    gy = vy - dy;
                }
            } else if (a == MotionEvent.ACTION_MOVE) {
                gx = vx - dx;
                gy = vy - dy;
            }
            upd();
            invalidate();
            return true;
        }
    }

    void showPicker(Bitmap bm, boolean fromGame, float gw, float gh, float x0, float y0, Bitmap nb, Game.AssetOrder imageOrder, final Xy cb) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
        LinearLayout root = vbox();
        root.setBackgroundColor(0xFF121212);
        TextView head = new TextView(this);
        head.setText(fromGame ? "Última partida: arrastra el recuadro a su lugar"
                : "Escena del editor (aún no hay partida guardada): arrastra el recuadro");
        head.setPadding(dp(12), dp(16), dp(12), dp(8));
        root.addView(head);
        final TextView info = new TextView(this);
        info.setPadding(dp(12), dp(8), dp(12), dp(8));
        final PickView pk = new PickView(this, bm, gw, gh, x0, y0, info, nb, imageOrder);
        root.addView(pk, new LinearLayout.LayoutParams(MP, 0, 1));
        root.addView(info);
        LinearLayout row = new LinearLayout(this);
        row.addView(btn("✕ Cancelar", x -> d.dismiss()),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(btn("✓ Aceptar", x -> {
            cb.got(pk.gx, pk.gy);
            d.dismiss();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row);
        d.setContentView(root);
        d.show();
    }

    void pickPosition(String nodeName, final EditText ex, final EditText ey) {
        Game.Node tn = nodeName.equals("self") ? scriptOwner : g.byName(nodeName);
        float gw = tn != null ? tn.w : 100f, gh = tn != null ? tn.h : 100f;
        float px = 0, py = 0;
        if (tn != null && tn.inherit && tn.parent >= 0) {
            Game.Node pn = g.byId(tn.parent);
            if (pn != null) {
                px = pn.wx;
                py = pn.wy;
            }
        }
        final float fox = px, foy = py;
        float cx = tn != null ? tn.wx : (g.vw - gw) / 2f;
        float cy = tn != null ? tn.wy : (g.vh - gh) / 2f;
        String tx = ex.getText().toString().trim(), ty = ey.getText().toString().trim();
        try {
            if (!tx.isEmpty()) cx = Float.parseFloat(tx) + fox;
        } catch (Exception e) {
        }
        try {
            if (!ty.isEmpty()) cy = Float.parseFloat(ty) + foy;
        } catch (Exception e) {
        }
        Bitmap bm = null;
        boolean fromGame = false;
        File last = new File(dir, "last.png");
        if (last.exists()) {
            bm = BitmapFactory.decodeFile(last.getPath());
            fromGame = bm != null;
        }
        if (bm == null) bm = g.snapshot(960);
        String nbName = tn == null ? "" : Game.visualImage(tn);
        Bitmap preview = nbName.isEmpty() ? null : g.img(nbName);
        showPicker(bm, fromGame, gw, gh, cx, cy, preview, g.assetOrders.get(nbName), (rx, ry) -> {
            ex.setText(trimf(rx - fox));
            ey.setText(trimf(ry - foy));
        });
    }

    // ================= Editor de bloques =================
    static String[] specOf(String t) {
        for (String[] s : SPEC) if (s[0].equals(t)) return s;
        return new String[]{t, t, "S"};
    }

    static boolean container(Game.Blk b) {
        String t = b.t;
        return specOf(t)[2].equals("E") || t.equals("match") || t.equals("case") || t.equals("default")
                || t.equals("if") || t.equals("elif") || t.equals("else")
                || t.equals("while") || t.equals("repeat") || t.equals("forever")
                || (t.equals("tween") && (b.b("after") || !b.body.isEmpty()));
    }

    static boolean control(String t) {
        return t.equals("if") || t.equals("elif") || t.equals("else") || t.equals("while")
                || t.equals("match") || t.equals("case") || t.equals("default") || t.equals("repeat") || t.equals("forever") || t.equals("wait") || t.equals("break")
                || t.equals("breakrep");
    }

    String describe(Game.Blk b) {
        if (b.t.equals("func")) return "Función " + functionSignature(b);
        if (isCall(b)) {
            boolean exact = b.t.equals("callNode");
            String prefix = exact ? (b.s("node").isEmpty() ? "self" : b.s("node")) + "." : "";
            String args = Game.callOption(b, "passArgs", "args") ? b.s("args") : "";
            String ret = !b.b("par") && Game.callOption(b, "saveResult", "ret") ? b.s("ret") : "";
            return "Invocar " + prefix + b.s("name") + "(" + args + ")"
                    + (b.b("par") ? " · en paralelo" : ret.isEmpty() ? "" : " → " + ret);
        }
        String[] sp = specOf(b.t);
        if (b.t.equals("if") || b.t.equals("elif") || b.t.equals("while")) {
            StringBuilder sc = new StringBuilder(sp[1] + "  " + (b.f.containsKey("expr") ? b.s("expr") : oldCond(b)));
            if (!b.s("prob").trim().isEmpty()) sc.append("  ·  prob ").append(b.s("prob"));
            if (pf(b.s("tsec"), 0) > 0) sc.append("  ·  por ").append(b.s("tsec")).append(" s");
            if (!b.s("touch").isEmpty()) sc.append("  ·  ").append(touchText(b.s("touch")));
            return sc.toString();
        }
        if (b.t.equals("var")) {
            return "Variable  " + b.s("name") + " (" + b.s("vtype") + ", " + b.s("scope") + ") = " + b.s("val");
        }
        StringBuilder sb = new StringBuilder(sp[1]);
        if (b.t.equals("tween") && (b.b("after") || !b.body.isEmpty())) sb.append(" · Al terminar");
        boolean first = true;
        for (int i = 3; i < sp.length; i++) {
            String v = b.s(sp[i].split(":", 4)[0]);
            if (v.isEmpty()) continue;
            sb.append(first ? ":  " : "  ·  ").append(v);
            first = false;
        }
        return sb.toString();
    }

    Game.Blk newBlk(String t) {
        Game.Blk b = new Game.Blk(t);
        String[] sp = specOf(t);
        for (int i = 3; i < sp.length; i++) {
            String[] p = sp[i].split(":", 4);
            if (p[2].equals("O")) b.f.put(p[0], p[3].split("\\|")[0]);
            else if (p[2].equals("G")) b.f.put(p[0], "self");
            else if (p[2].equals("B")) b.f.put(p[0], "false");
            else if (p[2].equals("Y")) b.f.put(p[0], "int");
        }
        return b;
    }

    String[] nodeNames() {
        ArrayList<String> l = new ArrayList<String>();
        l.add("self");
        if (!g.autoload.script.isEmpty() && scriptOwner != g.autoload && g.byName(g.autoload.name) == null) l.add(g.autoload.name);
        for (Game.Node n : g.nodes) l.add(n.name);
        return l.toArray(new String[0]);
    }

    String[] withEmpty(String[] a) {
        String[] r = new String[a.length + 1];
        r[0] = "(ninguno)";
        System.arraycopy(a, 0, r, 1, a.length);
        return r;
    }

    static String normNum(String s) {
        s = s.trim();
        if (s.matches("-?\\d+")) return s + ".0";
        return s;
    }

    String[] compatibleNodeNames(String types) {
        java.util.List<String> accepted = java.util.Arrays.asList(types.split("\\|"));
        ArrayList<String> names = new ArrayList<String>();
        if (scriptOwner != null && accepted.contains(scriptOwner.type)) names.add("self");
        for (Game.Node node : g.nodes) if (accepted.contains(node.type)) names.add(node.name);
        if (names.isEmpty()) names.add("(sin nodos compatibles)");
        return names.toArray(new String[0]);
    }

    void openScript(final Game.Node n) {
        scriptOwner = n;
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
        LinearLayout root = vbox();
        root.setBackgroundColor(0xFF121212);
        root.addView(title("  Script de " + n.name));
        if (n == g.autoload) root.addView(label("Al iniciar se ejecuta una vez antes de la primera escena. "
                + "Las funciones y variables del nodo Autoload quedan disponibles al cambiar de escena. "
                + "Las tareas pendientes se reinician con cada escena."));
        final LinearLayout box = vbox();
        box.setPadding(0, 0, 0, dp(24));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(MP, 0, 1));
        final Runnable[] ref = new Runnable[1];
        ref[0] = () -> {
            box.removeAllViews();
            render(box, n.script, 0, ref[0], true);
            box.addView(btn("＋ Agregar evento", x -> pickBlock(n.script, true, ref[0])));
        };
        root.addView(btn("Cerrar", x -> {
            d.dismiss();
        }));
        d.setOnDismissListener(dialog -> {
            save();
            rebuildProps();
        });
        ref[0].run();
        d.setContentView(root);
        d.show();
    }

    TextView row(int depth, String text, int bg, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setPadding(dp(12 + depth * 22), dp(10), dp(8), dp(10));
        t.setBackgroundColor(bg);
        if (l != null) t.setOnClickListener(l);
        return t;
    }

    void render(LinearLayout box, final ArrayList<Game.Blk> list, int depth, final Runnable ref, boolean top) {
        for (int i = 0; i < list.size(); i++) {
            final Game.Blk b = list.get(i);
            final int idx = i;
            boolean ev = specOf(b.t)[2].equals("E");
            int color = ev ? 0x44FFAA00 : control(b.t) ? 0x3300AAFF : 0x22FFFFFF;
            box.addView(row(depth, (ev ? "▶ " : "") + describe(b), color, x -> blockMenu(list, idx, ref)));
            if (container(b)) {
                if (b.t.equals("tween")) {
                    box.addView(row(depth + 1, "Al terminar:", 0, null));
                    render(box, b.body, depth + 2, ref, false);
                    box.addView(row(depth + 2, "＋ bloque", 0, x -> pickBlock(b.body, false, ref)));
                    box.addView(row(depth + 1, "− Quitar Al terminar", 0,
                            x -> { b.body.clear(); b.f.remove("after"); ref.run(); }));
                } else {
                    if (b.t.equals("hold")) box.addView(row(depth + 1, "Si sigue presionado:", 0, null));
                    render(box, b.body, depth + 1, ref, false);
                    box.addView(row(depth + 1, b.t.equals("match") ? "＋ caso / por defecto" : "＋ bloque", 0,
                            x -> pickBlock(b.body, false, ref, b.t.equals("match"))));
                }
                if (b.t.equals("hold")) {
                    box.addView(row(depth + 1, "Si no (al soltar):", 0, null));
                    render(box, b.els, depth + 1, ref, false);
                    box.addView(row(depth + 1, "＋ bloque", 0, x -> pickBlock(b.els, false, ref)));
                }
            } else if (b.t.equals("tween")) {
                box.addView(row(depth + 1, "＋ Al terminar", 0, x -> { b.f.put("after", "true"); ref.run(); }));
            }
        }
    }

    void blockMenu(final ArrayList<Game.Blk> list, final int idx, final Runnable ref) {
        String[] items = {"Editar", "Subir", "Bajar", "Eliminar"};
        new AlertDialog.Builder(this).setItems(items, (d, w) -> {
            Game.Blk b = list.get(idx);
            if (w == 0) {
                editBlock(b, ref);
            } else if (w == 1 && idx > 0) {
                Collections.swap(list, idx, idx - 1);
                ref.run();
            } else if (w == 2 && idx < list.size() - 1) {
                Collections.swap(list, idx, idx + 1);
                ref.run();
            } else if (w == 3) {
                list.remove(idx);
                ref.run();
            }
        }).show();
    }

    void pickBlock(final ArrayList<Game.Blk> target, boolean events, final Runnable ref) {
        pickBlock(target, events, ref, false);
    }

    void pickBlock(final ArrayList<Game.Blk> target, boolean events, final Runnable ref, boolean inMatch) {
        final ArrayList<String> types = new ArrayList<String>();
        ArrayList<String> labels = new ArrayList<String>();
        for (String[] s : SPEC) {
            if (Game.collisionEvent(s[0]) && (scriptOwner == null || !scriptOwner.type.equals("Collision"))) continue;
            if (scriptOwner == g.autoload && (s[0].equals("press") || s[0].equals("hold") || s[0].equals("vend"))) continue;
            boolean cs = s[0].equals("case") || s[0].equals("default");
            if (s[2].equals("E") == events && cs == inMatch) {
                types.add(s[0]);
                labels.add(s[1]);
            }
        }
        if (inMatch) labels.add("＋ Un caso por cada fase de un enum…");
        new AlertDialog.Builder(this).setTitle(events ? "Evento" : "Bloque")
                .setItems(labels.toArray(new String[0]), (d, i) -> {
                    if (i == types.size()) {
                        casesFromEnum(target, ref);
                        return;
                    }
                    Game.Blk b = newBlk(types.get(i));
                    target.add(b);
                    ref.run();
                    if (specOf(b.t).length > 3) editBlock(b, ref);
                }).show();
    }

    static boolean isCall(Game.Blk block) {
        return block.t.equals("call") || block.t.equals("callNode");
    }

    static String functionSignature(Game.Blk fn) {
        String mode = fn.s("returnMode");
        String result = Game.FN_VOID.equals(mode) ? "void" : Game.FN_VALUE.equals(mode) ? "valor" : "auto";
        return fn.s("name").trim() + "(" + fn.s("params").trim() + ") → " + result;
    }

    static Game.Blk declaredFunction(Game.Node owner, String name) {
        if (owner != null && !name.isEmpty()) for (Game.Blk fn : owner.script) {
            if (fn.t.equals("func") && fn.s("name").trim().equals(name)) return fn;
        }
        return null;
    }

    Game.Node chosenFunctionNode(Spinner node) {
        String name = String.valueOf(node.getSelectedItem());
        if (name.equals("self")) return scriptOwner;
        Game.Node owner = g.byName(name);
        return owner == null && name.equals(g.autoload.name) ? g.autoload : owner;
    }

    Game.Blk callSignature(Spinner node, String name) {
        if (node != null) return declaredFunction(chosenFunctionNode(node), name);
        Game.Blk fn = declaredFunction(scriptOwner, name);
        if (fn == null) fn = declaredFunction(g.autoload, name);
        if (fn != null) return fn;
        for (Game.Node owner : g.nodes) {
            fn = declaredFunction(owner, name);
            if (fn != null) return fn;
        }
        return null;
    }

    static boolean hasValueReturn(ArrayList<Game.Blk> body) {
        for (Game.Blk block : body) {
            if (block.t.equals("return") && !block.s("val").trim().isEmpty()) return true;
            if (hasValueReturn(block.body) || hasValueReturn(block.els)) return true;
        }
        return false;
    }

    void editCall(Game.Blk block, Runnable ref) {
        LinearLayout form = vbox();
        form.setPadding(dp(20), dp(10), dp(20), dp(10));
        final Spinner node;
        if (block.t.equals("callNode")) {
            ArrayList<String> names = new ArrayList<String>(java.util.Arrays.asList(nodeNames()));
            String current = block.s("node").trim();
            if (current.isEmpty()) current = "self";
            if (!names.contains(current)) names.add(current);
            node = spinner(names.toArray(new String[0]));
            node.setSelection(names.indexOf(current), false);
            form.addView(label("Nodo que tiene la función"));
            form.addView(node);
        } else node = null;

        final Runnable[] refresh = {() -> { }};
        EditText name = field(form, "Función", block.s("name"), TXT, s -> refresh[0].run());
        form.addView(btn("Elegir función", x -> {
            ArrayList<Game.Node> owners = new ArrayList<Game.Node>();
            if (node == null) {
                owners.add(g.autoload);
                owners.addAll(g.nodes);
            } else {
                Game.Node chosen = chosenFunctionNode(node);
                if (chosen != null) owners.add(chosen);
            }
            ArrayList<String> choices = new ArrayList<String>(funcNames(owners));
            if (choices.isEmpty()) {
                toast("Primero crea una función en el script");
                return;
            }
            String[] labels = new String[choices.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = functionSignature(callSignature(node, choices.get(i)));
            new AlertDialog.Builder(this).setTitle("Elegir función").setItems(labels, (d, i) -> {
                name.setText(choices.get(i));
            }).show();
        }));
        TextView signature = label("");
        form.addView(signature);

        CheckBox pass = check(form, "Pasar argumentos", Game.callOption(block, "passArgs", "args"), s -> refresh[0].run());
        LinearLayout argsBox = vbox();
        EditText args = field(argsBox, "Valores en el orden de los parámetros (separados por coma)", block.s("args"), TXT, s -> { });
        form.addView(argsBox);
        CheckBox parallel = check(form, "Ejecutar en paralelo (sin esperar)", block.b("par"), s -> refresh[0].run());
        CheckBox save = check(form, "Guardar resultado", Game.callOption(block, "saveResult", "ret"), s -> refresh[0].run());
        LinearLayout resultBox = vbox();
        EditText result = field(resultBox, "Variable de destino", block.s("ret"), TXT, s -> { });
        resultBox.addView(btn("Elegir variable", x -> pickValue(result, "Q")));
        form.addView(resultBox);

        refresh[0] = () -> {
            String named = name.getText().toString().trim();
            Game.Blk fn = callSignature(node, named);
            String params = fn == null ? "" : fn.s("params").trim();
            boolean acceptsArgs = fn == null || !Game.parameters(fn).isEmpty();
            boolean returnsValue = fn == null || (!Game.FN_VOID.equals(fn.s("returnMode"))
                    && (Game.FN_VALUE.equals(fn.s("returnMode")) || hasValueReturn(fn.body)));
            boolean canSave = returnsValue && !parallel.isChecked();
            signature.setText(fn != null ? functionSignature(fn) : named.isEmpty() ? "Elige la función que quieres ejecutar."
                    : "No encontré esa función en el editor. Comprueba el nombre y el nodo.");
            args.setHint(params);
            if (returnsValue && parallel.isChecked()) signature.append("\nLas llamadas en paralelo no guardan el resultado en el bloque que las invoca.");
            pass.setVisibility(acceptsArgs ? View.VISIBLE : View.GONE);
            argsBox.setVisibility(acceptsArgs && pass.isChecked() ? View.VISIBLE : View.GONE);
            save.setVisibility(canSave ? View.VISIBLE : View.GONE);
            resultBox.setVisibility(canSave && save.isChecked() ? View.VISIBLE : View.GONE);
        };
        if (node != null) node.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a, View v, int pos, long id) { refresh[0].run(); }
            @Override public void onNothingSelected(AdapterView<?> a) { refresh[0].run(); }
        });
        refresh[0].run();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        new AlertDialog.Builder(this).setTitle(specOf(block.t)[1]).setView(scroll)
                .setPositiveButton("Aceptar", (d, w) -> {
                    if (node != null) block.f.put("node", String.valueOf(node.getSelectedItem()));
                    block.f.put("name", name.getText().toString().trim());
                    block.f.put("passArgs", String.valueOf(pass.getVisibility() == View.VISIBLE && pass.isChecked()));
                    block.f.put("args", args.getText().toString());
                    block.f.put("par", String.valueOf(parallel.isChecked()));
                    block.f.put("saveResult", String.valueOf(save.getVisibility() == View.VISIBLE && save.isChecked()));
                    block.f.put("ret", result.getText().toString().trim());
                    ref.run();
                }).setNegativeButton("Cancelar", null).show();
    }

    void editBlock(final Game.Blk b, final Runnable ref) {
        if (isCall(b)) {
            editCall(b, ref);
            return;
        }
        final String[] sp = specOf(b.t);
        if (sp.length <= 3) {
            toast("Este bloque no tiene ajustes");
            return;
        }
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        final ArrayList<View> views = new ArrayList<View>();
        final ArrayList<String> keys = new ArrayList<String>();
        final ArrayList<String> kinds = new ArrayList<String>();
        final ArrayList<String[]> holders = new ArrayList<String[]>();
        final boolean isCond = b.t.equals("if") || b.t.equals("elif") || b.t.equals("while") || b.t.equals("speed");
        for (int i = 3; i < sp.length; i++) {
            String[] p = sp[i].split(":", 4);
            final String key = p[0], lab = p[1], kind = p[2];
            if (isCond && key.equals("expr")) continue;
            String opt = p.length > 3 ? p[3] : "";
            String cur = b.s(key);
            v.addView(label(lab));
            View w;
            String[] holder = null;
            if (kind.equals("L") || kind.equals("E")) {
                holder = new String[]{cur};
                w = listEditor(holder, kind.equals("L"));
                v.addView(w);
            } else if (kind.equals("T") || kind.equals("N") || kind.equals("V") || kind.equals("R")
                    || kind.equals("Q") || kind.equals("M") || kind.equals("F")) {
                final EditText e = new EditText(this);
                e.setInputType(TXT);
                e.setText(cur);
                w = e;
                if (kind.equals("T")) {
                    v.addView(e);
                } else {
                    LinearLayout r = new LinearLayout(this);
                    r.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    r.addView(btn("▼", x -> {
                        if (kind.equals("F") && b.t.equals("funcExists")) {
                            Game.Node owner = chosenFunctionNode((Spinner) views.get(keys.indexOf("node")));
                            pickFunction(e, owner == null ? Collections.emptyList() : Collections.singletonList(owner));
                        } else pickValue(e, kind);
                    }));
                    v.addView(r);
                }
            } else if (kind.equals("B")) {
                CheckBox c = new CheckBox(this);
                c.setText("Sí");
                c.setChecked("true".equals(cur));
                w = c;
                v.addView(c);
            } else {
                String[] opts;
                if (kind.equals("G")) opts = opt.isEmpty() ? nodeNames() : compatibleNodeNames(opt);
                else if (kind.equals("H")) {
                    ArrayList<String> targets = new ArrayList<String>();
                    targets.add("(ninguno)");
                    for (Game.Node n : g.nodes) if (n != scriptOwner) targets.add(n.name);
                    opts = targets.toArray(new String[0]);
                } else if (kind.equals("I")) opts = withEmpty(assets(true));
                else if (kind.equals("A")) opts = withEmpty(assets(false));
                else if (kind.equals("W")) opts = withEmpty(videoAssets());
                else if (kind.equals("C")) opts = sceneNames();
                else if (kind.equals("Y")) opts = typeNames();
                else if (kind.equals("Z")) opts = withEmpty(enumNames());
                else opts = opt.split("\\|");
                Spinner s = spinner(opts);
                if (b.t.equals("func") && key.equals("returnMode") && cur.isEmpty()) s.setSelection(opts.length - 1);
                else for (int k = 0; k < opts.length; k++) if (opts[k].equals(cur)) s.setSelection(k);
                w = s;
                v.addView(s);
            }
            views.add(w);
            keys.add(key);
            kinds.add(kind);
            holders.add(holder);
        }
        if (b.t.equals("func")) v.addView(label("Si solo realiza una acción, elige Sin retorno (void). "
                + "Usa Salir de la función para terminar antes. Puedes declarar valores por defecto, por ejemplo: distancia=30, rápido=false."));
        if (b.t.equals("visualClone")) {
            v.addView(label("No crea otro nodo, script ni colisión. Si compartes cambios, la copia sigue al original "
                    + "con el desplazamiento indicado; si no, conserva la apariencia y posición del momento."));
        }
        if (b.t.equals("play")) {
            v.addView(label("El video en reversa no reproduce audio. Su fluidez depende del formato y del dispositivo."));
        }
        if (b.t.equals("clone")) {
            v.addView(label("La copia aparece en el siguiente fotograma con los mismos ajustes, bloques y padre, "
                    + "pero sin los nodos hijos. Solo existe durante la ejecución de la escena (máximo "
                    + Game.MAX_SCENE_CLONES + " copias por escena)."));
        }
        if (b.t.equals("enumnext") || b.t.equals("enum")) {
            v.addView(btn("Información del enum", x -> {
                View w0 = views.get(keys.indexOf(b.t.equals("enum") ? "name" : "enum"));
                enumInfo(w0 instanceof Spinner ? String.valueOf(((Spinner) w0).getSelectedItem())
                        : ((EditText) w0).getText().toString().trim());
            }));
        }
        if (b.t.equals("pos") || b.t.equals("tween")) {
            v.addView(btn("Elegir posición visualmente", x -> {
                if (b.t.equals("tween")) {
                    Spinner sw = (Spinner) views.get(keys.indexOf("what"));
                    if (!String.valueOf(sw.getSelectedItem()).equals("mover")) {
                        toast("Cambia 'Qué cambia' a mover para elegir una posición");
                        return;
                    }
                }
                Spinner sn = (Spinner) views.get(keys.indexOf("node"));
                pickPosition(String.valueOf(sn.getSelectedItem()), (EditText) views.get(keys.indexOf("x")),
                        (EditText) views.get(keys.indexOf("y")));
            }));
        }
        final HashMap<String, String> backup = new HashMap<String, String>(b.f);
        if (isCond) {
            migrate(b);
            v.addView(label(b.t.equals("speed") ? "Mientras se cumpla (ej: pulsado; vacío = solo el tiempo)"
                    : "Condición (ej: como = 2 y restante > 0)"));
            final EditText ex = new EditText(this);
            ex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            ex.setText(b.s("expr"));
            ex.setSelection(ex.length());
            ex.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence q, int a1, int c, int d) {
                }

                public void onTextChanged(CharSequence q, int a1, int c, int d) {
                }

                public void afterTextChanged(Editable q) {
                    b.f.put("expr", q.toString());
                }
            });
            v.addView(ex);
            v.addView(btn("Lógica y condiciones", x -> logicMenu(b, ex)));
            v.addView(label("También: por(3) = durante 3 s · prob(0.5) = 50% · pulsado · dedo_dentro · dedo_fuera"));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        new AlertDialog.Builder(this).setTitle(sp[1]).setView(sv)
                .setPositiveButton("Aceptar", (d, wh) -> {
                    for (int i = 0; i < views.size(); i++) {
                        View w = views.get(i);
                        String kind = kinds.get(i);
                        String val;
                        if (holders.get(i) != null) {
                            val = holders.get(i)[0];
                        } else if (w instanceof EditText) {
                            val = ((EditText) w).getText().toString();
                            if (kind.equals("N")) val = normNum(val);
                        } else if (w instanceof CheckBox) {
                            val = ((CheckBox) w).isChecked() ? "true" : "false";
                        } else {
                            Spinner s = (Spinner) w;
                            val = String.valueOf(s.getSelectedItem());
                            if ((kind.equals("I") || kind.equals("A") || kind.equals("W") || kind.equals("Z")
                                    || kind.equals("H")) && s.getSelectedItemPosition() == 0) val = "";
                        }
                        b.f.put(keys.get(i), val);
                    }
                    ref.run();
                }).setNegativeButton("Cancelar", (dd, ww) -> {
                    b.f.clear();
                    b.f.putAll(backup);
                }).show();
    }

    static String touchText(String t) {
        if (t.equals("in")) return "dedo dentro";
        if (t.equals("out")) return "dedo fuera";
        if (t.equals("hold")) return "pulsado";
        return t.equals("swipe") ? "pasar dedo" : "";
    }

    // Bloques antiguos -> una sola condición escrita (incluye tiempo, probabilidad y tocar)
    void migrate(Game.Blk b) {
        StringBuilder sb = new StringBuilder(b.s("expr"));
        if (!b.f.containsKey("expr")) {
            int nc = (int) pf(b.s("nc"), 0);
            for (int i = 0; i <= nc; i++) {
                String k = i == 0 ? "" : "c" + i + "_";
                String l = qv(b.s(i == 0 ? "l" : k + "l")), r = qv(b.s(i == 0 ? "r" : k + "r"));
                String op = b.s(k + "op").isEmpty() ? "=" : b.s(k + "op");
                if (l.isEmpty() && r.isEmpty()) continue;
                String p = r.isEmpty() ? l : l + " " + op + " " + r;
                if (b.b(i == 0 ? "not" : k + "n")) p = "no ( " + p + " )";
                if (sb.length() > 0) sb.append(" y ");
                sb.append(p);
            }
            for (int i = 1; i <= nc; i++) for (String suf : new String[]{"l", "op", "r", "n"}) b.f.remove("c" + i + "_" + suf);
            for (String key : new String[]{"l", "op", "r", "not", "nc"}) b.f.remove(key);
        }
        String ts = b.s("tsec").trim(), pr = b.s("prob").trim(), tc = b.s("touch");
        String[] extra = {pf(ts, 0) > 0 ? "por(" + ts + ")" : "", pr.isEmpty() ? "" : "prob(" + pr + ")",
            tc.equals("hold") ? "pulsado" : tc.equals("out") ? "dedo_fuera" : tc.isEmpty() ? "" : "dedo_dentro"};
        for (String e : extra) {
            if (e.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" y ");
            sb.append(e);
        }
        for (String key : new String[]{"tsec", "prob", "touch"}) b.f.remove(key);
        b.f.put("expr", sb.toString());
    }

    static String qv(String s) {
        s = s.trim();
        return (s.matches(".*\\s.*") && !s.startsWith("\"")) ? "\"" + s + "\"" : s;
    }

    // Inserta texto donde está el cursor; con y=true añade "y" si lo anterior ya era un valor
    void ins(EditText e, String t, boolean y) {
        Editable ed = e.getText();
        int a = e.getSelectionStart(), c = e.getSelectionEnd();
        if (a < 0 || c < 0) a = c = ed.length();
        int s0 = Math.min(a, c), e0 = Math.max(a, c);
        String pre = s0 > 0 && !Character.isWhitespace(ed.charAt(s0 - 1)) ? " " : "";
        if (y) {
            ArrayList<String> tk = Game.tokens(ed.subSequence(0, s0).toString());
            if (!tk.isEmpty()) {
                String l = tk.get(tk.size() - 1);
                if (!(Game.isCmp(l) || Game.isAnd(l) || Game.isOr(l) || l.equals("(") || l.equals("no")
                        || l.equals("not") || l.equals("!"))) pre += "y ";
            }
        }
        boolean par = t.equals("()");
        String r = pre + t + (par ? "" : " ");
        ed.replace(s0, e0, r);
        e.setSelection(Math.min(ed.length(), par ? s0 + pre.length() + 1 : s0 + r.length()));
        e.requestFocus();
    }

    void ins(EditText e, String t) {
        ins(e, t, false);
    }

    // Los selectores de variable / propiedad / enum escriben en este campo; luego se inserta en la condición
    EditText proxy(final EditText ex) {
        final EditText p = new EditText(this);
        p.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int c, int d) {
            }

            public void afterTextChanged(Editable s) {
                if (s.length() > 0) ins(ex, s.toString());
            }
        });
        return p;
    }

    // Menú único: Operadores / Condiciones
    void logicMenu(final Game.Blk b, final EditText ex) {
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        v.addView(label("Lo que elijas se escribe en la condición, donde está el cursor."));
        final AlertDialog[] dl = new AlertDialog[1];
        final Runnable back = () -> logicMenu(b, ex);
        v.addView(btn("Operadores  ▸", x -> {
            dl[0].dismiss();
            final String[][] ops = {{"=", "igual a"}, {"!=", "distinto de"}, {"<", "menor que"}, {">", "mayor que"},
                {"<=", "menor o igual que"}, {">=", "mayor o igual que"}, {"y", "se cumplen las dos"},
                {"o", "se cumple alguna"}, {"no", "niega lo que sigue"}, {"()", "paréntesis"}};
            String[] it = new String[ops.length];
            for (int i = 0; i < it.length; i++) it[i] = ops[i][0] + "      " + ops[i][1];
            new AlertDialog.Builder(this).setTitle("Operadores").setItems(it, (d, i) -> ins(ex, ops[i][0]))
                    .setNegativeButton("Atrás", (d, w) -> back.run()).show();
        }));
        v.addView(btn("Condiciones  ▸", x -> {
            dl[0].dismiss();
            condsMenu(b, ex, back);
        }));
        dl[0] = new AlertDialog.Builder(this).setTitle("Lógica y condiciones").setView(v)
                .setNegativeButton("Cerrar", null).create();
        dl[0].show();
    }

    void condsMenu(final Game.Blk b, final EditText ex, final Runnable back) {
        final ArrayList<String> it = new ArrayList<String>();
        it.add("Una variable");
        it.add("Propiedad de un nodo (x, y, visible…)");
        it.add("Valor de un Enum");
        it.add("Texto…");
        it.add("Verdadero (true)");
        it.add("Falso (false)");
        if (!b.t.equals("while")) it.add("Tiempo  ▸");
        it.add("Probabilidad  ▸");
        it.add("Tocar  ▸");
        it.add("Colisión entre nodos  ▸");
        new AlertDialog.Builder(this).setTitle("Condiciones").setItems(it.toArray(new String[0]), (d, i) -> {
            String s = it.get(i);
            if (s.startsWith("Una")) varChooser(proxy(ex), false);
            else if (s.startsWith("Propiedad")) propChooser(proxy(ex));
            else if (s.startsWith("Valor")) enumChooser(proxy(ex));
            else if (s.startsWith("Texto")) inputDialog("Texto", t -> ins(ex, "\"" + t.replace("\"", "") + "\""));
            else if (s.startsWith("Verdadero")) ins(ex, "true");
            else if (s.startsWith("Falso")) ins(ex, "false");
            else if (s.startsWith("Tiempo")) numDialog(ex, "Tiempo", "Por", "segundos",
                    "La condición debe mantenerse cumplida todo ese tiempo.", "por", false, back);
            else if (s.startsWith("Prob")) numDialog(ex, "Probabilidad", "Con probabilidad", "(0 a 1)",
                    "De 0 a 1 con decimales: 0,5 = 50%, 0,9 = 90%, 1 = siempre.", "prob", true, back);
            else if (s.startsWith("Colisión")) collisionCondition(ex, back);
            else touchMenu(ex, back);
        }).setNegativeButton("Atrás", (d, w) -> back.run()).show();
    }

    void collisionCondition(EditText ex, Runnable back) {
        ArrayList<Game.Node> areas = new ArrayList<Game.Node>();
        ArrayList<String> names = new ArrayList<String>();
        for (Game.Node n : g.nodes) if (n.type.equals("Collision")) {
            areas.add(n);
            names.add(n.name);
        }
        if (areas.isEmpty()) {
            toast("Agrega un nodo Collision primero");
            back.run();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Elegir Collision").setItems(names.toArray(new String[0]), (d, i) -> {
            Game.Node area = areas.get(i);
            ArrayList<String> targets = new ArrayList<String>();
            for (Game.Node n : g.nodes) if (n != area) targets.add(n.name);
            AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("¿Con qué nodo?");
            if (targets.isEmpty()) dialog.setMessage("Agrega otro nodo para detectar el solapamiento.");
            else dialog.setItems(targets.toArray(new String[0]), (d2, j) -> ins(ex, "colisiona("
                    + collisionArg(area.name) + ", " + collisionArg(targets.get(j)) + ")", true));
            dialog.setNegativeButton("Atrás", (d2, w) -> back.run()).show();
        }).setNegativeButton("Atrás", (d, w) -> back.run()).show();
    }

    String collisionArg(String name) {
        return scriptOwner != null && name.equals(scriptOwner.name) ? "self" : org.json.JSONObject.quote(name);
    }

    // Cuadro "Por [X] tiempo" / "Con probabilidad [X]": escribe por(X) / prob(X) en la condición
    void numDialog(final EditText ex, String title, String pre, String post, String info, final String fn,
            final boolean prob, final Runnable back) {
        LinearLayout v = vbox();
        v.setPadding(dp(20), dp(10), dp(20), dp(10));
        v.addView(label(info));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(pre + " [");
        t1.setTextSize(18);
        final EditText e = new EditText(this);
        e.setInputType(NUM);
        e.setMinEms(4);
        TextView t2 = new TextView(this);
        t2.setText("] " + post);
        t2.setTextSize(18);
        row.addView(t1);
        row.addView(e);
        row.addView(t2);
        v.addView(row);
        new AlertDialog.Builder(this).setTitle(title).setView(v).setPositiveButton("Aceptar", (d, w) -> {
            float x = pf(e.getText().toString(), 0);
            if (prob) x = Math.max(0f, Math.min(1f, x));
            if (x > 0 || prob) ins(ex, fn + "(" + fmt1(x) + ")", true);
        }).setNegativeButton("Atrás", (d, w) -> back.run()).show();
    }

    void touchMenu(final EditText ex, final Runnable back) {
        final String[] it = {"Al pasar dedo  ▸", "Mantener pulsado"};
        new AlertDialog.Builder(this).setTitle("Tocar").setItems(it, (d, i) -> {
            if (i == 1) {
                ins(ex, "pulsado", true);
                return;
            }
            final String[] f = {"Dedo dentro del botón", "Dedo fuera"};
            new AlertDialog.Builder(this).setTitle("Al pasar dedo").setItems(f,
                    (d2, k) -> ins(ex, k == 0 ? "dedo_dentro" : "dedo_fuera", true))
                    .setNegativeButton("Atrás", (d2, w) -> touchMenu(ex, back)).show();
        }).setNegativeButton("Atrás", (d, w) -> back.run()).show();
    }

    String oldCond(Game.Blk b) {
        StringBuilder sc = new StringBuilder((b.b("not") ? "NOT " : "") + b.s("l") + " " + b.s("op") + " " + b.s("r"));
        int nc = (int) pf(b.s("nc"), 0);
        for (int i = 1; i <= nc; i++) {
            String k = "c" + i + "_";
            sc.append("  Y  ").append(b.b(k + "n") ? "NOT " : "").append(b.s(k + "l")).append(" ")
                    .append(b.s(k + "op")).append(" ").append(b.s(k + "r"));
        }
        return sc.toString();
    }

    static String join(ArrayList<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) sb.append(sb.length() > 0 ? "," : "").append(s);
        return sb.toString();
    }

    // Editor de valores de enum: tocar un valor para renombrar, mover o quitar
    View enumEditor(final String[] holder) {
        final LinearLayout box = vbox();
        final Runnable[] rf = new Runnable[1];
        rf[0] = () -> {
            box.removeAllViews();
            final ArrayList<String> vs = new ArrayList<String>();
            for (String s : holder[0].split(",")) if (!s.trim().isEmpty()) vs.add(s.trim());
            if (vs.isEmpty()) box.addView(label("(vacío)"));
            for (int i = 0; i < vs.size(); i++) {
                final int k = i;
                box.addView(row(0, i + "   " + vs.get(i), 0x22FFFFFF, x -> {
                    String[] it = {"Renombrar", "Subir", "Bajar", "Quitar"};
                    new AlertDialog.Builder(this).setTitle(vs.get(k)).setItems(it, (d, w) -> {
                        if (w == 0) {
                            inputDialog("Nuevo nombre", s -> {
                                vs.set(k, s.replaceAll("[^A-Za-z0-9_]", "_"));
                                holder[0] = join(vs);
                                rf[0].run();
                            });
                            return;
                        }
                        if (w == 1 && k > 0) Collections.swap(vs, k, k - 1);
                        else if (w == 2 && k < vs.size() - 1) Collections.swap(vs, k, k + 1);
                        else if (w == 3) vs.remove(k);
                        holder[0] = join(vs);
                        rf[0].run();
                    }).show();
                }));
            }
        };
        rf[0].run();
        box.addView(btn("＋ Agregar valores (separa con coma)", x -> inputDialog("Ej: Idle, Corre, Salta", s -> {
            ArrayList<String> vs = new ArrayList<String>();
            for (String q : holder[0].split(",")) if (!q.trim().isEmpty()) vs.add(q.trim());
            for (String q : s.split(",")) {
                q = q.trim().replaceAll("[^A-Za-z0-9_]", "_");
                if (!q.isEmpty() && !vs.contains(q)) vs.add(q);
            }
            holder[0] = join(vs);
            rf[0].run();
        })));
        return box;
    }
}

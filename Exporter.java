package com.mini.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Exporter {
    // Textos de relleno que están dentro de la plantilla (PlayerManifest.xml).
    // Se reemplazan por los valores reales con la MISMA longitud.
    static final String PKG_PH = "com.gx.placeholder00000000000000000000000000000000000000000000000000000000000000";
    // plantilla antigua (26 letras): sigue funcionando, pero con paquetes de hasta 26 letras
    static final String PKG_OLD = "com.gx.placeholder00000000";
    static final String LABEL_PH = "LABELPLACEHOLDERXXXXXXXXXXXXXXXX";
    static final String VER_PH = "VPLACEHOLDERXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
    static final String VER_OLD = "VPLACEHOLDER";

    static byte[] readAll(InputStream i) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = i.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    static String pad(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < n; i++) {
            char ch = s.charAt(i);
            sb.append(ch >= 32 && ch < 127 ? ch : '?');
        }
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }

    static byte[] rep(byte[] d, String from, String to, String enc) throws Exception {
        byte[] a = from.getBytes(enc);
        byte[] b = to.getBytes(enc);
        if (a.length != b.length) throw new Exception("Longitud inválida al reemplazar");
        for (int i = 0; i + a.length <= d.length; i++) {
            boolean ok = true;
            for (int j = 0; j < a.length; j++) {
                if (d[i + j] != a[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                System.arraycopy(b, 0, d, i, b.length);
                i += a.length - 1;
            }
        }
        return d;
    }

    static boolean has(byte[] d, String s, String enc) throws Exception {
        byte[] a = s.getBytes(enc);
        for (int i = 0; i + a.length <= d.length; i++) {
            int j = 0;
            while (j < a.length && d[i + j] == a[j]) j++;
            if (j == a.length) return true;
        }
        return false;
    }

    // El texto puede ser más corto que el relleno: se acorta la cadena dentro del manifiesto
    // (longitud + texto + fin de cadena) y el resto queda en ceros; nada más cambia de lugar.
    static byte[] setStr(byte[] d, String phNew, String phOld, String v, boolean strict) throws Exception {
        String ph = has(d, phNew, "UTF-8") || has(d, phNew, "UTF-16LE") ? phNew : phOld;
        if (v.length() > ph.length()) {
            if (strict) throw new Exception("Paquete demasiado largo (máximo " + ph.length() + " letras)");
            v = v.substring(0, ph.length());
        }
        if (v.length() == ph.length()) {
            d = rep(d, ph, v, "UTF-8");
            return rep(d, ph, v, "UTF-16LE");
        }
        d = shrink(d, ph, v, false);
        return shrink(d, ph, v, true);
    }

    static byte[] shrink(byte[] d, String ph, String pkg, boolean u16) throws Exception {
        String enc = u16 ? "UTF-16LE" : "UTF-8";
        byte[] a = ph.getBytes(enc), b = pkg.getBytes(enc);
        for (int i = 2; i + a.length <= d.length; i++) {
            int j = 0;
            while (j < a.length && d[i + j] == a[j]) j++;
            if (j < a.length) continue;
            if (u16) {
                if (((d[i - 2] & 0xff) | ((d[i - 1] & 0xff) << 8)) != ph.length()) throw new Exception("Manifiesto inesperado");
                d[i - 2] = (byte) pkg.length();
                d[i - 1] = 0;
            } else {
                if ((d[i - 2] & 0xff) != ph.length() || (d[i - 1] & 0xff) != ph.length()) throw new Exception("Manifiesto inesperado");
                d[i - 2] = (byte) pkg.length();
                d[i - 1] = (byte) pkg.length();
            }
            System.arraycopy(b, 0, d, i, b.length);
            for (int k = i + b.length; k < i + a.length + (u16 ? 2 : 1); k++) d[k] = 0;
            i += a.length - 1;
        }
        return d;
    }

    static byte[] patch(byte[] d, String pkg, String label, String ver) throws Exception {
        String lab = pad(label, LABEL_PH.length());
        String vv = pad(ver, ver.length()).trim().isEmpty() ? "1.0" : pad(ver, ver.length()).trim();
        d = setStr(d, PKG_PH, PKG_OLD, pkg, true);
        d = setStr(d, VER_PH, VER_OLD, vv, false);
        d = rep(d, LABEL_PH, lab, "UTF-8");
        d = rep(d, LABEL_PH, lab, "UTF-16LE");
        return d;
    }

    static boolean isImg(String n) {
        n = n.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp")
                || n.endsWith(".bmp");
    }

    // mode: 0 = WebP con pérdida mínima (85%), 1 = calidad máxima, 2 = no tocar.
    // Devuelve un temporal solo si la conversión ahorra espacio; null significa usar el original.
    // El muestreo ocurre antes de decodificar para limitar el pico de memoria en dispositivos antiguos.
    static File optimizedImage(File source, float[] size, boolean noDown, int mode, File cache) throws Exception {
        if (mode == 2 || !isImg(source.getName())) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        float scale = 1f;
        if (!noDown && size != null && size[0] > 0 && size[1] > 0) {
            scale = Math.min(1f, Math.max(size[0] / bounds.outWidth, size[1] / bounds.outHeight));
        }
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = 1;
        while (bounds.outWidth / (decode.inSampleSize * 2) >= 1
                && bounds.outHeight / (decode.inSampleSize * 2) >= 1
                && decode.inSampleSize * 2 <= 1f / Math.max(scale, 0.0001f)) {
            decode.inSampleSize *= 2;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(source.getPath(), decode);
        if (bitmap == null) return null;
        Bitmap scaled = bitmap;
        try {
            if (scale < 0.95f) {
                int width = Math.max(1, Math.round(bounds.outWidth * scale));
                int height = Math.max(1, Math.round(bounds.outHeight * scale));
                scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            Bitmap.CompressFormat format = Bitmap.CompressFormat.WEBP;
            if (Build.VERSION.SDK_INT >= 30) {
                // Evita referencias estáticas a enum values no presentes en Android 5-10.
                try {
                    format = Bitmap.CompressFormat.valueOf(mode == 1 ? "WEBP_LOSSLESS" : "WEBP_LOSSY");
                } catch (IllegalArgumentException ignored) { }
            }
            File converted = File.createTempFile("img-opt-", ".webp", cache);
            boolean ok;
            try (FileOutputStream stream = new FileOutputStream(converted)) {
                ok = scaled.compress(format, mode == 1 ? 100 : 85, stream);
                stream.flush();
            }
            if (ok && converted.length() > 0 && converted.length() < source.length()) return converted;
            converted.delete();
            return null;
        } finally {
            if (scaled != bitmap && !scaled.isRecycled()) scaled.recycle();
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    static void copy(InputStream in, java.io.OutputStream out) throws Exception {
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }

    static void putBytes(ZipOutputStream zip, String name, byte[] data) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    public static void build(Context c, String json, File adir, Set<String> used,
                             HashMap<String, float[]> sizes, boolean noDown, int imgMode,
                             byte[] icon, String name, String ver, String pkgIn, File out) throws Exception {
        byte[] md = MessageDigest.getInstance("MD5").digest(name.getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte b : md) hex.append(String.format("%02x", b));
        String pkg = pkgIn != null && !pkgIn.isEmpty() ? pkgIn : "com.gx.a" + hex.substring(0, PKG_OLD.length() - 8);

        File unsigned = new File(c.getCacheDir(), "unsigned.apk");
        if (unsigned.exists()) unsigned.delete();
        try (InputStream template = c.getAssets().open("template.apk");
             ZipInputStream zin = new ZipInputStream(template);
             ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(unsigned))) {
            zo.setLevel(9);
            ZipEntry entry;
            byte[] buffer = new byte[16384];
            while ((entry = zin.getNextEntry()) != null) {
                String path = entry.getName();
                if (entry.isDirectory() || path.startsWith("META-INF/")) continue;
                if (path.equals("AndroidManifest.xml")) {
                    byte[] manifest = patch(readAll(zin), pkg, name, ver);
                    putBytes(zo, path, manifest);
                } else if (icon != null && path.startsWith("res/") && path.endsWith("/ic.png")) {
                    putBytes(zo, path, icon);
                } else {
                    zo.putNextEntry(new ZipEntry(path));
                    int n;
                    while ((n = zin.read(buffer)) != -1) zo.write(buffer, 0, n);
                    zo.closeEntry();
                }
            }
            putBytes(zo, "assets/game.json", json.getBytes("UTF-8"));
            for (String asset : used) {
                File source = new File(adir, asset);
                if (!source.isFile()) continue;
                File converted = optimizedImage(source, sizes.get(asset), noDown, imgMode, c.getCacheDir());
                File chosen = converted == null ? source : converted;
                try (InputStream input = new FileInputStream(chosen)) {
                    zo.putNextEntry(new ZipEntry("assets/a/" + asset));
                    copy(input, zo);
                    zo.closeEntry();
                } finally {
                    if (converted != null) converted.delete();
                }
            }
        }
        try {
            Sign.sign(c, unsigned, out);
        } finally {
            unsigned.delete();
        }
    }
}

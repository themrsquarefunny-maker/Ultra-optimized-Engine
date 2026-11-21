package com.mini.engine;

import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Formato local compartido con herramientas/trim-assets.html. No se extraen rutas del ZIP.
final class AssetOrdersImport {
    static final String FORMAT = "motor-asset-orders-v1";
    static final int MAX_FILES = 256, MAX_MANIFEST = 256 * 1024, MAX_BYTES = 64 * 1024 * 1024;

    static LinkedHashMap<String, Game.AssetOrder> load(InputStream input, File assets) throws Exception {
        if (input == null) throw new IOException("No pude abrir el ZIP");
        ArrayList<File> staged = new ArrayList<File>(), installed = new ArrayList<File>();
        LinkedHashMap<String, File> files = new LinkedHashMap<String, File>();
        boolean success = false;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            byte[] buffer = new byte[16384];
            byte[] metadata = null;
            int total = 0, count = 0;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String key = entry.getName();
                if (++count > MAX_FILES + 1 || entry.isDirectory()) throw new IOException("Entradas ZIP no válidas");
                if (key.equals("ordenes.json")) {
                    if (metadata != null) throw new IOException("Órdenes duplicadas");
                    ByteArrayOutputStream payload = new ByteArrayOutputStream();
                    total += copy(zip, payload, buffer, Math.min(MAX_MANIFEST, MAX_BYTES - total));
                    metadata = payload.toByteArray();
                } else {
                    if (!key.matches("assets/[A-Za-z0-9_-]{1,90}\\.png") || files.containsKey(key)) {
                        throw new IOException("Archivo ZIP no válido: " + key);
                    }
                    File file = File.createTempFile("orden-", ".tmp", assets.getParentFile());
                    staged.add(file);
                    try (OutputStream out = new FileOutputStream(file)) {
                        total += copy(zip, out, buffer, MAX_BYTES - total);
                    }
                    files.put(key, file);
                }
            }
            if (metadata == null) throw new IOException("Falta ordenes.json; usa el ZIP de la herramienta de recorte");
            JSONObject manifest = new JSONObject(new String(metadata, StandardCharsets.UTF_8));
            if (!FORMAT.equals(manifest.optString("format"))) throw new IOException("Formato de órdenes no admitido");
            JSONArray orders = manifest.getJSONArray("assets");
            if (orders.length() == 0 || orders.length() != files.size()) throw new IOException("Las órdenes no coinciden con los PNG");

            Game.AssetOrder[] crops = new Game.AssetOrder[orders.length()];
            File[] sources = new File[orders.length()];
            String[] names = new String[orders.length()];
            for (int i = 0; i < orders.length(); i++) {
                JSONObject info = orders.getJSONObject(i);
                String path = info.getString("file"), name = info.getString("name");
                File file = files.remove(path);
                if (file == null || !name.matches("[A-Za-z0-9_-]{1,80}\\.png")) throw new IOException("Nombre de asset inválido");
                Game.AssetOrder order = new Game.AssetOrder(info);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), opts);
                if (!"image/png".equals(opts.outMimeType) || opts.outWidth != order.width || opts.outHeight != order.height) {
                    throw new IOException("El PNG no coincide con las coordenadas: " + name);
                }
                crops[i] = order;
                sources[i] = file;
                names[i] = name;
            }
            LinkedHashMap<String, Game.AssetOrder> result = new LinkedHashMap<String, Game.AssetOrder>();
            for (int i = 0; i < names.length; i++) {
                String name = names[i];
                // Reserva un archivo nuevo sin sobrescribir assets aunque otra importación se ejecute a la vez.
                File dest = new File(assets, name);
                for (int suffix = 1; !dest.createNewFile(); suffix++) dest = new File(assets, suffix + "_" + name);
                installed.add(dest);
                if (!sources[i].renameTo(dest)) throw new IOException("No pude guardar " + name);
                result.put(dest.getName(), crops[i]);
            }
            success = true;
            return result;
        } finally {
            for (File f : staged) f.delete();
            if (!success) for (File f : installed) f.delete();
        }
    }

    static int copy(InputStream in, OutputStream out, byte[] buffer, int limit) throws IOException {
        int total = 0, n;
        while ((n = in.read(buffer)) != -1) {
            if (n > limit - total) throw new IOException("El ZIP supera el tamaño permitido");
            out.write(buffer, 0, n);
            total += n;
        }
        return total;
    }
}

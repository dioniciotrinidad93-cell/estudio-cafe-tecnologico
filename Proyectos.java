package com.cafetecnologico.estudio;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Proyectos de video guardados. Cada proyecto es un archivo de texto (JSON) en el almacenamiento
 * privado de la aplicación: guarda direcciones de las imágenes y del audio, no copias de ellos.
 */
final class Proyectos {

    private Proyectos() {}

    static final class Item {
        final String nombre;
        final long fecha;

        Item(String nombre, long fecha) {
            this.nombre = nombre;
            this.fecha = fecha;
        }
    }

    private static File carpeta(Context c) {
        File d = new File(c.getFilesDir(), "proyectos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Quita los caracteres que no sirven en un nombre de archivo. */
    static String limpio(String s) {
        String n = s == null ? "" : s.trim().replaceAll("[\\\\/:*?\"<>|]", "-");
        while (n.endsWith(".")) n = n.substring(0, n.length() - 1).trim();
        if (n.length() > 80) n = n.substring(0, 80).trim();
        return n;
    }

    private static File archivo(Context c, String nombre) {
        return new File(carpeta(c), limpio(nombre) + ".json");
    }

    static boolean existe(Context c, String nombre) {
        return archivo(c, nombre).exists();
    }

    static List<Item> listar(Context c) {
        List<Item> r = new ArrayList<>();
        File[] l = carpeta(c).listFiles();
        if (l != null) {
            for (File f : l) {
                String n = f.getName();
                if (n.endsWith(".json")) {
                    r.add(new Item(n.substring(0, n.length() - 5), f.lastModified()));
                }
            }
        }
        Collections.sort(r, (a, b) -> Long.compare(b.fecha, a.fecha));
        return r;
    }

    static void guardar(Context c, String nombre, JSONObject j) throws Exception {
        File f = archivo(c, nombre);
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            os.write(j.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            if (!tmp.renameTo(f)) throw new Exception("No pude escribir el archivo del proyecto.");
        }
    }

    static JSONObject cargar(Context c, String nombre) throws Exception {
        File f = archivo(c, nombre);
        if (!f.exists()) throw new Exception("No encuentro ese proyecto.");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        }
        return new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
    }

    static void borrar(Context c, String nombre) {
        archivo(c, nombre).delete();
    }
}

package com.cafetecnologico.estudio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.graphics.Typeface;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Crea un video MP4 con un audio y una o varias imágenes fijas. Sin bibliotecas externas:
 * el video se codifica con MediaCodec (H.264) y se une con el audio (AAC) usando MediaMuxer.
 */
final class Video {

    private Video() {}

    static final int FPS = 5;
    /** Cuadros por segundo cuando hay zoom: el movimiento necesita más cuadros para verse suave. */
    static final int FPS_ZOOM = 24;
    /** Cuánto crece la imagen con el zoom: 0.2 es un 20 por ciento. */
    static final double ZOOM_MAX = 0.2;

    static final String[] NOMBRES_ZOOM = {
        "Sin zoom",
        "Acercar suavemente hacia el centro",
        "Alejar suavemente desde el centro"
    };

    static final String[] NOMBRES_FORMATO = {
        "Horizontal 16 a 9, para YouTube",
        "Vertical 9 a 16, para TikTok, Reels y Shorts",
        "Cuadrado 1 a 1"
    };
    static final int[][] TAMANOS = {{1280, 720}, {720, 1280}, {720, 720}};

    static final String[] NOMBRES_ENCUADRE = {
        "Rellenar todo el cuadro, recortando los bordes",
        "Imagen completa con fondo borroso",
        "Imagen completa con barras negras"
    };

    static final String[] NOMBRES_ROTULO_POS = {"Arriba", "En el centro", "Abajo"};
    static final String[] NOMBRES_ROTULO_TAM = {"Pequeño", "Mediano", "Grande"};

    static final String[] NOMBRES_FUENTE = {
        "Normal, letra clara y en negrita",
        "Clásica, con remates, como un libro o un periódico",
        "Máquina de escribir, todas las letras del mismo ancho",
        "Estrecha y alta, cabe más texto en cada línea",
        "Informal, estilo escrito a mano suelto",
        "Cursiva elegante, para dedicatorias y felicitaciones",
        "Extra gruesa, para títulos que impacten",
        "Delgada y ligera, discreta y moderna"
    };

    static final String[] NOMBRES_COLOR_TEXTO = {"Blanco", "Negro", "Amarillo", "Naranja", "Rojo",
        "Rosa", "Morado claro", "Azul", "Celeste", "Verde", "Dorado"};
    static final int[] COLORES_TEXTO = {0xFFFFFFFF, 0xFF000000, 0xFFFFD60A, 0xFFFF9500, 0xFFFF3B30,
        0xFFFF6FB5, 0xFFB57BFF, 0xFF4DA3FF, 0xFF7FDBFF, 0xFF34C759, 0xFFD4AF37};

    static final String[] NOMBRES_COLOR_FONDO = {"Negro", "Blanco", "Azul oscuro", "Rojo oscuro",
        "Verde oscuro", "Morado oscuro", "Gris oscuro", "Amarillo"};
    static final int[] COLORES_FONDO = {0xFF000000, 0xFFFFFFFF, 0xFF0B2A5B, 0xFF7A0C0C, 0xFF0E4D2B,
        0xFF3B1A6B, 0xFF333333, 0xFFFFD60A};

    /** Animaciones del texto sobre las imágenes (sugerencia de lizpalmy). */
    static final String[] NOMBRES_ANIM_TEXTO = {
        "Sin animación, el texto aparece fijo",
        "Aparecer con fundido",
        "Aparecer y desaparecer con fundido",
        "Subir desde abajo",
        "Entrar deslizándose desde la izquierda",
        "Escribir letra por letra, como máquina de escribir",
        "Crecer desde el centro"
    };
    static final int ANIM_ESCRIBIR = 5;
    static final int ANIM_FUNDIDO_SALIDA = 2;

    /** Transiciones visuales entre imágenes (sugerencia de Karime y de la comunidad). */
    static final String[] NOMBRES_TRANSICION_VISUAL = {
        "Sin transición, corte directo",
        "Fundido cruzado: una imagen se disuelve en la otra",
        "Fundido por negro: la imagen se oscurece y aparece la siguiente",
        "Deslizar: la imagen nueva empuja a la anterior hacia la izquierda",
        "Subir: la imagen nueva entra desde abajo",
        "Cortina: la imagen nueva se descubre de izquierda a derecha"
    };
    static final String[] NOMBRES_DURACION_TRANSICION = {
        "Rápida, medio segundo", "Normal, un segundo", "Lenta, un segundo y medio"};
    private static final double[] SEG_TRANSICION = {0.5, 1.0, 1.5};

    /** El destello de luz blanca que ilumina la imagen nueva cada vez que cambia. */
    static final String[] NOMBRES_FLASH = {"Sin destello", "Destello suave", "Destello normal",
        "Destello fuerte"};
    private static final float[] PICO_FLASH = {0f, 0.45f, 0.70f, 0.92f};
    private static final double DUR_FLASH = 0.30;

    /** Una imagen elegida por la persona. */
    static final class Imagen {
        Uri uri;
        String nombre;
        int ancho;
        int alto;
        double nitidez;
        int seg; // duración en segundos si se personaliza
        int zoom; // 0 sin zoom, 1 acercar, 2 alejar
        String descripcion = ""; // descripción hecha con Gemini
        String rotulo = ""; // texto que se dibuja sobre la imagen (título, dedicatoria...)
        int rotuloPos = 2; // 0 arriba, 1 en el centro, 2 abajo
        int rotuloTam = 1; // 0 pequeño, 1 mediano, 2 grande
        boolean rotuloFondo = true; // franja detrás del texto
        int rotuloFuente = 0; // ver NOMBRES_FUENTE
        int rotuloColor = 0; // color del texto, ver COLORES_TEXTO
        int rotuloFondoColor = 0; // color de la franja, ver COLORES_FONDO
        int rotuloAnim = 0; // animación del texto, ver NOMBRES_ANIM_TEXTO

        /** Aviso sobre la calidad de la imagen, o cadena vacía si se ve bien. */
        String aviso() {
            StringBuilder sb = new StringBuilder();
            if (Math.max(ancho, alto) < 720) {
                sb.append("Es pequeña y puede verse borrosa en el video. ");
            }
            if (nitidez >= 0 && nitidez < 30) {
                sb.append("Parece desenfocada o con muy poco detalle. ");
            }
            return sb.toString().trim();
        }
    }

    // ---------- Lectura de imágenes ----------

    private static int giroExif(Context c, Uri u) {
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            if (in == null) return 0;
            ExifInterface e = new ExifInterface(in);
            int o = e.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /** Carga una imagen ya girada, con su lado largo cerca de maxLado (sin pasarse del doble). */
    static Bitmap cargar(Context c, Uri u, int maxLado) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            if (in == null) throw new Exception("No pude abrir la imagen.");
            BitmapFactory.decodeStream(in, null, o);
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) {
            throw new Exception("No pude leer una de las imágenes. Puede que no sea un formato "
                    + "compatible.");
        }
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxLado) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b;
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            if (in == null) throw new Exception("No pude abrir la imagen.");
            b = BitmapFactory.decodeStream(in, null, o2);
        }
        if (b == null) throw new Exception("No pude leer una de las imágenes.");
        int g = giroExif(c, u);
        if (g != 0) {
            Matrix m = new Matrix();
            m.postRotate(g);
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (r != b) b.recycle();
            b = r;
        }
        return b;
    }

    /** Mide el tamaño real y la nitidez de una imagen (varianza del laplaciano, en una copia pequeña). */
    static void analizar(Context c, Imagen im) {
        im.ancho = 0;
        im.alto = 0;
        im.nitidez = -1;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(im.uri)) {
                if (in != null) BitmapFactory.decodeStream(in, null, o);
            }
            int ancho = o.outWidth;
            int alto = o.outHeight;
            int g = giroExif(c, im.uri);
            if (g == 90 || g == 270) {
                int t = ancho;
                ancho = alto;
                alto = t;
            }
            im.ancho = ancho;
            im.alto = alto;
            Bitmap b = cargar(c, im.uri, 640);
            int w = b.getWidth();
            int h = b.getHeight();
            double f = 640.0 / Math.max(w, h);
            if (f < 1) {
                int nw = Math.max(8, (int) Math.round(w * f));
                int nh = Math.max(8, (int) Math.round(h * f));
                Bitmap e = Bitmap.createScaledBitmap(b, nw, nh, true);
                if (e != b) b.recycle();
                b = e;
                w = nw;
                h = nh;
            }
            int[] px = new int[w * h];
            b.getPixels(px, 0, w, 0, 0, w, h);
            b.recycle();
            double[] gris = new double[w * h];
            for (int i = 0; i < px.length; i++) {
                int p = px[i];
                gris[i] = 0.299 * ((p >> 16) & 255) + 0.587 * ((p >> 8) & 255) + 0.114 * (p & 255);
            }
            double suma = 0;
            double suma2 = 0;
            long n = 0;
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    int k = y * w + x;
                    double lap = 4 * gris[k] - gris[k - 1] - gris[k + 1] - gris[k - w] - gris[k + w];
                    suma += lap;
                    suma2 += lap * lap;
                    n++;
                }
            }
            if (n > 0) {
                double media = suma / n;
                im.nitidez = suma2 / n - media * media;
            }
        } catch (Throwable e) {
            // si no se pudo medir, simplemente no se avisa nada
        }
    }

    // ---------- Fuentes y colores del texto ----------

    static Typeface tipografia(int i) {
        switch (i) {
            case 1:
                return Typeface.create(Typeface.SERIF, Typeface.BOLD);
            case 2:
                return Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
            case 3:
                return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case 4:
                return Typeface.create("casual", Typeface.NORMAL);
            case 5:
                return Typeface.create("cursive", Typeface.BOLD);
            case 6:
                return Typeface.create("sans-serif-black", Typeface.NORMAL);
            case 7:
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            default:
                return Typeface.DEFAULT_BOLD;
        }
    }

    static int colorTexto(Imagen im) {
        return COLORES_TEXTO[limitar(im.rotuloColor, 0, COLORES_TEXTO.length - 1)];
    }

    private static int colorBase(Imagen im) {
        return COLORES_FONDO[limitar(im.rotuloFondoColor, 0, COLORES_FONDO.length - 1)];
    }

    private static int colorFranja(Imagen im) {
        int c = colorBase(im);
        return Color.argb(170, Color.red(c), Color.green(c), Color.blue(c));
    }

    private static double luminancia(int c) {
        double[] v = {Color.red(c) / 255.0, Color.green(c) / 255.0, Color.blue(c) / 255.0};
        for (int i = 0; i < 3; i++) {
            v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    /** Razón de contraste entre dos colores, de 1 (iguales) a 21 (blanco y negro). */
    static double contraste(int a, int b) {
        double la = luminancia(a);
        double lb = luminancia(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** Una frase que dice si el texto se leerá bien con los colores elegidos. */
    static String avisoLegibilidad(Imagen im) {
        if (!im.rotuloFondo) {
            return "Sin franja, el texto se apoya solo en una sombra. Se lee mejor con la franja "
                    + "activada, sobre todo en fotos con muchos detalles.";
        }
        if (contraste(colorTexto(im), colorBase(im)) < 4.5) {
            return "Atención: el color del texto y el de la franja se parecen y el texto será "
                    + "difícil de leer. Cambia uno de los dos.";
        }
        return "Los colores del texto y de la franja contrastan bien: el texto se leerá con claridad.";
    }

    // ---------- Destello de luz ----------

    private static float alfaFlash(long rel, int n, float pico) {
        float r = 1f - rel / (float) n;
        return pico * r * r;
    }

    private static void aplicarFlash(Bitmap cuadro, float alfa) {
        if (alfa <= 0.01f) return;
        new Canvas(cuadro).drawColor(Color.argb(Math.round(255f * Math.min(1f, alfa)), 255, 255, 255));
    }

    // ---------- Composición del cuadro ----------

    /** Prepara el fondo del cuadro: negro, o la imagen muy difuminada si el encuadre es el 1. */
    private static void dibujarFondo(Bitmap src, Bitmap dest, int w, int h, int modo) {
        Canvas cv = new Canvas(dest);
        Paint pt = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        cv.drawColor(Color.BLACK);
        if (modo != 1) return;
        int sw = src.getWidth();
        int sh = src.getHeight();
        int pw = Math.max(8, w / 24);
        int ph = Math.max(8, h / 24);
        Bitmap peq = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
        Canvas c2 = new Canvas(peq);
        float s2 = Math.max((float) pw / sw, (float) ph / sh);
        float dw2 = sw * s2;
        float dh2 = sh * s2;
        c2.drawBitmap(src, null, new RectF((pw - dw2) / 2, (ph - dh2) / 2, (pw + dw2) / 2,
                (ph + dh2) / 2), pt);
        cv.drawBitmap(peq, null, new RectF(0, 0, w, h), pt);
        peq.recycle();
        cv.drawColor(Color.argb(110, 0, 0, 0));
    }

    /** Cómo se ve el texto en un cuadro concreto de su animación. */
    static final class AnimTexto {
        float alfa = 1f;
        float dx = 0f;
        float dy = 0f;
        float escala = 1f;
        int letras = -1; // cuántas letras se ven; -1 todas

        void reiniciar() {
            alfa = 1f;
            dx = 0f;
            dy = 0f;
            escala = 1f;
            letras = -1;
        }
    }

    /** El texto de una imagen, ya medido y listo para dibujarse entero o letra por letra. */
    static final class Rotulo {
        final Bitmap bmp;
        final String texto;
        final float cx;
        final float cy;
        private final Canvas cv;
        private final StaticLayout lay;
        private final TextPaint tp;
        private final float margenX;
        private final float y;
        private final float relleno;
        private final RectF franja;
        private final Paint fp;
        private int dibujados = -2;

        Rotulo(Bitmap bmp, String texto, StaticLayout lay, TextPaint tp, float margenX, float y,
                float relleno, RectF franja, Paint fp) {
            this.bmp = bmp;
            this.texto = texto;
            this.cv = new Canvas(bmp);
            this.lay = lay;
            this.tp = tp;
            this.margenX = margenX;
            this.y = y;
            this.relleno = relleno;
            this.franja = franja;
            this.fp = fp;
            this.cx = bmp.getWidth() / 2f;
            this.cy = y + lay.getHeight() / 2f;
        }

        /** Dibuja en la capa las primeras n letras del texto (todas si n es -1 o más que el texto). */
        void dibujar(int n) {
            if (n == dibujados) return;
            dibujados = n;
            bmp.eraseColor(Color.TRANSPARENT);
            if (franja != null) cv.drawRoundRect(franja, relleno, relleno, fp);
            if (n < 0 || n >= texto.length()) {
                cv.save();
                cv.translate(margenX, y);
                lay.draw(cv);
                cv.restore();
                return;
            }
            for (int i = 0; i < lay.getLineCount(); i++) {
                int ini = lay.getLineStart(i);
                if (ini >= n) break;
                int fin = Math.min(lay.getLineEnd(i), n);
                while (fin > ini && Character.isWhitespace(texto.charAt(fin - 1))) fin--;
                if (fin <= ini) continue;
                cv.drawText(texto, ini, fin, margenX + lay.getLineLeft(i), y + lay.getLineBaseline(i),
                        tp);
            }
        }

        void liberar() {
            bmp.recycle();
        }
    }

    // ---------- Animaciones del texto ----------

    static boolean hayAnimTexto(List<Imagen> imagenes) {
        for (Imagen im : imagenes) {
            if (im.rotuloAnim != 0 && im.rotulo != null && !im.rotulo.trim().isEmpty()) return true;
        }
        return false;
    }

    private static double durEntrada(int anim, int letras, double dur) {
        if (anim == ANIM_ESCRIBIR) {
            return Math.max(0.2, Math.min(letras / 18.0, Math.min(8.0, dur * 0.7)));
        }
        return Math.min(0.7, dur * 0.4);
    }

    private static double durSalida(double dur) {
        return Math.min(0.6, dur * 0.3);
    }

    /** Cuántos cuadros dura la entrada del texto y cuántos su salida (0 si no hay). */
    static long[] cuadrosAnimTexto(Imagen im, Rotulo r, long cuantos, int fps) {
        long[] v = {0, 0};
        if (r == null || im.rotuloAnim == 0) return v;
        double dur = cuantos / (double) fps;
        v[0] = Math.min(cuantos, (long) Math.ceil(durEntrada(im.rotuloAnim, r.texto.length(), dur)
                * fps) + 1);
        if (im.rotuloAnim == ANIM_FUNDIDO_SALIDA) {
            v[1] = Math.min(cuantos, (long) Math.ceil(durSalida(dur) * fps) + 1);
        }
        return v;
    }

    /** Calcula cómo se ve el texto en el cuadro rel de la imagen, que tiene cuantos cuadros. */
    static void animar(Imagen im, long rel, long cuantos, int fps, Rotulo r, AnimTexto a) {
        a.reiniciar();
        if (r == null || im.rotuloAnim == 0) return;
        double dur = cuantos / (double) fps;
        double t = rel / (double) fps;
        double entra = durEntrada(im.rotuloAnim, r.texto.length(), dur);
        double p = entra <= 0 ? 1.0 : Math.min(1.0, t / entra);
        double s = p * p * (3 - 2 * p);
        switch (im.rotuloAnim) {
            case 1:
                a.alfa = (float) s;
                break;
            case 2: {
                double sal = durSalida(dur);
                double q = sal <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, (dur - t) / sal));
                double sq = q * q * (3 - 2 * q);
                a.alfa = (float) Math.min(s, sq);
                break;
            }
            case 3:
                a.alfa = (float) s;
                a.dy = (float) ((1 - s) * r.bmp.getHeight() * 0.10);
                break;
            case 4:
                a.alfa = (float) s;
                a.dx = (float) (-(1 - s) * r.bmp.getWidth() * 0.5);
                break;
            case ANIM_ESCRIBIR:
                a.letras = (int) Math.ceil(p * r.texto.length());
                break;
            case 6:
                a.alfa = (float) s;
                a.escala = (float) (0.6 + 0.4 * s);
                break;
            default:
                break;
        }
    }

    // ---------- Transiciones visuales entre imágenes ----------

    /**
     * Mezcla la imagen que sale con la que entra. p va de 0 (solo la que sale) a 1 (solo la que entra).
     * El resultado queda en dest.
     */
    private static void mezclarTransicion(int tipo, float p, Bitmap entra, Bitmap sale, Bitmap dest,
            int w, int h) {
        float s = p * p * (3 - 2 * p);
        Canvas cv = new Canvas(dest);
        Paint pt = new Paint(Paint.FILTER_BITMAP_FLAG);
        cv.drawColor(Color.BLACK);
        switch (tipo) {
            case 1:
                cv.drawBitmap(sale, 0, 0, null);
                pt.setAlpha(Math.round(255f * s));
                cv.drawBitmap(entra, 0, 0, pt);
                break;
            case 2:
                if (p < 0.5f) {
                    pt.setAlpha(Math.round(255f * (1f - p * 2f)));
                    cv.drawBitmap(sale, 0, 0, pt);
                } else {
                    pt.setAlpha(Math.round(255f * (p - 0.5f) * 2f));
                    cv.drawBitmap(entra, 0, 0, pt);
                }
                break;
            case 3:
                cv.drawBitmap(sale, -s * w, 0, null);
                cv.drawBitmap(entra, (1f - s) * w, 0, null);
                break;
            case 4:
                cv.drawBitmap(sale, 0, -s * h, null);
                cv.drawBitmap(entra, 0, (1f - s) * h, null);
                break;
            default:
                cv.drawBitmap(sale, 0, 0, null);
                cv.save();
                cv.clipRect(0, 0, Math.round(s * w), h);
                cv.drawBitmap(entra, 0, 0, null);
                cv.restore();
                break;
        }
    }

    /** Dibuja el cuadro: el fondo, la imagen aumentada z veces desde el centro y el texto encima. */
    private static void dibujarPrimerPlano(Bitmap src, Bitmap fondo, Bitmap dest, int w, int h,
            int modo, float z, Rotulo rot, AnimTexto an) {
        Canvas cv = new Canvas(dest);
        Paint pt = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        cv.drawBitmap(fondo, 0, 0, null);
        int sw = src.getWidth();
        int sh = src.getHeight();
        float s = modo == 0 ? Math.max((float) w / sw, (float) h / sh)
                : Math.min((float) w / sw, (float) h / sh);
        float dw = sw * s * z;
        float dh = sh * s * z;
        cv.drawBitmap(src, null, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2,
                (h + dh) / 2), pt);
        if (rot == null) return;
        rot.dibujar(an == null ? -1 : an.letras);
        float alfa = an == null ? 1f : an.alfa;
        if (alfa <= 0.01f) return;
        Paint pc = new Paint(Paint.FILTER_BITMAP_FLAG);
        pc.setAlpha(Math.round(255f * Math.min(1f, alfa)));
        cv.save();
        if (an != null) {
            cv.translate(an.dx, an.dy);
            if (an.escala != 1f) cv.scale(an.escala, an.escala, rot.cx, rot.cy);
        }
        cv.drawBitmap(rot.bmp, 0, 0, pc);
        cv.restore();
    }

    /**
     * Prepara el texto de una imagen en una capa transparente del tamaño del cuadro. Devuelve null si
     * la imagen no tiene texto. Si el texto es largo, el tamaño se reduce solo hasta que quepa. En
     * videos verticales el texto se separa más de los bordes de arriba y de abajo, donde las
     * plataformas ponen sus propios botones y textos.
     */
    static Rotulo rotulo(Imagen im, int w, int h) {
        String t = im.rotulo == null ? "" : im.rotulo.trim();
        if (t.isEmpty()) return null;
        Bitmap capa = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int lado = Math.min(w, h);
        float[] fracciones = {1f / 22f, 1f / 16f, 1f / 11f};
        float tam = lado * fracciones[Math.max(0, Math.min(2, im.rotuloTam))];
        int margenX = Math.round(lado * 0.06f);
        int margenY = h > w ? Math.round(h * 0.14f) : margenX;
        int ancho = w - 2 * margenX;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final int colTexto = colorTexto(im);
        tp.setColor(colTexto);
        tp.setTypeface(tipografia(im.rotuloFuente));
        StaticLayout lay;
        float relleno;
        while (true) {
            tp.setTextSize(tam);
            relleno = im.rotuloFondo ? tam * 0.4f : 0f;
            lay = StaticLayout.Builder.obtain(t, 0, t.length(), tp, ancho)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build();
            if (lay.getHeight() + 2 * relleno <= h - 2 * margenY || tam <= 12f) break;
            tam *= 0.92f;
        }
        if (!im.rotuloFondo) {
            tp.setShadowLayer(Math.max(2f, tam / 10f), 0f, tam / 16f,
                    luminancia(colTexto) > 0.4 ? Color.BLACK : Color.WHITE);
        }
        float y;
        if (im.rotuloPos == 0) {
            y = margenY + relleno;
        } else if (im.rotuloPos == 1) {
            y = (h - lay.getHeight()) / 2f;
        } else {
            y = h - margenY - relleno - lay.getHeight();
        }
        RectF franja = null;
        Paint fp = null;
        if (im.rotuloFondo) {
            float maxLinea = 0f;
            for (int i = 0; i < lay.getLineCount(); i++) {
                maxLinea = Math.max(maxLinea, lay.getLineWidth(i));
            }
            float caja = maxLinea + 2 * relleno;
            float x0 = (w - caja) / 2f;
            fp = new Paint(Paint.ANTI_ALIAS_FLAG);
            fp.setColor(colorFranja(im));
            franja = new RectF(x0, y - relleno, x0 + caja, y + lay.getHeight() + relleno);
        }
        return new Rotulo(capa, t, lay, tp, margenX, y, relleno, franja, fp);
    }

    static boolean hayZoom(List<Imagen> imagenes) {
        for (Imagen im : imagenes) {
            if (im.zoom != 0) return true;
        }
        return false;
    }

    /** Una imagen en JPEG con el lado largo de a lo más maxLado, para mandarla a Gemini. */
    static byte[] jpeg(Context c, Uri u, int maxLado) throws Exception {
        Bitmap b = cargar(c, u, maxLado);
        int mayor = Math.max(b.getWidth(), b.getHeight());
        if (mayor > maxLado) {
            float f = (float) maxLado / mayor;
            Bitmap e = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * f)),
                    Math.max(1, Math.round(b.getHeight() * f)), true);
            if (e != b) b.recycle();
            b = e;
        }
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 85, bo);
        b.recycle();
        return bo.toByteArray();
    }

    private static int limitar(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** Pasa un cuadro ARGB a planos YUV 4:2:0 (BT.709, rango limitado). */
    private static void aYuv(Bitmap b, int w, int h, int[] px, byte[] y, byte[] u, byte[] v) {
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int j = 0; j < h; j++) {
            int fila = j * w;
            for (int i = 0; i < w; i++) {
                int p = px[fila + i];
                int r = (p >> 16) & 255;
                int g = (p >> 8) & 255;
                int bl = p & 255;
                y[fila + i] = (byte) (((47 * r + 157 * g + 16 * bl + 128) >> 8) + 16);
            }
        }
        int cw = w / 2;
        for (int j = 0; j < h / 2; j++) {
            for (int i = 0; i < cw; i++) {
                int a = px[(2 * j) * w + 2 * i];
                int b2 = px[(2 * j) * w + 2 * i + 1];
                int c = px[(2 * j + 1) * w + 2 * i];
                int d = px[(2 * j + 1) * w + 2 * i + 1];
                int r = (((a >> 16) & 255) + ((b2 >> 16) & 255) + ((c >> 16) & 255)
                        + ((d >> 16) & 255) + 2) >> 2;
                int g = (((a >> 8) & 255) + ((b2 >> 8) & 255) + ((c >> 8) & 255)
                        + ((d >> 8) & 255) + 2) >> 2;
                int bl = ((a & 255) + (b2 & 255) + (c & 255) + (d & 255) + 2) >> 2;
                u[j * cw + i] = (byte) limitar(((-26 * r - 87 * g + 112 * bl + 128) >> 8) + 128,
                        16, 240);
                v[j * cw + i] = (byte) limitar(((112 * r - 102 * g - 10 * bl + 128) >> 8) + 128,
                        16, 240);
            }
        }
    }

    private static void copiarPlano(Image.Plane pl, byte[] src, int w, int h) {
        ByteBuffer b = pl.getBuffer();
        int rs = pl.getRowStride();
        int ps = pl.getPixelStride();
        if (ps == 1 && rs == w) {
            b.position(0);
            b.put(src, 0, w * h);
        } else if (ps == 1) {
            for (int r = 0; r < h; r++) {
                b.position(r * rs);
                b.put(src, r * w, w);
            }
        } else {
            for (int r = 0; r < h; r++) {
                for (int cc = 0; cc < w; cc++) {
                    b.put(r * rs + cc * ps, src[r * w + cc]);
                }
            }
        }
    }

    private static boolean drenar(MediaCodec enc, MediaMuxer mx, int[] est,
            MediaCodec.BufferInfo bi, long esperaUs) {
        while (true) {
            int o = enc.dequeueOutputBuffer(bi, esperaUs);
            if (o == MediaCodec.INFO_TRY_AGAIN_LATER) return false;
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                est[0] = mx.addTrack(enc.getOutputFormat());
                mx.start();
                est[1] = 1;
                continue;
            }
            if (o < 0) continue;
            ByteBuffer b = enc.getOutputBuffer(o);
            if ((bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bi.size = 0;
            if (bi.size > 0 && b != null && est[1] == 1) {
                b.position(bi.offset);
                b.limit(bi.offset + bi.size);
                mx.writeSampleData(est[0], b, bi);
            }
            enc.releaseOutputBuffer(o, false);
            if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return true;
        }
    }

    // ---------- Crear el video ----------

    /**
     * Crea el video. audioM4a es el audio ya codificado; durSeg trae la duración de cada imagen
     * en segundos; totalSeg es lo que dura el audio (y el video). Si hay zoom, destello, transiciones
     * visuales o texto animado, el video se crea a más cuadros por segundo. Mientras una imagen está
     * quieta se envían pocos cuadros, para que el video no tarde de más.
     *
     * transVis es la transición visual entre imágenes (0 ninguna) y transDur su duración (0 a 2). La
     * transición empieza en el momento exacto en que cambia la imagen, igual que el sonido y el
     * destello, y dura como máximo la mitad de lo que dura la imagen nueva.
     */
    static void crear(Context c, List<Imagen> imagenes, double[] durSeg, File audioM4a,
            double totalSeg, int formato, int encuadre, int flash, int transVis, int transDur,
            File salida, File temporal, Audio.Progreso prog, Audio.Cancelado can) throws Exception {
        final int w = TAMANOS[formato][0];
        final int h = TAMANOS[formato][1];
        final boolean conZoom = hayZoom(imagenes);
        final boolean conFlash = flash > 0 && imagenes.size() > 1;
        final boolean conTransVis = transVis > 0 && imagenes.size() > 1;
        final boolean conAnimTexto = hayAnimTexto(imagenes);
        final boolean conMov = conZoom || conFlash || conTransVis || conAnimTexto;
        final int fps = conMov ? FPS_ZOOM : FPS;
        final int paso = Math.max(1, fps / FPS);
        final float picoFlash = PICO_FLASH[limitar(flash, 0, PICO_FLASH.length - 1)];
        final int cuadrosTrans = (int) Math.round(
                SEG_TRANSICION[limitar(transDur, 0, SEG_TRANSICION.length - 1)] * fps);
        final long totalMarcos = Math.max(1, Math.round(totalSeg * fps));
        int cuantas = imagenes.size();

        // Hasta qué cuadro llega cada imagen.
        long[] fines = new long[cuantas];
        double acum = 0;
        for (int i = 0; i < cuantas; i++) {
            acum += durSeg[i];
            fines[i] = Math.min(totalMarcos, Math.round(acum * fps));
            if (i > 0 && fines[i] < fines[i - 1]) fines[i] = fines[i - 1];
        }
        if (acum * fps <= totalMarcos) fines[cuantas - 1] = totalMarcos;

        MediaFormat f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        f.setInteger(MediaFormat.KEY_BIT_RATE,
                (conZoom || conTransVis) ? 3000000 : (conMov ? 2000000 : 1000000));
        f.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, conMov ? 2 : 5);
        f.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        f.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709);
        f.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);

        MediaCodec enc = null;
        MediaMuxer mx = null;
        boolean muxIniciado = false;
        int[] est = {-1, 0};
        try {
            enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            try {
                enc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            } catch (Exception e) {
                // Segundo intento, sin las opciones que algunos teléfonos no aceptan.
                try {
                    enc.release();
                } catch (Exception ignorada) {
                    // nada
                }
                f.removeKey(MediaFormat.KEY_BITRATE_MODE);
                f.removeKey(MediaFormat.KEY_COLOR_STANDARD);
                f.removeKey(MediaFormat.KEY_COLOR_RANGE);
                enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                try {
                    enc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                } catch (Exception e2) {
                    throw new Exception("El codificador de video del teléfono no aceptó la "
                            + "configuración. Prueba con otro tamaño de video.");
                }
            }
            enc.start();
            temporal.delete();
            mx = new MediaMuxer(temporal.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();

            byte[] yy = new byte[w * h];
            byte[] uu = new byte[w * h / 4];
            byte[] vv = new byte[w * h / 4];
            int[] px = new int[w * h];
            Bitmap cuadro = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Bitmap fondoA = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            // Solo con transición visual hacen falta dos fondos, un cuadro de mezcla y otro con la
            // imagen que sale.
            Bitmap fondoB = conTransVis ? Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) : null;
            Bitmap mezcla = conTransVis ? Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) : null;
            Bitmap saliente = conTransVis ? Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) : null;
            Bitmap prevSrc = null;
            Bitmap prevFondo = null;
            Rotulo prevRot = null;
            Imagen prevIm = null;
            long prevCuantos = 0;
            int prevZoom = 0;
            final AnimTexto an = new AnimTexto();
            long cuadroActual = 0;
            int ultimoPct = -1;
            boolean eos = false;

            for (int k = 0; k < cuantas && cuadroActual < totalMarcos; k++) {
                long fin = fines[k];
                if (fin <= cuadroActual) continue;
                if (can != null && can.si()) throw new Audio.Cancelacion();
                Imagen im = imagenes.get(k);
                boolean zoomea = im.zoom != 0;
                // Con zoom se carga la imagen más grande, para que al acercarla no se vea borrosa.
                Bitmap src = cargar(c, im.uri, zoomea ? Math.max(w, h) * 3 / 2 : Math.max(w, h));
                Bitmap fondo = (conTransVis && prevFondo == fondoA) ? fondoB : fondoA;
                dibujarFondo(src, fondo, w, h, encuadre);
                final Rotulo rot = rotulo(im, w, h);
                final long ini = cuadroActual;
                final long cuantos = fin - ini;
                // El destello ilumina la imagen nueva cada vez que cambia (no la primera).
                final int nFlash = (conFlash && k > 0)
                        ? Math.max(2, (int) Math.round(DUR_FLASH * fps)) : 0;
                // La transición visual ocupa los primeros cuadros de la imagen nueva.
                int nTr = 0;
                if (conTransVis && k > 0 && prevSrc != null) {
                    nTr = (int) Math.min(cuadrosTrans, cuantos / 2);
                    if (nTr < 2) nTr = 0;
                }
                if (nTr > 0) {
                    // La imagen que sale, tal como quedó en su último cuadro.
                    float zFin = prevZoom == 1 ? (float) (1 + ZOOM_MAX) : 1f;
                    animar(prevIm, prevCuantos - 1, prevCuantos, fps, prevRot, an);
                    dibujarPrimerPlano(prevSrc, prevFondo, saliente, w, h, encuadre, zFin, prevRot,
                            an);
                }
                final long[] ventAnim = cuadrosAnimTexto(im, rot, cuantos, fps);
                final long inicioSalida = ventAnim[1] > 0 ? fin - ventAnim[1] : Long.MAX_VALUE;
                boolean baseLista = false;
                while (cuadroActual < fin) {
                    if (can != null && can.si()) throw new Audio.Cancelacion();
                    int i = enc.dequeueInputBuffer(10000);
                    if (i >= 0) {
                        final long rel = cuadroActual - ini;
                        final boolean animaTexto = rot != null && im.rotuloAnim != 0
                                && (rel < ventAnim[0] || cuadroActual >= inicioSalida);
                        final boolean enTrans = rel < nTr;
                        final boolean enFlash = rel < nFlash;
                        long salto = 1;
                        if (zoomea || animaTexto || enTrans || enFlash) {
                            baseLista = false;
                            float z = 1f;
                            if (zoomea) {
                                double t = cuantos <= 1 ? 0
                                        : (cuadroActual - ini) / (double) (cuantos - 1);
                                double s = t * t * (3 - 2 * t);
                                z = (float) (im.zoom == 1 ? 1 + ZOOM_MAX * s
                                        : 1 + ZOOM_MAX * (1 - s));
                            }
                            animar(im, rel, cuantos, fps, rot, an);
                            dibujarPrimerPlano(src, fondo, cuadro, w, h, encuadre, z, rot, an);
                            Bitmap listo = cuadro;
                            if (enTrans) {
                                mezclarTransicion(transVis, (rel + 1f) / (nTr + 1f), cuadro,
                                        saliente, mezcla, w, h);
                                listo = mezcla;
                            }
                            if (enFlash) aplicarFlash(listo, alfaFlash(rel, nFlash, picoFlash));
                            aYuv(listo, w, h, px, yy, uu, vv);
                        } else {
                            if (!baseLista) {
                                an.reiniciar();
                                dibujarPrimerPlano(src, fondo, cuadro, w, h, encuadre, 1f, rot, an);
                                aYuv(cuadro, w, h, px, yy, uu, vv);
                                baseLista = true;
                            }
                            // Imagen quieta: basta con pocos cuadros por segundo, pero sin pasarse
                            // del momento en que empieza la salida del texto.
                            long limite = fin - cuadroActual;
                            if (inicioSalida != Long.MAX_VALUE && inicioSalida > cuadroActual) {
                                limite = Math.min(limite, inicioSalida - cuadroActual);
                            }
                            salto = Math.max(1, Math.min(paso, limite));
                        }
                        Image im2 = enc.getInputImage(i);
                        if (im2 == null) {
                            throw new Exception("El codificador de video de este teléfono no "
                                    + "permite crear el video desde imágenes.");
                        }
                        Image.Plane[] pl = im2.getPlanes();
                        copiarPlano(pl[0], yy, w, h);
                        copiarPlano(pl[1], uu, w / 2, h / 2);
                        copiarPlano(pl[2], vv, w / 2, h / 2);
                        boolean ultimo = cuadroActual + salto >= totalMarcos;
                        enc.queueInputBuffer(i, 0, w * h * 3 / 2,
                                cuadroActual * 1000000L / fps,
                                ultimo ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                        cuadroActual += salto;
                    }
                    if (!eos && drenar(enc, mx, est, bi, 0)) eos = true;
                    if (prog != null) {
                        int pct = (int) (cuadroActual * 92 / totalMarcos);
                        if (pct != ultimoPct) {
                            ultimoPct = pct;
                            prog.porcentaje(pct);
                        }
                    }
                }
                if (conTransVis) {
                    // Esta imagen se queda para la transición de la siguiente.
                    if (prevSrc != null) prevSrc.recycle();
                    if (prevRot != null) prevRot.liberar();
                    prevSrc = src;
                    prevFondo = fondo;
                    prevRot = rot;
                    prevIm = im;
                    prevCuantos = cuantos;
                    prevZoom = im.zoom;
                } else {
                    src.recycle();
                    if (rot != null) rot.liberar();
                }
            }
            if (cuadroActual < totalMarcos) {
                throw new Exception("No pude completar el video. Revisa las imágenes elegidas.");
            }
            while (!eos && !drenar(enc, mx, est, bi, 10000)) {
                if (can != null && can.si()) throw new Audio.Cancelacion();
            }
            cuadro.recycle();
            fondoA.recycle();
            if (fondoB != null) fondoB.recycle();
            if (mezcla != null) mezcla.recycle();
            if (saliente != null) saliente.recycle();
            if (prevSrc != null) prevSrc.recycle();
            if (prevRot != null) prevRot.liberar();
            muxIniciado = est[1] == 1;
        } finally {
            if (enc != null) {
                try {
                    enc.stop();
                } catch (Exception ignorada) {
                    // nada
                }
                try {
                    enc.release();
                } catch (Exception ignorada) {
                    // nada
                }
            }
            if (mx != null) {
                try {
                    if (muxIniciado) mx.stop();
                } catch (Exception ignorada) {
                    // nada
                }
                try {
                    mx.release();
                } catch (Exception ignorada) {
                    // nada
                }
            }
        }
        if (prog != null) prog.porcentaje(93);
        unir(temporal, audioM4a, salida);
        if (prog != null) prog.porcentaje(100);
    }

    /** Junta el video y el audio ya codificados en un solo MP4, sin volver a codificar. */
    private static void unir(File video, File audio, File salida) throws Exception {
        MediaExtractor ev = new MediaExtractor();
        MediaExtractor ea = new MediaExtractor();
        MediaMuxer mx = null;
        boolean iniciado = false;
        try {
            ev.setDataSource(video.getAbsolutePath());
            ea.setDataSource(audio.getAbsolutePath());
            int pv = pistaDe(ev, "video/");
            int pa = pistaDe(ea, "audio/");
            if (pv < 0 || pa < 0) throw new Exception("No pude unir el audio y el video.");
            ev.selectTrack(pv);
            ea.selectTrack(pa);
            salida.delete();
            mx = new MediaMuxer(salida.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int tv = mx.addTrack(ev.getTrackFormat(pv));
            int ta = mx.addTrack(ea.getTrackFormat(pa));
            mx.start();
            iniciado = true;
            ByteBuffer buf = ByteBuffer.allocate(8 << 20);
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            boolean vFin = false;
            boolean aFin = false;
            while (!vFin || !aFin) {
                long tvv = Long.MAX_VALUE;
                long taa = Long.MAX_VALUE;
                if (!vFin) {
                    tvv = ev.getSampleTime();
                    if (tvv < 0) {
                        vFin = true;
                        tvv = Long.MAX_VALUE;
                    }
                }
                if (!aFin) {
                    taa = ea.getSampleTime();
                    if (taa < 0) {
                        aFin = true;
                        taa = Long.MAX_VALUE;
                    }
                }
                if (vFin && aFin) break;
                boolean usaV = tvv <= taa;
                MediaExtractor e = usaV ? ev : ea;
                buf.clear();
                int n = e.readSampleData(buf, 0);
                if (n < 0) {
                    if (usaV) vFin = true;
                    else aFin = true;
                    continue;
                }
                bi.offset = 0;
                bi.size = n;
                bi.presentationTimeUs = e.getSampleTime();
                bi.flags = (e.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                mx.writeSampleData(usaV ? tv : ta, buf, bi);
                e.advance();
            }
        } finally {
            try {
                ev.release();
            } catch (Exception ignorada) {
                // nada
            }
            try {
                ea.release();
            } catch (Exception ignorada) {
                // nada
            }
            if (mx != null) {
                try {
                    if (iniciado) mx.stop();
                } catch (Exception ignorada) {
                    // nada
                }
                try {
                    mx.release();
                } catch (Exception ignorada) {
                    // nada
                }
            }
        }
    }

    // ---------- Poner un audio nuevo a un video del teléfono ----------

    /** Lo que se sabe de un video del teléfono. */
    static final class Info {
        double seg;
        boolean audio;
        int ancho;
        int alto;
        int giro;
        String mime = "";
    }

    /** Lee la duración, el tamaño, el giro y si el video tiene audio. */
    static Info info(Context c, Uri u) throws Exception {
        Info r = new Info();
        boolean hayVideo = false;
        long durVideoUs = 0;
        long durAudioUs = 0;
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(c, u, null);
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String m = f.getString(MediaFormat.KEY_MIME);
                if (m == null) continue;
                long d = f.containsKey(MediaFormat.KEY_DURATION) ? f.getLong(MediaFormat.KEY_DURATION) : 0;
                if (m.startsWith("video/") && !hayVideo) {
                    hayVideo = true;
                    r.mime = m;
                    r.ancho = f.containsKey(MediaFormat.KEY_WIDTH) ? f.getInteger(MediaFormat.KEY_WIDTH) : 0;
                    r.alto = f.containsKey(MediaFormat.KEY_HEIGHT) ? f.getInteger(MediaFormat.KEY_HEIGHT) : 0;
                    durVideoUs = d;
                } else if (m.startsWith("audio/")) {
                    r.audio = true;
                    durAudioUs = Math.max(durAudioUs, d);
                }
            }
        } catch (Exception e) {
            throw new Exception("No pude abrir este archivo. Puede que esté dañado o que no sea un "
                    + "video compatible.");
        } finally {
            try {
                ex.release();
            } catch (Exception ignorada) {
                // nada
            }
        }
        if (!hayVideo) throw new Exception("Este archivo no tiene video. Elige un archivo de video.");
        long durMs = 0;
        MediaMetadataRetriever mr = new MediaMetadataRetriever();
        try {
            mr.setDataSource(c, u);
            String g = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            r.giro = g == null ? 0 : Integer.parseInt(g.trim());
            String d = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            durMs = d == null ? 0 : Long.parseLong(d.trim());
            if (r.ancho <= 0 || r.alto <= 0) {
                String aw = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String ah = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                if (aw != null) r.ancho = Integer.parseInt(aw.trim());
                if (ah != null) r.alto = Integer.parseInt(ah.trim());
            }
        } catch (Exception ignorada) {
            // se usan los datos de las pistas
        } finally {
            try {
                mr.release();
            } catch (Exception ignorada) {
                // nada
            }
        }
        long us = durVideoUs > 0 ? durVideoUs : (durMs > 0 ? durMs * 1000 : durAudioUs);
        r.seg = us / 1000000.0;
        if (r.seg <= 0) throw new Exception("No pude saber cuánto dura este video.");
        return r;
    }

    /**
     * Pone el audio nuevo (un M4A) en un video del teléfono. El video no se vuelve a codificar: sus
     * cuadros se copian tal cual, así que no pierde calidad y es rápido. segTotal es lo que dura el
     * video, y el audio nunca pasa de ahí.
     */
    static void unirConAudio(Context c, Uri video, File audio, int giro, double segTotal, File salida,
            Audio.Progreso prog, Audio.Cancelado can) throws Exception {
        MediaExtractor ev = new MediaExtractor();
        MediaExtractor ea = new MediaExtractor();
        MediaMuxer mx = null;
        boolean iniciado = false;
        try {
            ev.setDataSource(c, video, null);
            ea.setDataSource(audio.getAbsolutePath());
            int pv = pistaDe(ev, "video/");
            int pa = pistaDe(ea, "audio/");
            if (pv < 0 || pa < 0) throw new Exception("No pude unir el audio nuevo con el video.");
            ev.selectTrack(pv);
            ea.selectTrack(pa);
            MediaFormat fv = ev.getTrackFormat(pv);
            int cap = 8 << 20;
            if (fv.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                cap = Math.min(32 << 20, Math.max(cap, fv.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)));
            }
            final long desfase = Math.max(0, ev.getSampleTime());
            salida.delete();
            mx = new MediaMuxer(salida.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if (giro != 0) mx.setOrientationHint(giro);
            int tv;
            try {
                tv = mx.addTrack(fv);
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new Exception("El video está en un formato que no se puede guardar como MP4 "
                        + "sin volver a codificarlo, así que no puedo ponerle el audio nuevo.");
            }
            int ta = mx.addTrack(ea.getTrackFormat(pa));
            mx.start();
            iniciado = true;
            ByteBuffer buf = ByteBuffer.allocate(cap);
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            boolean vFin = false;
            boolean aFin = false;
            int ultimoPct = -1;
            final long limiteUs = Math.round(segTotal * 1000000.0) + desfase;
            while (!vFin || !aFin) {
                if (can != null && can.si()) throw new Audio.Cancelacion();
                long tvv = Long.MAX_VALUE;
                long taa = Long.MAX_VALUE;
                if (!vFin) {
                    tvv = ev.getSampleTime();
                    if (tvv < 0) {
                        vFin = true;
                        tvv = Long.MAX_VALUE;
                    }
                }
                if (!aFin) {
                    taa = ea.getSampleTime();
                    if (taa < 0 || taa + desfase > limiteUs) {
                        aFin = true;
                        taa = Long.MAX_VALUE;
                    } else {
                        taa += desfase;
                    }
                }
                if (vFin && aFin) break;
                boolean usaV = tvv <= taa;
                MediaExtractor e = usaV ? ev : ea;
                buf.clear();
                int n = e.readSampleData(buf, 0);
                if (n < 0) {
                    if (usaV) vFin = true;
                    else aFin = true;
                    continue;
                }
                bi.offset = 0;
                bi.size = n;
                bi.presentationTimeUs = usaV ? tvv : taa;
                bi.flags = (e.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                mx.writeSampleData(usaV ? tv : ta, buf, bi);
                e.advance();
                if (prog != null && limiteUs > 0) {
                    int pct = (int) Math.min(99, bi.presentationTimeUs * 100 / limiteUs);
                    if (pct != ultimoPct) {
                        ultimoPct = pct;
                        prog.porcentaje(pct);
                    }
                }
            }
        } finally {
            try {
                ev.release();
            } catch (Exception ignorada) {
                // nada
            }
            try {
                ea.release();
            } catch (Exception ignorada) {
                // nada
            }
            if (mx != null) {
                try {
                    if (iniciado) mx.stop();
                } catch (Exception ignorada) {
                    // nada
                }
                try {
                    mx.release();
                } catch (Exception ignorada) {
                    // nada
                }
            }
        }
        if (prog != null) prog.porcentaje(100);
    }

    private static int pistaDe(MediaExtractor e, String prefijo) {
        for (int i = 0; i < e.getTrackCount(); i++) {
            String m = e.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith(prefijo)) return i;
        }
        return -1;
    }
}

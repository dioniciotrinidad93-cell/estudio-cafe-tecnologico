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
import android.net.Uri;

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

    /** Una imagen elegida por la persona. */
    static final class Imagen {
        Uri uri;
        String nombre;
        int ancho;
        int alto;
        double nitidez;
        int seg; // duración en segundos si se personaliza

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

    // ---------- Composición del cuadro ----------

    private static void componer(Bitmap src, Bitmap dest, int w, int h, int modo) {
        Canvas cv = new Canvas(dest);
        Paint pt = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        cv.drawColor(Color.BLACK);
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (modo == 0) {
            float s = Math.max((float) w / sw, (float) h / sh);
            float dw = sw * s;
            float dh = sh * s;
            cv.drawBitmap(src, null, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2,
                    (h + dh) / 2), pt);
            return;
        }
        if (modo == 1) {
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
        float s = Math.min((float) w / sw, (float) h / sh);
        float dw = sw * s;
        float dh = sh * s;
        cv.drawBitmap(src, null, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2,
                (h + dh) / 2), pt);
    }

    private static int limitar(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** Pasa un cuadro ARGB a planos YUV 4:2:0 (BT.709, rango limitado). */
    private static void aYuv(Bitmap b, int w, int h, byte[] y, byte[] u, byte[] v) {
        int[] px = new int[w * h];
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
     * en segundos; totalSeg es lo que dura el audio (y el video).
     */
    static void crear(Context c, List<Imagen> imagenes, double[] durSeg, File audioM4a,
            double totalSeg, int formato, int encuadre, File salida, File temporal,
            Audio.Progreso prog, Audio.Cancelado can) throws Exception {
        final int w = TAMANOS[formato][0];
        final int h = TAMANOS[formato][1];
        final long totalMarcos = Math.max(1, Math.round(totalSeg * FPS));
        int cuantas = imagenes.size();

        // Hasta qué cuadro llega cada imagen.
        long[] fines = new long[cuantas];
        double acum = 0;
        for (int i = 0; i < cuantas; i++) {
            acum += durSeg[i];
            fines[i] = Math.min(totalMarcos, Math.round(acum * FPS));
            if (i > 0 && fines[i] < fines[i - 1]) fines[i] = fines[i - 1];
        }
        if (acum * FPS <= totalMarcos) fines[cuantas - 1] = totalMarcos;

        MediaFormat f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        f.setInteger(MediaFormat.KEY_BIT_RATE, 1000000);
        f.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
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
            Bitmap cuadro = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            long cuadroActual = 0;
            int ultimoPct = -1;
            boolean eos = false;

            for (int k = 0; k < cuantas && cuadroActual < totalMarcos; k++) {
                long fin = fines[k];
                if (fin <= cuadroActual) continue;
                if (can != null && can.si()) throw new Audio.Cancelacion();
                Bitmap src = cargar(c, imagenes.get(k).uri, Math.max(w, h));
                componer(src, cuadro, w, h, encuadre);
                src.recycle();
                aYuv(cuadro, w, h, yy, uu, vv);
                while (cuadroActual < fin) {
                    if (can != null && can.si()) throw new Audio.Cancelacion();
                    int i = enc.dequeueInputBuffer(10000);
                    if (i >= 0) {
                        Image im = enc.getInputImage(i);
                        if (im == null) {
                            throw new Exception("El codificador de video de este teléfono no "
                                    + "permite crear el video desde imágenes.");
                        }
                        Image.Plane[] pl = im.getPlanes();
                        copiarPlano(pl[0], yy, w, h);
                        copiarPlano(pl[1], uu, w / 2, h / 2);
                        copiarPlano(pl[2], vv, w / 2, h / 2);
                        boolean ultimo = cuadroActual == totalMarcos - 1;
                        enc.queueInputBuffer(i, 0, w * h * 3 / 2,
                                cuadroActual * 1000000L / FPS,
                                ultimo ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                        cuadroActual++;
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
            }
            if (cuadroActual < totalMarcos) {
                throw new Exception("No pude completar el video. Revisa las imágenes elegidas.");
            }
            while (!eos && !drenar(enc, mx, est, bi, 10000)) {
                if (can != null && can.si()) throw new Audio.Cancelacion();
            }
            cuadro.recycle();
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

    private static int pistaDe(MediaExtractor e, String prefijo) {
        for (int i = 0; i < e.getTrackCount(); i++) {
            String m = e.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith(prefijo)) return i;
        }
        return -1;
    }
}

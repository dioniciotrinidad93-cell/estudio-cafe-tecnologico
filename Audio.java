package com.cafetecnologico.estudio;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.ShortBuffer;
import java.util.Arrays;

/**
 * Motor de audio de Estudio Café Tecnológico. Sin bibliotecas externas: solo las funciones de
 * Android (MediaExtractor, MediaCodec, MediaMuxer).
 *
 * Todo el audio de trabajo se guarda en archivos temporales de 16 bits, estéreo, a 44100 Hz, para
 * que un audio largo no llene la memoria.
 */
final class Audio {

    private Audio() {}

    static final int HZ = 44100;
    static final int BYTES_MARCO = 4; // 2 canales x 2 bytes

    interface Progreso {
        void porcentaje(int p);
    }

    interface Cancelado {
        boolean si();
    }

    interface Salida {
        void marcos(short[] estereo, int n) throws Exception;
    }

    static final class Cancelacion extends Exception {
        Cancelacion() {
            super("Operación cancelada.");
        }
    }

    /** Ajustes de la mezcla. */
    static final class Ajustes {
        int volVoz = 100;
        int volMus = 20;
        boolean repetir = true;
        boolean desvanecer = true;
        int introSeg = 0;
        int outroSeg = 0;
    }

    // ---------- Decodificar cualquier audio a PCM de trabajo ----------

    /** Convierte un archivo de audio (MP3, M4A, WAV, OGG, FLAC...) a PCM de trabajo. */
    static void decodificar(Context c, Uri uri, File salida, Progreso prog, Cancelado can)
            throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        OutputStream os = null;
        try {
            ex.setDataSource(c, uri, null);
            int pista = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String m = f.getString(MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("audio/")) {
                    pista = i;
                    fmt = f;
                    break;
                }
            }
            if (pista < 0 || fmt == null) {
                throw new Exception("Este archivo no tiene un audio que pueda leer.");
            }
            ex.selectTrack(pista);
            String mime = fmt.getString(MediaFormat.KEY_MIME);
            long durUs = fmt.containsKey(MediaFormat.KEY_DURATION)
                    ? fmt.getLong(MediaFormat.KEY_DURATION) : 0;
            int hz = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : HZ;
            int canales = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            boolean flotante = false;

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(fmt, null, null, 0);
            codec.start();
            os = new BufferedOutputStream(new FileOutputStream(salida), 1 << 16);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            Remuestreador rem = null;
            short[] est = new short[0];
            boolean entradaFin = false;
            boolean salidaFin = false;
            int ultimoPct = -1;
            long escritos = 0;

            while (!salidaFin) {
                if (can != null && can.si()) throw new Cancelacion();
                if (!entradaFin) {
                    int i = codec.dequeueInputBuffer(10000);
                    if (i >= 0) {
                        ByteBuffer b = codec.getInputBuffer(i);
                        int n = b == null ? -1 : ex.readSampleData(b, 0);
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            entradaFin = true;
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int o = codec.dequeueOutputBuffer(info, 10000);
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat f = codec.getOutputFormat();
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        hz = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        canales = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    flotante = f.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            && f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            == AudioFormat.ENCODING_PCM_FLOAT;
                } else if (o >= 0) {
                    ByteBuffer b = codec.getOutputBuffer(o);
                    if (info.size > 0 && b != null) {
                        b.position(info.offset);
                        b.limit(info.offset + info.size);
                        b.order(ByteOrder.LITTLE_ENDIAN);
                        if (canales < 1) canales = 1;
                        int total;
                        short[] crudo;
                        if (flotante) {
                            FloatBuffer fb = b.asFloatBuffer();
                            total = fb.remaining();
                            float[] ft = new float[total];
                            fb.get(ft);
                            crudo = new short[total];
                            for (int k = 0; k < total; k++) {
                                float v = ft[k] * 32767f;
                                if (v > 32767f) v = 32767f;
                                if (v < -32768f) v = -32768f;
                                crudo[k] = (short) Math.round(v);
                            }
                        } else {
                            ShortBuffer sb = b.asShortBuffer();
                            total = sb.remaining();
                            crudo = new short[total];
                            sb.get(crudo);
                        }
                        int marcos = total / canales;
                        if (est.length < marcos * 2) est = new short[marcos * 2];
                        for (int k = 0; k < marcos; k++) {
                            short l = crudo[k * canales];
                            short r = canales > 1 ? crudo[k * canales + 1] : l;
                            est[2 * k] = l;
                            est[2 * k + 1] = r;
                        }
                        if (rem == null || rem.hzOrigen != hz) {
                            if (rem != null) rem.vaciar();
                            rem = new Remuestreador(hz, os);
                        }
                        rem.alimentar(est, marcos);
                        escritos += marcos;
                    }
                    codec.releaseOutputBuffer(o, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) salidaFin = true;
                    if (prog != null && durUs > 0 && info.presentationTimeUs >= 0) {
                        int pct = (int) Math.min(99, info.presentationTimeUs * 100 / durUs);
                        if (pct != ultimoPct) {
                            ultimoPct = pct;
                            prog.porcentaje(pct);
                        }
                    }
                }
            }
            if (rem != null) rem.vaciar();
            os.flush();
            if (escritos == 0) {
                throw new Exception("No pude leer el audio de este archivo.");
            }
            if (prog != null) prog.porcentaje(100);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignorada) {
                    // ya estaba detenido
                }
                try {
                    codec.release();
                } catch (Exception ignorada) {
                    // ya estaba liberado
                }
            }
            try {
                ex.release();
            } catch (Exception ignorada) {
                // nada
            }
            if (os != null) {
                try {
                    os.close();
                } catch (Exception ignorada) {
                    // nada
                }
            }
        }
    }

    /** Pasa audio estéreo de cualquier frecuencia a 44100 Hz, interpolando, y lo escribe en el archivo. */
    private static final class Remuestreador {
        final int hzOrigen;
        private final boolean directo;
        private final double paso;
        private final OutputStream os;
        private double pos = 0;
        private long visto = 0;
        private short pl = 0;
        private short pr = 0;
        private final byte[] buf = new byte[1 << 16];
        private int nb = 0;

        Remuestreador(int hzOrigen, OutputStream os) {
            this.hzOrigen = hzOrigen <= 0 ? HZ : hzOrigen;
            this.os = os;
            this.directo = this.hzOrigen == HZ;
            this.paso = (double) this.hzOrigen / HZ;
        }

        private void escribir(short l, short r) throws IOException {
            if (nb + 4 > buf.length) {
                os.write(buf, 0, nb);
                nb = 0;
            }
            buf[nb++] = (byte) (l & 0xFF);
            buf[nb++] = (byte) ((l >> 8) & 0xFF);
            buf[nb++] = (byte) (r & 0xFF);
            buf[nb++] = (byte) ((r >> 8) & 0xFF);
        }

        void alimentar(short[] st, int n) throws IOException {
            if (n <= 0) return;
            if (directo) {
                for (int i = 0; i < n; i++) escribir(st[2 * i], st[2 * i + 1]);
                return;
            }
            long fin = visto + n;
            while (true) {
                long i0 = (long) Math.floor(pos);
                if (i0 + 1 >= fin) break;
                double fr = pos - i0;
                short l0;
                short r0;
                if (i0 >= visto) {
                    int k = (int) (i0 - visto);
                    l0 = st[2 * k];
                    r0 = st[2 * k + 1];
                } else {
                    l0 = pl;
                    r0 = pr;
                }
                int k1 = (int) (i0 + 1 - visto);
                short l1 = st[2 * k1];
                short r1 = st[2 * k1 + 1];
                escribir((short) Math.round(l0 + (l1 - l0) * fr),
                        (short) Math.round(r0 + (r1 - r0) * fr));
                pos += paso;
            }
            pl = st[2 * (n - 1)];
            pr = st[2 * (n - 1) + 1];
            visto = fin;
        }

        void vaciar() throws IOException {
            if (nb > 0) {
                os.write(buf, 0, nb);
                nb = 0;
            }
        }
    }

    // ---------- Lectura de los archivos de trabajo ----------

    static final class Lector implements Closeable {
        final long marcos;
        private final RandomAccessFile raf;
        private final byte[] buf = new byte[1 << 16];
        private int bpos = 0;
        private int blen = 0;
        private long pos = 0;

        Lector(File f) throws IOException {
            raf = new RandomAccessFile(f, "r");
            marcos = f.length() / BYTES_MARCO;
        }

        void buscar(long m) throws IOException {
            if (m < 0) m = 0;
            if (m > marcos) m = marcos;
            raf.seek(m * BYTES_MARCO);
            pos = m;
            bpos = 0;
            blen = 0;
        }

        /** Lee hasta n marcos estéreo en dst, desde el marco dstOff. Devuelve los que pudo leer. */
        int leer(short[] dst, int dstOff, int n) throws IOException {
            int hecho = 0;
            while (hecho < n && pos < marcos) {
                if (bpos >= blen) {
                    int len = (int) Math.min(buf.length, (marcos - pos) * BYTES_MARCO);
                    raf.readFully(buf, 0, len);
                    blen = len;
                    bpos = 0;
                }
                int d = 2 * (dstOff + hecho);
                dst[d] = (short) ((buf[bpos] & 0xFF) | (buf[bpos + 1] << 8));
                dst[d + 1] = (short) ((buf[bpos + 2] & 0xFF) | (buf[bpos + 3] << 8));
                bpos += 4;
                pos++;
                hecho++;
            }
            return hecho;
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }
    }

    // ---------- Mezcla ----------

    /** Duración total de la mezcla, en marcos. */
    static long totalMezcla(File voz, Ajustes a) {
        return (long) a.introSeg * HZ + voz.length() / BYTES_MARCO + (long) a.outroSeg * HZ;
    }

    /**
     * Mezcla el tramo [desde, hasta) del resultado. Si salida no es null, le entrega los marcos
     * ya mezclados (multiplicados por escala). Devuelve el pico más alto, antes de la escala.
     */
    static int mezclar(File vozPcm, File musPcm, Ajustes a, long desde, long hasta, double escala,
            Salida salida, Progreso prog, Cancelado can, int pIni, int pFin) throws Exception {
        final int bloque = 8192;
        final long intro = (long) a.introSeg * HZ;
        final long total = totalMezcla(vozPcm, a);
        final double gV = a.volVoz / 100.0;
        final double gM = a.volMus / 100.0;
        final long fIn = Math.max(1, Math.min(2L * HZ, total / 2));
        final long fOut = Math.max(1, Math.min(3L * HZ, total / 2));
        short[] bv = new short[bloque * 2];
        short[] bm = new short[bloque * 2];
        short[] sal = new short[bloque * 2];
        int pico = 0;
        int ultimoPct = -1;
        try (Lector lv = new Lector(vozPcm); Lector lm = new Lector(musPcm)) {
            if (desde - intro > 0) lv.buscar(desde - intro);
            if (lm.marcos > 0) {
                lm.buscar(a.repetir ? desde % lm.marcos : Math.min(desde, lm.marcos));
            }
            for (long p = desde; p < hasta; p += bloque) {
                if (can != null && can.si()) throw new Cancelacion();
                int n = (int) Math.min(bloque, hasta - p);
                Arrays.fill(bv, 0, 2 * n, (short) 0);
                long ini = Math.max(p, intro);
                long fin = Math.min(p + n, intro + lv.marcos);
                if (fin > ini) lv.leer(bv, (int) (ini - p), (int) (fin - ini));
                int hecho = 0;
                while (hecho < n) {
                    int k = lm.leer(bm, hecho, n - hecho);
                    hecho += k;
                    if (hecho < n) {
                        if (a.repetir && lm.marcos > 0) {
                            lm.buscar(0);
                        } else {
                            Arrays.fill(bm, 2 * hecho, 2 * n, (short) 0);
                            break;
                        }
                    }
                }
                for (int i = 0; i < n; i++) {
                    long q = p + i;
                    double g = 1.0;
                    if (a.desvanecer) {
                        if (q < fIn) g = (double) q / fIn;
                        long r = total - q;
                        if (r < fOut) g = Math.min(g, (double) r / fOut);
                    }
                    double gm = gM * g;
                    for (int ch = 0; ch < 2; ch++) {
                        double v = bv[2 * i + ch] * gV + bm[2 * i + ch] * gm;
                        int ab = (int) Math.abs(v);
                        if (ab > pico) pico = ab;
                        long s = Math.round(v * escala);
                        if (s > 32767) s = 32767;
                        if (s < -32768) s = -32768;
                        sal[2 * i + ch] = (short) s;
                    }
                }
                if (salida != null) salida.marcos(sal, n);
                if (prog != null) {
                    int pct = pIni + (int) ((p - desde + n) * (pFin - pIni) / Math.max(1, hasta - desde));
                    if (pct != ultimoPct) {
                        ultimoPct = pct;
                        prog.porcentaje(pct);
                    }
                }
            }
        }
        return pico;
    }

    /** Un fragmento de la mezcla (hasta 20 segundos) para escucharlo antes de exportar. */
    static short[] vistaPrevia(File voz, File mus, Ajustes a) throws Exception {
        long total = totalMezcla(voz, a);
        long intro = (long) a.introSeg * HZ;
        long desde = Math.min(intro + 3L * HZ, Math.max(0, total - 20L * HZ));
        long hasta = Math.min(total, desde + 20L * HZ);
        if (hasta <= desde) {
            desde = 0;
            hasta = Math.min(total, 20L * HZ);
        }
        final int n = (int) (hasta - desde);
        final short[] datos = new short[n * 2];
        final int[] fila = {0};
        mezclar(voz, mus, a, desde, hasta, 1.0, (st, k) -> {
            System.arraycopy(st, 0, datos, fila[0] * 2, k * 2);
            fila[0] += k;
        }, null, null, 0, 0);
        return datos;
    }

    private static void aBytes(short[] st, int n, byte[] dst) {
        for (int i = 0; i < n * 2; i++) {
            dst[2 * i] = (byte) (st[i] & 0xFF);
            dst[2 * i + 1] = (byte) ((st[i] >> 8) & 0xFF);
        }
    }

    /** Exporta la mezcla completa a un archivo M4A (AAC) o WAV. */
    static void exportarMezcla(File voz, File mus, Ajustes a, boolean wav, File destino,
            Progreso prog, Cancelado can) throws Exception {
        long total = totalMezcla(voz, a);
        if (total <= 0) throw new Exception("No hay audio que exportar.");
        int pico = mezclar(voz, mus, a, 0, total, 1.0, null, prog, can, 0, 15);
        double escala = pico > 32000 ? 32000.0 / pico : 1.0;
        final byte[] bytes = new byte[8192 * 4];
        if (wav) {
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(destino), 1 << 16)) {
                os.write(cabeceraWav(total * BYTES_MARCO));
                mezclar(voz, mus, a, 0, total, escala, (st, n) -> {
                    aBytes(st, n, bytes);
                    os.write(bytes, 0, n * 4);
                }, prog, can, 15, 100);
            }
        } else {
            CodificadorAac e = new CodificadorAac(destino);
            try {
                mezclar(voz, mus, a, 0, total, escala, (st, n) -> {
                    aBytes(st, n, bytes);
                    e.agregar(bytes, n * 4);
                }, prog, can, 15, 99);
                e.terminar();
            } catch (Exception ex) {
                e.abortar();
                throw ex;
            }
            if (prog != null) prog.porcentaje(100);
        }
    }

    private static byte[] cabeceraWav(long datos) {
        ByteBuffer b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[] {'R', 'I', 'F', 'F'});
        b.putInt((int) (36 + datos));
        b.put(new byte[] {'W', 'A', 'V', 'E'});
        b.put(new byte[] {'f', 'm', 't', ' '});
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort((short) 2);
        b.putInt(HZ);
        b.putInt(HZ * BYTES_MARCO);
        b.putShort((short) BYTES_MARCO);
        b.putShort((short) 16);
        b.put(new byte[] {'d', 'a', 't', 'a'});
        b.putInt((int) datos);
        return b.array();
    }

    /** Pasa un PCM de trabajo a un archivo M4A (AAC). Se usa para el audio de los videos. */
    static void pcmAM4a(File pcm, File destino, Progreso prog, Cancelado can) throws Exception {
        long total = pcm.length() / BYTES_MARCO;
        CodificadorAac e = new CodificadorAac(destino);
        try (FileInputStream in = new FileInputStream(pcm)) {
            byte[] buf = new byte[1 << 15];
            long hecho = 0;
            int ultimoPct = -1;
            int resto = 0;
            int leidos;
            while ((leidos = in.read(buf, resto, buf.length - resto)) > 0) {
                if (can != null && can.si()) throw new Cancelacion();
                int total2 = resto + leidos;
                int n = total2 - total2 % BYTES_MARCO;
                resto = total2 - n;
                if (n <= 0) continue;
                e.agregar(buf, n);
                System.arraycopy(buf, n, buf, 0, resto);
                hecho += n / BYTES_MARCO;
                if (prog != null && total > 0) {
                    int pct = (int) Math.min(99, hecho * 100 / total);
                    if (pct != ultimoPct) {
                        ultimoPct = pct;
                        prog.porcentaje(pct);
                    }
                }
            }
            e.terminar();
        } catch (Exception ex) {
            e.abortar();
            throw ex;
        }
        if (prog != null) prog.porcentaje(100);
    }

    // ---------- Codificador AAC ----------

    static final class CodificadorAac {
        private final MediaCodec codec;
        private final MediaMuxer muxer;
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        private int pista = -1;
        private boolean iniciado = false;
        private long marcos = 0;

        CodificadorAac(File salida) throws Exception {
            MediaFormat f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, HZ, 2);
            f.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            f.setInteger(MediaFormat.KEY_BIT_RATE, 160000);
            f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 << 16);
            MediaCodec cod = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            try {
                cod.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                cod.start();
            } catch (Exception e) {
                cod.release();
                throw e;
            }
            codec = cod;
            try {
                muxer = new MediaMuxer(salida.getAbsolutePath(),
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (Exception e) {
                cod.stop();
                cod.release();
                throw e;
            }
        }

        void agregar(byte[] pcm, int len) {
            int off = 0;
            while (off < len) {
                int i = codec.dequeueInputBuffer(10000);
                if (i < 0) {
                    vaciar(10000);
                    continue;
                }
                ByteBuffer b = codec.getInputBuffer(i);
                int n = Math.min(len - off, b.capacity());
                n -= n % BYTES_MARCO;
                b.clear();
                b.put(pcm, off, n);
                codec.queueInputBuffer(i, 0, n, marcos * 1000000L / HZ, 0);
                marcos += n / BYTES_MARCO;
                off += n;
                vaciar(0);
            }
        }

        void terminar() {
            int i;
            while ((i = codec.dequeueInputBuffer(10000)) < 0) vaciar(10000);
            codec.queueInputBuffer(i, 0, 0, marcos * 1000000L / HZ,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            while (!vaciar(10000)) {
                // sigue vaciando hasta el final del flujo
            }
            cerrar();
        }

        void abortar() {
            try {
                codec.stop();
            } catch (Exception ignorada) {
                // nada
            }
            cerrar();
        }

        private void cerrar() {
            try {
                codec.stop();
            } catch (Exception ignorada) {
                // ya estaba detenido
            }
            try {
                codec.release();
            } catch (Exception ignorada) {
                // nada
            }
            try {
                if (iniciado) muxer.stop();
            } catch (Exception ignorada) {
                // nada
            }
            try {
                muxer.release();
            } catch (Exception ignorada) {
                // nada
            }
        }

        /** Pasa al archivo lo que el codificador ya tiene. Devuelve true al llegar al final. */
        private boolean vaciar(long esperaUs) {
            while (true) {
                int o = codec.dequeueOutputBuffer(info, esperaUs);
                if (o == MediaCodec.INFO_TRY_AGAIN_LATER) return false;
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    pista = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    iniciado = true;
                    continue;
                }
                if (o < 0) continue;
                ByteBuffer b = codec.getOutputBuffer(o);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                if (info.size > 0 && b != null && iniciado) {
                    b.position(info.offset);
                    b.limit(info.offset + info.size);
                    muxer.writeSampleData(pista, b, info);
                }
                codec.releaseOutputBuffer(o, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return true;
            }
        }
    }

    // ---------- Sonido de transición entre imágenes ----------

    static final String[] NOMBRES_TRANSICION = {"Campanita suave", "Clic corto", "Dos tonos"};

    /** Genera con código el sonido de transición: estéreo, a 44100 Hz, sin volumen aplicado. */
    static short[] sonidoTransicion(int tipo) {
        double dur = tipo == 1 ? 0.12 : (tipo == 2 ? 0.40 : 0.60);
        int n = (int) (dur * HZ);
        short[] r = new short[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / (double) HZ;
            double v;
            if (tipo == 1) {
                v = Math.sin(2 * Math.PI * 1600 * t) * Math.exp(-t * 45);
            } else if (tipo == 2) {
                if (t < 0.15) {
                    v = Math.sin(2 * Math.PI * 660 * t) * Math.exp(-t * 9);
                } else {
                    double u = t - 0.15;
                    v = Math.sin(2 * Math.PI * 880 * u) * Math.exp(-u * 9);
                }
            } else {
                v = (Math.sin(2 * Math.PI * 1046.5 * t) * 0.7
                        + Math.sin(2 * Math.PI * 2093.0 * t) * 0.25
                        + Math.sin(2 * Math.PI * 2637.0 * t) * 0.1) * Math.exp(-t * 6.5);
            }
            if (t < 0.004) v *= t / 0.004;
            int resto = n - i;
            if (resto < 441) v *= resto / 441.0;
            short s = (short) Math.round(v * 0.8 * 32767);
            r[2 * i] = s;
            r[2 * i + 1] = s;
        }
        return r;
    }

    /**
     * Copia el audio de trabajo sumando el sonido de transición en cada momento (en segundos).
     * El volumen va de 0 a 100.
     */
    static void agregarTransiciones(File entrada, File salida, double[] segundos, int tipo,
            int volPct, Progreso prog, Cancelado can) throws Exception {
        final short[] cue = sonidoTransicion(tipo);
        final int cl = cue.length / 2;
        final double g = volPct / 100.0;
        final long[] pos = new long[segundos.length];
        for (int i = 0; i < pos.length; i++) pos[i] = Math.round(segundos[i] * HZ);
        final int bloque = 8192;
        short[] b = new short[bloque * 2];
        byte[] bytes = new byte[bloque * 4];
        try (Lector l = new Lector(entrada);
                OutputStream os = new BufferedOutputStream(new FileOutputStream(salida), 1 << 16)) {
            long total = l.marcos;
            long p = 0;
            int ultimoPct = -1;
            while (p < total) {
                if (can != null && can.si()) throw new Cancelacion();
                int n = l.leer(b, 0, (int) Math.min(bloque, total - p));
                if (n <= 0) break;
                for (long c : pos) {
                    if (c >= p + n || c + cl <= p) continue;
                    long ini = Math.max(c, p);
                    long fin = Math.min(c + cl, p + n);
                    for (long q = ini; q < fin; q++) {
                        int i = (int) (q - p);
                        int k = (int) (q - c);
                        for (int ch = 0; ch < 2; ch++) {
                            long v = Math.round(b[2 * i + ch] + cue[2 * k + ch] * g);
                            if (v > 32767) v = 32767;
                            if (v < -32768) v = -32768;
                            b[2 * i + ch] = (short) v;
                        }
                    }
                }
                aBytes(b, n, bytes);
                os.write(bytes, 0, n * 4);
                p += n;
                if (prog != null && total > 0) {
                    int pct = (int) Math.min(100, p * 100 / total);
                    if (pct != ultimoPct) {
                        ultimoPct = pct;
                        prog.porcentaje(pct);
                    }
                }
            }
        }
        if (prog != null) prog.porcentaje(100);
    }

    // ---------- Guardar en el teléfono ----------

    static final String CARPETA = "Estudio Cafe Tecnologico";

    /** Copia un archivo de trabajo a Música o Películas, y devuelve su dirección. */
    static Uri guardar(Context c, File origen, String nombre, String mime, boolean video)
            throws Exception {
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, nombre);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_MUSIC) + "/" + CARPETA);
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri base = video
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = cr.insert(base, v);
        if (uri == null) {
            throw new Exception("No pude crear el archivo en el almacenamiento del teléfono.");
        }
        try {
            OutputStream os = cr.openOutputStream(uri);
            if (os == null) throw new Exception("No pude abrir el archivo para guardarlo.");
            try (InputStream in = new FileInputStream(origen); OutputStream o = os) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            }
            ContentValues fin = new ContentValues();
            fin.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, fin, null, null);
        } catch (Exception e) {
            try {
                cr.delete(uri, null, null);
            } catch (Exception ignorada) {
                // nada
            }
            throw e;
        }
        return uri;
    }

    /** Guarda un texto como archivo TXT en Documentos y devuelve su dirección. */
    static Uri guardarTexto(Context c, String texto, String nombre) throws Exception {
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, nombre);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + CARPETA);
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        if (uri == null) {
            throw new Exception("No pude crear el archivo en el almacenamiento del teléfono.");
        }
        try {
            OutputStream os = cr.openOutputStream(uri);
            if (os == null) throw new Exception("No pude abrir el archivo para guardarlo.");
            try (OutputStream o = os) {
                o.write(texto.getBytes(StandardCharsets.UTF_8));
            }
            ContentValues fin = new ContentValues();
            fin.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, fin, null, null);
        } catch (Exception e) {
            try {
                cr.delete(uri, null, null);
            } catch (Exception ignorada) {
                // nada
            }
            throw e;
        }
        return uri;
    }

    /** Cambia el contenido de un TXT ya guardado. */
    static void reescribirTexto(Context c, Uri uri, String texto) throws Exception {
        OutputStream os = c.getContentResolver().openOutputStream(uri, "wt");
        if (os == null) throw new Exception("No pude abrir el archivo para guardar los cambios.");
        try (OutputStream o = os) {
            o.write(texto.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** El nombre con el que quedó guardado el archivo (el sistema lo cambia si ya existía). */
    static String nombreGuardado(Context c, Uri uri, String porDefecto) {
        try (Cursor q = c.getContentResolver().query(uri,
                new String[] {MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
            if (q != null && q.moveToFirst()) {
                String n = q.getString(0);
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Exception ignorada) {
            // se usa el nombre pedido
        }
        return porDefecto;
    }
}

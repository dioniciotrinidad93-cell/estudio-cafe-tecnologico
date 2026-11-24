package com.cafetecnologico.estudio;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import org.json.JSONArray;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Transcripción de audios con Gemini. El audio de trabajo (44100 Hz, estéreo) se pasa a 16000 Hz
 * mono, se corta en partes de unos 10 minutos en el lugar más silencioso cercano al corte, se
 * comprime (AAC) y se manda parte por parte.
 */
final class Transcribir {

    private Transcribir() {}

    static final int HZ = 16000;
    static final int PARTE_AAC_SEG = 600;
    static final int PARTE_WAV_SEG = 240;
    /** Lo más largo que se acepta: tres horas. */
    static final int MAX_SEG = 3 * 3600;

    static final String[] IDIOMAS = {
        "Detectar el idioma automáticamente", "Español", "Inglés", "Portugués", "Francés"
    };
    private static final String[] IDIOMAS_TEXTO = {"", "español", "inglés", "portugués", "francés"};

    /** En cuántas partes se mandará un audio de esa duración. */
    static int partesDe(long segundos) {
        long resto = segundos;
        int partes = 1;
        while (resto > PARTE_AAC_SEG + 90) {
            resto -= PARTE_AAC_SEG;
            partes++;
        }
        return partes;
    }

    private static Audio.Progreso escalar(final Audio.Progreso p, final int ini, final int fin) {
        return v -> p.porcentaje(ini + v * (fin - ini) / 100);
    }

    // ---------- Proceso completo ----------

    /**
     * Transcribe el audio. Cada parte terminada se va agregando a acumulado, para poder guardar lo
     * que haya si algo falla a la mitad. Devuelve el texto completo.
     */
    static String transcribir(File pcm44, File mono, String clave, int idioma, boolean hablantes,
            Audio.Progreso prog, Audio.Cancelado can, StringBuilder acumulado) throws Exception {
        aMono16k(pcm44, mono, escalar(prog, 0, 10), can);
        long total = mono.length() / 2;
        if (total < HZ / 2) throw new Exception("El audio es demasiado corto para transcribirlo.");
        boolean aac = probarAac();
        long[] lim = cortes(mono, total, aac ? PARTE_AAC_SEG : PARTE_WAV_SEG);
        int m = lim.length - 1;
        String[] modelos = Gemini.orden(clave, false);
        for (int i = 0; i < m; i++) {
            if (can != null && can.si()) throw new Audio.Cancelacion();
            short[] datos = leerMono(mono, lim[i], lim[i + 1]);
            byte[] bytes = aac ? codificarAac(datos) : aWav(datos);
            JSONArray partes = new JSONArray();
            partes.put(Gemini.texto(instruccion(idioma, hablantes, i + 1, m, acumulado)));
            partes.put(Gemini.datos(aac ? "audio/aac" : "audio/wav", bytes));
            String t = limpiar(Gemini.generar(clave, modelos, partes, 240, can));
            if (!t.isEmpty()) {
                if (acumulado.length() > 0) acumulado.append("\n\n");
                acumulado.append(t);
            }
            prog.porcentaje(10 + (i + 1) * 90 / m);
        }
        String r = acumulado.toString().trim();
        if (r.isEmpty()) {
            throw new Exception("Gemini no devolvió texto. Puede que el audio no tenga voz.");
        }
        return r;
    }

    private static String instruccion(int idioma, boolean hablantes, int parte, int partes,
            StringBuilder acumulado) {
        StringBuilder s = new StringBuilder();
        s.append("Transcribe fielmente este audio ");
        s.append(idioma <= 0 ? "en el idioma en que se habla" : "en " + IDIOMAS_TEXTO[idioma]);
        s.append(". Escribe únicamente la transcripción, sin introducción, sin comentarios y sin ")
                .append("resumir. Usa puntuación y mayúsculas correctas y separa los párrafos con una ")
                .append("línea en blanco. Si algo no se entiende, escribe [inaudible]. No uses Markdown.");
        if (hablantes) {
            s.append(" Cuando cambie la persona que habla, empieza un párrafo nuevo con la etiqueta ")
                    .append("«Hablante 1:», «Hablante 2:», etc., usando el mismo número para la misma voz.");
        }
        if (partes > 1) {
            s.append(" Este audio es la parte ").append(parte).append(" de ").append(partes)
                    .append(" de una grabación más larga: transcribe solo esta parte.");
            if (parte > 1 && acumulado.length() > 0) {
                int desde = Math.max(0, acumulado.length() - 300);
                s.append(" La parte anterior terminó con: «").append(acumulado.substring(desde))
                        .append("». No la repitas; continúa desde donde empieza este audio.");
            }
        }
        return s.toString();
    }

    private static String limpiar(String t) {
        t = t.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
        }
        if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        return t.trim();
    }

    // ---------- Pasar a 16000 Hz mono ----------

    /** Promedia los canales y baja la frecuencia a 16000 Hz promediando las muestras de cada tramo. */
    static void aMono16k(File pcm, File salida, Audio.Progreso prog, Audio.Cancelado can)
            throws Exception {
        final double paso = (double) Audio.HZ / HZ;
        final int bloque = 16384;
        short[] st = new short[bloque * 2];
        try (Audio.Lector l = new Audio.Lector(pcm);
                OutputStream os = new BufferedOutputStream(new FileOutputStream(salida), 1 << 16)) {
            long i = 0;
            long salidas = 0;
            double limite = paso;
            double acc = 0;
            double accW = 0;
            int ultimoPct = -1;
            while (true) {
                if (can != null && can.si()) throw new Audio.Cancelacion();
                int n = l.leer(st, 0, bloque);
                if (n <= 0) break;
                for (int k = 0; k < n; k++) {
                    double v = (st[2 * k] + st[2 * k + 1]) * 0.5;
                    double ini = i;
                    double finMuestra = i + 1;
                    while (true) {
                        double fin = Math.min(finMuestra, limite);
                        double w = fin - ini;
                        acc += v * w;
                        accW += w;
                        ini = fin;
                        if (fin >= limite - 1e-9) {
                            int s = accW > 0 ? (int) Math.round(acc / accW) : 0;
                            os.write(s & 0xFF);
                            os.write((s >> 8) & 0xFF);
                            acc = 0;
                            accW = 0;
                            salidas++;
                            limite = (salidas + 1) * paso;
                        }
                        if (ini >= finMuestra - 1e-9) break;
                    }
                    i++;
                }
                if (prog != null && l.marcos > 0) {
                    int pct = (int) Math.min(99, i * 100 / l.marcos);
                    if (pct != ultimoPct) {
                        ultimoPct = pct;
                        prog.porcentaje(pct);
                    }
                }
            }
        }
        if (prog != null) prog.porcentaje(100);
    }

    // ---------- Cortes en los silencios ----------

    /** Devuelve los límites de las partes: {0, corte1, corte2, ..., total}, en muestras de 16 kHz. */
    static long[] cortes(File mono, long total, int parteSeg) throws IOException {
        long parte = (long) parteSeg * HZ;
        ArrayList<Long> l = new ArrayList<>();
        l.add(0L);
        long ini = 0;
        try (RandomAccessFile raf = new RandomAccessFile(mono, "r")) {
            while (total - ini > parte + 90L * HZ) {
                long objetivo = ini + parte;
                long c = silencio(raf, objetivo - 20L * HZ, objetivo);
                l.add(c);
                ini = c;
            }
        }
        l.add(total);
        long[] r = new long[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    /** El punto más silencioso (ventanas de 100 ms) entre dos posiciones. */
    private static long silencio(RandomAccessFile raf, long desde, long hasta) throws IOException {
        int n = (int) (hasta - desde);
        byte[] b = new byte[n * 2];
        raf.seek(desde * 2);
        raf.readFully(b);
        final int ventana = HZ / 10;
        long mejor = Long.MAX_VALUE;
        int pos = n / 2;
        for (int s = 0; s + ventana <= n; s += ventana / 2) {
            long suma = 0;
            for (int k = 0; k < ventana; k++) {
                int idx = 2 * (s + k);
                short v = (short) ((b[idx] & 0xFF) | (b[idx + 1] << 8));
                suma += Math.abs((int) v);
            }
            if (suma < mejor) {
                mejor = suma;
                pos = s + ventana / 2;
            }
        }
        return desde + pos;
    }

    private static short[] leerMono(File f, long ini, long fin) throws IOException {
        int n = (int) (fin - ini);
        byte[] b = new byte[n * 2];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek(ini * 2);
            raf.readFully(b);
        }
        short[] r = new short[n];
        for (int i = 0; i < n; i++) {
            r[i] = (short) ((b[2 * i] & 0xFF) | (b[2 * i + 1] << 8));
        }
        return r;
    }

    // ---------- Comprimir cada parte ----------

    private static boolean probarAac() {
        try {
            return codificarAac(new short[HZ]).length > 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Codifica a AAC (16 kHz, mono, 24 kbps) en formato ADTS, que Gemini acepta como audio/aac. */
    static byte[] codificarAac(short[] m) throws Exception {
        MediaFormat f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, HZ, 1);
        f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        f.setInteger(MediaFormat.KEY_BIT_RATE, 24000);
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 << 15);
        MediaCodec cod = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, m.length / 8));
        try {
            cod.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            cod.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] tmp = new byte[1 << 15];
            int pos = 0;
            boolean enviado = false;
            boolean fin = false;
            int ociosos = 0;
            while (!fin) {
                boolean avanzo = false;
                if (!enviado) {
                    int i = cod.dequeueInputBuffer(10000);
                    if (i >= 0) {
                        ByteBuffer b = cod.getInputBuffer(i);
                        b.clear();
                        int cap = Math.min(b.capacity(), tmp.length) & ~1;
                        int muestras = Math.min(m.length - pos, cap / 2);
                        long t = (long) pos * 1000000L / HZ;
                        if (muestras <= 0) {
                            cod.queueInputBuffer(i, 0, 0, t, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            enviado = true;
                        } else {
                            for (int k = 0; k < muestras; k++) {
                                tmp[2 * k] = (byte) (m[pos + k] & 0xFF);
                                tmp[2 * k + 1] = (byte) ((m[pos + k] >> 8) & 0xFF);
                            }
                            b.put(tmp, 0, muestras * 2);
                            cod.queueInputBuffer(i, 0, muestras * 2, t, 0);
                            pos += muestras;
                        }
                        avanzo = true;
                    }
                }
                int o = cod.dequeueOutputBuffer(info, 10000);
                if (o >= 0) {
                    avanzo = true;
                    ByteBuffer ob = cod.getOutputBuffer(o);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0
                            && ob != null) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        byte[] dato = new byte[info.size];
                        ob.get(dato);
                        byte[] cab = adts(info.size + 7);
                        out.write(cab, 0, cab.length);
                        out.write(dato, 0, dato.length);
                    }
                    cod.releaseOutputBuffer(o, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) fin = true;
                } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    avanzo = true;
                }
                if (avanzo) {
                    ociosos = 0;
                } else if (++ociosos > 3000) {
                    throw new Exception("El codificador de audio del teléfono dejó de responder.");
                }
            }
        } finally {
            try {
                cod.stop();
            } catch (Exception ignorada) {
                // nada
            }
            try {
                cod.release();
            } catch (Exception ignorada) {
                // nada
            }
        }
        return out.toByteArray();
    }

    /** Cabecera ADTS de 7 bytes: AAC LC, 16000 Hz, un canal. */
    static byte[] adts(int largo) {
        final int perfil = 2;
        final int frecuencia = 8; // 16000 Hz
        final int canales = 1;
        byte[] h = new byte[7];
        h[0] = (byte) 0xFF;
        h[1] = (byte) 0xF1;
        h[2] = (byte) (((perfil - 1) << 6) + (frecuencia << 2) + (canales >> 2));
        h[3] = (byte) (((canales & 3) << 6) + (largo >> 11));
        h[4] = (byte) ((largo & 0x7FF) >> 3);
        h[5] = (byte) (((largo & 7) << 5) + 0x1F);
        h[6] = (byte) 0xFC;
        return h;
    }

    /** WAV de 16 bits, 16000 Hz, mono. Solo se usa si el teléfono no puede codificar AAC. */
    static byte[] aWav(short[] m) {
        int datos = m.length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + datos).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[] {'R', 'I', 'F', 'F'});
        b.putInt(36 + datos);
        b.put(new byte[] {'W', 'A', 'V', 'E'});
        b.put(new byte[] {'f', 'm', 't', ' '});
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort((short) 1);
        b.putInt(HZ);
        b.putInt(HZ * 2);
        b.putShort((short) 2);
        b.putShort((short) 16);
        b.put(new byte[] {'d', 'a', 't', 'a'});
        b.putInt(datos);
        for (int i = 0; i < m.length; i++) b.putShort(m[i]);
        return b.array();
    }
}

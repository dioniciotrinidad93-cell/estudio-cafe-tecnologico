package com.cafetecnologico.estudio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Graba audio del micrófono directo al formato de trabajo de Estudio Café Tecnológico (16 bits,
 * estéreo, 44100 Hz), para poder usarlo enseguida en la mezcla, en un video o con efectos de voz.
 * El micrófono es mono: el mismo sonido se escribe en los dos canales.
 *
 * Sugerencia de Mónica, de Colombia.
 */
final class Grabadora {

    /** Lo máximo que dura una grabación: 30 minutos. */
    static final int MAX_SEG = 30 * 60;

    static final int DETENIDA = 0;
    static final int GRABANDO = 1;
    static final int EN_PAUSA = 2;

    private final File destino;
    private AudioRecord ar;
    private Thread hilo;
    private volatile int estado = DETENIDA;
    private volatile long marcos = 0;
    private volatile String error = null;
    private volatile boolean limite = false;

    Grabadora(File destino) {
        this.destino = destino;
    }

    int estado() {
        return estado;
    }

    /** Cuántos segundos se llevan grabados. */
    long segundos() {
        return marcos / Audio.HZ;
    }

    long marcos() {
        return marcos;
    }

    /** Un mensaje si la grabación falló sola, o null. */
    String error() {
        return error;
    }

    /** Verdadero si la grabación llegó sola al máximo de tiempo. */
    boolean llegoAlLimite() {
        return limite;
    }

    /** Empieza a grabar. Debe llamarse con el permiso del micrófono ya concedido. */
    void iniciar() throws Exception {
        if (estado != DETENIDA) return;
        int min = AudioRecord.getMinBufferSize(Audio.HZ, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            throw new Exception("Este teléfono no permite grabar con esta calidad.");
        }
        int buffer = Math.max(min, Audio.HZ); // medio segundo, en bytes
        AudioRecord r;
        try {
            r = new AudioRecord(MediaRecorder.AudioSource.MIC, Audio.HZ,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer);
        } catch (Exception e) {
            throw new Exception("No pude usar el micrófono. Revisa que la aplicación tenga el "
                    + "permiso del micrófono.");
        }
        if (r.getState() != AudioRecord.STATE_INITIALIZED) {
            r.release();
            throw new Exception("No pude usar el micrófono. Puede que otra aplicación lo esté "
                    + "usando, por ejemplo una llamada.");
        }
        destino.delete();
        final OutputStream os;
        try {
            os = new BufferedOutputStream(new FileOutputStream(destino), 1 << 16);
        } catch (Exception e) {
            r.release();
            throw new Exception("No pude crear el archivo de la grabación. Revisa el espacio del "
                    + "teléfono.");
        }
        ar = r;
        marcos = 0;
        error = null;
        limite = false;
        try {
            r.startRecording();
        } catch (Exception e) {
            r.release();
            ar = null;
            try {
                os.close();
            } catch (Exception ignorada) {
                // nada
            }
            throw new Exception("No pude empezar a grabar. Puede que otra aplicación esté usando el "
                    + "micrófono.");
        }
        estado = GRABANDO;
        hilo = new Thread(() -> {
            short[] m = new short[4096];
            byte[] b = new byte[4096 * 4];
            try {
                while (estado != DETENIDA) {
                    if (estado == EN_PAUSA) {
                        Thread.sleep(40);
                        continue;
                    }
                    int n = r.read(m, 0, m.length);
                    if (n < 0) {
                        if (estado != GRABANDO) continue;
                        error = "El micrófono dejó de funcionar. Lo grabado hasta ahora se conserva.";
                        break;
                    }
                    if (n == 0) continue;
                    if (estado != GRABANDO) continue;
                    for (int i = 0; i < n; i++) {
                        short s = m[i];
                        byte lo = (byte) (s & 0xFF);
                        byte hi = (byte) ((s >> 8) & 0xFF);
                        b[4 * i] = lo;
                        b[4 * i + 1] = hi;
                        b[4 * i + 2] = lo;
                        b[4 * i + 3] = hi;
                    }
                    os.write(b, 0, n * 4);
                    marcos += n;
                    if (marcos >= (long) MAX_SEG * Audio.HZ) {
                        limite = true;
                        break;
                    }
                }
            } catch (Exception e) {
                error = "No pude guardar la grabación. Revisa el espacio del teléfono. Lo grabado "
                        + "hasta ahora se conserva.";
            } finally {
                try {
                    os.flush();
                    os.close();
                } catch (Exception ignorada) {
                    // nada
                }
            }
        });
        hilo.start();
    }

    void pausar() {
        if (estado != GRABANDO) return;
        estado = EN_PAUSA;
        try {
            ar.stop();
        } catch (Exception ignorada) {
            // nada
        }
    }

    void reanudar() throws Exception {
        if (estado != EN_PAUSA) return;
        try {
            ar.startRecording();
        } catch (Exception e) {
            throw new Exception("No pude seguir grabando. Puede que otra aplicación esté usando el "
                    + "micrófono.");
        }
        estado = GRABANDO;
    }

    /** Termina la grabación y libera el micrófono. Devuelve cuántos marcos se grabaron. */
    long detener() {
        estado = DETENIDA;
        if (hilo != null) {
            try {
                hilo.join(3000);
            } catch (InterruptedException ignorada) {
                // nada
            }
            hilo = null;
        }
        if (ar != null) {
            try {
                ar.stop();
            } catch (Exception ignorada) {
                // nada
            }
            try {
                ar.release();
            } catch (Exception ignorada) {
                // nada
            }
            ar = null;
        }
        return marcos;
    }

    /** Termina y borra lo grabado. */
    void descartar() {
        detener();
        destino.delete();
        marcos = 0;
    }
}

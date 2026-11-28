package com.cafetecnologico.estudio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Mantiene viva la grabación cuando sales de la aplicación, por ejemplo para grabar un tutorial en
 * otra aplicación. Android solo deja grabar con el micrófono en segundo plano si hay un servicio en
 * primer plano con un aviso visible; este es ese servicio. Mientras dura la grabación, la barra de
 * notificaciones muestra un aviso con los botones Pausar (o Seguir grabando) y Terminar, que los
 * lectores de pantalla leen como cualquier otro botón. Al tocar el aviso se vuelve a la aplicación.
 *
 * La grabadora vive aquí (en grabadora) y no en la pantalla, para que la grabación no se pierda si
 * Android cierra la pantalla mientras grabas en segundo plano.
 *
 * Sugerencia de la versión 0.7: la grabadora sigue funcionando al salir de la aplicación.
 */
public class ServicioGrabacion extends Service {

    static final String ACCION_PAUSAR = "com.cafetecnologico.estudio.GRABACION_PAUSAR";
    static final String ACCION_TERMINAR = "com.cafetecnologico.estudio.GRABACION_TERMINAR";

    private static final String CANAL = "grabacion";
    private static final int ID_AVISO = 7;
    /** Si pasado este tiempo no empezó ninguna grabación, el servicio se apaga solo. */
    private static final long ESPERA_MAXIMA_MS = 90 * 1000L;

    private static final int ESPERANDO = 0;
    private static final int GRABANDO = 1;
    private static final int EN_PAUSA = 2;
    private static final int TERMINADA = 3;

    /** La grabación en curso. La pantalla y el servicio la comparten. */
    static volatile Grabadora grabadora;

    private final Handler latido = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock bloqueo;
    private long inicio;
    private int claveActual = -1;
    private long segundosAviso = -1;
    private boolean enPrimerPlano = false;

    /** Arranca el servicio. Hay que llamarlo con la pantalla a la vista, antes de empezar a grabar. */
    static void iniciar(Context c) {
        grabadora = null;
        c.startForegroundService(new Intent(c, ServicioGrabacion.class));
    }

    /** Apaga el servicio, quita el aviso y suelta la grabadora. */
    static void soltar(Context c) {
        grabadora = null;
        c.stopService(new Intent(c, ServicioGrabacion.class));
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(ID_AVISO);
        } catch (Exception ignorada) {
            // nada
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!enPrimerPlano) {
            inicio = System.currentTimeMillis();
            crearCanal();
            try {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                bloqueo = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "estudio:grabacion");
                bloqueo.acquire(35L * 60 * 1000);
            } catch (Exception ignorada) {
                // se sigue sin el bloqueo
            }
            try {
                Notification n = aviso(ESPERANDO);
                if (Build.VERSION.SDK_INT >= 30) {
                    startForeground(ID_AVISO, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                } else {
                    startForeground(ID_AVISO, n);
                }
                enPrimerPlano = true;
                claveActual = ESPERANDO;
            } catch (Exception e) {
                // Android no dejó pasar a primer plano: la grabación solo funcionará con la
                // aplicación a la vista.
                stopSelf();
                return START_NOT_STICKY;
            }
            latido.postDelayed(vigilar, 1000);
        }
        if (intent != null && intent.getAction() != null) {
            Grabadora g = grabadora;
            if (ACCION_PAUSAR.equals(intent.getAction()) && g != null) {
                if (g.estado() == Grabadora.GRABANDO) {
                    g.pausar();
                } else if (g.estado() == Grabadora.EN_PAUSA) {
                    try {
                        g.reanudar();
                    } catch (Exception ignorada) {
                        // sigue en pausa
                    }
                }
            } else if (ACCION_TERMINAR.equals(intent.getAction()) && g != null) {
                g.detener();
            }
            actualizar();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        latido.removeCallbacks(vigilar);
        try {
            if (bloqueo != null && bloqueo.isHeld()) bloqueo.release();
        } catch (Exception ignorada) {
            // nada
        }
        bloqueo = null;
        super.onDestroy();
    }

    /** Cada segundo revisa la grabación y mantiene al día el aviso. */
    private final Runnable vigilar = new Runnable() {
        @Override
        public void run() {
            Grabadora g = grabadora;
            if (g == null) {
                if (System.currentTimeMillis() - inicio > ESPERA_MAXIMA_MS) {
                    stopSelf();
                    return;
                }
            } else if (g.estado() != Grabadora.DETENIDA && (g.error() != null || g.llegoAlLimite())) {
                // La grabación terminó sola (máximo de tiempo o fallo del micrófono): se guarda lo
                // grabado y se libera el micrófono aunque la pantalla no esté a la vista.
                g.detener();
            }
            actualizar();
            if (claveActual != TERMINADA) latido.postDelayed(this, 1000);
        }
    };

    private int claveDe(Grabadora g) {
        if (g == null) return ESPERANDO;
        if (g.estado() == Grabadora.GRABANDO) return GRABANDO;
        if (g.estado() == Grabadora.EN_PAUSA) return EN_PAUSA;
        return TERMINADA;
    }

    private void actualizar() {
        Grabadora g = grabadora;
        int clave = claveDe(g);
        long seg = g == null ? 0 : g.segundos();
        // En pausa se vuelve a escribir el aviso solo si cambió el tiempo, que es lo único que dice.
        if (clave == claveActual && !(clave == EN_PAUSA && seg != segundosAviso)) return;
        claveActual = clave;
        segundosAviso = seg;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(ID_AVISO, aviso(clave));
        } catch (Exception ignorada) {
            // nada
        }
        if (clave == TERMINADA) {
            // La grabación ya está guardada en el archivo. El aviso se queda, ya sin ser fijo, para
            // que se pueda tocar y volver a la aplicación.
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
        }
    }

    private void crearCanal() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel canal = new NotificationChannel(CANAL, "Grabación de audio",
                NotificationManager.IMPORTANCE_LOW);
        canal.setDescription("Avisa que Estudio Café Tecnológico está grabando y permite pausar o "
                + "terminar la grabación.");
        canal.setSound(null, null);
        nm.createNotificationChannel(canal);
    }

    private PendingIntent accion(String nombre, int codigo) {
        Intent i = new Intent(this, ServicioGrabacion.class).setAction(nombre);
        return PendingIntent.getService(this, codigo, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification aviso(int clave) {
        Intent abrir = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contenido = PendingIntent.getActivity(this, 0, abrir,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CANAL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Estudio Café Tecnológico")
                .setContentIntent(contenido)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true);
        Grabadora g = grabadora;
        long seg = g == null ? 0 : g.segundos();
        if (clave == ESPERANDO) {
            b.setContentText("Preparándome para grabar. Toca para volver a la aplicación.")
                    .setOngoing(true);
        } else if (clave == GRABANDO) {
            b.setContentText("Grabando audio. Sigue grabando aunque estés en otra aplicación. "
                            + "Toca para volver a Estudio Café Tecnológico.")
                    .setOngoing(true)
                    .setUsesChronometer(true)
                    .setWhen(System.currentTimeMillis() - seg * 1000L)
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                            "Pausar la grabación", accion(ACCION_PAUSAR, 1)).build())
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(this, android.R.drawable.ic_menu_save),
                            "Terminar la grabación", accion(ACCION_TERMINAR, 2)).build());
        } else if (clave == EN_PAUSA) {
            b.setContentText("Grabación en pausa. Llevas " + textoTiempo(seg) + ". Toca para volver "
                            + "a Estudio Café Tecnológico.")
                    .setOngoing(true)
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(this, android.R.drawable.ic_media_play),
                            "Seguir grabando", accion(ACCION_PAUSAR, 1)).build())
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(this, android.R.drawable.ic_menu_save),
                            "Terminar la grabación", accion(ACCION_TERMINAR, 2)).build());
        } else {
            b.setContentText("La grabación terminó y está guardada. Toca para volver a la "
                            + "aplicación, revisarla y guardarla.")
                    .setOngoing(false)
                    .setAutoCancel(true);
        }
        return b.build();
    }

    private static String textoTiempo(long seg) {
        long m = seg / 60;
        long s = seg % 60;
        if (m == 0) return s + (s == 1 ? " segundo" : " segundos");
        return m + (m == 1 ? " minuto" : " minutos") + " y " + s + (s == 1 ? " segundo" : " segundos");
    }
}

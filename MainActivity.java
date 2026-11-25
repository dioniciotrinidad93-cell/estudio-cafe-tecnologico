package com.cafetecnologico.estudio;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;

/**
 * Estudio Café Tecnológico, versión 0.4.
 * Interfaz hecha por código, pensada para lectores de pantalla (TalkBack, Jieshuo, Prudence):
 * solo botones y textos claros, sin iconos ni gestos de arrastrar.
 *
 * Funciones: mezclar audio con música de fondo, crear un video con audio e imágenes (con zoom,
 * texto sobre las imágenes, descripción de las imágenes, sonido de transición y proyectos
 * guardados), agregar un audio a un video del teléfono, cambiar la voz de un audio con efectos y
 * transcribir audios a texto. Cada una puede usarse con el asistente (pasos guiados y voz del teléfono) o en modo manual.
 */
public class MainActivity extends Activity {

    // Enlaces de la comunidad.
    private static final String URL_YOUTUBE = "https://youtube.com/@cafetecnologico-m3h";
    private static final String URL_TELEGRAM = "https://t.me/tecnologia_accesible";
    private static final String URL_WHATSAPP = "https://chat.whatsapp.com/K7jF65B2XQjDAemtvxmaxk";

    private static final String URL_REPO =
            "https://github.com/dioniciotrinidad93-cell/estudio-cafe-tecnologico";
    private static final String URL_VERSION =
            URL_REPO + "/releases/latest/download/version.json";
    private static final String URL_APK =
            URL_REPO + "/releases/latest/download/Estudio_Cafe_Tecnologico.apk";

    private static final int PEDIDO_VOZ = 1;
    private static final int PEDIDO_MUSICA = 2;
    private static final int PEDIDO_AUDIO_VIDEO = 3;
    private static final int PEDIDO_IMAGENES = 4;
    private static final int PEDIDO_TRANSCRIPCION = 5;
    private static final int PEDIDO_VIDEO = 6;
    private static final int PEDIDO_AUDIO_NUEVO = 7;
    private static final int PEDIDO_VOZ_EFECTO = 8;

    private ScrollView scroll;
    private LinearLayout contenido;
    private SharedPreferences prefs;
    private Runnable volver = null;
    private int pantallaId = 0;

    // Voz del asistente
    private TextToSpeech tts;
    private boolean ttsListo = false;

    // Trabajos largos
    private volatile boolean cancelar = false;
    private TextView estadoTrabajo;
    private PowerManager.WakeLock bloqueo;

    // Vista previa
    private AudioTrack pista;

    // Selección de archivos
    private int pedidoActual = 0;
    private Consumer<List<Uri>> alSeleccionar;

    // Estado de la mezcla
    private String mzVozNombre;
    private File mzVozPcm;
    private long mzVozMarcos;
    private String mzMusNombre;
    private File mzMusPcm;
    private long mzMusMarcos;
    private int mzVolVoz = 100;
    private int mzVolMus = 20;
    private int mzIntro = 0;
    private int mzOutro = 0;
    private int mzFormato = 0;
    private boolean mzRepetir = true;
    private boolean mzDesvanecer = true;
    private String mzNombreSalida = "Mezcla de audio";

    // Estado del video
    private String vdAudioNombre;
    private File vdAudioPcm;
    private long vdAudioMarcos;
    private final ArrayList<Video.Imagen> vdImagenes = new ArrayList<>();
    private int vdFormato = 0;
    private int vdEncuadre = 1;
    private boolean vdPersonal = false;
    private String vdNombreSalida = "Video con audio";
    private Uri ultimaMezclaUri;
    private String ultimaMezclaNombre;

    // Estado del video, versión 0.3
    private Uri vdAudioUri;
    private boolean vdTrans = false;
    private int vdTransTipo = 0;
    private int vdTransVol = 40;
    private int vdZoomGlobal = 0;
    private String vdProyecto = "";

    // Estado de la transcripción
    private String trNombre;
    private File trPcm;
    private long trMarcos;
    private int trIdioma = 0;
    private boolean trHablantes = false;
    private String trNombreSalida = "Transcripción";
    private final StringBuilder trParcial = new StringBuilder();
    private String trFalla;
    private String trTexto;
    private Uri trArchivo;

    // Estado de agregar audio a un video, versión 0.4
    private Uri avVideoUri;
    private String avVideoNombre;
    private Video.Info avInfo;
    private String avNuevoNombre;
    private File avNuevoPcm;
    private long avNuevoMarcos;
    private File avOrigPcm;
    private int avModo = 0; // 0 reemplazar el audio del video, 1 mezclarlo con el nuevo
    private int avVolOrig = 100;
    private int avVolNuevo = 100;
    private int avInicio = 0;
    private boolean avRepetir = false;
    private boolean avDesvanecer = true;
    private String avNombreSalida = "Video con audio nuevo";

    // Estado del cambio de voz con efectos, versión 0.4
    private String vzNombre;
    private File vzPcm;
    private long vzMarcos;
    private int vzEfecto = 0;
    private int vzIntensidad = 5;
    private int vzFormato = 0;
    private String vzNombreSalida = "Audio con efecto de voz";

    // ---------- Ciclo de vida ----------

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        setTitle("Estudio Café Tecnológico");
        prefs = getSharedPreferences("estudio", MODE_PRIVATE);

        scroll = new ScrollView(this);
        contenido = new LinearLayout(this);
        contenido.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        contenido.setPadding(p, p, p, p);
        scroll.addView(contenido, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        limpiarCache();
        iniciarTts();
        mostrarMenu();
    }

    @Override
    protected void onPause() {
        detenerVista();
        if (tts != null) tts.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        detenerVista();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        soltarBloqueo();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (volver != null) {
            volver.run();
        } else {
            super.onBackPressed();
        }
    }

    // ---------- Ayudas de interfaz ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void nuevaPantalla(final String titulo, Runnable atras) {
        pantallaId++;
        volver = atras;
        detenerVista();
        if (tts != null) tts.stop();
        contenido.removeAllViews();
        TextView t = new TextView(this);
        t.setText(titulo);
        t.setTextSize(24);
        t.setAccessibilityHeading(true);
        t.setPadding(0, 0, 0, dp(12));
        contenido.addView(t);
        scroll.scrollTo(0, 0);
        contenido.post(() -> contenido.announceForAccessibility(titulo));
    }

    private TextView texto(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setPadding(0, 0, 0, dp(10));
        contenido.addView(t);
        return t;
    }

    private TextView encabezado(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(21);
        t.setAccessibilityHeading(true);
        t.setPadding(0, dp(18), 0, dp(6));
        contenido.addView(t);
        return t;
    }

    private Button boton(String s, final Runnable accion) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setOnClickListener(v -> accion.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        contenido.addView(b, lp);
        return b;
    }

    private CheckBox casilla(String s, boolean marcada, final Consumer<Boolean> alCambiar) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextSize(18);
        c.setChecked(marcada);
        c.setOnCheckedChangeListener((v, m) -> alCambiar.accept(m));
        contenido.addView(c);
        return c;
    }

    /** Un valor con dos botones, uno para bajarlo y otro para subirlo. El valor se lee solo al cambiar. */
    private void ajustador(final String nombre, final IntSupplier valor, final String unidad,
            String txtBajar, String txtSubir, final Runnable bajar, final Runnable subir,
            final Runnable alCambiar) {
        final TextView t = texto(nombre + ": " + valor.getAsInt() + unidad + ".");
        t.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        boton(txtBajar, () -> {
            bajar.run();
            t.setText(nombre + ": " + valor.getAsInt() + unidad + ".");
            if (alCambiar != null) alCambiar.run();
        });
        boton(txtSubir, () -> {
            subir.run();
            t.setText(nombre + ": " + valor.getAsInt() + unidad + ".");
            if (alCambiar != null) alCambiar.run();
        });
    }

    /** Un botón que pasa por varias opciones cada vez que se toca. */
    private void ciclo(final String titulo, final String[] opciones, final IntSupplier actual,
            final IntConsumer cambiar) {
        final Button[] b = new Button[1];
        b[0] = boton(titulo + ": " + opciones[actual.getAsInt()] + ". Toca para cambiar.", () -> {
            cambiar.accept((actual.getAsInt() + 1) % opciones.length);
            b[0].setText(titulo + ": " + opciones[actual.getAsInt()] + ". Toca para cambiar.");
            b[0].announceForAccessibility(opciones[actual.getAsInt()]);
        });
    }

    private EditText campoNombre(String etiqueta, String valor) {
        TextView t = texto(etiqueta);
        EditText e = new EditText(this);
        e.setId(View.generateViewId());
        e.setText(valor);
        e.setTextSize(18);
        e.setSingleLine(true);
        e.setHint(etiqueta);
        t.setLabelFor(e.getId());
        contenido.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    /** Un cuadro de texto de varias líneas. Cada cambio se avisa enseguida, sin botón de guardar. */
    private EditText campoMultilinea(String etiqueta, String valor, int maximo,
            final Consumer<String> alCambiar) {
        TextView t = texto(etiqueta);
        EditText e = new EditText(this);
        e.setId(View.generateViewId());
        e.setText(valor == null ? "" : valor);
        e.setTextSize(18);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(2);
        e.setMaxLines(6);
        e.setHint(etiqueta);
        e.setFilters(new InputFilter[] {new InputFilter.LengthFilter(maximo)});
        t.setLabelFor(e.getId());
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                // no hace falta
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                // no hace falta
            }

            @Override
            public void afterTextChanged(Editable s) {
                alCambiar.accept(s.toString());
            }
        });
        contenido.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private void aviso(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void abrir(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            aviso("No encontré una aplicación para abrir este enlace.");
        }
    }

    private File cache(String nombre) {
        return new File(getCacheDir(), nombre);
    }

    private void limpiarCache() {
        File[] l = getCacheDir().listFiles();
        if (l == null) return;
        for (File f : l) {
            String n = f.getName();
            if (n.endsWith(".pcm") || n.endsWith(".m4a") || n.endsWith(".wav") || n.endsWith(".mp4")
                    || n.endsWith(".aac")) {
                f.delete();
            }
        }
    }

    private static String textoDuracion(long s) {
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long r = s % 60;
        List<String> p = new ArrayList<>();
        if (h > 0) p.add(h + (h == 1 ? " hora" : " horas"));
        if (m > 0) p.add(m + (m == 1 ? " minuto" : " minutos"));
        if (r > 0 || p.isEmpty()) p.add(r + (r == 1 ? " segundo" : " segundos"));
        if (p.size() == 1) return p.get(0);
        if (p.size() == 2) return p.get(0) + " y " + p.get(1);
        return p.get(0) + ", " + p.get(1) + " y " + p.get(2);
    }

    private static String duracion(long marcos) {
        return textoDuracion(marcos / Audio.HZ);
    }

    private static String nombreArchivo(String s, String porDefecto) {
        String n = s == null ? "" : s.trim().replaceAll("[\\\\/:*?\"<>|]", "-");
        while (n.endsWith(".")) n = n.substring(0, n.length() - 1).trim();
        return n.isEmpty() ? porDefecto : n;
    }

    private String textoError(Throwable e) {
        String m = e.getMessage();
        if (e instanceof OutOfMemoryError) {
            return "Se acabó la memoria del teléfono. Cierra otras aplicaciones e inténtalo de nuevo.";
        }
        if (m != null && (m.contains("ENOSPC") || m.toLowerCase().contains("no space"))) {
            return "No hay espacio suficiente en el teléfono. Libera espacio e inténtalo de nuevo.";
        }
        return m == null ? e.toString() : m;
    }

    // ---------- Voz del asistente ----------

    private boolean asistente() {
        return prefs.getBoolean("asistente_activo", true);
    }

    private boolean vozAsistente() {
        return prefs.getBoolean("voz_asistente", true);
    }

    private void iniciarTts() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ttsListo = false;
        final String motor = prefs.getString("tts_motor", "");
        final TextToSpeech[] ref = new TextToSpeech[1];
        final TextToSpeech nuevo = new TextToSpeech(this, status -> {
            TextToSpeech t = ref[0];
            if (t == null || t != tts) return;
            if (status == TextToSpeech.SUCCESS) {
                Voces.aplicar(t, prefs);
                ttsListo = true;
            } else if (!motor.isEmpty()) {
                aviso("El motor de voz elegido no pudo iniciarse. Elige otro en Asistente y voz.");
            }
        }, motor.isEmpty() ? null : motor);
        ref[0] = nuevo;
        tts = nuevo;
    }

    /** Habla con la voz del asistente. Con forzar se oye aunque la voz esté desactivada. */
    private void habla(String frase, boolean forzar) {
        if (!forzar && !(asistente() && vozAsistente())) return;
        if (ttsListo && tts != null) {
            tts.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "asistente");
        } else if (forzar) {
            aviso("La voz del teléfono aún no está lista.");
        }
    }

    private void guia(String frase) {
        habla(frase, false);
    }

    // ---------- Selección de archivos ----------

    private void elegirArchivo(int pedido, String tipo, boolean varios, Consumer<List<Uri>> alElegir) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if ("audio/*".equals(tipo)) {
            // Algunos teléfonos marcan los M4A y otros audios como video: se muestran también, y de
            // un video se usa solo el sonido.
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"audio/*", "video/*", "application/ogg"});
        } else {
            i.setType(tipo);
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (varios) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pedidoActual = pedido;
        alSeleccionar = alElegir;
        try {
            startActivityForResult(i, pedido);
        } catch (ActivityNotFoundException e) {
            aviso("No encontré un selector de archivos en el teléfono.");
        }
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent datos) {
        super.onActivityResult(pedido, resultado, datos);
        if (pedido != pedidoActual || alSeleccionar == null) return;
        if (resultado != RESULT_OK || datos == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData cd = datos.getClipData();
        if (cd != null) {
            for (int i = 0; i < cd.getItemCount(); i++) {
                Uri u = cd.getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        } else if (datos.getData() != null) {
            uris.add(datos.getData());
        }
        if (uris.isEmpty()) return;
        for (Uri u : uris) {
            try {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignorada) {
                // no todos los proveedores lo permiten; basta con el permiso temporal
            }
        }
        Consumer<List<Uri>> a = alSeleccionar;
        alSeleccionar = null;
        a.accept(uris);
    }

    private String nombreDe(Uri u) {
        try (Cursor q = getContentResolver().query(u, new String[] {OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (q != null && q.moveToFirst()) {
                String n = q.getString(0);
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Exception ignorada) {
            // se usa un nombre genérico
        }
        String s = u.getLastPathSegment();
        return s == null ? "archivo" : s;
    }

    // ---------- Trabajos largos ----------

    private interface Trabajo {
        void hacer(Audio.Progreso p, Audio.Cancelado c) throws Exception;
    }

    private void soltarBloqueo() {
        try {
            if (bloqueo != null && bloqueo.isHeld()) bloqueo.release();
        } catch (Exception ignorada) {
            // nada
        }
        bloqueo = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private static Audio.Progreso escalar(final Audio.Progreso p, final int ini, final int fin) {
        return v -> p.porcentaje(ini + v * (fin - ini) / 100);
    }

    /** Muestra una pantalla de avance, hace el trabajo en segundo plano y sigue con ok, atras o un error. */
    private void trabajar(String titulo, String detalle, final Trabajo t, final Runnable ok,
            final Runnable atras) {
        nuevaPantalla(titulo, null);
        cancelar = false;
        texto(detalle);
        estadoTrabajo = texto("Avance: 0 por ciento.");
        estadoTrabajo.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        final TextView estado = estadoTrabajo;
        final Runnable cancelarYa = () -> {
            cancelar = true;
            estado.setText("Cancelando, un momento.");
        };
        volver = cancelarYa;
        boton("Cancelar", cancelarYa);
        guia(titulo + ". " + detalle);

        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            bloqueo = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "estudio:trabajo");
            bloqueo.acquire(4L * 60 * 60 * 1000);
        } catch (Exception ignorada) {
            // se sigue sin el bloqueo
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final int id = pantallaId;
        final int[] ultimo = {-100};
        new Thread(() -> {
            String error = null;
            boolean cancelado = false;
            try {
                t.hacer(pct -> {
                    if (pct / 10 != ultimo[0] / 10 || pct >= 100) {
                        ultimo[0] = pct;
                        runOnUiThread(() -> {
                            if (id == pantallaId && !cancelar) {
                                estado.setText("Avance: " + pct + " por ciento.");
                            }
                        });
                    }
                }, () -> cancelar);
            } catch (Audio.Cancelacion e) {
                cancelado = true;
            } catch (Throwable e) {
                error = textoError(e);
            }
            final boolean fueCancelado = cancelado;
            final String falla = error;
            runOnUiThread(() -> {
                soltarBloqueo();
                if (fueCancelado) {
                    atras.run();
                } else if (falla != null) {
                    mostrarError(falla, atras);
                } else {
                    ok.run();
                }
            });
        }).start();
    }

    private void mostrarError(String mensaje, Runnable atras) {
        nuevaPantalla("No se pudo completar", atras);
        texto(mensaje);
        boton("Volver", atras);
        guia("No se pudo completar. " + mensaje);
    }

    /** Lee un audio y lo deja listo para trabajar; al terminar avisa con el número de marcos. */
    private void prepararAudio(final Uri u, final String nombre, final File pcm,
            final LongConsumer alListo, final Runnable atras) {
        // Se lee a un archivo aparte: si falla o se cancela, el audio anterior se conserva.
        final File nuevo = new File(pcm.getParentFile(), pcm.getName().replace(".pcm", "_n.pcm"));
        trabajar("Preparando el audio",
                "Estoy leyendo " + nombre + ". Mantén la aplicación abierta hasta que termine.",
                (p, c) -> {
                    nuevo.delete();
                    try {
                        Audio.decodificar(this, u, nuevo, p, c);
                    } catch (Audio.Cancelacion e) {
                        nuevo.delete();
                        throw e;
                    } catch (Exception e) {
                        nuevo.delete();
                        throw new Exception("No pude leer el archivo " + nombre + ". " + textoError(e));
                    }
                },
                () -> {
                    pcm.delete();
                    if (!nuevo.renameTo(pcm)) {
                        mostrarError("No pude guardar el audio preparado. Revisa el espacio del "
                                + "teléfono.", atras);
                        return;
                    }
                    alListo.accept(pcm.length() / Audio.BYTES_MARCO);
                },
                atras);
    }

    // ---------- Vista previa ----------

    private void detenerVista() {
        if (pista != null) {
            try {
                pista.stop();
            } catch (Exception ignorada) {
                // nada
            }
            try {
                pista.release();
            } catch (Exception ignorada) {
                // nada
            }
            pista = null;
        }
    }

    private void reproducir(short[] datos, final Runnable alTerminar) {
        detenerVista();
        if (datos.length < 2) {
            alTerminar.run();
            return;
        }
        final int marcos = datos.length / 2;
        final AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Audio.HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(datos.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        t.write(datos, 0, datos.length);
        t.setNotificationMarkerPosition(Math.max(1, marcos - 1));
        t.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack a) {
                if (pista == t) alTerminar.run();
            }

            @Override
            public void onPeriodicNotification(AudioTrack a) {
                // no hace falta
            }
        });
        pista = t;
        t.play();
    }

    // ---------- Menú y pantallas generales ----------

    private void mostrarMenu() {
        nuevaPantalla("Estudio Café Tecnológico", null);
        texto("Edita audio y video con un asistente que te guía paso a paso, o por tu cuenta en "
                + "modo manual. Gratuito y de código abierto. Las funciones con Gemini usan tu propia "
                + "clave gratuita de Google.");
        texto("Modo actual: " + (asistente() ? "con asistente" : "manual, sin asistente") + ".");
        boton("Mezclar audio con música de fondo", () -> entrar(this::mzInicio, this::mzManual));
        boton("Crear video con audio e imágenes", () -> entrar(this::vdInicio, this::vdManual));
        boton("Agregar un audio a un video del teléfono", () -> entrar(this::avInicio, this::avManual));
        boton("Cambiar la voz de un audio con efectos", () -> entrar(this::vzInicio, this::vzManual));
        boton("Transcribir un audio a texto", () -> entrar(this::trInicio, this::trManual));
        boton("Proyectos de video guardados", this::mostrarProyectos);
        boton("Clave de Gemini", () -> mostrarClave(this::mostrarMenu, this::mostrarMenu));
        boton("Asistente y voz", this::mostrarAsistente);
        boton("Nuestros canales", this::mostrarCanales);
        boton("Buscar actualizaciones", this::buscarActualizaciones);
        boton("Acerca de", this::mostrarAcerca);
    }

    /** Abre una función: con advertencia y pasos guiados, o directo en modo manual. */
    private void entrar(Runnable guiado, Runnable manual) {
        if (!asistente()) {
            manual.run();
        } else if (prefs.getBoolean("sin_advertencia", false)) {
            guiado.run();
        } else {
            mostrarAdvertencia(guiado, manual);
        }
    }

    private void empezar(Runnable guiado, Runnable manual) {
        if (asistente()) guiado.run();
        else manual.run();
    }

    private void mostrarAdvertencia(final Runnable guiado, final Runnable manual) {
        nuevaPantalla("Antes de usar el asistente", this::mostrarMenu);
        String t = "El asistente habla con la voz del teléfono. Si usas un lector de pantalla, las "
                + "dos voces pueden hablar a la vez y no entenderse. Para evitarlo, puedes elegir "
                + "una voz distinta a la de tu lector en Asistente y voz, bajar la velocidad, o "
                + "continuar sin que el asistente hable: los pasos se seguirán mostrando en "
                + "pantalla para tu lector. También puedes trabajar en modo manual, con todas las "
                + "opciones en una sola pantalla.";
        texto(t);
        guia(t);
        final boolean[] noMostrar = {false};
        casilla("No volver a mostrar esta advertencia", false, m -> noMostrar[0] = m);
        final Runnable guardar = () -> {
            if (noMostrar[0]) prefs.edit().putBoolean("sin_advertencia", true).apply();
        };
        boton("Entendido, continuar con el asistente", () -> {
            guardar.run();
            guiado.run();
        });
        boton("Continuar con el asistente, pero sin que hable", () -> {
            guardar.run();
            prefs.edit().putBoolean("voz_asistente", false).apply();
            guiado.run();
        });
        boton("Usar el modo manual", () -> {
            guardar.run();
            prefs.edit().putBoolean("asistente_activo", false).apply();
            aviso("Modo manual activado. Puedes volver a activar el asistente en Asistente y voz.");
            manual.run();
        });
        boton("Volver al menú", this::mostrarMenu);
    }

    private void mostrarCanales() {
        nuevaPantalla("Nuestros canales", this::mostrarMenu);
        texto("Únete a la comunidad de Café Tecnológico.");
        boton("Canal de YouTube", () -> abrir(URL_YOUTUBE));
        boton("Canal de Telegram", () -> abrir(URL_TELEGRAM));
        boton("Grupo de WhatsApp", () -> abrir(URL_WHATSAPP));
        boton("Volver al menú", this::mostrarMenu);
    }

    private void mostrarAcerca() {
        nuevaPantalla("Acerca de", this::mostrarMenu);
        texto("Estudio Café Tecnológico, versión " + versionName() + ".");
        texto("Aplicación gratuita y de código abierto, con licencia MIT, creada por "
                + "Café Tecnológico para que editar audio y video sea accesible con lector de pantalla.");
        boton("Ver el código en GitHub", () -> abrir(URL_REPO));
        boton("Volver al menú", this::mostrarMenu);
    }

    // ---------- Asistente y voz ----------

    private void mostrarAsistente() {
        nuevaPantalla("Asistente y voz", this::mostrarMenu);
        texto("El asistente te guía paso a paso en cada función y habla con la voz del teléfono. "
                + "Aquí eliges si lo usas, si habla, y con qué voz y velocidad.");
        casilla("Usar el asistente con pasos guiados. Si lo desactivas, trabajas en modo manual.",
                asistente(), m -> prefs.edit().putBoolean("asistente_activo", m).apply());
        casilla("Que el asistente hable con la voz elegida", vozAsistente(),
                m -> prefs.edit().putBoolean("voz_asistente", m).apply());
        casilla("Mostrar una advertencia antes de usar el asistente",
                !prefs.getBoolean("sin_advertencia", false),
                m -> prefs.edit().putBoolean("sin_advertencia", !m).apply());

        encabezado("Voz elegida");
        texto("Motor de voz: " + Voces.nombreMotor(this, prefs.getString("tts_motor", "")) + ".");
        String idioma = prefs.getString("tts_idioma_nombre", "");
        texto("Idioma: " + (idioma.isEmpty() ? "español, automático" : idioma) + ".");
        String voz = prefs.getString("tts_voz_nombre", "");
        texto("Voz: " + (voz.isEmpty() ? "la predeterminada del idioma" : voz) + ".");
        boton("Elegir el motor de voz", this::mostrarMotores);
        boton("Elegir el idioma y la voz", this::mostrarIdiomas);

        encabezado("Velocidad");
        ajustador("Velocidad de la voz", () -> prefs.getInt("tts_vel", 100), " por ciento",
                "Hablar más lento", "Hablar más rápido",
                () -> cambiarVelocidad(-Voces.VEL_PASO), () -> cambiarVelocidad(Voces.VEL_PASO),
                () -> habla("Así suena esta velocidad.", true));
        boton("Probar la voz del asistente", () -> habla(
                "Hola, soy el asistente de Estudio Café Tecnológico. Te iré guiando paso a paso.",
                true));
        boton("Volver al menú", this::mostrarMenu);
    }

    private void cambiarVelocidad(int delta) {
        int v = prefs.getInt("tts_vel", 100) + delta;
        v = Math.max(Voces.VEL_MIN, Math.min(Voces.VEL_MAX, v));
        prefs.edit().putInt("tts_vel", v).apply();
        if (tts != null && ttsListo) tts.setSpeechRate(v / 100f);
    }

    private void mostrarMotores() {
        nuevaPantalla("Motor de voz", this::mostrarAsistente);
        texto("Elige qué motor de texto a voz usará el asistente. Cada motor trae sus propias voces.");
        boton("El predeterminado del teléfono", () -> elegirMotor("", ""));
        for (final Voces.Motor m : Voces.motores(this)) {
            boton(m.etiqueta, () -> elegirMotor(m.paquete, m.etiqueta));
        }
        boton("Volver", this::mostrarAsistente);
    }

    private void elegirMotor(String paquete, String etiqueta) {
        prefs.edit().putString("tts_motor", paquete)
                .remove("tts_idioma").remove("tts_voz")
                .remove("tts_idioma_nombre").remove("tts_voz_nombre").apply();
        iniciarTts();
        mostrarAsistente();
    }

    private void mostrarIdiomas() {
        nuevaPantalla("Idioma y voz", this::mostrarAsistente);
        texto("Consultando las voces del motor, un momento.");
        final int id = pantallaId;
        Voces.cargar(this, prefs.getString("tts_motor", ""), (idiomas, error) -> {
            if (id != pantallaId) return;
            if (error != null || idiomas == null) {
                mostrarError(error == null ? "No pude consultar las voces." : error,
                        this::mostrarAsistente);
                return;
            }
            mostrarListaIdiomas(idiomas);
        });
    }

    private void mostrarListaIdiomas(final List<Voces.Idioma> idiomas) {
        nuevaPantalla("Elige el idioma", this::mostrarAsistente);
        texto("Los idiomas en español aparecen primero.");
        for (final Voces.Idioma i : idiomas) {
            boton(i.etiqueta, () -> mostrarVocesDe(i, idiomas));
        }
        boton("Volver", this::mostrarAsistente);
    }

    private void mostrarVocesDe(final Voces.Idioma idioma, final List<Voces.Idioma> idiomas) {
        nuevaPantalla("Voces de " + idioma.etiqueta, () -> mostrarListaIdiomas(idiomas));
        if (idioma.voces.isEmpty()) {
            texto("El motor no listó voces para este idioma. Se usará su voz predeterminada.");
        }
        boton("Voz predeterminada de este idioma", () -> elegirVoz(idioma, null));
        for (final Voces.Voz v : idioma.voces) {
            boton(v.etiqueta, () -> elegirVoz(idioma, v));
        }
        boton("Volver a los idiomas", () -> mostrarListaIdiomas(idiomas));
    }

    private void elegirVoz(Voces.Idioma idioma, Voces.Voz voz) {
        prefs.edit().putString("tts_idioma", idioma.tag)
                .putString("tts_idioma_nombre", idioma.etiqueta)
                .putString("tts_voz", voz == null ? "" : voz.nombre)
                .putString("tts_voz_nombre", voz == null ? "" : voz.etiqueta).apply();
        if (tts != null && ttsListo) Voces.aplicar(tts, prefs);
        mostrarAsistente();
        habla("Así suena la voz elegida.", true);
    }

    // ---------- Compartir y abrir lo exportado ----------

    private void abrirArchivo(Uri u, String mime) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            aviso("No encontré una aplicación para reproducirlo. Puedes abrirlo desde tu reproductor.");
        }
    }

    private void compartir(Uri u, String mime) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, u);
        i.setClipData(ClipData.newRawUri("", u));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, "Compartir"));
        } catch (ActivityNotFoundException e) {
            aviso("No encontré una aplicación para compartirlo.");
        }
    }

    // =====================================================================
    // MEZCLAR AUDIO CON MÚSICA DE FONDO
    // =====================================================================

    private Audio.Ajustes ajustesMezcla() {
        Audio.Ajustes a = new Audio.Ajustes();
        a.volVoz = mzVolVoz;
        a.volMus = mzVolMus;
        a.repetir = mzRepetir;
        a.desvanecer = mzDesvanecer;
        a.introSeg = mzIntro;
        a.outroSeg = mzOutro;
        return a;
    }

    private boolean mzListo() {
        return mzVozPcm != null && mzMusPcm != null;
    }

    private void reiniciarMezcla() {
        mzVozPcm = null;
        mzMusPcm = null;
        mzVozNombre = null;
        mzMusNombre = null;
        mzVolVoz = 100;
        mzVolMus = 20;
        mzIntro = 0;
        mzOutro = 0;
        mzRepetir = true;
        mzDesvanecer = true;
        limpiarCache();
    }

    private String estadoVolumenes() {
        return "Voz al " + mzVolVoz + " por ciento. Música al " + mzVolMus + " por ciento.";
    }

    // ----- Bloques que usan los pasos guiados y el modo manual -----

    private void mzBloqueAudio(final boolean esVoz, final Runnable refrescar) {
        boolean hay = esVoz ? mzVozPcm != null : mzMusPcm != null;
        if (hay) {
            texto((esVoz ? "Audio principal: " : "Música: ")
                    + (esVoz ? mzVozNombre : mzMusNombre) + ". Dura "
                    + duracion(esVoz ? mzVozMarcos : mzMusMarcos) + ".");
        } else {
            texto(esVoz ? "Todavía no elegiste el audio principal." : "Todavía no elegiste la música.");
        }
        String etiqueta = esVoz
                ? (hay ? "Cambiar el audio principal" : "Elegir el audio principal")
                : (hay ? "Cambiar la música" : "Elegir la música");
        boton(etiqueta, () -> mzElegir(esVoz, refrescar));
    }

    private void mzElegir(final boolean esVoz, final Runnable refrescar) {
        elegirArchivo(esVoz ? PEDIDO_VOZ : PEDIDO_MUSICA, "audio/*", false, uris -> {
            final Uri u = uris.get(0);
            final String nombre = nombreDe(u);
            final File pcm = cache(esVoz ? "mezcla_voz.pcm" : "mezcla_musica.pcm");
            prepararAudio(u, nombre, pcm, marcos -> {
                if (esVoz) {
                    mzVozNombre = nombre;
                    mzVozPcm = pcm;
                    mzVozMarcos = marcos;
                } else {
                    mzMusNombre = nombre;
                    mzMusPcm = pcm;
                    mzMusMarcos = marcos;
                }
                refrescar.run();
            }, refrescar);
        });
    }

    private void mzBloqueVolumenes(Runnable alCambiar) {
        ajustador("Volumen de la voz", () -> mzVolVoz, " por ciento",
                "Bajar el volumen de la voz", "Subir el volumen de la voz",
                () -> mzVolVoz = Math.max(10, mzVolVoz - 10),
                () -> mzVolVoz = Math.min(150, mzVolVoz + 10), alCambiar);
        ajustador("Volumen de la música", () -> mzVolMus, " por ciento",
                "Bajar el volumen de la música", "Subir el volumen de la música",
                () -> mzVolMus = Math.max(0, mzVolMus - 5),
                () -> mzVolMus = Math.min(100, mzVolMus + 5), alCambiar);
    }

    private void mzBloqueDuracion() {
        String d = "Tu audio dura " + duracion(mzVozMarcos) + ". La música dura "
                + duracion(mzMusMarcos) + ".";
        if (mzMusMarcos < mzVozMarcos) {
            d += " La música es más corta que tu audio.";
        } else if (mzMusMarcos > mzVozMarcos) {
            d += " La música es más larga y se cortará donde termine tu audio.";
        }
        texto(d);
        casilla("Repetir la música si es más corta que el audio", mzRepetir,
                m -> mzRepetir = m);
        casilla("Desvanecer la música al empezar y al terminar", mzDesvanecer,
                m -> mzDesvanecer = m);
        ajustador("Música sola al inicio", () -> mzIntro, " segundos",
                "Menos segundos de música al inicio", "Más segundos de música al inicio",
                () -> mzIntro = Math.max(0, mzIntro - 1),
                () -> mzIntro = Math.min(30, mzIntro + 1), null);
        ajustador("Música sola al final", () -> mzOutro, " segundos",
                "Menos segundos de música al final", "Más segundos de música al final",
                () -> mzOutro = Math.max(0, mzOutro - 1),
                () -> mzOutro = Math.min(30, mzOutro + 1), null);
    }

    /** Botón de vista previa. Con preguntas, el asistente pregunta cómo se oyó y ajusta. */
    private void mzBloqueVista(boolean conPreguntas) {
        final TextView estado = texto(estadoVolumenes());
        estado.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        boton("Escuchar la vista previa", () -> mzReproducir(estado, ""));
        if (conPreguntas) {
            encabezado("Según lo que escuchas");
            boton("La música se oye muy fuerte", () -> {
                mzVolMus = Math.max(0, mzVolMus - 5);
                mzReproducir(estado, "Bajé la música.");
            });
            boton("La música casi no se oye", () -> {
                mzVolMus = Math.min(100, mzVolMus + 5);
                mzReproducir(estado, "Subí la música.");
            });
            boton("No entiendo bien la voz", () -> {
                mzVolMus = Math.max(0, mzVolMus - 10);
                mzVolVoz = Math.min(150, mzVolVoz + 10);
                mzReproducir(estado, "Bajé la música y subí la voz.");
            });
        }
    }

    private void mzReproducir(final TextView estado, final String antes) {
        detenerVista();
        if (tts != null) tts.stop();
        final int id = pantallaId;
        final Audio.Ajustes a = ajustesMezcla();
        final File voz = mzVozPcm;
        final File mus = mzMusPcm;
        final String prefijo = antes.isEmpty() ? "" : antes + " ";
        estado.setText(prefijo + estadoVolumenes() + " Preparando la vista previa.");
        new Thread(() -> {
            short[] datos = null;
            String error = null;
            try {
                datos = Audio.vistaPrevia(voz, mus, a);
            } catch (Throwable e) {
                error = textoError(e);
            }
            final short[] d = datos;
            final String falla = error;
            runOnUiThread(() -> {
                if (id != pantallaId) return;
                if (falla != null) {
                    estado.setText(falla);
                    return;
                }
                estado.setText(prefijo + estadoVolumenes() + " Escuchando la vista previa.");
                reproducir(d, () -> {
                    if (id != pantallaId) return;
                    estado.setText(estadoVolumenes() + " Terminó la vista previa.");
                    guia("Terminó la vista previa. Dime cómo se oyó.");
                });
            });
        }).start();
    }

    private void mzBloqueExportar(final Runnable volverAqui) {
        texto("La mezcla final durará " + textoDuracion(Audio.totalMezcla(mzVozPcm, ajustesMezcla())
                / Audio.HZ) + ".");
        final EditText nombre = campoNombre("Nombre del archivo", mzNombreSalida);
        ciclo("Formato", new String[] {"M4A, recomendado: pesa poco y suena bien",
                "WAV, sin compresión: pesa mucho"}, () -> mzFormato, v -> mzFormato = v);
        boton("Exportar la mezcla", () -> {
            mzNombreSalida = nombre.getText().toString();
            mzExportar(volverAqui);
        });
    }

    private void mzExportar(final Runnable volverAqui) {
        final Audio.Ajustes a = ajustesMezcla();
        final boolean wav = mzFormato == 1;
        final String nombre = nombreArchivo(mzNombreSalida, "Mezcla de audio") + (wav ? ".wav" : ".m4a");
        final String mime = wav ? "audio/x-wav" : "audio/mp4";
        final File tmp = cache(wav ? "mezcla_salida.wav" : "mezcla_salida.m4a");
        final File voz = mzVozPcm;
        final File mus = mzMusPcm;
        final Uri[] resultado = new Uri[1];
        trabajar("Exportando la mezcla",
                "Estoy mezclando y guardando tu audio. Puede tardar un poco si es largo. "
                        + "Mantén la aplicación abierta.",
                (p, c) -> {
                    try {
                        tmp.delete();
                        Audio.exportarMezcla(voz, mus, a, wav, tmp, p, c);
                        resultado[0] = Audio.guardar(this, tmp, nombre, mime, false);
                    } finally {
                        tmp.delete();
                    }
                },
                () -> mzResultado(resultado[0], nombre, mime), volverAqui);
    }

    private void mzResultado(final Uri uri, String nombre, final String mime) {
        final String guardado = Audio.nombreGuardado(this, uri, nombre);
        ultimaMezclaUri = uri;
        ultimaMezclaNombre = guardado;
        nuevaPantalla("Mezcla guardada", this::mostrarMenu);
        String t = "Tu mezcla se guardó en la carpeta Música, dentro de la carpeta "
                + Audio.CARPETA + ", con el nombre " + guardado + ".";
        texto(t);
        guia("Listo. " + t);
        boton("Reproducir la mezcla", () -> abrirArchivo(uri, mime));
        boton("Compartir la mezcla", () -> compartir(uri, mime));
        boton("Crear un video con esta mezcla", () -> vdUsarAudioExistente(uri, guardado));
        boton("Hacer otra mezcla", () -> {
            reiniciarMezcla();
            empezar(this::mzInicio, this::mzManual);
        });
        boton("Volver al menú", this::mostrarMenu);
    }

    // ----- Pasos guiados -----

    private void mzInicio() {
        nuevaPantalla("Mezclar audio con música de fondo", this::mostrarMenu);
        String t = "Vamos a mezclar tu audio con una música de fondo. Te guiaré paso a paso: primero "
                + "tu audio, luego la música, después los volúmenes, una vista previa para que "
                + "escuches cómo queda, qué hacer si duran distinto, y por último exportar.";
        texto(t);
        guia(t);
        boton("Comenzar", this::mzPasoVoz);
        boton("Volver al menú", this::mostrarMenu);
    }

    private void mzPasoVoz() {
        nuevaPantalla("Paso 1: tu audio principal", this::mzInicio);
        String t = "Elige el archivo de audio con tu voz o tu contenido. Puede ser MP3, M4A, WAV u "
                + "otros formatos.";
        texto(t);
        guia(t);
        mzBloqueAudio(true, this::mzPasoVoz);
        if (mzVozPcm != null) boton("Siguiente: elegir la música", this::mzPasoMusica);
        boton("Atrás", this::mzInicio);
    }

    private void mzPasoMusica() {
        nuevaPantalla("Paso 2: la música de fondo", this::mzPasoVoz);
        String t = "Ahora elige la música de fondo. Funciona mejor una música instrumental, sin voz, "
                + "para que no compita con la tuya.";
        texto(t);
        guia(t);
        mzBloqueAudio(false, this::mzPasoMusica);
        if (mzMusPcm != null) boton("Siguiente: ajustar los volúmenes", this::mzPasoVolumenes);
        boton("Atrás", this::mzPasoVoz);
    }

    private void mzPasoVolumenes() {
        nuevaPantalla("Paso 3: los volúmenes", this::mzPasoMusica);
        String t = "Ajusta cuánto suena cada audio. Lo normal es la voz al 100 por ciento y la música "
                + "entre 10 y 25 por ciento, para que no tape tu voz. Lo podrás comprobar en la "
                + "vista previa.";
        texto(t);
        guia(t);
        mzBloqueVolumenes(null);
        boton("Siguiente: escuchar la vista previa", this::mzPasoVista);
        boton("Atrás", this::mzPasoMusica);
    }

    private void mzPasoVista() {
        nuevaPantalla("Paso 4: vista previa", this::mzPasoVolumenes);
        String t = "Escucharás un fragmento de unos 20 segundos de la mezcla. Después dime cómo se "
                + "oyó y lo ajusto. Puedes repetirlo las veces que quieras.";
        texto(t);
        guia(t);
        mzBloqueVista(true);
        boton("Se oye bien, continuar", this::mzPasoDuracion);
        boton("Atrás", this::mzPasoVolumenes);
    }

    private void mzPasoDuracion() {
        nuevaPantalla("Paso 5: si duran distinto", this::mzPasoVista);
        String t = "Aquí decides qué pasa cuando el audio y la música no duran lo mismo.";
        texto(t);
        guia(t);
        mzBloqueDuracion();
        boton("Siguiente: exportar", this::mzPasoExportar);
        boton("Atrás", this::mzPasoVista);
    }

    private void mzPasoExportar() {
        nuevaPantalla("Paso 6: exportar", this::mzPasoDuracion);
        String t = "Último paso. Elige el nombre y el formato, y exporta tu mezcla.";
        texto(t);
        guia(t);
        mzBloqueExportar(this::mzPasoExportar);
        boton("Atrás", this::mzPasoDuracion);
    }

    // ----- Modo manual -----

    private void mzManual() {
        nuevaPantalla("Mezclar audio, modo manual", this::mostrarMenu);
        texto("En modo manual tienes todas las opciones en esta pantalla, sin pasos guiados.");
        encabezado("Audio principal");
        mzBloqueAudio(true, this::mzManual);
        encabezado("Música de fondo");
        mzBloqueAudio(false, this::mzManual);
        if (!mzListo()) {
            texto("Cuando elijas el audio y la música, aparecerán los demás ajustes.");
            boton("Volver al menú", this::mostrarMenu);
            return;
        }
        encabezado("Volúmenes");
        mzBloqueVolumenes(null);
        encabezado("Duración");
        mzBloqueDuracion();
        encabezado("Vista previa");
        mzBloqueVista(false);
        encabezado("Exportar");
        mzBloqueExportar(this::mzManual);
        boton("Volver al menú", this::mostrarMenu);
    }

    // =====================================================================
    // CREAR VIDEO CON AUDIO E IMÁGENES
    // =====================================================================

    private double vdTotalSeg() {
        return vdAudioMarcos / (double) Audio.HZ;
    }

    /** Segundos de la imagen i: los que puso la persona, o su parte igual del audio. */
    private int vdSegDe(int i) {
        Video.Imagen im = vdImagenes.get(i);
        if (im.seg > 0) return im.seg;
        return (int) Math.max(1, Math.round(vdTotalSeg() / Math.max(1, vdImagenes.size())));
    }

    private double[] vdDuraciones() {
        int n = vdImagenes.size();
        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            d[i] = vdPersonal ? vdSegDe(i) : vdTotalSeg() / n;
        }
        return d;
    }

    private String vdResumenDuracion() {
        int n = vdImagenes.size();
        if (n == 0) return "Aún no hay imágenes.";
        double total = vdTotalSeg();
        if (!vdPersonal) {
            double c = total / n;
            String c1 = c >= 10 ? String.valueOf(Math.round(c))
                    : String.format(java.util.Locale.forLanguageTag("es"), "%.1f", c);
            return "Las " + n + " imágenes se reparten en partes iguales: cada una dura unos " + c1
                    + " segundos.";
        }
        long suma = 0;
        for (int i = 0; i < n; i++) suma += vdSegDe(i);
        String s = "Las duraciones suman " + textoDuracion(suma) + " y el audio dura "
                + textoDuracion(Math.round(total)) + ".";
        if (suma < total) s += " La última imagen se alargará hasta el final del audio.";
        else if (suma > total) s += " El video se cortará cuando termine el audio.";
        return s;
    }

    private void vdUsarAudioExistente(final Uri u, final String nombre) {
        vdAudioUri = u;
        vdAudioPcm = null;
        final File pcm = cache("video_audio.pcm");
        prepararAudio(u, nombre, pcm, marcos -> {
            vdAudioNombre = nombre;
            vdAudioPcm = pcm;
            vdAudioMarcos = marcos;
            empezar(this::vdInicio, this::vdManual);
        }, this::mostrarMenu);
    }

    // ----- Bloques -----

    private void vdBloqueAudio(final Runnable refrescar) {
        boolean hay = vdAudioPcm != null;
        if (hay) {
            texto("Audio: " + vdAudioNombre + ". Dura " + duracion(vdAudioMarcos) + ".");
        } else {
            texto("Todavía no elegiste el audio.");
        }
        boton(hay ? "Cambiar el audio" : "Elegir el audio", () ->
                elegirArchivo(PEDIDO_AUDIO_VIDEO, "audio/*", false, uris -> {
                    final Uri u = uris.get(0);
                    final String nombre = nombreDe(u);
                    final File pcm = cache("video_audio.pcm");
                    vdAudioUri = u;
                    vdAudioPcm = null;
                    prepararAudio(u, nombre, pcm, marcos -> {
                        vdAudioNombre = nombre;
                        vdAudioPcm = pcm;
                        vdAudioMarcos = marcos;
                        refrescar.run();
                    }, refrescar);
                }));
        if (ultimaMezclaUri != null) {
            final Uri u = ultimaMezclaUri;
            final String nombre = ultimaMezclaNombre;
            boton("Usar la última mezcla: " + nombre, () -> {
                vdAudioUri = u;
                vdAudioPcm = null;
                final File pcm = cache("video_audio.pcm");
                prepararAudio(u, nombre, pcm, marcos -> {
                    vdAudioNombre = nombre;
                    vdAudioPcm = pcm;
                    vdAudioMarcos = marcos;
                    refrescar.run();
                }, refrescar);
            });
        }
    }

    private void vdBloqueImagenes(final Runnable refrescar) {
        int n = vdImagenes.size();
        texto(n == 0 ? "Todavía no elegiste imágenes."
                : "Tienes " + n + (n == 1 ? " imagen." : " imágenes.")
                + " Toca una imagen para moverla, quitarla, describirla, ponerle texto o cambiar su duración y su zoom.");
        boton(n == 0 ? "Elegir imágenes" : "Agregar más imágenes", () ->
                elegirArchivo(PEDIDO_IMAGENES, "image/*", true, uris -> vdAgregar(uris, refrescar)));
        vdListaImagenes(refrescar);
        if (n > 0) {
            encabezado("Descripción de las imágenes");
            final List<Integer> pendientes = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String d = vdImagenes.get(i).descripcion;
                if (d == null || d.isEmpty()) pendientes.add(i);
            }
            int sin = pendientes.size();
            texto("Gemini puede describirte cada imagen para que sepas qué fotos agregaste. "
                    + (sin == 0 ? "Todas tienen descripción."
                    : sin + (sin == 1 ? " imagen todavía no tiene descripción."
                    : " imágenes todavía no tienen descripción.")));
            ciclo("Nivel de detalle", new String[] {"Breve, una o dos frases", "Detallada"},
                    () -> prefs.getInt("desc_detalle", 0),
                    v -> prefs.edit().putInt("desc_detalle", v).apply());
            ciclo("Idioma de la descripción", Gemini.NOMBRES_IDIOMA_DESCRIPCION,
                    () -> prefs.getInt("desc_idioma", 0),
                    v -> prefs.edit().putInt("desc_idioma", v).apply());
            if (sin > 0) {
                boton(sin == 1 ? "Describir la imagen sin descripción"
                        : "Describir las " + sin + " imágenes sin descripción",
                        () -> vdDescribir(pendientes, refrescar, refrescar));
            }
        }
    }

    private void vdListaImagenes(final Runnable refrescar) {
        int n = vdImagenes.size();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Video.Imagen im = vdImagenes.get(i);
            String t = "Imagen " + (i + 1) + " de " + n + ": " + im.nombre + ".";
            if (vdPersonal) {
                int s = vdSegDe(i);
                t += " Dura " + s + (s == 1 ? " segundo." : " segundos.");
            }
            if (im.zoom == 1) t += " Zoom: acercar.";
            else if (im.zoom == 2) t += " Zoom: alejar.";
            if (im.descripcion != null && !im.descripcion.isEmpty()) t += " Tiene descripción.";
            if (im.rotulo != null && !im.rotulo.trim().isEmpty()) t += " Tiene texto.";
            String av = im.aviso();
            if (!av.isEmpty()) t += " Aviso: " + av;
            boton(t, () -> vdOpcionesImagen(idx, refrescar));
        }
    }

    // ----- Descripción de imágenes con Gemini -----

    private void vdDescribir(final List<Integer> indices, final Runnable alTerminar,
            final Runnable atras) {
        final List<Video.Imagen> lista = new ArrayList<>();
        for (int i : indices) lista.add(vdImagenes.get(i));
        final Runnable seguir = () -> conClave(
                () -> vdDescribirYa(lista, alTerminar, atras), atras);
        if (prefs.getBoolean("aviso_img_ok", false)) {
            seguir.run();
            return;
        }
        nuevaPantalla("Antes de describir las imágenes", atras);
        String t = "Para describirlas, la aplicación envía cada imagen a Gemini, de Google, con tu "
                + "clave. Cada imagen usa una solicitud del cupo de tu clave. Con el nivel gratuito, "
                + "Google puede usar lo que envías para mejorar sus productos, así que no envíes "
                + "imágenes privadas o confidenciales. La descripción puede tener errores: "
                + "revísala antes de confiar en ella.";
        texto(t);
        guia(t);
        final boolean[] no = {false};
        casilla("No volver a mostrar este aviso", false, m -> no[0] = m);
        boton("Entendido, describir", () -> {
            if (no[0]) prefs.edit().putBoolean("aviso_img_ok", true).apply();
            seguir.run();
        });
        boton("Cancelar", atras);
    }

    private void vdDescribirYa(final List<Video.Imagen> lista, final Runnable alTerminar,
            final Runnable atras) {
        final String clave = claveGemini();
        final int detalle = prefs.getInt("desc_detalle", 0);
        final int idioma = prefs.getInt("desc_idioma", 0);
        final int total = lista.size();
        trabajar("Describiendo las imágenes",
                "Estoy enviando " + (total == 1 ? "la imagen" : "las " + total + " imágenes")
                        + " a Gemini, una por una. Mantén la aplicación abierta.",
                (p, c) -> {
                    String[] modelos = Gemini.orden(clave, true);
                    int k = 0;
                    for (Video.Imagen im : lista) {
                        if (c.si()) throw new Audio.Cancelacion();
                        im.descripcion = Gemini.describirImagen(this, im.uri, clave, modelos,
                                detalle, idioma, c);
                        k++;
                        p.porcentaje(k * 100 / total);
                    }
                },
                () -> {
                    alTerminar.run();
                    guia(total == 1 ? "Listo, la imagen ya tiene descripción."
                            : "Listo, las " + total + " imágenes ya tienen descripción.");
                },
                atras);
    }

    private void vdAgregar(List<Uri> uris, final Runnable refrescar) {
        final List<Video.Imagen> nuevas = new ArrayList<>();
        for (Uri u : uris) {
            Video.Imagen im = new Video.Imagen();
            im.uri = u;
            im.nombre = nombreDe(u);
            nuevas.add(im);
        }
        trabajar("Revisando las imágenes",
                "Estoy comprobando el tamaño y el enfoque de cada imagen. Mantén la aplicación "
                        + "abierta.",
                (p, c) -> {
                    int k = 0;
                    for (Video.Imagen im : nuevas) {
                        if (c.si()) throw new Audio.Cancelacion();
                        Video.analizar(this, im);
                        k++;
                        p.porcentaje(k * 100 / nuevas.size());
                    }
                },
                () -> {
                    vdImagenes.addAll(nuevas);
                    refrescar.run();
                    int conAviso = 0;
                    for (Video.Imagen im : nuevas) {
                        if (!im.aviso().isEmpty()) conAviso++;
                    }
                    String m = "Agregué " + nuevas.size() + (nuevas.size() == 1 ? " imagen." : " imágenes.");
                    if (conAviso > 0) {
                        m += " " + conAviso + (conAviso == 1 ? " tiene un aviso" : " tienen avisos")
                                + " de calidad, revísalos en la lista.";
                    }
                    guia(m);
                },
                refrescar);
    }

    private void vdOpcionesImagen(final int idx, final Runnable regreso) {
        final Video.Imagen im = vdImagenes.get(idx);
        nuevaPantalla("Imagen " + (idx + 1) + " de " + vdImagenes.size(), regreso);
        texto("Archivo: " + im.nombre + ".");
        if (im.ancho > 0) texto("Tamaño: " + im.ancho + " por " + im.alto + " píxeles.");
        String av = im.aviso();
        if (!av.isEmpty()) {
            texto("Aviso: " + av + " Para que el video se vea nítido y las plataformas lo "
                    + "recomienden, usa imágenes enfocadas y grandes.");
        }
        encabezado("Descripción");
        final List<Integer> esta = new ArrayList<>();
        esta.add(idx);
        final Runnable aqui = () -> vdOpcionesImagen(idx, regreso);
        if (im.descripcion != null && !im.descripcion.isEmpty()) {
            texto(im.descripcion);
            boton("Describir otra vez", () -> vdDescribir(esta, aqui, aqui));
        } else {
            boton("Describir esta imagen con Gemini", () -> vdDescribir(esta, aqui, aqui));
        }
        encabezado("Texto sobre la imagen");
        texto("Escribe un título, una dedicatoria o cualquier texto corto. Se muestra sobre esta imagen "
                + "mientras dura. Si es largo, el tamaño se reduce solo para que quepa. En videos "
                + "verticales se separa de los bordes para que las plataformas no lo tapen. Déjalo "
                + "vacío si no quieres texto.");
        campoMultilinea("Texto de esta imagen", im.rotulo, 300, s -> im.rotulo = s);
        ciclo("Posición del texto", Video.NOMBRES_ROTULO_POS, () -> im.rotuloPos,
                v -> im.rotuloPos = v);
        ciclo("Tamaño del texto", Video.NOMBRES_ROTULO_TAM, () -> im.rotuloTam,
                v -> im.rotuloTam = v);
        casilla("Franja oscura detrás del texto, para que se lea mejor", im.rotuloFondo,
                m -> im.rotuloFondo = m);
        encabezado("Zoom");
        ciclo("Zoom de esta imagen", Video.NOMBRES_ZOOM, () -> im.zoom, v -> im.zoom = v);
        if (vdPersonal) {
            encabezado("Duración");
            final TextView t = texto("Duración de esta imagen: " + vdSegDe(idx) + " segundos.");
            t.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            final int[] pasos = {-10, -1, 1, 10};
            final String[] nombres = {"Menos 10 segundos", "Menos 1 segundo", "Más 1 segundo",
                "Más 10 segundos"};
            for (int k = 0; k < pasos.length; k++) {
                final int paso = pasos[k];
                boton(nombres[k], () -> {
                    im.seg = Math.max(1, Math.min(3600, vdSegDe(idx) + paso));
                    t.setText("Duración de esta imagen: " + im.seg + " segundos.");
                });
            }
        }
        encabezado("Orden");
        if (idx > 0) {
            boton("Mover esta imagen antes, a la posición " + idx, () -> {
                vdImagenes.remove(idx);
                vdImagenes.add(idx - 1, im);
                vdOpcionesImagen(idx - 1, regreso);
            });
        }
        if (idx < vdImagenes.size() - 1) {
            boton("Mover esta imagen después, a la posición " + (idx + 2), () -> {
                vdImagenes.remove(idx);
                vdImagenes.add(idx + 1, im);
                vdOpcionesImagen(idx + 1, regreso);
            });
        }
        boton("Quitar esta imagen", () -> {
            vdImagenes.remove(idx);
            regreso.run();
        });
        boton("Volver a la lista", regreso);
    }

    private void vdBloqueFormato() {
        ciclo("Tamaño del video", Video.NOMBRES_FORMATO, () -> vdFormato, v -> vdFormato = v);
        ciclo("Encuadre de las imágenes", Video.NOMBRES_ENCUADRE, () -> vdEncuadre,
                v -> vdEncuadre = v);
    }

    private void vdBloqueDuracion(final Runnable refrescar) {
        ciclo("Duración de las imágenes", new String[] {"Reparto automático en partes iguales",
                "Personalizar la duración de cada imagen"}, () -> vdPersonal ? 1 : 0, v -> {
                    vdPersonal = v == 1;
                    refrescar.run();
                });
        texto(vdResumenDuracion());
        if (vdPersonal) {
            texto("Toca una imagen para cambiar su duración.");
            vdListaImagenes(refrescar);
            boton("Repartir otra vez en partes iguales", () -> {
                for (Video.Imagen im : vdImagenes) im.seg = 0;
                refrescar.run();
            });
        }
    }

    private void vdBloqueEfectos(final Runnable refrescar) {
        encabezado("Zoom");
        texto("El zoom acerca o aleja suavemente la imagen hacia el centro mientras dura. Se crea "
                + "con la mejor calidad posible, así que el video tarda bastante más en exportarse. "
                + "También puedes poner el zoom a una sola imagen: tócala en la lista de imágenes.");
        ciclo("Zoom en todas las imágenes", new String[] {"Sin zoom en ninguna",
                "Acercar en todas", "Alejar en todas", "Alternar: acercar, alejar, acercar..."},
                () -> vdZoomGlobal, v -> {
                    vdZoomGlobal = v;
                    for (int i = 0; i < vdImagenes.size(); i++) {
                        vdImagenes.get(i).zoom = v == 0 ? 0 : (v == 1 ? 1 : (v == 2 ? 2
                                : (i % 2 == 0 ? 1 : 2)));
                    }
                });
        encabezado("Sonido de transición");
        texto("Un sonido corto y suave que suena cada vez que cambia la imagen, para saber el "
                + "momento exacto del cambio. Se mezcla en el audio del video. Es opcional.");
        casilla("Poner un sonido en cada cambio de imagen", vdTrans, m -> {
            vdTrans = m;
            refrescar.run();
        });
        if (vdTrans) {
            ciclo("Sonido", Audio.NOMBRES_TRANSICION, () -> vdTransTipo, v -> vdTransTipo = v);
            ajustador("Volumen del sonido de transición", () -> vdTransVol, " por ciento",
                    "Bajar el volumen del sonido", "Subir el volumen del sonido",
                    () -> vdTransVol = Math.max(10, vdTransVol - 10),
                    () -> vdTransVol = Math.min(100, vdTransVol + 10), null);
            boton("Escuchar el sonido de transición", () -> {
                short[] c = Audio.sonidoTransicion(vdTransTipo);
                double g = vdTransVol / 100.0;
                for (int i = 0; i < c.length; i++) c[i] = (short) Math.round(c[i] * g);
                reproducir(c, () -> { });
            });
            if (vdImagenes.size() < 2) {
                texto("Con una sola imagen no hay cambios, así que el sonido no se oirá.");
            }
        }
    }

    private void vdBloqueExportar(final Runnable volverAqui) {
        int n = vdImagenes.size();
        String t = "Se creará un video de " + textoDuracion(Math.round(vdTotalSeg())) + " con " + n
                + (n == 1 ? " imagen" : " imágenes") + ". Puede tardar varios minutos si el audio es largo.";
        if (Video.hayZoom(vdImagenes)) {
            t += " Como hay zoom, el video se crea cuadro por cuadro con la mejor calidad y tarda "
                    + "mucho más: puede tardar tanto como lo que dura el video, o más, según tu "
                    + "teléfono. Conéctalo a la corriente y no cierres la aplicación.";
        }
        texto(t);
        final EditText nombre = campoNombre("Nombre del archivo", vdNombreSalida);
        boton("Crear el video", () -> {
            vdNombreSalida = nombre.getText().toString();
            vdExportar(volverAqui);
        });
        vdBloqueProyecto();
    }

    private void vdBloqueProyecto() {
        encabezado("Proyecto");
        texto(vdProyecto.isEmpty()
                ? "Guarda este trabajo como proyecto para seguirlo después sin volver a elegir todo."
                : "Proyecto actual: " + vdProyecto + ".");
        final EditText nombre = campoNombre("Nombre del proyecto",
                vdProyecto.isEmpty() ? "Mi proyecto de video" : vdProyecto);
        final TextView estado = texto("");
        estado.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        boton("Guardar el proyecto", () -> guardarProyecto(nombre.getText().toString(), estado));
    }

    /** Los momentos, en segundos, en que cambia la imagen. */
    private static double[] momentosDeCambio(double[] dur, double total) {
        double[] m = new double[Math.max(0, dur.length - 1)];
        double acum = 0;
        int k = 0;
        for (int i = 0; i < dur.length - 1; i++) {
            acum += dur[i];
            if (acum >= total) break;
            m[k++] = acum;
        }
        return Arrays.copyOf(m, k);
    }

    private void vdExportar(final Runnable volverAqui) {
        final File audio = vdAudioPcm;
        final List<Video.Imagen> imagenes = new ArrayList<>(vdImagenes);
        final double total = vdTotalSeg();
        final double[] dur = vdDuraciones();
        final int formato = vdFormato;
        final int encuadre = vdEncuadre;
        final boolean trans = vdTrans && imagenes.size() > 1;
        final int tipo = vdTransTipo;
        final int vol = vdTransVol;
        final String nombre = nombreArchivo(vdNombreSalida, "Video con audio") + ".mp4";
        final File conSonido = cache("video_audio_t.pcm");
        final File m4a = cache("video_audio.m4a");
        final File solo = cache("video_solo.mp4");
        final File fin = cache("video_final.mp4");
        final Uri[] resultado = new Uri[1];
        final boolean largo = Video.hayZoom(imagenes);
        trabajar("Creando el video",
                "Estoy creando tu video. Puede tardar " + (largo ? "mucho" : "varios minutos")
                        + ". Mantén la aplicación abierta.",
                (p, c) -> {
                    try {
                        m4a.delete();
                        solo.delete();
                        fin.delete();
                        File origen = audio;
                        if (trans) {
                            conSonido.delete();
                            Audio.agregarTransiciones(audio, conSonido,
                                    momentosDeCambio(dur, total), tipo, vol, escalar(p, 0, 4), c);
                            origen = conSonido;
                        }
                        Audio.pcmAM4a(origen, m4a, escalar(p, trans ? 4 : 0, 8), c);
                        Video.crear(this, imagenes, dur, m4a, total, formato, encuadre, fin, solo,
                                escalar(p, 8, 98), c);
                        resultado[0] = Audio.guardar(this, fin, nombre, "video/mp4", true);
                        p.porcentaje(100);
                    } finally {
                        conSonido.delete();
                        m4a.delete();
                        solo.delete();
                        fin.delete();
                    }
                },
                () -> vdResultado(resultado[0], nombre), volverAqui);
    }

    private void vdResultado(final Uri uri, String nombre) {
        final String guardado = Audio.nombreGuardado(this, uri, nombre);
        nuevaPantalla("Video guardado", this::mostrarMenu);
        String t = "Tu video se guardó en la carpeta Películas, dentro de la carpeta " + Audio.CARPETA
                + ", con el nombre " + guardado + ".";
        texto(t);
        guia("Listo. " + t);
        boton("Reproducir el video", () -> abrirArchivo(uri, "video/mp4"));
        boton("Compartir el video", () -> compartir(uri, "video/mp4"));
        boton("Crear otro video", () -> {
            vdImagenes.clear();
            vdPersonal = false;
            vdProyecto = "";
            vdZoomGlobal = 0;
            empezar(this::vdInicio, this::vdManual);
        });
        boton("Volver al menú", this::mostrarMenu);
    }

    // ----- Pasos guiados -----

    private void vdInicio() {
        nuevaPantalla("Crear video con audio e imágenes", this::mostrarMenu);
        String t = "Vamos a crear un video con tu audio y una o varias imágenes. Te guiaré paso a "
                + "paso: elegir el audio, elegir las imágenes (y describirlas si quieres), el tamaño "
                + "y el encuadre, cuánto dura cada imagen, el zoom y el sonido de transición, y "
                + "exportar el video.";
        texto(t);
        guia(t);
        boton("Comenzar", this::vdPasoAudio);
        boton("Abrir un proyecto guardado", this::mostrarProyectos);
        boton("Volver al menú", this::mostrarMenu);
    }

    private void vdPasoAudio() {
        nuevaPantalla("Paso 1: el audio del video", this::vdInicio);
        String t = "Elige el audio que sonará en el video. Puede ser un archivo tuyo o la última "
                + "mezcla que hiciste aquí.";
        texto(t);
        guia(t);
        vdBloqueAudio(this::vdPasoAudio);
        if (vdAudioPcm != null) boton("Siguiente: elegir las imágenes", this::vdPasoImagenes);
        boton("Atrás", this::vdInicio);
    }

    private void vdPasoImagenes() {
        nuevaPantalla("Paso 2: las imágenes", this::vdPasoAudio);
        String t = "Elige una o varias imágenes. Aparecerán en el orden de la lista. Reviso el tamaño y "
                + "el enfoque de cada una, porque una imagen borrosa o pequeña hace que el video se "
                + "vea mal y las plataformas lo recomienden menos. Si quieres, Gemini te describe "
                + "cada imagen para que sepas qué fotos agregaste.";
        texto(t);
        guia(t);
        vdBloqueImagenes(this::vdPasoImagenes);
        if (!vdImagenes.isEmpty()) boton("Siguiente: tamaño y encuadre", this::vdPasoFormato);
        boton("Atrás", this::vdPasoAudio);
    }

    private void vdPasoFormato() {
        nuevaPantalla("Paso 3: tamaño y encuadre", this::vdPasoImagenes);
        String t = "Elige el tamaño del video según dónde lo vas a publicar. Para el encuadre, la "
                + "opción de fondo borroso muestra la imagen completa sin recortar nada importante.";
        texto(t);
        guia(t);
        vdBloqueFormato();
        boton("Siguiente: duración de las imágenes", this::vdPasoDuracion);
        boton("Atrás", this::vdPasoImagenes);
    }

    private void vdPasoDuracion() {
        nuevaPantalla("Paso 4: duración de las imágenes", this::vdPasoFormato);
        String t = "Decide cuánto dura cada imagen: repartidas en partes iguales a lo largo del audio, "
                + "o con la duración que tú elijas para cada una.";
        texto(t);
        guia(t);
        vdBloqueDuracion(this::vdPasoDuracion);
        boton("Siguiente: zoom y sonido de transición", this::vdPasoEfectos);
        boton("Atrás", this::vdPasoFormato);
    }

    private void vdPasoEfectos() {
        nuevaPantalla("Paso 5: zoom y sonido de transición", this::vdPasoDuracion);
        String t = "Dos adornos opcionales. El zoom da movimiento a las imágenes. El sonido de "
                + "transición marca el momento exacto en que pasa de una imagen a la siguiente.";
        texto(t);
        guia(t);
        vdBloqueEfectos(this::vdPasoEfectos);
        boton("Siguiente: crear el video", this::vdPasoExportar);
        boton("Atrás", this::vdPasoDuracion);
    }

    private void vdPasoExportar() {
        nuevaPantalla("Paso 6: crear el video", this::vdPasoEfectos);
        String t = "Último paso. Elige el nombre del archivo y crea el video. También puedes guardar "
                + "el trabajo como proyecto.";
        texto(t);
        guia(t);
        vdBloqueExportar(this::vdPasoExportar);
        boton("Atrás", this::vdPasoEfectos);
    }

    // ----- Modo manual -----

    private void vdManual() {
        nuevaPantalla("Crear video, modo manual", this::mostrarMenu);
        texto("En modo manual tienes todas las opciones en esta pantalla, sin pasos guiados.");
        encabezado("Audio");
        vdBloqueAudio(this::vdManual);
        encabezado("Imágenes");
        vdBloqueImagenes(this::vdManual);
        if (vdAudioPcm == null || vdImagenes.isEmpty()) {
            texto("Cuando elijas el audio y al menos una imagen, aparecerán los demás ajustes.");
            boton("Abrir un proyecto guardado", this::mostrarProyectos);
            boton("Volver al menú", this::mostrarMenu);
            return;
        }
        encabezado("Tamaño y encuadre");
        vdBloqueFormato();
        encabezado("Duración de las imágenes");
        vdBloqueDuracion(this::vdManual);
        vdBloqueEfectos(this::vdManual);
        encabezado("Crear el video");
        vdBloqueExportar(this::vdManual);
        boton("Volver al menú", this::mostrarMenu);
    }

    // =====================================================================
    // AGREGAR UN AUDIO A UN VIDEO DEL TELÉFONO
    // =====================================================================

    private boolean avMezcla() {
        return avModo == 1 && avInfo != null && avInfo.audio;
    }

    private boolean avListo() {
        return avInfo != null && avNuevoPcm != null;
    }

    private long avTotalMarcos() {
        return Math.max(1, Math.round(avInfo.seg * Audio.HZ));
    }

    private Audio.AjustesAv ajustesAv() {
        Audio.AjustesAv a = new Audio.AjustesAv();
        a.mezclar = avMezcla();
        a.volOrig = avVolOrig;
        a.volNuevo = avVolNuevo;
        a.inicioSeg = avInicio;
        a.repetir = avRepetir;
        a.desvanecer = avDesvanecer;
        return a;
    }

    private String avEstadoVolumenes() {
        String s = "Audio nuevo al " + avVolNuevo + " por ciento.";
        if (avMezcla()) s = "Audio original del video al " + avVolOrig + " por ciento. " + s;
        return s;
    }

    // ----- Bloques -----

    private void avBloqueVideo(final Runnable refrescar) {
        boolean hay = avInfo != null;
        if (hay) {
            texto("Video: " + avVideoNombre + ". Dura " + textoDuracion(Math.round(avInfo.seg))
                    + ". Tamaño: " + avInfo.ancho + " por " + avInfo.alto + " píxeles. "
                    + (avInfo.audio ? "Ya tiene audio." : "No tiene audio."));
        } else {
            texto("Todavía no elegiste el video.");
        }
        boton(hay ? "Cambiar el video" : "Elegir el video", () ->
                elegirArchivo(PEDIDO_VIDEO, "video/*", false, uris -> {
                    final Uri u = uris.get(0);
                    final String nombre = nombreDe(u);
                    final Video.Info[] info = new Video.Info[1];
                    trabajar("Revisando el video", "Estoy leyendo los datos de " + nombre + ".",
                            (p, c) -> info[0] = Video.info(this, u),
                            () -> {
                                avVideoUri = u;
                                avVideoNombre = nombre;
                                avInfo = info[0];
                                avOrigPcm = null;
                                if (!avInfo.audio) avModo = 0;
                                avInicio = entre(avInicio, 0, Math.max(0, (int) avInfo.seg - 1));
                                refrescar.run();
                            }, refrescar);
                }));
    }

    private void avBloqueAudio(final Runnable refrescar) {
        boolean hay = avNuevoPcm != null;
        if (hay) {
            texto("Audio nuevo: " + avNuevoNombre + ". Dura " + duracion(avNuevoMarcos) + ".");
        } else {
            texto("Todavía no elegiste el audio nuevo.");
        }
        boton(hay ? "Cambiar el audio nuevo" : "Elegir el audio nuevo", () ->
                elegirArchivo(PEDIDO_AUDIO_NUEVO, "audio/*", false, uris -> {
                    final Uri u = uris.get(0);
                    final String nombre = nombreDe(u);
                    final File pcm = cache("av_nuevo.pcm");
                    prepararAudio(u, nombre, pcm, marcos -> {
                        avNuevoNombre = nombre;
                        avNuevoPcm = pcm;
                        avNuevoMarcos = marcos;
                        refrescar.run();
                    }, refrescar);
                }));
    }

    private void avBloqueModo(final Runnable refrescar) {
        if (avInfo == null) return;
        if (!avInfo.audio) {
            texto("Este video no tiene audio, así que el audio nuevo se agrega tal cual.");
            return;
        }
        texto("Este video ya tiene su propio audio. Elige qué hacer con él.");
        ciclo("Qué hacer con el audio del video", new String[] {
            "Reemplazarlo: solo se oirá el audio nuevo",
            "Conservarlo y mezclar el audio nuevo con él"}, () -> avModo, v -> {
                avModo = v;
                if (v == 1 && avVolNuevo == 100) avVolNuevo = 30;
                else if (v == 0 && avVolNuevo == 30) avVolNuevo = 100;
                refrescar.run();
            });
    }

    private void avBloqueVolumenes(Runnable alCambiar) {
        if (avMezcla()) {
            ajustador("Volumen del audio original del video", () -> avVolOrig, " por ciento",
                    "Bajar el volumen del audio original", "Subir el volumen del audio original",
                    () -> avVolOrig = Math.max(0, avVolOrig - 10),
                    () -> avVolOrig = Math.min(150, avVolOrig + 10), alCambiar);
        }
        ajustador("Volumen del audio nuevo", () -> avVolNuevo, " por ciento",
                "Bajar el volumen del audio nuevo", "Subir el volumen del audio nuevo",
                () -> avVolNuevo = Math.max(0, avVolNuevo - 5),
                () -> avVolNuevo = Math.min(150, avVolNuevo + 5), alCambiar);
    }

    private void avBloqueInicio() {
        if (!avListo()) return;
        String d = "El video dura " + textoDuracion(Math.round(avInfo.seg)) + ". El audio nuevo dura "
                + duracion(avNuevoMarcos) + ".";
        if (avNuevoMarcos + (long) avInicio * Audio.HZ > avTotalMarcos()) {
            d += " El audio nuevo se cortará cuando termine el video.";
        } else if (avRepetir) {
            d += " Como es más corto, se repetirá hasta el final del video.";
        } else {
            d += " Como es más corto, el resto del video quedará sin el audio nuevo"
                    + (avMezcla() ? ", solo con su audio original." : ", en silencio.");
        }
        texto(d);
        casilla("Repetir el audio nuevo si es más corto que el video", avRepetir, m -> avRepetir = m);
        casilla("Desvanecer el audio nuevo al empezar y al terminar", avDesvanecer,
                m -> avDesvanecer = m);
        final int max = Math.max(0, (int) avInfo.seg - 1);
        final TextView t = texto("El audio nuevo empieza en el segundo " + avInicio + " del video.");
        t.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        final int[] pasos = {-10, -1, 1, 10};
        final String[] nombres = {"Empezar 10 segundos antes", "Empezar 1 segundo antes",
            "Empezar 1 segundo después", "Empezar 10 segundos después"};
        for (int k = 0; k < pasos.length; k++) {
            final int paso = pasos[k];
            boton(nombres[k], () -> {
                avInicio = entre(avInicio + paso, 0, max);
                t.setText("El audio nuevo empieza en el segundo " + avInicio + " del video.");
            });
        }
    }

    /** Si hay que conservar el audio del video y aún no se lee, lo lee y sigue con luego. */
    private void avAsegurarOriginal(final Runnable luego, final Runnable atras) {
        if (!avMezcla() || avOrigPcm != null) {
            luego.run();
            return;
        }
        final File pcm = cache("av_original.pcm");
        prepararAudio(avVideoUri, avVideoNombre, pcm, marcos -> {
            avOrigPcm = pcm;
            luego.run();
        }, atras);
    }

    private void avBloqueVista(boolean conPreguntas, final Runnable refrescar) {
        final TextView estado = texto(avEstadoVolumenes());
        estado.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        boton("Escuchar la vista previa", () -> avVista(estado, "", refrescar));
        if (conPreguntas) {
            encabezado("Según lo que escuchas");
            boton("El audio nuevo se oye muy fuerte", () -> {
                avVolNuevo = Math.max(0, avVolNuevo - 5);
                avVista(estado, "Bajé el audio nuevo.", refrescar);
            });
            boton("El audio nuevo casi no se oye", () -> {
                avVolNuevo = Math.min(150, avVolNuevo + 5);
                avVista(estado, "Subí el audio nuevo.", refrescar);
            });
            if (avMezcla()) {
                boton("No entiendo bien el audio original", () -> {
                    avVolNuevo = Math.max(0, avVolNuevo - 10);
                    avVolOrig = Math.min(150, avVolOrig + 10);
                    avVista(estado, "Bajé el audio nuevo y subí el original.", refrescar);
                });
            }
        }
    }

    private void avVista(final TextView estado, final String antes, final Runnable refrescar) {
        if (avMezcla() && avOrigPcm == null) {
            avAsegurarOriginal(() -> {
                refrescar.run();
                aviso("Listo. Ya puedes escuchar la vista previa.");
                guia("Listo. Ya puedes escuchar la vista previa.");
            }, refrescar);
            return;
        }
        detenerVista();
        if (tts != null) tts.stop();
        final int id = pantallaId;
        final Audio.AjustesAv a = ajustesAv();
        final File orig = avMezcla() ? avOrigPcm : null;
        final File nuevo = avNuevoPcm;
        final long total = avTotalMarcos();
        final String prefijo = antes.isEmpty() ? "" : antes + " ";
        estado.setText(prefijo + avEstadoVolumenes() + " Preparando la vista previa.");
        new Thread(() -> {
            short[] datos = null;
            String error = null;
            try {
                datos = Audio.vistaPreviaVideo(orig, nuevo, a, total);
            } catch (Throwable e) {
                error = textoError(e);
            }
            final short[] d = datos;
            final String falla = error;
            runOnUiThread(() -> {
                if (id != pantallaId) return;
                if (falla != null) {
                    estado.setText(falla);
                    return;
                }
                estado.setText(prefijo + avEstadoVolumenes() + " Escuchando la vista previa.");
                reproducir(d, () -> {
                    if (id != pantallaId) return;
                    estado.setText(avEstadoVolumenes() + " Terminó la vista previa.");
                    guia("Terminó la vista previa. Dime cómo se oyó.");
                });
            });
        }).start();
    }

    private void avBloqueExportar(final Runnable volverAqui) {
        texto("El video final durará lo mismo que el original: "
                + textoDuracion(Math.round(avInfo.seg)) + ". El video no se vuelve a codificar, así "
                + "que no pierde calidad y el proceso es rápido.");
        final EditText nombre = campoNombre("Nombre del archivo", avNombreSalida);
        boton("Crear el video con el audio nuevo", () -> {
            avNombreSalida = nombre.getText().toString();
            avExportar(volverAqui);
        });
    }

    private void avExportar(final Runnable volverAqui) {
        avAsegurarOriginal(() -> avExportarYa(volverAqui), volverAqui);
    }

    private void avExportarYa(final Runnable volverAqui) {
        final Audio.AjustesAv a = ajustesAv();
        final long total = avTotalMarcos();
        final File orig = avMezcla() ? avOrigPcm : null;
        final File nuevo = avNuevoPcm;
        final Uri video = avVideoUri;
        final int giro = avInfo.giro;
        final double seg = avInfo.seg;
        final String nombre = nombreArchivo(avNombreSalida, "Video con audio nuevo") + ".mp4";
        final File m4a = cache("av_salida.m4a");
        final File fin = cache("av_final.mp4");
        final Uri[] resultado = new Uri[1];
        trabajar("Creando el video",
                "Estoy poniendo el audio nuevo en tu video. Mantén la aplicación abierta.",
                (p, c) -> {
                    try {
                        m4a.delete();
                        fin.delete();
                        Audio.exportarAudioVideo(orig, nuevo, a, total, m4a, escalar(p, 0, 80), c);
                        Video.unirConAudio(this, video, m4a, giro, seg, fin, escalar(p, 80, 98), c);
                        resultado[0] = Audio.guardar(this, fin, nombre, "video/mp4", true);
                        p.porcentaje(100);
                    } finally {
                        m4a.delete();
                        fin.delete();
                    }
                },
                () -> avResultado(resultado[0], nombre), volverAqui);
    }

    private void avResultado(final Uri uri, String nombre) {
        final String guardado = Audio.nombreGuardado(this, uri, nombre);
        nuevaPantalla("Video guardado", this::mostrarMenu);
        String t = "Tu video se guardó en la carpeta Películas, dentro de la carpeta " + Audio.CARPETA
                + ", con el nombre " + guardado + ".";
        texto(t);
        guia("Listo. " + t);
        boton("Reproducir el video", () -> abrirArchivo(uri, "video/mp4"));
        boton("Compartir el video", () -> compartir(uri, "video/mp4"));
        boton("Probar otro audio con este mismo video", () -> {
            avNuevoPcm = null;
            empezar(this::avPasoAudio, this::avManual);
        });
        boton("Volver al menú", this::mostrarMenu);
    }

    // ----- Pasos guiados -----

    private void avInicio() {
        nuevaPantalla("Agregar un audio a un video", this::mostrarMenu);
        String t = "Vamos a ponerle un audio nuevo a un video que ya tienes en el teléfono: una "
                + "música, una narración o lo que quieras. Puedes reemplazar el audio del video o "
                + "mezclarlo con el nuevo. Te guiaré paso a paso: el video, el audio nuevo, qué hacer "
                + "con el audio original y los volúmenes, cuándo empieza, una vista previa y por "
                + "último crear el video.";
        texto(t);
        guia(t);
        boton("Comenzar", this::avPasoVideo);
        boton("Volver al menú", this::mostrarMenu);
    }

    private void avPasoVideo() {
        nuevaPantalla("Paso 1: el video", this::avInicio);
        String t = "Elige el video del teléfono al que quieres ponerle el audio nuevo.";
        texto(t);
        guia(t);
        avBloqueVideo(this::avPasoVideo);
        if (avInfo != null) boton("Siguiente: elegir el audio nuevo", this::avPasoAudio);
        boton("Atrás", this::avInicio);
    }

    private void avPasoAudio() {
        nuevaPantalla("Paso 2: el audio nuevo", this::avPasoVideo);
        String t = "Elige el audio que quieres poner. Puede ser MP3, M4A, WAV u otros formatos, o un "
                + "video del que quieras usar solo el sonido.";
        texto(t);
        guia(t);
        avBloqueAudio(this::avPasoAudio);
        if (avNuevoPcm != null) boton("Siguiente: audio original y volúmenes", this::avPasoModo);
        boton("Atrás", this::avPasoVideo);
    }

    private void avPasoModo() {
        nuevaPantalla("Paso 3: audio original y volúmenes", this::avPasoAudio);
        String t = "Decide qué pasa con el audio que ya tiene el video y ajusta los volúmenes. Para "
                + "una música de fondo, el audio nuevo suele ir entre 15 y 30 por ciento. Lo "
                + "comprobarás en la vista previa.";
        texto(t);
        guia(t);
        avBloqueModo(this::avPasoModo);
        avBloqueVolumenes(null);
        boton("Siguiente: cuándo empieza el audio nuevo", this::avPasoInicio);
        boton("Atrás", this::avPasoAudio);
    }

    private void avPasoInicio() {
        nuevaPantalla("Paso 4: cuándo empieza", this::avPasoModo);
        String t = "Elige en qué segundo del video empieza el audio nuevo y qué pasa si duran distinto.";
        texto(t);
        guia(t);
        avBloqueInicio();
        boton("Siguiente: escuchar la vista previa", this::avPasoVista);
        boton("Atrás", this::avPasoModo);
    }

    private void avPasoVista() {
        nuevaPantalla("Paso 5: vista previa", this::avPasoInicio);
        String t = "Escucharás un fragmento de unos 20 segundos del audio final del video. Después dime "
                + "cómo se oyó y lo ajusto. Puedes repetirlo las veces que quieras.";
        texto(t);
        guia(t);
        avBloqueVista(true, this::avPasoVista);
        boton("Se oye bien, continuar", this::avPasoExportar);
        boton("Atrás", this::avPasoInicio);
    }

    private void avPasoExportar() {
        nuevaPantalla("Paso 6: crear el video", this::avPasoVista);
        String t = "Último paso. Elige el nombre del archivo y crea el video.";
        texto(t);
        guia(t);
        avBloqueExportar(this::avPasoExportar);
        boton("Atrás", this::avPasoVista);
    }

    // ----- Modo manual -----

    private void avManual() {
        nuevaPantalla("Agregar audio a un video, modo manual", this::mostrarMenu);
        texto("En modo manual tienes todas las opciones en esta pantalla, sin pasos guiados.");
        encabezado("Video");
        avBloqueVideo(this::avManual);
        encabezado("Audio nuevo");
        avBloqueAudio(this::avManual);
        if (!avListo()) {
            texto("Cuando elijas el video y el audio nuevo, aparecerán los demás ajustes.");
            boton("Volver al menú", this::mostrarMenu);
            return;
        }
        encabezado("Audio original y volúmenes");
        avBloqueModo(this::avManual);
        avBloqueVolumenes(null);
        encabezado("Cuándo empieza y duración");
        avBloqueInicio();
        encabezado("Vista previa");
        avBloqueVista(false, this::avManual);
        encabezado("Crear el video");
        avBloqueExportar(this::avManual);
        boton("Volver al menú", this::mostrarMenu);
    }

    // =====================================================================
    // CAMBIAR LA VOZ DE UN AUDIO CON EFECTOS
    // =====================================================================

    private String vzEstado() {
        return Efectos.NOMBRES[vzEfecto] + ". " + Efectos.resumen(vzEfecto, vzIntensidad);
    }

    private void vzBloqueAudio(final Runnable refrescar) {
        boolean hay = vzPcm != null;
        if (hay) {
            texto("Audio: " + vzNombre + ". Dura " + duracion(vzMarcos) + ".");
        } else {
            texto("Todavía no elegiste el audio.");
        }
        boton(hay ? "Cambiar el audio" : "Elegir el audio", () ->
                elegirArchivo(PEDIDO_VOZ_EFECTO, "audio/*", false, uris -> {
                    final Uri u = uris.get(0);
                    final String nombre = nombreDe(u);
                    final File pcm = cache("voz_efecto.pcm");
                    prepararAudio(u, nombre, pcm, marcos -> {
                        vzNombre = nombre;
                        vzPcm = pcm;
                        vzMarcos = marcos;
                        refrescar.run();
                    }, refrescar);
                }));
    }

    private void vzBloqueEfecto(final Runnable refrescar) {
        texto("Efecto elegido: " + Efectos.NOMBRES[vzEfecto] + ". " + Efectos.DESCRIPCIONES[vzEfecto]);
        for (int i = 0; i < Efectos.NOMBRES.length; i++) {
            final int idx = i;
            boton(Efectos.NOMBRES[i] + (i == vzEfecto ? ". Elegido." : ""), () -> {
                vzEfecto = idx;
                refrescar.run();
                guia(Efectos.DESCRIPCIONES[idx]);
            });
        }
    }

    private void vzBloqueIntensidad() {
        final TextView res = texto(Efectos.resumen(vzEfecto, vzIntensidad));
        res.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ajustador("Intensidad del efecto", () -> vzIntensidad, " de 10",
                "Bajar la intensidad", "Subir la intensidad",
                () -> vzIntensidad = Math.max(1, vzIntensidad - 1),
                () -> vzIntensidad = Math.min(10, vzIntensidad + 1),
                () -> res.setText(Efectos.resumen(vzEfecto, vzIntensidad)));
    }

    private void vzBloqueVista(boolean conPreguntas) {
        final TextView estado = texto(vzEstado());
        estado.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        boton("Escuchar con el efecto", () -> vzReproducir(estado, "", true));
        boton("Escuchar el original, sin efecto", () -> vzReproducir(estado, "", false));
        if (conPreguntas) {
            encabezado("Según lo que escuchas");
            boton("El efecto es demasiado fuerte", () -> {
                vzIntensidad = Math.max(1, vzIntensidad - 2);
                vzReproducir(estado, "Bajé la intensidad.", true);
            });
            boton("Casi no se nota el efecto", () -> {
                vzIntensidad = Math.min(10, vzIntensidad + 2);
                vzReproducir(estado, "Subí la intensidad.", true);
            });
        }
    }

    private void vzReproducir(final TextView estado, final String antes, final boolean conEfecto) {
        detenerVista();
        if (tts != null) tts.stop();
        final int id = pantallaId;
        final File pcm = vzPcm;
        final int efecto = vzEfecto;
        final int intensidad = vzIntensidad;
        final String prefijo = antes.isEmpty() ? "" : antes + " ";
        estado.setText(prefijo + vzEstado() + " Preparando la vista previa.");
        new Thread(() -> {
            short[] datos = null;
            String error = null;
            try {
                datos = Audio.vistaPreviaEfecto(pcm, efecto, intensidad, conEfecto);
            } catch (Throwable e) {
                error = textoError(e);
            }
            final short[] d = datos;
            final String falla = error;
            runOnUiThread(() -> {
                if (id != pantallaId) return;
                if (falla != null) {
                    estado.setText(falla);
                    return;
                }
                estado.setText(prefijo + vzEstado()
                        + (conEfecto ? " Escuchando con el efecto." : " Escuchando el original."));
                reproducir(d, () -> {
                    if (id != pantallaId) return;
                    estado.setText(vzEstado() + " Terminó la vista previa.");
                    guia("Terminó la vista previa. Dime cómo se oyó.");
                });
            });
        }).start();
    }

    private void vzBloqueExportar(final Runnable volverAqui) {
        texto("El audio final durará un poco más que el original si el efecto deja una cola, como el "
                + "eco o la cueva.");
        final EditText nombre = campoNombre("Nombre del archivo", vzNombreSalida);
        ciclo("Formato", new String[] {"M4A, recomendado: pesa poco y suena bien",
                "WAV, sin compresión: pesa mucho"}, () -> vzFormato, v -> vzFormato = v);
        boton("Guardar el audio con el efecto", () -> {
            vzNombreSalida = nombre.getText().toString();
            vzExportar(volverAqui);
        });
    }

    private void vzExportar(final Runnable volverAqui) {
        final boolean wav = vzFormato == 1;
        final String nombre = nombreArchivo(vzNombreSalida, "Audio con efecto de voz")
                + (wav ? ".wav" : ".m4a");
        final String mime = wav ? "audio/x-wav" : "audio/mp4";
        final File tmp = cache(wav ? "voz_salida.wav" : "voz_salida.m4a");
        final File pcm = vzPcm;
        final int efecto = vzEfecto;
        final int intensidad = vzIntensidad;
        final Uri[] resultado = new Uri[1];
        trabajar("Aplicando el efecto",
                "Estoy cambiando la voz y guardando tu audio. Puede tardar un poco si es largo. "
                        + "Mantén la aplicación abierta.",
                (p, c) -> {
                    try {
                        tmp.delete();
                        Audio.exportarEfecto(pcm, efecto, intensidad, wav, tmp, p, c);
                        resultado[0] = Audio.guardar(this, tmp, nombre, mime, false);
                    } finally {
                        tmp.delete();
                    }
                },
                () -> vzResultado(resultado[0], nombre, mime), volverAqui);
    }

    private void vzResultado(final Uri uri, String nombre, final String mime) {
        final String guardado = Audio.nombreGuardado(this, uri, nombre);
        ultimaMezclaUri = uri;
        ultimaMezclaNombre = guardado;
        nuevaPantalla("Audio guardado", this::mostrarMenu);
        String t = "Tu audio se guardó en la carpeta Música, dentro de la carpeta " + Audio.CARPETA
                + ", con el nombre " + guardado + ".";
        texto(t);
        guia("Listo. " + t);
        boton("Reproducir el audio", () -> abrirArchivo(uri, mime));
        boton("Compartir el audio", () -> compartir(uri, mime));
        boton("Crear un video con este audio", () -> vdUsarAudioExistente(uri, guardado));
        boton("Probar otro efecto con el mismo audio", () ->
                empezar(this::vzPasoEfecto, this::vzManual));
        boton("Volver al menú", this::mostrarMenu);
    }

    // ----- Pasos guiados -----

    private void vzInicio() {
        nuevaPantalla("Cambiar la voz con efectos", this::mostrarMenu);
        String t = "Vamos a cambiar la voz de un audio con un efecto: ardilla, gigante, robot, eco, "
                + "cueva o teléfono antiguo. Te guiaré paso a paso: el audio, el efecto, la "
                + "intensidad con una vista previa, y por último guardar.";
        texto(t);
        guia(t);
        boton("Comenzar", this::vzPasoAudio);
        boton("Volver al menú", this::mostrarMenu);
    }

    private void vzPasoAudio() {
        nuevaPantalla("Paso 1: el audio", this::vzInicio);
        String t = "Elige el audio al que quieres cambiarle la voz. Puede ser MP3, M4A, WAV u otros "
                + "formatos.";
        texto(t);
        guia(t);
        vzBloqueAudio(this::vzPasoAudio);
        if (vzPcm != null) boton("Siguiente: elegir el efecto", this::vzPasoEfecto);
        boton("Atrás", this::vzInicio);
    }

    private void vzPasoEfecto() {
        nuevaPantalla("Paso 2: el efecto", this::vzPasoAudio);
        String t = "Elige un efecto. Después podrás ajustar su intensidad y escucharlo.";
        texto(t);
        guia(t);
        vzBloqueEfecto(this::vzPasoEfecto);
        boton("Siguiente: intensidad y vista previa", this::vzPasoVista);
        boton("Atrás", this::vzPasoAudio);
    }

    private void vzPasoVista() {
        nuevaPantalla("Paso 3: intensidad y vista previa", this::vzPasoEfecto);
        String t = "Ajusta la intensidad y escucha un fragmento de unos 20 segundos. Después dime cómo "
                + "se oyó y lo ajusto. También puedes escuchar el original para comparar.";
        texto(t);
        guia(t);
        vzBloqueIntensidad();
        vzBloqueVista(true);
        boton("Se oye bien, continuar", this::vzPasoExportar);
        boton("Atrás", this::vzPasoEfecto);
    }

    private void vzPasoExportar() {
        nuevaPantalla("Paso 4: guardar", this::vzPasoVista);
        String t = "Último paso. Elige el nombre y el formato, y guarda el audio con el efecto.";
        texto(t);
        guia(t);
        vzBloqueExportar(this::vzPasoExportar);
        boton("Atrás", this::vzPasoVista);
    }

    // ----- Modo manual -----

    private void vzManual() {
        nuevaPantalla("Cambiar la voz, modo manual", this::mostrarMenu);
        texto("En modo manual tienes todas las opciones en esta pantalla, sin pasos guiados.");
        encabezado("Audio");
        vzBloqueAudio(this::vzManual);
        if (vzPcm == null) {
            texto("Cuando elijas el audio, aparecerán los demás ajustes.");
            boton("Volver al menú", this::mostrarMenu);
            return;
        }
        encabezado("Efecto");
        vzBloqueEfecto(this::vzManual);
        encabezado("Intensidad");
        vzBloqueIntensidad();
        encabezado("Vista previa");
        vzBloqueVista(false);
        encabezado("Guardar");
        vzBloqueExportar(this::vzManual);
        boton("Volver al menú", this::mostrarMenu);
    }

    // =====================================================================
    // CLAVE DE GEMINI
    // =====================================================================

    private String claveGemini() {
        String c = prefs.getString("gm_clave", "");
        return c == null ? "" : c.trim();
    }

    /** Sigue con siguiente si ya hay clave guardada; si no, la pide primero. */
    private void conClave(Runnable siguiente, Runnable atras) {
        if (claveGemini().isEmpty()) mostrarClave(siguiente, atras);
        else siguiente.run();
    }

    private void claveBloque(Runnable refrescar) {
        String c = claveGemini();
        if (c.isEmpty()) {
            texto("Todavía no has puesto tu clave de Gemini.");
        } else {
            texto("Clave de Gemini guardada. Termina en " + c.substring(Math.max(0, c.length() - 4))
                    + ".");
        }
        boton(c.isEmpty() ? "Poner mi clave de Gemini" : "Cambiar mi clave de Gemini",
                () -> mostrarClave(refrescar, refrescar));
    }

    private void mostrarClave(final Runnable alGuardar, final Runnable atras) {
        nuevaPantalla("Clave de Gemini", atras);
        String t = "Para transcribir audios y describir imágenes, la aplicación usa Gemini, de Google, "
                + "con tu propia clave gratuita. La clave se guarda solo en este teléfono y se envía "
                + "únicamente a Google. Puedes conseguirla gratis con tu cuenta de Google.";
        texto(t);
        guia(t);
        final String actual = claveGemini();
        if (!actual.isEmpty()) {
            texto("Ya hay una clave guardada que termina en "
                    + actual.substring(Math.max(0, actual.length() - 4))
                    + ". Si escribes otra, reemplaza a la anterior.");
        }
        final EditText campo = campoNombre("Escribe o pega tu clave de Gemini", "");
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        final TextView estado = texto("");
        estado.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        boton("Guardar la clave", () -> {
            String k = campo.getText().toString().trim();
            if (k.length() < 20 || k.contains(" ")) {
                estado.setText("La clave parece incompleta. Pégala completa, sin espacios.");
                return;
            }
            prefs.edit().putString("gm_clave", k).apply();
            Gemini.olvidar();
            aviso("Clave guardada.");
            alGuardar.run();
        });
        boton("Probar la clave", () -> {
            String escrita = campo.getText().toString().trim();
            final String k = escrita.isEmpty() ? actual : escrita;
            if (k.isEmpty()) {
                estado.setText("Primero escribe tu clave.");
                return;
            }
            estado.setText("Probando la clave, un momento.");
            final int id = pantallaId;
            new Thread(() -> {
                String r;
                try {
                    String[] m = Gemini.modelos(k);
                    r = "La clave funciona. Usaré los modelos " + m[0] + " y " + m[1] + ".";
                } catch (Exception e) {
                    r = textoError(e);
                }
                final String res = r;
                runOnUiThread(() -> {
                    if (id == pantallaId) estado.setText(res);
                });
            }).start();
        });
        if (!actual.isEmpty()) {
            boton("Borrar la clave guardada", () -> {
                prefs.edit().remove("gm_clave").apply();
                Gemini.olvidar();
                mostrarClave(alGuardar, atras);
            });
        }
        boton("Cómo conseguir una clave gratis", () -> abrir(Gemini.URL_CLAVE));
        boton("Volver", atras);
    }

    // =====================================================================
    // TRANSCRIBIR UN AUDIO A TEXTO
    // =====================================================================

    private void trReiniciar() {
        trPcm = null;
        trNombre = null;
        trTexto = null;
        trArchivo = null;
        trFalla = null;
        trParcial.setLength(0);
        trNombreSalida = "Transcripción";
        limpiarCache();
    }

    private void trBloqueAudio(final Runnable refrescar) {
        boolean hay = trPcm != null;
        if (hay) {
            texto("Audio: " + trNombre + ". Dura " + duracion(trMarcos) + ".");
        } else {
            texto("Todavía no elegiste el audio.");
        }
        boton(hay ? "Cambiar el audio" : "Elegir el audio", () ->
                elegirArchivo(PEDIDO_TRANSCRIPCION, "audio/*", false, uris -> {
                    final Uri u = uris.get(0);
                    final String nombre = nombreDe(u);
                    final File pcm = cache("trans_audio.pcm");
                    trPcm = null;
                    prepararAudio(u, nombre, pcm, marcos -> {
                        trNombre = nombre;
                        trPcm = pcm;
                        trMarcos = marcos;
                        int punto = nombre.lastIndexOf('.');
                        String base = punto > 0 ? nombre.substring(0, punto) : nombre;
                        trNombreSalida = base + " - transcripción";
                        refrescar.run();
                    }, refrescar);
                }));
    }

    private void trBloqueOpciones() {
        ciclo("Idioma del audio", Transcribir.IDIOMAS, () -> trIdioma, v -> trIdioma = v);
        casilla("Marcar quién habla cuando cambie la persona (Hablante 1, Hablante 2...)",
                trHablantes, m -> trHablantes = m);
    }

    private String trTextoAviso() {
        long seg = trMarcos / Audio.HZ;
        int partes = Transcribir.partesDe(seg);
        return "Tu audio dura " + textoDuracion(seg) + ". Se enviará a Gemini, de Google, con tu "
                + "clave, en " + partes + (partes == 1 ? " parte" : " partes de unos 10 minutos")
                + ". Cada parte usa una solicitud del cupo de tu clave. Con el nivel gratuito, Google "
                + "puede usar lo que envías para mejorar sus productos, así que no transcribas audios "
                + "privados o confidenciales. La transcripción puede tener errores: revísala. Si "
                + "marcas a los hablantes, los números pueden cambiar de una parte a otra.";
    }

    private void trBloqueEjecutar(final Runnable volverAqui) {
        if (trMarcos / Audio.HZ > Transcribir.MAX_SEG) {
            texto("Este audio es demasiado largo. Puedo transcribir hasta "
                    + textoDuracion(Transcribir.MAX_SEG) + ". Elige uno más corto.");
            return;
        }
        final EditText nombre = campoNombre("Nombre del archivo de texto", trNombreSalida);
        boton("Transcribir y guardar", () -> {
            trNombreSalida = nombre.getText().toString();
            conClave(() -> trTranscribir(volverAqui), volverAqui);
        });
    }

    private void trTranscribir(final Runnable volverAqui) {
        final File pcm = trPcm;
        final File mono = cache("trans_16k.pcm");
        final String clave = claveGemini();
        final int idioma = trIdioma;
        final boolean hablantes = trHablantes;
        final String nombre = nombreArchivo(trNombreSalida, "Transcripción") + ".txt";
        trParcial.setLength(0);
        trFalla = null;
        trTexto = null;
        trArchivo = null;
        trabajar("Transcribiendo el audio",
                "Estoy enviando tu audio a Gemini por partes. Puede tardar varios minutos. "
                        + "Mantén la aplicación abierta.",
                (p, c) -> {
                    try {
                        String res;
                        try {
                            res = Transcribir.transcribir(pcm, mono, clave, idioma, hablantes, p, c,
                                    trParcial);
                        } catch (Audio.Cancelacion e) {
                            throw e;
                        } catch (Exception e) {
                            if (trParcial.length() == 0) throw e;
                            trFalla = textoError(e);
                            res = trParcial.toString().trim();
                        }
                        trTexto = res;
                        if (trFalla == null) trArchivo = Audio.guardarTexto(this, res, nombre);
                    } finally {
                        mono.delete();
                    }
                },
                () -> trResultado(nombre), volverAqui);
    }

    private void trResultado(final String nombre) {
        final String texto = trTexto == null ? "" : trTexto;
        nuevaPantalla(trFalla == null ? "Transcripción lista" : "Transcripción incompleta",
                this::mostrarMenu);
        String t;
        if (trFalla != null) {
            t = "No pude terminar la transcripción. " + trFalla + " Esto es lo que alcancé a "
                    + "transcribir. Puedes guardarlo como archivo de texto.";
        } else {
            String guardado = Audio.nombreGuardado(this, trArchivo, nombre);
            String limpio = texto.trim();
            int palabras = limpio.isEmpty() ? 0 : limpio.split("\\s+").length;
            t = "Tu transcripción se guardó en la carpeta Documentos, dentro de la carpeta "
                    + Audio.CARPETA + ", con el nombre " + guardado + ". Tiene " + palabras
                    + " palabras.";
        }
        texto(t);
        guia(trFalla == null ? "Listo. " + t : t);
        final EditText cuerpo;
        if (texto.length() <= 60000) {
            TextView etiqueta = texto("Texto de la transcripción. Puedes corregirlo aquí.");
            cuerpo = new EditText(this);
            cuerpo.setId(View.generateViewId());
            cuerpo.setText(texto);
            cuerpo.setTextSize(18);
            cuerpo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            cuerpo.setMinLines(5);
            etiqueta.setLabelFor(cuerpo.getId());
            contenido.addView(cuerpo, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            cuerpo = null;
            texto("El texto es muy largo para mostrarlo aquí. Ábrelo con tu aplicación de texto.");
        }
        final TextView estado = texto("");
        estado.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (trArchivo != null) {
            if (cuerpo != null) {
                boton("Guardar los cambios", () -> {
                    try {
                        trTexto = cuerpo.getText().toString();
                        Audio.reescribirTexto(this, trArchivo, trTexto);
                        estado.setText("Cambios guardados.");
                    } catch (Exception e) {
                        estado.setText("No pude guardar los cambios. " + textoError(e));
                    }
                });
            }
            boton("Abrir el archivo de texto", () -> abrirArchivo(trArchivo, "text/plain"));
            boton("Compartir el archivo de texto", () -> compartir(trArchivo, "text/plain"));
        } else if (!texto.isEmpty()) {
            boton("Guardar lo transcrito como archivo de texto", () -> {
                try {
                    String contenidoActual = cuerpo != null ? cuerpo.getText().toString() : trTexto;
                    trArchivo = Audio.guardarTexto(this, contenidoActual, nombre);
                    estado.setText("Guardado como " + Audio.nombreGuardado(this, trArchivo, nombre)
                            + " en Documentos.");
                } catch (Exception e) {
                    estado.setText("No pude guardarlo. " + textoError(e));
                }
            });
        }
        boton("Transcribir otro audio", () -> {
            trReiniciar();
            empezar(this::trInicio, this::trManual);
        });
        boton("Volver al menú", this::mostrarMenu);
    }

    // ----- Pasos guiados -----

    private void trInicio() {
        nuevaPantalla("Transcribir un audio a texto", this::mostrarMenu);
        String t = "Vamos a convertir un audio en texto con Gemini. Te guiaré paso a paso: elegir el "
                + "audio, elegir el idioma, revisar el aviso y tu clave, y transcribir. El resultado "
                + "es un archivo de texto que puedes corregir.";
        texto(t);
        guia(t);
        boton("Comenzar", this::trPasoAudio);
        boton("Volver al menú", this::mostrarMenu);
    }

    private void trPasoAudio() {
        nuevaPantalla("Paso 1: el audio", this::trInicio);
        String t = "Elige el audio que quieres transcribir. Puede ser una clase, una nota de voz o "
                + "una grabación, en MP3, M4A, WAV u otros formatos.";
        texto(t);
        guia(t);
        trBloqueAudio(this::trPasoAudio);
        if (trPcm != null) boton("Siguiente: idioma y opciones", this::trPasoOpciones);
        boton("Atrás", this::trInicio);
    }

    private void trPasoOpciones() {
        nuevaPantalla("Paso 2: idioma y opciones", this::trPasoAudio);
        String t = "Elige el idioma del audio, o deja que Gemini lo detecte. Si hay varias personas "
                + "hablando, puedes pedir que se marque quién habla.";
        texto(t);
        guia(t);
        trBloqueOpciones();
        boton("Siguiente: aviso y clave", this::trPasoClave);
        boton("Atrás", this::trPasoAudio);
    }

    private void trPasoClave() {
        nuevaPantalla("Paso 3: aviso y clave", this::trPasoOpciones);
        String t = trTextoAviso();
        texto(t);
        guia(t);
        claveBloque(this::trPasoClave);
        boton("Siguiente: transcribir", this::trPasoTranscribir);
        boton("Atrás", this::trPasoOpciones);
    }

    private void trPasoTranscribir() {
        nuevaPantalla("Paso 4: transcribir y guardar", this::trPasoClave);
        String t = "Último paso. Elige el nombre del archivo de texto y empieza. Al terminar podrás "
                + "leer el texto, corregirlo, abrirlo o compartirlo.";
        texto(t);
        guia(t);
        trBloqueEjecutar(this::trPasoTranscribir);
        boton("Atrás", this::trPasoClave);
    }

    // ----- Modo manual -----

    private void trManual() {
        nuevaPantalla("Transcribir audio, modo manual", this::mostrarMenu);
        texto("En modo manual tienes todas las opciones en esta pantalla, sin pasos guiados.");
        encabezado("Audio");
        trBloqueAudio(this::trManual);
        if (trPcm == null) {
            texto("Cuando elijas el audio, aparecerán los demás ajustes.");
            boton("Volver al menú", this::mostrarMenu);
            return;
        }
        encabezado("Idioma y opciones");
        trBloqueOpciones();
        encabezado("Aviso y clave");
        texto(trTextoAviso());
        claveBloque(this::trManual);
        encabezado("Transcribir");
        trBloqueEjecutar(this::trManual);
        boton("Volver al menú", this::mostrarMenu);
    }

    // =====================================================================
    // PROYECTOS DE VIDEO
    // =====================================================================

    private void mostrarProyectos() {
        nuevaPantalla("Proyectos de video guardados", this::mostrarMenu);
        List<Proyectos.Item> lista = Proyectos.listar(this);
        if (lista.isEmpty()) {
            texto("Todavía no tienes proyectos guardados. Para guardar uno, prepara un video y usa "
                    + "el botón Guardar el proyecto, en el último paso.");
        } else {
            texto("Tienes " + lista.size() + (lista.size() == 1 ? " proyecto." : " proyectos.")
                    + " Toca uno para abrirlo o borrarlo.");
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
                    new Locale("es"));
            for (final Proyectos.Item it : lista) {
                boton(it.nombre + ". Guardado el " + df.format(new Date(it.fecha)) + ".",
                        () -> mostrarProyecto(it.nombre));
            }
        }
        boton("Volver al menú", this::mostrarMenu);
    }

    private void mostrarProyecto(final String nombre) {
        nuevaPantalla("Proyecto " + nombre, this::mostrarProyectos);
        texto("Abrir el proyecto recupera el audio, las imágenes, el orden, las duraciones, el zoom, "
                + "el sonido de transición, el texto de cada imagen, el formato y las descripciones.");
        boton("Abrir este proyecto", () -> abrirProyecto(nombre));
        boton("Borrar este proyecto", () -> confirmarBorrarProyecto(nombre));
        boton("Volver a la lista", this::mostrarProyectos);
    }

    private void confirmarBorrarProyecto(final String nombre) {
        nuevaPantalla("¿Borrar el proyecto?", () -> mostrarProyecto(nombre));
        String t = "Vas a borrar el proyecto " + nombre + ". Tus imágenes y tu audio no se borran, "
                + "solo el proyecto.";
        texto(t);
        guia(t);
        boton("Sí, borrar el proyecto", () -> {
            Proyectos.borrar(this, nombre);
            if (nombre.equals(vdProyecto)) vdProyecto = "";
            aviso("Proyecto borrado.");
            mostrarProyectos();
        });
        boton("No, volver", () -> mostrarProyecto(nombre));
    }

    private void guardarProyecto(String deseado, TextView estado) {
        String nombre = Proyectos.limpio(deseado);
        if (nombre.isEmpty()) nombre = "Mi proyecto de video";
        try {
            JSONObject j = new JSONObject();
            j.put("version", 1);
            j.put("audioUri", vdAudioPcm != null && vdAudioUri != null ? vdAudioUri.toString() : "");
            j.put("audioNombre", vdAudioNombre == null ? "" : vdAudioNombre);
            j.put("formato", vdFormato);
            j.put("encuadre", vdEncuadre);
            j.put("personal", vdPersonal);
            j.put("trans", vdTrans);
            j.put("transTipo", vdTransTipo);
            j.put("transVol", vdTransVol);
            j.put("zoomGlobal", vdZoomGlobal);
            j.put("nombreSalida", vdNombreSalida);
            JSONArray a = new JSONArray();
            for (Video.Imagen im : vdImagenes) {
                JSONObject o = new JSONObject();
                o.put("uri", im.uri.toString());
                o.put("nombre", im.nombre);
                o.put("ancho", im.ancho);
                o.put("alto", im.alto);
                o.put("nitidez", im.nitidez);
                o.put("seg", im.seg);
                o.put("zoom", im.zoom);
                o.put("descripcion", im.descripcion == null ? "" : im.descripcion);
                o.put("rotulo", im.rotulo == null ? "" : im.rotulo);
                o.put("rotuloPos", im.rotuloPos);
                o.put("rotuloTam", im.rotuloTam);
                o.put("rotuloFondo", im.rotuloFondo);
                a.put(o);
            }
            j.put("imagenes", a);
            boolean existia = Proyectos.existe(this, nombre);
            Proyectos.guardar(this, nombre, j);
            vdProyecto = nombre;
            estado.setText((existia ? "Proyecto actualizado: " : "Proyecto guardado: ") + nombre + ".");
        } catch (Exception e) {
            estado.setText("No pude guardar el proyecto. " + textoError(e));
        }
    }

    private boolean legible(Uri u) {
        try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static int entre(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void abrirProyecto(final String nombre) {
        final int[] faltan = {0};
        try {
            JSONObject j = Proyectos.cargar(this, nombre);
            List<Video.Imagen> nuevas = new ArrayList<>();
            JSONArray a = j.getJSONArray("imagenes");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Uri u = Uri.parse(o.getString("uri"));
                if (!legible(u)) {
                    faltan[0]++;
                    continue;
                }
                Video.Imagen im = new Video.Imagen();
                im.uri = u;
                im.nombre = o.optString("nombre", "imagen");
                im.ancho = o.optInt("ancho", 0);
                im.alto = o.optInt("alto", 0);
                im.nitidez = o.optDouble("nitidez", -1);
                im.seg = entre(o.optInt("seg", 0), 0, 3600);
                im.zoom = entre(o.optInt("zoom", 0), 0, 2);
                im.descripcion = o.optString("descripcion", "");
                im.rotulo = o.optString("rotulo", "");
                im.rotuloPos = entre(o.optInt("rotuloPos", 2), 0, 2);
                im.rotuloTam = entre(o.optInt("rotuloTam", 1), 0, 2);
                im.rotuloFondo = o.optBoolean("rotuloFondo", true);
                nuevas.add(im);
            }
            vdImagenes.clear();
            vdImagenes.addAll(nuevas);
            vdFormato = entre(j.optInt("formato", 0), 0, Video.TAMANOS.length - 1);
            vdEncuadre = entre(j.optInt("encuadre", 1), 0, Video.NOMBRES_ENCUADRE.length - 1);
            vdPersonal = j.optBoolean("personal", false);
            vdTrans = j.optBoolean("trans", false);
            vdTransTipo = entre(j.optInt("transTipo", 0), 0, Audio.NOMBRES_TRANSICION.length - 1);
            vdTransVol = entre(j.optInt("transVol", 40), 10, 100);
            vdZoomGlobal = entre(j.optInt("zoomGlobal", 0), 0, 3);
            vdNombreSalida = j.optString("nombreSalida", "Video con audio");
            vdProyecto = nombre;
            vdAudioPcm = null;
            final String audioTxt = j.optString("audioUri", "");
            final String audioNombre = j.optString("audioNombre", "audio");
            Uri audio = audioTxt.isEmpty() ? null : Uri.parse(audioTxt);
            if (audio != null && legible(audio)) {
                final Uri audioUri = audio;
                final File pcm = cache("video_audio.pcm");
                vdAudioUri = audioUri;
                prepararAudio(audioUri, audioNombre, pcm, marcos -> {
                    vdAudioNombre = audioNombre;
                    vdAudioPcm = pcm;
                    vdAudioMarcos = marcos;
                    proyectoAbierto(nombre, faltan[0], false);
                }, this::mostrarMenu);
            } else {
                proyectoAbierto(nombre, faltan[0], true);
            }
        } catch (Exception e) {
            aviso("No pude abrir el proyecto. " + textoError(e));
        }
    }

    private void proyectoAbierto(String nombre, int imagenesFaltan, boolean sinAudio) {
        StringBuilder m = new StringBuilder("Proyecto " + nombre + " abierto.");
        if (sinAudio) m.append(" El audio ya no está disponible: elígelo otra vez.");
        if (imagenesFaltan > 0) {
            m.append(" ").append(imagenesFaltan)
                    .append(imagenesFaltan == 1 ? " imagen ya no está disponible y se quitó."
                            : " imágenes ya no están disponibles y se quitaron.");
        }
        final String mensaje = m.toString();
        if (!asistente()) {
            vdManual();
        } else if (sinAudio) {
            vdPasoAudio();
        } else if (vdImagenes.isEmpty()) {
            vdPasoImagenes();
        } else {
            vdPasoExportar();
        }
        aviso(mensaje);
        guia(mensaje);
    }

    // =====================================================================
    // ACTUALIZACIONES
    // =====================================================================

    private String versionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "desconocida";
        }
    }

    private long versionCode() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    private void buscarActualizaciones() {
        nuevaPantalla("Buscar actualizaciones", this::mostrarMenu);
        texto("Buscando actualizaciones, un momento.");
        final int id = pantallaId;
        new Thread(() -> {
            // r[0] = error (o null), r[1] = versionCode, r[2] = versionName, r[3] = notas
            final String[] r = new String[4];
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(URL_VERSION).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                int codigo = c.getResponseCode();
                if (codigo != 200) {
                    r[0] = "No pude consultar las actualizaciones (código " + codigo + "). "
                            + "Revisa tu conexión e inténtalo de nuevo.";
                } else {
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String linea;
                    while ((linea = br.readLine()) != null) {
                        sb.append(linea).append('\n');
                    }
                    br.close();
                    JSONObject j = new JSONObject(sb.toString());
                    r[1] = String.valueOf(j.getLong("versionCode"));
                    r[2] = j.getString("versionName");
                    r[3] = j.optString("notas", "");
                }
                c.disconnect();
            } catch (Exception e) {
                r[0] = "No pude consultar las actualizaciones. "
                        + "Revisa tu conexión e inténtalo de nuevo.";
            }
            runOnUiThread(() -> {
                if (id != pantallaId) {
                    return; // la persona ya cambió de pantalla
                }
                mostrarResultadoActualizacion(r);
            });
        }).start();
    }

    private void mostrarResultadoActualizacion(String[] r) {
        nuevaPantalla("Actualizaciones", this::mostrarMenu);
        if (r[0] != null) {
            texto(r[0]);
            boton("Intentar de nuevo", this::buscarActualizaciones);
        } else if (Long.parseLong(r[1]) > versionCode()) {
            texto("Hay una versión nueva: " + r[2] + ". La tuya es la " + versionName() + ".");
            if (r[3] != null && !r[3].trim().isEmpty()) {
                texto("Novedades: " + r[3].trim());
            }
            boton("Descargar la versión " + r[2], () -> abrir(URL_APK));
        } else {
            texto("Tu aplicación está al día. Versión " + versionName() + ".");
        }
        boton("Volver al menú", this::mostrarMenu);
    }
}

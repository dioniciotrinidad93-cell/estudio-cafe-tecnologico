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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;

/**
 * Estudio Café Tecnológico, versión 0.2.
 * Interfaz hecha por código, pensada para lectores de pantalla (TalkBack, Jieshuo, Prudence):
 * solo botones y textos claros, sin iconos ni gestos de arrastrar.
 *
 * Funciones: mezclar audio con música de fondo y crear un video con audio e imágenes. Cada una
 * puede usarse con el asistente (pasos guiados y voz del teléfono) o en modo manual.
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
            if (n.endsWith(".pcm") || n.endsWith(".m4a") || n.endsWith(".wav") || n.endsWith(".mp4")) {
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
        i.setType(tipo);
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
        trabajar("Preparando el audio",
                "Estoy leyendo " + nombre + ". Mantén la aplicación abierta hasta que termine.",
                (p, c) -> {
                    pcm.delete();
                    Audio.decodificar(this, u, pcm, p, c);
                },
                () -> alListo.accept(pcm.length() / Audio.BYTES_MARCO),
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
                + "modo manual. Gratuito y de código abierto.");
        texto("Modo actual: " + (asistente() ? "con asistente" : "manual, sin asistente") + ".");
        boton("Mezclar audio con música de fondo", () -> entrar(this::mzInicio, this::mzManual));
        boton("Crear video con audio e imágenes", () -> entrar(this::vdInicio, this::vdManual));
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
            if (esVoz) mzVozPcm = null;
            else mzMusPcm = null;
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
                + " Toca una imagen para moverla, quitarla o cambiar su duración.");
        boton(n == 0 ? "Elegir imágenes" : "Agregar más imágenes", () ->
                elegirArchivo(PEDIDO_IMAGENES, "image/*", true, uris -> vdAgregar(uris, refrescar)));
        vdListaImagenes(refrescar);
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
            String av = im.aviso();
            if (!av.isEmpty()) t += " Aviso: " + av;
            boton(t, () -> vdOpcionesImagen(idx, refrescar));
        }
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
        if (vdPersonal) {
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

    private void vdBloqueExportar(final Runnable volverAqui) {
        int n = vdImagenes.size();
        texto("Se creará un video de " + textoDuracion(Math.round(vdTotalSeg())) + " con " + n
                + (n == 1 ? " imagen" : " imágenes") + ". Puede tardar varios minutos si el audio es largo.");
        final EditText nombre = campoNombre("Nombre del archivo", vdNombreSalida);
        boton("Crear el video", () -> {
            vdNombreSalida = nombre.getText().toString();
            vdExportar(volverAqui);
        });
    }

    private void vdExportar(final Runnable volverAqui) {
        final File audio = vdAudioPcm;
        final List<Video.Imagen> imagenes = new ArrayList<>(vdImagenes);
        final double total = vdTotalSeg();
        final double[] dur = vdDuraciones();
        final int formato = vdFormato;
        final int encuadre = vdEncuadre;
        final String nombre = nombreArchivo(vdNombreSalida, "Video con audio") + ".mp4";
        final File m4a = cache("video_audio.m4a");
        final File solo = cache("video_solo.mp4");
        final File fin = cache("video_final.mp4");
        final Uri[] resultado = new Uri[1];
        trabajar("Creando el video",
                "Estoy creando tu video. Puede tardar varios minutos. Mantén la aplicación abierta.",
                (p, c) -> {
                    try {
                        m4a.delete();
                        solo.delete();
                        fin.delete();
                        Audio.pcmAM4a(audio, m4a, escalar(p, 0, 8), c);
                        Video.crear(this, imagenes, dur, m4a, total, formato, encuadre, fin, solo,
                                escalar(p, 8, 98), c);
                        resultado[0] = Audio.guardar(this, fin, nombre, "video/mp4", true);
                        p.porcentaje(100);
                    } finally {
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
            empezar(this::vdInicio, this::vdManual);
        });
        boton("Volver al menú", this::mostrarMenu);
    }

    // ----- Pasos guiados -----

    private void vdInicio() {
        nuevaPantalla("Crear video con audio e imágenes", this::mostrarMenu);
        String t = "Vamos a crear un video con tu audio y una o varias imágenes. Te guiaré paso a "
                + "paso: elegir el audio, elegir las imágenes, el tamaño y el encuadre, cuánto dura "
                + "cada imagen, y exportar el video.";
        texto(t);
        guia(t);
        boton("Comenzar", this::vdPasoAudio);
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
                + "vea mal y las plataformas lo recomienden menos.";
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
        boton("Siguiente: crear el video", this::vdPasoExportar);
        boton("Atrás", this::vdPasoFormato);
    }

    private void vdPasoExportar() {
        nuevaPantalla("Paso 5: crear el video", this::vdPasoDuracion);
        String t = "Último paso. Elige el nombre del archivo y crea el video.";
        texto(t);
        guia(t);
        vdBloqueExportar(this::vdPasoExportar);
        boton("Atrás", this::vdPasoDuracion);
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
            boton("Volver al menú", this::mostrarMenu);
            return;
        }
        encabezado("Tamaño y encuadre");
        vdBloqueFormato();
        encabezado("Duración de las imágenes");
        vdBloqueDuracion(this::vdManual);
        encabezado("Crear el video");
        vdBloqueExportar(this::vdManual);
        boton("Volver al menú", this::mostrarMenu);
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

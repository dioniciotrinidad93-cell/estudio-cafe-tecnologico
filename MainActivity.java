package com.cafetecnologico.estudio;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Estudio Café Tecnológico, versión 0.1.
 * Interfaz hecha por código, pensada para lectores de pantalla (TalkBack, Jieshuo, Prudence):
 * solo botones con texto claro, sin iconos ni gestos de arrastrar.
 */
public class MainActivity extends Activity {

    // Enlaces de la comunidad. Si un enlace está vacío, la app avisa que aún no está configurado.
    private static final String URL_YOUTUBE = "";
    private static final String URL_TELEGRAM = "";
    private static final String URL_WHATSAPP = "";

    private static final String URL_REPO =
            "https://github.com/dioniciotrinidad93-cell/estudio-cafe-tecnologico";
    private static final String URL_VERSION =
            URL_REPO + "/releases/latest/download/version.json";
    private static final String URL_APK =
            URL_REPO + "/releases/latest/download/Estudio_Cafe_Tecnologico.apk";

    private ScrollView scroll;
    private LinearLayout contenido;
    private TextToSpeech tts;
    private boolean ttsListo = false;
    private SharedPreferences prefs;
    private Runnable volver = null;
    private int pantallaId = 0;

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

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsListo = true;
                tts.setLanguage(new Locale("es"));
            }
        });

        mostrarMenu();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
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

    private void texto(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setPadding(0, 0, 0, dp(10));
        contenido.addView(t);
    }

    private void boton(String s, final Runnable accion) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setOnClickListener(v -> accion.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        contenido.addView(b, lp);
    }

    private void abrir(String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "Este enlace aún no está configurado.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No encontré una aplicación para abrir este enlace.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void habla(String frase, boolean forzar) {
        if (forzar || prefs.getBoolean("voz_asistente", true)) {
            if (ttsListo) {
                tts.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "asistente");
            } else if (forzar) {
                Toast.makeText(this, "La voz del teléfono aún no está lista.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------- Pantallas ----------

    private void mostrarMenu() {
        nuevaPantalla("Estudio Café Tecnológico", null);
        texto("Edita audio y video con un asistente que te guía paso a paso. "
                + "Gratuito y de código abierto.");
        boton("Mezclar audio con música de fondo", () -> proximamente(
                "Mezclar audio con música de fondo",
                "Elegirás tu audio y una música, ajustarás los volúmenes escuchando una vista "
                        + "previa y guardarás la mezcla."));
        boton("Crear video con audio e imágenes", () -> proximamente(
                "Crear video con audio e imágenes",
                "Elegirás tu audio y varias imágenes, las ordenarás con botones, definirás cuánto "
                        + "dura cada una y exportarás un video MP4."));
        boton("Nuestros canales", this::mostrarCanales);
        boton("Buscar actualizaciones", this::buscarActualizaciones);
        boton("Voz del asistente", this::mostrarVoz);
        boton("Acerca de", this::mostrarAcerca);
    }

    private void proximamente(String titulo, String descripcion) {
        nuevaPantalla(titulo, this::mostrarMenu);
        texto("Esta función llegará en las próximas versiones.");
        texto(descripcion);
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

    private void mostrarVoz() {
        nuevaPantalla("Voz del asistente", this::mostrarMenu);
        texto("El asistente puede hablar con la voz que tengas configurada en el teléfono. "
                + "Si usas un lector de pantalla y prefieres que solo hable él, desactiva esta opción "
                + "para que no se pisen las voces.");
        CheckBox c = new CheckBox(this);
        c.setText("Que el asistente hable con la voz del teléfono");
        c.setTextSize(18);
        c.setChecked(prefs.getBoolean("voz_asistente", true));
        c.setOnCheckedChangeListener((v, marcado) ->
                prefs.edit().putBoolean("voz_asistente", marcado).apply());
        contenido.addView(c);
        boton("Probar la voz del asistente", () -> habla(
                "Hola, soy el asistente de Estudio Café Tecnológico. Te iré guiando paso a paso.",
                true));
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

    // ---------- Actualizaciones ----------

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

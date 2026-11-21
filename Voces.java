package com.cafetecnologico.estudio;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Voces del sistema para el asistente: motores de texto a voz instalados en el teléfono (Google,
 * Samsung, RHVoice, Eloquence, etc.), sus idiomas y voces, y la velocidad. Basado en el módulo
 * de voces del convertidor de Café Tecnológico.
 */
final class Voces {

    private Voces() {}

    static final int VEL_MIN = 50;
    static final int VEL_MAX = 200;
    static final int VEL_PASO = 10;

    // ---------- Motores instalados ----------

    static final class Motor {
        final String paquete;
        final String etiqueta;

        Motor(String paquete, String etiqueta) {
            this.paquete = paquete;
            this.etiqueta = etiqueta;
        }
    }

    /** Los motores de texto a voz que hay en el teléfono. Usa el permiso de consulta del manifiesto. */
    static List<Motor> motores(Context c) {
        List<Motor> r = new ArrayList<>();
        try {
            PackageManager pm = c.getPackageManager();
            List<ResolveInfo> l = pm.queryIntentServices(
                    new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0);
            for (ResolveInfo ri : l) {
                if (ri.serviceInfo == null) continue;
                String paquete = ri.serviceInfo.packageName;
                boolean repetido = false;
                for (Motor m : r) {
                    if (m.paquete.equals(paquete)) repetido = true;
                }
                if (repetido) continue;
                CharSequence et = ri.loadLabel(pm);
                r.add(new Motor(paquete, et == null ? paquete : et.toString()));
            }
        } catch (Exception ignorada) {
            // se usará el motor predeterminado
        }
        if (r.isEmpty()) {
            try {
                String p = Settings.Secure.getString(c.getContentResolver(), "tts_default_synth");
                if (p != null && !p.isEmpty()) r.add(new Motor(p, p));
            } catch (Exception ignorada) {
                // sin motor predeterminado
            }
        }
        Collections.sort(r, (a, b) -> a.etiqueta.compareToIgnoreCase(b.etiqueta));
        return r;
    }

    /** El nombre de un motor a partir de su paquete, o "el predeterminado del teléfono". */
    static String nombreMotor(Context c, String paquete) {
        if (paquete == null || paquete.isEmpty()) return "el predeterminado del teléfono";
        for (Motor m : motores(c)) {
            if (m.paquete.equals(paquete)) return m.etiqueta;
        }
        return paquete;
    }

    // ---------- Idiomas y voces de un motor ----------

    static final class Voz {
        final String nombre;
        final String etiqueta;

        Voz(String nombre, String etiqueta) {
            this.nombre = nombre;
            this.etiqueta = etiqueta;
        }
    }

    static final class Idioma {
        final String tag;
        final String etiqueta;
        final List<Voz> voces = new ArrayList<>();

        Idioma(String tag, String etiqueta) {
            this.tag = tag;
            this.etiqueta = etiqueta;
        }
    }

    interface AlCargar {
        void listo(List<Idioma> idiomas, String error);
    }

    /** Pregunta a un motor qué idiomas y voces tiene. Se llama desde el hilo principal. */
    static void cargar(Context c, String motor, final AlCargar alTerminar) {
        final Handler ui = new Handler(Looper.getMainLooper());
        final TextToSpeech[] t = new TextToSpeech[1];
        t[0] = new TextToSpeech(c.getApplicationContext(), status -> {
            final TextToSpeech tts = t[0];
            if (status != TextToSpeech.SUCCESS || tts == null) {
                if (tts != null) tts.shutdown();
                ui.post(() -> alTerminar.listo(null, "El motor no pudo iniciarse. Puede que no esté "
                        + "bien instalado o que le falten datos de voz."));
                return;
            }
            new Thread(() -> {
                List<Idioma> r = null;
                String error = null;
                try {
                    r = construir(tts);
                } catch (Throwable e) {
                    error = e.getMessage() == null ? e.toString() : e.getMessage();
                } finally {
                    try {
                        tts.shutdown();
                    } catch (Exception ignorada) {
                        // ya estaba cerrado
                    }
                }
                final List<Idioma> res = r;
                final String falla = error;
                ui.post(() -> alTerminar.listo(res, falla));
            }).start();
        }, motor == null || motor.isEmpty() ? null : motor);
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(new Locale("es")) + s.substring(1);
    }

    private static String nombreIdioma(Locale l) {
        String n = l.getDisplayName(new Locale("es"));
        if (n == null || n.isEmpty()) n = l.toLanguageTag();
        return capitalizar(n);
    }

    private static List<Idioma> construir(TextToSpeech tts) throws Exception {
        Map<String, List<Voice>> voces = new LinkedHashMap<>();
        Map<String, Locale> locales = new LinkedHashMap<>();
        Set<Voice> conjunto = null;
        try {
            conjunto = tts.getVoices();
        } catch (Throwable ignorada) {
            // algunos motores no saben listar voces
        }
        if (conjunto != null) {
            for (Voice v : conjunto) {
                Set<String> rasgos = v.getFeatures();
                if (rasgos != null && rasgos.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                    continue;
                }
                Locale l = v.getLocale();
                if (l == null) continue;
                String tag = l.toLanguageTag();
                locales.put(tag, l);
                List<Voice> lista = voces.get(tag);
                if (lista == null) {
                    lista = new ArrayList<>();
                    voces.put(tag, lista);
                }
                lista.add(v);
            }
        }
        if (locales.isEmpty()) {
            Set<Locale> ls = null;
            try {
                ls = tts.getAvailableLanguages();
            } catch (Throwable ignorada) {
                // sin lista de idiomas
            }
            if (ls != null) {
                for (Locale l : ls) {
                    if (l != null) locales.put(l.toLanguageTag(), l);
                }
            }
        }
        if (locales.isEmpty()) {
            throw new Exception("El motor no informó de ningún idioma.");
        }
        List<Idioma> r = new ArrayList<>();
        for (Map.Entry<String, Locale> e : locales.entrySet()) {
            Idioma i = new Idioma(e.getKey(), nombreIdioma(e.getValue()));
            List<Voice> lista = voces.get(e.getKey());
            if (lista != null) {
                Collections.sort(lista, (a, b) -> {
                    if (a.getQuality() != b.getQuality()) return b.getQuality() - a.getQuality();
                    return a.getName().compareTo(b.getName());
                });
                int k = 1;
                for (Voice v : lista) {
                    String et = "Voz " + (k++) + ", " + v.getName();
                    if (v.getQuality() >= Voice.QUALITY_HIGH) et += ", calidad alta";
                    if (v.isNetworkConnectionRequired()) et += ", necesita internet";
                    i.voces.add(new Voz(v.getName(), et));
                }
            }
            r.add(i);
        }
        Collections.sort(r, (a, b) -> {
            boolean ea = a.tag.startsWith("es");
            boolean eb = b.tag.startsWith("es");
            if (ea != eb) return ea ? -1 : 1;
            boolean ma = a.tag.equals("es-MX");
            boolean mb = b.tag.equals("es-MX");
            if (ma != mb) return ma ? -1 : 1;
            return a.etiqueta.compareToIgnoreCase(b.etiqueta);
        });
        return r;
    }

    // ---------- Aplicar la elección al asistente ----------

    /** Pone en el motor la voz, el idioma y la velocidad que guardó la persona. */
    static void aplicar(TextToSpeech tts, SharedPreferences p) {
        String nombre = p.getString("tts_voz", "");
        String tag = p.getString("tts_idioma", "");
        boolean puesta = false;
        if (nombre != null && !nombre.isEmpty()) {
            try {
                Set<Voice> vs = tts.getVoices();
                if (vs != null) {
                    for (Voice v : vs) {
                        if (v.getName().equals(nombre)) {
                            puesta = tts.setVoice(v) == TextToSpeech.SUCCESS;
                            break;
                        }
                    }
                }
            } catch (Throwable ignorada) {
                // se prueba solo con el idioma
            }
        }
        if (!puesta) {
            try {
                tts.setLanguage(tag == null || tag.isEmpty()
                        ? new Locale("es") : Locale.forLanguageTag(tag));
            } catch (Throwable ignorada) {
                // se queda con el idioma del motor
            }
        }
        tts.setSpeechRate(p.getInt("tts_vel", 100) / 100f);
    }
}

package com.cafetecnologico.estudio;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente mínimo de Gemini (Google) con la clave de cada persona. Sin bibliotecas externas.
 * Se usa para transcribir audios y para describir imágenes. Todo se llama desde un hilo de fondo.
 */
final class Gemini {

    private Gemini() {}

    static final String BASE = "https://generativelanguage.googleapis.com/v1beta";
    static final String URL_CLAVE = "https://aistudio.google.com/apikey";

    /** Modelos que se usan si la clave no puede listar los suyos: {Flash, Flash Lite}. */
    private static final String[] RESPALDO = {"gemini-3.8-flash", "gemini-3.8-flash-lite"};

    static final String[] IDIOMAS_DESCRIPCION = {"español", "inglés", "portugués", "francés"};
    static final String[] NOMBRES_IDIOMA_DESCRIPCION = {"Español", "Inglés", "Portugués", "Francés"};

    /** Un error de Gemini con un mensaje ya listo para la persona. */
    static final class ErrorGemini extends Exception {
        final int codigo;
        final int espera;
        final boolean diario;

        ErrorGemini(String mensaje, int codigo, int espera, boolean diario) {
            super(mensaje);
            this.codigo = codigo;
            this.espera = espera;
            this.diario = diario;
        }

        ErrorGemini(String mensaje, int codigo) {
            this(mensaje, codigo, 0, false);
        }
    }

    // ---------- Piezas de la solicitud ----------

    static JSONObject texto(String s) throws Exception {
        return new JSONObject().put("text", s);
    }

    static JSONObject datos(String mime, byte[] d) throws Exception {
        JSONObject inl = new JSONObject();
        inl.put("mimeType", mime);
        inl.put("data", Base64.encodeToString(d, Base64.NO_WRAP));
        return new JSONObject().put("inlineData", inl);
    }

    // ---------- Modelos ----------

    private static String cacheClave = null;
    private static String[] cacheModelos = null;

    static void olvidar() {
        cacheClave = null;
        cacheModelos = null;
    }

    /** Pregunta a la clave qué modelos tiene y devuelve {Flash más reciente, Flash Lite más reciente}. */
    static String[] modelos(String clave) throws Exception {
        if (clave.equals(cacheClave) && cacheModelos != null) return cacheModelos;
        Pattern p = Pattern.compile("^models/gemini-(\\d+)\\.(\\d+)-flash(-lite)?$");
        int[] mejorFlash = {-1, -1};
        int[] mejorLite = {-1, -1};
        String nombreFlash = null;
        String nombreLite = null;
        String pagina = null;
        for (int vuelta = 0; vuelta < 4; vuelta++) {
            String url = BASE + "/models?pageSize=200" + (pagina == null ? "" : "&pageToken=" + pagina);
            JSONObject j = new JSONObject(pedir("GET", url, clave, null, 30));
            JSONArray l = j.optJSONArray("models");
            if (l != null) {
                for (int i = 0; i < l.length(); i++) {
                    String n = l.getJSONObject(i).optString("name", "");
                    Matcher m = p.matcher(n);
                    if (!m.matches()) continue;
                    int a = Integer.parseInt(m.group(1));
                    int b = Integer.parseInt(m.group(2));
                    boolean lite = m.group(3) != null;
                    int[] mejor = lite ? mejorLite : mejorFlash;
                    if (a > mejor[0] || (a == mejor[0] && b > mejor[1])) {
                        mejor[0] = a;
                        mejor[1] = b;
                        if (lite) nombreLite = n.substring("models/".length());
                        else nombreFlash = n.substring("models/".length());
                    }
                }
            }
            pagina = j.optString("nextPageToken", "");
            if (pagina.isEmpty()) break;
        }
        String[] r = {nombreFlash == null ? RESPALDO[0] : nombreFlash,
            nombreLite == null ? RESPALDO[1] : nombreLite};
        cacheClave = clave;
        cacheModelos = r;
        return r;
    }

    /** Los modelos en el orden en que se probarán. Si no se pueden listar, usa los de respaldo. */
    static String[] orden(String clave, boolean liteAntes) {
        String[] m;
        try {
            m = modelos(clave);
        } catch (Exception e) {
            m = RESPALDO;
        }
        return liteAntes ? new String[] {m[1], m[0]} : new String[] {m[0], m[1]};
    }

    // ---------- Pedir texto a un modelo ----------

    /**
     * Manda las partes (texto, audio, imagen) y devuelve el texto de la respuesta. Reintenta si Google
     * pide esperar, y si un modelo falla prueba con el siguiente.
     */
    static String generar(String clave, String[] modelos, JSONArray partes, int esperaSeg,
            Audio.Cancelado can) throws Exception {
        ErrorGemini ultimo = null;
        for (String modelo : modelos) {
            boolean pensar = !modelo.startsWith("gemini-2");
            int reintentos = 0;
            while (true) {
                if (can != null && can.si()) throw new Audio.Cancelacion();
                try {
                    return llamar(clave, modelo, partes, esperaSeg, pensar);
                } catch (ErrorGemini e) {
                    ultimo = e;
                    if (e.codigo == 400 && pensar) {
                        pensar = false;
                        continue;
                    }
                    if (e.codigo == 429 && !e.diario && reintentos < 2) {
                        reintentos++;
                        esperar(Math.min(60, Math.max(5, e.espera)), can);
                        continue;
                    }
                    if (e.codigo == 401 || e.codigo == 403 || e.diario) throw e;
                    break;
                }
            }
        }
        throw ultimo != null ? ultimo : new ErrorGemini("No pude usar Gemini.", 0);
    }

    private static void esperar(int segundos, Audio.Cancelado can) throws Exception {
        for (int i = 0; i < segundos * 2; i++) {
            if (can != null && can.si()) throw new Audio.Cancelacion();
            Thread.sleep(500);
        }
    }

    private static String llamar(String clave, String modelo, JSONArray partes, int esperaSeg,
            boolean pensar) throws Exception {
        JSONObject gen = new JSONObject();
        gen.put("temperature", 0.2);
        if (pensar) gen.put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));
        JSONObject contenido = new JSONObject();
        contenido.put("role", "user");
        contenido.put("parts", partes);
        JSONObject cuerpo = new JSONObject();
        cuerpo.put("contents", new JSONArray().put(contenido));
        cuerpo.put("generationConfig", gen);
        String r = pedir("POST", BASE + "/models/" + modelo + ":generateContent", clave,
                cuerpo.toString().getBytes(StandardCharsets.UTF_8), esperaSeg);
        return extraerTexto(r);
    }

    private static String extraerTexto(String respuesta) throws Exception {
        JSONObject j = new JSONObject(respuesta);
        JSONArray cands = j.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) {
            JSONObject pf = j.optJSONObject("promptFeedback");
            if (pf != null && pf.has("blockReason")) {
                throw new ErrorGemini("Google no quiso procesar este contenido ("
                        + pf.optString("blockReason") + ").", 0);
            }
            throw new ErrorGemini("Google no devolvió ninguna respuesta.", 0);
        }
        JSONObject c = cands.getJSONObject(0);
        JSONObject cont = c.optJSONObject("content");
        StringBuilder sb = new StringBuilder();
        if (cont != null) {
            JSONArray ps = cont.optJSONArray("parts");
            if (ps != null) {
                for (int i = 0; i < ps.length(); i++) {
                    JSONObject p = ps.getJSONObject(i);
                    if (p.optBoolean("thought", false)) continue;
                    sb.append(p.optString("text", ""));
                }
            }
        }
        String t = sb.toString().trim();
        if (t.isEmpty()) {
            String motivo = c.optString("finishReason", "");
            throw new ErrorGemini("Google no devolvió texto" + (motivo.isEmpty() ? "." : " (" + motivo + ")."), 0);
        }
        return t;
    }

    // ---------- HTTP ----------

    private static String pedir(String metodo, String url, String clave, byte[] cuerpo, int esperaSeg)
            throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(metodo);
            c.setConnectTimeout(20000);
            c.setReadTimeout(esperaSeg * 1000);
            c.setRequestProperty("x-goog-api-key", clave);
            if (cuerpo != null) {
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(cuerpo.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(cuerpo);
                }
            }
            int codigo = c.getResponseCode();
            String r = leer(codigo >= 400 ? c.getErrorStream() : c.getInputStream());
            if (codigo != 200) throw errorDe(codigo, r);
            return r;
        } catch (SocketTimeoutException e) {
            throw new ErrorGemini("Google tardó demasiado en responder. Inténtalo de nuevo.", -1);
        } catch (IOException e) {
            throw new ErrorGemini("No pude conectar con Google. Revisa tu conexión a internet.", -2);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String leer(InputStream in) throws IOException {
        if (in == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] b = new char[8192];
            int n;
            while ((n = br.read(b)) > 0) sb.append(b, 0, n);
            return sb.toString();
        }
    }

    private static ErrorGemini errorDe(int codigo, String cuerpo) {
        String msg = "";
        try {
            JSONObject e = new JSONObject(cuerpo).optJSONObject("error");
            if (e != null) msg = e.optString("message", "");
        } catch (Exception ignorada) {
            // se usa el código solo
        }
        String bajo = (msg + " " + cuerpo).toLowerCase();
        if (codigo == 400 && (bajo.contains("api key not valid") || bajo.contains("api_key_invalid"))) {
            return new ErrorGemini("Google dice que tu clave no es válida. Revisa que esté completa "
                    + "y bien copiada, o crea una nueva.", 401);
        }
        if (codigo == 401 || codigo == 403) {
            return new ErrorGemini("Google no aceptó tu clave. Revisa que esté bien copiada y que "
                    + "tenga permiso para usar Gemini.", codigo);
        }
        if (codigo == 404) {
            return new ErrorGemini("Ese modelo de Gemini no está disponible con tu clave.", 404);
        }
        if (codigo == 429) {
            boolean diario = bajo.contains("perday") || bajo.contains("per day");
            int espera = 30;
            Matcher m = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)").matcher(cuerpo);
            if (m.find()) espera = Integer.parseInt(m.group(1)) + 1;
            if (diario) {
                return new ErrorGemini("Se acabó el cupo diario de tu clave de Gemini. Inténtalo "
                        + "mañana o usa otra clave.", 429, espera, true);
            }
            return new ErrorGemini("Tu clave llegó al límite de solicitudes por minuto. Espera un "
                    + "momento e inténtalo de nuevo.", 429, espera, false);
        }
        if (codigo >= 500) {
            return new ErrorGemini("Los servidores de Google están ocupados. Inténtalo en unos minutos.",
                    codigo);
        }
        String corto = msg.length() > 200 ? msg.substring(0, 200) : msg;
        return new ErrorGemini("Google rechazó la solicitud (código " + codigo + ")."
                + (corto.isEmpty() ? "" : " " + corto), codigo);
    }

    // ---------- Describir una imagen ----------

    static String describirImagen(Context c, Uri u, String clave, String[] modelos, int detalle,
            int idioma, Audio.Cancelado can) throws Exception {
        String lengua = IDIOMAS_DESCRIPCION[Math.max(0, Math.min(IDIOMAS_DESCRIPCION.length - 1, idioma))];
        String p;
        if (detalle == 1) {
            p = "Describe esta imagen en " + lengua + " con detalle, pensando en una persona ciega: "
                    + "qué muestra, las personas (apariencia, ropa, expresión, postura), los objetos, "
                    + "los colores, el ambiente y cómo están colocados los elementos. Transcribe "
                    + "cualquier texto visible.";
        } else {
            p = "Describe esta imagen en " + lengua + " en una o dos frases, pensando en una persona "
                    + "ciega que necesita saber qué foto está agregando a un video.";
        }
        p += " No identifiques a nadie por su rostro y no inventes lo que no se ve. Termina con una "
                + "frase corta sobre la calidad de la imagen: si se ve nítida, o si está borrosa, "
                + "desenfocada, oscura o pixelada. Responde en texto plano, sin Markdown ni listas.";
        byte[] jpg = Video.jpeg(c, u, 1024);
        JSONArray partes = new JSONArray();
        partes.put(texto(p));
        partes.put(datos("image/jpeg", jpg));
        return generar(clave, modelos, partes, 90, can);
    }
}

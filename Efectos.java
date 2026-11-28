package com.cafetecnologico.estudio;

import java.util.Locale;
import java.util.Random;

/**
 * Sonidos y efectos de voz de Estudio Café Tecnológico hechos con código, sin bibliotecas externas
 * y sin descargar nada. Este archivo no usa nada de Android, solo cálculos.
 *
 * Los efectos trabajan con audio estéreo a 44100 Hz, bloque por bloque y con memoria fija, así que
 * sirven para audios largos. Cada efecto tiene una intensidad de 1 a 10.
 */
final class Efectos {

    private Efectos() {}

    static final int HZ = 44100;

    static final int ARDILLA = 0;
    static final int GIGANTE = 1;
    static final int ROBOT = 2;
    static final int ECO = 3;
    static final int CUEVA = 4;
    static final int TELEFONO = 5;
    static final int DUO = 6;
    /** Desde aquí, cada efecto es un preset del ecualizador. */
    static final int EQ0 = 7;

    static final String[] NOMBRES = {
        "Ardilla: voz muy aguda",
        "Gigante: voz muy grave",
        "Robot: voz metálica",
        "Eco: la voz se repite",
        "Cueva: sonido de lugar enorme",
        "Teléfono o radio antigua",
        "Dúo: la voz suena doble",
        "Ecualizador, voz clara: más claridad y presencia",
        "Ecualizador, voz cálida: más cuerpo, estilo radio",
        "Ecualizador, más graves",
        "Ecualizador, más agudos: más brillo",
        "Ecualizador, menos graves: quita el retumbo",
        "Ecualizador, realzar los medios: se entiende mejor",
        "Ecualizador, música con fuerza: graves y agudos realzados"
    };

    static final String[] DESCRIPCIONES = {
        "Sube el tono de la voz sin cambiar la velocidad, como una ardilla o un personaje de "
                + "caricatura.",
        "Baja el tono de la voz sin cambiar la velocidad, como un gigante o un monstruo.",
        "Convierte la voz en una voz metálica de robot, con un zumbido que la hace vibrar.",
        "Repite lo que se dice, cada vez más bajito, como si hubiera rebotado en una pared lejana.",
        "Hace que suene como dentro de una cueva, una iglesia o un salón enorme: la voz se alarga "
                + "y se apaga poco a poco.",
        "Hace que la voz suene como si saliera de un teléfono antiguo o de una radio de bolsillo: "
                + "sin graves, sin agudos y un poco áspera.",
        "Duplica la voz con una segunda voz casi igual, apenas retrasada y con un poco de "
                + "diferencia de tono, como si dos personas dijeran lo mismo a la vez. Da cuerpo "
                + "a una voz sola.",
        "Preset de ecualizador. Realza la zona donde se distinguen las consonantes y quita un poco "
                + "de graves, para que la voz suene más clara y cercana. Funciona bien en voces "
                + "grabadas con el teléfono.",
        "Preset de ecualizador. Da cuerpo a la voz con más graves suaves y quita un poco de "
                + "agudos ásperos, como una voz de locutor de radio.",
        "Preset de ecualizador. Refuerza los sonidos graves, útil para música o para dar peso a una "
                + "voz delgada.",
        "Preset de ecualizador. Realza los sonidos agudos para que el audio suene más abierto y "
                + "brillante, útil cuando se oye apagado.",
        "Preset de ecualizador. Baja los graves para quitar el retumbo, el zumbido de fondo y el "
                + "ruido del viento en grabaciones hechas con el teléfono.",
        "Preset de ecualizador. Realza la zona media, que es donde está la mayor parte de la voz, "
                + "para entender mejor lo que se dice aunque haya ruido o música de fondo.",
        "Preset de ecualizador para música. Sube los graves y los agudos y baja un poco los medios, "
                + "para que la música suene potente y con brillo."
    };

    static int limitar(int intensidad) {
        return Math.max(1, Math.min(10, intensidad));
    }

    private static String grado(int i) {
        if (i <= 2) return "muy poco";
        if (i <= 4) return "poco";
        if (i <= 6) return "bastante";
        if (i <= 8) return "mucho";
        return "muchísimo";
    }

    private static String dec(double v) {
        return String.format(Locale.forLanguageTag("es"), "%.2f", v);
    }

    /** Una frase que dice cómo sonará el efecto con esa intensidad. */
    static String resumen(int efecto, int intensidad) {
        int i = limitar(intensidad);
        String base = "Intensidad " + i + " de 10: ";
        switch (efecto) {
            case ARDILLA:
                return base + "la voz sube de tono " + grado(i) + ".";
            case GIGANTE:
                return base + "la voz baja de tono " + grado(i) + ".";
            case ROBOT:
                return base + "voz metálica con un zumbido de " + frecRobot(i)
                        + " vibraciones por segundo.";
            case ECO:
                return base + "el eco se repite cada " + dec(retardoEco(i) / (double) HZ)
                        + " segundos y se apaga en unos "
                        + Math.round(colaEco(i) / (double) HZ) + " segundos.";
            case CUEVA:
                return base + (i <= 3 ? "una cueva pequeña." : (i <= 7 ? "una cueva mediana."
                        : "un lugar enorme, con mucha resonancia."));
            case DUO:
                return base + "la segunda voz se nota " + grado(i) + " y llega "
                        + retardoDuoMs(i) + " milésimas de segundo después de la original.";
            case TELEFONO:
                return base + "voz de teléfono antiguo, " + grado(i) + " marcada.";
            default:
                return base + "el volumen de algunas zonas del sonido cambia hasta "
                        + dec(maxDbEq(efecto - EQ0, i)) + " decibelios, un efecto " + grado(i)
                        + " marcado.";
        }
    }

    // ---------- Parámetros de cada efecto ----------

    private static double razonArdilla(int i) {
        return Math.pow(2, (3 + 1.2 * i) / 12.0);
    }

    private static double razonGigante(int i) {
        return Math.pow(2, -(2 + i) / 12.0);
    }

    private static int frecRobot(int i) {
        return 15 + 7 * i;
    }

    private static int retardoEco(int i) {
        return (int) Math.round(HZ * (0.12 + 0.06 * i));
    }

    private static double realimentacionEco(int i) {
        return 0.25 + 0.04 * i;
    }

    private static long colaEco(int i) {
        int r = retardoEco(i);
        double fb = realimentacionEco(i);
        long veces = (long) Math.ceil(Math.log(0.001) / Math.log(fb));
        return Math.min(8L * HZ, veces * r);
    }

    private static int retardoDuoMs(int i) {
        return 18 + 2 * i;
    }

    private static double escalaCueva(int i) {
        return 0.8 + 0.2 * i;
    }

    private static double realimentacionCueva(int i) {
        return Math.min(0.93, 0.80 + 0.012 * i);
    }

    private static long colaCueva(int i) {
        double fb = realimentacionCueva(i);
        double mayor = 1356 * escalaCueva(i);
        long veces = (long) Math.ceil(Math.log(0.001) / Math.log(fb));
        return Math.min(8L * HZ, Math.round(mayor * veces * 0.8));
    }

    // ---------- Procesador ----------

    private interface Canal {
        float proc(float x);
    }

    /**
     * Aplica un efecto a audio estéreo (muestras de 16 bits intercaladas: izquierda, derecha...).
     * Guarda su propio estado entre bloques, así que hay que usar uno nuevo para cada audio.
     */
    static final class Procesador {
        private final Canal[] c = new Canal[2];
        private final long cola;

        Procesador(int efecto, int intensidad) {
            int i = limitar(intensidad);
            long cl;
            switch (efecto) {
                case ARDILLA: {
                    double r = razonArdilla(i);
                    c[0] = new Tono(r);
                    c[1] = new Tono(r);
                    cl = 2L * Tono.VENTANA;
                    break;
                }
                case GIGANTE: {
                    double r = razonGigante(i);
                    c[0] = new Tono(r);
                    c[1] = new Tono(r);
                    cl = 2L * Tono.VENTANA;
                    break;
                }
                case ROBOT:
                    c[0] = new Robot(frecRobot(i));
                    c[1] = new Robot(frecRobot(i));
                    cl = 0;
                    break;
                case ECO:
                    c[0] = new Eco(retardoEco(i), realimentacionEco(i));
                    c[1] = new Eco(retardoEco(i), realimentacionEco(i));
                    cl = colaEco(i);
                    break;
                case CUEVA:
                    c[0] = new Reverberacion(escalaCueva(i), realimentacionCueva(i), i, 0);
                    c[1] = new Reverberacion(escalaCueva(i), realimentacionCueva(i), i, 23);
                    cl = colaCueva(i);
                    break;
                case DUO:
                    c[0] = new Duo(i, 0.0, 0);
                    c[1] = new Duo(i, Math.PI, 3);
                    cl = HZ / 8;
                    break;
                case TELEFONO:
                    c[0] = new Telefono(i);
                    c[1] = new Telefono(i);
                    cl = HZ / 5;
                    break;
                default:
                    c[0] = new Ecualizador(efecto - EQ0, i);
                    c[1] = new Ecualizador(efecto - EQ0, i);
                    cl = HZ / 10;
                    break;
            }
            cola = cl;
        }

        /** Cuántos marcos de silencio hay que seguir procesando al final para no cortar la cola. */
        long cola() {
            return cola;
        }

        /** Procesa n marcos. La salida queda en unidades de 16 bits, sin recortar. */
        void procesar(short[] entrada, int n, float[] salida) {
            for (int k = 0; k < n; k++) {
                salida[2 * k] = c[0].proc(entrada[2 * k]);
                salida[2 * k + 1] = c[1].proc(entrada[2 * k + 1]);
            }
        }
    }

    // ---------- Cambio de tono sin cambiar la velocidad ----------

    /**
     * Cambia el tono con dos lecturas de una línea de retardo que se mueven a otra velocidad y se
     * mezclan una con la otra. La duración no cambia.
     */
    private static final class Tono implements Canal {
        static final int VENTANA = 2048;
        private static final int N = 4096;
        private static final int MASCARA = N - 1;
        private final float[] buf = new float[N];
        private int w = 0;
        private double fase = 0;
        private final double paso;

        Tono(double razon) {
            paso = (1 - razon) / VENTANA;
        }

        private float leer(double retardo) {
            double pos = w - retardo;
            int i0 = (int) Math.floor(pos);
            double fr = pos - i0;
            float a = buf[i0 & MASCARA];
            float b = buf[(i0 + 1) & MASCARA];
            return (float) (a + (b - a) * fr);
        }

        @Override
        public float proc(float x) {
            buf[w & MASCARA] = x;
            double pa = fase;
            double pb = pa + 0.5;
            if (pb >= 1) pb -= 1;
            float ya = leer(pa * VENTANA);
            float yb = leer(pb * VENTANA);
            double ga = pa < 0.5 ? 2 * pa : 2 - 2 * pa;
            float y = (float) ((ya * ga + yb * (1 - ga)) * 1.25);
            fase += paso;
            if (fase >= 1) fase -= 1;
            else if (fase < 0) fase += 1;
            w++;
            return y;
        }
    }

    // ---------- Robot ----------

    private static final class Robot implements Canal {
        private double s = 0;
        private double co = 1;
        private final double ds;
        private final double dc;
        private int cuenta = 0;

        Robot(double hz) {
            double w = 2 * Math.PI * hz / HZ;
            ds = Math.sin(w);
            dc = Math.cos(w);
        }

        @Override
        public float proc(float x) {
            double ns = s * dc + co * ds;
            double nc = co * dc - s * ds;
            s = ns;
            co = nc;
            if (++cuenta >= 4096) {
                cuenta = 0;
                double m = Math.sqrt(s * s + co * co);
                s /= m;
                co /= m;
            }
            return (float) (0.4 * x + 1.1 * x * s);
        }
    }

    // ---------- Eco ----------

    private static final class Eco implements Canal {
        private final float[] buf;
        private int idx = 0;
        private final float fb;

        Eco(int retardo, double fb) {
            buf = new float[Math.max(1, retardo)];
            this.fb = (float) fb;
        }

        @Override
        public float proc(float x) {
            float d = buf[idx];
            buf[idx] = x + d * fb;
            if (++idx >= buf.length) idx = 0;
            return 0.85f * x + 0.55f * d;
        }
    }

    // ---------- Cueva: reverberación ----------

    private static final class Reverberacion implements Canal {
        private static final int[] PEINES = {1116, 1188, 1277, 1356};
        private static final int[] PASATODO = {556, 441};
        private final float[][] peine = new float[4][];
        private final int[] pi = new int[4];
        private final float[] filtro = new float[4];
        private final float[][] pasa = new float[2][];
        private final int[] ai = new int[2];
        private final float fb;
        private final float amortigua = 0.25f;
        private final float humedo;
        private final float seco;

        Reverberacion(double escala, double fb, int intensidad, int desfase) {
            for (int j = 0; j < 4; j++) {
                peine[j] = new float[(int) Math.round((PEINES[j] + desfase) * escala)];
            }
            for (int j = 0; j < 2; j++) {
                pasa[j] = new float[PASATODO[j] + desfase];
            }
            this.fb = (float) fb;
            this.humedo = (float) (0.9 + 0.12 * intensidad);
            this.seco = (float) (0.9 - 0.03 * intensidad);
        }

        @Override
        public float proc(float x) {
            float entrada = x * 0.08f;
            float suma = 0;
            for (int j = 0; j < 4; j++) {
                float o = peine[j][pi[j]];
                filtro[j] = o * (1 - amortigua) + filtro[j] * amortigua;
                peine[j][pi[j]] = entrada + filtro[j] * fb;
                if (++pi[j] >= peine[j].length) pi[j] = 0;
                suma += o;
            }
            for (int j = 0; j < 2; j++) {
                float bo = pasa[j][ai[j]];
                float o = -suma + bo;
                pasa[j][ai[j]] = suma + bo * 0.5f;
                if (++ai[j] >= pasa[j].length) ai[j] = 0;
                suma = o;
            }
            return seco * x + humedo * suma;
        }
    }

    // ---------- Dúo: la voz suena doble ----------

    /**
     * Suma a la voz una segunda voz casi igual: una copia retrasada unos 20 a 40 milésimas de segundo
     * cuyo retraso oscila despacio, lo que le da una pequeña diferencia de tono. Cada canal usa una
     * fase distinta de la oscilación y un retraso un poco distinto, para que la segunda voz se abra
     * en estéreo.
     */
    private static final class Duo implements Canal {
        private static final int N = 4096;
        private static final int MASCARA = N - 1;
        private final float[] buf = new float[N];
        private int w = 0;
        private double fase;
        private final double dFase = 2 * Math.PI * 0.6 / HZ;
        private final double base;
        private final double profundidad;
        private final float seco = 0.8f;
        private final float humedo;

        Duo(int i, double fase0, int extraMs) {
            fase = fase0;
            base = HZ * (retardoDuoMs(i) + extraMs) / 1000.0;
            profundidad = HZ * (0.5 + 0.35 * i) / 1000.0;
            humedo = (float) (0.45 + 0.04 * i);
        }

        @Override
        public float proc(float x) {
            buf[w & MASCARA] = x;
            double retardo = base + profundidad * Math.sin(fase);
            fase += dFase;
            if (fase > 2 * Math.PI) fase -= 2 * Math.PI;
            double pos = w - retardo;
            int i0 = (int) Math.floor(pos);
            double fr = pos - i0;
            float a = buf[i0 & MASCARA];
            float b = buf[(i0 + 1) & MASCARA];
            float d = (float) (a + (b - a) * fr);
            w++;
            return seco * x + humedo * d;
        }
    }

    // ---------- Teléfono o radio antigua ----------

    private static final class Filtro {
        private double b0;
        private double b1;
        private double b2;
        private double a1;
        private double a2;
        private double z1 = 0;
        private double z2 = 0;

        static Filtro pasaAltos(double f) {
            return crear(f, true);
        }

        static Filtro pasaBajos(double f) {
            return crear(f, false);
        }

        private static Filtro crear(double f, boolean altos) {
            double w = 2 * Math.PI * f / HZ;
            double cs = Math.cos(w);
            double alfa = Math.sin(w) / (2 * 0.7071);
            double a0 = 1 + alfa;
            Filtro r = new Filtro();
            if (altos) {
                r.b0 = (1 + cs) / 2 / a0;
                r.b1 = -(1 + cs) / a0;
                r.b2 = (1 + cs) / 2 / a0;
            } else {
                r.b0 = (1 - cs) / 2 / a0;
                r.b1 = (1 - cs) / a0;
                r.b2 = (1 - cs) / 2 / a0;
            }
            r.a1 = -2 * cs / a0;
            r.a2 = (1 - alfa) / a0;
            return r;
        }

        double proc(double x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }
    }

    private static final class Telefono implements Canal {
        private final Filtro alto1;
        private final Filtro alto2;
        private final Filtro bajo1;
        private final Filtro bajo2;
        private final double empuje;
        private final double norma;

        Telefono(int i) {
            double fa = 250 + 50 * i;
            double fb = 4000 - 150 * i;
            alto1 = Filtro.pasaAltos(fa);
            alto2 = Filtro.pasaAltos(fa);
            bajo1 = Filtro.pasaBajos(fb);
            bajo2 = Filtro.pasaBajos(fb);
            empuje = 1 + 0.25 * i;
            norma = Math.tanh(empuje);
        }

        @Override
        public float proc(float x) {
            double y = bajo2.proc(bajo1.proc(alto2.proc(alto1.proc(x))));
            double n = y / 32768.0;
            double s = Math.tanh(empuje * n) / norma;
            return (float) (s * 32768.0);
        }
    }

    // ---------- Ecualizador con presets ----------

    private static final int PICO = 0;
    private static final int GRAVES = 1;
    private static final int AGUDOS = 2;

    /**
     * Cada preset es una lista de bandas: tipo, frecuencia en hertz, cambio en decibelios con la
     * intensidad 5, y ancho (Q). Una intensidad menor aplica menos cambio y una mayor aplica más.
     */
    private static final double[][][] PRESETS = {
        // Voz clara
        {{PICO, 3000, 4.0, 1.0}, {AGUDOS, 8000, 3.0, 0.7}, {GRAVES, 110, -4.0, 0.7}},
        // Voz cálida
        {{GRAVES, 150, 5.0, 0.7}, {PICO, 2500, 2.0, 0.9}, {AGUDOS, 9000, -2.5, 0.7}},
        // Más graves
        {{GRAVES, 100, 7.0, 0.7}, {PICO, 60, 2.0, 1.0}},
        // Más agudos
        {{AGUDOS, 5000, 6.0, 0.7}, {PICO, 11000, 2.0, 1.0}},
        // Menos graves
        {{GRAVES, 160, -8.0, 0.7}, {PICO, 90, -3.0, 1.0}},
        // Realzar los medios
        {{PICO, 1500, 5.0, 0.8}, {PICO, 3000, 3.0, 1.0}, {GRAVES, 90, -2.0, 0.7}},
        // Música con fuerza
        {{GRAVES, 100, 5.0, 0.7}, {AGUDOS, 7000, 4.0, 0.7}, {PICO, 800, -2.5, 0.8}}
    };

    /** El cambio más grande, en decibelios, que hace un preset con esa intensidad. */
    private static double maxDbEq(int preset, int i) {
        double m = 0;
        double[][] bandas = PRESETS[Math.max(0, Math.min(PRESETS.length - 1, preset))];
        for (double[] b : bandas) m = Math.max(m, Math.abs(b[2]));
        return m * i / 5.0;
    }

    /** Un filtro de una banda del ecualizador (fórmulas de Robert Bristow-Johnson). */
    private static final class Banda {
        private final double b0;
        private final double b1;
        private final double b2;
        private final double a1;
        private final double a2;
        private double z1 = 0;
        private double z2 = 0;

        Banda(int tipo, double hz, double db, double q) {
            double aa = Math.pow(10, db / 40.0);
            double w = 2 * Math.PI * Math.min(hz, HZ * 0.45) / HZ;
            double cs = Math.cos(w);
            double sn = Math.sin(w);
            double n0;
            double n1;
            double n2;
            double d0;
            double d1;
            double d2;
            if (tipo == PICO) {
                double alfa = sn / (2 * q);
                n0 = 1 + alfa * aa;
                n1 = -2 * cs;
                n2 = 1 - alfa * aa;
                d0 = 1 + alfa / aa;
                d1 = -2 * cs;
                d2 = 1 - alfa / aa;
            } else {
                double alfa = sn / 2 * Math.sqrt(2);
                double raiz = 2 * Math.sqrt(aa) * alfa;
                if (tipo == GRAVES) {
                    n0 = aa * ((aa + 1) - (aa - 1) * cs + raiz);
                    n1 = 2 * aa * ((aa - 1) - (aa + 1) * cs);
                    n2 = aa * ((aa + 1) - (aa - 1) * cs - raiz);
                    d0 = (aa + 1) + (aa - 1) * cs + raiz;
                    d1 = -2 * ((aa - 1) + (aa + 1) * cs);
                    d2 = (aa + 1) + (aa - 1) * cs - raiz;
                } else {
                    n0 = aa * ((aa + 1) + (aa - 1) * cs + raiz);
                    n1 = -2 * aa * ((aa - 1) + (aa + 1) * cs);
                    n2 = aa * ((aa + 1) + (aa - 1) * cs - raiz);
                    d0 = (aa + 1) - (aa - 1) * cs + raiz;
                    d1 = 2 * ((aa - 1) - (aa + 1) * cs);
                    d2 = (aa + 1) - (aa - 1) * cs - raiz;
                }
            }
            b0 = n0 / d0;
            b1 = n1 / d0;
            b2 = n2 / d0;
            a1 = d1 / d0;
            a2 = d2 / d0;
        }

        double proc(double x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }
    }

    private static final class Ecualizador implements Canal {
        private final Banda[] bandas;

        Ecualizador(int preset, int intensidad) {
            double[][] def = PRESETS[Math.max(0, Math.min(PRESETS.length - 1, preset))];
            bandas = new Banda[def.length];
            for (int k = 0; k < def.length; k++) {
                bandas[k] = new Banda((int) def[k][0], def[k][1], def[k][2] * intensidad / 5.0,
                        def[k][3]);
            }
        }

        @Override
        public float proc(float x) {
            double y = x;
            for (Banda b : bandas) y = b.proc(y);
            return (float) y;
        }
    }

    // ---------- Sonido de flash de cámara ----------

    /**
     * Un flash de cámara fotográfica hecho con código: chasquido del obturador, golpe grave, soplido
     * del destello, segundo chasquido y el zumbido agudo de la recarga. Estéreo, a 44100 Hz.
     * Dura seis décimas de segundo y el chasquido está justo al inicio, para marcar el momento exacto.
     */
    static short[] flash() {
        final int n = (int) (0.60 * HZ);
        short[] r = new short[n * 2];
        Random rnd = new Random(20261125L);
        double previo = 0;
        double fase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) HZ;
            double b = rnd.nextDouble() * 2 - 1;
            double agudo = (b - previo) * 0.5;
            previo = b;
            double v = agudo * Math.exp(-t * 700) * 0.9;
            v += Math.sin(2 * Math.PI * 150 * t) * Math.exp(-t * 55) * 0.5;
            v += agudo * Math.exp(-t * 20) * 0.45;
            double t2 = t - 0.07;
            if (t2 >= 0) v += agudo * Math.exp(-t2 * 900) * 0.55;
            double u = t - 0.18;
            if (u >= 0 && u < 0.34) {
                double f = 3200 + 4200 * (u / 0.34);
                fase += 2 * Math.PI * f / HZ;
                v += Math.sin(fase) * Math.sin(Math.PI * u / 0.34) * 0.05;
            }
            if (t < 0.0005) v *= t / 0.0005;
            int resto = n - i;
            if (resto < 441) v *= resto / 441.0;
            v = Math.max(-1, Math.min(1, v));
            short s = (short) Math.round(v * 0.8 * 32767);
            r[2 * i] = s;
            r[2 * i + 1] = s;
        }
        return r;
    }
}

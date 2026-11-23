package dev.chargescope.monitor;

import java.util.Locale;

final class PdProfile {
    final int index;
    final String kind;
    final int minMv;
    final int maxMv;
    final int maxMa;
    final int maxMw;
    final boolean powerLimited;

    private PdProfile(int index, String kind, int minMv, int maxMv, int maxMa,
                      int maxMw, boolean powerLimited) {
        this.index = index;
        this.kind = kind;
        this.minMv = minMv;
        this.maxMv = maxMv;
        this.maxMa = maxMa;
        this.maxMw = maxMw;
        this.powerLimited = powerLimited;
    }

    static PdProfile decode(int index, String raw) {
        long pdo = hex(raw);
        if (pdo <= 0) return null;

        int type = (int) (pdo >>> 30);
        int current = (int) (pdo & 0x3ff) * 10;
        int maxVoltage = (int) ((pdo >>> 20) & 0x3ff) * 50;
        int minVoltage = (int) ((pdo >>> 10) & 0x3ff) * 50;

        switch (type) {
            case 0:
                return new PdProfile(index, "Fixed", minVoltage, minVoltage, current, 0, false);
            case 1:
                return new PdProfile(index, "Battery supply", minVoltage, maxVoltage, 0,
                    (int) (pdo & 0x3ff) * 250, false);
            case 2:
                return new PdProfile(index, "Variable", minVoltage, maxVoltage, current, 0, false);
            case 3:
                if (((pdo >>> 28) & 3) == 0) {
                    return new PdProfile(index, "PPS", (int) ((pdo >>> 8) & 0xff) * 100,
                        (int) ((pdo >>> 17) & 0xff) * 100, (int) (pdo & 0x7f) * 50, 0,
                        (pdo & (1L << 27)) != 0);
                }
                return new PdProfile(index, "Extended profile", 0, 0, 0, 0, false);
            default:
                return null;
        }
    }

    String voltageLabel() {
        if (maxMv == 0) return "—";
        if (minMv == maxMv) return number(minMv / 1000.0) + " V";
        return number(minMv / 1000.0) + "–" + number(maxMv / 1000.0) + " V";
    }

    String limitLabel() {
        if (maxMa > 0) return number(maxMa / 1000.0) + " A";
        if (maxMw > 0) return number(maxMw / 1000.0) + " W";
        return "—";
    }

    int fixedPowerMw() {
        return "Fixed".equals(kind) ? maxMv * maxMa / 1000 : 0;
    }

    static int requestedIndex(String request) {
        return (int) ((hex(request) >>> 28) & 7);
    }

    double requestedVoltage(String request) {
        long value = hex(request);
        if (value <= 0) return Double.NaN;
        if ("PPS".equals(kind)) return ((value >>> 9) & 0x7ff) * 0.020;
        if ("Fixed".equals(kind)) return maxMv / 1000.0;
        return Double.NaN;
    }

    double requestedCurrent(String request) {
        long value = hex(request);
        if (value <= 0) return Double.NaN;
        if ("PPS".equals(kind)) return (value & 0x7f) * 0.050;
        if ("Fixed".equals(kind) || "Variable".equals(kind)) return ((value >>> 10) & 0x3ff) * 0.010;
        return Double.NaN;
    }

    private static long hex(String raw) {
        if (raw == null) return 0;
        String value = raw.trim();
        if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
        if (value.length() > 8) return 0;
        try {
            return Long.parseLong(value, 16);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static String number(double value) {
        return String.format(Locale.US, "%.2f", value).replaceAll("\\.?0+$", "");
    }
}

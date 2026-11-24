package dev.chargescope.monitor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class RootSampler {
    static final String PREFERENCES = "telemetry";
    static final String ROOT_PREF = "read_root_telemetry";

    interface Listener {
        void onSample(Map<String, String> values);
        void onUnavailable(boolean receivedSamples);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;
    private Process process;

    static String loadChargingScript(Context context, String name) throws IOException {
        String script = loadScript(context, "charging_interfaces.sh") + "\n"
            + loadScript(context, "qualcomm_telemetry.sh");
        for (String asset : context.getAssets().list("")) {
            if ("legacy_telemetry.sh".equals(asset)) {
                script += "\n" + loadScript(context, asset);
                break;
            }
        }
        return script + "\n" + loadScript(context, name);
    }

    private static String loadScript(Context context, String name) throws IOException {
        try (InputStream input = context.getAssets().open(name);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) bytes.write(buffer, 0, length);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    synchronized void start(String script, Listener listener) {
        start(script, true, listener);
    }

    synchronized void start(String script, boolean useRoot, Listener listener) {
        stop();
        int run = generation;
        new Thread(() -> collect(run, script, useRoot, listener), "charging-telemetry").start();
    }

    synchronized void stop() {
        generation++;
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    private synchronized boolean current(int run) {
        return generation == run;
    }

    private void collect(int run, String script, boolean useRoot, Listener listener) {
        Process child = null;
        boolean ready = !useRoot;
        boolean receivedSamples = false;
        try {
            child = new ProcessBuilder(useRoot ? "su" : "/system/bin/sh", "-c", script)
                .redirectErrorStream(true).start();
            synchronized (this) {
                if (!current(run)) return;
                process = child;
            }
            try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                Map<String, String> fields = new HashMap<>();
                String line;
                while (current(run) && (line = reader.readLine()) != null) {
                    if ("@ROOT".equals(line)) {
                        ready = true;
                    } else if (ready && "@SAMPLE".equals(line)) {
                        Map<String, String> sample = fields;
                        fields = new HashMap<>();
                        receivedSamples = true;
                        main.post(() -> {
                            if (current(run)) listener.onSample(sample);
                        });
                    } else if (ready && line.contains("=")) {
                        int split = line.indexOf('=');
                        fields.put(line.substring(0, split), line.substring(split + 1));
                    }
                }
            }
        } catch (IOException ignored) {
            // Android battery telemetry stays available if the root shell cannot start.
        } finally {
            if (child != null) child.destroy();
            synchronized (this) {
                if (current(run)) process = null;
            }
            boolean sampled = receivedSamples;
            main.post(() -> {
                if (current(run)) listener.onUnavailable(sampled);
            });
        }
    }
}

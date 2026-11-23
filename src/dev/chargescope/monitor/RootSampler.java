package dev.chargescope.monitor;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class RootSampler {
    interface Listener {
        void onSample(Map<String, String> values);
        void onUnavailable(boolean receivedSamples);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;
    private Process process;

    synchronized void start(String script, Listener listener) {
        stop();
        int run = generation;
        new Thread(() -> collect(run, script, listener), "charging-telemetry").start();
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

    private void collect(int run, String script, Listener listener) {
        Process child = null;
        boolean root = false;
        boolean receivedSamples = false;
        try {
            child = new ProcessBuilder("su", "-c", script).redirectErrorStream(true).start();
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
                        root = true;
                    } else if (root && "@SAMPLE".equals(line)) {
                        Map<String, String> sample = fields;
                        fields = new HashMap<>();
                        receivedSamples = true;
                        main.post(() -> {
                            if (current(run)) listener.onSample(sample);
                        });
                    } else if (root && line.contains("=")) {
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

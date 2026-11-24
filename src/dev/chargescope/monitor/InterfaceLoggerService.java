package dev.chargescope.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public final class InterfaceLoggerService extends Service {
    private static final String CHANNEL = "interface-logging";
    private static final String STOP = "dev.chargescope.monitor.STOP_INTERFACE_LOGGING";
    private static final int NOTIFICATION_ID = 1;
    private static boolean running;

    private SharedPreferences prefs;
    private String script;
    private String previousSnapshot;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final RootSampler sampler = new RootSampler();
    private final Runnable rescan = () -> startProbe(prefs.getBoolean(RootSampler.ROOT_PREF, false));
    private final SharedPreferences.OnSharedPreferenceChangeListener rootChanged = (store, key) -> {
        if (RootSampler.ROOT_PREF.equals(key)) scheduleProbe(0);
    };
    private final BroadcastReceiver powerChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            scheduleProbe(500);
        }
    };

    static boolean isRunning() {
        return running;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;

        startForeground(NOTIFICATION_ID, buildNotification());
        try {
            script = RootSampler.loadChargingScript(this, "interface_probe.sh");
        } catch (IOException error) {
            append("Logging could not start: probe asset unavailable.");
            stopSelf();
            return START_NOT_STICKY;
        }

        prefs = getSharedPreferences(RootSampler.PREFERENCES, MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(rootChanged);
        IntentFilter powerEvents = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        powerEvents.addAction(Intent.ACTION_POWER_DISCONNECTED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(powerChanges, powerEvents, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(powerChanges, powerEvents);
        }

        running = true;
        append("Logging started on " + Build.MANUFACTURER + " " + Build.MODEL
            + " · Android " + Build.VERSION.RELEASE + " · kernel "
            + System.getProperty("os.version", "unknown"));
        scheduleProbe(0);
        return START_NOT_STICKY;
    }

    private void scheduleProbe(long delay) {
        main.removeCallbacks(rescan);
        if (running) main.postDelayed(rescan, delay);
    }

    private void startProbe(boolean useRoot) {
        sampler.start(script, useRoot, new RootSampler.Listener() {
            @Override public void onSample(Map<String, String> fields) {
                String snapshot = snapshot(fields, useRoot);
                if (!snapshot.equals(previousSnapshot) && append(snapshot)) previousSnapshot = snapshot;
            }

            @Override public void onUnavailable(boolean receivedSamples) {
                if (useRoot) {
                    append("Root probe unavailable. Scanning interfaces readable by Android.");
                    startProbe(false);
                } else {
                    scheduleProbe(30_000);
                }
            }
        });
    }

    private String snapshot(Map<String, String> fields, boolean useRoot) {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        StringBuilder text = new StringBuilder(useRoot ? "Access: root" : "Access: Android shell")
            .append('\n').append("Android power source: ").append(ChargingConnection.androidSource(plugged));
        boolean found = false;
        for (Map.Entry<String, String> field : new TreeMap<>(fields).entrySet()) {
            String key = field.getKey();
            if (key.startsWith("pd.") || key.startsWith("typec.") ||
                key.startsWith("controller.") || key.startsWith("supply.") || key.startsWith("selected.")) {
                text.append('\n').append(field.getValue());
                found = true;
            }
        }
        if (!found) text.append("\nNo USB or charging interface was readable.");
        return text.toString();
    }

    private boolean append(String text) {
        try {
            InterfaceLog.append(getFilesDir(), text);
            return true;
        } catch (IOException error) {
            Log.w("ChargeScope", "Could not write interface log", error);
            return false;
        }
    }

    private Notification buildNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "USB interface logging",
            NotificationManager.IMPORTANCE_LOW));

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openApp = PendingIntent.getActivity(this, 0, open, flags);
        Intent stop = new Intent(this, InterfaceLoggerService.class).setAction(STOP);
        PendingIntent stopLogging = PendingIntent.getService(this, 0, stop, flags);

        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_logging)
            .setContentTitle(getApplicationInfo().loadLabel(getPackageManager()) + " interface logger")
            .setContentText("Recording USB interface changes on this device")
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Stop logging", stopLogging).build())
            .build();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onDestroy() {
        if (running) {
            running = false;
            main.removeCallbacks(rescan);
            sampler.stop();
            prefs.unregisterOnSharedPreferenceChangeListener(rootChanged);
            unregisterReceiver(powerChanges);
            append("Logging stopped.");
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}

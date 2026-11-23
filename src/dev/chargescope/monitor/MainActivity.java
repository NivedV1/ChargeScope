package dev.chargescope.monitor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String EMPTY = "—";
    private static final String THEME_PREF = "appearance";
    private static final String NOTIFICATION_PREF = "logger_notification_requested";
    private static final int LOGGER_NOTIFICATION_PERMISSION = 1;
    private static final String[] THEMES = {"System default", "Light", "Dark"};
    private static final int SYSTEM = 0;
    private static final int DARK = 2;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final RootSampler sampler = new RootSampler();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            main.postDelayed(this, 2000);
        }
    };

    private SharedPreferences prefs;
    private ThemePalette palette;
    private int themeChoice;
    private BatteryManager battery;
    private String script;
    private Map<String, String> sample = Collections.emptyMap();
    private boolean root;
    private boolean requesting;
    private String rootMessage;

    private TextView access;
    private TextView mode;
    private TextView connection;
    private TextView watts;
    private TextView charge;
    private TextView request;
    private TextView temperature;
    private TextView profilesHint;
    private TextView logStatus;
    private TextView logToggle;
    private LinearLayout profilesView;
    private String displayedProfiles = "";
    private SensorRow pumpRow;
    private SensorRow usbRow;
    private SensorRow batteryRow;
    private TextView rails;
    private PowerTrend trend;

    @Override public void onCreate(Bundle state) {
        prefs = getSharedPreferences(RootSampler.PREFERENCES, MODE_PRIVATE);
        themeChoice = prefs.getInt(THEME_PREF, SYSTEM);
        boolean systemDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        boolean dark = themeChoice == DARK || (themeChoice == SYSTEM && systemDark);
        setTheme(dark ? R.style.ChargeScopeDark : R.style.ChargeScopeLight);
        super.onCreate(state);
        palette = ThemePalette.forMode(this, dark);
        getWindow().setBackgroundDrawable(new ColorDrawable(palette.background));
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        battery = getSystemService(BatteryManager.class);
        try {
            script = RootSampler.loadScript(this, "telemetry.sh");
        } catch (IOException ignored) {
            rootMessage = "The telemetry script could not be opened.";
        }
        buildView();
        if (state != null) trend.restore(state);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        trend.save(state);
    }

    @Override protected void onStart() {
        super.onStart();
        if (prefs.getBoolean(RootSampler.ROOT_PREF, false)) requestRoot();
        tick.run();
    }

    @Override protected void onStop() {
        main.removeCallbacks(tick);
        sampler.stop();
        sample = Collections.emptyMap();
        root = false;
        requesting = false;
        super.onStop();
    }

    private void requestRoot() {
        if (script == null || requesting || root) return;
        requesting = true;
        rootMessage = null;
        renderAccess();
        sampler.start(script, new RootSampler.Listener() {
            @Override public void onSample(Map<String, String> values) {
                sample = values;
                root = true;
                if (requesting) prefs.edit().putBoolean(RootSampler.ROOT_PREF, true).apply();
                requesting = false;
                rootMessage = null;
                render();
            }

            @Override public void onUnavailable(boolean receivedSamples) {
                root = false;
                requesting = false;
                sample = Collections.emptyMap();
                prefs.edit().putBoolean(RootSampler.ROOT_PREF, false).apply();
                rootMessage = receivedSamples ? "Root connection ended. Tap to reconnect."
                    : "Grant ChargeScope superuser access in KernelSU, then tap to retry.";
                render();
            }
        });
    }

    private void stopRoot() {
        sampler.stop();
        prefs.edit().putBoolean(RootSampler.ROOT_PREF, false).apply();
        sample = Collections.emptyMap();
        root = false;
        requesting = false;
        rootMessage = null;
        render();
    }

    private void buildView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.dashboard_scroll);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(palette.background);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = column();
        content.setPadding(dp(20), dp(14), dp(20), dp(28));
        scroll.addView(content);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView symbol = label("ϟ", 24, palette.onPrimaryContainer, true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(background(palette.primaryContainer, 14, Color.TRANSPARENT));
        header.addView(symbol, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = label("ChargeScope", 23, palette.onSurface, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(11);
        header.addView(title, titleParams);
        access = label("Enable root", 12, palette.primary, true);
        access.setPadding(dp(11), 0, dp(11), 0);
        access.setMinimumHeight(dp(48));
        access.setGravity(Gravity.CENTER);
        access.setBackground(controlBackground(24));
        access.setOnClickListener(view -> {
            if (root || requesting) stopRoot();
            else requestRoot();
        });
        header.addView(access, new LinearLayout.LayoutParams(-2, -2));
        ImageButton appearance = new ImageButton(this);
        appearance.setImageResource(R.drawable.ic_theme);
        appearance.setImageTintList(ColorStateList.valueOf(palette.onSurfaceVariant));
        appearance.setBackground(controlBackground(24));
        appearance.setContentDescription("Theme, " + THEMES[themeChoice]);
        appearance.setOnClickListener(view -> showAppearance());
        LinearLayout.LayoutParams appearanceParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        appearanceParams.leftMargin = dp(7);
        header.addView(appearance, appearanceParams);
        content.addView(header);

        connection = label("", 12, palette.onSurfaceVariant, false);
        connection.setLineSpacing(dp(3), 1);
        add(content, connection, 9);

        LinearLayout hero = card(palette.primaryContainer);
        mode = label("Battery telemetry", 13, palette.onPrimaryContainer, true);
        hero.addView(mode);
        watts = label(EMPTY, 49, palette.onPrimaryContainer, true);
        watts.setFontFeatureSettings("tnum");
        add(hero, watts, 3);
        charge = label("", 13, palette.onPrimaryContainer, false);
        add(hero, charge, 0);
        trend = new PowerTrend();
        LinearLayout.LayoutParams chart = new LinearLayout.LayoutParams(-1, dp(43));
        chart.topMargin = dp(13);
        hero.addView(trend, chart);
        TextView chartLabel = label("Battery power · last minute", 11, palette.onPrimaryContainer, false);
        add(hero, chartLabel, 4);
        add(content, hero, 14);

        LinearLayout contract = card(palette.surface);
        contract.addView(label("Connection", 17, palette.onSurface, true));
        request = label("", 14, palette.onSurface, false);
        add(contract, request, 7);
        temperature = label("", 12, palette.onSurfaceVariant, false);
        add(contract, temperature, 5);
        add(content, contract, 12);

        LinearLayout measurements = card(palette.surface);
        measurements.addView(label("Live measurements", 17, palette.onSurface, true));
        LinearLayout legend = row();
        legend.addView(weighted(label("MEASUREMENT", 10, palette.onSurfaceVariant, true), Gravity.START),
            new LinearLayout.LayoutParams(0, -2, 1.6f));
        for (String heading : new String[]{"VOLTS", "AMPS", "WATTS"}) {
            legend.addView(weighted(label(heading, 10, palette.onSurfaceVariant, true), Gravity.END),
                new LinearLayout.LayoutParams(0, -2, 0.8f));
        }
        add(measurements, legend, 12);
        pumpRow = new SensorRow("Charge pump");
        usbRow = new SensorRow("Main USB path");
        batteryRow = new SensorRow("Battery");
        for (SensorRow sensor : new SensorRow[]{pumpRow, usbRow, batteryRow}) {
            addDivider(measurements);
            add(measurements, sensor.view, 7);
        }
        rails = label("", 11, palette.onSurfaceVariant, false);
        add(measurements, rails, 9);
        add(content, measurements, 12);

        LinearLayout capabilities = card(palette.surface);
        capabilities.addView(label("Charger profiles", 17, palette.onSurface, true));
        profilesHint = label("", 12, palette.onSurfaceVariant, false);
        add(capabilities, profilesHint, 6);
        profilesView = column();
        add(capabilities, profilesView, 2);
        add(content, capabilities, 12);

        LinearLayout logger = card(palette.surface);
        logger.addView(label("USB interface log", 17, palette.onSurface, true));
        logStatus = label("", 12, palette.onSurfaceVariant, false);
        add(logger, logStatus, 6);
        LinearLayout loggerActions = row();
        logToggle = actionLabel("Start logging");
        logToggle.setOnClickListener(view -> toggleLogging());
        loggerActions.addView(logToggle, new LinearLayout.LayoutParams(-2, dp(48)));
        TextView viewLog = actionLabel("View log");
        viewLog.setOnClickListener(view -> showInterfaceLog());
        LinearLayout.LayoutParams logViewParams = new LinearLayout.LayoutParams(-2, dp(48));
        logViewParams.leftMargin = dp(8);
        loggerActions.addView(viewLog, logViewParams);
        add(logger, loggerActions, 9);
        add(content, logger, 12);
        setContentView(scroll);
    }

    private TextView actionLabel(String text) {
        TextView view = label(text, 12, palette.primary, true);
        view.setPadding(dp(13), 0, dp(13), 0);
        view.setGravity(Gravity.CENTER);
        view.setBackground(controlBackground(24));
        return view;
    }

    private void toggleLogging() {
        if (InterfaceLoggerService.isRunning()) {
            stopService(new Intent(this, InterfaceLoggerService.class));
        } else if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean(NOTIFICATION_PREF, false)
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean(NOTIFICATION_PREF, true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, LOGGER_NOTIFICATION_PERMISSION);
        } else {
            startLogger();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOGGER_NOTIFICATION_PERMISSION) startLogger();
    }

    private void startLogger() {
        try {
            startForegroundService(new Intent(this, InterfaceLoggerService.class));
        } catch (IllegalStateException | SecurityException error) {
            Toast.makeText(this, "Android couldn’t start interface logging.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showInterfaceLog() {
        String record;
        try {
            record = InterfaceLog.recent(getFilesDir());
        } catch (IOException error) {
            Toast.makeText(this, "Couldn’t read the interface log.", Toast.LENGTH_SHORT).show();
            return;
        }
        TextView body = label(record.isEmpty() ? "No interface snapshots yet." : record,
            12, palette.onSurface, false);
        body.setTextIsSelectable(true);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(dp(20), dp(12), dp(20), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
            .setTitle("Recent interface log")
            .setView(scroll)
            .setNegativeButton("Close", null);
        if (!record.isEmpty()) {
            dialog.setPositiveButton("Copy log", (ignored, button) ->
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("USB interface log", record)));
        }
        dialog.show();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void showAppearance() {
        new AlertDialog.Builder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(THEMES, themeChoice, (dialog, selected) -> {
                dialog.dismiss();
                if (selected == themeChoice) return;
                prefs.edit().putInt(THEME_PREF, selected).apply();
                recreate();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void renderAccess() {
        access.setText(root ? "Root active" : requesting ? "Cancel" : "Enable root");
        access.setContentDescription(root ? "Root active. Tap to stop root telemetry"
            : requesting ? "Cancel root request" : "Enable root telemetry");
        connection.setText(rootMessage != null ? rootMessage : requesting
            ? "Waiting for superuser access…" : root
            ? "Live · refreshes every 2 seconds" : "Battery only · enable root for USB data");
    }

    private void render() {
        renderAccess();
        boolean logging = InterfaceLoggerService.isRunning();
        logToggle.setText(logging ? "Stop logging" : "Start logging");
        logStatus.setText(logging ? "Logging interface changes in the background"
            : "Record USB-C and charging interfaces on this device");
        Intent info = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = info == null ? -1 : info.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int status = info == null ? 0 : info.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        int plugged = info == null ? 0 : info.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        double batteryV = info == null ? Double.NaN
            : positive(info.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) * 0.001);
        int rawCurrent = battery == null ? Integer.MIN_VALUE
            : battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        double batteryA = rawCurrent == Integer.MIN_VALUE ? Double.NaN : rawCurrent * 0.000001;
        double batteryTemp = info == null ? Double.NaN
            : info.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE) * 0.1;
        if (batteryTemp < -100) batteryTemp = Double.NaN;
        batteryV = or(positive(field("battery.voltage_now", 0.000001)), batteryV);
        batteryTemp = or(field("battery.temp", 0.1), batteryTemp);
        if (sample.containsKey("pump.charging_enabled")) {
            // The Mi 11X fuel gauge reports a negative raw current while charging.
            batteryA = or(-field("battery.current_now", 0.000001), batteryA);
        }
        double batteryW = batteryV * batteryA;

        String dataRole = selectedRole(sample.get("port.data_role"));
        String powerRole = selectedRole(sample.get("port.power_role"));
        ChargingConnection power = new ChargingConnection(sample, plugged);
        boolean pd = power.usbAttached && "explicit".equals(sample.get("pd.contract"));
        List<PdProfile> profiles = new ArrayList<>();
        if (power.usbAttached) {
            for (int i = 1; i <= 7; i++) {
                PdProfile profile = PdProfile.decode(i, sample.get("pd.pdo" + i));
                if (profile != null) profiles.add(profile);
            }
        }
        String rdo = sample.get("pd.rdo");
        int activeIndex = pd ? PdProfile.requestedIndex(rdo) : 0;
        if (pd && activeIndex == 0) activeIndex = (int) or(field("pd.select_pdo", 1), 0);
        PdProfile active = null;
        for (PdProfile profile : profiles) {
            if (profile.index == activeIndex) active = profile;
        }

        String type = power.usbAttached ? sample.get("usb.real_type") : null;
        String modeText;
        if (!power.powerAttached) modeText = "No charger connected";
        else if (power.wireless && !power.usbAttached) modeText = "Wireless charging";
        else if (active != null && "PPS".equals(active.kind)) modeText = "USB Power Delivery · PPS";
        else if (pd || "USB_PD".equals(type)) modeText = "USB Power Delivery";
        else if ("source".equals(powerRole)) modeText = "USB host · power source";
        else if (type != null && !type.equals("Unknown")) modeText = type.replace('_', ' ');
        else modeText = "External power connected";
        mode.setText(modeText);

        watts.setText(unit(Math.abs(batteryW), "W"));
        String batteryStatus = status == BatteryManager.BATTERY_STATUS_FULL ? "Full"
            : batteryW < -0.05 ? "Battery discharging" : batteryW > 0.05 ? "Power into battery"
            : status == BatteryManager.BATTERY_STATUS_CHARGING ? "Charging" : "Battery power";
        charge.setText(batteryStatus + (level >= 0 ? "  ·  " + level + "% battery" : ""));
        if (!requesting) trend.sample(batteryW);

        double v = Double.NaN;
        double a = Double.NaN;
        if (pd) {
            v = active == null ? Double.NaN : active.requestedVoltage(rdo);
            a = active == null ? Double.NaN : active.requestedCurrent(rdo);
        }
        boolean hasRequest = Double.isFinite(positive(v)) && Double.isFinite(positive(a));
        if (!hasRequest && power.usbAttached && (pd || "USB_PD".equals(type))) {
            v = positive(field("usb.pd_voltage_max", 0.000001));
            a = positive(field("usb.pd_current_max", 0.000001));
        }
        String roles = dataRole != null && powerRole != null && power.usbAttached
            ? capitalize(dataRole) + " · power " + powerRole : "";
        if (Double.isFinite(v) && Double.isFinite(a)) {
            String suffix = roles.isEmpty() ? "" : "\n" + roles;
            request.setText((hasRequest ? "Requested " : "PD limit ") + unit(v, "V") + " × " + unit(a, "A")
                + "  ·  " + unit(v * a, "W") + suffix);
        } else {
            request.setText(roles.isEmpty() ? modeText : roles);
        }

        double connectorTemp = power.usbAttached ? field("usb.connector_temp", 0.1) : Double.NaN;
        temperature.setText("Battery " + unit(batteryTemp, "°C") + "  ·  Connector "
            + unit(connectorTemp, "°C"));

        double usbV = power.usbAttached ? positive(field("usb.voltage_now", 0.000001)) : Double.NaN;
        double usbA = power.usbAttached ? field("usb.input_current_now", 0.000001) : Double.NaN;
        String pumpEnabled = sample.get("pump.charging_enabled");
        String pumpVbus = sample.get("pump.ti_vbus_present");
        double pumpVoltage = positive(field("pump.ti_bus_voltage", 0.001));
        boolean pumpHasInput = power.usbAttached && ("1".equals(pumpVbus)
            || (pumpVbus == null && Double.isFinite(pumpVoltage)));
        double pumpV = pumpHasInput ? pumpVoltage : Double.NaN;
        double pumpA = pumpHasInput ? field("pump.ti_bus_current", 0.001) : Double.NaN;
        String pumpStatus = !root ? "Requires root"
            : pumpEnabled == null && pumpVbus == null && !Double.isFinite(pumpVoltage) ? "Unavailable"
            : !pumpHasInput ? "No USB input"
            : "0".equals(pumpEnabled) ? "Inactive"
            : "1".equals(pumpEnabled) ? "Enabled" : "Input detected";
        usbRow.update(usbV, usbA);
        pumpRow.setDetail(pumpStatus);
        pumpRow.update(pumpV, pumpA);
        batteryRow.update(batteryV, batteryA);
        double rail = positive(field("usb.voltage_vph", 0.000001));
        double sense = positive(field("pump.ti_battery_voltage", 0.001));
        rails.setVisibility(root && (Double.isFinite(rail) || Double.isFinite(sense)) ? View.VISIBLE : View.GONE);
        rails.setText("System rail " + unit(rail, "V") + "  ·  Pump battery sense " + unit(sense, "V"));
        renderProfiles(profiles, activeIndex, power.usbAttached);
    }

    private void renderProfiles(List<PdProfile> profiles, int active, boolean attached) {
        int fixedPeak = 0;
        boolean limited = false;
        StringBuilder key = new StringBuilder().append(active).append(':');
        for (PdProfile profile : profiles) {
            fixedPeak = Math.max(fixedPeak, profile.fixedPowerMw());
            limited |= profile.powerLimited;
            key.append(profile.index).append(profile.kind).append(profile.voltageLabel())
                .append(profile.limitLabel()).append(profile.powerLimited);
        }
        if (profiles.isEmpty()) {
            profilesHint.setText(!root ? "Enable root to read the charger’s USB PD profiles."
                : attached ? "No USB PD profiles reported." : "Connect a USB PD charger to see its profiles.");
        } else {
            String summary = fixedPeak > 0 ? "Up to " + PdProfile.number(fixedPeak / 1000.0) + " W fixed"
                : profiles.size() + " advertised profiles";
            profilesHint.setText(limited ? summary + "  ·  PPS limits vary with voltage" : summary);
        }
        if (displayedProfiles.equals(key.toString())) return;
        displayedProfiles = key.toString();
        profilesView.removeAllViews();
        for (PdProfile profile : profiles) {
            LinearLayout line = row();
            line.setGravity(Gravity.CENTER_VERTICAL);
            boolean selected = profile.index == active;
            line.setPadding(dp(9), dp(9), dp(9), dp(9));
            if (selected) line.setBackground(background(palette.primaryContainer, 12, Color.TRANSPARENT));
            int color = selected ? palette.onPrimaryContainer : palette.onSurface;
            TextView name = label(profile.kind, 12, selected ? color : palette.onSurfaceVariant, true);
            line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView voltage = label(profile.voltageLabel(), 13, color, true);
            line.addView(weighted(voltage, Gravity.CENTER), new LinearLayout.LayoutParams(0, -2, 1.35f));
            TextView limit = label(profile.limitLabel(), 13, color, false);
            line.addView(weighted(limit, Gravity.END), new LinearLayout.LayoutParams(0, -2, 0.85f));
            if (selected) {
                line.setContentDescription("Active " + profile.kind + " profile, "
                    + profile.voltageLabel() + ", up to " + profile.limitLabel());
                line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
            add(profilesView, line, 3);
        }
    }

    private double field(String key, double scale) {
        String raw = sample.get(key);
        if (raw != null) {
            try {
                return Long.parseLong(raw.trim()) * scale;
            } catch (NumberFormatException ignored) {
                // A vendor-specific property may return a status instead of a number.
            }
        }
        return Double.NaN;
    }

    private static double positive(double value) {
        return value > 0 ? value : Double.NaN;
    }

    private static double or(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static String unit(double value, String unit) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.2f %s", value, unit) : EMPTY;
    }

    private static String selectedRole(String value) {
        if (value == null) return null;
        int open = value.indexOf('[');
        int close = value.indexOf(']', open + 1);
        if (open >= 0 && close > open) value = value.substring(open + 1, close);
        value = value.trim();
        return value.isEmpty() || value.equals("none") ? null : value;
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout row() {
        return new LinearLayout(this);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout card(int color) {
        LinearLayout view = column();
        view.setPadding(dp(16), dp(15), dp(16), dp(15));
        view.setBackground(background(color, 18, palette.outline));
        return view;
    }

    private GradientDrawable background(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private RippleDrawable controlBackground(int radius) {
        int ripple = (palette.primary & 0x00ffffff) | 0x24000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple),
            background(palette.surface, radius, palette.outline), null);
    }

    private void addDivider(LinearLayout parent) {
        View view = new View(this);
        view.setBackgroundColor(palette.outline);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.topMargin = dp(7);
        parent.addView(view, params);
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView weighted(TextView view, int gravity) {
        view.setGravity(gravity);
        return view;
    }

    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(child, params);
    }

    private final class SensorRow {
        final LinearLayout view = row();
        final TextView[] values = new TextView[3];
        final TextView detail;

        SensorRow(String name) {
            view.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout nameColumn = column();
            nameColumn.addView(label(name, 12, palette.onSurface, true));
            detail = label("", 10, palette.onSurfaceVariant, false);
            detail.setVisibility(View.GONE);
            add(nameColumn, detail, 3);
            view.addView(nameColumn, new LinearLayout.LayoutParams(0, -2, 1.6f));
            for (int i = 0; i < values.length; i++) {
                values[i] = label(EMPTY, 13, palette.onSurface, false);
                values[i].setGravity(Gravity.END);
                values[i].setFontFeatureSettings("tnum");
                view.addView(values[i], new LinearLayout.LayoutParams(0, -2, 0.8f));
            }
        }

        void setDetail(String text) {
            detail.setText(text);
            detail.setVisibility(View.VISIBLE);
        }

        void update(double voltage, double current) {
            double[] measures = {voltage, current, voltage * current};
            for (int i = 0; i < values.length; i++) {
                double value = measures[i];
                values[i].setText(Double.isFinite(value) ? String.format(Locale.US, "%.2f", value) : EMPTY);
                values[i].setTextColor(Double.isFinite(value) ? i == 2 ? palette.primary : palette.onSurface : palette.onSurfaceVariant);
            }
        }
    }

    private final class PowerTrend extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<Double> readings = new ArrayList<>();
        private long lastSample;

        PowerTrend() {
            super(MainActivity.this);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void sample(double power) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastSample < 1750) return;
            if (now - lastSample > 5000) readings.clear();
            lastSample = now;
            readings.add(power);
            if (readings.size() > 30) readings.remove(0);
            invalidate();
        }

        void save(Bundle state) {
            double[] values = new double[readings.size()];
            for (int i = 0; i < values.length; i++) values[i] = readings.get(i);
            state.putDoubleArray("powerTrend", values);
            state.putLong("powerTrendTime", lastSample);
        }

        void restore(Bundle state) {
            double[] values = state.getDoubleArray("powerTrend");
            if (values != null) {
                for (double value : values) readings.add(value);
                lastSample = state.getLong("powerTrendTime");
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor((palette.onPrimaryContainer & 0x00ffffff) | 0x42000000);
            canvas.drawLine(0, height - 1, width, height - 1, paint);

            double min = 0;
            double max = 1;
            for (double reading : readings) {
                if (!Double.isFinite(reading)) continue;
                min = Math.min(min, reading);
                max = Math.max(max, reading);
            }
            boolean continuing = false;
            Path trace = new Path();
            for (int i = 0; i < readings.size(); i++) {
                double value = readings.get(i);
                if (!Double.isFinite(value)) {
                    continuing = false;
                    continue;
                }
                float x = width * (29 - readings.size() + 1 + i) / 29f;
                float y = height - dp(3) - (float) ((value - min) / (max - min)) * (height - dp(6));
                if (continuing) trace.lineTo(x, y);
                else trace.moveTo(x, y);
                continuing = true;
            }
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(palette.onPrimaryContainer);
            canvas.drawPath(trace, paint);
            if (!readings.isEmpty()) {
                double last = readings.get(readings.size() - 1);
                if (Double.isFinite(last)) {
                    float y = height - dp(3) - (float) ((last - min) / (max - min)) * (height - dp(6));
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(width - dp(3), y, dp(3), paint);
                }
            }
        }
    }
}

package dev.chargescope.monitor;

import android.os.BatteryManager;

import java.util.Map;

final class ChargingConnection {
    final boolean usbAttached;
    final boolean powerAttached;
    final boolean wireless;

    ChargingConnection(Map<String, String> fields, int plugged) {
        boolean usbReported = "1".equals(fields.get("usb.online"))
            || "1".equals(fields.get("usb.present"))
            || fields.containsKey("partner.supports_usb_power_delivery");
        boolean androidReportsWired = (plugged &
            (BatteryManager.BATTERY_PLUGGED_AC | BatteryManager.BATTERY_PLUGGED_USB)) != 0;
        usbAttached = usbReported || androidReportsWired;
        powerAttached = usbAttached || plugged != 0;
        wireless = !androidReportsWired && (plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0;
    }

    static String androidSource(int plugged) {
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) return "USB";
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) return "Wired (AC)";
        if ((plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) return "Wireless";
        return plugged == 0 ? "Battery" : "External power";
    }
}

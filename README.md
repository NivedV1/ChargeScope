# ChargeScope

ChargeScope shows live charging telemetry on Android. The battery view uses Android's
public battery API. Tap **Enable root** for charger capabilities, USB-C roles,
the active PD request, connector temperature, and separate USB and charge-pump
measurements. On the Mi 11X, allow ChargeScope in KernelSU's Superuser tab if the
root request is denied, then tap **Enable root** again.

Use the theme button beside the root control to choose **System default**, **Light**,
or **Dark**. Android 12 and newer supply the Material You palette. Older Android
versions use a built-in light or dark palette.

The app reads kernel properties without writing to them. The USB and charge-pump
rows describe individual measurement points. Battery power is signed in the
measurements table, with positive values representing net power into the battery.
When the charge pump is inactive, its row still shows the input voltage and
current reported by the sensor while USB power is present.
Telemetry runs every two seconds while the app is visible. Availability of detailed
readings depends on the phone's kernel.

## Build

Install Android SDK Platform 36, Android Build Tools 36.0.0, a JDK, and `zip`.
Set `ANDROID_SDK_ROOT` to the SDK directory and run `./build.sh`. The script writes
a development-signed `ChargeScope.apk`. Keep the generated `.build/debug.keystore`
if you want to install future builds as updates.

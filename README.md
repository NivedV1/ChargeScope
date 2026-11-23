# ChargeScope

ChargeScope shows live battery, USB-C, and charging telemetry on Android 8.0 and
newer. Download `ChargeScope.apk` from [GitHub Releases](https://github.com/NivedV1/ChargeScope/releases/latest).

## Charging dashboard

- **Battery and power status** use Android's battery API and work without root. Since
  1.2.0, Android's plug status also applies when root is enabled and vendor USB files
  are missing.
- **Charger and USB-C details** can include the charger's advertised USB PD profiles,
  the active PD request, and the port's data and power roles. Tap **Enable root** to
  read them. With KernelSU, allow ChargeScope in the **Superuser** tab if the request
  is denied, then tap **Enable root** again.
- **Live measurements** update every two seconds while the dashboard is visible.
  The main USB path, charge-pump input, and battery readings describe different
  measurement points. USB current and power are negative when the phone supplies
  power to a connected accessory. Positive battery power indicates net power into
  the battery. An inactive charge pump can still report its input voltage and
  current while USB is connected.
- **Appearance** follows your choice of **System default**, **Light**, or **Dark**.
  Android 12 and newer supply the Material You color palette.

Detailed readings depend on the phone's kernel and available sensors. The detailed
USB PD and charge-pump dashboard was tested on the Xiaomi Mi 11X. Other Qualcomm
and MediaTek devices may expose different interfaces and show fewer readings.

## Interface logging

Open **USB interface log** and tap **Start logging**. ChargeScope scans on power
connection changes and about every 30 seconds, even when the dashboard is in the
background. Android can show an ongoing notification with **Stop logging** when
notifications are allowed. You can also stop logging in the app.

The log records the device model, Android and kernel versions, and changes to the
power source, visible USB-C and charging interfaces, driver names, and reported
modes. The scan covers standard Linux Type-C and USB PD classes, vendor USB PD
classes, Type-C port controllers, and charging power supplies. Root can make more
interfaces readable. An interface appearing in the log confirms discovery. Detailed
dashboard readings still depend on which properties the app can read and interpret.

The scan is read-only. Logs rotate in the app's private storage. Use **View log** and
**Copy log** to share recent entries manually, for example when reporting a device
compatibility issue. ChargeScope keeps the log on the phone.

## Contributing code and device data

Code contributions and device reports are welcome. Logs from MediaTek phones and
other Qualcomm models are especially helpful for improving compatibility. A report
is useful even if you have no root access or the dashboard shows limited data.

- **Share a device report.** In **USB interface log**, tap **Start logging**, plug
  in a charger, wait for the readings to settle, then unplug it. Use **View log**
  and **Copy log** to include the relevant entries in a
  [GitHub issue](https://github.com/NivedV1/ChargeScope/issues/new). Include the phone
  model, Android and ChargeScope versions, whether root was enabled, and the charger
  model or rated wattage if known. Mention any readings that were missing or looked
  wrong. Issues are public, so review the log before posting and remove anything
  you prefer to keep private.
- **Contribute a fix or improvement.** Open a
  [pull request](https://github.com/NivedV1/ChargeScope/compare) and describe what
  changed and how you tested it. For device support changes, include the phone model
  and the relevant interface log entries if available.

## Build

Install Android SDK Platform 36, Android Build Tools 36.0.0, a JDK, and `zip`.
Set `ANDROID_SDK_ROOT` to the SDK directory and run `./build.sh`. The script writes
a development-signed `ChargeScope.apk`. Keep the generated `.build/debug.keystore`
if you want to install future builds as updates.

## License

ChargeScope is released under the [MIT License](LICENSE).

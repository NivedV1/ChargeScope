select_vendor_supply() {
    local node="$sys/class/power_supply/usb"
    legacy_usb=
    # These companion nodes identify the legacy layout used by the Mi 11X.
    if [ -d "$node" ] &&
        { [ -d "$sys/class/usbpd/usbpd0" ] || [ -d "$sys/class/power_supply/bq2597x-standalone" ]; } &&
        { [ "$port_rank" -eq 0 ] || [ "$port" = "$sys/class/typec/port0" ]; } &&
        { [ "$usb_supply" = "$node" ] || { [ -z "$qcom_usb" ] && [ "$supply_rank" -lt 2 ]; }; }; then
        legacy_usb=$node
        usb_supply=$node
    fi
}

read_vendor_telemetry() {
    if [ -n "$legacy_usb" ]; then
        if [ -z "$qcom_usb" ]; then
            read_fields usb "$legacy_usb" real_type voltage_now input_current_now pd_voltage_max pd_current_max
        fi
        read_fields usb "$legacy_usb" voltage_vph connector_temp
        read_fields pump "$sys/class/power_supply/bq2597x-standalone" charging_enabled \
            ti_vbus_present ti_bus_voltage ti_bus_current ti_battery_voltage
        if [ -z "$qcom_pd" ]; then
            read_fields pd "$sys/class/usbpd/usbpd0" contract select_pdo rdo pdo1 pdo2 pdo3 pdo4 pdo5 pdo6 pdo7
        fi
    fi
    read_fields battery "$sys/class/power_supply/battery" voltage_now current_now temp
}

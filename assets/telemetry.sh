read_fields() {
    local prefix="$1" base="$2" field value
    shift 2
    for field in "$@"; do
        if [ -r "$base/$field" ]; then
            value=
            { IFS= read -r value < "$base/$field" || [ -n "$value" ]; } 2>/dev/null &&
                printf '%s.%s=%s\n' "$prefix" "$field" "$value"
        fi
    done
}

[ "$(id -u)" = 0 ] || exit 1
printf '@ROOT\n'
while :; do
    read_fields usb /sys/class/power_supply/usb present online real_type type typec_mode \
        voltage_now voltage_vph input_current_now input_current_settled \
        pd_active pd_voltage_min pd_voltage_max pd_current_max connector_temp connector_health
    read_fields port /sys/class/typec/port0 data_role power_role power_operation_mode
    read_fields partner /sys/class/typec/port0-partner supports_usb_power_delivery
    read_fields battery /sys/class/power_supply/battery voltage_now current_now temp capacity status
    read_fields pump /sys/class/power_supply/bq2597x-standalone charging_enabled \
        ti_vbus_present ti_bus_voltage ti_bus_current ti_battery_voltage ti_die_temperature
    read_fields pd /sys/class/usbpd/usbpd0 contract select_pdo rdo pdo1 pdo2 pdo3 pdo4 pdo5 pdo6 pdo7
    printf '@SAMPLE\n' || exit
    sleep 2
done

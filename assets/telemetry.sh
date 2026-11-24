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

read_standard_pd() {
    local node entry index prefix
    [ -n "$pd_caps" ] || return
    for node in "$pd_caps"/[1-7]:*; do
        [ -d "$node" ] || continue
        entry=${node##*/}
        index=${entry%%:*}
        prefix="standard_pd.$index"
        case "${entry#*:}" in
            fixed_supply|variable_supply|battery|programmable_supply)
                printf '%s.type=%s\n' "$prefix" "${entry#*:}"
                read_fields "$prefix" "$node" voltage minimum_voltage maximum_voltage \
                    maximum_current maximum_power pps_power_limited
                ;;
        esac
    done
}

[ "$(id -u)" = 0 ] || exit 1
printf '@ROOT\n'
while :; do
    # Resolve again for each snapshot so unplugging or swapping ports drops stale readings.
    discover_charging_interfaces
    if [ -n "$port" ]; then
        read_fields port "$port" data_role power_role power_operation_mode
    fi
    if [ -n "$partner" ]; then
        printf 'partner.attached=1\n'
        read_fields partner "$partner" supports_usb_power_delivery
    fi
    if [ -n "$usb_supply" ]; then
        read_fields usb "$usb_supply" present online type usb_type
    fi
    read_standard_pd
    read_vendor_telemetry
    read_qualcomm_interfaces
    printf '@SAMPLE\n' || exit
    sleep 2
done

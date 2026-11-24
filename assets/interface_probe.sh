# Only read kernel attributes. Some USB PD nodes trigger requests when read.
# Keep the probe to cached status and interface identifiers.
emit() {
    local kind="$1" node="$2" detail="$3" driver
    driver=$(bound_driver "$node")
    [ -z "$driver" ] || detail="$detail · driver $driver"
    printf '%s.%s=/sys%s%s\n' "$kind" "${node##*/}" "${node#"$sys"}" "${detail:+ · $detail}"
}

log_typec_port() {
    local node="$1" roles power operation attachment
    roles=$(first_value "$node/data_role")
    power=$(first_value "$node/power_role")
    operation=$(first_value "$node/power_operation_mode")
    if [ -d "$node-partner" ]; then attachment=' · partner attached'; fi
    emit typec "$node" "USB-C port${roles:+ · data $roles}${power:+ · power $power}${operation:+ · $operation}${attachment:-}"
}

log_supply() {
    local node="$1" kind="$2" mode online enabled field fields=
    mode=$(first_value "$node/real_type" "$node/usb_type" "$node/charge_type")
    online=$(first_value "$node/online")
    enabled=$(first_value "$node/charging_enabled")
    for field in voltage_now current_now input_current_now voltage_max current_max input_current_limit; do
        if [ -r "$node/$field" ]; then fields="${fields:+$fields, }$field"; fi
    done
    emit supply "$node" "Power supply${kind:+ · $kind}${mode:+ · $mode}${online:+ · online $online}${enabled:+ · charging enabled $enabled}${fields:+ · attributes $fields}"
}

if [ "$(id -u)" = 0 ]; then printf '@ROOT\n'; fi
while :; do
    discover_charging_interfaces
    if [ -n "$partner" ]; then emit selected "$partner" 'Selected USB-C partner'; fi
    if [ -n "$pd_caps" ]; then emit selected "$pd_caps" 'Selected USB PD profiles'; fi
    if [ -n "$usb_supply" ]; then emit selected "$usb_supply" 'Selected USB power supply'; fi
    if [ -n "$qcom_usb" ]; then emit selected.usb "$qcom_usb" 'Qualcomm live USB measurements'; fi
    if [ -n "$qcom_pd" ]; then emit selected.pd "$qcom_pd" 'Qualcomm USB PD profile fallback'; fi
    for_each_typec_port log_typec_port

    for node in "$sys"/class/usbpd/* "$sys"/class/usb_power_delivery/*; do
        [ -d "$node" ] || continue
        case "$node" in
            */class/usbpd/*)
                contract=$(first_value "$node/contract")
                detail="Vendor USB PD${contract:+ · $contract}"
                if [ -r "$node/pdo1" ]; then detail="$detail · PDO/RDO files"; fi
                ;;
            *)
                detail="Linux USB PD"
                if [ -d "$node/source-capabilities" ]; then detail="$detail · source capabilities"; fi
                ;;
        esac
        emit pd "$node" "$detail"
    done

    for node in "$sys"/class/tcpc/* "$sys"/class/tcpm/*; do
        [ -d "$node" ] || continue
        emit controller "$node" 'Type-C port controller'
    done

    for_each_charging_supply log_supply
    printf '@SAMPLE\n' || exit
    sleep 30
done

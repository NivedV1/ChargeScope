# Only read kernel attributes. Some USB PD nodes trigger requests when read.
# Keep the probe to cached status and interface identifiers.
sys=/sys

first_value() {
    local path value
    for path in "$@"; do
        value=
        if [ -r "$path" ] && { IFS= read -r value < "$path" || [ -n "$value" ]; } 2>/dev/null && [ -n "$value" ]; then
            printf '%s' "$value"
            return
        fi
    done
}

bound_driver() {
    local node link
    node=$(readlink -f "$1" 2>/dev/null) || return
    while [ "${node#"$sys/devices/"}" != "$node" ]; do
        if [ -L "$node/driver" ]; then
            link=$(readlink "$node/driver" 2>/dev/null) || return
            printf '%s' "${link##*/}"
            return
        fi
        node=${node%/*}
    done
}

emit() {
    local kind="$1" node="$2" detail="$3" driver
    driver=$(bound_driver "$node")
    [ -z "$driver" ] || detail="$detail · driver $driver"
    printf '%s.%s=/sys%s%s\n' "$kind" "${node##*/}" "${node#"$sys"}" "${detail:+ · $detail}"
}

if [ "$(id -u)" = 0 ]; then printf '@ROOT\n'; fi
while :; do
    for node in "$sys"/class/typec/port[0-9]*; do
        [ -d "$node" ] || continue
        case "${node##*/}" in *-*) continue ;; esac
        roles=$(first_value "$node/data_role")
        power=$(first_value "$node/power_role")
        operation=$(first_value "$node/power_operation_mode")
        emit typec "$node" "USB-C port${roles:+ · data $roles}${power:+ · power $power}${operation:+ · $operation}"
    done

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

    for node in "$sys"/class/power_supply/*; do
        [ -d "$node" ] || continue
        kind=$(first_value "$node/type")
        name=${node##*/}
        case "$name:$kind" in
            *usb*:*|*charg*:*|*pump*:*|*bq259*:*|*ln800*:*|*cp_master*:*|*cp_slave*:*|*:USB*|*:Mains|*:Wireless|*:Charge_Pump)
                mode=$(first_value "$node/real_type" "$node/usb_type" "$node/charge_type")
                online=$(first_value "$node/online")
                enabled=$(first_value "$node/charging_enabled")
                emit supply "$node" "Power supply${kind:+ · $kind}${mode:+ · $mode}${online:+ · online $online}${enabled:+ · charging enabled $enabled}"
                ;;
        esac
    done
    printf '@SAMPLE\n' || exit
    sleep 30
done

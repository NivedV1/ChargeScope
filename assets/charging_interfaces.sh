# Shared sysfs discovery for the dashboard and interface log.
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

bound_device() {
    local node
    node=$(readlink -f "$1" 2>/dev/null) || return
    while [ "${node#"$sys/devices/"}" != "$node" ]; do
        if [ -L "$node/driver" ]; then
            printf '%s' "$node"
            return
        fi
        node=${node%/*}
    done
}

bound_driver() {
    local device link
    device=$(bound_device "$1")
    [ -n "$device" ] || return
    link=$(readlink "$device/driver" 2>/dev/null) || return
    printf '%s' "${link##*/}"
}

for_each_typec_port() {
    local node index
    for node in "$sys"/class/typec/port[0-9]*; do
        [ -d "$node" ] || continue
        index=${node##*/port}
        case "$index" in *[!0-9]*) continue ;; esac
        "$1" "$node"
    done
}

for_each_charging_supply() {
    local node kind
    for node in "$sys"/class/power_supply/*; do
        [ -d "$node" ] || continue
        kind=$(first_value "$node/type")
        case "${node##*/}:$kind" in
            *usb*:*|*charg*:*|*pump*:*|*cp_master*:*|*cp_slave*:*|*:USB*|*:Mains|*:Wireless|*:Charge_Pump)
                "$1" "$node" "$kind"
                ;;
        esac
    done
}

consider_port() {
    local node="$1" role rank=1
    if [ -d "$node-partner" ]; then
        rank=2
        role=$(first_value "$node/power_role")
        case "$role" in sink|*'[sink]'*) rank=3 ;; esac
    fi
    if [ "$rank" -gt "$port_rank" ]; then
        port=$node
        port_rank=$rank
    elif [ "$rank" -eq "$port_rank" ]; then
        port=
    fi
}

consider_usb_supply() {
    local node="$1" kind="$2" online present rank=1
    case "$kind" in USB*) ;; *) return ;; esac
    online=$(first_value "$node/online")
    present=$(first_value "$node/present")
    case "$online" in
        ''|0|*[!0-9]*) [ "$present" != 1 ] || rank=2 ;;
        *) rank=3 ;;
    esac
    if [ "$rank" -gt "$supply_rank" ]; then
        usb_supply=$node
        supply_rank=$rank
    elif [ "$rank" -eq "$supply_rank" ]; then
        usb_supply=
    fi
}

# Only the normal APK adds the legacy device-layout fallback.
select_vendor_supply() { :; }
read_vendor_telemetry() { :; }

discover_charging_interfaces() {
    port= partner= pd_caps= usb_supply= port_rank=0 supply_rank=0
    for_each_typec_port consider_port
    if [ -n "$port" ] && [ -d "$port-partner" ]; then
        partner="$port-partner"
        if [ -d "$partner/usb_power_delivery/source-capabilities" ]; then
            pd_caps="$partner/usb_power_delivery/source-capabilities"
        fi
    fi
    for_each_charging_supply consider_usb_supply
    discover_qualcomm_interfaces
    select_vendor_supply
}

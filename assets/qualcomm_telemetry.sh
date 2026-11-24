# Only use these fields with the drivers that define their measurement and PD semantics.
discover_qualcomm_interfaces() {
    local node controller
    qcom_usb= qcom_pd=
    if [ -n "$usb_supply" ] && { [ -r "$usb_supply/voltage_now" ] || [ -r "$usb_supply/input_current_now" ]; } &&
        [ "$(bound_driver "$usb_supply")" = qcom,qpnp-smb5 ]; then
        qcom_usb=$usb_supply
    fi

    [ -n "$partner" ] && [ "$(bound_driver "$port")" = qpnp-pdphy ] || return 0
    controller=$(bound_device "$port")
    [ -n "$controller" ] || return 0
    for node in "$sys"/class/usbpd/*; do
        [ -r "$node/pdo1" ] && [ "$(bound_device "$node")" = "$controller" ] &&
            [ "$(first_value "$node/contract")" = explicit ] || continue
        if [ -n "$qcom_pd" ]; then
            qcom_pd=
            return
        fi
        qcom_pd=$node
    done
}

read_qualcomm_interfaces() {
    if [ -n "$qcom_usb" ]; then
        read_fields usb "$qcom_usb" voltage_now input_current_now real_type pd_voltage_max pd_current_max
    fi
    if [ -n "$qcom_pd" ]; then
        read_fields pd "$qcom_pd" contract select_pdo rdo pdo1 pdo2 pdo3 pdo4 pdo5 pdo6 pdo7
    fi
}

#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

## Bluetooth
PRODUCT_PACKAGES += \
    BluetoothOverlayTarget

## IR remote control
PRODUCT_PACKAGES += \
    TvSettingsOverlayTarget \
    XiaomiRemoteButtons

## Init
PRODUCT_PACKAGES += \
    init.jaws.bluetooth.rc \
    init.jaws.led.rc \
    init.jaws.usb.rc \
    init.recovery.jaws.rc

## Keylayout
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/keylayout/Vendor_0001_Product_0001.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/Vendor_0001_Product_0001.kl \
    $(LOCAL_PATH)/configs/keylayout/Vendor_2717_Product_32b9.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/Vendor_2717_Product_32b9.kl

## Netflix
PRODUCT_PACKAGES += \
    NetflixConfig \
    NetflixConfigOverlayTarget

## Platform
TARGET_AMLOGIC_SOC := sc2

# Remote control
PRODUCT_PACKAGES += \
    XiaomiRemoteVoice \
    ble-remote-scanner

## Soong Namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH) \
    hardware/mediatek/wlan/wifi_hal \
    hardware/xiaomi

## Wi-Fi
PRODUCT_PACKAGES += \
    libwifi-hal-mediatek \
    wifi_vendor_hal.xml

## Inherit from the common tree product makefile
$(call inherit-product, device/amlogic/ne-common/ne.mk)

## Inherit from the proprietary files makefile
$(call inherit-product, vendor/xiaomi/jaws/jaws-vendor.mk)

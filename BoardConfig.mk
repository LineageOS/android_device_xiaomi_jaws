#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

DEVICE_PATH := device/xiaomi/jaws

## Bootloader
TARGET_BOOTLOADER_BOARD_NAME := jaws

## DTB
BOARD_KERNEL_SEPARATED_DTBO := true
TARGET_DTB_NAME := sc2_s905x4_jaws
TARGET_DTBO_NAME := android_overlay_dt

## Kernel
TARGET_KERNEL_PLATFORM_TARGET := jaws
TARGET_KERNEL_SOURCE := vendor/xiaomi/jaws-build

## Kernel modules
BOOT_KERNEL_MODULES := $(strip $(shell cat $(DEVICE_PATH)/vendor_boot.modules.load))

BOARD_VENDOR_KERNEL_MODULES_LOAD := $(strip $(shell cat $(DEVICE_PATH)/vendor_dlkm.modules.load))
BOARD_VENDOR_RAMDISK_KERNEL_MODULES_LOAD := $(BOOT_KERNEL_MODULES)

## Partitions
BOARD_SUPER_PARTITION_SIZE := 2202009600

## Properties
TARGET_VENDOR_PROP += $(DEVICE_PATH)/vendor.prop

# SELinux
BOARD_VENDOR_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/vendor

## Wi-Fi
BOARD_HOSTAPD_DRIVER := NL80211
BOARD_WPA_SUPPLICANT_DRIVER := NL80211
WIFI_DRIVER_SOCKET_IFACE := wlan0
WPA_SUPPLICANT_VERSION := VER_0_8_X

## Include the common tree BoardConfig makefile
include device/amlogic/ne-common/BoardConfigCommon.mk

## Include the proprietary BoardConfig makefile
include vendor/xiaomi/jaws/BoardConfigVendor.mk

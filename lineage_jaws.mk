#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Not set in time to check, so set before everything else
PRODUCT_IS_ATV := true

# Inherit some common AOSP stuff
$(call inherit-product, device/google/atv/products/atv_base.mk)

# Inherit some common Lineage stuff
$(call inherit-product, vendor/lineage/config/common_full_tv.mk)

# Inherit device configuration
$(call inherit-product, $(LOCAL_PATH)/device.mk)

## Device identifier. This must come after all inclusions
PRODUCT_BRAND := Xiaomi
PRODUCT_DEVICE := jaws
PRODUCT_GMS_CLIENTID_BASE := android-xiaomi-tv
PRODUCT_MANUFACTURER := Xiaomi
PRODUCT_MODEL := MiTV-AFKR0
PRODUCT_NAME := lineage_jaws

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="jaws-user 14 UKG3.250107.001 V816.0.26.4.17.UZFAABX release-keys" \
    BuildFingerprint=Xiaomi/jaws/jaws:14/UKG3.250107.001/V816.0.26.4.17.UZFAABX:user/release-keys \
    DeviceProduct=jaws \
    SystemName=jaws

#
# Copyright (C) 2024-2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Bluetooth
PRODUCT_PACKAGES += \
    libldacBT_abr \
    libldacBT_enc

# Device Settings
PRODUCT_PACKAGES += \
    XiaomiDolby

# Properties
PRODUCT_VENDOR_PROPERTIES += \
    ro.vendor.dolby.dax.version=DAX3_3.6.0.12_r1 \
    ro.vendor.dolby.model=PAFM00 \
    ro.vendor.dolby.device=OP46C3 \
    ro.vendor.dolby.manufacturer=OPLUS \
    ro.vendor.dolby.brand=OPLUS

# SEPolicy
BOARD_VENDOR_SEPOLICY_DIRS += vendor/oneplus/dolby/sepolicy/vendor

# Inherit from proprietary targets
$(call inherit-product, vendor/oneplus/dolby/dolby-vendor.mk)

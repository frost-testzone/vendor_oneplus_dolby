#
# Copyright (C) 2024-2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

DOLBY_PATH := vendor/oneplus/dolby

# Media
PRODUCT_COPY_FILES += \
    $(DOLBY_PATH)/media/media_codecs_dolby_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_dolby_audio.xml

# Properties
TARGET_ODM_PROP += $(DOLBY_PATH)/properties/odm.prop

# SEPolicy
BOARD_VENDOR_SEPOLICY_DIRS += $(DOLBY_PATH)/sepolicy/vendor

# Xiaomi Dolby
PRODUCT_PACKAGES += \
    XiaomiDolby \
    XiaomiDolbyResCommon

# Inherit from proprietary targets
$(call inherit-product, $(DOLBY_PATH)/dolby-vendor.mk)

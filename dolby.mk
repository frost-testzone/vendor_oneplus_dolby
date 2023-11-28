#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Media
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/media/media_codecs_dolby_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_dolby_audio.xml

# Xiaomi Dolby
PRODUCT_PACKAGES += \
    XiaomiDolby \
    XiaomiDolbyResCommon

# Inherit from proprietary targets
$(call inherit-product, $(LOCAL_PATH)/dolby-vendor.mk)

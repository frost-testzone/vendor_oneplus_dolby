#!/bin/bash
#
# SPDX-FileCopyrightText: 2016 The CyanogenMod Project
# SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

set -e

DEVICE=dolby
VENDOR=oneplus

# Load extract_utils and do some sanity checks
MY_DIR="${BASH_SOURCE%/*}"
if [[ ! -d "${MY_DIR}" ]]; then MY_DIR="${PWD}"; fi

ANDROID_ROOT="${MY_DIR}/../../.."

HELPER="${ANDROID_ROOT}/tools/extract-utils/extract_utils.sh"
if [ ! -f "${HELPER}" ]; then
    echo "Unable to find helper script at ${HELPER}"
    exit 1
fi
source "${HELPER}"

# Default to sanitizing the vendor folder before extraction
CLEAN_VENDOR=true

ONLY_FIRMWARE=
KANG=
SECTION=

while [ "${#}" -gt 0 ]; do
    case "${1}" in
        --only-firmware)
            ONLY_FIRMWARE=true
            ;;
        -n | --no-cleanup)
            CLEAN_VENDOR=false
            ;;
        -k | --kang)
            KANG="--kang"
            ;;
        -s | --section)
            SECTION="${2}"
            shift
            CLEAN_VENDOR=false
            ;;
        *)
            SRC="${1}"
            ;;
    esac
    shift
done

if [ -z "${SRC}" ]; then
    SRC="adb"
fi

function blob_fixup() {
    case "${1}" in
        odm/bin/hw/vendor.dolby_sp.media.c2@1.0-service)
            [ "$2" = "" ] && return 0
            "${PATCHELF}" --replace-needed "libcodec2_hidl@1.0.so" "libcodec2_hidl@1.0_sp.so" "${2}"
            "${PATCHELF}" --replace-needed "libcodec2_vndk.so" "libcodec2_vndk_sp.so" "${2}"
            ;;
        odm/lib64/libcodec2_store_dolby_sp.so)
            [ "$2" = "" ] && return 0
            "${PATCHELF}" --replace-needed "libcodec2_vndk.so" "libcodec2_vndk_sp.so" "${2}"
            ;;
        odm/lib64/libcodec2_soft_ac4dec_sp.so|odm/lib64/libcodec2_soft_ddpdec_sp.so)
            [ "$2" = "" ] && return 0
            "${PATCHELF}" --replace-needed "libcodec2_vndk.so" "libcodec2_vndk_sp.so" "${2}"
            "${PATCHELF}" --replace-needed "libcodec2_soft_common.so" "libcodec2_soft_common_sp.so" "${2}"
            "${PATCHELF}" --replace-needed "libstagefright_foundation.so" "libstagefright_foundation-v33.so" "${2}"
            ;;
        odm/lib64/libcodec2_soft_common_sp.so|odm/lib64/libcodec2_hidl_plugin_sp.so)
            [ "$2" = "" ] && return 0
            "${PATCHELF}" --replace-needed "libcodec2_vndk.so" "libcodec2_vndk_sp.so" "${2}"
            "${PATCHELF}" --replace-needed "libstagefright_foundation.so" "libstagefright_foundation-v33.so" "${2}"
            ;;
        odm/lib/libdlbdsservice_v3_6.so|odm/lib/libstagefright_soft_ddpdec.so|odm/lib64/libdlbdsservice_sp.so|odm/lib64/libdlbdsservice_v3_6.so)
            [ "$2" = "" ] && return 0
            "${PATCHELF}" --replace-needed "libstagefright_foundation.so" "libstagefright_foundation-v33.so" "${2}"
            ;;
        odm/lib64/libcodec2_vndk_sp.so)
            [ "$2" = "" ] && return 0
            "${PATCHELF}" --replace-needed "libui.so" "libui_sp.so" "${2}"
            "${PATCHELF}" --replace-needed "libstagefright_foundation.so" "libstagefright_foundation-v33.so" "${2}"
            ;;
        odm/lib64/libcodec2_hidl@1.0_sp.so)
            [ "$2" = "" ] && return 0
            "${PATCHELF}" --replace-needed "libcodec2_hidl_plugin.so" "libcodec2_hidl_plugin_sp.so" "${2}"
            "${PATCHELF}" --replace-needed "libcodec2_vndk.so" "libcodec2_vndk_sp.so" "${2}"
            ;;
        odm/lib64/libui_sp.so)
            [ "$2" = "" ] && return 0
            "${PATCHELF}" --replace-needed "android.hardware.graphics.common-V3-ndk.so" "android.hardware.graphics.common-V6-ndk.so" "${2}"
            "${PATCHELF}" --replace-needed "android.hardware.graphics.allocator-V1-ndk.so" "android.hardware.graphics.allocator-V2-ndk.so" "${2}"
            ;;
        *)
            return 1
            ;;
    esac

    return 0
}

function blob_fixup_dry() {
    blob_fixup "$1" ""
}

# Initialize the helper
setup_vendor "${DEVICE}" "${VENDOR}" "${ANDROID_ROOT}" false "${CLEAN_VENDOR}"

if [ -z "${ONLY_FIRMWARE}" ]; then
    extract "${MY_DIR}/proprietary-files.txt" "${SRC}" "${KANG}" --section "${SECTION}"
fi

"${MY_DIR}/setup-makefiles.sh"

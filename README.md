# OSS AOSPA Dolby & OnePlus Dolby Blobs

## Not for devices with 64-bit audio support! You can add 64-bit audio FX modules if needed.

## Getting Started

-  Clone this repository into `vendor/oneplus/dolby`.

- For dolby media codecs to work, add this line in your `media_codecs_*.xml` :-

```
<Include href="media_codecs_dolby_audio.xml" />
```

- Add dolby audio effects in your device's `audio_effects.xml` :-

```
<library name="dap_sw" path="libswdap_v3_6.so"/>
<library name="dap_hw" path="libhwdap_v3_6.so"/>
```

```
<effectProxy name="dap" library="proxy" uuid="9d4921da-8225-4f29-aefa-39537a04bcaa">
    <libsw library="dap_sw" uuid="6ab06da4-c516-4611-8166-452799218539"/>
    <libhw library="dap_hw" uuid="a0c30891-8246-4aef-b8ad-d53e26da0253"/>
</effectProxy>
```

- Inherit from dolby makefile by adding this line in your device's makefile (`device.mk`) :-

```
$(call inherit-product, vendor/oneplus/dolby/dolby.mk)
```

- Finally, inherit the FCM and manifest from the Dolby repository. For example :-

```
DEVICE_FRAMEWORK_COMPATIBILITY_MATRIX_FILE += \
    vendor/oneplus/dolby/vintf/dolby_framework_compatibility_matrix.xml
```

```
DEVICE_MANIFEST_FILE += \
    vendor/oneplus/dolby/vintf/dolby_manifest.xml
```

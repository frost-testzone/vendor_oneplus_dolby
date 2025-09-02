/*
 * Copyright (C) 2023-25 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.AudioPlaybackCallback
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.util.Log
import androidx.preference.PreferenceManager
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.DolbyConstants.DsParam
import co.aospa.dolby.xiaomi.R

internal class DolbyController private constructor(
    private val context: Context
) {
    private var dolbyEffect = DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0)
    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val handler = Handler(context.mainLooper)
    private val stereoWideningSupported =
        context.getResources().getBoolean(R.bool.dolby_stereo_widening_supported)
    private val volumeLevelerSupported =
        context.getResources().getBoolean(R.bool.dolby_volume_leveler_supported)

    // Restore current profile on every media session
    private val playbackCallback = object : AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            val isPlaying = configs.any {
                it.playerState == AudioPlaybackConfiguration.PLAYER_STATE_STARTED
            }
            dlog(TAG, "onPlaybackConfigChanged: isPlaying=$isPlaying")
            if (isPlaying)
                setCurrentProfile()
        }
    }

    // Restore current profile on audio device change
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesAdded")
            setCurrentProfile()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesRemoved")
            setCurrentProfile()
        }
    }

    private var registerCallbacks = false
        set(value) {
            if (field == value) return
            field = value
            dlog(TAG, "setRegisterCallbacks($value)")
            if (value) {
                audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
            } else {
                audioManager.unregisterAudioPlaybackCallback(playbackCallback)
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            }
        }

    var dsOn: Boolean
        get() =
            dolbyEffect.dsOn.also {
                dlog(TAG, "getDsOn: $it")
            }
        set(value) {
            dlog(TAG, "setDsOn: $value")
            checkEffect()
            dolbyEffect.dsOn = value
            registerCallbacks = value
            if (value)
                setCurrentProfile()
        }

    var profile: Int
        get() =
            dolbyEffect.profile.also {
                dlog(TAG, "getProfile: $it")
            }
        set(value) {
            dlog(TAG, "setProfile: $value")
            checkEffect()
            dolbyEffect.profile = value
        }

    init {
        // Restore our main settings
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        dsOn = prefs.getBoolean(DolbyConstants.PREF_ENABLE, true)

        context.resources.getStringArray(R.array.dolby_profile_values)
                .map { it.toInt() }
                .forEach { profile ->
                    // Reset dolby first to prevent it from loading bad settings
                    dolbyEffect.resetProfileSpecificSettings(profile)
                    // Now restore our profile-specific settings
                    restoreSettings(profile)
                }

        // Finally restore the current profile.
        setCurrentProfile()

        dlog(TAG, "initialized")
    }

    fun onBootCompleted () {
        dlog(TAG, "onBootCompleted()")

        // Migrate presets from credential protected storage if needed
        maybeMigratePresets()
    }

    private fun restoreSettings(profile: Int) {
        dlog(TAG, "restoreSettings(profile=$profile)")
        val prefs = context.getSharedPreferences("profile_$profile", Context.MODE_PRIVATE)
        setPreset(
            prefs.getString(DolbyConstants.PREF_PRESET, getPreset(profile))!!,
            profile
        )
        setBassEnhancerEnabled(
            prefs.getBoolean(DolbyConstants.PREF_BASS, getBassEnhancerEnabled(profile)),
            profile
        )
        setVolumeLevelerEnabled(
            // force disable if unsupported, else force enable on dynamic profile
            volumeLevelerSupported && (profile == 0 ||
                prefs.getBoolean(DolbyConstants.PREF_VOLUME, getVolumeLevelerEnabled(profile))),
            profile
        )
        if (profile == 0) {
            // below settings are not applicable for dynamic
            return
        }
        setIeqPreset(
            prefs.getString(
                DolbyConstants.PREF_IEQ,
                getIeqPreset(profile).toString()
            )!!.toInt(),
            profile
        )
        setHeadphoneVirtEnabled(
            prefs.getBoolean(DolbyConstants.PREF_HP_VIRTUALIZER, getHeadphoneVirtEnabled(profile)),
            profile
        )
        setSpeakerVirtEnabled(
            prefs.getBoolean(DolbyConstants.PREF_SPK_VIRTUALIZER, getSpeakerVirtEnabled(profile)),
            profile
        )
        setStereoWideningAmount(
            prefs.getInt(
                DolbyConstants.PREF_STEREO_WIDENING,
                getStereoWideningAmount(profile)
            )!!.toInt(),
            profile
        )
        setDialogueEnhancerEnabled(
            prefs.getBoolean(
                DolbyConstants.PREF_DIALOGUE,
                getDialogueEnhancerEnabled(profile)
            ),
            profile
        )
        setDialogueEnhancerAmount(
            prefs.getInt(
                DolbyConstants.PREF_DIALOGUE_AMOUNT,
                getDialogueEnhancerAmount(profile)
            )!!.toInt(),
            profile
        )
    }

    private fun maybeMigratePresets() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(PREF_KEY_PRESETS_MIGRATED, false)) {
            return
        }
        val ceContext = context.createCredentialProtectedStorageContext()
        val cePrefs = ceContext.getSharedPreferences(PREF_PRESETS, Context.MODE_PRIVATE)
        if (cePrefs.all.isEmpty()) {
            dlog(TAG, "no presets to migrate")
            return
        }
        if (context.moveSharedPreferencesFrom(ceContext, PREF_PRESETS)) {
            prefs.edit().putBoolean(PREF_KEY_PRESETS_MIGRATED, true).apply()
            dlog(TAG, "presets migrated successfully")
        } else {
            Log.w(TAG, "failed to migrate presets")
        }
    }

    private fun checkEffect() {
        if (!dolbyEffect.hasControl()) {
            Log.w(TAG, "lost control, recreating effect")
            dolbyEffect.release()
            dolbyEffect = DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0)
        }
    }

    private fun setCurrentProfile() {
        dlog(TAG, "setCurrentProfile")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        profile = prefs.getString(DolbyConstants.PREF_PROFILE, "0" /*dynamic*/)!!.toInt()
    }

    fun setDsOnAndPersist(dsOn: Boolean) {
        this.dsOn = dsOn
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(DolbyConstants.PREF_ENABLE, dsOn)
            .apply()
    }

    fun getProfileName(): String? {
        val profile = dolbyEffect.profile.toString()
        val profiles = context.resources.getStringArray(R.array.dolby_profile_values)
        val profileIndex = profiles.indexOf(profile)
        dlog(TAG, "getProfileName: profile=$profile index=$profileIndex")
        return if (profileIndex == -1) null else context.resources.getStringArray(
            R.array.dolby_profile_entries
        )[profileIndex]
    }

    fun resetProfileSpecificSettings(profile: Int = this.profile) {
        dlog(TAG, "resetProfileSpecificSettings($profile)")
        checkEffect()
        dolbyEffect.resetProfileSpecificSettings(profile)
        context.deleteSharedPreferences("profile_$profile")
    }

    fun resetAllProfiles() {
        dlog(TAG, "resetAllProfiles")
        checkEffect()
        // Reset profile-specific settings
        context.resources.getStringArray(R.array.dolby_profile_values)
            .map { it.toInt() }
            .forEach { resetProfileSpecificSettings(it) }
        // Set default dynamic profile
        profile = 0
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(DolbyConstants.PREF_PROFILE, "0")
            .apply()
    }

    fun getPreset(profile: Int = this.profile): String {
        val gains = dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
        return gains.joinToString(separator = ",").also {
            dlog(TAG, "getPreset: $it")
        }
    }

    fun setPreset(value: String, profile: Int = this.profile) {
        dlog(TAG, "setPreset: $value")
        checkEffect()
        val gains = value.split(",")
                .map { it.toInt() }
                .toIntArray()
        dolbyEffect.setDapParameter(DsParam.GEQ_BAND_GAINS, gains, profile)
    }

    fun getPresetName(): String {
        val preset = getPreset()
        val presets = context.resources.getStringArray(R.array.dolby_preset_values)
        val userPresets = context.getSharedPreferences(PREF_PRESETS, Context.MODE_PRIVATE)
        return if (presets.contains(preset)) {
            context.resources.getStringArray(R.array.dolby_preset_entries)[presets.indexOf(preset)]
        } else {
            userPresets.all.entries.firstOrNull { it.value == preset }?.key
                ?: context.getString(R.string.dolby_preset_custom)
        }
    }

    fun getHeadphoneVirtEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER, profile).also {
            dlog(TAG, "getHeadphoneVirtEnabled: $it")
        }

    fun setHeadphoneVirtEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setHeadphoneVirtEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, value, profile)
    }

    fun getSpeakerVirtEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.SPEAKER_VIRTUALIZER, profile).also {
            dlog(TAG, "getSpeakerVirtEnabled: $it")
        }

    fun setSpeakerVirtEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setSpeakerVirtEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.SPEAKER_VIRTUALIZER, value, profile)
    }

    fun getBassEnhancerEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.BASS_ENHANCER_ENABLE, profile).also {
            dlog(TAG, "getBassEnhancerEnabled: $it")
        }

    fun setBassEnhancerEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setBassEnhancerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.BASS_ENHANCER_ENABLE, value, profile)
    }

    fun getVolumeLevelerEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.VOLUME_LEVELER_ENABLE, profile).also {
            dlog(TAG, "getVolumeLevelerEnabled: $it")
        }

    fun setVolumeLevelerEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setVolumeLevelerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.VOLUME_LEVELER_ENABLE, value, profile)
    }

    fun getStereoWideningAmount(profile: Int = this.profile) =
        if (!stereoWideningSupported) {
            0
        } else {
            dolbyEffect.getDapParameterInt(DsParam.STEREO_WIDENING_AMOUNT, profile).also {
                dlog(TAG, "getStereoWideningAmount: $it")
            }
        }

    fun setStereoWideningAmount(value: Int, profile: Int = this.profile) {
        if (!stereoWideningSupported) return
        dlog(TAG, "setStereoWideningAmount: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, value, profile)
    }

    fun getDialogueEnhancerEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, profile).also {
            dlog(TAG, "getDialogueEnhancerEnabled: $it")
        }

    fun setDialogueEnhancerEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setDialogueEnhancerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, value, profile)
    }

    fun getDialogueEnhancerAmount(profile: Int = this.profile) =
        dolbyEffect.getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, profile).also {
            dlog(TAG, "getDialogueEnhancerAmount: $it")
        }

    fun setDialogueEnhancerAmount(value: Int, profile: Int = this.profile) {
        dlog(TAG, "setDialogueEnhancerAmount: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, value, profile)
    }

    fun getIeqPreset(profile: Int = this.profile) =
        dolbyEffect.getDapParameterInt(DsParam.IEQ_PRESET, profile).also {
            dlog(TAG, "getIeqPreset: $it")
        }

    fun setIeqPreset(value: Int, profile: Int = this.profile) {
        dlog(TAG, "setIeqPreset: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.IEQ_PRESET, value, profile)
    }

    companion object {
        private const val TAG = "DolbyController"
        private const val EFFECT_PRIORITY = 100
        private const val PREF_PRESETS = "presets"
        private const val PREF_KEY_PRESETS_MIGRATED = "presets_migrated"

        @Volatile
        private var instance: DolbyController? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DolbyController(context).also { instance = it }
            }
    }
}

package com.halil.ozel.exoplayerdemo

import android.content.Context
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import java.nio.charset.StandardCharsets

object DrmHelper {

    @OptIn(UnstableApi::class)
    fun createMediaSource(
        context: Context,
        streamUrl: String,
        licenseType: String?,
        licenseKey: String?,
        httpHeaders: Map<String, String> = emptyMap()
    ): DashMediaSource? {
        if (streamUrl.isBlank() || !streamUrl.contains(".mpd")) {
            return null
        }

        val drmConfigurationBuilder = MediaItem.DrmConfiguration.Builder(getDrmUuid(licenseType))

        var drmSessionManager: DrmSessionManager? = null
        val uuid = getDrmUuid(licenseType)

        if (licenseType != null && licenseKey != null) {
            when (licenseType.lowercase()) {
                "clearkey" -> {
                    drmSessionManager = createClearKeyDrmSessionManager(licenseKey, uuid)
                }
                "widevine" -> {
                    if (licenseKey.startsWith("http")) {
                        drmConfigurationBuilder.setLicenseUri(licenseKey)
                        if (httpHeaders.isNotEmpty()) {
                            drmConfigurationBuilder.setLicenseRequestHeaders(httpHeaders)
                        }
                    } else {
                        drmConfigurationBuilder.setLicenseUri(licenseKey)
                    }
                }
            }
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(httpHeaders["User-Agent"] ?: "ExoPlayer")
            .setAllowCrossProtocolRedirects(true)

        if (httpHeaders.isNotEmpty()) {
            val headersToApply = httpHeaders.filter { it.key.equals("Cookie", ignoreCase = true) }
            if (headersToApply.isNotEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(headersToApply)
            }
        }

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType(MimeTypes.APPLICATION_MPD)

        if (drmSessionManager != null) {
            val drmConfig = MediaItem.DrmConfiguration.Builder(uuid)
                .build()
            mediaItemBuilder.setDrmConfiguration(drmConfig)
        } else if (licenseType != null && licenseKey != null) {
            val drmConfig = drmConfigurationBuilder.build()
            mediaItemBuilder.setDrmConfiguration(drmConfig)
        }

        val mediaItem = mediaItemBuilder.build()

        return if (drmSessionManager != null) {
            DashMediaSource.Factory(httpDataSourceFactory)
                .setDrmSessionManagerProvider { drmSessionManager }
                .createMediaSource(mediaItem)
        } else {
            DashMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(mediaItem)
        }
    }

    private fun getDrmUuid(licenseType: String?): java.util.UUID {
        return when (licenseType?.lowercase()) {
            "clearkey" -> C.CLEARKEY_UUID
            "widevine" -> C.WIDEVINE_UUID
            else -> C.WIDEVINE_UUID
        }
    }

    @OptIn(UnstableApi::class)
    private fun createClearKeyDrmSessionManager(licenseKey: String, uuid: java.util.UUID): DrmSessionManager {
        val clearKeyJson: String

        if (licenseKey.startsWith("http")) {
            return DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(HttpMediaDrmCallback(licenseKey, DefaultHttpDataSource.Factory()))
        }

        if (licenseKey.contains(":")) {
            val parts = licenseKey.split(":")
            if (parts.size == 2) {
                val kidHex = parts[0].trim()
                val keyHex = parts[1].trim()
                clearKeyJson = buildClearKeyJson(kidHex, keyHex)
            } else {
                clearKeyJson = licenseKey
            }
        } else {
            clearKeyJson = licenseKey
        }

        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .build(LocalMediaDrmCallback(clearKeyJson.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun buildClearKeyJson(kidHex: String, keyHex: String): String {
        val kidBase64 = hexToBase64(kidHex)
        val keyBase64 = hexToBase64(keyHex)
        return """{"keys":[{"kty":"oct","k":"$keyBase64","kid":"$kidBase64"}],"type":"temporary"}"""
    }

    private fun hexToBase64(hex: String): String {
        val hexClean = hex.replace(" ", "").replace("-", "")
        val bytes = hexClean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun buildHttpHeaders(headers: Map<String, String>): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        for ((key, value) in headers) {
            if (key.equals("cookie", ignoreCase = true)) {
                result["Cookie"] = value
            } else if (key.equals("user-agent", ignoreCase = true)) {
                result["User-Agent"] = value
            } else {
                result[key] = value
            }
        }
        return result
    }
}
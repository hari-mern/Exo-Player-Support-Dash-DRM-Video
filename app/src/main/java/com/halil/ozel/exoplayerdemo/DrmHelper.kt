package com.halil.ozel.exoplayerdemo

import android.content.Context
import android.util.Base64
import android.util.Log
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
    private const val TAG = "DrmHelper"

    @OptIn(UnstableApi::class)
    fun createMediaSource(
        context: Context,
        streamUrl: String,
        licenseType: String?,
        licenseKey: String?,
        httpHeaders: Map<String, String> = emptyMap()
    ): DashMediaSource? {
        Log.d(TAG, "createMediaSource called")
        Log.d(TAG, "  Stream URL: $streamUrl")
        Log.d(TAG, "  License Type: $licenseType")
        Log.d(TAG, "  License Key: $licenseKey")
        Log.d(TAG, "  HTTP Headers: $httpHeaders")

        // Check if it's a valid DASH stream URL (either .mpd or returns MPD content)
        // servertvhub uses PHP endpoints like mpd.php?id=xxx
        val isDashUrl = streamUrl.contains(".mpd") || 
                        streamUrl.contains("mpd.php") || 
                        streamUrl.contains("manifest.mpd")
        
        if (streamUrl.isBlank() || !isDashUrl) {
            Log.e(TAG, "Invalid stream URL or not a DASH file")
            return null
        }

        val drmConfigurationBuilder = MediaItem.DrmConfiguration.Builder(getDrmUuid(licenseType))

        var drmSessionManager: DrmSessionManager? = null
        val uuid = getDrmUuid(licenseType)

        Log.d(TAG, "========== START DrmHelper.createMediaSource DRM handling ==========")
        Log.d(TAG, "licenseType: $licenseType")
        Log.d(TAG, "licenseKey: $licenseKey")
        Log.d(TAG, "httpHeaders: $httpHeaders")
        
        if (licenseType != null && licenseKey != null) {
            when (licenseType.lowercase()) {
                "clearkey" -> {
                    Log.d(TAG, "Processing clearkey DRM...")
                    val keyResult = LicenseKeyHandler.processLicenseKey(licenseKey, httpHeaders)
                    when (keyResult) {
                        is LicenseKeyHandler.LicenseKeyResult.Success -> {
                            Log.d(TAG, "SUCCESS: License key processed - KID: ${keyResult.kidHex}, KEY: ${keyResult.keyHex}")
                            Log.d(TAG, "ClearKey JSON: ${keyResult.clearKeyJson}")
                            drmSessionManager = createClearKeyDrmSessionManagerFromHex(keyResult.kidHex, keyResult.keyHex, uuid)
                            Log.d(TAG, "Created DRM session manager from hex keys")
                        }
                        is LicenseKeyHandler.LicenseKeyResult.Error -> {
                            Log.e(TAG, "ERROR: Failed to process license key: ${keyResult.message}")
                            Log.d(TAG, "Falling back to createClearKeyDrmSessionManager with raw key")
                            drmSessionManager = createClearKeyDrmSessionManager(licenseKey, uuid)
                        }
                    }
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
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)

        // Apply ALL headers to the data source (including Referer, Origin for MPD content)
        if (httpHeaders.isNotEmpty()) {
            val requestProperties = mutableMapOf<String, String>()
            for ((key, value) in httpHeaders) {
                when (key.lowercase()) {
                    "referer" -> requestProperties["Referer"] = value
                    "origin" -> requestProperties["Origin"] = value
                    "cookie" -> requestProperties["Cookie"] = value
                    "user-agent" -> {} // Already set via setUserAgent
                    else -> requestProperties[key] = value
                }
            }
            
            // Add additional headers that server might require
            requestProperties["Accept"] = "*/*"
            requestProperties["Accept-Encoding"] = "identity"
            
            if (requestProperties.isNotEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(requestProperties)
                Log.d(TAG, "Applied request properties to data source: $requestProperties")
            }
        }

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType(MimeTypes.APPLICATION_MPD)

        if (drmSessionManager != null) {
            // Use our custom drmSessionManager - also need to set license URI for DRM
            val drmConfigBuilder = MediaItem.DrmConfiguration.Builder(uuid)
            // For ClearKey with custom session manager, set the key sets (license data)
            if (licenseKey != null) {
                // If license key is URL, the drmSessionManager already handles fetching
                // If it's inline keys, we need to set the key set
                if (!licenseKey.startsWith("http")) {
                    // For inline keys, we could set licenseUri but our session manager has the keys
                }
            }
            val drmConfig = drmConfigBuilder.build()
            mediaItemBuilder.setDrmConfiguration(drmConfig)
            Log.d(TAG, "Using custom drmSessionManager for MediaItem")
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

    @OptIn(UnstableApi::class)
    private fun createClearKeyDrmSessionManagerFromJson(clearKeyJson: String, uuid: java.util.UUID): DrmSessionManager {
        Log.d(TAG, "Creating ClearKey DRM session from JSON")
        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .build(LocalMediaDrmCallback(clearKeyJson.toByteArray(StandardCharsets.UTF_8)))
    }

    @OptIn(UnstableApi::class)
    private fun createClearKeyDrmSessionManagerFromHex(kidHex: String, keyHex: String, uuid: java.util.UUID): DrmSessionManager {
        Log.d(TAG, "========== START createClearKeyDrmSessionManagerFromHex ==========")
        Log.d(TAG, "KID (hex): $kidHex")
        Log.d(TAG, "KEY (hex): $keyHex")
        Log.d(TAG, "UUID: $uuid")
        
        val clearKeyJson = buildClearKeyJson(kidHex, keyHex)
        Log.d(TAG, "Built ClearKey JSON: $clearKeyJson")
        
        val callback = LocalMediaDrmCallback(clearKeyJson.toByteArray(StandardCharsets.UTF_8))
        Log.d(TAG, "Created LocalMediaDrmCallback")
        
        val drmManagerBuilder = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
        Log.d(TAG, "Built DefaultDrmSessionManager.Builder")
        
        val drmSessionManager = drmManagerBuilder.build(callback)
        Log.d(TAG, "Built DrmSessionManager")
        
        Log.d(TAG, "========== END createClearKeyDrmSessionManagerFromHex ==========")
        return drmSessionManager
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
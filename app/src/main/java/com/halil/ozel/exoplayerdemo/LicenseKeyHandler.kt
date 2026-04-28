package com.halil.ozel.exoplayerdemo

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.net.URL
import java.nio.charset.StandardCharsets

object LicenseKeyHandler {
    private const val TAG = "LicenseKeyHandler"

    sealed class LicenseKeyResult {
        data class Success(val kidHex: String, val keyHex: String, val clearKeyJson: String) : LicenseKeyResult()
        data class Error(val message: String) : LicenseKeyResult()
    }

    fun processLicenseKey(licenseKey: String?): LicenseKeyResult {
        if (licenseKey.isNullOrBlank()) {
            return LicenseKeyResult.Error("License key is null or blank")
        }

        Log.d(TAG, "Processing license key: ${licenseKey.take(50)}...")

        return when {
            // Format 1: URL (http/https) - fetch from license server
            licenseKey.startsWith("http://") || licenseKey.startsWith("https://") -> {
                fetchKeysFromServer(licenseKey)
            }

            // Format 2: kid_hex:key_hex (e.g., "400131994b445d8c8817202248760fda:2d56cb6f07a75b9aff165d534ae2bfc4")
            licenseKey.contains(":") && !licenseKey.startsWith("http") -> {
                parseInlineKeys(licenseKey)
            }

            // Format 3: Already JSON format (direct ClearKey JSON)
            licenseKey.startsWith("{") -> {
                parseJsonKeys(licenseKey)
            }

            else -> {
                LicenseKeyResult.Error("Unknown license key format: ${licenseKey.take(30)}")
            }
        }
    }

    private fun fetchKeysFromServer(url: String): LicenseKeyResult {
        Log.d(TAG, "Fetching keys from license server: $url")
        return try {
            val response = URL(url).readText()
            Log.d(TAG, "License server response: $response")

            // Parse JSON response
            val json = JSONObject(response)

            if (json.has("keys") && json.getJSONArray("keys").length() > 0) {
                val keysArray = json.getJSONArray("keys")
                val firstKey = keysArray.getJSONObject(0)

                val kidBase64 = firstKey.getString("kid")
                val keyBase64 = firstKey.getString("k")

                // Convert base64 to hex
                val kidBytes = Base64.decode(kidBase64, Base64.NO_WRAP)
                val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)

                val kidHex = kidBytes.toHexString()
                val keyHex = keyBytes.toHexString()

                Log.d(TAG, "Extracted - kid (hex): $kidHex, key (hex): $keyHex")

                // Generate ClearKey JSON
                val clearKeyJson = """{"keys":[{"kty":"oct","k":"$keyBase64","kid":"$kidBase64"}],"type":"temporary"}"""

                LicenseKeyResult.Success(kidHex, keyHex, clearKeyJson)
            } else {
                LicenseKeyResult.Error("No keys found in license server response")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch keys from server: ${e.message}")
            LicenseKeyResult.Error("Failed to fetch keys: ${e.message}")
        }
    }

    private fun parseInlineKeys(input: String): LicenseKeyResult {
        Log.d(TAG, "Parsing inline keys: $input")
        return try {
            val parts = input.split(":")
            if (parts.size == 2) {
                var kidHex = parts[0].trim()
                var keyHex = parts[1].trim()

                // Check if they're already hex or base64
                kidHex = normalizeHex(kidHex)
                keyHex = normalizeHex(keyHex)

                Log.d(TAG, "Parsed - kid: $kidHex, key: $keyHex")

                // Convert to base64 for ClearKey JSON
                val kidBytes = hexToBytes(kidHex)
                val keyBytes = hexToBytes(keyHex)
                val kidBase64 = Base64.encodeToString(kidBytes, Base64.NO_WRAP)
                val keyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)

                val clearKeyJson = """{"keys":[{"kty":"oct","k":"$keyBase64","kid":"$kidBase64"}],"type":"temporary"}"""

                LicenseKeyResult.Success(kidHex, keyHex, clearKeyJson)
            } else {
                LicenseKeyResult.Error("Invalid inline key format. Expected: kid:key")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse inline keys: ${e.message}")
            LicenseKeyResult.Error("Failed to parse keys: ${e.message}")
        }
    }

    private fun parseJsonKeys(jsonStr: String): LicenseKeyResult {
        Log.d(TAG, "Parsing JSON keys: ${jsonStr.take(100)}")
        return try {
            val json = JSONObject(jsonStr)

            if (json.has("keys") && json.getJSONArray("keys").length() > 0) {
                val keysArray = json.getJSONArray("keys")
                val firstKey = keysArray.getJSONObject(0)

                val kidBase64 = firstKey.getString("kid")
                val keyBase64 = firstKey.getString("k")

                // Convert base64 to hex
                val kidBytes = Base64.decode(kidBase64, Base64.NO_WRAP)
                val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)

                val kidHex = kidBytes.toHexString()
                val keyHex = keyBytes.toHexString()

                Log.d(TAG, "Parsed JSON - kid (hex): $kidHex, key (hex): $keyHex")

                LicenseKeyResult.Success(kidHex, keyHex, jsonStr)
            } else {
                LicenseKeyResult.Error("No keys found in JSON")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON keys: ${e.message}")
            LicenseKeyResult.Error("Failed to parse JSON: ${e.message}")
        }
    }

    private fun normalizeHex(hex: String): String {
        // If it looks like base64 (contains + / =), decode it first
        return try {
            if (hex.contains("+") || hex.contains("/") || hex.contains("=")) {
                val bytes = Base64.decode(hex, Base64.NO_WRAP)
                bytes.toHexString()
            } else {
                // Remove any spaces or dashes
                hex.replace(" ", "").replace("-", "")
            }
        } catch (e: Exception) {
            // If not base64, return as-is (assuming it's already hex)
            hex.replace(" ", "").replace("-", "")
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("-", "")
        return cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
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

    fun processLicenseKey(licenseKey: String?, httpHeaders: Map<String, String> = emptyMap()): LicenseKeyResult {
        Log.d(TAG, "========== START processLicenseKey ==========")
        Log.d(TAG, "License key: ${licenseKey?.take(80)}...")
        Log.d(TAG, "HTTP Headers: $httpHeaders")
        
        if (licenseKey.isNullOrBlank()) {
            Log.e(TAG, "License key is null or blank!")
            return LicenseKeyResult.Error("License key is null or blank")
        }

        val result = when {
            // Format 1: URL (http/https) - fetch from license server
            licenseKey.startsWith("http://") || licenseKey.startsWith("https://") -> {
                Log.d(TAG, "Detected URL format, calling fetchKeysFromServer")
                fetchKeysFromServer(licenseKey, httpHeaders)
            }

            // Format 2: kid_hex:key_hex (e.g., "400131994b445d8c8817202248760fda:2d56cb6f07a75b9aff165d534ae2bfc4")
            licenseKey.contains(":") && !licenseKey.startsWith("http") -> {
                Log.d(TAG, "Detected inline hex format, calling parseInlineKeys")
                parseInlineKeys(licenseKey)
            }

            // Format 3: Already JSON format (direct ClearKey JSON)
            licenseKey.startsWith("{") -> {
                Log.d(TAG, "Detected JSON format, calling parseJsonKeys")
                parseJsonKeys(licenseKey)
            }

            else -> {
                Log.e(TAG, "Unknown license key format: ${licenseKey.take(30)}")
                LicenseKeyResult.Error("Unknown license key format: ${licenseKey.take(30)}")
            }
        }
        
        Log.d(TAG, "processLicenseKey result: $result")
        Log.d(TAG, "========== END processLicenseKey ==========")
        return result
    }

    private fun fetchKeysFromServer(url: String, httpHeaders: Map<String, String> = emptyMap()): LicenseKeyResult {
        Log.d(TAG, "========== START fetchKeysFromServer ==========")
        Log.d(TAG, "URL: $url")
        Log.d(TAG, "HTTP Headers: $httpHeaders")
        
        var result: LicenseKeyResult = LicenseKeyResult.Error("Failed to fetch keys - default error")
        
        try {
            val thread = Thread {
                try {
                    // Try GET first
                    Log.d(TAG, "Trying GET method...")
                    val connection = URL(url).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    
                    val userAgent = httpHeaders["User-Agent"] ?: "plaYtv/7.1.3 (Linux;Android 14) ExoPlayerLib/2.11.7"
                    val referer = httpHeaders["Referer"] ?: "https://www.jiotv.com/"
                    
                    connection.setRequestProperty("User-Agent", userAgent)
                    connection.setRequestProperty("Referer", referer)
                    connection.setRequestProperty("Origin", "https://www.jiotv.com")
                    
                    var responseCode = connection.responseCode
                    Log.d(TAG, "License server response code: $responseCode")
                    
                    // If GET returns 405 or other error, try POST
                    if (responseCode != 200) {
                        Log.d(TAG, "GET returned $responseCode, trying POST method...")
                        connection.disconnect()
                        
                        val postConnection = URL(url).openConnection() as java.net.HttpURLConnection
                        postConnection.requestMethod = "POST"
                        postConnection.connectTimeout = 15000
                        postConnection.readTimeout = 15000
                        postConnection.doOutput = true
                        postConnection.setRequestProperty("User-Agent", userAgent)
                        postConnection.setRequestProperty("Referer", referer)
                        postConnection.setRequestProperty("Origin", "https://www.jiotv.com")
                        postConnection.setRequestProperty("Content-Type", "application/json")
                        postConnection.setRequestProperty("Accept", "application/json")
                        
                        responseCode = postConnection.responseCode
                        Log.d(TAG, "POST response code: $responseCode")
                        
                        if (responseCode == 200) {
                            val response = postConnection.inputStream.bufferedReader().readText()
                            result = parseLicenseResponse(response)
                        } else {
                            result = LicenseKeyResult.Error("License server returned code: $responseCode")
                        }
                    } else {
                        val response = connection.inputStream.bufferedReader().readText()
                        result = parseLicenseResponse(response)
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message ?: e.toString()
                    Log.e(TAG, "Exception in fetchKeysFromServer thread: $errorMsg", e)
                    result = LicenseKeyResult.Error("Failed to fetch keys: $errorMsg")
                }
            }
            
            thread.start()
            thread.join(30000)
            
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.toString()
            Log.e(TAG, "Thread error: $errorMsg", e)
            result = LicenseKeyResult.Error("Thread error: $errorMsg")
        }
        
        Log.d(TAG, "========== END fetchKeysFromServer, result: $result ==========")
        return result
    }
    
    private fun parseLicenseResponse(response: String): LicenseKeyResult {
        Log.d(TAG, "License server response length: ${response.length}")
        Log.d(TAG, "License server response: $response")
        
        return try {
            val json = JSONObject(response)
            if (json.has("keys") && json.getJSONArray("keys").length() > 0) {
                val keysArray = json.getJSONArray("keys")
                val firstKey = keysArray.getJSONObject(0)
                val kidBase64 = firstKey.getString("kid")
                val keyBase64 = firstKey.getString("k")
                
                val kidBytes = Base64.decode(kidBase64, Base64.NO_WRAP)
                val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
                val kidHex = kidBytes.toHexString()
                val keyHex = keyBytes.toHexString()
                
                val clearKeyJson = """{"keys":[{"kty":"oct","k":"$keyBase64","kid":"$kidBase64"}],"type":"temporary"}"""
                LicenseKeyResult.Success(kidHex, keyHex, clearKeyJson)
            } else {
                LicenseKeyResult.Error("No keys found in license server response")
            }
        } catch (e: Exception) {
            LicenseKeyResult.Error("Failed to parse license response: ${e.message}")
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
package com.halil.ozel.exoplayerdemo

import java.util.regex.Pattern

data class ChannelInfo(
    val name: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgLogo: String? = null,
    val groupTitle: String? = null,
    val streamUrl: String? = null,
    val licenseType: String? = null,
    val licenseKey: String? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
    val userAgent: String? = null
)

object StreamParser {

    fun parseM3U8(content: String): List<ChannelInfo> {
        val entries = mutableListOf<ChannelInfo>()
        val lines = content.split("\n")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF:")) {
                val entry = parseM3U8Entry(lines, i)
                if (entry.streamUrl != null) {
                    entries.add(entry)
                }
            }
            i++
        }
        return entries
    }

    private fun parseM3U8Entry(lines: List<String>, startIndex: Int): ChannelInfo {
        val extInfLine = lines[startIndex]
        if (!extInfLine.startsWith("#EXTINF:")) {
            return ChannelInfo()
        }

        var channelName: String? = null
        var tvgId: String? = null
        var tvgName: String? = null
        var tvgLogo: String? = null
        var groupTitle: String? = null
        var streamUrl: String? = null
        var licenseType: String? = null
        var licenseKey: String? = null
        var userAgent: String? = null
        val httpHeaders = mutableMapOf<String, String>()

        val attrsPart = extInfLine.removePrefix("#EXTINF:")
        val commaIndex = attrsPart.indexOf(',')
        if (commaIndex >= 0) {
            val attrs = attrsPart.substring(0, commaIndex)
            channelName = attrsPart.substring(commaIndex + 1).trim()

            tvgId = extractAttribute(attrs, "tvg-id")
            tvgName = extractAttribute(attrs, "tvg-name")
            tvgLogo = extractAttribute(attrs, "tvg-logo")
            groupTitle = extractAttribute(attrs, "group-title")
        }

        var i = startIndex + 1
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("#KODIPROP:") -> {
                    val prop = line.removePrefix("#KODIPROP:")
                    when {
                        prop.contains("inputstream.adaptive.license_type") -> {
                            val match = Pattern.compile("license_type=(\\w+)").matcher(prop)
                            if (match.find()) {
                                licenseType = match.group(1)
                            }
                        }
                        prop.contains("inputstream.adaptive.license_key") -> {
                            val match = Pattern.compile("license_key=(.+)").matcher(prop)
                            if (match.find()) {
                                licenseKey = match.group(1).trim()
                            }
                        }
                    }
                }
                line.startsWith("#EXTHTTP:") -> {
                    val jsonStr = line.removePrefix("#EXTHTTP:")
                    try {
                        val json = parseSimpleJson(jsonStr)
                        httpHeaders.putAll(json)
                    } catch (e: Exception) {
                    }
                }
                line.startsWith("#EXTVLCOPT:") -> {
                    val opt = line.removePrefix("#EXTVLCOPT:")
                    if (opt.contains("http-user-agent")) {
                        val match = Pattern.compile("http-user-agent=(.+)$").matcher(opt)
                        if (match.find()) {
                            userAgent = match.group(1).trim()
                        }
                    }
                }
                !line.startsWith("#") && line.isNotBlank() -> {
                    // Check if URL contains pipe separator for combined MPD + license
                    val parsedUrl = parsePipeSeparatedUrl(line)
                    streamUrl = parsedUrl.first
                    if (parsedUrl.second != null && licenseType == null) {
                        licenseType = parsedUrl.second
                    }
                    if (parsedUrl.third != null && licenseKey == null) {
                        licenseKey = parsedUrl.third
                    }
                    break
                }
            }
            i++
        }

        return ChannelInfo(
            name = channelName,
            tvgId = tvgId,
            tvgName = tvgName,
            tvgLogo = tvgLogo,
            groupTitle = groupTitle,
            streamUrl = streamUrl,
            licenseType = licenseType,
            licenseKey = licenseKey,
            httpHeaders = httpHeaders,
            userAgent = userAgent
        )
    }

    private fun extractAttribute(attrs: String, attrName: String): String? {
        val pattern = Pattern.compile("$attrName=\"([^\"]*)\"")
        val matcher = pattern.matcher(attrs)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun parseSimpleJson(jsonStr: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val cleanJson = jsonStr.trim().removePrefix("{").removeSuffix("}")
        val pairs = cleanJson.split(",")
        for (pair in pairs) {
            val colonIndex = pair.indexOf(':')
            if (colonIndex > 0) {
                val key = pair.substring(0, colonIndex).trim().removeSurrounding("\"")
                val value = pair.substring(colonIndex + 1).trim().removeSurrounding("\"")
                result[key] = value
            }
        }
        return result
    }

    // Parse URL format: "mpd_url|license_type=xxx&license_key=xxx"
    // Returns Triple<streamUrl, licenseType, licenseKey>
    private fun parsePipeSeparatedUrl(url: String): Triple<String?, String?, String?> {
        if (!url.contains("|")) {
            return Triple(url, null, null)
        }

        val parts = url.split("|")
        val streamUrl = parts[0].trim()

        var licenseType: String? = null
        var licenseKey: String? = null

        // Parse the params part (e.g., "license_type=clearkey&license_key=...")
        if (parts.size > 1) {
            val paramsPart = parts[1]
            val paramPairs = paramsPart.split("&")
            for (pair in paramPairs) {
                val keyValue = pair.split("=", limit = 2)
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim()
                    val value = keyValue[1].trim()
                    when (key.lowercase()) {
                        "license_type" -> licenseType = value
                        "license_key" -> licenseKey = value
                    }
                }
            }
        }

        return Triple(streamUrl, licenseType, licenseKey)
    }
}
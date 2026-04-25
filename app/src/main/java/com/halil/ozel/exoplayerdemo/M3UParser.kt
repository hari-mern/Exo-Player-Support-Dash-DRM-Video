package com.halil.ozel.exoplayerdemo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object M3UParser {

    suspend fun parse(playlistUrl: String): List<M3UChannel> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<M3UChannel>()
        try {
            val content = URL(playlistUrl).readText()
            parseContent(content)
        } catch (e: Exception) {
            e.printStackTrace()
            channels
        }
    }

    private fun parseContent(content: String): List<M3UChannel> {
        val channels = mutableListOf<M3UChannel>()
        val pattern = Regex("#EXTINF:-1([^,]*),(.+)\\s*(http[\\s\\S]*?)(?=\\n#EXTINF|\\Z)")

        pattern.findAll(content).forEach { match ->
            val attributes = match.groupValues[1]
            val name = match.groupValues[2].trim()
            val streamUrl = match.groupValues[3].trim()

            val logo = Regex("tvg-logo=\"([^\"]*)\"").find(attributes)?.groupValues?.get(1)
            val group = Regex("group-title=\"([^\"]*)\"").find(attributes)?.groupValues?.get(1)

            if (streamUrl.startsWith("http")) {
                channels.add(M3UChannel(
                    name = name,
                    logoUrl = logo?.ifEmpty { null },
                    group = group?.ifEmpty { null },
                    streamUrl = streamUrl
                ))
            }
        }
        return channels
    }
}
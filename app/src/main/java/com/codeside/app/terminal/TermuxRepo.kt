package com.codeside.app.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.util.zip.GZIPInputStream

class TermuxRepo(
    private val baseUrl: String = "https://packages.termux.dev/apt/termux-main",
) {
    private val http = OkHttpClient()

    suspend fun findPackageUrl(packageName: String, androidAbi: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val arch = when {
                androidAbi.contains("arm64") || androidAbi == "aarch64" -> "aarch64"
                androidAbi.contains("armeabi") || androidAbi == "arm" -> "arm"
                androidAbi.contains("x86_64") -> "x86_64"
                androidAbi.contains("x86") -> "i686"
                else -> androidAbi
            }
            val packagesUrl = "$baseUrl/dists/stable/main/binary-$arch/Packages"
            val body = fetchText(packagesUrl)
            val filename = parseFilename(body, packageName)
                ?: throw IOException("Package '$packageName' not found in $packagesUrl")
            "$baseUrl/$filename"
        }
    }

    private fun fetchText(url: String): String {
        val req = Request.Builder().url(url).header("Accept-Encoding", "identity").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
            val body = resp.body ?: throw IOException("Empty response: $url")
            return body.string()
        }
    }

    private fun parseFilename(packages: String, name: String): String? {
        var currentName: String? = null
        var currentFilename: String? = null
        fun flush(): String? {
            if (currentName == name) return currentFilename
            return null
        }
        packages.lineSequence().forEach { line ->
            if (line.isBlank()) {
                val hit = flush()
                if (hit != null) return hit
                currentName = null
                currentFilename = null
            } else if (line.startsWith("Package:")) {
                currentName = line.removePrefix("Package:").trim()
            } else if (line.startsWith("Filename:")) {
                currentFilename = line.removePrefix("Filename:").trim()
            }
        }
        return flush()
    }
}

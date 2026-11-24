package com.codeside.app.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class DebInstaller(private val env: TerminalEnvironment) {
    private val http = OkHttpClient()

    suspend fun installFromUrl(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            env.ensureDirectories()
            val tmp = File(env.tmpDir, "pkg-${System.currentTimeMillis()}.deb")
            download(url, tmp)
            val extracted = installDeb(tmp)
            tmp.delete()
            "Installed $extracted entries from $url"
        }
    }

    private fun download(url: String, out: File) {
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} downloading package")
            val body = resp.body ?: throw IOException("Empty package response")
            FileOutputStream(out).use { fos -> body.byteStream().copyTo(fos) }
        }
    }

    private fun installDeb(debFile: File): Int {
        debFile.inputStream().buffered().use { fis ->
            ArArchiveInputStream(fis).use { ar ->
                var entry = ar.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith("data.tar")) {
                        return extractDataTar(ar, name, env.prefixDir)
                    }
                    entry = ar.nextEntry
                }
            }
        }
        throw IOException("No data.tar found in deb")
    }

    private fun extractDataTar(ar: ArArchiveInputStream, name: String, prefix: File): Int {
        val wrapped: InputStream = when {
            name.endsWith(".gz") -> GzipCompressorInputStream(BufferedInputStream(ar))
            name.endsWith(".xz") -> XZCompressorInputStream(BufferedInputStream(ar))
            name == "data.tar" -> BufferedInputStream(ar)
            else -> throw IOException("Unsupported data archive: $name")
        }
        var count = 0
        TarArchiveInputStream(wrapped).use { tar ->
            var te: TarArchiveEntry? = tar.nextEntry
            while (te != null) {
                extractTarEntry(tar, te, prefix)
                count++
                te = tar.nextEntry
            }
        }
        return count
    }

    private fun extractTarEntry(tar: TarArchiveInputStream, entry: TarArchiveEntry, prefix: File) {
        val name = entry.name.removePrefix("./").removePrefix("/")
        if (name.isBlank()) return
        val out = safeResolve(prefix, name)

        if (entry.isDirectory) {
            out.mkdirs()
            return
        }

        if (entry.isSymbolicLink) {
            out.parentFile?.mkdirs()
            try { if (out.exists()) out.delete() } catch (_: Throwable) {}
            try { android.system.Os.symlink(entry.linkName, out.absolutePath) } catch (_: Throwable) {}
            return
        }

        if (entry.isLink) {
            out.parentFile?.mkdirs()
            val target = safeResolve(prefix, entry.linkName.removePrefix("./").removePrefix("/"))
            try { if (out.exists()) out.delete() } catch (_: Throwable) {}
            try { java.nio.file.Files.createLink(out.toPath(), target.toPath()) } catch (_: Throwable) {
                try { target.copyTo(out, overwrite = true) } catch (_: Throwable) {}
            }
            return
        }

        if (!entry.isFile) return
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { fos -> tar.copyTo(fos) }
        if (entry.mode and 0b001_000_000 != 0 || entry.mode and 0b000_001_000 != 0 || entry.mode and 0b000_000_001 != 0) {
            out.setExecutable(true, false)
        }
        out.setReadable(true, false)
    }

    private fun safeResolve(base: File, name: String): File {
        val target = File(base, name).canonicalFile
        val baseCanon = base.canonicalFile
        if (!target.path.startsWith(baseCanon.path + File.separator) && target.path != baseCanon.path) {
            throw IOException("Unsafe package entry: $name")
        }
        return target
    }
}

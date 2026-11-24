package com.codeside.app.agent

import java.io.File

class Workspace(val root: File) {
    init {
        root.mkdirs()
    }

    fun resolve(path: String): File {
        val clean = path.trim().ifBlank { "." }
        val target = File(root, clean).canonicalFile
        val rootCanon = root.canonicalFile
        if (target.path != rootCanon.path && !target.path.startsWith(rootCanon.path + File.separator)) {
            throw SecurityException("Path escapes workspace: $path")
        }
        return target
    }

    fun relative(file: File): String {
        val rootPath = root.canonicalFile.path
        val filePath = file.canonicalFile.path
        return if (filePath == rootPath) "." else filePath.removePrefix(rootPath + File.separator)
    }
}

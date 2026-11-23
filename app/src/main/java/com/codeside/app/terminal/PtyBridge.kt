package com.codeside.app.terminal

import java.io.IOException

object PtyBridge {
    init {
        System.loadLibrary("codeside-pty")
    }

    @Throws(IOException::class)
    external fun nativeStart(cmd: String, cwd: String?, envVars: Array<String>?): IntArray

    external fun nativeRead(fd: Int, buffer: ByteArray): Int
    @Throws(IOException::class)
    external fun nativeWrite(fd: Int, data: ByteArray)
    external fun nativeClose(fd: Int)
    external fun nativeWait(pid: Int): Int
    external fun nativeKill(pid: Int)
}

package com.codeside.app.terminal

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class TerminalSession(
    private val onOutput: (String) -> Unit,
    private val onExit: (Int) -> Unit,
) : Closeable {
    private var fd: Int = -1
    private var pid: Int = -1
    private val running = AtomicBoolean(false)

    fun start(cmd: String = "/system/bin/sh", cwd: String? = null, env: Array<String>? = null) {
        if (running.get()) return
        val result = PtyBridge.nativeStart(cmd, cwd, env)
        fd = result[0]
        pid = result[1]
        running.set(true)
        thread(name = "codeside-pty-read", isDaemon = true) { readLoop() }
        thread(name = "codeside-pty-wait", isDaemon = true) { waitLoop() }
    }

    private fun readLoop() {
        val buf = ByteArray(4096)
        while (running.get()) {
            val n = try {
                PtyBridge.nativeRead(fd, buf)
            } catch (_: Throwable) {
                -1
            }
            when {
                n > 0 -> onOutput(buf.copyOfRange(0, n).toString(Charsets.UTF_8))
                n == 0 -> Thread.sleep(20)
                else -> break
            }
        }
    }

    private fun waitLoop() {
        val code = try { PtyBridge.nativeWait(pid) } catch (_: Throwable) { -1 }
        running.set(false)
        try { PtyBridge.nativeClose(fd) } catch (_: Throwable) {}
        onExit(code)
    }

    fun write(text: String) {
        if (!running.get()) return
        try {
            PtyBridge.nativeWrite(fd, text.toByteArray(Charsets.UTF_8))
        } catch (_: Throwable) {
        }
    }

    override fun close() {
        running.set(false)
        try { PtyBridge.nativeKill(pid) } catch (_: Throwable) {}
        try { PtyBridge.nativeClose(fd) } catch (_: Throwable) {}
    }

    fun isRunning(): Boolean = running.get()
}

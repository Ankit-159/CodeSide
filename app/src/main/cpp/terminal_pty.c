#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>
#include <signal.h>

#define LOG_TAG "CodeSidePty"

static void throw_io(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/io/IOException");
    if (cls != NULL) {
        char buf[256];
        snprintf(buf, sizeof(buf), "%s: %s", msg, strerror(errno));
        (*env)->ThrowNew(env, cls, buf);
    }
}

static char *dup_jstring(JNIEnv *env, jstring s) {
    if (s == NULL) return NULL;
    const char *chars = (*env)->GetStringUTFChars(env, s, NULL);
    if (chars == NULL) return NULL;
    char *copy = strdup(chars);
    (*env)->ReleaseStringUTFChars(env, s, chars);
    return copy;
}

JNIEXPORT jintArray JNICALL
Java_com_codeside_app_terminal_PtyBridge_nativeStart(JNIEnv *env, jclass clazz, jstring cmd, jstring cwd, jobjectArray envVars) {
    (void)clazz;

    char *cmdStr = dup_jstring(env, cmd);
    char *cwdStr = dup_jstring(env, cwd);
    if (cmdStr == NULL) {
        free(cwdStr);
        throw_io(env, "missing command");
        return NULL;
    }

    jsize envCount = (envVars != NULL) ? (*env)->GetArrayLength(env, envVars) : 0;
    char **envStrings = NULL;
    if (envCount > 0) {
        envStrings = (char **)calloc((size_t)envCount + 1, sizeof(char *));
        if (envStrings == NULL) {
            free(cmdStr);
            free(cwdStr);
            throw_io(env, "alloc env failed");
            return NULL;
        }
        for (jsize i = 0; i < envCount; i++) {
            jstring entry = (jstring)(*env)->GetObjectArrayElement(env, envVars, i);
            envStrings[i] = dup_jstring(env, entry);
            if (entry != NULL) (*env)->DeleteLocalRef(env, entry);
        }
    }

    int masterFd = open("/dev/ptmx", O_RDWR | O_NOCTTY);
    if (masterFd < 0) {
        for (jsize i = 0; i < envCount; i++) free(envStrings ? envStrings[i] : NULL);
        free(envStrings);
        free(cmdStr);
        free(cwdStr);
        throw_io(env, "open /dev/ptmx failed");
        return NULL;
    }

    if (grantpt(masterFd) != 0 || unlockpt(masterFd) != 0) {
        close(masterFd);
        for (jsize i = 0; i < envCount; i++) free(envStrings ? envStrings[i] : NULL);
        free(envStrings);
        free(cmdStr);
        free(cwdStr);
        throw_io(env, "grantpt/unlockpt failed");
        return NULL;
    }

    char *pts = ptsname(masterFd);
    if (pts == NULL) {
        close(masterFd);
        for (jsize i = 0; i < envCount; i++) free(envStrings ? envStrings[i] : NULL);
        free(envStrings);
        free(cmdStr);
        free(cwdStr);
        throw_io(env, "ptsname failed");
        return NULL;
    }
    char slaveName[64];
    snprintf(slaveName, sizeof(slaveName), "%s", pts);

    pid_t pid = fork();
    if (pid < 0) {
        close(masterFd);
        for (jsize i = 0; i < envCount; i++) free(envStrings ? envStrings[i] : NULL);
        free(envStrings);
        free(cmdStr);
        free(cwdStr);
        throw_io(env, "fork failed");
        return NULL;
    }

    if (pid == 0) {
        setsid();
        int slaveFd = open(slaveName, O_RDWR);
        if (slaveFd < 0) _exit(127);
        ioctl(slaveFd, TIOCSCTTY, 0);
        dup2(slaveFd, STDIN_FILENO);
        dup2(slaveFd, STDOUT_FILENO);
        dup2(slaveFd, STDERR_FILENO);
        if (slaveFd > STDERR_FILENO) close(slaveFd);
        close(masterFd);

        if (cwdStr != NULL) {
            if (chdir(cwdStr) != 0) _exit(127);
        }

        for (jsize i = 0; i < envCount; i++) {
            if (envStrings && envStrings[i]) putenv(envStrings[i]);
        }
        setenv("TERM", "xterm-256color", 1);
        setenv("COLORTERM", "truecolor", 0);

        execl("/system/bin/sh", "sh", "-c", cmdStr, (char *)NULL);
        execl("/bin/sh", "sh", "-c", cmdStr, (char *)NULL);
        _exit(127);
    }

    close(-1); /* no-op, keeps structure clear */
    for (jsize i = 0; i < envCount; i++) free(envStrings ? envStrings[i] : NULL);
    free(envStrings);
    free(cmdStr);
    free(cwdStr);

    int flags = fcntl(masterFd, F_GETFL, 0);
    if (flags >= 0) fcntl(masterFd, F_SETFL, flags | O_NONBLOCK);

    jintArray result = (*env)->NewIntArray(env, 2);
    jint vals[2];
    vals[0] = (jint)masterFd;
    vals[1] = (jint)pid;
    (*env)->SetIntArrayRegion(env, result, 0, 2, vals);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_codeside_app_terminal_PtyBridge_nativeRead(JNIEnv *env, jclass clazz, jint fd, jbyteArray buffer) {
    (void)clazz;
    jsize len = (*env)->GetArrayLength(env, buffer);
    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, NULL);
    ssize_t n = read((int)fd, buf, (size_t)len);
    if (n < 0) {
        int err = errno;
        (*env)->ReleaseByteArrayElements(env, buffer, buf, JNI_ABORT);
        if (err == EAGAIN || err == EWOULDBLOCK) return 0;
        return -1;
    }
    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
    return (jint)n;
}

JNIEXPORT void JNICALL
Java_com_codeside_app_terminal_PtyBridge_nativeWrite(JNIEnv *env, jclass clazz, jint fd, jbyteArray data) {
    (void)clazz;
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    ssize_t written = 0;
    while (written < len) {
        ssize_t n = write((int)fd, buf + written, (size_t)(len - written));
        if (n < 0) {
            if (errno == EINTR) continue;
            (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
            throw_io(env, "write failed");
            return;
        }
        written += n;
    }
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_codeside_app_terminal_PtyBridge_nativeClose(JNIEnv *env, jclass clazz, jint fd) {
    (void)env;
    (void)clazz;
    if (fd >= 0) close((int)fd);
}

JNIEXPORT jint JNICALL
Java_com_codeside_app_terminal_PtyBridge_nativeWait(JNIEnv *env, jclass clazz, jint pid) {
    (void)env;
    (void)clazz;
    int status = 0;
    pid_t r = waitpid((pid_t)pid, &status, 0);
    if (r < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_codeside_app_terminal_PtyBridge_nativeKill(JNIEnv *env, jclass clazz, jint pid) {
    (void)env;
    (void)clazz;
    if (pid > 0) kill((pid_t)pid, SIGKILL);
}

JNIEXPORT void JNICALL
Java_com_codeside_app_terminal_PtyBridge_nativeResize(JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols) {
    (void)env;
    (void)clazz;
    struct winsize ws;
    ws.ws_row = (unsigned short)rows;
    ws.ws_col = (unsigned short)cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    ioctl((int)fd, TIOCSWINSZ, &ws);
}

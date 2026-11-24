#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>

static const char *TERMUX_PREFIX = "/data/data/com.termux/files/usr";
static const char *TERMUX_HOME = "/data/data/com.termux/files/home";

static int starts_with(const char *s, const char *prefix) {
    return strncmp(s, prefix, strlen(prefix)) == 0;
}

static const char *get_env(const char *key, const char *fallback) {
    const char *v = getenv(key);
    return (v && v[0]) ? v : fallback;
}

static char *rewrite_path(const char *path) {
    if (!path) return NULL;
    const char *prefix = get_env("PREFIX", NULL);
    const char *home = get_env("HOME", NULL);

    if (prefix && starts_with(path, TERMUX_PREFIX)) {
        size_t tailLen = strlen(path) - strlen(TERMUX_PREFIX);
        size_t outLen = strlen(prefix) + tailLen + 1;
        char *out = (char *)malloc(outLen);
        if (!out) return NULL;
        strcpy(out, prefix);
        strcat(out, path + strlen(TERMUX_PREFIX));
        return out;
    }
    if (home && starts_with(path, TERMUX_HOME)) {
        size_t tailLen = strlen(path) - strlen(TERMUX_HOME);
        size_t outLen = strlen(home) + tailLen + 1;
        char *out = (char *)malloc(outLen);
        if (!out) return NULL;
        strcpy(out, home);
        strcat(out, path + strlen(TERMUX_HOME));
        return out;
    }
    return NULL;
}

int execve(const char *pathname, char *const argv[], char *const envp[]) {
    static int (*real_execve)(const char *, char *const[], char *const[]) = NULL;
    if (!real_execve) real_execve = (int (*)(const char *, char *const[], char *const[]))dlsym(RTLD_NEXT, "execve");
    if (!real_execve) { errno = ENOSYS; return -1; }

    char *rewritten = rewrite_path(pathname);
    int rc = real_execve(rewritten ? rewritten : pathname, argv, envp);
    free(rewritten);
    return rc;
}

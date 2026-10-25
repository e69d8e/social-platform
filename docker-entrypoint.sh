#!/bin/sh
set -e

# 确保应用目录可写（日志目录 /app/logs 由 spring 用户写入）
chown -R spring:spring /app 2>/dev/null || true

# 若以 root 身份运行，降权为 spring 用户执行；否则直接执行
if [ "$(id -u)" = '0' ]; then
    exec su-exec spring java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher "$@"
fi

exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher "$@"

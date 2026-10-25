#!/bin/sh
set -e

# 图片已改存 MinIO 对象存储，容器内无需本地图片目录；仅确保应用目录可写
chown -R spring:spring /app 2>/dev/null || true

# 若以 root 身份运行，降权为 spring 用户执行；否则直接执行
if [ "$(id -u)" = '0' ]; then
    exec su-exec spring java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher "$@"
fi

exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher "$@"

#!/bin/sh
set -e

# 确保上传目录存在并授予 spring 用户写入权限（解决 Linux 宿主机 bind mount 权限问题）
mkdir -p /usr/local/nginx/html/imgs
chown -R spring:spring /usr/local/nginx/html/imgs 2>/dev/null || true
chown -R spring:spring /app 2>/dev/null || true

# 若以 root 身份运行，降权为 spring 用户执行；否则直接执行
if [ "$(id -u)" = '0' ]; then
    exec su-exec spring java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher "$@"
fi

exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher "$@"

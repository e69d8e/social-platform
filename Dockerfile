# ========== 构建阶段 ==========
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
# 先复制 pom.xml 单独下载依赖（利用 Docker 缓存层）
COPY pom.xml .
RUN mvn dependency:go-offline -B
# 复制源码并打包（跳过测试）
COPY src ./src
RUN mvn package -DskipTests -B

# ========== 运行阶段 ==========
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="li"

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/target/SocialPlatform-0.0.1-SNAPSHOT.jar app.jar

# 暴露应用端口
EXPOSE 8081

# JVM 参数可通过环境变量调整
ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

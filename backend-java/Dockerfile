# ============================================================
# Backend Java Dockerfile - Spring Boot + Spring Cloud Alibaba
# ============================================================

# 阶段1: 构建
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制 pom.xml 并利用 Docker 缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# 阶段2: 运行
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装 curl (健康检查用)
RUN apk add --no-cache curl

# 从构建阶段复制 JAR 文件
COPY --from=builder /app/target/*.jar app.jar

# JVM 参数优化
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"

# 暴露端口
EXPOSE 8080

# 健康检查（无 actuator，用 TCP 探测 8080 端口）
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD nc -z 127.0.0.1 8080 || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

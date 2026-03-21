# Multi-stage build
FROM gradle:8.5-jdk21-alpine AS build
WORKDIR /app

# Copy gradle files first for cache
COPY build.gradle settings.gradle gradle.properties ./
COPY gradle gradle

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source and build
COPY src src
RUN gradle bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache tzdata \
    && ln -snf /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo Asia/Seoul > /etc/timezone
ENV TZ=Asia/Seoul

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/build/libs/*.jar app.jar

# logback 이 logs/, logs_dev/, logs_local/ 에 쓸 수 있도록 (spring 사용자는 /app 에 mkdir 불가)
RUN mkdir -p /app/logs /app/logs_dev /app/logs_local \
    && chown -R spring:spring /app

USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

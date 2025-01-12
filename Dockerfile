# Build stage
FROM gradle:8.12.0-jdk21 AS build
WORKDIR /app

# Copy only dependency-related files first
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

# Download dependencies only - this layer gets cached
RUN gradle dependencies --no-daemon

# Copy source code
COPY src src

# Build with specific memory constraints
RUN gradle build --no-daemon -x test \
    --gradle-user-home ~/.gradle \
    -Dorg.gradle.jvmargs="-Xmx2048m -XX:+HeapDumpOnOutOfMemoryError" \
    -Dorg.gradle.workers.max=2

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add necessary packages
RUN apk add --no-cache curl

# Create a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Set Java options for containers
ENV JAVA_OPTS="\
    -XX:InitialRAMPercentage=50.0 \
    -XX:MaxRAMPercentage=70.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dnetworkaddress.cache.ttl=60 \
    --enable-preview"

ENV PORT=8080
EXPOSE ${PORT}

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT}"]
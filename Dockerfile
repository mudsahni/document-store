# Use an official Gradle image with JDK for Kotlin development
FROM gradle:jdk21 AS build

# Set the working directory in the container
WORKDIR /app

# Copy the Gradle configuration files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat

# Copy the source code
# Assuming standard Kotlin project structure
COPY src ./src

# Make gradlew executable
RUN chmod +x ./gradlew

# Build the application
# Using gradlew instead of gradle for better version control
# Build the application with credentials if provided
RUN ./gradlew build -x test --no-daemon;


# Use Amazon Corretto as the base image for running the application
FROM eclipse-temurin:21-jre

WORKDIR /app

# Install required native libraries and dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    libtcnative-1 \
    libatomic1 \
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# Create directories for mounted secrets
RUN mkdir -p /secrets/firebase /secrets/gcp

# Copy the built JAR file from the build stage
# Assuming your JAR is built in the 'build/libs' directory
COPY --from=build /app/build/libs/*.jar ./app.jar

# Expose the port your application runs on
EXPOSE 8080

# Environment variable for Spring Boot
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

# Command to run the application
CMD ["java", \
     "-XX:+UseContainerSupport", \
     "-XX:MaxRAMPercentage=75", \
     "-XX:InitialRAMPercentage=50", \
     "-XX:+OptimizeStringConcat", \
     "-jar", "app.jar"]

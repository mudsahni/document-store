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
RUN ./gradlew build --no-daemon

# Use Amazon Corretto as the base image for running the application
FROM amazoncorretto:21-alpine

WORKDIR /app

# Copy the built JAR file from the build stage
# Assuming your JAR is built in the 'build/libs' directory
COPY --from=build /app/build/libs/*.jar ./app.jar

# Expose the port your application runs on
EXPOSE 8080

# Command to run the application
CMD ["java", "-jar", "app.jar"]
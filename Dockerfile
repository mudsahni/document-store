# Use an official Gradle image with JDK for Kotlin development
FROM gradle:jdk17 AS build

# Set the working directory in the container
WORKDIR /app

# Create a directory for certificates
RUN mkdir -p /usr/local/share/ca-certificates/extra
COPY "./ssl-com-root.crt" /usr/local/share/ca-certificates/extra/


# Update CA certificates
RUN update-ca-certificates

# Add the certificates to Java keystore
RUN keytool -importcert -trustcacerts \
    -file "/usr/local/share/ca-certificates/extra/ssl-com-root.crt" \
    -alias sslcom-ev-root-r2 \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit \
    -noprompt && \


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
FROM amazoncorretto:17-alpine

WORKDIR /app

# Copy the built JAR file from the build stage
# Assuming your JAR is built in the 'build/libs' directory
COPY --from=build /app/build/libs/*.jar ./app.jar

# Expose the port your application runs on
EXPOSE 8080

# Command to run the application
CMD ["java", "-jar", "app.jar"]
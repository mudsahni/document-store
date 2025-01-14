# Use an official Gradle image with JDK for Kotlin development
FROM gradle:jdk21 AS build

# Set the working directory in the container
WORKDIR /app

# Arguments for secrets
ARG FIREBASE_SA_KEY
ARG GCP_SA_KEY

# Set up credentials if provided
RUN if [ -n "$FIREBASE_SA_KEY" ]; then \
    echo "$FIREBASE_SA_KEY" > /app/firebase_sa_key.json; \
    fi

RUN if [ -n "$GCP_SA_KEY" ]; then \
    echo "$GCP_SA_KEY" > /app/gcp_sa_key.json; \
    fi


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
RUN if [ -f "/app/firebase-service-account.json" ]; then \
    FIREBASE_SA_KEY=/app/firebase_sa_key.json GCP_SA_KEY=/app/gcp_sa_key.json ./gradlew build --no-daemon; \
    else \
    ./gradlew build -x test --no-daemon; \
    fi


# Use Amazon Corretto as the base image for running the application
FROM amazoncorretto:21-alpine

WORKDIR /app

# Copy the built JAR file from the build stage
# Assuming your JAR is built in the 'build/libs' directory
COPY --from=build /app/build/libs/*.jar ./app.jar

# Copy credentials if needed at runtime
COPY --from=build /app/firebase_sa_key.json /app/firebase_sa_key.json
COPY --from=build /app/gcp_sa_key.json /app/gcp_sa_key.json

# Expose the port your application runs on
EXPOSE 8080

# Command to run the application
CMD ["java", "-jar", "app.jar"]
# Multi-stage build for Spring Boot application
# Stage 1: Build the application
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy Gradle wrapper and configuration files
COPY gradle/ gradle/
COPY gradlew* ./
COPY build.gradle settings.gradle gradle.properties ./
RUN chmod +x ./gradlew

# Copy source code
COPY src/ src/

# Build the application (skip tests for faster builds, remove --no-daemon if you want to run tests)
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the JAR from builder stage
COPY --from=builder /app/build/libs/nerya-all-naturals-0.0.1-SNAPSHOT.jar app.jar

# Change ownership to non-root user
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

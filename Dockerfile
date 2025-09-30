# ---- Build Stage ----
FROM maven:3.9.6-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy project files
COPY pom.xml .
COPY src ./src

# Build the Spring Boot application, skip tests for faster build
RUN mvn clean package -DskipTests

# ---- Final Stage ----
FROM eclipse-temurin:17-jdk-jammy

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/Media-0.0.1-SNAPSHOT.jar ./Media.jar

# Expose the port the Spring Boot app runs on
EXPOSE 8080

# Run the Spring Boot application
CMD ["java", "-jar", "Media.jar"]

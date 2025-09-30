# ---- Build Stage ----
FROM maven:3.9.6-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy project source
COPY src ./src

# Build the Spring Boot application, skip tests for faster build
RUN mvn clean package -DskipTests

# ---- Final Stage ----
FROM eclipse-temurin:17-jre-jammy

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/*.jar ./app.jar

# Expose the port the Spring Boot app runs on
EXPOSE 8080

# Run the Spring Boot application
CMD ["java", "-jar", "app.jar"]

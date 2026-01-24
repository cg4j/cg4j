# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml and download dependencies (layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime image (use JDK because WALA needs jmods directory)
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Copy only the fat JAR from build stage
COPY --from=builder /build/target/cg4j-cli-0.1.0-SNAPSHOT-jar-with-dependencies.jar /app/cg4j.jar

# Create volume mount points
RUN mkdir -p /input /output /deps

# Default working directory for output
WORKDIR /output

# Run the JAR, allow args to be passed
ENTRYPOINT ["java", "-jar", "/app/cg4j.jar"]
CMD ["--help"]

# Document volumes
VOLUME ["/input", "/output", "/deps"]

# Labels
LABEL maintainer="cg4j"
LABEL description="Call Graph Generator for Java using IBM WALA"
LABEL version="0.1.0-SNAPSHOT"

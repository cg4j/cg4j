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
COPY --from=builder /build/target/*-jar-with-dependencies.jar /app/cg4j.jar

# Create volume mount points
RUN mkdir -p /input /output /deps

# Default working directory for output
WORKDIR /output

# Run the JAR, allow args to be passed
ENTRYPOINT ["java", "-jar", "/app/cg4j.jar"]
CMD ["--help"]

# Document volumes
VOLUME ["/input", "/output", "/deps"]

# Release workflows can override these image metadata values at build time.
ARG APP_VERSION=dev
ARG VCS_REF=unknown
ARG SOURCE_URL=https://github.com/cg4j/cg4j

# Labels
LABEL maintainer="cg4j" \
      description="CG4j: Call Graph Generation for Java" \
      version="${APP_VERSION}" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${SOURCE_URL}"

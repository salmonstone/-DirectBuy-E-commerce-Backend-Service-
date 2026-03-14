# ═══════════════════════════════════════════
#  Stage 1 — Build the JAR
# ═══════════════════════════════════════════
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom first — cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -q

# ═══════════════════════════════════════════
#  Stage 2 — Lean production image
# ═══════════════════════════════════════════
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Security — non-root user
RUN groupadd -r shopuser && useradd -r -g shopuser shopuser

# Copy JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Set ownership
RUN chown -R shopuser:shopuser /app
USER shopuser

# JVM tuning for containers
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

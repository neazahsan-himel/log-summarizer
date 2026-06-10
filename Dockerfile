# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Cache dependency resolution separately from source compilation
COPY pom.xml .
COPY .mvn/ .mvn/
RUN test -f mvnw || (apk add --no-cache maven && mvn dependency:go-offline -q)
COPY mvnw* ./
RUN chmod +x mvnw 2>/dev/null || true

COPY src/ src/

RUN if [ -f mvnw ]; then \
      ./mvnw clean package -DskipTests -q; \
    else \
      mvn clean package -DskipTests -q; \
    fi

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
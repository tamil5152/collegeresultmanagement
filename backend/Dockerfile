# ---- build stage -------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- runtime stage -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 --no-create-home appuser
WORKDIR /app
COPY --from=build /build/target/result-management.jar app.jar
USER appuser
EXPOSE 8080
# The platform injects PORT; configuration comes only from environment variables (see README).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

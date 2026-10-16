# syntax=docker/dockerfile:1
FROM eclipse-temurin:24-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package && mv target/nexus-ai-*.jar app.jar

FROM eclipse-temurin:24-jre
RUN groupadd --system nexus && useradd --system --gid nexus --no-create-home nexus
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER nexus
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

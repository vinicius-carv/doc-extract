FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B -DskipTests package \
    && mkdir -p results

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home app
WORKDIR /app
COPY --from=build /src/target/doc-extract-0.1.0-SNAPSHOT.jar app.jar
COPY --from=build /src/corpus corpus
COPY --from=build --chown=app:app /src/results results
USER app
# Reach Ollama running on the host; override as needed.
ENV OLLAMA_URL=http://host.docker.internal:11434
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

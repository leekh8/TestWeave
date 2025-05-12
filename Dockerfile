# Dockerfile
FROM eclipse-temurin:17-jdk

WORKDIR /app
COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew build
RUN ls -la build/libs/

CMD find ./build/libs -name "*.jar" | xargs java -jar

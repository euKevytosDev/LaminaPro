# Build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src ./src
RUN ./mvnw -q -B package -DskipTests

# Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S barberini && adduser -S barberini -G barberini
USER barberini

COPY --from=build /app/target/barberini-*.jar app.jar

# Northflank/Render free ~512 MB: JVM enxuta pra não estourar RAM
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=55.0 -XX:+UseSerialGC -Xss256k --enable-native-access=ALL-UNNAMED"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]

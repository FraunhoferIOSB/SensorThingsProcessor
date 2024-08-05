FROM maven:3.8.5-openjdk-17 AS build

WORKDIR /usr/local/FROST
COPY . .
RUN mvn -B package

FROM openjdk:17
ARG JAR_FILE
ARG VERSION
WORKDIR /usr/local/FROST
COPY --from=build /usr/local/FROST/target/SensorThingsProcessor-${VERSION}-jar-with-dependencies.jar /usr/local/FROST/${JAR_FILE}
CMD ["java", "-jar", "FROST-Processor.jar"]

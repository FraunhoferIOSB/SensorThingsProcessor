FROM maven:3.8.5-openjdk-17 AS build

WORKDIR /usr/local/FROST
COPY . .
RUN mvn -B package
RUN pwd
RUN ls -la
RUN ls -la target

FROM openjdk:17
ARG JAR_FILE
WORKDIR /usr/local/FROST
COPY --from=build /usr/local/FROST/target/${JAR_FILE} /usr/local/FROST/${JAR_FILE}
RUN ls -la
CMD ["java", "-jar", "FROST-Processor.jar"]

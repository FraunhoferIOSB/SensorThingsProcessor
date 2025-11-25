FROM eclipse-temurin:21-alpine

# Copy to images tomcat path
ARG JAR_FILE
COPY target/${JAR_FILE} /usr/local/FROST/FROST-Processor.jar
WORKDIR /usr/local/FROST
CMD ["java", "-jar", "FROST-Processor.jar"]

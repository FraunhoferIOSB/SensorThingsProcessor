FROM openjdk:17

# Copy to images tomcat path
ARG JAR_FILE
ADD target/${JAR_FILE} /usr/local/FROST/FROST-Processor.jar
WORKDIR /usr/local/FROST
CMD ["java", "-jar", "FROST-Processor.jar"]

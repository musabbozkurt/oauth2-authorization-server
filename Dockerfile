FROM openjdk:21-jdk-slim
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} oauth2-authorization-server.jar
ENTRYPOINT ["java","-jar","/oauth2-authorization-server.jar"]
FROM openjdk:21-jdk-slim

WORKDIR /app

COPY target/rinha-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
EXPOSE 5005

ENV JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

ENTRYPOINT ["java", "-jar", "app.jar"]

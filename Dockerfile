FROM ibm-semeru-runtimes:open-21-jre-focal

WORKDIR /app

COPY target/rinha-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

FROM bellsoft/liberica-openjdk-alpine
EXPOSE 8081
COPY target/demo6-0.0.1-SNAPSHOT.jar app.jar
CMD ["java", "-jar", "app.jar"]
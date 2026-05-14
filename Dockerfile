FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY docker/maven/settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src
RUN mvn -s /root/.m2/settings.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /workspace/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

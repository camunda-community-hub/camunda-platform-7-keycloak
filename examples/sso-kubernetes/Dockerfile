# maven build
FROM maven:3.9.3-eclipse-temurin-17-alpine AS build
COPY pom-docker.xml /tmp/pom.xml
COPY src /tmp/src/
COPY settings-docker.xml /usr/share/maven/ref/
WORKDIR /tmp/
RUN mvn -s /usr/share/maven/ref/settings-docker.xml package -DskipTests

# Container
FROM eclipse-temurin:17.0.8_7-jre-jammy AS main

# add spring boot application
VOLUME /tmp
COPY --from=build /tmp/target/camunda-platform-7-keycloak-examples-sso-kubernetes*.jar ./app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]

# maven build
FROM maven:3.6.1-jdk-11-slim AS MAVEN_TOOL_CHAIN
COPY pom-docker.xml /tmp/pom.xml
COPY src /tmp/src/
COPY settings-docker.xml /usr/share/maven/ref/
WORKDIR /tmp/
RUN mvn -s /usr/share/maven/ref/settings-docker.xml package

FROM camunda/camunda-bpm-platform:run-7.13.0

COPY --from=MAVEN_TOOL_CHAIN /tmp/target/camunda-bpm-identity-keycloak-examples-camunda-run-*.jar /camunda/configuration/userlib/camunda-bpm-identity-keycloak-examples-camunda-run.jar
COPY --from=MAVEN_TOOL_CHAIN /tmp/src/main/resources/application.yaml /camunda/configuration/default.yml
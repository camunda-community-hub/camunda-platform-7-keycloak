# Camunda Showcase for Spring Boot & Keycloak Identity Provider

## What it does

This is a basic showcase for a Camunda Spring Boot application using the [Keycloak Identity Provider Plugin](https://github.com/camunda/camunda-bpm-identity-keycloak).

You will not only login using Keycloak (or if configured using your preferred social identity provider)

![Keycloak-Login](docs/Keycloak-Login.PNG) 

but most importantly get Users and Groups in Camunda managed by Keycloak as well

![Keycloak-Groups](docs/Keycloak-Groups.PNG) 

## Prerequisites

* In order to run the project as Spring Boot App you will need JDK 8, Maven.
* The optional Docker build requires only Docker itself. It uses JDK 11, Maven within a multi-stage Docker build.
* The optional Kubernetes setup requires the NGINX Ingress Controller.

## Build process

The build process uses Maven.

<table>
    <tr>
        <td><b>Target</b></td>
        <td>&nbsp;</td>
        <td><b>Maven Goal</b></td>
    </tr>
    <tr>
        <td>Build Spring Boot Jar</td>	
        <td>&nbsp;</td>
        <td><code>clean install</code></td>	
    </tr>
    <tr>
    	<td>Run Spring Boot App</td>
        <td>&nbsp;</td>
    	<td><code>spring-boot:run</code></td>
    </tr>
</table>

## Show me the important parts

The following description is a quick start. A more detailed description will follow.

### Run it

1.  Start Keycloak Server as described below.
2.  Run this project as Spring Boot App.
3.  Point a new private window of your browser to ``localhost:8080/camunda``
4.  Login with camunda@accso.de / camunda1!
5.  Choose the admin console and browse users and groups from Keycloak

### Keycloak server - local test setup

Use a ``docker-compose.yml`` file as follows:

	version: "3.3"
	
	services:
	  jboss.keycloak:
	    build: .
	#    image: jboss/keycloak:7.0.0
	    image: gunnaraccso/keycloak.server:7.0.0
	    restart: always
	    environment:
	      TZ: Europe/Berlin
	      KEYCLOAK_USER: keycloak
	      KEYCLOAK_PASSWORD: keycloak1!
	    ports:
	      - "9001:8443"
	      - "9000:8080"

The image ``gunnaraccso/keycloak.server`` has been derived from the original ``jboss/keycloak`` docker image. It additionally includes a basic test setup matching the test configuration of this project. The image exists only for demonstration purposes. Do not use in production. For original Keycloak docker images see [Keycloak Docker image](https://hub.docker.com/r/jboss/keycloak/).

The only thing you have to adapt is the **Redirect URI** of the Camuna Identity Service Client. Login at the [Keycloak Admin Console](https://localhost:9001/auth/admin/master/console/#/) using user/password as configured above and set ``http://localhost:8080/camunda/login`` as Valid Redirect URI configuration:

![Keycloak-RedirectURI](docs/Keycloak-RedirectURI.PNG) 

**Beware**: This is a first basic test setup which currently still uses the Master realm. For production I would strongly encourage you to setup your own realm and use Master only for administration purposes. In a future version this showcase will be modified as well.

For further details on how to setup a Keycloak Camunda Identity Service Client see documentation of [Keycloak Identity Provider Plugin](https://github.com/camunda/camunda-bpm-identity-keycloak)

### Keycloak Identity Provider Plugin

``KeycloakIdentityProvider.java`` in package ``org.camunda.bpm.extension.keycloak.showcase.plugin`` will activate the plugin.

The configuration part in ``applicaton.yaml`` is as follows:

	keycloak.url.plugin: ${KEYCLOAK_URL_PLUGIN:https://localhost:9001}

	plugin.identity.keycloak:
	  keycloakIssuerUrl: ${keycloak.url.plugin}/auth/realms/master
	  keycloakAdminUrl: ${keycloak.url.plugin}/auth/admin/realms/master
	  clientId: camunda-identity-service
	  clientSecret: 7d3c845d-f652-4bed-9797-d6d20b7623da
	  useEmailAsCamundaUserId: true
	  useUsernameAsCamundaUserId: false
	  administratorGroupName: camunda-admin
	  disableSSLCertificateValidation: true
	  
For configuration details of the plugin see documentation of [Keycloak Identity Provider Plugin](https://github.com/camunda/camunda-bpm-identity-keycloak) 

### OAuth2 SSO Configuration

See package ``org.camunda.bpm.extension.keycloak.showcase.sso``.

The main configuration part in ``applicaton.yaml`` is as follows:

	keycloak.url.client: ${KEYCLOAK_URL_CLIENT:http://localhost:9000}
	keycloak.url.token: ${KEYCLOAK_URL_TOKEN:http://localhost:9000}

	security:
	  basic:
	    enabled: false
	  oauth2:
	    client:
	      client-id: camunda-identity-service
	      client-secret: 7d3c845d-f652-4bed-9797-d6d20b7623da
	      accessTokenUri: ${keycloak.url.token}/auth/realms/master/protocol/openid-connect/token
	      userAuthorizationUri: ${keycloak.url.client}/auth/realms/master/protocol/openid-connect/auth
	      scope: openid profile email
	    resource:
	      userInfoUri: ${keycloak.url.client}/auth/realms/master/protocol/openid-connect/userinfo

## Kubernetes Setup

Finally - a quick introduction on how to setup Keycloak and this showcase on Kubernetes.

![KubernetesSetup](docs/KubernetesSetup.PNG)


Before we turn to Kubernetes it is necessary to shortly introduce the Docker Build process.

### Multi-Stage Docker Build

The Dockerfile is using a multi-stage Docker build starting with a maven Docker image. Why do we do that? Because we do not want to deal with maven and java versions etc. within our pipeline. In our case the pipeline will have to deal with Docker, that's all.

The Docker build uses the separate standalone Maven `docker-pom.xml` as build file. When using the Camunda Enterprise Version you have to adapt the file ``settings-docker.xml`` and set your credentials of the Camunda Enterprise Maven Repository accordingly:

    <!-- Maven Settings for Docker Build -->
    <servers>
		<server>
			<id>camunda-bpm-ee</id>
			<username>xxxxxx</username>
			<password>xxxxxx</password>
		</server>
    </servers>

Just run a standard Docker image build to get your docker container.

### Java module dependencies & jlinked Java 11

The Dockerfile includes stages for building a shrinked JDK 11 using Java's ``jlink``. Keep in mind that this is optional.

Just for the records - how to find out java module dependencies and shrink your JDK:
* Extract ``target/camunda-showcase-keycloak.jar/BOOT-INF/lib`` to `target/lib``
* Open a shell in ``target`` and run ``jdeps -cp lib/* -R --multi-release 11 --print-module-deps --ignore-missing-deps camunda-showcase-keycloak.jar``

The result goes to the jlink ``add-modules`` option in the following Dockerfile section (which has already been applied for this showcase):

	# jlinked java 11 (do NOT use alpine-slim here which has important module files deleted)
	FROM adoptopenjdk/openjdk11:jdk-11.0.3_7-alpine AS JLINKED_JAVA
	RUN ["jlink", "--compress=2", \
	     "--module-path", "/opt/java/openjdk/jmods", \
	     "--add-modules", "java.base,java.compiler,java.desktop,java.instrument,java.management,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql.rowset,jdk.httpserver,jdk.jdi,jdk.unsupported", \
	     "--output", "/jlinked"]

The final result will be a slim custom JDK which has been reduced in image size. Feel free to skip this part, delete the corresponding Dockerfile sections and use a full JDK 11 as base image for your Spring Boot Application.

### Kubernetes

The Kubernetes setup can be found in directory ``k8s``. It contains a subfolder ``keycloak`` setting up the Keycloak test server.

**Keycloak Kubernetes Setup**

In order to make Keycloak run with Kubernetes you have to be aware of two things:
* Activate the ``PROXY_ADDRESS_FORWARDING`` option for Keycloak.
* Activate ``nginx.ingress.kubernetes.io/ssl-redirect`` in your ingress service.
* The Redirect URI within Keycloak's Camunda-Identity-Service Client should be ``/camunda/login/*``.

Keep in mind that the included ``keycloak/deployment.yaml`` is only a test setup. Adapt to your own needs. For production I would strongly encourage you to setup your own realm and use Master only for administration purposes.

After setting up your Keycloak server you can start the deployment of the showcase.

**Camunda Showcase Kubernetes Setup**

In order to make the Camunda Showcase work the following points are noteworthy:
* You have to activate sticky sessions within the ingress service. We have more than one pod running the showcase!
* Keep in mind, that sticky sessions won't work without a host setting (important for a local test setup) and it is recommended to add a ``session-cookie-path`` (I have seen error reports on that - might be fixed, might be not fixed meanwhile).

You should work through the following points:
* Within the ``deployment.yaml`` of the showcase adapt the image name to your own needs.
* Within the ``deployment.yaml`` of the showcase adapt the environment variable ``KEYCLOAK_URL_CLIENT`` to your own host
* Within the ``ingress-service.yaml`` adapt the host name to your own environment.

### SSL

Since this is just a quick start, I didn't use a full SSL setup including the installation of my own certificates. Sadly the OAuth2 security part of Spring Boot has no option to deactivate SSL certificate validation, which would make a quick test setup much easier. For production keep in mind to use SSL certificates and HTTPS, especially for the login part.

### Outlook

This showcase still works with sticky sessions. It might be worth reading the Camunda blog article [Camunda BPM - Session Management in Cloud Environments](https://blog.camunda.com/post/2019/06/camunda-bpm-with-session-manager/) and integrate the findings.

------------------------------------------------------------

That's it. Have a happy Camunda Keycloak experience and focus on what really matters: the core processes of your customer.

Brought to you by:

[Gunnar von der Beck](https://www.xing.com/profile/Gunnar_vonderBeck/portfolio "XING Profile")
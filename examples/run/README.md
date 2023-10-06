# Installation on Camunda Platform Run
This document describes the installation of the **Keycloak Camunda Identity Provider Plugin** on a the [Camunda BPM Run](https://docs.camunda.org/manual/latest/user-guide/camunda-bpm-run/) distribution.

## Install the Keycloak Identity Provider Plugin

In order to install the Keycloak Identity Provider Plugin you have to download the library ``camunda-platform-7-keycloak-run-x.y.z.jar`` and copy it to ``$CAMUNDA_BPM_RUN_ROOT/configuration/userlib``.

Please be aware that you must use the provided ``*-run-x.y.z.jar`` (fat jar, packaged with the "**-run**" extension) including transitive dependencies. The additional library is available since version ``2.0.0`` and can be found e.g. on [Maven Central](https://search.maven.org/search?q=g:org.camunda.bpm.extension%20AND%20a:camunda-platform-7-keycloak-run).

For the records - included dependencies are:

* org.apache.httpcomponents:client5
	* org.apache.httpcomponents:core5
* com.google.code.gson:gson
* com.github.ben-manes.caffeine:caffeine
	* org.checkerframework:checker-qual
	* com.google.errorprone:error_prone_annotations

The ``com.google.code.gson`` and ``com.github.ben-manes.caffeine`` dependencies are shaded into the ``keycloakjar`` package namespace. Please be aware ``httpclient`` dependencies (including transitive ones) are not(!) shaded.

## Configure the Keycloak Identity Provider Plugin

The last step is to edit the ``default.yml`` or ``production.yml`` file inside the folder ``$CAMUNDA_BPM_RUN_ROOT/configuration`` and configure the plugin. A sample configuration looks as follows:

```yml
# Camunda Keycloak Identity Provider Plugin
plugin.identity.keycloak:
  keycloakIssuerUrl: https://localhost:9001/auth/realms/camunda
  keycloakAdminUrl: https://localhost:9001/auth/admin/realms/camunda
  clientId: camunda-identity-service
  clientSecret: 12345678-abcd-efgh-ijkl-123456789012
  useUsernameAsCamundaUserId: true
  useGroupPathAsCamundaGroupId: true
  administratorGroupName: camunda-admin
  disableSSLCertificateValidation: true
```

Please be aware that you have to **delete** the following properties:

```yml
camunda.bpm:
  admin-user:
  id: demo
  password: demo
```

The Keycloak Identity Provider is a ReadOnly Identity Provider and thus not allowed to create users upon startup.

For a full documentation of all configuration properties see the documentation of the [Keycloak Identity Provider Plugin](https://github.com/camunda-community-hub/camunda-platform-7-keycloak) itself.

## Docker Sample Setup

Within the subdirectory `docker` you'll find a basic sample consisting of:

* ``Dockerfile``: custom Docker image consisting of Camunda BPM Run and the Keycloak Identity Provider Plugin. Adapt Camunda and plugin versions to your own needs.
* ``docker-compose.yml``: simple setup consisting of the custom Camunda Keycloak Docker image and a preconfigured Keycloak instance with the ``camunda-identity-service`` client and a Camunda admin user and group.

Usage:

1. ``docker compose build``
2. ``docker compose up -d``
3. Login at ``http://localhost:8080`` using ``camunda`` / ``camunda1!`` and use Cockpit / Tasklist / Admin.
4. Keycloak is available under ``https://localhost:9001/auth``. Login with ``keycloak`` / ``keycloak1!``.

**Beware**: This is not production ready, still using a H2 database for each of the instances, but might help you to understand the configuration basics.
# Installation on Camunda BPM Run
This document describes the installation of the **Keycloak Camunda Identity Provider Plugin** on a the [Camunda BPM Run](https://docs.camunda.org/manual/latest/user-guide/camunda-bpm-run/) distribution.

## Install the Keycloak Identity Provider Plugin

In order to install the Keycloak Identity Provider Plugin you have to download the library ``camunda-bpm-identity-keycloak-run-x.y.z.jar`` and copy it to ``$CAMUNDA_BPM_RUN_ROOT/configuration/userlib``.

Please be aware that you must use the provided ``*-run-x.y.z.jar`` (fat jar, packaged with the "**-run**" extension) including transitive dependencies. The additional library is available since version ``2.0.0`` and can be found e.g. on [Maven Central](https://search.maven.org/search?q=g:org.camunda.bpm.extension%20AND%20a:camunda-bpm-identity-keycloak-run).

For the records - included dependencies are:

* org.apache.httpcomponents:httpclient
* org.apache.httpcomponents:httpcore
* commons-codec:commons-codec
* com.google.code.gson:gson

The ``com.google.code.gson`` dependency is shaded into the ``keycloakjar`` package namespace. Please be aware ``httpclient`` dependencies (including transitive ones) are not(!) shaded.

## Configure the Keycloak Identity Provider Plugin

The last step is to edit the ``default.yml`` or ``production.yml`` file inside the folder ``$CAMUNDA_BPM_RUN_ROOT/configuration`` and configure the plugin. A sample configuration looks as follows:

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

Please be aware that you have to **delete** the following properties:

	camunda.bpm:
	  admin-user:
	    id: demo
	    password: demo

The Keycloak Identity Provider is a ReadOnly Identity Provider and thus not allowed to create users upon startup.

For a full documentation of all configuration properties see the documentation of the [Keycloak Identity Provider Plugin](https://github.com/camunda/camunda-bpm-identity-keycloak) itself.

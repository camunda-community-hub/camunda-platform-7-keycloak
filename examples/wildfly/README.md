# Installation on JBoss/Wildfly
This document describes the installation of the **Keycloak Camunda Identity Provider Plugin** on a full distribution for JBoss/Wildfly.

## Camunda Installation on JBoss/Wildfly

For information on how to install Camunda on JBoss/Wildfly carefully read and follow the installation reference within the Camunda Docs: [https://docs.camunda.org/manual/latest/installation/full/jboss/](https://docs.camunda.org/manual/latest/installation/full/jboss/)

## Install the Keycloak Identity Provider Plugin

In order to install the Keycloak Identity Provider Plugin you have to download the library ``camunda-bpm-identity-keycloak-all-x.y.z.jar`` (can be found e.g. on [Maven Central](https://search.maven.org/search?q=g:org.camunda.bpm.extension%20AND%20a:camunda-bpm-identity-keycloak-all)) and create a module containing it.
To do so, create a directory ``modules/org/camunda/bpm/identity/camunda-identity-keycloak/main`` in your JBoss/Wildfly installation and put the library inside. In the same directory, create a descriptor file named ``module.xml`` with the following content:

    <module xmlns="urn:jboss:module:1.0" name="org.camunda.bpm.identity.camunda-identity-keycloak">
        <resources>
            <resource-root path="camunda-bpm-identity-keycloak-all-x.y.z.jar" />
        </resources>

        <dependencies>

            <module name="sun.jdk" />

            <module name="javax.api" />
            <module name="org.camunda.bpm.camunda-engine" />
            <module name="org.camunda.commons.camunda-commons-logging" />

        </dependencies>
    </module>

Reference this module in the module descriptor of your Camunda Wildfly Subsystem (``modules/org/camunda/bpm/wildfly/camunda-wildfly-subsystem/main/module.xml``) by adding:

    <module xmlns="urn:jboss:module:1.0" name="org.camunda.bpm.wildfly.camunda-wildfly-subsystem">
        <resources>
            ...
        </resources>

        <dependencies>
            ...
            <module name="org.camunda.bpm.identity.camunda-identity-keycloak"/>
        </dependencies>
    </module>



## Configure the Keycloak Identity Provider Plugin

The last step is to edit the ``standalone.xml`` configuration file in ``standalone/configuration`` to use the plugin in the camunda subsystem. A sample configuration looks as follows:

	<subsystem xmlns="urn:org.camunda.bpm.jboss:1.1">
            <process-engines>
                <process-engine name="default" default="true">
                    <datasource>java:jboss/datasources/ProcessEngine</datasource>
                    <history-level>full</history-level>
                    <properties>
                       ...
                    </properties>
                    <plugins>
                        <plugin>
                            <class>org.camunda.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin</class>
                            <properties>
                                <property name="keycloakIssuerUrl">
                                    http://localhost:8082/auth/realms/ndb
                                </property>
                                <property name="keycloakAdminUrl">
                                    http://localhost:8082/auth/admin/realms/ndb
                                </property>
                                <property name="clientId">
                                    camunda-identity-service
                                </property>
                                <property name="clientSecret">
                                    acda1430-...
                                </property>
                                <property name="useUsernameAsCamundaUserId">
                                    true
                                </property>
                                <property name="useGroupPathAsCamundaGroupId">
                                    true
                                </property>
                                <property name="administratorGroupName">
                                    camunda-admin
                                </property>
                                <property name="disableSSLCertificateValidation">
                                    true
                                </property>
                            </properties>
                        </plugin>
                    </plugins>
                </process-engine>
            </process-engines>

For a full documentation of all configuration properties see the documentation of the [Keycloak Identity Provider Plugin](https://github.com/camunda/camunda-bpm-identity-keycloak) itself.

# Installation on JBoss/Wildfly
This document describes the installation of the **Keycloak CIB seven Identity Provider Plugin** on a full distribution for JBoss/Wildfly.

## CIB seven Installation on JBoss/Wildfly

For information on how to install CIB seven on JBoss/Wildfly carefully read and follow the installation reference within the CIB seven Docs: [https://docs.cibseven.org/manual/latest/installation/full/jboss/](https://docs.cibseven.org/manual/latest/installation/full/jboss/)

## Install the Keycloak Identity Provider Plugin

In order to install the Keycloak Identity Provider Plugin you have to download the library ``cibseven-keycloak-all-x.y.z.jar`` (can be found e.g. on [Maven Central](https://search.maven.org/search?q=g:org.cibseven.community.keycloak%20AND%20a:cibseven-keycloak-all)) and create a module containing it.
To do so, create a directory ``modules/org/cibseven/bpm/identity/cibseven-identity-keycloak/main`` in your JBoss/Wildfly installation and put the library inside. In the same directory, create a descriptor file named ``module.xml`` with the following content:

```xml
<module xmlns="urn:jboss:module:1.0" name="org.cibseven.bpm.identity.cibseven-identity-keycloak">
    <resources>
        <resource-root path="cibseven-keycloak-all-x.y.z.jar" />
    </resources>

    <dependencies>

        <module name="sun.jdk" />

        <module name="javax.api" />
        <module name="org.cibseven.bpm.cibseven-engine" />
        <module name="org.cibseven.commons.cibseven-commons-logging" />
        <module name="org.slf4j"/>
        
    </dependencies>
</module>
```

Reference this module in the module descriptor of your CIB seven Wildfly Subsystem (``modules/org/cibseven/bpm/wildfly/cibseven-wildfly-subsystem/main/module.xml``) by adding:

```xml
<module xmlns="urn:jboss:module:1.0" name="org.cibseven.bpm.wildfly.cibseven-wildfly-subsystem">
    <resources>
        ...
    </resources>

    <dependencies>
        ...
        <module name="org.cibseven.bpm.identity.cibseven-identity-keycloak"/>
    </dependencies>
</module>
```

## Configure the Keycloak Identity Provider Plugin

The last step is to edit the ``standalone.xml`` configuration file in ``standalone/configuration`` to use the plugin in the cibseven subsystem. A sample configuration looks as follows:

```xml
<subsystem xmlns="urn:org.cibseven.bpm.jboss:1.1">
        <process-engines>
            <process-engine name="default" default="true">
                <datasource>java:jboss/datasources/ProcessEngine</datasource>
                <history-level>full</history-level>
                <properties>
                    ...
                </properties>
                <plugins>
                    <plugin>
                        <class>org.cibseven.community.keycloak.plugin.KeycloakIdentityProviderPlugin</class>
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
```

For a full documentation of all configuration properties see the documentation of the [Keycloak Identity Provider Plugin](https://github.com/cibseven-community-hub/cibseven-keycloak) itself.

package org.camunda.bpm.extension.keycloak.showcase.plugin;

import org.camunda.bpm.extension.keycloak.config.KeycloakCockpitConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="plugin.cockpit.keycloak")
public class KeycloakCockpitPlugin extends KeycloakCockpitConfiguration {
}

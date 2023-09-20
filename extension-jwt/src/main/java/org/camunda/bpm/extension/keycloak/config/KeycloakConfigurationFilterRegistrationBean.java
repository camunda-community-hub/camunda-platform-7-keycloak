package org.camunda.bpm.extension.keycloak.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

public class KeycloakConfigurationFilterRegistrationBean extends FilterRegistrationBean {

    public KeycloakConfigurationFilterRegistrationBean(KeycloakCockpitConfiguration keycloakCockpitConfiguration, String camundaWebappApplicationPath) {
        setFilter(new KeycloakCockpitConfigurationFilter(keycloakCockpitConfiguration));
        setOrder(Ordered.HIGHEST_PRECEDENCE);
        addUrlPatterns(camundaWebappApplicationPath + KeycloakCockpitConfigurationFilter.KEYCLOAK_OPTIONS_PATH);
    }
}

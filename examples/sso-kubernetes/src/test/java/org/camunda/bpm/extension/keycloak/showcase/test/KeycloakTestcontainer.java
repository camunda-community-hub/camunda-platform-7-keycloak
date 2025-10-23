package org.camunda.bpm.extension.keycloak.showcase.test;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class KeycloakTestcontainer {

  private static final Logger log = LoggerFactory.getLogger(KeycloakTestcontainer.class);

  static KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.4.2")
      .withRealmImportFile("/camunda-realm.json")
      .withAdminUsername("keycloak")
      .withAdminPassword("keycloak1!");

  public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {

      // we have to start the container. Automatic shutdown by testcontainers.
      log.info("Attempt to start Keycloak container.");
      keycloakContainer.start();
      log.info("Keycloak container started, inject dynamic properties into the spring environment.");

      ConfigurableEnvironment environment = applicationContext.getEnvironment();
      environment.getPropertySources().addFirst(new MapPropertySource("keycloak-demo", Map.of(
          "keycloak.url.auth", keycloakContainer.getAuthServerUrl(),
          "keycloak.url.token", keycloakContainer.getAuthServerUrl(),
          "keycloak.url.plugin", keycloakContainer.getAuthServerUrl()
      )));
    }
  }

}

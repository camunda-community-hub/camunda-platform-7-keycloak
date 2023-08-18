package org.camunda.bpm.extension.keycloak.showcase.sso;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import org.camunda.bpm.extension.keycloak.auth.KeycloakJwtAuthenticationFilter;
import org.camunda.bpm.extension.keycloak.config.KeycloakCockpitConfiguration;
import org.camunda.bpm.extension.keycloak.config.KeycloakConfigurationFilterRegistrationBean;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.request.RequestContextListener;

import jakarta.inject.Inject;
import java.util.Collections;

/**
 * Camunda Web application SSO configuration for usage with KeycloakIdentityProviderPlugin.
 */
@ConditionalOnMissingClass("org.springframework.test.context.junit.jupiter.SpringExtension")
@Configuration
@EnableWebSecurity
public class WebAppSecurityConfig {

	private static final int AFTER_SPRING_SECURITY_FILTER_CHAIN_ORDER = 201;
	private static final String API_FILTER_PATTERN = "/api/*";
	private static final String AUTHENTICATION_FILTER_NAME = "Authentication Filter";

	@Inject
	private CamundaBpmProperties camundaBpmProperties;

	@Inject
	private KeycloakCockpitConfiguration keycloakCockpitConfiguration;

	@Bean
	public SecurityFilterChain httpSecurity(HttpSecurity http) throws Exception {
		String path = camundaBpmProperties.getWebapp().getApplicationPath();
		return http
				.csrf(csrf -> csrf
						.ignoringRequestMatchers(antMatcher("/api/**"), antMatcher("/engine-rest/**")))
				.authorizeHttpRequests(authz -> authz
						.requestMatchers(antMatcher("/")).permitAll()
						.requestMatchers(antMatcher(path + "/app/**")).permitAll()
						.requestMatchers(antMatcher(path + "/assets/**")).permitAll()
						.requestMatchers(antMatcher(path + "/lib/**")).permitAll()
						.requestMatchers(antMatcher(path + "/api/engine/engine/**")).permitAll()
						.requestMatchers(antMatcher(path + "/api/*/plugin/*/static/app/plugin.css")).permitAll()
						.requestMatchers(antMatcher(path + "/api/*/plugin/*/static/app/plugin.js")).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.build();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Bean
    public FilterRegistrationBean containerBasedAuthenticationFilter() {
		String camundaWebappPath = camundaBpmProperties.getWebapp().getApplicationPath();

        FilterRegistrationBean filterRegistration = new FilterRegistrationBean();
        filterRegistration.setFilter(new KeycloakJwtAuthenticationFilter());
        filterRegistration.setInitParameters(Collections.singletonMap("authentication-provider", "org.camunda.bpm.extension.keycloak.auth.KeycloakJwtAuthenticationProvider"));
		filterRegistration.setName(AUTHENTICATION_FILTER_NAME);
		filterRegistration.setOrder(AFTER_SPRING_SECURITY_FILTER_CHAIN_ORDER);
		filterRegistration.addUrlPatterns(camundaWebappPath + API_FILTER_PATTERN);
		return filterRegistration;
    }

	@Bean
	public FilterRegistrationBean cockpitConfigurationFilter() {
		return new KeycloakConfigurationFilterRegistrationBean(
				keycloakCockpitConfiguration,
				camundaBpmProperties.getWebapp().getApplicationPath()
		);
	}

	@Bean
	@Order(0)
	public RequestContextListener requestContextListener() {
	    return new RequestContextListener();
	}
	
}
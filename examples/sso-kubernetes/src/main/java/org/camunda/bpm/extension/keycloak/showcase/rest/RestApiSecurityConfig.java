package org.camunda.bpm.extension.keycloak.showcase.rest;

import jakarta.inject.Inject;
import org.camunda.bpm.engine.IdentityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * Optional Security Configuration for Camunda REST Api.
 */
@Configuration
@ConditionalOnProperty(name = "rest.security.enabled", havingValue = "true", matchIfMissing = true)
public class RestApiSecurityConfig {

	/** Configuration for REST Api security. */
	@Inject
	private RestApiSecurityConfigurationProperties configProps;
	
	/** Access to Camunda's Identity Service. */
	@Inject
	private IdentityService identityService;
	
	/** Access to Spring Security OAuth2 client service. */
	@Inject
	private OAuth2AuthorizedClientService clientService;

	@Inject
	private ApplicationContext applicationContext;
	
	/**
	 * {@inheritDoc}
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain httpSecurityRest(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
		String jwkSetUri = applicationContext.getEnvironment().getRequiredProperty(
				"spring.security.oauth2.client.provider." + configProps.getProvider() + ".jwk-set-uri");

		return http
				.securityMatcher(antMatcher("/engine-rest/**"))
				.csrf(csrf -> csrf.ignoringRequestMatchers(antMatcher("/engine-rest/**")))
				.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2ResourceServer -> oauth2ResourceServer
						.jwt(jwt -> jwt
								.decoder(jwtDecoder)
								.jwkSetUri(jwkSetUri)))
				.addFilterBefore(keycloakAuthenticationFilter(), AuthorizationFilter.class)
				.build();
	}

	/**
	 * Create a JWT decoder with issuer and audience claim validation.
	 * @return the JWT decoder
	 */
	@Bean
	public JwtDecoder jwtDecoder() {
		String issuerUri = applicationContext.getEnvironment().getRequiredProperty(
				"spring.security.oauth2.client.provider." + configProps.getProvider() + ".issuer-uri");
		
		NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder)
				JwtDecoders.fromOidcIssuerLocation(issuerUri);

		OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(configProps.getRequiredAudience());
		OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
		OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

		jwtDecoder.setJwtValidator(withAudience);

		return jwtDecoder;
	}

    /**
     * Creates the REST Api Keycloak Authentication Filter.
     * @return the filter
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public KeycloakAuthenticationFilter keycloakAuthenticationFilter(){
		String userNameAttribute = this.applicationContext.getEnvironment().getRequiredProperty(
			"spring.security.oauth2.client.provider." + this.configProps.getProvider() + ".user-name-attribute");

    	return new KeycloakAuthenticationFilter(this.identityService, this.clientService, userNameAttribute);
    }

}

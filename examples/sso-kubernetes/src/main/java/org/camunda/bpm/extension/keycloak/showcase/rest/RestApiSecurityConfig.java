package org.camunda.bpm.extension.keycloak.showcase.rest;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import jakarta.inject.Inject;
import org.camunda.bpm.engine.IdentityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Optional Security Configuration for Camunda REST Api.
 */
@Configuration
@EnableWebSecurity
@Order(SecurityProperties.BASIC_AUTH_ORDER - 20)
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
	public SecurityFilterChain httpSecurity(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
		String jwkSetUri = applicationContext.getEnvironment().getRequiredProperty(
				"spring.security.oauth2.client.provider." + configProps.getProvider() + ".jwk-set-uri");

		return http
				.csrf(csrf -> csrf
						.ignoringRequestMatchers(antMatcher("/api/**"), antMatcher("/engine-rest/**")))
				.authorizeHttpRequests(authorize -> authorize.
						requestMatchers(antMatcher("/engine-rest/**")).authenticated()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2ResourceServer -> oauth2ResourceServer
						.jwt(jwt -> jwt
								.decoder(jwtDecoder)
								.jwkSetUri(jwkSetUri)))
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
     * Registers the REST Api Keycloak Authentication Filter.
     * @return filter registration
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
	@Bean
    public FilterRegistrationBean keycloakAuthenticationFilter(){
        FilterRegistrationBean filterRegistration = new FilterRegistrationBean();
		
		String userNameAttribute = this.applicationContext.getEnvironment().getRequiredProperty(
			"spring.security.oauth2.client.provider." + this.configProps.getProvider() + ".user-name-attribute");
    
    	filterRegistration.setFilter(new KeycloakAuthenticationFilter(this.identityService, this.clientService, userNameAttribute));
        
        filterRegistration.setOrder(102); // make sure the filter is registered after the Spring Security Filter Chain
        filterRegistration.addUrlPatterns("/engine-rest/*");
        return filterRegistration;
    }
   
}

package org.cibseven.community.keycloak.showcase.sso;

import jakarta.inject.Inject;

import org.cibseven.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.cibseven.bpm.webapp.impl.security.auth.ContainerBasedAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.Collections;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

/**
 * Legacy Camunda Web application SSO configuration for usage with KeycloakIdentityProviderPlugin.
 */
@ConditionalOnMissingClass("org.springframework.test.context.junit.jupiter.SpringExtension")
@EnableWebSecurity
@Configuration
public class WebAppSecurityConfig {

	@Inject
	private KeycloakLogoutHandler keycloakLogoutHandler;

    private final String legacyWebappPath;

    public WebAppSecurityConfig(CamundaBpmProperties properties) {
      this.legacyWebappPath = properties.getWebapp().getLegacyApplicationPath();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain httpSecurity(HttpSecurity http) throws Exception {
        return http
            .securityMatcher(request -> {
                String fullPath = request.getServletPath() + (request.getPathInfo() != null ? request.getPathInfo() : "");
                return fullPath.startsWith(legacyWebappPath) || 
                       fullPath.startsWith(OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI) ||
                       fullPath.startsWith("/login") ||
                       fullPath.startsWith("/logout");
              })
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                        pathPattern(legacyWebappPath + "/api/**"),
                        pathPattern("/engine-rest/**")))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                        pathPattern(legacyWebappPath + "/assets/**"),
                        pathPattern(legacyWebappPath + "/app/**"),
                        pathPattern(legacyWebappPath + "/api/**"),
                        pathPattern(legacyWebappPath + "/lib/**"))
                .authenticated()
                .anyRequest()
                .permitAll())
            .oauth2Login(withDefaults())
            .logout(logout -> logout
                .logoutRequestMatcher(pathPattern(legacyWebappPath + "/app/*/*/logout"))
                .logoutSuccessHandler(keycloakLogoutHandler)
            )
            .build();
    }

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Bean
    public FilterRegistrationBean containerBasedAuthenticationFilter(){

        FilterRegistrationBean filterRegistration = new FilterRegistrationBean();
        filterRegistration.setFilter(new ContainerBasedAuthenticationFilter());
        filterRegistration.setInitParameters(Collections.singletonMap("authentication-provider", "org.cibseven.community.keycloak.showcase.sso.KeycloakAuthenticationProvider"));
        filterRegistration.setOrder(201); // make sure the filter is registered after the Spring Security Filter Chain
        filterRegistration.addUrlPatterns(legacyWebappPath + "/app/*");
        return filterRegistration;
    }
 
    // The ForwardedHeaderFilter is required to correctly assemble the redirect URL for OAUth2 login. 
	// Without the filter, Spring generates an HTTP URL even though the container route is accessed through HTTPS.
    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new ForwardedHeaderFilter());
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filterRegistrationBean;
    }
	
	@Bean
	@Order(0)
	public RequestContextListener requestContextListener() {
	    return new RequestContextListener();
	}

    // Modify firewall in order to allow request details for child groups
    @Bean
    public HttpFirewall getHttpFirewall() {
        StrictHttpFirewall strictHttpFirewall = new StrictHttpFirewall();
        strictHttpFirewall.setAllowUrlEncodedPercent(true);
        strictHttpFirewall.setAllowUrlEncodedSlash(true);
        return strictHttpFirewall;
    }
}
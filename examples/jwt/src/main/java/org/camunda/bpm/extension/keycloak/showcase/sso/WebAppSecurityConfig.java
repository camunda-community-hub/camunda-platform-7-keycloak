package org.camunda.bpm.extension.keycloak.showcase.sso;

import org.camunda.bpm.extension.keycloak.filter.StatelessAuthenticationFilter;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.web.context.request.RequestContextListener;

import javax.inject.Inject;
import javax.servlet.Filter;
import java.util.Collections;

/**
 * Camunda Web application SSO configuration for usage with KeycloakIdentityProviderPlugin.
 */
@ConditionalOnMissingClass("org.springframework.test.context.junit.jupiter.SpringExtension")
@Configuration
@Order(SecurityProperties.BASIC_AUTH_ORDER - 10)
public class WebAppSecurityConfig extends WebSecurityConfigurerAdapter {

	private static final int AFTER_SPRING_SECURITY_FILTER_CHAIN_ORDER = 201;
	private static final String API_FILTER_PATTERN = "/api/*";
	private static final String AUTHENTICATION_FILTER_NAME = "Authentication Filter";
	private static final String CSRF_PREVENTION_FILTER_NAME = "CsrfPreventionFilter";


	@Inject
	private CamundaBpmProperties camundaBpmProperties;

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		String path = camundaBpmProperties.getWebapp().getApplicationPath();
		http
				.authorizeRequests(authz -> authz
						.antMatchers( "/").permitAll()
						.antMatchers(path + "/app/**").permitAll()
						.antMatchers(path + "/lib/**").permitAll()
						.antMatchers(path + "/api/engine/engine/**").permitAll()
						.antMatchers(path + "/api/*/plugin/*/static/app/plugin.css").permitAll()
						.antMatchers(path + "/api/*/plugin/*/static/app/plugin.js").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Bean
    public FilterRegistrationBean containerBasedAuthenticationFilter(){
		String camundaWebappPath = camundaBpmProperties.getWebapp().getApplicationPath();

        FilterRegistrationBean filterRegistration = new FilterRegistrationBean();
        filterRegistration.setFilter(new StatelessAuthenticationFilter());
        filterRegistration.setInitParameters(Collections.singletonMap("authentication-provider", "org.camunda.bpm.extension.keycloak.showcase.sso.KeycloakAuthenticationProvider"));

		filterRegistration.setName(AUTHENTICATION_FILTER_NAME);
		filterRegistration.setOrder(AFTER_SPRING_SECURITY_FILTER_CHAIN_ORDER);
		filterRegistration.addUrlPatterns(camundaWebappPath + API_FILTER_PATTERN);
        return filterRegistration;
    }

	/**
	 * Configuration for disabling CSRF filter.
	 * In new Camunda version (7.11) was added special protection header with {@link org.camunda.bpm.webapp.impl.security.filter.CsrfPreventionFilter}
	 * that expects a CSRF Token on any modifying request coming through the /api/* url.
	 * If no CSRF Token is present, any requests mapped to "/api" will fail.
	 * To solve this problem, Camunda CsrfPreventionFilter must be overridden.
	 *
	 * @return servlet context initializer
	 * @see <a href="https://docs.camunda.org/manual/latest/update/minor/710-to-711/#http-header-security-in-webapps">Camunda docs</a>
	 * @see <a href="https://forum.camunda.org/t/how-to-disable-csrfpreventionfilter/13095/9">Solution from Camunda forum</a>
	 */
	@Bean
	public FilterRegistrationBean<Filter> csrfOverwriteFilter() {
		String camundaWebappPath = camundaBpmProperties.getWebapp().getApplicationPath();

		FilterRegistrationBean<Filter> filterRegistration = new FilterRegistrationBean<>();
		filterRegistration.setFilter((request, response, chain) -> chain.doFilter(request, response));
		filterRegistration.setName(CSRF_PREVENTION_FILTER_NAME);
		filterRegistration.setOrder(AFTER_SPRING_SECURITY_FILTER_CHAIN_ORDER);
		filterRegistration.addUrlPatterns(camundaWebappPath + API_FILTER_PATTERN);
		return filterRegistration;
	}

	@Bean
	@Order(0)
	public RequestContextListener requestContextListener() {
	    return new RequestContextListener();
	}
	
}
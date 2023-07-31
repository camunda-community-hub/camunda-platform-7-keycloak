package org.camunda.bpm.extension.keycloak;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.identity.IdentityProviderException;
import org.camunda.bpm.engine.impl.identity.ReadOnlyIdentityProvider;
import org.camunda.bpm.engine.impl.interceptor.Session;
import org.camunda.bpm.engine.impl.interceptor.SessionFactory;
import org.camunda.bpm.extension.keycloak.cache.CacheConfiguration;
import org.camunda.bpm.extension.keycloak.cache.CacheFactory;
import org.camunda.bpm.extension.keycloak.cache.QueryCache;
import org.camunda.bpm.extension.keycloak.rest.KeycloakRestTemplate;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Keycloak Identity Provider Session Factory.
 */
public class KeycloakIdentityProviderFactory implements SessionFactory {

	protected KeycloakConfiguration keycloakConfiguration;
	protected KeycloakContextProvider keycloakContextProvider;

	protected QueryCache<CacheableKeycloakUserQuery, List<User>> userQueryCache;
	protected QueryCache<CacheableKeycloakGroupQuery, List<Group>> groupQueryCache;
	protected QueryCache<CacheableKeycloakCheckPasswordCall, Boolean> checkPasswordCache;

	protected KeycloakRestTemplate restTemplate = new KeycloakRestTemplate();

	/**
	 * Creates a new Keycloak session factory.
	 * @param keycloakConfiguration the Keycloak configuration
	 * @param customHttpRequestInterceptors custom interceptors to modify behaviour of default KeycloakRestTemplate
	 */
	public KeycloakIdentityProviderFactory(
					KeycloakConfiguration keycloakConfiguration, List<ClientHttpRequestInterceptor> customHttpRequestInterceptors) {

		this.keycloakConfiguration = keycloakConfiguration;

		CacheConfiguration cacheConfiguration = CacheConfiguration.from(keycloakConfiguration);
		CacheConfiguration loginCacheConfiguration = CacheConfiguration.fromLoginConfigOf(keycloakConfiguration);

		this.setUserQueryCache(CacheFactory.create(cacheConfiguration));
		this.setGroupQueryCache(CacheFactory.create(cacheConfiguration));
		this.setCheckPasswordCache(CacheFactory.create(loginCacheConfiguration));

		// Create REST template with pooling HTTP client
		PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder
				.create()
				.setMaxConnTotal(keycloakConfiguration.getMaxHttpConnections());

		if (keycloakConfiguration.isDisableSSLCertificateValidation()) {
			try {
			SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(new TrustAllStrategy()).build();
			SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext,
					NoopHostnameVerifier.INSTANCE);
			connectionManagerBuilder.setSSLSocketFactory(sslConnectionSocketFactory);
			} catch (GeneralSecurityException e) {
				throw new IdentityProviderException("Disabling SSL certificate validation failed", e);
			}
		}

		final CredentialsStore credentialsProvider = new BasicCredentialsProvider();
		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
				//.setRedirectStrategy(new LaxRedirectStrategy())
				.setConnectionManager(connectionManagerBuilder.build())
				.setDefaultCredentialsProvider(credentialsProvider);

		// configure proxy if set
		if (StringUtils.hasLength(keycloakConfiguration.getProxyUri())) {
			final URI proxyUri = URI.create(keycloakConfiguration.getProxyUri());
			final HttpHost proxy = new HttpHost(proxyUri.getScheme(), proxyUri.getHost(), proxyUri.getPort());
			httpClientBuilder.setProxy(proxy);
			// configure proxy auth if set
			if (StringUtils.hasLength(keycloakConfiguration.getProxyUser()) && keycloakConfiguration.getProxyPassword() != null) {
				credentialsProvider.setCredentials(
						new AuthScope(proxyUri.getHost(), proxyUri.getPort()),
						new UsernamePasswordCredentials(keycloakConfiguration.getProxyUser(),
								keycloakConfiguration.getProxyPassword().toCharArray())
				);
			}
		}

		final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
		restTemplate.setRequestFactory(factory);

		// replace ISO-8859-1 encoding with configured charset (default: UTF-8)
		for (int i = 0; i < restTemplate.getMessageConverters().size(); i++) {
			if (restTemplate.getMessageConverters().get(i) instanceof StringHttpMessageConverter) {
				restTemplate.getMessageConverters().set(i, new StringHttpMessageConverter(Charset.forName(keycloakConfiguration.getCharset())));
				break;
			}
		}

		restTemplate.getInterceptors().addAll(customHttpRequestInterceptors);
		
		// Create Keycloak context provider for access token handling
		keycloakContextProvider = new KeycloakContextProvider(keycloakConfiguration, restTemplate);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<?> getSessionType() {
		return ReadOnlyIdentityProvider.class;
	}

	/**
	 * @param userQueryCache set the queryCache for user queries 
	 */
	public void setUserQueryCache(QueryCache<CacheableKeycloakUserQuery, List<User>> userQueryCache) {
		this.userQueryCache = userQueryCache;
	}

	/**
	 * @param groupQueryCache set the queryCache for group queries
	 */
	public void setGroupQueryCache(QueryCache<CacheableKeycloakGroupQuery, List<Group>> groupQueryCache) {
		this.groupQueryCache = groupQueryCache;
	}

	/**
	 * @param checkPasswordCache set the cache for check password function
	 */
	public void setCheckPasswordCache(QueryCache<CacheableKeycloakCheckPasswordCall, Boolean> checkPasswordCache) {
		this.checkPasswordCache = checkPasswordCache;
	}
	
	/**
	 * immediately clear entries from cache
	 */
	public void clearCache() {
		this.userQueryCache.clear();
		this.groupQueryCache.clear();
		this.checkPasswordCache.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Session openSession() {
		return new KeycloakIdentityProviderSession(
						keycloakConfiguration, restTemplate, keycloakContextProvider, userQueryCache, groupQueryCache, checkPasswordCache);
	}

}

package org.camunda.bpm.extension.keycloak;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.TrustStrategy;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.identity.IdentityProviderException;
import org.camunda.bpm.engine.impl.identity.ReadOnlyIdentityProvider;
import org.camunda.bpm.engine.impl.interceptor.Session;
import org.camunda.bpm.engine.impl.interceptor.SessionFactory;
import org.camunda.bpm.extension.keycloak.cache.CacheFactory;
import org.camunda.bpm.extension.keycloak.cache.QueryCache;
import org.camunda.bpm.extension.keycloak.rest.KeycloakRestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.StringUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Keycloak Identity Provider Session Factory.
 */
public class KeycloakIdentityProviderFactory implements SessionFactory {

	protected KeycloakConfiguration keycloakConfiguration;
	protected KeycloakContextProvider keycloakContextProvider;

	protected QueryCache<KeycloakUserQueryProxy, List<User>> userQueryCache;
	protected QueryCache<KeycloakGroupQueryProxy, List<Group>> groupQueryCache;

	protected KeycloakRestTemplate restTemplate = new KeycloakRestTemplate();

	/**
	 * Creates a new Keycloak session factory.
	 * @param keycloakConfiguration the Keycloak configuration
	 */
	public KeycloakIdentityProviderFactory(KeycloakConfiguration keycloakConfiguration) {
		this.keycloakConfiguration = keycloakConfiguration;
		this.userQueryCache = CacheFactory.create(keycloakConfiguration.getCache());
		this.groupQueryCache = CacheFactory.create(keycloakConfiguration.getCache());

		// Create REST template with pooling HTTP client
		final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		HttpClientBuilder httpClient = HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy());
		if (keycloakConfiguration.isDisableSSLCertificateValidation()) {
			try {
				TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
				SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom()
				        .loadTrustMaterial(null, acceptingTrustStrategy)
				        .build();
				HostnameVerifier allowAllHosts = new NoopHostnameVerifier();
				Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
				        .<ConnectionSocketFactory> create()
						.register("https", new SSLConnectionSocketFactory(sslContext, allowAllHosts))
						.register("http", new PlainConnectionSocketFactory()) // needed if http proxy is in place
				        .build();
				final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
				connectionManager.setMaxTotal(keycloakConfiguration.getMaxHttpConnections());
				httpClient.setConnectionManager(connectionManager);
			} catch (GeneralSecurityException e) {
				throw new IdentityProviderException("Disabling SSL certificate validation failed", e);
			}
		} else {
			final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
			connectionManager.setMaxTotal(keycloakConfiguration.getMaxHttpConnections());
			httpClient.setConnectionManager(connectionManager);
		}

		// confgure proxy if set
		if (StringUtils.hasLength(keycloakConfiguration.getProxyUri())) {
			final URI proxyUri = URI.create(keycloakConfiguration.getProxyUri());
			final HttpHost proxy = new HttpHost(proxyUri.getHost(), proxyUri.getPort(), proxyUri.getScheme());
			httpClient.setProxy(proxy);
			// configure proxy auth if set
			if (StringUtils.hasLength(keycloakConfiguration.getProxyUser()) && keycloakConfiguration.getProxyPassword() != null) {
				final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
				credentialsProvider.setCredentials(
						new AuthScope(proxyUri.getHost(), proxyUri.getPort()),
						new UsernamePasswordCredentials(keycloakConfiguration.getProxyUser(), keycloakConfiguration.getProxyPassword())
				);
				httpClient.setDefaultCredentialsProvider(credentialsProvider)
						.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
			}
		}

		factory.setHttpClient(httpClient.build());
		restTemplate.setRequestFactory(factory);

		// replace ISO-8859-1 encoding with configured charset (default: UTF-8)
		for (int i = 0; i < restTemplate.getMessageConverters().size(); i++) {
			if (restTemplate.getMessageConverters().get(i) instanceof StringHttpMessageConverter) {
				restTemplate.getMessageConverters().set(i, new StringHttpMessageConverter(Charset.forName(keycloakConfiguration.getCharset())));
				break;
			}
		}

		restTemplate.getInterceptors().addAll(keycloakConfiguration.getCustomHttpRequestInterceptors());
		
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
	 * immediately clear entries from cache
	 */
	public void clearCache() {
		this.userQueryCache.clear();
		this.groupQueryCache.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Session openSession() {
		return new KeycloakIdentityProviderSession(
						keycloakConfiguration, restTemplate, keycloakContextProvider, userQueryCache, groupQueryCache);
	}

}

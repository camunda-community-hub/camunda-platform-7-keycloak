package org.camunda.bpm.extension.keycloak.showcase.test.bpm.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.AbstractAssertions.init;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.http.entity.ContentType;
import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.engine.ProcessEngine;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Tests the security of the engine's REST interface.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@ActiveProfiles(profiles = "engine-rest")
@Disabled("Runs only with Keycloak backend")
public class RestApiSecurityConfigTest {

	private static final String REST_API_USER = "camunda";
	private static final String REST_API_PWD = "camunda1!";
	
	@Autowired
	private ProcessEngine processEngine;
	
	private  RestTemplate restTemplate = new RestTemplate();

	@Value("${spring.security.oauth2.client.provider.keycloak.token-uri}")
	private String accessTokenUri;
	
	@Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
	private String clientId;
	
	@Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
	private String clientSecret;

	/** Local server's HTTP port allocated at runtime. */
	@LocalServerPort
	int serverPort;
	
	static {
		LogFactory.useSlf4jLogging(); // MyBatis
	}

	@BeforeEach
	public void setup() {
		// init BPM assert
		init(processEngine);
	}

	/**
	 * Returns the complete URL of a given engine rest resource.
	 * @param path the path to test
	 * @return the complete URL
	 */
	private String getEngineRestUrl(String path) {
		return "http://localhost:" + serverPort + "/engine-rest/" + path;
	}

	// ---------------------------------------------------------------------------
	// Tests
	// ---------------------------------------------------------------------------

	@Test
	public void testSecuredRestApi_Accepted() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + getToken());
		ResponseEntity<String> response = restTemplate.exchange(getEngineRestUrl("engine"), 
				HttpMethod.GET, new HttpEntity<>(headers), String.class);
		assertEquals("[{\"name\":\"default\"}]", response.getBody());
	}
	
	@Test
	public void testUnSecuredRestApi_Denied() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		try {
			restTemplate.exchange(getEngineRestUrl("engine"), 
					HttpMethod.GET, new HttpEntity<>(headers), String.class);
			fail("Expected Status 401 Unauthorized");
		} catch (HttpClientErrorException ex) {
			assertEquals(ex.getStatusCode(), HttpStatus.UNAUTHORIZED);
		}
	}

	// ---------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------

	private String getToken() throws Exception {
		HttpHeaders headers = new HttpHeaders();
	    headers.add(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.toString());
	    HttpEntity<String> request = new HttpEntity<>(
	    		"client_id=" + clientId
	    		+ "&client_secret=" + clientSecret
	    		+ "&username=" + REST_API_USER
	    		+ "&password=" + REST_API_PWD
	    		+ "&grant_type=password",
				headers);
	    ResponseEntity<String> response = restTemplate.postForEntity(accessTokenUri, request, String.class);
	    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	    
	    JSONObject json = new JSONObject(response.getBody());
		return json.getString("access_token");
	}
}

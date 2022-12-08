package org.camunda.bpm.extension.keycloak.auth;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.camunda.bpm.engine.rest.security.auth.impl.ContainerBasedAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * OAuth2 Authentication Provider for usage with Keycloak JWT.
 */
public class KeycloakJwtAuthenticationProvider extends ContainerBasedAuthenticationProvider {

    @Override
    public AuthenticationResult extractAuthenticatedUser(HttpServletRequest request, ProcessEngine engine) {
        // Extract user-name-attribute of the OAuth2 token
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof AbstractOAuth2TokenAuthenticationToken) || !(authentication.getPrincipal() instanceof Jwt)) {
			return AuthenticationResult.unsuccessful();
		}
        String userId = ((Jwt)authentication.getPrincipal()).getClaimAsString("preferred_username");
        if (!StringUtils.hasLength(userId)) {
            return AuthenticationResult.unsuccessful();
        }

        // Authentication successful
        AuthenticationResult authenticationResult = new AuthenticationResult(userId, true);
        authenticationResult.setGroups(((Jwt)authentication.getPrincipal()).getClaimAsStringList("groups")
                .stream().map(g -> g.startsWith("/") ? g.substring(1) : g).collect(Collectors.toList()));

        return authenticationResult;
    }

}
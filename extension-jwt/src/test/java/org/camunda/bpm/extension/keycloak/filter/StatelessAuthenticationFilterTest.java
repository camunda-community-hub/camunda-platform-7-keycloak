package org.camunda.bpm.extension.keycloak.filter;

import org.camunda.bpm.cockpit.Cockpit;
import org.camunda.bpm.cockpit.impl.DefaultCockpitRuntimeDelegate;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationProvider;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.camunda.bpm.extension.keycloak.auth.KeycloakJwtAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class StatelessAuthenticationFilterTest extends PluggableProcessEngineTestCase {

    private final AuthenticationProvider authenticationProvider = mock(AuthenticationProvider.class);

    private final KeycloakJwtAuthenticationFilter statelessAuthenticationFilter = new KeycloakJwtAuthenticationFilter() {
        {
            authenticationProvider = StatelessAuthenticationFilterTest.this.authenticationProvider;
        }
    };

    @Override
    protected void setUp() {
        Cockpit.setCockpitRuntimeDelegate(new DefaultCockpitRuntimeDelegate());
        IdentityService identityService = getProcessEngine().getIdentityService();
        identityService.saveUser(identityService.newUser("user"));
    }
    
    @Override
    protected void tearDown() {
    	getProcessEngine().getIdentityService().deleteUser("user");
    }

    public void testDoFilterForProtectedResource() throws ServletException, IOException {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/test");
        when(httpServletRequest.getMethod()).thenReturn("GET");

        when(authenticationProvider.extractAuthenticatedUser(any(), any()))
                .thenReturn(new AuthenticationResult("user", true));

        FilterChain filterChain = spy(new FilterChain() {
            @Override
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
                Authentication currentAuthentication = processEngine.getIdentityService().getCurrentAuthentication();
                assertNotNull(currentAuthentication);
                assertEquals("user", currentAuthentication.getUserId());
            }
        });

        statelessAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        Authentication currentAuthentication = processEngine.getIdentityService().getCurrentAuthentication();
        assertNull(currentAuthentication);
    }

    public void testDoFilterForStaticResource() throws ServletException, IOException {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/cockpit/plugin/test/static/test");
        when(httpServletRequest.getMethod()).thenReturn("GET");

        when(authenticationProvider.extractAuthenticatedUser(any(), any()))
                .thenReturn(new AuthenticationResult("user", true));

        statelessAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse,
                (servletRequest, servletResponse) -> {
                    Authentication currentAuthentication = processEngine.getIdentityService().getCurrentAuthentication();
                    assertNull(currentAuthentication);
                });

        Authentication currentAuthentication = processEngine.getIdentityService().getCurrentAuthentication();
        assertNull(currentAuthentication);
    }

    public void testDoFilterForProtectedResourceWithoutUser() throws ServletException, IOException {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/test");
        when(httpServletRequest.getMethod()).thenReturn("GET");

        when(authenticationProvider.extractAuthenticatedUser(any(), any()))
                .thenReturn(new AuthenticationResult(null, false));

        FilterChain filterChain = mock(FilterChain.class);

        statelessAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        verifyNoInteractions(filterChain);

        verify(httpServletResponse).setStatus(eq(401));
        Authentication currentAuthentication = processEngine.getIdentityService().getCurrentAuthentication();
        assertNull(currentAuthentication);
    }

}
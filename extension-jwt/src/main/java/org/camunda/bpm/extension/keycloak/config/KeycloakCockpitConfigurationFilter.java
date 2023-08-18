package org.camunda.bpm.extension.keycloak.config;

import org.springframework.web.client.RestTemplate;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;

public class KeycloakCockpitConfigurationFilter implements Filter {
    public static String KEYCLOAK_OPTIONS_PATH = "/app/keycloak/keycloak-options.json";
    public static String KEYCLOAK_JS_PATH = "/app/keycloak/keycloak.min.js";

    KeycloakCockpitConfiguration configuration;

    public KeycloakCockpitConfigurationFilter(KeycloakCockpitConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        PrintWriter out = response.getWriter();
        response.setCharacterEncoding("UTF-8");
        if (((HttpServletRequest) request).getRequestURI().endsWith("keycloak.min.js")) {
            response.setContentType("text/javascript");
            getKeycloakMinJs(out);
        } else {
            response.setContentType("application/json");
            out.print(configuration.toJSON());
        }
        out.flush();
    }

    private void getKeycloakMinJs(PrintWriter out) {
        try {
            out.print(new RestTemplate().getForObject(new URI(configuration.getKeycloakUrl() + "/js/keycloak.min.js"), String.class));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}

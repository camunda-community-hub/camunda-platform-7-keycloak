package org.camunda.bpm.extension.keycloak.config;

public class KeycloakCockpitConfiguration {

    private String keycloakUrl;
    private String realm;
    private String clientId;

    public String getKeycloakUrl() {
        return keycloakUrl;
    }

    public void setKeycloakUrl(String keycloakUrl) {
        this.keycloakUrl = keycloakUrl;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String toJSON() {
        return new StringBuffer()
                .append("{\"url\":\"").append(keycloakUrl)
                .append("\",\"realm\":\"").append(realm)
                .append("\",\"clientId\":\"").append(clientId)
                .append("\"}").toString();
    }
}

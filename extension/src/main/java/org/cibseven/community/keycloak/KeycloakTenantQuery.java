package org.cibseven.community.keycloak;

import java.util.List;

import org.cibseven.bpm.engine.identity.Tenant;
import org.cibseven.bpm.engine.impl.Page;
import org.cibseven.bpm.engine.impl.TenantQueryImpl;
import org.cibseven.bpm.engine.impl.interceptor.CommandContext;
import org.cibseven.bpm.engine.impl.interceptor.CommandExecutor;

/**
 * Tenant query for the Keycloak plugin. Tenants are mapped to Keycloak organizations.
 */
public class KeycloakTenantQuery extends TenantQueryImpl {

  private static final long serialVersionUID = 1L;

  public KeycloakTenantQuery() {
    super();
  }

  public KeycloakTenantQuery(CommandExecutor commandExecutor) {
    super(commandExecutor);
  }

  @Override
  public long executeCount(CommandContext commandContext) {
    return getIdentityProvider(commandContext).findTenants(this).size();
  }

  @Override
  public List<Tenant> executeList(CommandContext commandContext, Page page) {
    return getIdentityProvider(commandContext).findTenants(this);
  }

  protected KeycloakIdentityProviderSession getIdentityProvider(CommandContext commandContext) {
    return (KeycloakIdentityProviderSession) commandContext.getReadOnlyIdentityProvider();
  }

}

package org.camunda.bpm.extension.keycloak;

import java.util.Collections;
import java.util.List;

import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.impl.Page;
import org.camunda.bpm.engine.impl.TenantQueryImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;

/**
 * Since multi-tenancy is currently not yet supported for the Keycloak plugin, the query always
 * returns <code>0</code> or an empty list.
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
    return 0;
  }

  @Override
  public List<Tenant> executeList(CommandContext commandContext, Page page) {
    return Collections.emptyList();
  }

}

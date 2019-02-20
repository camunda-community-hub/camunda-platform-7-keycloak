package de.vonderbeck.bpm.identity.keycloak;

import java.util.List;

import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.impl.GroupQueryImpl;
import org.camunda.bpm.engine.impl.Page;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;

/**
 * Keycloak specific group query implementation.
 */
public class KeycloakGroupQuery extends GroupQueryImpl {

	private static final long serialVersionUID = 1L;

	public KeycloakGroupQuery() {
		super();
	}

	public KeycloakGroupQuery(CommandExecutor commandExecutor) {
		super(commandExecutor);
	}

	// execute queries ////////////////////////////

	public long executeCount(CommandContext commandContext) {
		final KeycloakIdentityProviderSession identityProvider = getKeycloakIdentityProvider(commandContext);
		return identityProvider.findGroupCountByQueryCriteria(this);
	}

	public List<Group> executeList(CommandContext commandContext, Page page) {
		final KeycloakIdentityProviderSession identityProvider = getKeycloakIdentityProvider(commandContext);
		return identityProvider.findGroupByQueryCriteria(this);
	}

	protected KeycloakIdentityProviderSession getKeycloakIdentityProvider(CommandContext commandContext) {
		return (KeycloakIdentityProviderSession) commandContext.getReadOnlyIdentityProvider();
	}

	// unimplemented features //////////////////////////////////

	@Override
	public GroupQuery memberOfTenant(String tenantId) {
		  throw new UnsupportedOperationException("The Keycloak identity provider does currently not support tenant queries.");
	}

}

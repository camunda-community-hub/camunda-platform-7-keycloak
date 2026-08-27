package org.cibseven.community.keycloak;

import java.util.List;

import org.cibseven.bpm.engine.identity.User;
import org.cibseven.bpm.engine.identity.UserQuery;
import org.cibseven.bpm.engine.impl.Page;
import org.cibseven.bpm.engine.impl.UserQueryImpl;
import org.cibseven.bpm.engine.impl.interceptor.CommandContext;
import org.cibseven.bpm.engine.impl.interceptor.CommandExecutor;

/**
 * Keycloak specific user query implementation.
 */
public class KeycloakUserQuery extends UserQueryImpl {

	private static final long serialVersionUID = 1L;

	public KeycloakUserQuery() {
		super();
	}

	public KeycloakUserQuery(CommandExecutor commandExecutor) {
		super(commandExecutor);
	}

	// execute queries /////////////////////////////////////////

	@Override
	public long executeCount(CommandContext commandContext) {
		final KeycloakIdentityProviderSession provider = getKeycloakIdentityProvider(commandContext);
		return provider.findUserCountByQueryCriteria(this);
	}

	@Override
	public List<User> executeList(CommandContext commandContext, Page page) {
		final KeycloakIdentityProviderSession provider = getKeycloakIdentityProvider(commandContext);
		return provider.findUserByQueryCriteria(this);
	}

	protected KeycloakIdentityProviderSession getKeycloakIdentityProvider(CommandContext commandContext) {
		return (KeycloakIdentityProviderSession) commandContext.getReadOnlyIdentityProvider();
	}

	// unimplemented features //////////////////////////////////

	@Override
	public UserQuery memberOfTenant(String tenantId) {
		throw new UnsupportedOperationException("The Keycloak identity provider does currently not support tenant queries.");
	}

	@Override
	public UserQuery potentialStarter(String procDefId) {
		throw new UnsupportedOperationException("The Keycloak identity provider does currently not support potential starter queries.");
	}

}

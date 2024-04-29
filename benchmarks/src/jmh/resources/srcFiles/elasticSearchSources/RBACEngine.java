/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.security.authz;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.elasticsearch.ElasticsearchRoleRestrictionException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRunnable;
import org.elasticsearch.action.AliasesRequest;
import org.elasticsearch.action.CompositeIndicesRequest;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.bulk.BulkShardRequest;
import org.elasticsearch.action.bulk.SimulateBulkAction;
import org.elasticsearch.action.bulk.TransportBulkAction;
import org.elasticsearch.action.delete.TransportDeleteAction;
import org.elasticsearch.action.get.TransportMultiGetAction;
import org.elasticsearch.action.index.TransportIndexAction;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.search.SearchTransportService;
import org.elasticsearch.action.search.TransportClearScrollAction;
import org.elasticsearch.action.search.TransportClosePointInTimeAction;
import org.elasticsearch.action.search.TransportMultiSearchAction;
import org.elasticsearch.action.search.TransportSearchScrollAction;
import org.elasticsearch.action.termvectors.MultiTermVectorsAction;
import org.elasticsearch.cluster.metadata.IndexAbstraction;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.CachedSupplier;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.transport.TransportActionProxy;
import org.elasticsearch.transport.TransportRequest;
import org.elasticsearch.xpack.core.async.TransportDeleteAsyncResultAction;
import org.elasticsearch.xpack.core.eql.EqlAsyncActionNames;
import org.elasticsearch.xpack.core.esql.EsqlAsyncActionNames;
import org.elasticsearch.xpack.core.search.action.GetAsyncSearchAction;
import org.elasticsearch.xpack.core.search.action.GetAsyncStatusAction;
import org.elasticsearch.xpack.core.search.action.SubmitAsyncSearchAction;
import org.elasticsearch.xpack.core.security.action.apikey.GetApiKeyAction;
import org.elasticsearch.xpack.core.security.action.apikey.GetApiKeyRequest;
import org.elasticsearch.xpack.core.security.action.user.AuthenticateAction;
import org.elasticsearch.xpack.core.security.action.user.AuthenticateRequest;
import org.elasticsearch.xpack.core.security.action.user.GetUserPrivilegesAction;
import org.elasticsearch.xpack.core.security.action.user.GetUserPrivilegesResponse;
import org.elasticsearch.xpack.core.security.action.user.HasPrivilegesAction;
import org.elasticsearch.xpack.core.security.action.user.HasPrivilegesRequest;
import org.elasticsearch.xpack.core.security.action.user.UserRequest;
import org.elasticsearch.xpack.core.security.authc.Authentication;
import org.elasticsearch.xpack.core.security.authc.Authentication.AuthenticationType;
import org.elasticsearch.xpack.core.security.authc.AuthenticationField;
import org.elasticsearch.xpack.core.security.authc.Subject;
import org.elasticsearch.xpack.core.security.authc.esnative.NativeRealmSettings;
import org.elasticsearch.xpack.core.security.authz.AuthorizationEngine;
import org.elasticsearch.xpack.core.security.authz.IndicesAndAliasesResolverField;
import org.elasticsearch.xpack.core.security.authz.ResolvedIndices;
import org.elasticsearch.xpack.core.security.authz.RoleDescriptor;
import org.elasticsearch.xpack.core.security.authz.RoleDescriptorsIntersection;
import org.elasticsearch.xpack.core.security.authz.accesscontrol.IndicesAccessControl;
import org.elasticsearch.xpack.core.security.authz.permission.FieldPermissionsCache;
import org.elasticsearch.xpack.core.security.authz.permission.FieldPermissionsDefinition;
import org.elasticsearch.xpack.core.security.authz.permission.IndicesPermission;
import org.elasticsearch.xpack.core.security.authz.permission.IndicesPermission.IsResourceAuthorizedPredicate;
import org.elasticsearch.xpack.core.security.authz.permission.RemoteIndicesPermission;
import org.elasticsearch.xpack.core.security.authz.permission.ResourcePrivileges;
import org.elasticsearch.xpack.core.security.authz.permission.ResourcePrivilegesMap;
import org.elasticsearch.xpack.core.security.authz.permission.Role;
import org.elasticsearch.xpack.core.security.authz.permission.SimpleRole;
import org.elasticsearch.xpack.core.security.authz.privilege.ApplicationPrivilege;
import org.elasticsearch.xpack.core.security.authz.privilege.ApplicationPrivilegeDescriptor;
import org.elasticsearch.xpack.core.security.authz.privilege.ClusterPrivilege;
import org.elasticsearch.xpack.core.security.authz.privilege.ClusterPrivilegeResolver;
import org.elasticsearch.xpack.core.security.authz.privilege.ConfigurableClusterPrivilege;
import org.elasticsearch.xpack.core.security.authz.privilege.NamedClusterPrivilege;
import org.elasticsearch.xpack.core.security.authz.privilege.Privilege;
import org.elasticsearch.xpack.core.security.support.StringMatcher;
import org.elasticsearch.xpack.core.sql.SqlAsyncActionNames;
import org.elasticsearch.xpack.security.action.user.TransportChangePasswordAction;
import org.elasticsearch.xpack.security.authc.esnative.ReservedRealm;
import org.elasticsearch.xpack.security.authz.store.CompositeRolesStore;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.elasticsearch.common.Strings.arrayToCommaDelimitedString;
import static org.elasticsearch.core.Strings.format;
import static org.elasticsearch.xpack.core.security.authc.Authentication.getAuthenticationFromCrossClusterAccessMetadata;
import static org.elasticsearch.xpack.security.audit.logfile.LoggingAuditTrail.PRINCIPAL_ROLES_FIELD_NAME;

public class RBACEngine implements AuthorizationEngine {

    private static final Predicate<String> SAME_USER_PRIVILEGE = StringMatcher.of(
        TransportChangePasswordAction.TYPE.name(),
        AuthenticateAction.NAME,
        HasPrivilegesAction.NAME,
        GetUserPrivilegesAction.NAME,
        GetApiKeyAction.NAME
    );
    private static final String INDEX_SUB_REQUEST_PRIMARY = TransportIndexAction.NAME + "[p]";
    private static final String INDEX_SUB_REQUEST_REPLICA = TransportIndexAction.NAME + "[r]";
    private static final String DELETE_SUB_REQUEST_PRIMARY = TransportDeleteAction.NAME + "[p]";
    private static final String DELETE_SUB_REQUEST_REPLICA = TransportDeleteAction.NAME + "[r]";

    private static final Logger logger = LogManager.getLogger(RBACEngine.class);
    private final Settings settings;
    private final CompositeRolesStore rolesStore;
    private final FieldPermissionsCache fieldPermissionsCache;
    private final LoadAuthorizedIndicesTimeChecker.Factory authzIndicesTimerFactory;

    public RBACEngine(
        Settings settings,
        CompositeRolesStore rolesStore,
        FieldPermissionsCache fieldPermissionsCache,
        LoadAuthorizedIndicesTimeChecker.Factory authzIndicesTimerFactory
    ) {
        this.settings = settings;
        this.rolesStore = rolesStore;
        this.fieldPermissionsCache = fieldPermissionsCache;
        this.authzIndicesTimerFactory = authzIndicesTimerFactory;
    }

    @Override
    public void resolveAuthorizationInfo(RequestInfo requestInfo, ActionListener<AuthorizationInfo> listener) {
        final Authentication authentication = requestInfo.getAuthentication();
        rolesStore.getRoles(authentication, listener.delegateFailureAndWrap((l, roleTuple) -> {
            if (roleTuple.v1() == Role.EMPTY_RESTRICTED_BY_WORKFLOW || roleTuple.v2() == Role.EMPTY_RESTRICTED_BY_WORKFLOW) {
                l.onFailure(new ElasticsearchRoleRestrictionException("access restricted by workflow"));
            } else {
                l.onResponse(new RBACAuthorizationInfo(roleTuple.v1(), roleTuple.v2()));
            }
        }));
    }

    @Override
    public void resolveAuthorizationInfo(Subject subject, ActionListener<AuthorizationInfo> listener) {
        rolesStore.getRole(subject, listener.map(role -> new RBACAuthorizationInfo(role, role)));
    }

    @Override
    public void authorizeRunAs(RequestInfo requestInfo, AuthorizationInfo authorizationInfo, ActionListener<AuthorizationResult> listener) {
        if (authorizationInfo instanceof RBACAuthorizationInfo) {
            final Role role = ((RBACAuthorizationInfo) authorizationInfo).getAuthenticatedUserAuthorizationInfo().getRole();
            listener.onResponse(
                new AuthorizationResult(role.checkRunAs(requestInfo.getAuthentication().getEffectiveSubject().getUser().principal()))
            );
        } else {
            listener.onFailure(
                new IllegalArgumentException("unsupported authorization info:" + authorizationInfo.getClass().getSimpleName())
            );
        }
    }

    @Override
    public void authorizeClusterAction(
        RequestInfo requestInfo,
        AuthorizationInfo authorizationInfo,
        ActionListener<AuthorizationResult> listener
    ) {
        if (authorizationInfo instanceof RBACAuthorizationInfo) {
            final Role role = ((RBACAuthorizationInfo) authorizationInfo).getRole();
            if (role.checkClusterAction(requestInfo.getAction(), requestInfo.getRequest(), requestInfo.getAuthentication())) {
                listener.onResponse(AuthorizationResult.granted());
            } else if (checkSameUserPermissions(requestInfo.getAction(), requestInfo.getRequest(), requestInfo.getAuthentication())) {
                listener.onResponse(AuthorizationResult.granted());
            } else if (GetAsyncStatusAction.NAME.equals(requestInfo.getAction()) && role.checkIndicesAction(SubmitAsyncSearchAction.NAME)) {
                listener.onResponse(AuthorizationResult.granted());
            } else {
                listener.onResponse(AuthorizationResult.deny());
            }
        } else {
            listener.onFailure(
                new IllegalArgumentException("unsupported authorization info:" + authorizationInfo.getClass().getSimpleName())
            );
        }
    }

    static boolean checkSameUserPermissions(String action, TransportRequest request, Authentication authentication) {
        final boolean actionAllowed = SAME_USER_PRIVILEGE.test(action);
        if (actionAllowed) {
            if (request instanceof AuthenticateRequest) {
                return true;
            } else if (request instanceof UserRequest userRequest) {
                String[] usernames = userRequest.usernames();
                if (usernames == null || usernames.length != 1 || usernames[0] == null) {
                    assert false : "this role should only be used for actions to apply to a single user";
                    return false;
                }
                final String username = usernames[0];
                if (authentication.isCrossClusterAccess() && HasPrivilegesAction.NAME.equals(action)) {
                    assert request instanceof HasPrivilegesRequest;
                    return getAuthenticationFromCrossClusterAccessMetadata(authentication).getEffectiveSubject()
                        .getUser()
                        .principal()
                        .equals(username);
                }

                final boolean sameUsername = authentication.getEffectiveSubject().getUser().principal().equals(username);
                if (sameUsername && TransportChangePasswordAction.TYPE.name().equals(action)) {
                    return checkChangePasswordAction(authentication);
                }

                assert AuthenticateAction.NAME.equals(action)
                    || HasPrivilegesAction.NAME.equals(action)
                    || GetUserPrivilegesAction.NAME.equals(action)
                    || sameUsername == false : "Action '" + action + "' should not be possible when sameUsername=" + sameUsername;
                return sameUsername;
            } else if (request instanceof GetApiKeyRequest getApiKeyRequest) {
                if (authentication.isApiKey()) {
                    String authenticatedApiKeyId = (String) authentication.getAuthenticatingSubject()
                        .getMetadata()
                        .get(AuthenticationField.API_KEY_ID_KEY);
                    if (Strings.hasText(getApiKeyRequest.getApiKeyId())) {
                        return getApiKeyRequest.getApiKeyId().equals(authenticatedApiKeyId) && false == getApiKeyRequest.withLimitedBy();
                    } else {
                        return false;
                    }
                }
            } else {
                assert false : "right now only a user request or get api key request should be allowed";
                return false;
            }
        }
        return false;
    }

    private static boolean shouldAuthorizeIndexActionNameOnly(String action, TransportRequest request) {
        switch (action) {
            case TransportBulkAction.NAME:
            case SimulateBulkAction.NAME:
            case TransportIndexAction.NAME:
            case TransportDeleteAction.NAME:
            case INDEX_SUB_REQUEST_PRIMARY:
            case INDEX_SUB_REQUEST_REPLICA:
            case DELETE_SUB_REQUEST_PRIMARY:
            case DELETE_SUB_REQUEST_REPLICA:
            case TransportMultiGetAction.NAME:
            case MultiTermVectorsAction.NAME:
            case TransportMultiSearchAction.NAME:
            case "indices:data/read/mpercolate":
            case "indices:data/read/msearch/template":
            case "indices:data/read/search/template":
            case "indices:data/write/reindex":
            case "indices:data/read/sql":
            case "indices:data/read/sql/translate":
            case "indices:data/read/esql":
            case "indices:data/read/esql/compute":
                if (request instanceof BulkShardRequest) {
                    return false;
                }
                if (request instanceof CompositeIndicesRequest == false) {
                    throw new IllegalStateException(
                        "Composite and bulk actions must implement "
                            + CompositeIndicesRequest.class.getSimpleName()
                            + ", "
                            + request.getClass().getSimpleName()
                            + " doesn't. Action "
                            + action
                    );
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public void authorizeIndexAction(
        RequestInfo requestInfo,
        AuthorizationInfo authorizationInfo,
        AsyncSupplier<ResolvedIndices> indicesAsyncSupplier,
        Map<String, IndexAbstraction> aliasOrIndexLookup,
        ActionListener<IndexAuthorizationResult> listener
    ) {
        final String action = requestInfo.getAction();
        final TransportRequest request = requestInfo.getRequest();
        final Role role;
        try {
            role = ensureRBAC(authorizationInfo).getRole();
        } catch (Exception e) {
            listener.onFailure(e);
            return;
        }
        if (TransportActionProxy.isProxyAction(action) || shouldAuthorizeIndexActionNameOnly(action, request)) {
            listener.onResponse(role.checkIndicesAction(action) ? IndexAuthorizationResult.EMPTY : IndexAuthorizationResult.DENIED);
        } else if (request instanceof IndicesRequest == false) {
            if (isScrollRelatedAction(action)) {

                if (TransportSearchScrollAction.TYPE.name().equals(action)) {
                    ActionRunnable.supply(listener.delegateFailureAndWrap((l, parsedScrollId) -> {
                        if (parsedScrollId.hasLocalIndices()) {
                            l.onResponse(
                                role.checkIndicesAction(action) ? IndexAuthorizationResult.EMPTY : IndexAuthorizationResult.DENIED
                            );
                        } else {
                            l.onResponse(IndexAuthorizationResult.EMPTY);
                        }
                    }), ((SearchScrollRequest) request)::parseScrollId).run();
                } else {
                    listener.onResponse(IndexAuthorizationResult.EMPTY);
                }
            } else if (isAsyncRelatedAction(action)) {
                if (SubmitAsyncSearchAction.NAME.equals(action)) {
                    listener.onResponse(IndexAuthorizationResult.EMPTY);
                } else {
                    listener.onResponse(IndexAuthorizationResult.ALLOW_NO_INDICES);
                }
            } else if (action.equals(TransportClosePointInTimeAction.TYPE.name())) {
                listener.onResponse(IndexAuthorizationResult.ALLOW_NO_INDICES);
            } else {
                assert false
                    : "only scroll and async-search related requests are known indices api that don't "
                        + "support retrieving the indices they relate to";
                listener.onFailure(
                    new IllegalStateException(
                        "only scroll and async-search related requests are known indices "
                            + "api that don't support retrieving the indices they relate to"
                    )
                );
            }
        } else if (isChildActionAuthorizedByParentOnLocalNode(requestInfo, authorizationInfo)) {
            listener.onResponse(new IndexAuthorizationResult(requestInfo.getOriginatingAuthorizationContext().getIndicesAccessControl()));
        } else if (PreAuthorizationUtils.shouldPreAuthorizeChildByParentAction(requestInfo, authorizationInfo)) {
            listener.onResponse(new IndexAuthorizationResult(IndicesAccessControl.allowAll()));
        } else if (allowsRemoteIndices(request) || role.checkIndicesAction(action)) {
            indicesAsyncSupplier.getAsync(listener.delegateFailureAndWrap((delegateListener, resolvedIndices) -> {
                assert resolvedIndices.isEmpty() == false
                    : "every indices request needs to have its indices set thus the resolved indices must not be empty";
                if (resolvedIndices.isNoIndicesPlaceholder()) {
                    if (allowsRemoteIndices(request) && role.checkIndicesAction(action) == false) {
                        delegateListener.onResponse(IndexAuthorizationResult.DENIED);
                    } else {
                        delegateListener.onResponse(IndexAuthorizationResult.ALLOW_NO_INDICES);
                    }
                } else {
                    assert resolvedIndices.getLocal().stream().noneMatch(Regex::isSimpleMatchPattern)
                        || ((IndicesRequest) request).indicesOptions().expandWildcardExpressions() == false
                        || (request instanceof AliasesRequest aliasesRequest && aliasesRequest.expandAliasesWildcards() == false)
                        || (request instanceof IndicesAliasesRequest indicesAliasesRequest
                            && false == indicesAliasesRequest.getAliasActions()
                                .stream()
                                .allMatch(IndicesAliasesRequest.AliasActions::expandAliasesWildcards))
                        : "expanded wildcards for local indices OR the request should not expand wildcards at all";

                    IndexAuthorizationResult result = buildIndicesAccessControl(action, role, resolvedIndices, aliasOrIndexLookup);
                    if (requestInfo.getAuthentication().isCrossClusterAccess()
                        && request instanceof IndicesRequest.RemoteClusterShardRequest shardsRequest
                        && shardsRequest.shards() != null) {
                        for (ShardId shardId : shardsRequest.shards()) {
                            if (shardId != null && shardIdAuthorized(shardsRequest, shardId, result.getIndicesAccessControl()) == false) {
                                listener.onResponse(IndexAuthorizationResult.DENIED);
                                return;
                            }
                        }
                    }
                    delegateListener.onResponse(result);
                }
            }));
        } else {
            listener.onResponse(IndexAuthorizationResult.DENIED);
        }
    }

    private static boolean shardIdAuthorized(IndicesRequest request, ShardId shardId, IndicesAccessControl accessControl) {
        var shardIdAccessPermissions = accessControl.getIndexPermissions(shardId.getIndexName());
        if (shardIdAccessPermissions != null) {
            return true;
        }

        logger.warn(
            Strings.format(
                "bad request of type [%s], request's stated indices %s are authorized but specified internal shard "
                    + "ID %s is not authorized",
                request.getClass().getCanonicalName(),
                request.indices(),
                shardId
            )
        );
        return false;
    }

    private static boolean allowsRemoteIndices(TransportRequest transportRequest) {
        if (transportRequest instanceof IndicesRequest.SingleIndexNoWildcards single) {
            return single.allowsRemoteIndices();
        } else {
            return transportRequest instanceof IndicesRequest.Replaceable replaceable && replaceable.allowsRemoteIndices();
        }
    }

    private static boolean isChildActionAuthorizedByParentOnLocalNode(RequestInfo requestInfo, AuthorizationInfo authorizationInfo) {
        final AuthorizationContext parent = requestInfo.getOriginatingAuthorizationContext();
        if (parent == null) {
            return false;
        }

        final IndicesAccessControl indicesAccessControl = parent.getIndicesAccessControl();
        if (indicesAccessControl == null) {
            return false;
        }

        if (requestInfo.getAction().startsWith(parent.getAction()) == false) {
            return false;
        }

        if (authorizationInfo.equals(parent.getAuthorizationInfo()) == false) {
            return false;
        }

        final IndicesRequest indicesRequest;
        if (requestInfo.getRequest() instanceof IndicesRequest) {
            indicesRequest = (IndicesRequest) requestInfo.getRequest();
        } else {
            return false;
        }

        final String[] indices = indicesRequest.indices();
        if (indices == null || indices.length == 0) {
            return false;
        }

        if (Arrays.equals(IndicesAndAliasesResolverField.NO_INDICES_OR_ALIASES_ARRAY, indices)) {
            return false;
        }

        assert Arrays.stream(indices).noneMatch(Regex::isSimpleMatchPattern)
            || indicesRequest.indicesOptions().expandWildcardExpressions() == false
            || (indicesRequest instanceof AliasesRequest aliasesRequest && aliasesRequest.expandAliasesWildcards() == false)
            || (indicesRequest instanceof IndicesAliasesRequest indicesAliasesRequest
                && false == indicesAliasesRequest.getAliasActions()
                    .stream()
                    .allMatch(IndicesAliasesRequest.AliasActions::expandAliasesWildcards))
            : "child request with action ["
                + requestInfo.getAction()
                + "] contains unexpanded wildcards "
                + Arrays.stream(indices).filter(Regex::isSimpleMatchPattern).toList();

        return Arrays.stream(indices).allMatch(indicesAccessControl::hasIndexPermissions);
    }

    @Override
    public void loadAuthorizedIndices(
        RequestInfo requestInfo,
        AuthorizationInfo authorizationInfo,
        Map<String, IndexAbstraction> indicesLookup,
        ActionListener<AuthorizationEngine.AuthorizedIndices> listener
    ) {
        if (authorizationInfo instanceof RBACAuthorizationInfo) {
            final Role role = ((RBACAuthorizationInfo) authorizationInfo).getRole();
            listener.onResponse(
                resolveAuthorizedIndicesFromRole(role, requestInfo, indicesLookup, () -> authzIndicesTimerFactory.newTimer(requestInfo))
            );
        } else {
            listener.onFailure(
                new IllegalArgumentException("unsupported authorization info:" + authorizationInfo.getClass().getSimpleName())
            );
        }
    }

    @Override
    public void validateIndexPermissionsAreSubset(
        RequestInfo requestInfo,
        AuthorizationInfo authorizationInfo,
        Map<String, List<String>> indexNameToNewNames,
        ActionListener<AuthorizationResult> listener
    ) {
        if (authorizationInfo instanceof RBACAuthorizationInfo) {
            final Role role = ((RBACAuthorizationInfo) authorizationInfo).getRole();
            Map<String, Automaton> permissionMap = new HashMap<>();
            for (Entry<String, List<String>> entry : indexNameToNewNames.entrySet()) {
                Automaton existingPermissions = permissionMap.computeIfAbsent(entry.getKey(), role::allowedActionsMatcher);
                for (String alias : entry.getValue()) {
                    Automaton newNamePermissions = permissionMap.computeIfAbsent(alias, role::allowedActionsMatcher);
                    if (Operations.subsetOf(newNamePermissions, existingPermissions) == false) {
                        listener.onResponse(AuthorizationResult.deny());
                        return;
                    }
                }
            }
            listener.onResponse(AuthorizationResult.granted());
        } else {
            listener.onFailure(
                new IllegalArgumentException("unsupported authorization info:" + authorizationInfo.getClass().getSimpleName())
            );
        }
    }

    @Override
    public void checkPrivileges(
        AuthorizationInfo authorizationInfo,
        PrivilegesToCheck privilegesToCheck,
        Collection<ApplicationPrivilegeDescriptor> applicationPrivileges,
        ActionListener<PrivilegesCheckResult> originalListener
    ) {
        if (authorizationInfo instanceof RBACAuthorizationInfo == false) {
            originalListener.onFailure(
                new IllegalArgumentException("unsupported authorization info:" + authorizationInfo.getClass().getSimpleName())
            );
            return;
        }
        final Role userRole = ((RBACAuthorizationInfo) authorizationInfo).getRole();
        logger.trace(
            () -> format(
                "Check whether role [%s] has privileges [%s]",
                Strings.arrayToCommaDelimitedString(userRole.names()),
                privilegesToCheck
            )
        );

        final ActionListener<PrivilegesCheckResult> listener;
        if (userRole instanceof SimpleRole simpleRole) {
            final PrivilegesCheckResult result = simpleRole.checkPrivilegesWithCache(privilegesToCheck);
            if (result != null) {
                logger.debug(
                    () -> format(
                        "role [%s] has privileges check result in cache for check: [%s]",
                        arrayToCommaDelimitedString(userRole.names()),
                        privilegesToCheck
                    )
                );
                originalListener.onResponse(result);
                return;
            }
            listener = originalListener.delegateFailure((delegateListener, privilegesCheckResult) -> {
                try {
                    simpleRole.cacheHasPrivileges(settings, privilegesToCheck, privilegesCheckResult);
                } catch (Exception e) {
                    logger.error("Failed to cache check result for [{}]", privilegesToCheck);
                    delegateListener.onFailure(e);
                    return;
                }
                delegateListener.onResponse(privilegesCheckResult);
            });
        } else {
            listener = originalListener;
        }

        boolean allMatch = true;

        final Map<String, Boolean> clusterPrivilegesCheckResults = new HashMap<>();
        for (String checkAction : privilegesToCheck.cluster()) {
            boolean privilegeGranted = userRole.grants(ClusterPrivilegeResolver.resolve(checkAction));
            allMatch = allMatch && privilegeGranted;
            if (privilegesToCheck.runDetailedCheck()) {
                clusterPrivilegesCheckResults.put(checkAction, privilegeGranted);
            } else if (false == allMatch) {
                listener.onResponse(PrivilegesCheckResult.SOME_CHECKS_FAILURE_NO_DETAILS);
                return;
            }
        }

        final ResourcePrivilegesMap.Builder combineIndicesResourcePrivileges = privilegesToCheck.runDetailedCheck()
            ? ResourcePrivilegesMap.builder()
            : null;
        for (RoleDescriptor.IndicesPrivileges check : privilegesToCheck.index()) {
            boolean privilegesGranted = userRole.checkIndicesPrivileges(
                Sets.newHashSet(check.getIndices()),
                check.allowRestrictedIndices(),
                Sets.newHashSet(check.getPrivileges()),
                combineIndicesResourcePrivileges
            );
            allMatch = allMatch && privilegesGranted;
            if (false == privilegesToCheck.runDetailedCheck() && false == allMatch) {
                assert combineIndicesResourcePrivileges == null;
                listener.onResponse(PrivilegesCheckResult.SOME_CHECKS_FAILURE_NO_DETAILS);
                return;
            }
        }

        final Map<String, Collection<ResourcePrivileges>> privilegesByApplication = new HashMap<>();

        final Set<String> applicationNames = Arrays.stream(privilegesToCheck.application())
            .map(RoleDescriptor.ApplicationResourcePrivileges::getApplication)
            .collect(Collectors.toSet());
        for (String applicationName : applicationNames) {
            logger.debug(() -> format("Checking privileges for application [%s]", applicationName));
            final ResourcePrivilegesMap.Builder resourcePrivilegesMapBuilder = privilegesToCheck.runDetailedCheck()
                ? ResourcePrivilegesMap.builder()
                : null;
            for (RoleDescriptor.ApplicationResourcePrivileges p : privilegesToCheck.application()) {
                if (applicationName.equals(p.getApplication())) {
                    boolean privilegesGranted = userRole.checkApplicationResourcePrivileges(
                        applicationName,
                        Sets.newHashSet(p.getResources()),
                        Sets.newHashSet(p.getPrivileges()),
                        applicationPrivileges,
                        resourcePrivilegesMapBuilder
                    );
                    allMatch = allMatch && privilegesGranted;
                    if (false == privilegesToCheck.runDetailedCheck() && false == allMatch) {
                        listener.onResponse(PrivilegesCheckResult.SOME_CHECKS_FAILURE_NO_DETAILS);
                        return;
                    }
                }
            }
            if (resourcePrivilegesMapBuilder != null) {
                privilegesByApplication.put(
                    applicationName,
                    resourcePrivilegesMapBuilder.build().getResourceToResourcePrivileges().values()
                );
            }
        }

        if (privilegesToCheck.runDetailedCheck()) {
            assert combineIndicesResourcePrivileges != null;
            listener.onResponse(
                new PrivilegesCheckResult(
                    allMatch,
                    new PrivilegesCheckResult.Details(
                        clusterPrivilegesCheckResults,
                        combineIndicesResourcePrivileges.build().getResourceToResourcePrivileges(),
                        privilegesByApplication
                    )
                )
            );
        } else {
            assert allMatch;
            listener.onResponse(PrivilegesCheckResult.ALL_CHECKS_SUCCESS_NO_DETAILS);
        }
    }

    @Override
    public void getUserPrivileges(AuthorizationInfo authorizationInfo, ActionListener<GetUserPrivilegesResponse> listener) {
        if (authorizationInfo instanceof RBACAuthorizationInfo == false) {
            listener.onFailure(
                new IllegalArgumentException("unsupported authorization info:" + authorizationInfo.getClass().getSimpleName())
            );
        } else {
            final Role role = ((RBACAuthorizationInfo) authorizationInfo).getRole();
            final GetUserPrivilegesResponse getUserPrivilegesResponse;
            try {
                getUserPrivilegesResponse = buildUserPrivilegesResponseObject(role);
            } catch (UnsupportedOperationException e) {
                listener.onFailure(
                    new IllegalArgumentException(
                        "Cannot retrieve privileges for API keys with assigned role descriptors. "
                            + "Please use the Get API key information API https:
                        e
                    )
                );
                return;
            }
            listener.onResponse(getUserPrivilegesResponse);
        }
    }

    @Override
    public void getRoleDescriptorsIntersectionForRemoteCluster(
        final String remoteClusterAlias,
        final AuthorizationInfo authorizationInfo,
        final ActionListener<RoleDescriptorsIntersection> listener
    ) {
        if (authorizationInfo instanceof RBACAuthorizationInfo rbacAuthzInfo) {
            final Role role = rbacAuthzInfo.getRole();
            listener.onResponse(role.getRoleDescriptorsIntersectionForRemoteCluster(remoteClusterAlias));
        } else {
            listener.onFailure(
                new IllegalArgumentException("unsupported authorization info: " + authorizationInfo.getClass().getSimpleName())
            );
        }
    }

    static GetUserPrivilegesResponse buildUserPrivilegesResponseObject(Role userRole) {
        logger.trace(() -> "List privileges for role [" + arrayToCommaDelimitedString(userRole.names()) + "]");

        final Set<String> cluster = new TreeSet<>();
        final Set<ConfigurableClusterPrivilege> conditionalCluster = new HashSet<>();
        for (ClusterPrivilege privilege : userRole.cluster().privileges()) {
            if (privilege instanceof NamedClusterPrivilege) {
                cluster.add(((NamedClusterPrivilege) privilege).name());
            } else if (privilege instanceof ConfigurableClusterPrivilege) {
                conditionalCluster.add((ConfigurableClusterPrivilege) privilege);
            } else {
                throw new IllegalArgumentException(
                    "found unsupported cluster privilege : "
                        + privilege
                        + ((privilege != null) ? " of type " + privilege.getClass().getSimpleName() : "")
                );
            }
        }

        final Set<GetUserPrivilegesResponse.Indices> indices = new LinkedHashSet<>();
        for (IndicesPermission.Group group : userRole.indices().groups()) {
            indices.add(toIndices(group));
        }

        final Set<GetUserPrivilegesResponse.RemoteIndices> remoteIndices = new LinkedHashSet<>();
        for (RemoteIndicesPermission.RemoteIndicesGroup remoteIndicesGroup : userRole.remoteIndices().remoteIndicesGroups()) {
            for (IndicesPermission.Group group : remoteIndicesGroup.indicesPermissionGroups()) {
                remoteIndices.add(new GetUserPrivilegesResponse.RemoteIndices(toIndices(group), remoteIndicesGroup.remoteClusterAliases()));
            }
        }

        final Set<RoleDescriptor.ApplicationResourcePrivileges> application = new LinkedHashSet<>();
        for (String applicationName : userRole.application().getApplicationNames()) {
            for (ApplicationPrivilege privilege : userRole.application().getPrivileges(applicationName)) {
                final Set<String> resources = userRole.application().getResourcePatterns(privilege);
                if (resources.isEmpty()) {
                    logger.trace("No resources defined in application privilege {}", privilege);
                } else {
                    application.add(
                        RoleDescriptor.ApplicationResourcePrivileges.builder()
                            .application(applicationName)
                            .privileges(privilege.name())
                            .resources(resources)
                            .build()
                    );
                }
            }
        }

        final Privilege runAsPrivilege = userRole.runAs().getPrivilege();
        final Set<String> runAs;
        if (Operations.isEmpty(runAsPrivilege.getAutomaton())) {
            runAs = Collections.emptySet();
        } else {
            runAs = runAsPrivilege.name();
        }

        return new GetUserPrivilegesResponse(cluster, conditionalCluster, indices, application, runAs, remoteIndices);
    }

    private static GetUserPrivilegesResponse.Indices toIndices(final IndicesPermission.Group group) {
        final Set<BytesReference> queries = group.getQuery() == null ? Collections.emptySet() : group.getQuery();
        final Set<FieldPermissionsDefinition.FieldGrantExcludeGroup> fieldSecurity = getFieldGrantExcludeGroups(group);
        return new GetUserPrivilegesResponse.Indices(
            Arrays.asList(group.indices()),
            group.privilege().name(),
            fieldSecurity,
            queries,
            group.allowRestrictedIndices()
        );
    }

    private static Set<FieldPermissionsDefinition.FieldGrantExcludeGroup> getFieldGrantExcludeGroups(IndicesPermission.Group group) {
        if (group.getFieldPermissions().hasFieldLevelSecurity()) {
            final List<FieldPermissionsDefinition> fieldPermissionsDefinitions = group.getFieldPermissions()
                .getFieldPermissionsDefinitions();
            assert fieldPermissionsDefinitions.size() == 1
                : "limited-by field must not exist since we do not support reporting user privileges for limited roles";
            final FieldPermissionsDefinition definition = fieldPermissionsDefinitions.get(0);
            return definition.getFieldGrantExcludeGroups();
        } else {
            return Collections.emptySet();
        }
    }

    static AuthorizedIndices resolveAuthorizedIndicesFromRole(
        Role role,
        RequestInfo requestInfo,
        Map<String, IndexAbstraction> lookup,
        Supplier<Consumer<Collection<String>>> timerSupplier
    ) {
        IsResourceAuthorizedPredicate predicate = role.allowedIndicesMatcher(requestInfo.getAction());

        TransportRequest request = requestInfo.getRequest();
        final boolean includeDataStreams = (request instanceof IndicesRequest) && ((IndicesRequest) request).includeDataStreams();

        return new AuthorizedIndices(() -> {
            Consumer<Collection<String>> timeChecker = timerSupplier.get();
            Set<String> indicesAndAliases = new HashSet<>();
            if (includeDataStreams) {
                for (IndexAbstraction indexAbstraction : lookup.values()) {
                    if (predicate.test(indexAbstraction)) {
                        indicesAndAliases.add(indexAbstraction.getName());
                        if (indexAbstraction.getType() == IndexAbstraction.Type.DATA_STREAM) {
                            for (Index index : indexAbstraction.getIndices()) {
                                indicesAndAliases.add(index.getName());
                            }
                        }
                    }
                }
            } else {
                for (IndexAbstraction indexAbstraction : lookup.values()) {
                    if (indexAbstraction.getType() != IndexAbstraction.Type.DATA_STREAM && predicate.test(indexAbstraction)) {
                        indicesAndAliases.add(indexAbstraction.getName());
                    }
                }
            }
            timeChecker.accept(indicesAndAliases);
            return indicesAndAliases;
        }, name -> {
            final IndexAbstraction indexAbstraction = lookup.get(name);
            if (indexAbstraction == null) {
                return predicate.test(name, null);
            } else {
                return (indexAbstraction.getParentDataStream() != null && predicate.test(indexAbstraction.getParentDataStream()))
                    || predicate.test(indexAbstraction);
            }
        });
    }

    private IndexAuthorizationResult buildIndicesAccessControl(
        String action,
        Role role,
        ResolvedIndices resolvedIndices,
        Map<String, IndexAbstraction> aliasAndIndexLookup
    ) {
        final IndicesAccessControl accessControl = role.authorize(
            action,
            Sets.newHashSet(resolvedIndices.getLocal()),
            aliasAndIndexLookup,
            fieldPermissionsCache
        );
        return new IndexAuthorizationResult(accessControl);
    }

    private static RBACAuthorizationInfo ensureRBAC(AuthorizationInfo authorizationInfo) {
        if (authorizationInfo instanceof RBACAuthorizationInfo == false) {
            throw new IllegalArgumentException("unsupported authorization info:" + authorizationInfo.getClass().getSimpleName());
        }
        return (RBACAuthorizationInfo) authorizationInfo;
    }

    public static Role maybeGetRBACEngineRole(AuthorizationInfo authorizationInfo) {
        if (authorizationInfo instanceof RBACAuthorizationInfo) {
            return ((RBACAuthorizationInfo) authorizationInfo).getRole();
        }
        return null;
    }

    private static boolean checkChangePasswordAction(Authentication authentication) {
        final boolean isRunAs = authentication.isRunAs();
        final String realmType;
        if (isRunAs) {
            realmType = authentication.getEffectiveSubject().getRealm().getType();
        } else {
            realmType = authentication.getAuthenticatingSubject().getRealm().getType();
        }

        assert realmType != null;
        final AuthenticationType authType = authentication.getAuthenticationType();
        return (authType.equals(AuthenticationType.REALM)
            && (ReservedRealm.TYPE.equals(realmType) || NativeRealmSettings.TYPE.equals(realmType)));
    }

    static class RBACAuthorizationInfo implements AuthorizationInfo {

        private final Role role;
        private final Map<String, Object> info;
        private final RBACAuthorizationInfo authenticatedUserAuthorizationInfo;

        RBACAuthorizationInfo(Role role, Role authenticatedUserRole) {
            this.role = Objects.requireNonNull(role);
            this.info = Collections.singletonMap(PRINCIPAL_ROLES_FIELD_NAME, role.names());
            this.authenticatedUserAuthorizationInfo = authenticatedUserRole == null
                ? this
                : new RBACAuthorizationInfo(authenticatedUserRole, null);
        }

        Role getRole() {
            return role;
        }

        @Override
        public Map<String, Object> asMap() {
            return info;
        }

        @Override
        public RBACAuthorizationInfo getAuthenticatedUserAuthorizationInfo() {
            return authenticatedUserAuthorizationInfo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RBACAuthorizationInfo that = (RBACAuthorizationInfo) o;
            if (this.role.equals(that.role) == false) {
                return false;
            }
            if (this.authenticatedUserAuthorizationInfo == this) {
                return that.authenticatedUserAuthorizationInfo == that;
            } else {
                return this.authenticatedUserAuthorizationInfo.equals(that.authenticatedUserAuthorizationInfo);
            }
        }

        @Override
        public int hashCode() {
            if (this.authenticatedUserAuthorizationInfo == this) {
                return Objects.hashCode(role);
            } else {
                return Objects.hash(role, authenticatedUserAuthorizationInfo);
            }
        }
    }

    private static boolean isScrollRelatedAction(String action) {
        return action.equals(TransportSearchScrollAction.TYPE.name())
            || action.equals(SearchTransportService.FETCH_ID_SCROLL_ACTION_NAME)
            || action.equals(SearchTransportService.QUERY_FETCH_SCROLL_ACTION_NAME)
            || action.equals(SearchTransportService.QUERY_SCROLL_ACTION_NAME)
            || action.equals(SearchTransportService.FREE_CONTEXT_SCROLL_ACTION_NAME)
            || action.equals(TransportClearScrollAction.NAME)
            || action.equals("indices:data/read/sql/close_cursor")
            || action.equals(SearchTransportService.CLEAR_SCROLL_CONTEXTS_ACTION_NAME);
    }

    private static boolean isAsyncRelatedAction(String action) {
        return action.equals(SubmitAsyncSearchAction.NAME)
            || action.equals(GetAsyncSearchAction.NAME)
            || action.equals(TransportDeleteAsyncResultAction.TYPE.name())
            || action.equals(EqlAsyncActionNames.EQL_ASYNC_GET_RESULT_ACTION_NAME)
            || action.equals(EsqlAsyncActionNames.ESQL_ASYNC_GET_RESULT_ACTION_NAME)
            || action.equals(SqlAsyncActionNames.SQL_ASYNC_GET_RESULT_ACTION_NAME);
    }

    static final class AuthorizedIndices implements AuthorizationEngine.AuthorizedIndices {

        private final CachedSupplier<Set<String>> allAuthorizedAndAvailableSupplier;
        private final Predicate<String> isAuthorizedPredicate;

        AuthorizedIndices(Supplier<Set<String>> allAuthorizedAndAvailableSupplier, Predicate<String> isAuthorizedPredicate) {
            this.allAuthorizedAndAvailableSupplier = CachedSupplier.wrap(allAuthorizedAndAvailableSupplier);
            this.isAuthorizedPredicate = Objects.requireNonNull(isAuthorizedPredicate);
        }

        @Override
        public Supplier<Set<String>> all() {
            return allAuthorizedAndAvailableSupplier;
        }

        @Override
        public boolean check(String name) {
            return this.isAuthorizedPredicate.test(name);
        }
    }
}
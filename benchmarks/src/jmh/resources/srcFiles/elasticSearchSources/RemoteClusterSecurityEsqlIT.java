/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.remotecluster;

import org.elasticsearch.Build;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.CheckedConsumer;
import org.elasticsearch.core.Strings;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.cluster.util.resource.Resource;
import org.elasticsearch.test.junit.RunnableTestRuleAdapter;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class RemoteClusterSecurityEsqlIT extends AbstractRemoteClusterSecurityTestCase {
    private static final String ESQL_VERSION = "2024.04.01";

    private static final AtomicReference<Map<String, Object>> API_KEY_MAP_REF = new AtomicReference<>();
    private static final AtomicReference<Map<String, Object>> REST_API_KEY_MAP_REF = new AtomicReference<>();
    private static final AtomicBoolean SSL_ENABLED_REF = new AtomicBoolean();
    private static final AtomicBoolean NODE1_RCS_SERVER_ENABLED = new AtomicBoolean();
    private static final AtomicBoolean NODE2_RCS_SERVER_ENABLED = new AtomicBoolean();
    private static final AtomicInteger INVALID_SECRET_LENGTH = new AtomicInteger();

    static {
        fulfillingCluster = ElasticsearchCluster.local()
            .name("fulfilling-cluster")
            .nodes(3)
            .module("x-pack-esql")
            .module("x-pack-enrich")
            .module("ingest-common")
            .apply(commonClusterConfig)
            .setting("remote_cluster.port", "0")
            .setting("xpack.security.remote_cluster_server.ssl.enabled", () -> String.valueOf(SSL_ENABLED_REF.get()))
            .setting("xpack.security.remote_cluster_server.ssl.key", "remote-cluster.key")
            .setting("xpack.security.remote_cluster_server.ssl.certificate", "remote-cluster.crt")
            .setting("xpack.security.authc.token.enabled", "true")
            .keystore("xpack.security.remote_cluster_server.ssl.secure_key_passphrase", "remote-cluster-password")
            .node(0, spec -> spec.setting("remote_cluster_server.enabled", "true"))
            .node(1, spec -> spec.setting("remote_cluster_server.enabled", () -> String.valueOf(NODE1_RCS_SERVER_ENABLED.get())))
            .node(2, spec -> spec.setting("remote_cluster_server.enabled", () -> String.valueOf(NODE2_RCS_SERVER_ENABLED.get())))
            .build();

        queryCluster = ElasticsearchCluster.local()
            .name("query-cluster")
            .module("x-pack-esql")
            .module("x-pack-enrich")
            .module("ingest-common")
            .apply(commonClusterConfig)
            .setting("xpack.security.remote_cluster_client.ssl.enabled", () -> String.valueOf(SSL_ENABLED_REF.get()))
            .setting("xpack.security.remote_cluster_client.ssl.certificate_authorities", "remote-cluster-ca.crt")
            .setting("xpack.security.authc.token.enabled", "true")
            .keystore("cluster.remote.my_remote_cluster.credentials", () -> {
                if (API_KEY_MAP_REF.get() == null) {
                    final Map<String, Object> apiKeyMap = createCrossClusterAccessApiKey("""
                        {
                          "search": [
                            {
                                "names": ["index*", "not_found_index", "employees", "employees2"]
                            },
                            {
                                "names": ["employees3"],
                                "query": {"term" : {"department" : "engineering"}}
                            }
                          ]
                        }""");
                    API_KEY_MAP_REF.set(apiKeyMap);
                }
                return (String) API_KEY_MAP_REF.get().get("encoded");
            })
            .keystore("cluster.remote.invalid_remote.credentials", randomEncodedApiKey())
            .keystore("cluster.remote.wrong_api_key_type.credentials", () -> {
                if (REST_API_KEY_MAP_REF.get() == null) {
                    initFulfillingClusterClient();
                    final var createApiKeyRequest = new Request("POST", "/_security/api_key");
                    createApiKeyRequest.setJsonEntity("""
                        {
                          "name": "rest_api_key"
                        }""");
                    try {
                        final Response createApiKeyResponse = performRequestWithAdminUser(fulfillingClusterClient, createApiKeyRequest);
                        assertOK(createApiKeyResponse);
                        REST_API_KEY_MAP_REF.set(responseAsMap(createApiKeyResponse));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return (String) REST_API_KEY_MAP_REF.get().get("encoded");
            })
            .keystore(
                "cluster.remote.invalid_secret_length.credentials",
                () -> Base64.getEncoder()
                    .encodeToString(
                        (UUIDs.base64UUID() + ":" + randomAlphaOfLength(INVALID_SECRET_LENGTH.get())).getBytes(StandardCharsets.UTF_8)
                    )
            )
            .rolesFile(Resource.fromClasspath("roles.yml"))
            .user(REMOTE_METRIC_USER, PASS.toString(), "read_remote_shared_metrics", false)
            .build();
    }

    @ClassRule
    public static TestRule clusterRule = RuleChain.outerRule(new RunnableTestRuleAdapter(() -> {
        SSL_ENABLED_REF.set(usually());
        NODE1_RCS_SERVER_ENABLED.set(randomBoolean());
        NODE2_RCS_SERVER_ENABLED.set(randomBoolean());
        INVALID_SECRET_LENGTH.set(randomValueOtherThan(22, () -> randomIntBetween(0, 99)));
    })).around(fulfillingCluster).around(queryCluster);

    public void populateData() throws Exception {
        CheckedConsumer<RestClient, IOException> setupEnrich = client -> {
            Request createIndex = new Request("PUT", "countries");
            createIndex.setJsonEntity("""
                {
                    "mappings": {
                        "properties": {
                          "emp_id": { "type": "keyword" },
                          "country": { "type": "text" }
                        }
                    }
                }
                """);
            assertOK(performRequestWithAdminUser(client, createIndex));
            final Request bulkRequest = new Request("POST", "/_bulk?refresh=true");
            bulkRequest.setJsonEntity(Strings.format("""
                { "index": { "_index": "countries" } }
                { "emp_id": "1", "country": "usa"}
                { "index": { "_index": "countries" } }
                { "emp_id": "2", "country": "canada"}
                { "index": { "_index": "countries" } }
                { "emp_id": "3", "country": "germany"}
                { "index": { "_index": "countries" } }
                { "emp_id": "4", "country": "spain"}
                { "index": { "_index": "countries" } }
                { "emp_id": "5", "country": "japan"}
                { "index": { "_index": "countries" } }
                { "emp_id": "6", "country": "france"}
                { "index": { "_index": "countries" } }
                { "emp_id": "7", "country": "usa"}
                { "index": { "_index": "countries" } }
                { "emp_id": "8", "country": "canada"}
                { "index": { "_index": "countries" } }
                { "emp_id": "9", "country": "usa"}
                """));
            assertOK(performRequestWithAdminUser(client, bulkRequest));

            Request createEnrich = new Request("PUT", "/_enrich/policy/countries");
            createEnrich.setJsonEntity("""
                {
                    "match": {
                        "indices": "countries",
                        "match_field": "emp_id",
                        "enrich_fields": ["country"]
                    }
                }
                """);
            assertOK(performRequestWithAdminUser(client, createEnrich));
            assertOK(performRequestWithAdminUser(client, new Request("PUT", "_enrich/policy/countries/_execute")));
            performRequestWithAdminUser(client, new Request("DELETE", "/countries"));
        };
        setupEnrich.accept(fulfillingClusterClient);
        String employeesMapping = """
            {
                "mappings": {
                    "properties": {
                      "emp_id": { "type": "keyword" },
                      "department": { "type": "keyword" }
                    }
                }
            }
            """;
        Request createIndex = new Request("PUT", "employees");
        createIndex.setJsonEntity(employeesMapping);
        assertOK(performRequestAgainstFulfillingCluster(createIndex));
        Request createIndex2 = new Request("PUT", "employees2");
        createIndex2.setJsonEntity(employeesMapping);
        assertOK(performRequestAgainstFulfillingCluster(createIndex2));
        Request createIndex3 = new Request("PUT", "employees3");
        createIndex3.setJsonEntity(employeesMapping);
        assertOK(performRequestAgainstFulfillingCluster(createIndex3));
        Request bulkRequest = new Request("POST", "/_bulk?refresh=true");
        bulkRequest.setJsonEntity(Strings.format("""
            { "index": { "_index": "employees" } }
            { "emp_id": "1", "department" : "engineering" }
            { "index": { "_index": "employees" } }
            { "emp_id": "3", "department" : "sales" }
            { "index": { "_index": "employees" } }
            { "emp_id": "5", "department" : "marketing" }
            { "index": { "_index": "employees" } }
            { "emp_id": "7", "department" : "engineering" }
            { "index": { "_index": "employees" } }
            { "emp_id": "9", "department" : "sales" }
            { "index": { "_index": "employees2" } }
            { "emp_id": "11", "department" : "engineering" }
            { "index": { "_index": "employees2" } }
            { "emp_id": "13", "department" : "sales" }
             { "index": { "_index": "employees3" } }
            { "emp_id": "21", "department" : "engineering" }
            { "index": { "_index": "employees3" } }
            { "emp_id": "23", "department" : "sales" }
            { "index": { "_index": "employees3" } }
            { "emp_id": "25", "department" : "engineering" }
            { "index": { "_index": "employees3" } }
            { "emp_id": "27", "department" : "sales" }
            """));
        assertOK(performRequestAgainstFulfillingCluster(bulkRequest));

        setupEnrich.accept(client());

        createIndex = new Request("PUT", "employees");
        createIndex.setJsonEntity(employeesMapping);
        assertOK(adminClient().performRequest(createIndex));
        createIndex2 = new Request("PUT", "employees2");
        createIndex2.setJsonEntity(employeesMapping);
        assertOK(adminClient().performRequest(createIndex2));
        createIndex3 = new Request("PUT", "employees3");
        createIndex3.setJsonEntity(employeesMapping);
        assertOK(adminClient().performRequest(createIndex3));
        bulkRequest = new Request("POST", "/_bulk?refresh=true");
        bulkRequest.setJsonEntity(Strings.format("""
            { "index": { "_index": "employees" } }
            { "emp_id": "2", "department" : "management" }
            { "index": { "_index": "employees"} }
            { "emp_id": "4", "department" : "engineering" }
            { "index": { "_index": "employees" } }
            { "emp_id": "6", "department" : "marketing"}
            { "index": { "_index": "employees"} }
            { "emp_id": "8", "department" : "support"}
            { "index": { "_index": "employees2"} }
            { "emp_id": "10", "department" : "management"}
            { "index": { "_index": "employees2"} }
            { "emp_id": "12", "department" : "engineering"}
            { "index": { "_index": "employees3"} }
            { "emp_id": "20", "department" : "management"}
            { "index": { "_index": "employees3"} }
            { "emp_id": "22", "department" : "engineering"}
            """));
        assertOK(client().performRequest(bulkRequest));

        final var putRoleRequest = new Request("PUT", "/_security/role/" + REMOTE_SEARCH_ROLE);
        putRoleRequest.setJsonEntity("""
            {
              "indices": [
                {
                  "names": ["employees"],
                  "privileges": ["read"]
                }
              ],
              "cluster": [ "monitor_enrich" ],
              "remote_indices": [
                {
                  "names": ["employees"],
                  "privileges": ["read"],
                  "clusters": ["my_remote_cluster"]
                }
              ]
            }""");
        assertOK(adminClient().performRequest(putRoleRequest));
        final var putUserRequest = new Request("PUT", "/_security/user/" + REMOTE_SEARCH_USER);
        putUserRequest.setJsonEntity("""
            {
              "password": "x-pack-test-password",
              "roles" : ["remote_search"]
            }""");
        assertOK(adminClient().performRequest(putUserRequest));
    }

    @After
    public void wipeData() throws Exception {
        CheckedConsumer<RestClient, IOException> wipe = client -> {
            performRequestWithAdminUser(client, new Request("DELETE", "/employees"));
            performRequestWithAdminUser(client, new Request("DELETE", "/employees2"));
            performRequestWithAdminUser(client, new Request("DELETE", "/employees3"));
            performRequestWithAdminUser(client, new Request("DELETE", "/_enrich/policy/countries"));
        };
        wipe.accept(fulfillingClusterClient);
        wipe.accept(client());
    }

    @SuppressWarnings("unchecked")
    public void testCrossClusterQuery() throws Exception {
        configureRemoteCluster();
        populateData();

        Response response = performRequestWithRemoteSearchUser(esqlRequest("""
            FROM my_remote_cluster:employees
            | SORT emp_id ASC
            | LIMIT 2
            | KEEP emp_id, department"""));
        assertOK(response);
        assertRemoteOnlyResults(response);

        response = performRequestWithRemoteSearchUser(esqlRequest("""
            FROM my_remote_cluster:employees,employees
            | SORT emp_id ASC
            | LIMIT 10"""));
        assertOK(response);
        assertRemoteAndLocalResults(response);

        response = performRequestWithRemoteSearchUser(esqlRequest("""
            FROM my_remote_cluster:employees,my_remote_cluster:employees2
            | SORT emp_id ASC
            | LIMIT 2
            | KEEP emp_id, department"""));
        assertOK(response);
        assertRemoteOnlyResults(response); 

        response = performRequestWithRemoteSearchUser(esqlRequest("""
            FROM my_remote_cluster:employees,my_remote_cluster:employees2,employees,employees2
            | SORT emp_id ASC
            | LIMIT 10"""));
        assertOK(response);
        assertRemoteAndLocalResults(response); 

        final var putRoleRequest = new Request("PUT", "/_security/role/" + REMOTE_SEARCH_ROLE);
        putRoleRequest.setJsonEntity("""
            {
              "indices": [{"names": [""], "privileges": ["read_cross_cluster"]}],
              "remote_indices": [
                {
                  "names": ["employees*"],
                  "privileges": ["read"],
                  "clusters": ["my_remote_cluster"]
                }
              ]
            }""");
        response = adminClient().performRequest(putRoleRequest);
        assertOK(response);

        response = performRequestWithRemoteSearchUser(esqlRequest("""
            FROM my_remote_cluster:employees,my_remote_cluster:employees2
            | SORT emp_id ASC
            | LIMIT 2
            | KEEP emp_id, department"""));
        assertOK(response);
        assertRemoteOnlyAgainst2IndexResults(response);
    }

    @SuppressWarnings("unchecked")
    public void testCrossClusterQueryWithRemoteDLSAndFLS() throws Exception {
        configureRemoteCluster();
        populateData();

        final var putRoleRequest = new Request("PUT", "/_security/role/" + REMOTE_SEARCH_ROLE);
        putRoleRequest.setJsonEntity("""
            {
              "indices": [{"names": [""], "privileges": ["read_cross_cluster"]}],
              "remote_indices": [
                {
                  "names": ["employees*"],
                  "privileges": ["read"],
                  "clusters": ["my_remote_cluster"]

                }
              ]
            }""");
        Response response = adminClient().performRequest(putRoleRequest);
        assertOK(response);

        response = performRequestWithRemoteSearchUser(esqlRequest("""
            FROM my_remote_cluster:employees3
            | SORT emp_id ASC
            | LIMIT 10
            | KEEP emp_id, department"""));
        assertOK(response);

        Map<String, Object> responseAsMap = entityAsMap(response);
        List<?> columns = (List<?>) responseAsMap.get("columns");
        List<?> values = (List<?>) responseAsMap.get("values");
        assertEquals(2, columns.size());
        assertEquals(2, values.size());
        List<String> flatList = values.stream()
            .flatMap(innerList -> innerList instanceof List ? ((List<String>) innerList).stream() : Stream.empty())
            .collect(Collectors.toList());
        assertThat(flatList, containsInAnyOrder("21", "25", "engineering", "engineering"));

        putRoleRequest.setJsonEntity("""
            {
              "indices": [{"names": [""], "privileges": ["read_cross_cluster"]}],
              "remote_indices": [
                {
                  "names": ["employees*"],
                  "privileges": ["read"],
                  "clusters": ["my_remote_cluster"],
                  "query": {"term" : {"emp_id" : "21"}}

                }
              ]
            }""");
        response = adminClient().performRequest(putRoleRequest);
        assertOK(response);

        response = performRequestWithRemoteSearchUser(esqlRequest("""
            FROM my_remote_cluster:employees3
            | SORT emp_id ASC
            | LIMIT 2
            | KEEP emp_id, department"""));
        assertOK(response);

        responseAsMap = entityAsMap(response);
        columns = (List<?>) responseAsMap.get("columns");
        values = (List<?>) responseAsMap.get("values");
        assertEquals(2, columns.size());
        assertEquals(1, values.size());
        flatList = values.stream()
            .flatMap(innerList -> innerList instanceof List ? ((List<String>) innerList).stream() : Stream.empty())
            .collect(Collectors.toList());
        assertThat(flatList, containsInAnyOrder("21", "engineering"));

        putRoleRequest.setJsonEntity("""
            {
              "indices": [{"names": [""], "privileges": ["read_cross_cluster"]}],
              "remote_indices": [
                {
                  "names": ["employees*"],
                  "privileges": ["read"],
                  "clusters": ["my_remote_cluster"],
                  "query": {"term" : {"emp_id" : "21"}},
                  "field_security": {"grant": [ "department" ]}
                }
              ]
            }""");
        response = adminClient().performRequest(putRoleRequest);
        assertOK(response);

        response = performRequestWithRemoteSearchUser(esqlRequest("""
            FROM my_remote_cluster:employees3
            | LIMIT 2
            """));
        assertOK(response);
        responseAsMap = entityAsMap(response);
        columns = (List<?>) responseAsMap.get("columns");
        values = (List<?>) responseAsMap.get("values");
        assertEquals(1, columns.size());
        assertEquals(1, values.size());
        flatList = values.stream()
            .flatMap(innerList -> innerList instanceof List ? ((List<String>) innerList).stream() : Stream.empty())
            .collect(Collectors.toList());
        assertThat(flatList, containsInAnyOrder("engineering"));
    }

    public void testCrossClusterQueryAgainstInvalidRemote() throws Exception {
        configureRemoteCluster();
        populateData();

        updateClusterSettings(
            randomBoolean()
                ? Settings.builder().put("cluster.remote.invalid_remote.seeds", fulfillingCluster.getRemoteClusterServerEndpoint(0)).build()
                : Settings.builder()
                    .put("cluster.remote.invalid_remote.mode", "proxy")
                    .put("cluster.remote.invalid_remote.proxy_address", fulfillingCluster.getRemoteClusterServerEndpoint(0))
                    .build()
        );

        var q = "FROM invalid_remote:employees,employees |  SORT emp_id DESC | LIMIT 10";
        Response response = performRequestWithRemoteSearchUser(esqlRequest(q));
        assertOK(response);
        assertLocalOnlyResults(response);

        ResponseException error = expectThrows(ResponseException.class, () -> {
            var q2 = "FROM invalid_remote:employees |  SORT emp_id DESC | LIMIT 10";
            performRequestWithRemoteSearchUser(esqlRequest(q2));
        });
        assertThat(error.getResponse().getStatusLine().getStatusCode(), equalTo(401));
        assertThat(error.getMessage(), containsString("unable to find apikey"));
    }

    @SuppressWarnings("unchecked")
    public void testCrossClusterQueryWithOnlyRemotePrivs() throws Exception {
        configureRemoteCluster();
        populateData();

        var putRoleRequest = new Request("PUT", "/_security/role/" + REMOTE_SEARCH_ROLE);
        putRoleRequest.setJsonEntity("""
            {
              "indices": [{"names": [""], "privileges": ["read_cross_cluster"]}],
              "remote_indices": [
                {
                  "names": ["employees"],
                  "privileges": ["read"],
                  "clusters": ["my_remote_cluster"]
                }
              ]
            }""");
        assertOK(adminClient().performRequest(putRoleRequest));

        Response response = performRequestWithRemoteSearchUser(esqlRequest("""
            FROM my_remote_cluster:employees
            | SORT emp_id ASC
            | LIMIT 2
            | KEEP emp_id, department"""));
        assertOK(response);
        assertRemoteOnlyResults(response);

        putRoleRequest.setJsonEntity("""
            {
              "indices": [{"names": [""], "privileges": ["read_cross_cluster"]}],
              "remote_indices": [
                {
                  "names": ["idontexist"],
                  "privileges": ["read"],
                  "clusters": ["my_remote_cluster"]
                }
              ]
            }""");
        assertOK(adminClient().performRequest(putRoleRequest));

        ResponseException error = expectThrows(ResponseException.class, () -> performRequestWithRemoteSearchUser(esqlRequest("""
            FROM my_remote_cluster:employees
            | SORT emp_id ASC
            | LIMIT 2
            | KEEP emp_id, department""")));
        assertThat(error.getResponse().getStatusLine().getStatusCode(), equalTo(400));
        assertThat(error.getMessage(), containsString("Unknown index [my_remote_cluster:employees]"));

        final var putRoleNoLocalPrivs = new Request("PUT", "/_security/role/" + REMOTE_SEARCH_ROLE);
        putRoleNoLocalPrivs.setJsonEntity("""
            {
              "indices": [],
              "remote_indices": [
                {
                  "names": ["employees"],
                  "privileges": ["read"],
                  "clusters": ["my_remote_cluster"]
                }
              ]
            }""");
        assertOK(adminClient().performRequest(putRoleNoLocalPrivs));

        error = expectThrows(ResponseException.class, () -> { performRequestWithRemoteSearchUser(esqlRequest("""
            FROM my_remote_cluster:employees
            | SORT emp_id ASC
            | LIMIT 2
            | KEEP emp_id, department""")); });

        assertThat(error.getResponse().getStatusLine().getStatusCode(), equalTo(403));
        assertThat(
            error.getMessage(),
            containsString(
                "action [indices:data/read/esql] is unauthorized for user [remote_search_user] with effective roles [remote_search], "
                    + "this action is granted by the index privileges [read,read_cross_cluster,all]"
            )
        );
    }

    @AwaitsFix(bugUrl = "cross-clusters enrich doesn't work with RCS 2.0")
    public void testCrossClusterEnrich() throws Exception {
        configureRemoteCluster();
        populateData();
        {
            Response response = performRequestWithRemoteSearchUser(esqlRequest("""
                FROM my_remote_cluster:employees,employees
                | ENRICH countries
                | STATS size=count(*) by country
                | SORT size DESC
                | LIMIT 2"""));
            assertOK(response);
            Map<String, Object> values = entityAsMap(response);

            final var putLocalSearchRoleRequest = new Request("PUT", "/_security/role/local_search");
            putLocalSearchRoleRequest.setJsonEntity("""
                {
                  "indices": [
                    {
                      "names": ["employees"],
                      "privileges": ["read"]
                    }
                  ],
                  "cluster": [ ],
                  "remote_indices": [
                    {
                      "names": ["employees"],
                      "privileges": ["read"],
                      "clusters": ["my_remote_cluster"]
                    }
                  ]
                }""");
            assertOK(adminClient().performRequest(putLocalSearchRoleRequest));
            final var putlocalSearchUserRequest = new Request("PUT", "/_security/user/local_search_user");
            putlocalSearchUserRequest.setJsonEntity("""
                {
                  "password": "x-pack-test-password",
                  "roles" : ["local_search"]
                }""");
            assertOK(adminClient().performRequest(putlocalSearchUserRequest));
            for (String indices : List.of("my_remote_cluster:employees,employees", "my_remote_cluster:employees")) {
                ResponseException error = expectThrows(ResponseException.class, () -> {
                    var q = "FROM " + indices + "| ENRICH countries | STATS size=count(*) by country | SORT size | LIMIT 2";
                    performRequestWithLocalSearchUser(esqlRequest(q));
                });
                assertThat(error.getResponse().getStatusLine().getStatusCode(), equalTo(403));
                assertThat(
                    error.getMessage(),
                    containsString(
                        "action [cluster:monitor/xpack/enrich/esql/resolve_policy] towards remote cluster [my_remote_cluster]"
                            + " is unauthorized for user [local_search_user] with effective roles [local_search]"
                    )
                );
            }
        }
    }

    protected Request esqlRequest(String command) throws IOException {
        XContentBuilder body = JsonXContent.contentBuilder();
        body.startObject();
        body.field("query", command);
        if (Build.current().isSnapshot() && randomBoolean()) {
            Settings.Builder settings = Settings.builder();
            if (randomBoolean()) {
                settings.put("page_size", between(1, 5));
            }
            if (randomBoolean()) {
                settings.put("exchange_buffer_size", between(1, 2));
            }
            if (randomBoolean()) {
                settings.put("data_partitioning", randomFrom("shard", "segment", "doc"));
            }
            if (randomBoolean()) {
                settings.put("enrich_max_workers", between(1, 5));
            }
            Settings pragmas = settings.build();
            if (pragmas != Settings.EMPTY) {
                body.startObject("pragma");
                body.value(pragmas);
                body.endObject();
            }
        }
        body.field("version", ESQL_VERSION);
        body.endObject();
        Request request = new Request("POST", "_query");
        request.setJsonEntity(org.elasticsearch.common.Strings.toString(body));
        return request;
    }

    private Response performRequestWithRemoteSearchUser(final Request request) throws IOException {
        request.setOptions(
            RequestOptions.DEFAULT.toBuilder().addHeader("Authorization", headerFromRandomAuthMethod(REMOTE_SEARCH_USER, PASS))
        );
        return client().performRequest(request);
    }

    private Response performRequestWithLocalSearchUser(final Request request) throws IOException {
        request.setOptions(
            RequestOptions.DEFAULT.toBuilder().addHeader("Authorization", headerFromRandomAuthMethod("local_search_user", PASS))
        );
        return client().performRequest(request);
    }

    @SuppressWarnings("unchecked")
    private void assertRemoteOnlyResults(Response response) throws IOException {
        Map<String, Object> responseAsMap = entityAsMap(response);
        List<?> columns = (List<?>) responseAsMap.get("columns");
        List<?> values = (List<?>) responseAsMap.get("values");
        assertEquals(2, columns.size());
        assertEquals(2, values.size());
        List<String> flatList = values.stream()
            .flatMap(innerList -> innerList instanceof List ? ((List<String>) innerList).stream() : Stream.empty())
            .collect(Collectors.toList());
        assertThat(flatList, containsInAnyOrder("1", "3", "engineering", "sales"));
    }

    @SuppressWarnings("unchecked")
    private void assertRemoteOnlyAgainst2IndexResults(Response response) throws IOException {
        Map<String, Object> responseAsMap = entityAsMap(response);
        List<?> columns = (List<?>) responseAsMap.get("columns");
        List<?> values = (List<?>) responseAsMap.get("values");
        assertEquals(2, columns.size());
        assertEquals(2, values.size());
        List<String> flatList = values.stream()
            .flatMap(innerList -> innerList instanceof List ? ((List<String>) innerList).stream() : Stream.empty())
            .collect(Collectors.toList());
        assertThat(flatList, containsInAnyOrder("1", "11", "engineering", "engineering"));
    }

    @SuppressWarnings("unchecked")
    private void assertLocalOnlyResults(Response response) throws IOException {
        Map<String, Object> responseAsMap = entityAsMap(response);
        List<?> columns = (List<?>) responseAsMap.get("columns");
        List<?> values = (List<?>) responseAsMap.get("values");
        assertEquals(2, columns.size());
        assertEquals(4, values.size());
        List<String> flatList = values.stream()
            .flatMap(innerList -> innerList instanceof List ? ((List<String>) innerList).stream() : Stream.empty())
            .collect(Collectors.toList());
        assertThat(flatList, containsInAnyOrder("2", "4", "6", "8", "support", "management", "engineering", "marketing"));
    }

    @SuppressWarnings("unchecked")
    private void assertRemoteAndLocalResults(Response response) throws IOException {
        Map<String, Object> responseAsMap = entityAsMap(response);
        List<?> columns = (List<?>) responseAsMap.get("columns");
        List<?> values = (List<?>) responseAsMap.get("values");
        assertEquals(2, columns.size());
        assertEquals(9, values.size());
        List<String> flatList = values.stream()
            .flatMap(innerList -> innerList instanceof List ? ((List<String>) innerList).stream() : Stream.empty())
            .collect(Collectors.toList());
        assertThat(
            flatList,
            containsInAnyOrder(
                "1",
                "2",
                "3",
                "4",
                "5",
                "6",
                "7",
                "8",
                "9",
                "engineering",
                "engineering",
                "engineering",
                "management",
                "sales",
                "sales",
                "marketing",
                "marketing",
                "support"
            )
        );
    }
}
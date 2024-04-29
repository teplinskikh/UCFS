/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.indices.memory.breaker;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.client.internal.Requests;
import org.elasticsearch.cluster.routing.allocation.decider.EnableAllocationDecider;
import org.elasticsearch.common.breaker.CircuitBreaker;
import org.elasticsearch.common.breaker.CircuitBreakingException;
import org.elasticsearch.common.breaker.NoopCircuitBreaker;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.indices.breaker.CircuitBreakerStats;
import org.elasticsearch.indices.breaker.HierarchyCircuitBreakerService;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.elasticsearch.xcontent.XContentType;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.elasticsearch.cluster.metadata.IndexMetadata.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.cardinality;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.test.ESIntegTestCase.Scope.TEST;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertResponse;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Integration tests for InternalCircuitBreakerService
 */
@ClusterScope(scope = TEST, numClientNodes = 0, maxNumDataNodes = 1)
public class CircuitBreakerServiceIT extends ESIntegTestCase {
    /** Reset all breaker settings back to their defaults */
    private void reset() {
        logger.info("--> resetting breaker settings");
        indicesAdmin().prepareClearCache().setFieldDataCache(true).setQueryCache(true).setRequestCache(true).get();

        Settings.Builder resetSettings = Settings.builder();
        Stream.of(
            HierarchyCircuitBreakerService.FIELDDATA_CIRCUIT_BREAKER_LIMIT_SETTING,
            HierarchyCircuitBreakerService.FIELDDATA_CIRCUIT_BREAKER_OVERHEAD_SETTING,
            HierarchyCircuitBreakerService.REQUEST_CIRCUIT_BREAKER_LIMIT_SETTING,
            HierarchyCircuitBreakerService.REQUEST_CIRCUIT_BREAKER_OVERHEAD_SETTING,
            HierarchyCircuitBreakerService.IN_FLIGHT_REQUESTS_CIRCUIT_BREAKER_LIMIT_SETTING,
            HierarchyCircuitBreakerService.IN_FLIGHT_REQUESTS_CIRCUIT_BREAKER_OVERHEAD_SETTING,
            HierarchyCircuitBreakerService.TOTAL_CIRCUIT_BREAKER_LIMIT_SETTING
        ).forEach(s -> resetSettings.putNull(s.getKey()));
        updateClusterSettings(resetSettings);
    }

    @Before
    public void setup() {
        reset();
    }

    @After
    public void teardown() {
        reset();
    }

    /** Returns true if any of the nodes used a noop breaker */
    private boolean noopBreakerUsed() {
        NodesStatsResponse stats = clusterAdmin().prepareNodesStats().setBreaker(true).get();
        for (NodeStats nodeStats : stats.getNodes()) {
            if (nodeStats.getBreaker().getStats(CircuitBreaker.REQUEST).getLimit() == NoopCircuitBreaker.LIMIT) {
                return true;
            }
            if (nodeStats.getBreaker().getStats(CircuitBreaker.IN_FLIGHT_REQUESTS).getLimit() == NoopCircuitBreaker.LIMIT) {
                return true;
            }
            if (nodeStats.getBreaker().getStats(CircuitBreaker.FIELDDATA).getLimit() == NoopCircuitBreaker.LIMIT) {
                return true;
            }
        }
        return false;
    }

    public void testMemoryBreaker() throws Exception {
        if (noopBreakerUsed()) {
            logger.info("--> noop breakers used, skipping test");
            return;
        }
        assertAcked(
            prepareCreate("cb-test", 1, Settings.builder().put(SETTING_NUMBER_OF_REPLICAS, between(0, 1))).setMapping(
                "test",
                "type=text,fielddata=true"
            )
        );
        final Client client = client();

        int docCount = scaledRandomIntBetween(300, 1000);
        List<IndexRequestBuilder> reqs = new ArrayList<>();
        for (long id = 0; id < docCount; id++) {
            reqs.add(client.prepareIndex("cb-test").setId(Long.toString(id)).setSource("test", "value" + id));
        }
        indexRandom(true, false, true, reqs);

        clearFieldData();

        updateClusterSettings(
            Settings.builder()
                .put(HierarchyCircuitBreakerService.FIELDDATA_CIRCUIT_BREAKER_LIMIT_SETTING.getKey(), "100b")
                .put(HierarchyCircuitBreakerService.FIELDDATA_CIRCUIT_BREAKER_OVERHEAD_SETTING.getKey(), 1.05)
        );

        SearchRequestBuilder searchRequest = client.prepareSearch("cb-test").setQuery(matchAllQuery()).addSort("test", SortOrder.DESC);

        String errMsg = "Data too large, data for [test] would be";
        assertFailures(searchRequest, RestStatus.TOO_MANY_REQUESTS, containsString(errMsg));
        errMsg = "which is larger than the limit of [100/100b]";
        assertFailures(searchRequest, RestStatus.TOO_MANY_REQUESTS, containsString(errMsg));

        NodesStatsResponse stats = client.admin().cluster().prepareNodesStats().setBreaker(true).get();
        long breaks = 0;
        for (NodeStats stat : stats.getNodes()) {
            CircuitBreakerStats breakerStats = stat.getBreaker().getStats(CircuitBreaker.FIELDDATA);
            breaks += breakerStats.getTrippedCount();
        }
        assertThat(breaks, greaterThanOrEqualTo(1L));
    }

    public void testRamAccountingTermsEnum() throws Exception {
        if (noopBreakerUsed()) {
            logger.info("--> noop breakers used, skipping test");
            return;
        }
        final Client client = client();

        assertAcked(prepareCreate("ramtest").setSource("""
            {
              "mappings": {
                "type": {
                  "properties": {
                    "test": {
                      "type": "text",
                      "fielddata": true,
                      "fielddata_frequency_filter": {
                        "max": 10000
                      }
                    }
                  }
                }
              }
            }""", XContentType.JSON));

        ensureGreen("ramtest");

        int docCount = scaledRandomIntBetween(300, 1000);
        List<IndexRequestBuilder> reqs = new ArrayList<>();
        for (long id = 0; id < docCount; id++) {
            reqs.add(client.prepareIndex("ramtest").setId(Long.toString(id)).setSource("test", "value" + id));
        }
        indexRandom(true, false, true, reqs);

        client.prepareSearch("ramtest").setQuery(matchAllQuery()).addSort("test", SortOrder.DESC).get().decRef();

        clearFieldData();

        updateClusterSettings(
            Settings.builder()
                .put(HierarchyCircuitBreakerService.FIELDDATA_CIRCUIT_BREAKER_LIMIT_SETTING.getKey(), "100b")
                .put(HierarchyCircuitBreakerService.FIELDDATA_CIRCUIT_BREAKER_OVERHEAD_SETTING.getKey(), 1.05)
        );

        SearchRequestBuilder searchRequest = client.prepareSearch("ramtest").setQuery(matchAllQuery()).addSort("test", SortOrder.DESC);

        String errMsg = "Data too large, data for [test] would be";
        assertFailures(searchRequest, RestStatus.TOO_MANY_REQUESTS, containsString(errMsg));
        errMsg = "which is larger than the limit of [100/100b]";
        assertFailures(searchRequest, RestStatus.TOO_MANY_REQUESTS, containsString(errMsg));

        NodesStatsResponse stats = client.admin().cluster().prepareNodesStats().setBreaker(true).get();
        long breaks = 0;
        for (NodeStats stat : stats.getNodes()) {
            CircuitBreakerStats breakerStats = stat.getBreaker().getStats(CircuitBreaker.FIELDDATA);
            breaks += breakerStats.getTrippedCount();
        }
        assertThat(breaks, greaterThanOrEqualTo(1L));
    }

    public void testRequestBreaker() throws Exception {
        if (noopBreakerUsed()) {
            logger.info("--> noop breakers used, skipping test");
            return;
        }
        assertAcked(prepareCreate("cb-test", 1, Settings.builder().put(SETTING_NUMBER_OF_REPLICAS, between(0, 1))));
        Client client = client();

        updateClusterSettings(Settings.builder().put(HierarchyCircuitBreakerService.REQUEST_CIRCUIT_BREAKER_LIMIT_SETTING.getKey(), "10b"));

        int docCount = scaledRandomIntBetween(300, 1000);
        List<IndexRequestBuilder> reqs = new ArrayList<>();
        for (long id = 0; id < docCount; id++) {
            reqs.add(client.prepareIndex("cb-test").setId(Long.toString(id)).setSource("test", id));
        }
        indexRandom(true, reqs);

        try {
            client.prepareSearch("cb-test").setQuery(matchAllQuery()).addAggregation(cardinality("card").field("test")).get();
            fail("aggregation should have tripped the breaker");
        } catch (Exception e) {
            Throwable cause = e.getCause();
            assertThat("Exception cause should be a CircuitBreakingException", cause, instanceOf(CircuitBreakingException.class));
            assertThat(cause.toString(), containsString("Data too large"));
            assertThat(cause.toString(), containsString("which is larger than the limit of [10/10b]"));
        }
    }

    public void testAggTookTooMuch() throws Exception {
        if (noopBreakerUsed()) {
            logger.info("--> noop breakers used, skipping test");
            return;
        }
        assertAcked(prepareCreate("cb-test", 1, Settings.builder().put(SETTING_NUMBER_OF_REPLICAS, between(0, 1))));
        Client client = client();

        updateClusterSettings(
            Settings.builder().put(HierarchyCircuitBreakerService.REQUEST_CIRCUIT_BREAKER_LIMIT_SETTING.getKey(), "100b")
        );

        int docCount = scaledRandomIntBetween(100, 1000);
        List<IndexRequestBuilder> reqs = new ArrayList<>();
        for (long id = 0; id < docCount; id++) {
            reqs.add(client.prepareIndex("cb-test").setId(Long.toString(id)).setSource("test", id));
        }
        indexRandom(true, reqs);

        try {
            assertResponse(
                client.prepareSearch("cb-test").setQuery(matchAllQuery()).addAggregation(terms("my_terms").field("test")),
                response -> assertTrue("there should be shard failures", response.getFailedShards() > 0)
            );
            fail("aggregation should have tripped the breaker");
        } catch (Exception e) {
            Throwable cause = e.getCause();
            assertThat(cause, instanceOf(CircuitBreakingException.class));
            assertThat(cause.toString(), containsString("[request] Data too large, data for [preallocate[aggregations]] would be"));
            assertThat(cause.toString(), containsString("which is larger than the limit of [100/100b]"));
        }
    }

    /** Issues a cache clear and waits 30 seconds for the field data breaker to be cleared */
    public void clearFieldData() throws Exception {
        indicesAdmin().prepareClearCache().setFieldDataCache(true).get();
        assertBusy(() -> {
            NodesStatsResponse resp = clusterAdmin().prepareNodesStats().clear().setBreaker(true).get(new TimeValue(15, TimeUnit.SECONDS));
            for (NodeStats nStats : resp.getNodes()) {
                assertThat(
                    "fielddata breaker never reset back to 0",
                    nStats.getBreaker().getStats(CircuitBreaker.FIELDDATA).getEstimated(),
                    equalTo(0L)
                );
            }
        }, 30, TimeUnit.SECONDS);
    }

    public void testCanResetUnreasonableSettings() {
        if (noopBreakerUsed()) {
            logger.info("--> noop breakers used, skipping test");
            return;
        }
        updateClusterSettings(
            Settings.builder().put(HierarchyCircuitBreakerService.IN_FLIGHT_REQUESTS_CIRCUIT_BREAKER_LIMIT_SETTING.getKey(), "5b")
        );

        reset();

        assertThat(
            clusterAdmin().prepareState()
                .get()
                .getState()
                .metadata()
                .persistentSettings()
                .get(HierarchyCircuitBreakerService.TOTAL_CIRCUIT_BREAKER_LIMIT_SETTING.getKey()),
            nullValue()
        );

    }

    public void testLimitsRequestSize() {
        ByteSizeValue inFlightRequestsLimit = new ByteSizeValue(8, ByteSizeUnit.KB);
        if (noopBreakerUsed()) {
            logger.info("--> noop breakers used, skipping test");
            return;
        }

        internalCluster().ensureAtLeastNumDataNodes(2);

        NodesStatsResponse nodeStats = clusterAdmin().prepareNodesStats().get();
        List<NodeStats> dataNodeStats = new ArrayList<>();
        for (NodeStats stat : nodeStats.getNodes()) {
            if (stat.getNode().canContainData()) {
                dataNodeStats.add(stat);
            }
        }

        assertThat(dataNodeStats.size(), greaterThanOrEqualTo(2));
        Collections.shuffle(dataNodeStats, random());

        NodeStats targetNode = dataNodeStats.get(0);
        NodeStats sourceNode = dataNodeStats.get(1);

        assertAcked(
            prepareCreate("index").setSettings(
                indexSettings(1, 0).put("index.routing.allocation.include._name", targetNode.getNode().getName())
                    .put(EnableAllocationDecider.INDEX_ROUTING_REBALANCE_ENABLE_SETTING.getKey(), EnableAllocationDecider.Rebalance.NONE)
            )
        );

        Client client = client(sourceNode.getNode().getName());

        int numRequests = inFlightRequestsLimit.bytesAsInt();
        BulkRequest bulkRequest = new BulkRequest();
        for (int i = 0; i < numRequests; i++) {
            IndexRequest indexRequest = new IndexRequest("index").id(Integer.toString(i));
            indexRequest.source(Requests.INDEX_CONTENT_TYPE, "field", "value", "num", i);
            bulkRequest.add(indexRequest);
        }

        updateClusterSettings(
            Settings.builder()
                .put(HierarchyCircuitBreakerService.IN_FLIGHT_REQUESTS_CIRCUIT_BREAKER_LIMIT_SETTING.getKey(), inFlightRequestsLimit)
        );

        try {
            BulkResponse response = client.bulk(bulkRequest).actionGet();
            if (response.hasFailures() == false) {
                fail("Should have thrown CircuitBreakingException");
            } else {
                for (BulkItemResponse bulkItemResponse : response) {
                    Throwable cause = ExceptionsHelper.unwrapCause(bulkItemResponse.getFailure().getCause());
                    assertThat(cause, instanceOf(CircuitBreakingException.class));
                    assertEquals(((CircuitBreakingException) cause).getByteLimit(), inFlightRequestsLimit.getBytes());
                }
            }
        } catch (CircuitBreakingException ex) {
            assertEquals(ex.getByteLimit(), inFlightRequestsLimit.getBytes());
        }
    }

    public void testDynamicUseRealMemory() {
        final Client client = client();
        checkLimitSize(client, 0.7);
        String useRealMemoryUsageSetting = HierarchyCircuitBreakerService.USE_REAL_MEMORY_USAGE_SETTING.getKey();
        String totalCircuitBreakerLimitSettingKey = HierarchyCircuitBreakerService.TOTAL_CIRCUIT_BREAKER_LIMIT_SETTING.getKey();

        updateClusterSettings(Settings.builder().put(useRealMemoryUsageSetting, true));
        checkLimitSize(client, 0.95);

        updateClusterSettings(Settings.builder().put(totalCircuitBreakerLimitSettingKey, "80%").put(useRealMemoryUsageSetting, true));
        checkLimitSize(client, 0.8);

        updateClusterSettings(Settings.builder().put(useRealMemoryUsageSetting, false));
        checkLimitSize(client, 0.8);

        updateClusterSettings(Settings.builder().putNull(totalCircuitBreakerLimitSettingKey).putNull(useRealMemoryUsageSetting));
    }

    private void checkLimitSize(Client client, double limitRatio) {
        NodesStatsResponse stats = client.admin().cluster().prepareNodesStats().setBreaker(true).setJvm(true).get();
        for (NodeStats node : stats.getNodes()) {
            long heapSize = node.getJvm().getMem().getHeapCommitted().getBytes();
            long limitSize = node.getBreaker().getStats(CircuitBreaker.PARENT).getLimit();
            assertEquals((long) (heapSize * limitRatio), limitSize);
        }
    }
}
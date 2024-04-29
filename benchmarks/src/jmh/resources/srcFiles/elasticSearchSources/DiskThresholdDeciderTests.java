/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.routing.allocation.decider;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.cluster.ClusterInfo;
import org.elasticsearch.cluster.ClusterInfoService;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.DiskUsage;
import org.elasticsearch.cluster.ESAllocationTestCase;
import org.elasticsearch.cluster.RestoreInProgress;
import org.elasticsearch.cluster.TestShardRoutingRoleStrategies;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodeRole;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.AllocationId;
import org.elasticsearch.cluster.routing.IndexRoutingTable;
import org.elasticsearch.cluster.routing.IndexShardRoutingTable;
import org.elasticsearch.cluster.routing.RecoverySource;
import org.elasticsearch.cluster.routing.RoutingNode;
import org.elasticsearch.cluster.routing.RoutingNodes;
import org.elasticsearch.cluster.routing.RoutingNodesHelper;
import org.elasticsearch.cluster.routing.RoutingTable;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.ShardRoutingState;
import org.elasticsearch.cluster.routing.TestShardRouting;
import org.elasticsearch.cluster.routing.UnassignedInfo.AllocationStatus;
import org.elasticsearch.cluster.routing.UnassignedInfo.Reason;
import org.elasticsearch.cluster.routing.allocation.AllocationService;
import org.elasticsearch.cluster.routing.allocation.DiskThresholdSettings;
import org.elasticsearch.cluster.routing.allocation.RoutingAllocation;
import org.elasticsearch.cluster.routing.allocation.allocator.BalancedShardsAllocator;
import org.elasticsearch.cluster.routing.allocation.command.AllocationCommand;
import org.elasticsearch.cluster.routing.allocation.command.AllocationCommands;
import org.elasticsearch.cluster.routing.allocation.command.MoveAllocationCommand;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.repositories.IndexId;
import org.elasticsearch.snapshots.EmptySnapshotsInfoService;
import org.elasticsearch.snapshots.InternalSnapshotsInfoService.SnapshotShard;
import org.elasticsearch.snapshots.Snapshot;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotShardSizeInfo;
import org.elasticsearch.snapshots.SnapshotsInfoService;
import org.elasticsearch.test.gateway.TestGatewayAllocator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.elasticsearch.cluster.routing.RoutingNodesHelper.assignedShardsIn;
import static org.elasticsearch.cluster.routing.RoutingNodesHelper.numberOfShardsWithState;
import static org.elasticsearch.cluster.routing.RoutingNodesHelper.shardsWithState;
import static org.elasticsearch.cluster.routing.ShardRoutingState.INITIALIZING;
import static org.elasticsearch.cluster.routing.ShardRoutingState.RELOCATING;
import static org.elasticsearch.cluster.routing.ShardRoutingState.STARTED;
import static org.elasticsearch.cluster.routing.ShardRoutingState.UNASSIGNED;
import static org.elasticsearch.cluster.routing.allocation.decider.EnableAllocationDecider.CLUSTER_ROUTING_REBALANCE_ENABLE_SETTING;
import static org.elasticsearch.common.settings.ClusterSettings.createBuiltInClusterSettings;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;

public class DiskThresholdDeciderTests extends ESAllocationTestCase {

    private void doTestDiskThreshold(boolean testMaxHeadroom) {
        Settings.Builder diskSettings = Settings.builder()
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), 0.7)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), 0.8);
        if (testMaxHeadroom) {
            diskSettings = diskSettings.put(
                DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_MAX_HEADROOM_SETTING.getKey(),
                ByteSizeValue.ofGb(200).toString()
            )
                .put(
                    DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_MAX_HEADROOM_SETTING.getKey(),
                    ByteSizeValue.ofGb(150).toString()
                );
        }

        Map<String, DiskUsage> usages = new HashMap<>();
        final long totalBytes = testMaxHeadroom ? ByteSizeValue.ofGb(10000).getBytes() : 100;
        final long exactFreeSpaceForHighWatermark = testMaxHeadroom ? ByteSizeValue.ofGb(150).getBytes() : 10;
        usages.put("node1", new DiskUsage("node1", "node1", "/dev/null", totalBytes, exactFreeSpaceForHighWatermark));
        usages.put(
            "node2",
            new DiskUsage("node2", "node2", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(350).getBytes() : 35)
        );
        usages.put(
            "node3",
            new DiskUsage("node3", "node3", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(600).getBytes() : 60)
        );
        usages.put(
            "node4",
            new DiskUsage("node4", "node4", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(800).getBytes() : 80)
        );

        Map<String, Long> shardSizes = new HashMap<>();
        shardSizes.put("[test][0][p]", exactFreeSpaceForHighWatermark);
        shardSizes.put("[test][0][r]", exactFreeSpaceForHighWatermark);
        final ClusterInfo clusterInfo = new DevNullClusterInfo(usages, usages, shardSizes);

        AllocationService strategy = createAllocationService(clusterInfo, createDiskThresholdDecider(diskSettings.build()));

        var indexMetadata = IndexMetadata.builder("test")
            .settings(settings(IndexVersion.current()))
            .numberOfShards(1)
            .numberOfReplicas(1)
            .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
            .metadata(Metadata.builder().put(indexMetadata, false).build())
            .routingTable(RoutingTable.builder(TestShardRoutingRoleStrategies.DEFAULT_ROLE_ONLY).addAsNew(indexMetadata).build())
            .build();

        logger.info("--> adding two nodes");
        clusterState = ClusterState.builder(clusterState)
            .nodes(DiscoveryNodes.builder().add(newNode("node1")).add(newNode("node2")))
            .build();
        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());
        logShardStates(clusterState);

        assertThat(shardsWithState(clusterState.getRoutingNodes(), INITIALIZING).size(), equalTo(1));

        logger.info("--> start the shards (primaries)");
        clusterState = startInitializingShardsAndReroute(strategy, clusterState);

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), ShardRoutingState.STARTED).size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node1").size(), equalTo(0));

        logger.info("--> start the shards (replicas)");
        clusterState = startInitializingShardsAndReroute(strategy, clusterState);

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), ShardRoutingState.STARTED).size(), equalTo(1));

        logger.info("--> adding node3");

        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).add(newNode("node3"))).build();
        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), ShardRoutingState.STARTED).size(), equalTo(1));
        assertThat(shardsWithState(clusterState.getRoutingNodes(), ShardRoutingState.INITIALIZING).size(), equalTo(1));

        logger.info("--> start the shards (replicas)");
        clusterState = startInitializingShardsAndReroute(strategy, clusterState);

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), ShardRoutingState.STARTED).size(), equalTo(2));
        assertThat(clusterState.getRoutingNodes().node("node1").size(), equalTo(0));
        assertThat(clusterState.getRoutingNodes().node("node2").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node3").size(), equalTo(1));

        logger.info("--> changing decider settings");

        if (testMaxHeadroom) {
            diskSettings = Settings.builder()
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_MAX_HEADROOM_SETTING.getKey(), ByteSizeValue.ofGb(250))
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_MAX_HEADROOM_SETTING.getKey(), ByteSizeValue.ofGb(150));
        } else {
            diskSettings = Settings.builder()
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), "60%")
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), 0.7);
        }
        strategy = createAllocationService(clusterInfo, createDiskThresholdDecider(diskSettings.build()));

        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());
        logShardStates(clusterState);

        assertThat(shardsWithState(clusterState.getRoutingNodes(), STARTED).size(), equalTo(2));
        assertThat(clusterState.getRoutingNodes().node("node1").size(), equalTo(0));
        assertThat(clusterState.getRoutingNodes().node("node2").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node3").size(), equalTo(1));

        logger.info("--> changing settings again");

        if (testMaxHeadroom) {
            diskSettings = Settings.builder()
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_MAX_HEADROOM_SETTING.getKey(), ByteSizeValue.ofGb(500))
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_MAX_HEADROOM_SETTING.getKey(), ByteSizeValue.ofGb(400));
        } else {
            diskSettings = Settings.builder()
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), 0.5)
                .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), 0.6);
        }
        strategy = createAllocationService(clusterInfo, createDiskThresholdDecider(diskSettings.build()));

        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), STARTED).size(), equalTo(2));
        assertThat(clusterState.getRoutingNodes().node("node1").size(), equalTo(0));
        assertThat(clusterState.getRoutingNodes().node("node2").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node3").size(), equalTo(1));

        logger.info("--> adding node4");

        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).add(newNode("node4"))).build();
        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), STARTED).size(), equalTo(1));
        assertThat(shardsWithState(clusterState.getRoutingNodes(), INITIALIZING).size(), equalTo(1));

        logger.info("--> apply INITIALIZING shards");
        clusterState = startInitializingShardsAndReroute(strategy, clusterState);

        logShardStates(clusterState);
        assertThat(clusterState.getRoutingNodes().node("node1").size(), equalTo(0));
        assertThat(clusterState.getRoutingNodes().node("node2").size(), equalTo(0));
        assertThat(clusterState.getRoutingNodes().node("node3").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node4").size(), equalTo(1));
    }

    public void testDiskThresholdWithPercentages() {
        doTestDiskThreshold(false);
    }

    public void testDiskThresholdWithMaxHeadroom() {
        doTestDiskThreshold(true);
    }

    public void testDiskThresholdWithAbsoluteSizes() {
        Settings diskSettings = Settings.builder()
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), "30b")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), "9b")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_FLOOD_STAGE_WATERMARK_SETTING.getKey(), "5b")
            .build();

        Map<String, DiskUsage> usages = new HashMap<>();
        usages.put("node1", new DiskUsage("node1", "n1", "/dev/null", 100, 10)); 
        usages.put("node2", new DiskUsage("node2", "n2", "/dev/null", 100, 10)); 
        usages.put("node3", new DiskUsage("node3", "n3", "/dev/null", 100, 60)); 
        usages.put("node4", new DiskUsage("node4", "n4", "/dev/null", 100, 80)); 
        usages.put("node5", new DiskUsage("node5", "n5", "/dev/null", 100, 85)); 

        Map<String, Long> shardSizes = new HashMap<>();
        shardSizes.put("[test][0][p]", 10L); 
        shardSizes.put("[test][0][r]", 10L);
        final ClusterInfo clusterInfo = new DevNullClusterInfo(usages, usages, shardSizes);

        AllocationService strategy = createAllocationService(clusterInfo, createDiskThresholdDecider(diskSettings));

        var indexMetadata = IndexMetadata.builder("test")
            .settings(settings(IndexVersion.current()))
            .numberOfShards(1)
            .numberOfReplicas(2)
            .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
            .metadata(Metadata.builder().put(indexMetadata, false).build())
            .routingTable(RoutingTable.builder(TestShardRoutingRoleStrategies.DEFAULT_ROLE_ONLY).addAsNew(indexMetadata).build())
            .build();

        logger.info("--> adding node1 and node2 node");
        clusterState = ClusterState.builder(clusterState)
            .nodes(DiscoveryNodes.builder().add(newNode("node1")).add(newNode("node2")))
            .build();

        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());
        logShardStates(clusterState);

        assertThat(shardsWithState(clusterState.getRoutingNodes(), INITIALIZING).size(), equalTo(1));

        String nodeWithPrimary, nodeWithoutPrimary;
        if (clusterState.getRoutingNodes().node("node1").size() == 1) {
            nodeWithPrimary = "node1";
            nodeWithoutPrimary = "node2";
        } else {
            nodeWithPrimary = "node2";
            nodeWithoutPrimary = "node1";
        }
        logger.info("--> nodeWithPrimary: {}", nodeWithPrimary);
        logger.info("--> nodeWithoutPrimary: {}", nodeWithoutPrimary);

        usages = new HashMap<>(usages);
        usages.put(nodeWithoutPrimary, new DiskUsage(nodeWithoutPrimary, "", "/dev/null", 100, 35)); 
        final ClusterInfo clusterInfo2 = new DevNullClusterInfo(usages, usages, shardSizes);

        strategy = createAllocationService(clusterInfo2, createDiskThresholdDecider(diskSettings));

        logShardStates(clusterState);

        logger.info("--> start the shards (primaries)");
        clusterState = startInitializingShardsAndReroute(strategy, clusterState);

        assertThat(shardsWithState(clusterState.getRoutingNodes(), INITIALIZING).size(), equalTo(1));

        clusterState = startInitializingShardsAndReroute(strategy, clusterState);

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), ShardRoutingState.STARTED).size(), equalTo(2));
        assertThat(clusterState.getRoutingNodes().node("node1").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node2").size(), equalTo(1));


        logger.info("--> adding node3");

        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).add(newNode("node3"))).build();
        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), ShardRoutingState.STARTED).size(), equalTo(2));
        assertThat(shardsWithState(clusterState.getRoutingNodes(), ShardRoutingState.INITIALIZING).size(), equalTo(1));

        logger.info("--> start the shards (replicas)");
        clusterState = startInitializingShardsAndReroute(strategy, clusterState);

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), ShardRoutingState.STARTED).size(), equalTo(3));
        assertThat(clusterState.getRoutingNodes().node("node1").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node2").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node3").size(), equalTo(1));

        logger.info("--> changing decider settings");

        diskSettings = Settings.builder()
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), "40b")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), "30b")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_FLOOD_STAGE_WATERMARK_SETTING.getKey(), "20b")
            .build();
        strategy = createAllocationService(clusterInfo2, createDiskThresholdDecider(diskSettings));

        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());
        logShardStates(clusterState);

        assertThat(shardsWithState(clusterState.getRoutingNodes(), STARTED).size(), equalTo(3));
        assertThat(clusterState.getRoutingNodes().node("node1").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node2").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node3").size(), equalTo(1));

        logger.info("--> changing settings again");

        diskSettings = Settings.builder()
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), "50b")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), "40b")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_FLOOD_STAGE_WATERMARK_SETTING.getKey(), "30b")
            .build();
        strategy = createAllocationService(clusterInfo2, createDiskThresholdDecider(diskSettings));

        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), STARTED).size(), equalTo(3));
        assertThat(clusterState.getRoutingNodes().node("node1").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node2").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node3").size(), equalTo(1));

        logger.info("--> adding node4");

        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).add(newNode("node4"))).build();
        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), STARTED).size(), equalTo(2));
        assertThat(shardsWithState(clusterState.getRoutingNodes(), RELOCATING).size(), equalTo(1));
        assertThat(shardsWithState(clusterState.getRoutingNodes(), INITIALIZING).size(), equalTo(1));

        logger.info("--> apply INITIALIZING shards");
        clusterState = startInitializingShardsAndReroute(strategy, clusterState);

        logShardStates(clusterState);
        assertThat(
            clusterState.getRoutingNodes().node(nodeWithPrimary).size() + clusterState.getRoutingNodes().node(nodeWithoutPrimary).size(),
            equalTo(1)
        );
        assertThat(clusterState.getRoutingNodes().node("node3").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node4").size(), equalTo(1));

        logger.info("--> adding node5");

        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).add(newNode("node5"))).build();
        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), STARTED).size(), equalTo(2));
        assertThat(shardsWithState(clusterState.getRoutingNodes(), RELOCATING).size(), equalTo(1));
        assertThat(shardsWithState(clusterState.getRoutingNodes(), INITIALIZING).size(), equalTo(1));

        logger.info("--> apply INITIALIZING shards");
        clusterState = startInitializingShardsAndReroute(strategy, clusterState);

        logger.info("--> final cluster state:");
        logShardStates(clusterState);
        assertThat(clusterState.getRoutingNodes().node("node1").size(), equalTo(0));
        assertThat(clusterState.getRoutingNodes().node("node2").size(), equalTo(0));
        assertThat(clusterState.getRoutingNodes().node("node3").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node4").size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node5").size(), equalTo(1));
    }

    private void doTestDiskThresholdWithShardSizes(boolean testMaxHeadroom) {
        Settings.Builder diskSettings = Settings.builder()
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), 0.7)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), "71%");
        if (testMaxHeadroom) {
            diskSettings = diskSettings.put(
                DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_MAX_HEADROOM_SETTING.getKey(),
                ByteSizeValue.ofGb(200).toString()
            )
                .put(
                    DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_MAX_HEADROOM_SETTING.getKey(),
                    ByteSizeValue.ofGb(199).toString()
                );
        }

        Map<String, DiskUsage> usages = new HashMap<>();
        final long totalBytes = testMaxHeadroom ? ByteSizeValue.ofGb(10000).getBytes() : 100;
        usages.put(
            "node1",
            new DiskUsage("node1", "n1", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(201).getBytes() : 31)
        );
        usages.put("node2", new DiskUsage("node2", "n2", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(1).getBytes() : 1));

        final ClusterInfo clusterInfo = new DevNullClusterInfo(
            usages,
            usages,
            Map.of("[test][0][p]", testMaxHeadroom ? ByteSizeValue.ofGb(10).getBytes() : 10L)
        );

        AllocationService strategy = createAllocationService(clusterInfo, createDiskThresholdDecider(diskSettings.build()));

        var indexMetadata = IndexMetadata.builder("test")
            .settings(settings(IndexVersion.current()))
            .numberOfShards(1)
            .numberOfReplicas(0)
            .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
            .metadata(Metadata.builder().put(indexMetadata, false).build())
            .routingTable(RoutingTable.builder(TestShardRoutingRoleStrategies.DEFAULT_ROLE_ONLY).addAsNew(indexMetadata).build())
            .build();
        logger.info("--> adding node1");
        clusterState = ClusterState.builder(clusterState)
            .nodes(DiscoveryNodes.builder().add(newNode("node1")).add(newNode("node2")))
            .build();
        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());
        logger.info("--> start the shards (primaries)");
        clusterState = startInitializingShardsAndReroute(strategy, clusterState);
        logShardStates(clusterState);

        assertThat(shardsWithState(clusterState.getRoutingNodes(), INITIALIZING).size(), equalTo(0));
        assertThat(shardsWithState(clusterState.getRoutingNodes(), STARTED).size(), equalTo(0));
    }

    public void testDiskThresholdWithShardSizesWithPercentages() {
        doTestDiskThresholdWithShardSizes(false);
    }

    public void testDiskThresholdWithShardSizesWithMaxHeadroom() {
        doTestDiskThresholdWithShardSizes(true);
    }

    public void testUnknownDiskUsage() {
        Settings diskSettings = Settings.builder()
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), 0.7)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), 0.85)
            .build();

        Map<String, DiskUsage> usages = new HashMap<>();
        usages.put("node2", new DiskUsage("node2", "node2", "/dev/null", 100, 50)); 
        usages.put("node3", new DiskUsage("node3", "node3", "/dev/null", 100, 0));  

        Map<String, Long> shardSizes = new HashMap<>();
        shardSizes.put("[test][0][p]", 10L); 
        shardSizes.put("[test][0][r]", 10L); 
        final ClusterInfo clusterInfo = new DevNullClusterInfo(usages, usages, shardSizes);

        AllocationService strategy = createAllocationService(clusterInfo, createDiskThresholdDecider(diskSettings));

        var indexMetadata = IndexMetadata.builder("test")
            .settings(settings(IndexVersion.current()))
            .numberOfShards(1)
            .numberOfReplicas(0)
            .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
            .metadata(Metadata.builder().put(indexMetadata, false).build())
            .routingTable(RoutingTable.builder(TestShardRoutingRoleStrategies.DEFAULT_ROLE_ONLY).addAsNew(indexMetadata).build())
            .build();
        logger.info("--> adding node1");
        clusterState = ClusterState.builder(clusterState)
            .nodes(
                DiscoveryNodes.builder().add(newNode("node1")).add(newNode("node3")) 
            )
            .build();
        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());

        assertThat(shardsWithState(clusterState.getRoutingNodes(), INITIALIZING).size(), equalTo(1));

        logger.info("--> start the shards (primaries)");
        clusterState = startInitializingShardsAndReroute(strategy, clusterState);
        logShardStates(clusterState);

        assertThat(shardsWithState(clusterState.getRoutingNodes(), STARTED).size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().node("node1").size(), equalTo(1));
    }

    public void testAverageUsage() {
        RoutingNode rn = RoutingNodesHelper.routingNode("node1", newNode("node1"));

        Map<String, DiskUsage> usages = new HashMap<>();
        usages.put("node2", new DiskUsage("node2", "n2", "/dev/null", 100, 50)); 
        usages.put("node3", new DiskUsage("node3", "n3", "/dev/null", 100, 0));  

        DiskUsage node1Usage = DiskThresholdDecider.averageUsage(rn, usages);
        assertThat(node1Usage.totalBytes(), equalTo(100L));
        assertThat(node1Usage.freeBytes(), equalTo(25L));
    }

    private void doTestShardRelocationsTakenIntoAccount(boolean testMaxHeadroom) {
        Settings.Builder diskSettings = Settings.builder()
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), 0.7)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), 0.8);
        if (testMaxHeadroom) {
            diskSettings = diskSettings.put(
                DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_MAX_HEADROOM_SETTING.getKey(),
                ByteSizeValue.ofGb(150).toString()
            )
                .put(
                    DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_MAX_HEADROOM_SETTING.getKey(),
                    ByteSizeValue.ofGb(110).toString()
                );
        }

        Map<String, DiskUsage> usages = new HashMap<>();
        final long totalBytes = testMaxHeadroom ? ByteSizeValue.ofGb(10000).getBytes() : 100;
        usages.put(
            "node1",
            new DiskUsage("node1", "n1", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(160).getBytes() : 40)
        );
        usages.put(
            "node2",
            new DiskUsage("node2", "n2", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(160).getBytes() : 40)
        );
        usages.put(
            "node3",
            new DiskUsage("node3", "n3", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(160).getBytes() : 40)
        );

        Map<String, Long> shardSizes = new HashMap<>();
        shardSizes.put("[test][0][p]", testMaxHeadroom ? ByteSizeValue.ofGb(14).getBytes() : 14L);
        shardSizes.put("[test][0][r]", testMaxHeadroom ? ByteSizeValue.ofGb(14).getBytes() : 14L);
        shardSizes.put("[test2][0][p]", testMaxHeadroom ? ByteSizeValue.ofGb(1).getBytes() : 1L);
        shardSizes.put("[test2][0][r]", testMaxHeadroom ? ByteSizeValue.ofGb(1).getBytes() : 1L);
        final ClusterInfo clusterInfo = new DevNullClusterInfo(usages, usages, shardSizes);

        final AtomicReference<ClusterInfo> clusterInfoReference = new AtomicReference<>(clusterInfo);

        AllocationService strategy = createAllocationService(
            clusterInfoReference::get,
            EmptySnapshotsInfoService.INSTANCE,
            createEnableAllocationDecider(Settings.builder().put(CLUSTER_ROUTING_REBALANCE_ENABLE_SETTING.getKey(), "none").build()),
            createDiskThresholdDecider(diskSettings.build())
        );

        var indexMetadata1 = IndexMetadata.builder("test")
            .settings(settings(IndexVersion.current()))
            .numberOfShards(1)
            .numberOfReplicas(1)
            .build();
        var indexMetadata2 = IndexMetadata.builder("test2")
            .settings(settings(IndexVersion.current()))
            .numberOfShards(1)
            .numberOfReplicas(1)
            .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
            .metadata(Metadata.builder().put(indexMetadata1, false).put(indexMetadata2, false).build())
            .routingTable(
                RoutingTable.builder(TestShardRoutingRoleStrategies.DEFAULT_ROLE_ONLY)
                    .addAsNew(indexMetadata1)
                    .addAsNew(indexMetadata2)
                    .build()
            )
            .build();

        logger.info("--> adding two nodes");
        clusterState = ClusterState.builder(clusterState)
            .nodes(DiscoveryNodes.builder().add(newNode("node1")).add(newNode("node2")))
            .build();
        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());
        logShardStates(clusterState);

        assertThat(shardsWithState(clusterState.getRoutingNodes(), INITIALIZING).size(), equalTo(2));

        logger.info("--> start the shards");
        clusterState = startInitializingShardsAndReroute(strategy, clusterState);

        assertThat(shardsWithState(clusterState.getRoutingNodes(), INITIALIZING).size(), equalTo(2));

        clusterState = startInitializingShardsAndReroute(strategy, clusterState);

        logShardStates(clusterState);
        assertThat(shardsWithState(clusterState.getRoutingNodes(), ShardRoutingState.STARTED).size(), equalTo(4));

        logger.info("--> adding node3");
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).add(newNode("node3"))).build();

        {
            AllocationCommand moveAllocationCommand = new MoveAllocationCommand("test", 0, "node2", "node3");
            AllocationCommands cmds = new AllocationCommands(moveAllocationCommand);

            clusterState = strategy.reroute(clusterState, cmds, false, false, false, ActionListener.noop()).clusterState();
            logShardStates(clusterState);
        }

        Map<String, DiskUsage> overfullUsages = new HashMap<>();
        overfullUsages.put(
            "node1",
            new DiskUsage("node1", "n1", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(160).getBytes() : 40)
        );
        overfullUsages.put(
            "node2",
            new DiskUsage("node2", "n2", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(160).getBytes() : 40)
        );
        overfullUsages.put("node3", new DiskUsage("node3", "n3", "/dev/null", totalBytes, 0));  

        Map<String, Long> largerShardSizes = new HashMap<>();
        largerShardSizes.put("[test][0][p]", testMaxHeadroom ? ByteSizeValue.ofGb(14).getBytes() : 14L);
        largerShardSizes.put("[test][0][r]", testMaxHeadroom ? ByteSizeValue.ofGb(14).getBytes() : 14L);
        largerShardSizes.put("[test2][0][p]", testMaxHeadroom ? ByteSizeValue.ofGb(2).getBytes() : 2L);
        largerShardSizes.put("[test2][0][r]", testMaxHeadroom ? ByteSizeValue.ofGb(2).getBytes() : 2L);

        final ClusterInfo overfullClusterInfo = new DevNullClusterInfo(overfullUsages, overfullUsages, largerShardSizes);

        {
            AllocationCommand moveAllocationCommand = new MoveAllocationCommand("test2", 0, "node2", "node3");
            AllocationCommands cmds = new AllocationCommands(moveAllocationCommand);

            final ClusterState clusterStateThatRejectsCommands = clusterState;

            assertThat(
                expectThrows(
                    IllegalArgumentException.class,
                    () -> strategy.reroute(clusterStateThatRejectsCommands, cmds, false, false, false, ActionListener.noop())
                ).getMessage(),
                containsString(
                    testMaxHeadroom
                        ? "the node is above the low watermark cluster setting "
                            + "[cluster.routing.allocation.disk.watermark.low.max_headroom=150gb], "
                            + "having less than the minimum required [150gb] free space, actual free: [146gb], actual used: [98.5%]"
                        : "the node is above the low watermark cluster setting [cluster.routing.allocation.disk.watermark.low=70%], "
                            + "having less than the minimum required [30b] free space, actual free: [26b], actual used: [74%]"
                )
            );

            clusterInfoReference.set(overfullClusterInfo);

            assertThat(
                expectThrows(
                    IllegalArgumentException.class,
                    () -> strategy.reroute(clusterStateThatRejectsCommands, cmds, false, false, false, ActionListener.noop())
                ).getMessage(),
                containsString("the node has fewer free bytes remaining than the total size of all incoming shards")
            );

            clusterInfoReference.set(clusterInfo);
        }

        {
            AllocationCommand moveAllocationCommand = new MoveAllocationCommand("test2", 0, "node2", "node3");
            AllocationCommands cmds = new AllocationCommands(moveAllocationCommand);

            clusterState = startInitializingShardsAndReroute(strategy, clusterState);
            clusterState = strategy.reroute(clusterState, cmds, false, false, false, ActionListener.noop()).clusterState();
            logShardStates(clusterState);

            clusterInfoReference.set(overfullClusterInfo);

            strategy.reroute(clusterState, "foo", ActionListener.noop()); 
        }

        {
            clusterInfoReference.set(overfullClusterInfo);
            clusterState = applyStartedShardsUntilNoChange(clusterState, strategy);
            final List<ShardRouting> startedShardsWithOverfullDisk = shardsWithState(clusterState.getRoutingNodes(), STARTED);
            assertThat(startedShardsWithOverfullDisk.size(), equalTo(4));
            for (ShardRouting shardRouting : startedShardsWithOverfullDisk) {
                assertThat(shardRouting.toString(), shardRouting.currentNodeId(), oneOf("node1", "node2"));
            }

            clusterInfoReference.set(
                new DevNullClusterInfo(
                    usages,
                    usages,
                    shardSizes,
                    Map.of(
                        new ClusterInfo.NodeAndPath("node1", "/dev/null"),
                        new ClusterInfo.ReservedSpace.Builder().add(
                            new ShardId("", "", 0),
                            testMaxHeadroom ? ByteSizeValue.ofGb(between(200, 250)).getBytes() : between(51, 200)
                        ).build()
                    )
                )
            );
            clusterState = applyStartedShardsUntilNoChange(clusterState, strategy);
            final List<ShardRouting> startedShardsWithReservedSpace = shardsWithState(clusterState.getRoutingNodes(), STARTED);
            assertThat(startedShardsWithReservedSpace.size(), equalTo(4));
            for (ShardRouting shardRouting : startedShardsWithReservedSpace) {
                assertThat(shardRouting.toString(), shardRouting.currentNodeId(), oneOf("node2", "node3"));
            }
        }
    }

    public void testShardRelocationsTakenIntoAccountWithPercentages() {
        doTestShardRelocationsTakenIntoAccount(false);
    }

    public void testShardRelocationsTakenIntoAccountWithMaxHeadroom() {
        doTestShardRelocationsTakenIntoAccount(true);
    }

    private void doTestCanRemainWithShardRelocatingAway(boolean testMaxHeadroom) {
        Settings.Builder diskSettings = Settings.builder()
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), "60%")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), "70%");
        if (testMaxHeadroom) {
            diskSettings = diskSettings.put(
                DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_MAX_HEADROOM_SETTING.getKey(),
                ByteSizeValue.ofGb(150).toString()
            )
                .put(
                    DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_MAX_HEADROOM_SETTING.getKey(),
                    ByteSizeValue.ofGb(110).toString()
                );
        }

        Map<String, DiskUsage> usages = new HashMap<>();
        final long totalBytes = testMaxHeadroom ? ByteSizeValue.ofGb(10000).getBytes() : 100;
        usages.put(
            "node1",
            new DiskUsage("node1", "n1", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(40).getBytes() : 20)
        );
        usages.put(
            "node2",
            new DiskUsage("node2", "n2", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(10000).getBytes() : 100)
        );

        Map<String, Long> shardSizes = new HashMap<>();
        shardSizes.put("[test][0][p]", testMaxHeadroom ? ByteSizeValue.ofGb(4980).getBytes() : 40L);
        shardSizes.put("[test][1][p]", testMaxHeadroom ? ByteSizeValue.ofGb(4980).getBytes() : 40L);
        shardSizes.put("[foo][0][p]", testMaxHeadroom ? ByteSizeValue.ofGb(10).getBytes() : 10L);

        final ClusterInfo clusterInfo = new DevNullClusterInfo(usages, usages, shardSizes);

        DiskThresholdDecider diskThresholdDecider = createDiskThresholdDecider(diskSettings.build());

        DiscoveryNode discoveryNode1 = newNode("node1");
        DiscoveryNode discoveryNode2 = newNode("node2");

        var testMetadata = IndexMetadata.builder("test")
            .settings(settings(IndexVersion.current()))
            .numberOfShards(2)
            .numberOfReplicas(0)
            .build();
        var fooMetadata = IndexMetadata.builder("foo")
            .settings(settings(IndexVersion.current()))
            .numberOfShards(1)
            .numberOfReplicas(0)
            .build();
        ClusterState baseClusterState = ClusterState.builder(ClusterName.DEFAULT)
            .metadata(Metadata.builder().put(testMetadata, false).put(fooMetadata, false).build())
            .routingTable(
                RoutingTable.builder(TestShardRoutingRoleStrategies.DEFAULT_ROLE_ONLY).addAsNew(testMetadata).addAsNew(fooMetadata).build()
            )
            .nodes(DiscoveryNodes.builder().add(discoveryNode1).add(discoveryNode2).build())
            .build();

        ShardRouting firstRouting = TestShardRouting.newShardRouting("test", 0, "node1", null, true, ShardRoutingState.STARTED);
        ShardRouting secondRouting = TestShardRouting.newShardRouting("test", 1, "node1", null, true, ShardRoutingState.STARTED);
        RoutingNode firstRoutingNode = RoutingNodesHelper.routingNode("node1", discoveryNode1, firstRouting, secondRouting);
        RoutingTable.Builder builder = RoutingTable.builder()
            .add(
                IndexRoutingTable.builder(firstRouting.index())
                    .addIndexShard(IndexShardRoutingTable.builder(firstRouting.shardId()).addShard(firstRouting))
                    .addIndexShard(IndexShardRoutingTable.builder(secondRouting.shardId()).addShard(secondRouting))
            );
        ClusterState clusterState = ClusterState.builder(baseClusterState).routingTable(builder.build()).build();
        RoutingAllocation routingAllocation = new RoutingAllocation(
            null,
            RoutingNodes.immutable(clusterState.routingTable(), clusterState.nodes()),
            clusterState,
            clusterInfo,
            null,
            System.nanoTime()
        );
        routingAllocation.debugDecision(true);
        Decision decision = diskThresholdDecider.canRemain(
            routingAllocation.metadata().getIndexSafe(firstRouting.index()),
            firstRouting,
            firstRoutingNode,
            routingAllocation
        );
        assertThat(decision.type(), equalTo(Decision.Type.NO));
        assertThat(
            decision.getExplanation(),
            containsString(
                testMaxHeadroom
                    ? "the shard cannot remain on this node because it is above the high watermark cluster setting "
                        + "[cluster.routing.allocation.disk.watermark.high.max_headroom=110gb] and there is less than the required [110gb] "
                        + "free space on node, actual free: [40gb], actual used: [99.6%]"
                    : "the shard cannot remain on this node because it is above the high watermark cluster setting "
                        + "[cluster.routing.allocation.disk.watermark.high=70%] and there is less than the required [30b] free space "
                        + "on node, actual free: [20b], actual used: [80%]"
            )
        );

        firstRouting = TestShardRouting.newShardRouting("test", 0, "node1", null, true, ShardRoutingState.STARTED);
        secondRouting = TestShardRouting.newShardRouting("test", 1, "node1", "node2", true, ShardRoutingState.RELOCATING);
        ShardRouting fooRouting = TestShardRouting.newShardRouting("foo", 0, null, true, ShardRoutingState.UNASSIGNED);
        firstRoutingNode = RoutingNodesHelper.routingNode("node1", discoveryNode1, firstRouting, secondRouting);
        builder = RoutingTable.builder()
            .add(
                IndexRoutingTable.builder(firstRouting.index())
                    .addIndexShard(new IndexShardRoutingTable.Builder(firstRouting.shardId()).addShard(firstRouting))
                    .addIndexShard(new IndexShardRoutingTable.Builder(secondRouting.shardId()).addShard(secondRouting))
            );
        clusterState = ClusterState.builder(baseClusterState).routingTable(builder.build()).build();
        routingAllocation = new RoutingAllocation(
            null,
            RoutingNodes.immutable(clusterState.routingTable(), clusterState.nodes()),
            clusterState,
            clusterInfo,
            null,
            System.nanoTime()
        );
        routingAllocation.debugDecision(true);
        decision = diskThresholdDecider.canRemain(
            routingAllocation.metadata().getIndexSafe(firstRouting.index()),
            firstRouting,
            firstRoutingNode,
            routingAllocation
        );
        assertThat(decision.type(), equalTo(Decision.Type.YES));
        assertEquals(
            testMaxHeadroom
                ? "there is enough disk on this node for the shard to remain, free: [4.9tb]"
                : "there is enough disk on this node for the shard to remain, free: [60b]",
            decision.getExplanation()
        );
        decision = diskThresholdDecider.canAllocate(fooRouting, firstRoutingNode, routingAllocation);
        assertThat(decision.type(), equalTo(Decision.Type.NO));
        if (fooRouting.recoverySource().getType() == RecoverySource.Type.EMPTY_STORE) {
            assertThat(
                decision.getExplanation(),
                containsString(
                    testMaxHeadroom
                        ? "the node is above the high watermark cluster setting [cluster.routing.allocation.disk.watermark"
                            + ".high.max_headroom=110gb], having less than the minimum required [110gb] free space, actual free: "
                            + "[40gb], actual used: [99.6%]"
                        : "the node is above the high watermark cluster setting [cluster.routing.allocation.disk.watermark.high=70%], "
                            + "having less than the minimum required [30b] free space, actual free: [20b], actual used: [80%]"
                )
            );
        } else {
            assertThat(
                decision.getExplanation(),
                containsString(
                    testMaxHeadroom
                        ? "the node is above the low watermark cluster setting [cluster.routing.allocation.disk.watermark.low"
                            + ".max_headroom=150gb], having less than the minimum required [150gb] free space, actual free: [40gb], actual "
                            + "used: [99.6%]"
                        : "the node is above the low watermark cluster setting [cluster.routing.allocation.disk.watermark.low=60%], "
                            + "having less than the minimum required [40b] free space, actual free: [20b], actual used: [80%]"
                )
            );
        }

        AllocationService strategy = createAllocationService(clusterInfo, diskThresholdDecider);
        ClusterState result = strategy.reroute(clusterState, "reroute", ActionListener.noop());
        assertThat(result, equalTo(clusterState));
        assertThat(result.routingTable().index("test").shard(0).primaryShard().state(), equalTo(STARTED));
        assertThat(result.routingTable().index("test").shard(0).primaryShard().currentNodeId(), equalTo("node1"));
        assertThat(result.routingTable().index("test").shard(0).primaryShard().relocatingNodeId(), nullValue());
        assertThat(result.routingTable().index("test").shard(1).primaryShard().state(), equalTo(RELOCATING));
        assertThat(result.routingTable().index("test").shard(1).primaryShard().currentNodeId(), equalTo("node1"));
        assertThat(result.routingTable().index("test").shard(1).primaryShard().relocatingNodeId(), equalTo("node2"));
    }

    public void testCanRemainWithShardRelocatingAwayWithPercentages() {
        doTestCanRemainWithShardRelocatingAway(false);
    }

    public void testCanRemainWithShardRelocatingAwayWithMaxHeadroom() {
        doTestCanRemainWithShardRelocatingAway(true);
    }

    private void doTestWatermarksEnabledForSingleDataNode(boolean testMaxHeadroom) {
        Settings.Builder builder = Settings.builder()
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), "60%")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), "70%");
        if (testMaxHeadroom) {
            builder = builder.put(
                DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_MAX_HEADROOM_SETTING.getKey(),
                ByteSizeValue.ofGb(150).toString()
            )
                .put(
                    DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_MAX_HEADROOM_SETTING.getKey(),
                    ByteSizeValue.ofGb(110).toString()
                );
        }
        if (randomBoolean()) {
            builder = builder.put(DiskThresholdDecider.ENABLE_FOR_SINGLE_DATA_NODE.getKey(), true);
        }
        Settings diskSettings = builder.build();

        final long totalBytes = testMaxHeadroom ? ByteSizeValue.ofGb(10000).getBytes() : 100;
        Map<String, DiskUsage> usages = Map.of(
            "data",
            new DiskUsage("data", "data", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(40).getBytes() : 20)
        );

        Map<String, Long> shardSizes = Map.of("[test][0][p]", testMaxHeadroom ? ByteSizeValue.ofGb(60).getBytes() : 40L);
        final ClusterInfo clusterInfo = new DevNullClusterInfo(usages, usages, shardSizes);

        DiskThresholdDecider diskThresholdDecider = createDiskThresholdDecider(diskSettings);

        var discoveryNodesBuilder = DiscoveryNodes.builder().add(newNode("data", "data", Set.of(DiscoveryNodeRole.DATA_ROLE)));
        if (randomBoolean()) {
            discoveryNodesBuilder.add(newNode("master", "master", Set.of(DiscoveryNodeRole.MASTER_ROLE)));
        }

        var testMetadata = IndexMetadata.builder("test")
            .settings(settings(IndexVersion.current()))
            .numberOfShards(1)
            .numberOfReplicas(0)
            .build();
        ClusterState clusterState = ClusterState.builder(new ClusterName("test"))
            .nodes(discoveryNodesBuilder.build())
            .metadata(Metadata.builder().put(testMetadata, false).build())
            .routingTable(RoutingTable.builder(TestShardRoutingRoleStrategies.DEFAULT_ROLE_ONLY).addAsNew(testMetadata).build())
            .build();

        AllocationService strategy = createAllocationService(clusterInfo, diskThresholdDecider);
        ClusterState result = strategy.reroute(clusterState, "reroute", ActionListener.noop());

        ShardRouting shardRouting = result.routingTable().index("test").shard(0).primaryShard();
        assertThat(shardRouting.state(), equalTo(UNASSIGNED));
        assertThat(shardRouting.currentNodeId(), nullValue());
        assertThat(shardRouting.relocatingNodeId(), nullValue());

        ShardId shardId = shardRouting.shardId();
        ShardRouting startedShard = shardRouting.initialize("data", null, 40L).moveToStarted(ShardRouting.UNAVAILABLE_EXPECTED_SHARD_SIZE);
        RoutingTable forceAssignedRoutingTable = RoutingTable.builder()
            .add(
                IndexRoutingTable.builder(shardId.getIndex())
                    .addIndexShard(new IndexShardRoutingTable.Builder(shardId).addShard(startedShard))
            )
            .build();
        clusterState = ClusterState.builder(clusterState).routingTable(forceAssignedRoutingTable).build();

        RoutingAllocation routingAllocation = new RoutingAllocation(
            null,
            RoutingNodes.immutable(clusterState.routingTable(), clusterState.nodes()),
            clusterState,
            clusterInfo,
            null,
            System.nanoTime()
        );
        routingAllocation.debugDecision(true);
        Decision decision = diskThresholdDecider.canRemain(
            routingAllocation.metadata().getIndexSafe(startedShard.index()),
            startedShard,
            clusterState.getRoutingNodes().node("data"),
            routingAllocation
        );
        assertThat(decision.type(), equalTo(Decision.Type.NO));
        assertThat(
            decision.getExplanation(),
            containsString(
                testMaxHeadroom
                    ? "the shard cannot remain on this node because it is above the high watermark cluster setting [cluster"
                        + ".routing.allocation.disk.watermark.high.max_headroom=110gb] and there is less than the required [110gb] free "
                        + "space on node, actual free: [40gb], actual used: [99.6%]"
                    : "the shard cannot remain on this node because it is above the high watermark cluster setting"
                        + " [cluster.routing.allocation.disk.watermark.high=70%] and there is less than the required [30b] free space "
                        + "on node, actual free: [20b], actual used: [80%]"
            )
        );

        if (DiskThresholdDecider.ENABLE_FOR_SINGLE_DATA_NODE.exists(diskSettings)) {
            assertSettingDeprecationsAndWarnings(new Setting<?>[] { DiskThresholdDecider.ENABLE_FOR_SINGLE_DATA_NODE });
        }
    }

    public void testWatermarksEnabledForSingleDataNodeWithPercentages() {
        doTestWatermarksEnabledForSingleDataNode(false);
    }

    public void testWatermarksEnabledForSingleDataNodeWithMaxHeadroom() {
        doTestWatermarksEnabledForSingleDataNode(true);
    }

    public void testSingleDataNodeDeprecationWarning() {
        Settings settings = Settings.builder().put(DiskThresholdDecider.ENABLE_FOR_SINGLE_DATA_NODE.getKey(), false).build();

        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> new DiskThresholdDecider(settings, new ClusterSettings(settings, ClusterSettings.BUILT_IN_CLUSTER_SETTINGS))
        );

        assertThat(
            e.getCause().getMessage(),
            equalTo(
                "setting [cluster.routing.allocation.disk.watermark.enable_for_single_data_node=false] is not allowed,"
                    + " only true is valid"
            )
        );

        assertSettingDeprecationsAndWarnings(new Setting<?>[] { DiskThresholdDecider.ENABLE_FOR_SINGLE_DATA_NODE });
    }

    private void doTestDiskThresholdWithSnapshotShardSizes(boolean testMaxHeadroom) {
        final long shardSizeInBytes = randomBoolean()
            ? (testMaxHeadroom ? ByteSizeValue.ofGb(99).getBytes() : 10L) 
            : (testMaxHeadroom ? ByteSizeValue.ofGb(350).getBytes() : 50L); 
        logger.info("--> using shard size [{}]", shardSizeInBytes);

        Settings.Builder diskSettings = Settings.builder()
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_THRESHOLD_ENABLED_SETTING.getKey(), true)
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), "90%")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), "95%");
        if (testMaxHeadroom) {
            diskSettings = diskSettings.put(
                DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_MAX_HEADROOM_SETTING.getKey(),
                ByteSizeValue.ofGb(150).toString()
            )
                .put(
                    DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_MAX_HEADROOM_SETTING.getKey(),
                    ByteSizeValue.ofGb(110).toString()
                );
        }

        Map<String, DiskUsage> usages = new HashMap<>();
        final long totalBytes = testMaxHeadroom ? ByteSizeValue.ofGb(10000).getBytes() : 100;
        usages.put(
            "node1",
            new DiskUsage("node1", "n1", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(210).getBytes() : 21)
        );
        usages.put("node2", new DiskUsage("node2", "n2", "/dev/null", totalBytes, testMaxHeadroom ? ByteSizeValue.ofGb(1).getBytes() : 1));

        final Snapshot snapshot = new Snapshot("_repository", new SnapshotId("_snapshot_name", UUIDs.randomBase64UUID(random())));
        final IndexId indexId = new IndexId("_indexid_name", UUIDs.randomBase64UUID(random()));
        final ShardId shardId = new ShardId(new Index("test", IndexMetadata.INDEX_UUID_NA_VALUE), 0);

        var indexMetadata = IndexMetadata.builder("test")
            .settings(settings(IndexVersion.current()))
            .numberOfShards(1)
            .numberOfReplicas(0)
            .putInSyncAllocationIds(0, Set.of(AllocationId.newInitializing().getId()))
            .build();
        ClusterState clusterState = ClusterState.builder(new ClusterName(getTestName()))
            .metadata(Metadata.builder().put(indexMetadata, false).build())
            .routingTable(
                RoutingTable.builder(TestShardRoutingRoleStrategies.DEFAULT_ROLE_ONLY)
                    .addAsNewRestore(
                        indexMetadata,
                        new RecoverySource.SnapshotRecoverySource("_restore_uuid", snapshot, IndexVersion.current(), indexId),
                        new HashSet<>()
                    )
                    .build()
            )
            .putCustom(
                RestoreInProgress.TYPE,
                new RestoreInProgress.Builder().add(
                    new RestoreInProgress.Entry(
                        "_restore_uuid",
                        snapshot,
                        RestoreInProgress.State.INIT,
                        false,
                        List.of("test"),
                        Map.of(shardId, new RestoreInProgress.ShardRestoreStatus("node1"))
                    )
                ).build()
            )
            .nodes(DiscoveryNodes.builder().add(newNode("node1")).add(newNode("node2")) 
            )
            .build();

        assertThat(
            shardsWithState(clusterState.getRoutingNodes(), UNASSIGNED).stream()
                .map(ShardRouting::unassignedInfo)
                .allMatch(unassignedInfo -> Reason.NEW_INDEX_RESTORED.equals(unassignedInfo.getReason())),
            is(true)
        );
        assertThat(
            shardsWithState(clusterState.getRoutingNodes(), UNASSIGNED).stream()
                .map(ShardRouting::unassignedInfo)
                .allMatch(unassignedInfo -> AllocationStatus.NO_ATTEMPT.equals(unassignedInfo.getLastAllocationStatus())),
            is(true)
        );
        assertThat(shardsWithState(clusterState.getRoutingNodes(), UNASSIGNED).size(), equalTo(1));

        final AtomicReference<SnapshotShardSizeInfo> snapshotShardSizeInfoRef = new AtomicReference<>(SnapshotShardSizeInfo.EMPTY);

        final AllocationService strategy = createAllocationService(
            () -> new DevNullClusterInfo(usages, usages, Map.of()),
            snapshotShardSizeInfoRef::get,
            new RestoreInProgressAllocationDecider(),
            createDiskThresholdDecider(diskSettings.build())
        );

        clusterState = strategy.reroute(clusterState, "reroute", ActionListener.noop());
        logShardStates(clusterState);

        assertThat(
            shardsWithState(clusterState.getRoutingNodes(), UNASSIGNED).stream()
                .map(ShardRouting::unassignedInfo)
                .allMatch(unassignedInfo -> AllocationStatus.FETCHING_SHARD_DATA.equals(unassignedInfo.getLastAllocationStatus())),
            is(true)
        );
        assertThat(shardsWithState(clusterState.getRoutingNodes(), UNASSIGNED).size(), equalTo(1));

        final SnapshotShard snapshotShard = new SnapshotShard(snapshot, indexId, shardId);
        final Map<SnapshotShard, Long> snapshotShardSizes = new HashMap<>();

        final boolean shouldAllocate;
        if (randomBoolean()) {
            logger.info("--> simulating snapshot shards size retrieval success");
            snapshotShardSizes.put(snapshotShard, shardSizeInBytes);
            logger.info("--> shard allocation depends on its size");
            DiskUsage usage = usages.get("node1");
            shouldAllocate = shardSizeInBytes < usage.freeBytes();
        } else {
            logger.info("--> simulating snapshot shards size retrieval failure");
            snapshotShardSizes.put(snapshotShard, ShardRouting.UNAVAILABLE_EXPECTED_SHARD_SIZE);
            logger.info("--> shard is always allocated when its size could not be retrieved");
            shouldAllocate = true;
        }
        snapshotShardSizeInfoRef.set(new SnapshotShardSizeInfo(snapshotShardSizes));

        clusterState = startInitializingShardsAndReroute(strategy, clusterState);
        logShardStates(clusterState);

        assertThat(shardsWithState(clusterState.getRoutingNodes(), UNASSIGNED).size(), equalTo(shouldAllocate ? 0 : 1));
        assertThat(
            shardsWithState(clusterState.getRoutingNodes(), "test", INITIALIZING).size() 
                + shardsWithState(clusterState.getRoutingNodes(), "test", STARTED).size(),
            equalTo(shouldAllocate ? 1 : 0)
        );
    }

    public void testDiskThresholdWithSnapshotShardSizesWithPercentages() {
        doTestDiskThresholdWithSnapshotShardSizes(false);
    }

    public void testDiskThresholdWithSnapshotShardSizesWithMaxHeadroom() {
        doTestDiskThresholdWithSnapshotShardSizes(true);
    }

    public void logShardStates(ClusterState state) {
        RoutingNodes rn = state.getRoutingNodes();
        logger.info(
            "--> counts: total: {}, unassigned: {}, initializing: {}, relocating: {}, started: {}",
            assignedShardsIn(rn).count(),
            rn.unassigned().size(),
            numberOfShardsWithState(rn, INITIALIZING),
            numberOfShardsWithState(rn, RELOCATING),
            numberOfShardsWithState(rn, STARTED)
        );
        logger.info(
            "--> unassigned: {}, initializing: {}, relocating: {}, started: {}",
            shardsWithState(rn, UNASSIGNED),
            shardsWithState(rn, INITIALIZING),
            shardsWithState(rn, RELOCATING),
            shardsWithState(rn, STARTED)
        );
    }

    private AllocationService createAllocationService(ClusterInfo clusterInfo, AllocationDecider... allocationDeciders) {
        return createAllocationService(() -> clusterInfo, EmptySnapshotsInfoService.INSTANCE, allocationDeciders);
    }

    private AllocationService createAllocationService(
        ClusterInfoService clusterInfoService,
        SnapshotsInfoService snapshotShardSizeInfoService,
        AllocationDecider... allocationDeciders
    ) {
        return new AllocationService(
            new AllocationDeciders(
                Stream.concat(
                    Stream.of(createSameShardAllocationDecider(Settings.EMPTY), new ReplicaAfterPrimaryActiveAllocationDecider()),
                    Stream.of(allocationDeciders)
                ).toList()
            ),
            new TestGatewayAllocator(),
            new BalancedShardsAllocator(Settings.EMPTY),
            clusterInfoService,
            snapshotShardSizeInfoService,
            TestShardRoutingRoleStrategies.DEFAULT_ROLE_ONLY
        );
    }

    private DiskThresholdDecider createDiskThresholdDecider(Settings settings) {
        return new DiskThresholdDecider(settings, createBuiltInClusterSettings(settings));
    }

    private SameShardAllocationDecider createSameShardAllocationDecider(Settings settings) {
        return new SameShardAllocationDecider(createBuiltInClusterSettings(settings));
    }

    private EnableAllocationDecider createEnableAllocationDecider(Settings settings) {
        return new EnableAllocationDecider(createBuiltInClusterSettings(settings));
    }

    /**
     * ClusterInfo that always reports /dev/null for the shards' data paths.
     */
    static class DevNullClusterInfo extends ClusterInfo {
        DevNullClusterInfo(
            Map<String, DiskUsage> leastAvailableSpaceUsage,
            Map<String, DiskUsage> mostAvailableSpaceUsage,
            Map<String, Long> shardSizes
        ) {
            this(leastAvailableSpaceUsage, mostAvailableSpaceUsage, shardSizes, Map.of());
        }

        DevNullClusterInfo(
            Map<String, DiskUsage> leastAvailableSpaceUsage,
            Map<String, DiskUsage> mostAvailableSpaceUsage,
            Map<String, Long> shardSizes,
            Map<NodeAndPath, ReservedSpace> reservedSpace
        ) {
            super(leastAvailableSpaceUsage, mostAvailableSpaceUsage, shardSizes, Map.of(), Map.of(), reservedSpace);
        }

        @Override
        public String getDataPath(ShardRouting shardRouting) {
            return "/dev/null";
        }
    }
}
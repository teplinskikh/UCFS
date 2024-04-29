package org.elasticsearch.compute.aggregation;

import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.util.List;
import org.elasticsearch.compute.operator.DriverContext;

/**
 * {@link AggregatorFunctionSupplier} implementation for {@link CountDistinctLongAggregator}.
 * This class is generated. Do not edit it.
 */
public final class CountDistinctLongAggregatorFunctionSupplier implements AggregatorFunctionSupplier {
  private final List<Integer> channels;

  private final int precision;

  public CountDistinctLongAggregatorFunctionSupplier(List<Integer> channels, int precision) {
    this.channels = channels;
    this.precision = precision;
  }

  @Override
  public CountDistinctLongAggregatorFunction aggregator(DriverContext driverContext) {
    return CountDistinctLongAggregatorFunction.create(driverContext, channels, precision);
  }

  @Override
  public CountDistinctLongGroupingAggregatorFunction groupingAggregator(
      DriverContext driverContext) {
    return CountDistinctLongGroupingAggregatorFunction.create(channels, driverContext, precision);
  }

  @Override
  public String describe() {
    return "count_distinct of longs";
  }
}
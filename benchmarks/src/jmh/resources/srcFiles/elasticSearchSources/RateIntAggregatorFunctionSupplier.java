package org.elasticsearch.compute.aggregation;

import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.util.List;
import org.elasticsearch.compute.operator.DriverContext;

/**
 * {@link AggregatorFunctionSupplier} implementation for {@link RateIntAggregator}.
 * This class is generated. Do not edit it.
 */
public final class RateIntAggregatorFunctionSupplier implements AggregatorFunctionSupplier {
  private final List<Integer> channels;

  private final long unitInMillis;

  public RateIntAggregatorFunctionSupplier(List<Integer> channels, long unitInMillis) {
    this.channels = channels;
    this.unitInMillis = unitInMillis;
  }

  @Override
  public AggregatorFunction aggregator(DriverContext driverContext) {
    throw new UnsupportedOperationException("non-grouping aggregator is not supported");
  }

  @Override
  public RateIntGroupingAggregatorFunction groupingAggregator(DriverContext driverContext) {
    return RateIntGroupingAggregatorFunction.create(channels, driverContext, unitInMillis);
  }

  @Override
  public String describe() {
    return "rate of ints";
  }
}
/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.function.scalar.spatial;

import org.apache.lucene.document.ShapeField;
import org.apache.lucene.geo.Component2D;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.geo.Orientation;
import org.elasticsearch.compute.ann.Evaluator;
import org.elasticsearch.compute.ann.Fixed;
import org.elasticsearch.geometry.Geometry;
import org.elasticsearch.index.mapper.GeoShapeIndexer;
import org.elasticsearch.lucene.spatial.CartesianShapeIndexer;
import org.elasticsearch.lucene.spatial.CoordinateEncoder;
import org.elasticsearch.lucene.spatial.GeometryDocValueReader;
import org.elasticsearch.xpack.esql.expression.function.Example;
import org.elasticsearch.xpack.esql.expression.function.FunctionInfo;
import org.elasticsearch.xpack.esql.expression.function.Param;
import org.elasticsearch.xpack.esql.type.EsqlDataTypes;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.FieldAttribute;
import org.elasticsearch.xpack.ql.tree.NodeInfo;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.util.SpatialCoordinateTypes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.xpack.esql.expression.function.scalar.spatial.SpatialRelatesUtils.asGeometryDocValueReader;
import static org.elasticsearch.xpack.esql.expression.function.scalar.spatial.SpatialRelatesUtils.asLuceneComponent2D;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypes.CARTESIAN_POINT;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypes.CARTESIAN_SHAPE;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypes.GEO_POINT;
import static org.elasticsearch.xpack.esql.type.EsqlDataTypes.GEO_SHAPE;

/**
 * This is the primary class for supporting the function ST_DISJOINT.
 * The bulk of the capabilities are within the parent class SpatialRelatesFunction,
 * which supports all the relations in the ShapeField.QueryRelation enum.
 * Here we simply wire the rules together specific to ST_DISJOINT and QueryRelation.DISJOINT.
 */
public class SpatialDisjoint extends SpatialRelatesFunction {
    public static final SpatialRelations GEO = new SpatialRelations(
        ShapeField.QueryRelation.DISJOINT,
        SpatialCoordinateTypes.GEO,
        CoordinateEncoder.GEO,
        new GeoShapeIndexer(Orientation.CCW, "ST_Disjoint")
    );
    public static final SpatialRelations CARTESIAN = new SpatialRelations(
        ShapeField.QueryRelation.DISJOINT,
        SpatialCoordinateTypes.CARTESIAN,
        CoordinateEncoder.CARTESIAN,
        new CartesianShapeIndexer("ST_Disjoint")
    );

    @FunctionInfo(
        returnType = { "boolean" },
        description = "Returns whether the two geometries or geometry columns are disjoint.",
        examples = @Example(file = "spatial_shapes", tag = "st_disjoint-airport_city_boundaries")
    )
    public SpatialDisjoint(
        Source source,
        @Param(
            name = "geomA",
            type = { "geo_point", "cartesian_point", "geo_shape", "cartesian_shape" },
            description = "Geometry column name or variable of geometry type"
        ) Expression left,
        @Param(
            name = "geomB",
            type = { "geo_point", "cartesian_point", "geo_shape", "cartesian_shape" },
            description = "Geometry column name or variable of geometry type"
        ) Expression right
    ) {
        this(source, left, right, false, false);
    }

    private SpatialDisjoint(Source source, Expression left, Expression right, boolean leftDocValues, boolean rightDocValues) {
        super(source, left, right, leftDocValues, rightDocValues);
    }

    @Override
    public ShapeField.QueryRelation queryRelation() {
        return ShapeField.QueryRelation.DISJOINT;
    }

    @Override
    public SpatialDisjoint withDocValues(Set<FieldAttribute> attributes) {
        boolean leftDV = leftDocValues || foundField(left(), attributes);
        boolean rightDV = rightDocValues || foundField(right(), attributes);
        return new SpatialDisjoint(source(), left(), right(), leftDV, rightDV);
    }

    @Override
    protected SpatialDisjoint replaceChildren(Expression newLeft, Expression newRight) {
        return new SpatialDisjoint(source(), newLeft, newRight, leftDocValues, rightDocValues);
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, SpatialDisjoint::new, left(), right());
    }

    @Override
    public Object fold() {
        try {
            GeometryDocValueReader docValueReader = asGeometryDocValueReader(crsType, left());
            Component2D component2D = asLuceneComponent2D(crsType, right());
            return (crsType == SpatialCrsType.GEO)
                ? GEO.geometryRelatesGeometry(docValueReader, component2D)
                : CARTESIAN.geometryRelatesGeometry(docValueReader, component2D);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to fold constant fields: " + e.getMessage(), e);
        }
    }

    @Override
    Map<SpatialEvaluatorFactory.SpatialEvaluatorKey, SpatialEvaluatorFactory<?, ?>> evaluatorRules() {
        return evaluatorMap;
    }

    private static final Map<SpatialEvaluatorFactory.SpatialEvaluatorKey, SpatialEvaluatorFactory<?, ?>> evaluatorMap = new HashMap<>();

    static {
        for (DataType spatialType : new DataType[] { GEO_POINT, GEO_SHAPE }) {
            for (DataType otherType : new DataType[] { GEO_POINT, GEO_SHAPE }) {
                evaluatorMap.put(
                    SpatialEvaluatorFactory.SpatialEvaluatorKey.fromSources(spatialType, otherType),
                    new SpatialEvaluatorFactory.SpatialEvaluatorFactoryWithFields(SpatialDisjointGeoSourceAndSourceEvaluator.Factory::new)
                );
                evaluatorMap.put(
                    SpatialEvaluatorFactory.SpatialEvaluatorKey.fromSourceAndConstant(spatialType, otherType),
                    new SpatialEvaluatorFactory.SpatialEvaluatorWithConstantFactory(
                        SpatialDisjointGeoSourceAndConstantEvaluator.Factory::new
                    )
                );
                if (EsqlDataTypes.isSpatialPoint(spatialType)) {
                    evaluatorMap.put(
                        SpatialEvaluatorFactory.SpatialEvaluatorKey.fromSources(spatialType, otherType).withLeftDocValues(),
                        new SpatialEvaluatorFactory.SpatialEvaluatorFactoryWithFields(
                            SpatialDisjointGeoPointDocValuesAndSourceEvaluator.Factory::new
                        )
                    );
                    evaluatorMap.put(
                        SpatialEvaluatorFactory.SpatialEvaluatorKey.fromSourceAndConstant(spatialType, otherType).withLeftDocValues(),
                        new SpatialEvaluatorFactory.SpatialEvaluatorWithConstantFactory(
                            SpatialDisjointGeoPointDocValuesAndConstantEvaluator.Factory::new
                        )
                    );
                }
            }
        }

        for (DataType spatialType : new DataType[] { CARTESIAN_POINT, CARTESIAN_SHAPE }) {
            for (DataType otherType : new DataType[] { CARTESIAN_POINT, CARTESIAN_SHAPE }) {
                evaluatorMap.put(
                    SpatialEvaluatorFactory.SpatialEvaluatorKey.fromSources(spatialType, otherType),
                    new SpatialEvaluatorFactory.SpatialEvaluatorFactoryWithFields(
                        SpatialDisjointCartesianSourceAndSourceEvaluator.Factory::new
                    )
                );
                evaluatorMap.put(
                    SpatialEvaluatorFactory.SpatialEvaluatorKey.fromSourceAndConstant(spatialType, otherType),
                    new SpatialEvaluatorFactory.SpatialEvaluatorWithConstantFactory(
                        SpatialDisjointCartesianSourceAndConstantEvaluator.Factory::new
                    )
                );
                if (EsqlDataTypes.isSpatialPoint(spatialType)) {
                    evaluatorMap.put(
                        SpatialEvaluatorFactory.SpatialEvaluatorKey.fromSources(spatialType, otherType).withLeftDocValues(),
                        new SpatialEvaluatorFactory.SpatialEvaluatorFactoryWithFields(
                            SpatialDisjointCartesianPointDocValuesAndSourceEvaluator.Factory::new
                        )
                    );
                    evaluatorMap.put(
                        SpatialEvaluatorFactory.SpatialEvaluatorKey.fromSourceAndConstant(spatialType, otherType).withLeftDocValues(),
                        new SpatialEvaluatorFactory.SpatialEvaluatorWithConstantFactory(
                            SpatialDisjointCartesianPointDocValuesAndConstantEvaluator.Factory::new
                        )
                    );
                }
            }
        }
    }

    @Evaluator(extraName = "GeoSourceAndConstant", warnExceptions = { IllegalArgumentException.class, IOException.class })
    static boolean processGeoSourceAndConstant(BytesRef leftValue, @Fixed Component2D rightValue) throws IOException {
        return GEO.geometryRelatesGeometry(leftValue, rightValue);
    }

    @Evaluator(extraName = "GeoSourceAndSource", warnExceptions = { IllegalArgumentException.class, IOException.class })
    static boolean processGeoSourceAndSource(BytesRef leftValue, BytesRef rightValue) throws IOException {
        return GEO.geometryRelatesGeometry(leftValue, rightValue);
    }

    @Evaluator(extraName = "GeoPointDocValuesAndConstant", warnExceptions = { IllegalArgumentException.class })
    static boolean processGeoPointDocValuesAndConstant(long leftValue, @Fixed Component2D rightValue) {
        return GEO.pointRelatesGeometry(leftValue, rightValue);
    }

    @Evaluator(extraName = "GeoPointDocValuesAndSource", warnExceptions = { IllegalArgumentException.class })
    static boolean processGeoPointDocValuesAndSource(long leftValue, BytesRef rightValue) {
        Geometry geometry = SpatialCoordinateTypes.UNSPECIFIED.wkbToGeometry(rightValue);
        return GEO.pointRelatesGeometry(leftValue, geometry);
    }

    @Evaluator(extraName = "CartesianSourceAndConstant", warnExceptions = { IllegalArgumentException.class, IOException.class })
    static boolean processCartesianSourceAndConstant(BytesRef leftValue, @Fixed Component2D rightValue) throws IOException {
        return CARTESIAN.geometryRelatesGeometry(leftValue, rightValue);
    }

    @Evaluator(extraName = "CartesianSourceAndSource", warnExceptions = { IllegalArgumentException.class, IOException.class })
    static boolean processCartesianSourceAndSource(BytesRef leftValue, BytesRef rightValue) throws IOException {
        return CARTESIAN.geometryRelatesGeometry(leftValue, rightValue);
    }

    @Evaluator(extraName = "CartesianPointDocValuesAndConstant", warnExceptions = { IllegalArgumentException.class })
    static boolean processCartesianPointDocValuesAndConstant(long leftValue, @Fixed Component2D rightValue) {
        return CARTESIAN.pointRelatesGeometry(leftValue, rightValue);
    }

    @Evaluator(extraName = "CartesianPointDocValuesAndSource")
    static boolean processCartesianPointDocValuesAndSource(long leftValue, BytesRef rightValue) {
        Geometry geometry = SpatialCoordinateTypes.UNSPECIFIED.wkbToGeometry(rightValue);
        return CARTESIAN.pointRelatesGeometry(leftValue, geometry);
    }
}
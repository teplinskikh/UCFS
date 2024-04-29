/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.expression.predicate.operator.arithmetic;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;

import org.elasticsearch.xpack.esql.expression.function.AbstractFunctionTestCase;
import org.elasticsearch.xpack.esql.expression.function.TestCaseSupplier;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class ModTests extends AbstractFunctionTestCase {
    public ModTests(@Name("TestCase") Supplier<TestCaseSupplier.TestCase> testCaseSupplier) {
        this.testCase = testCaseSupplier.get();
    }

    @ParametersFactory
    public static Iterable<Object[]> parameters() {
        List<TestCaseSupplier> suppliers = new ArrayList<>();
        suppliers.addAll(
            TestCaseSupplier.forBinaryWithWidening(
                new TestCaseSupplier.NumericTypeTestConfigs<Number>(
                    new TestCaseSupplier.NumericTypeTestConfig<>(
                        (Integer.MIN_VALUE >> 1) - 1,
                        (Integer.MAX_VALUE >> 1) - 1,
                        (l, r) -> l.intValue() % r.intValue(),
                        "ModIntsEvaluator"
                    ),
                    new TestCaseSupplier.NumericTypeTestConfig<>(
                        (Long.MIN_VALUE >> 1) - 1,
                        (Long.MAX_VALUE >> 1) - 1,
                        (l, r) -> l.longValue() % r.longValue(),
                        "ModLongsEvaluator"
                    ),
                    new TestCaseSupplier.NumericTypeTestConfig<>(
                        Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY,
                        (l, r) -> l.doubleValue() % r.doubleValue(),
                        "ModDoublesEvaluator"
                    )
                ),
                "lhs",
                "rhs",
                (lhs, rhs) -> List.of(),
                false
            )
        );
        suppliers.addAll(
            TestCaseSupplier.forBinaryNotCasting(
                "ModUnsignedLongsEvaluator",
                "lhs",
                "rhs",
                (l, r) -> (((BigInteger) l).mod((BigInteger) r)),
                DataTypes.UNSIGNED_LONG,
                TestCaseSupplier.ulongCases(BigInteger.ZERO, BigInteger.valueOf(Long.MAX_VALUE), true),
                TestCaseSupplier.ulongCases(BigInteger.ONE, BigInteger.valueOf(Long.MAX_VALUE), true),
                List.of(),
                false
            )
        );

        suppliers = errorsForCasesWithoutExamples(anyNullIsNull(true, suppliers), ModTests::modErrorMessageString);

        TestCaseSupplier.NumericTypeTestConfigs<Number> typeStuff = new TestCaseSupplier.NumericTypeTestConfigs<>(
            new TestCaseSupplier.NumericTypeTestConfig<>(
                (Integer.MIN_VALUE >> 1) - 1,
                (Integer.MAX_VALUE >> 1) - 1,
                (l, r) -> null,
                "ModIntsEvaluator"
            ),
            new TestCaseSupplier.NumericTypeTestConfig<>(
                (Long.MIN_VALUE >> 1) - 1,
                (Long.MAX_VALUE >> 1) - 1,
                (l, r) -> null,
                "ModLongsEvaluator"
            ),
            new TestCaseSupplier.NumericTypeTestConfig<>(
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                (l, r) -> null,
                "ModDoublesEvaluator"
            )
        );
        List<DataType> numericTypes = List.of(DataTypes.INTEGER, DataTypes.LONG, DataTypes.DOUBLE);

        for (DataType lhsType : numericTypes) {
            for (DataType rhsType : numericTypes) {
                DataType expected = TestCaseSupplier.widen(lhsType, rhsType);
                TestCaseSupplier.NumericTypeTestConfig<Number> expectedTypeStuff = typeStuff.get(expected);
                BiFunction<DataType, DataType, String> evaluatorToString = (lhs, rhs) -> expectedTypeStuff.evaluatorName()
                    + "["
                    + "lhs"
                    + "="
                    + TestCaseSupplier.getCastEvaluator("Attribute[channel=0]", lhs, expected)
                    + ", "
                    + "rhs"
                    + "="
                    + TestCaseSupplier.getCastEvaluator("Attribute[channel=1]", rhs, expected)
                    + "]";
                TestCaseSupplier.casesCrossProduct(
                    (l1, r1) -> expectedTypeStuff.expected().apply((Number) l1, (Number) r1),
                    TestCaseSupplier.getSuppliersForNumericType(lhsType, expectedTypeStuff.min(), expectedTypeStuff.max(), true),
                    TestCaseSupplier.getSuppliersForNumericType(rhsType, 0, 0, true),
                    evaluatorToString,
                    (lhs, rhs) -> List.of(
                        "Line -1:-1: evaluation of [] failed, treating result as null. Only first 20 failures recorded.",
                        "Line -1:-1: java.lang.ArithmeticException: / by zero"
                    ),
                    suppliers,
                    expected,
                    false
                );
            }
        }

        suppliers.addAll(
            TestCaseSupplier.forBinaryNotCasting(
                "ModUnsignedLongsEvaluator",
                "lhs",
                "rhs",
                (l, r) -> null,
                DataTypes.UNSIGNED_LONG,
                TestCaseSupplier.ulongCases(BigInteger.ZERO, BigInteger.valueOf(Long.MAX_VALUE), true),
                TestCaseSupplier.ulongCases(BigInteger.ZERO, BigInteger.ZERO, true),
                List.of(
                    "Line -1:-1: evaluation of [] failed, treating result as null. Only first 20 failures recorded.",
                    "Line -1:-1: java.lang.ArithmeticException: / by zero"
                ),
                false
            )
        );

        return parameterSuppliersFromTypedData(suppliers);
    }

    private static String modErrorMessageString(boolean includeOrdinal, List<Set<DataType>> validPerPosition, List<DataType> types) {
        try {
            return typeErrorMessage(includeOrdinal, validPerPosition, types);
        } catch (IllegalStateException e) {
            return "[%] has arguments with incompatible types [" + types.get(0).typeName() + "] and [" + types.get(1).typeName() + "]";

        }
    }

    @Override
    protected Expression build(Source source, List<Expression> args) {
        return new Mod(source, args.get(0), args.get(1));
    }
}
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
import org.elasticsearch.xpack.esql.type.EsqlDataTypes;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.elasticsearch.xpack.esql.expression.predicate.operator.arithmetic.AbstractArithmeticTestCase.arithmeticExceptionOverflowCase;
import static org.elasticsearch.xpack.ql.type.DateUtils.asDateTime;
import static org.elasticsearch.xpack.ql.type.DateUtils.asMillis;
import static org.elasticsearch.xpack.ql.util.NumericUtils.asLongUnsigned;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class AddTests extends AbstractFunctionTestCase {
    public AddTests(@Name("TestCase") Supplier<TestCaseSupplier.TestCase> testCaseSupplier) {
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
                        (l, r) -> l.intValue() + r.intValue(),
                        "AddIntsEvaluator"
                    ),
                    new TestCaseSupplier.NumericTypeTestConfig<>(
                        (Long.MIN_VALUE >> 1) - 1,
                        (Long.MAX_VALUE >> 1) - 1,
                        (l, r) -> l.longValue() + r.longValue(),
                        "AddLongsEvaluator"
                    ),
                    new TestCaseSupplier.NumericTypeTestConfig<>(
                        Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY,
                        (l, r) -> l.doubleValue() + r.doubleValue(),
                        "AddDoublesEvaluator"
                    )
                ),
                "lhs",
                "rhs",
                (lhs, rhs) -> List.of(),
                true
            )
        );

        suppliers.addAll(
            TestCaseSupplier.forBinaryNotCasting(
                "AddUnsignedLongsEvaluator",
                "lhs",
                "rhs",
                (l, r) -> (((BigInteger) l).add((BigInteger) r)),
                DataTypes.UNSIGNED_LONG,
                TestCaseSupplier.ulongCases(BigInteger.ZERO, BigInteger.valueOf(Long.MAX_VALUE), true),
                TestCaseSupplier.ulongCases(BigInteger.ZERO, BigInteger.valueOf(Long.MAX_VALUE), true),
                List.of(),
                true
            )
        );


        suppliers.addAll(
            TestCaseSupplier.forBinaryNotCasting(
                "No evaluator, the tests only trigger the folding code since Period is not representable",
                "lhs",
                "rhs",
                (lhs, rhs) -> ((Period) lhs).plus((Period) rhs),
                EsqlDataTypes.DATE_PERIOD,
                TestCaseSupplier.datePeriodCases(),
                TestCaseSupplier.datePeriodCases(),
                List.of(),
                true
            )
        );
        suppliers.addAll(
            TestCaseSupplier.forBinaryNotCasting(
                "No evaluator, the tests only trigger the folding code since Duration is not representable",
                "lhs",
                "rhs",
                (lhs, rhs) -> ((Duration) lhs).plus((Duration) rhs),
                EsqlDataTypes.TIME_DURATION,
                TestCaseSupplier.timeDurationCases(),
                TestCaseSupplier.timeDurationCases(),
                List.of(),
                true
            )
        );

        suppliers.addAll(
            TestCaseSupplier.forBinaryNotCasting(
                "No evaluator, the tests only trigger the folding code since Period is not representable",
                "lhs",
                "rhs",
                (lhs, rhs) -> {
                    Long date;
                    Period period;
                    if (lhs instanceof Long) {
                        date = (Long) lhs;
                        period = (Period) rhs;
                    } else {
                        date = (Long) rhs;
                        period = (Period) lhs;
                    }
                    return asMillis(asDateTime(date).plus(period));
                },
                DataTypes.DATETIME,
                TestCaseSupplier.dateCases(),
                TestCaseSupplier.datePeriodCases(),
                List.of(),
                true
            )
        );
        suppliers.addAll(
            TestCaseSupplier.forBinaryNotCasting(
                "No evaluator, the tests only trigger the folding code since Duration is not representable",
                "lhs",
                "rhs",
                (lhs, rhs) -> {
                    Long date;
                    Duration duration;
                    if (lhs instanceof Long) {
                        date = (Long) lhs;
                        duration = (Duration) rhs;
                    } else {
                        date = (Long) rhs;
                        duration = (Duration) lhs;
                    }
                    return asMillis(asDateTime(date).plus(duration));
                },
                DataTypes.DATETIME,
                TestCaseSupplier.dateCases(),
                TestCaseSupplier.timeDurationCases(),
                List.of(),
                true
            )
        );
        suppliers.addAll(TestCaseSupplier.dateCases().stream().<TestCaseSupplier>mapMulti((tds, consumer) -> {
            consumer.accept(
                new TestCaseSupplier(
                    List.of(DataTypes.DATETIME, DataTypes.NULL),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(tds.get(), TestCaseSupplier.TypedData.NULL),
                        "LiteralsEvaluator[lit=null]",
                        DataTypes.DATETIME,
                        nullValue()
                    )
                )
            );
            consumer.accept(
                new TestCaseSupplier(
                    List.of(DataTypes.NULL, DataTypes.DATETIME),
                    () -> new TestCaseSupplier.TestCase(
                        List.of(TestCaseSupplier.TypedData.NULL, tds.get()),
                        "LiteralsEvaluator[lit=null]",
                        DataTypes.DATETIME,
                        nullValue()
                    )
                )
            );
        }).toList());

        suppliers = errorsForCasesWithoutExamples(anyNullIsNull(true, suppliers), AddTests::addErrorMessageString);

        suppliers.addAll(List.of(new TestCaseSupplier("MV", () -> {
            int rhs = randomIntBetween((Integer.MIN_VALUE >> 1) - 1, (Integer.MAX_VALUE >> 1) - 1);
            int lhs = randomIntBetween((Integer.MIN_VALUE >> 1) - 1, (Integer.MAX_VALUE >> 1) - 1);
            int lhs2 = randomIntBetween((Integer.MIN_VALUE >> 1) - 1, (Integer.MAX_VALUE >> 1) - 1);
            return new TestCaseSupplier.TestCase(
                List.of(
                    new TestCaseSupplier.TypedData(List.of(lhs, lhs2), DataTypes.INTEGER, "lhs"),
                    new TestCaseSupplier.TypedData(rhs, DataTypes.INTEGER, "rhs")
                ),
                "AddIntsEvaluator[lhs=Attribute[channel=0], rhs=Attribute[channel=1]]",
                DataTypes.INTEGER,
                is(nullValue())
            ).withWarning("Line -1:-1: evaluation of [] failed, treating result as null. Only first 20 failures recorded.")
                .withWarning("Line -1:-1: java.lang.IllegalArgumentException: single-value function encountered multi-value");
        })));
        suppliers.add(
            arithmeticExceptionOverflowCase(
                DataTypes.INTEGER,
                () -> randomIntBetween(1, Integer.MAX_VALUE),
                () -> Integer.MAX_VALUE,
                "AddIntsEvaluator"
            )
        );
        suppliers.add(
            arithmeticExceptionOverflowCase(
                DataTypes.INTEGER,
                () -> randomIntBetween(Integer.MIN_VALUE, -1),
                () -> Integer.MIN_VALUE,
                "AddIntsEvaluator"
            )
        );
        suppliers.add(
            arithmeticExceptionOverflowCase(
                DataTypes.LONG,
                () -> randomLongBetween(1L, Long.MAX_VALUE),
                () -> Long.MAX_VALUE,
                "AddLongsEvaluator"
            )
        );
        suppliers.add(
            arithmeticExceptionOverflowCase(
                DataTypes.LONG,
                () -> randomLongBetween(Long.MIN_VALUE, -1L),
                () -> Long.MIN_VALUE,
                "AddLongsEvaluator"
            )
        );
        suppliers.add(
            arithmeticExceptionOverflowCase(
                DataTypes.UNSIGNED_LONG,
                () -> asLongUnsigned(randomBigInteger()),
                () -> asLongUnsigned(UNSIGNED_LONG_MAX),
                "AddUnsignedLongsEvaluator"
            )
        );

        return parameterSuppliersFromTypedData(suppliers);
    }

    private static String addErrorMessageString(boolean includeOrdinal, List<Set<DataType>> validPerPosition, List<DataType> types) {
        try {
            return typeErrorMessage(includeOrdinal, validPerPosition, types);
        } catch (IllegalStateException e) {
            return "[+] has arguments with incompatible types [" + types.get(0).typeName() + "] and [" + types.get(1).typeName() + "]";

        }
    }

    @Override
    protected Expression build(Source source, List<Expression> args) {
        return new Add(source, args.get(0), args.get(1));
    }
}
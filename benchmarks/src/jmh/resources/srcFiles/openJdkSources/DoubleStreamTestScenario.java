/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.util.stream;

import java.util.Collections;
import java.util.EnumSet;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.SpliteratorTestHelper;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;

/**
 * Test scenarios for double streams.
 *
 * Each scenario is provided with a data source, a function that maps a fresh
 * stream (as provided by the data source) to a new stream, and a sink to
 * receive results.  Each scenario describes a different way of computing the
 * stream contents.  The test driver will ensure that all scenarios produce
 * the same output (modulo allowable differences in ordering).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public enum DoubleStreamTestScenario implements OpTestCase.BaseStreamTestScenario {

    STREAM_FOR_EACH(false) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            DoubleStream s = m.apply(source);
            if (s.isParallel()) {
                s = s.sequential();
            }
            s.forEach(b);
        }
    },

    STREAM_TO_ARRAY(false) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            for (double t : m.apply(source).toArray()) {
                b.accept(t);
            }
        }
    },

    STREAM_ITERATOR(false) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            for (PrimitiveIterator.OfDouble seqIter = m.apply(source).iterator(); seqIter.hasNext(); )
                b.accept(seqIter.nextDouble());
        }
    },

    STREAM_SPLITERATOR(false) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            for (Spliterator.OfDouble spl = m.apply(source).spliterator(); spl.tryAdvance(b); ) {
            }
        }
    },

    STREAM_SPLITERATOR_WITH_MIXED_TRAVERSE_AND_SPLIT(false) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            SpliteratorTestHelper.mixedTraverseAndSplit(b, m.apply(source).spliterator());
        }
    },

    STREAM_SPLITERATOR_FOREACH(false) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            m.apply(source).spliterator().forEachRemaining(b);
        }
    },

    PAR_STREAM_SEQUENTIAL_FOR_EACH(true) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            m.apply(source).sequential().forEach(b);
        }
    },

    PAR_STREAM_FOR_EACH_ORDERED(true) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            m.apply(source).forEachOrdered(b);
        }
    },

    PAR_STREAM_SPLITERATOR(true) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            for (Spliterator.OfDouble spl = m.apply(source).spliterator(); spl.tryAdvance(b); ) {
            }
        }
    },

    PAR_STREAM_SPLITERATOR_FOREACH(true) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            m.apply(source).spliterator().forEachRemaining(b);
        }
    },

    PAR_STREAM_TO_ARRAY(true) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            for (double t : m.apply(source).toArray())
                b.accept(t);
        }
    },

    PAR_STREAM_SPLITERATOR_STREAM_TO_ARRAY(true) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            DoubleStream s = m.apply(source);
            Spliterator.OfDouble sp = s.spliterator();
            DoubleStream ss = StreamSupport.doubleStream(() -> sp,
                                                         StreamOpFlag.toCharacteristics(OpTestCase.getStreamFlags(s))
                                                         | (sp.getExactSizeIfKnown() < 0 ? 0 : Spliterator.SIZED), true);
            for (double t : ss.toArray())
                b.accept(t);
        }
    },

    PAR_STREAM_TO_ARRAY_CLEAR_SIZED(true) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            S_IN pipe1 = (S_IN) OpTestCase.chain(source,
                                                 new FlagDeclaringOp(StreamOpFlag.NOT_SIZED, data.getShape()));
            DoubleStream pipe2 = m.apply(pipe1);

            for (double t : pipe2.toArray())
                b.accept(t);
        }
    },

    PAR_STREAM_FOR_EACH(true, false) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            m.apply(source).forEach(e -> {
                synchronized (data) {
                    b.accept(e);
                }
            });
        }
    },

    PAR_STREAM_FOR_EACH_CLEAR_SIZED(true, false) {
        <T, S_IN extends BaseStream<T, S_IN>>
        void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m) {
            S_IN pipe1 = (S_IN) OpTestCase.chain(source,
                                                 new FlagDeclaringOp(StreamOpFlag.NOT_SIZED, data.getShape()));
            m.apply(pipe1).forEach(e -> {
                synchronized (data) {
                    b.accept(e);
                }
            });
        }
    },
    ;

    public static final Set<DoubleStreamTestScenario> CLEAR_SIZED_SCENARIOS = Collections.unmodifiableSet(
            EnumSet.of(PAR_STREAM_TO_ARRAY_CLEAR_SIZED, PAR_STREAM_FOR_EACH_CLEAR_SIZED));

    private boolean isParallel;

    private final boolean isOrdered;

    DoubleStreamTestScenario(boolean isParallel) {
        this(isParallel, true);
    }

    DoubleStreamTestScenario(boolean isParallel, boolean isOrdered) {
        this.isParallel = isParallel;
        this.isOrdered = isOrdered;
    }

    public StreamShape getShape() {
        return StreamShape.DOUBLE_VALUE;
    }

    public boolean isParallel() {
        return isParallel;
    }

    public boolean isOrdered() {
        return isOrdered;
    }

    public <T, U, S_IN extends BaseStream<T, S_IN>, S_OUT extends BaseStream<U, S_OUT>>
    void run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, S_OUT> m) {
        try (S_IN source = getStream(data)) {
            run(data, source, (DoubleConsumer) b, (Function<S_IN, DoubleStream>) m);
        }
    }

    abstract <T, S_IN extends BaseStream<T, S_IN>>
    void run(TestData<T, S_IN> data, S_IN source, DoubleConsumer b, Function<S_IN, DoubleStream> m);

}
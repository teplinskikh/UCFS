/*
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http:
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.rxjava3.tck;

import org.reactivestreams.Publisher;
import org.testng.annotations.Test;

import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.functions.BiFunction;
import io.reactivex.rxjava3.internal.functions.Functions;

@Test
public class GenerateTckTest extends BaseTck<Long> {

    @Override
    public Publisher<Long> createPublisher(final long elements) {
        return
            Flowable.generate(Functions.justSupplier(0L),
            new BiFunction<Long, Emitter<Long>, Long>() {
                @Override
                public Long apply(Long s, Emitter<Long> e) throws Exception {
                    e.onNext(s);
                    if (++s == elements) {
                        e.onComplete();
                    }
                    return s;
                }
            }, Functions.<Long>emptyConsumer())
        ;
    }
}
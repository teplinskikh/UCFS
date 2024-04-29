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

import java.util.concurrent.*;

import org.reactivestreams.Publisher;
import org.testng.annotations.Test;

import io.reactivex.rxjava3.core.Flowable;

@Test
public class FromFutureTckTest extends BaseTck<Long> {

    @Override
    public Publisher<Long> createPublisher(final long elements) {
        FutureTask<Long> ft = new FutureTask<>(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return 1L;
            }
        });

        ft.run();
        return Flowable.fromFuture(ft);
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1;
    }
}
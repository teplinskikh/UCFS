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

package io.reactivex.rxjava3.internal.operators.maybe;

import org.junit.Test;

import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.exceptions.TestException;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.testsupport.TestHelper;

public class MaybeConcatMapCompletableTest extends RxJavaTest {

    @Test
    public void dispose() {
        TestHelper.checkDisposed(Maybe.just(1).concatMapCompletable(new Function<Integer, Completable>() {
            @Override
            public Completable apply(Integer v) throws Exception {
                return Completable.complete();
            }
        }));
    }

    @Test
    public void mapperThrows() {
        Maybe.just(1)
        .concatMapCompletable(new Function<Integer, Completable>() {
            @Override
            public Completable apply(Integer v) throws Exception {
                throw new TestException();
            }
        })
        .test()
        .assertFailure(TestException.class);
    }

    @Test
    public void mapperReturnsNull() {
        Maybe.just(1)
        .concatMapCompletable(new Function<Integer, Completable>() {
            @Override
            public Completable apply(Integer v) throws Exception {
                return null;
            }
        })
        .test()
        .assertFailure(NullPointerException.class);
    }
}
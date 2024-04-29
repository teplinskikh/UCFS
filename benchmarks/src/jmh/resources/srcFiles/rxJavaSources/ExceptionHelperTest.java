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

package io.reactivex.rxjava3.internal.util;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import io.reactivex.rxjava3.core.RxJavaTest;
import io.reactivex.rxjava3.exceptions.TestException;
import io.reactivex.rxjava3.testsupport.TestHelper;

public class ExceptionHelperTest extends RxJavaTest {
    @Test
    public void utilityClass() {
        TestHelper.checkUtilityClass(ExceptionHelper.class);
    }

    @Test
    public void addRace() {
        for (int i = 0; i < TestHelper.RACE_DEFAULT_LOOPS; i++) {

            final AtomicReference<Throwable> error = new AtomicReference<>();

            final TestException ex = new TestException();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    assertTrue(ExceptionHelper.addThrowable(error, ex));
                }
            };

            TestHelper.race(r, r);
        }
    }

    @Test(expected = InternalError.class)
    public void throwIfThrowable() throws Exception {
        ExceptionHelper.<Exception>throwIfThrowable(new InternalError());
    }
}
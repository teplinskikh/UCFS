/*
 * Copyright (C) 2012 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.collect.testing.google;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.Multimap;
import com.google.common.collect.testing.SampleElements;
import com.google.common.collect.testing.TestContainerGenerator;
import java.util.Collection;
import java.util.Map.Entry;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Creates multimaps, containing sample elements, to be tested.
 *
 * @author Louis Wasserman
 */
@GwtCompatible
@ElementTypesAreNonnullByDefault
public interface TestMultimapGenerator<
        K extends @Nullable Object, V extends @Nullable Object, M extends Multimap<K, V>>
    extends TestContainerGenerator<M, Entry<K, V>> {

  K[] createKeyArray(int length);

  V[] createValueArray(int length);

  SampleElements<K> sampleKeys();

  SampleElements<V> sampleValues();

  Collection<V> createCollection(Iterable<? extends V> values);
}
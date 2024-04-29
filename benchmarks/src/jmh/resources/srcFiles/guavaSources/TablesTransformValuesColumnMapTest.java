/*
 * Copyright (C) 2008 The Guava Authors
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

package com.google.common.collect;

import static com.google.common.collect.TableCollectionTest.FIRST_CHARACTER;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.TableCollectionTest.ColumnMapTests;
import java.util.Map;

@GwtCompatible
@ElementTypesAreNonnullByDefault
public class TablesTransformValuesColumnMapTest extends ColumnMapTests {
  public TablesTransformValuesColumnMapTest() {
    super(false, true, true, false);
  }

  @Override
  Table<Integer, String, Character> makeTable() {
    Table<Integer, String, String> original = HashBasedTable.create();
    return Tables.transformValues(original, FIRST_CHARACTER);
  }

  @Override
  protected Map<String, Map<Integer, Character>> makePopulatedMap() {
    Table<Integer, String, String> table = HashBasedTable.create();
    table.put(1, "foo", "apple");
    table.put(1, "bar", "banana");
    table.put(3, "foo", "cat");
    return Tables.transformValues(table, FIRST_CHARACTER).columnMap();
  }
}
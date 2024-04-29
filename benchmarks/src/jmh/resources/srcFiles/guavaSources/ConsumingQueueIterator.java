/*
 * Copyright (C) 2015 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http:
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.collect;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtCompatible;
import java.util.Queue;
import javax.annotation.CheckForNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An Iterator implementation which draws elements from a queue, removing them from the queue as it
 * iterates. This class is not thread safe.
 */
@GwtCompatible
@ElementTypesAreNonnullByDefault
final class ConsumingQueueIterator<T extends @Nullable Object> extends AbstractIterator<T> {
  private final Queue<T> queue;

  ConsumingQueueIterator(Queue<T> queue) {
    this.queue = checkNotNull(queue);
  }

  @Override
  @CheckForNull
  protected T computeNext() {
    if (queue.isEmpty()) {
      return endOfData();
    }
    return queue.remove();
  }
}
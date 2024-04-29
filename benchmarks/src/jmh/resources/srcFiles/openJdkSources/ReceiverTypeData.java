/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.oops;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class ReceiverTypeData<K,M> extends CounterData {
  static final int nonProfiledCountOffset = counterCellCount;
  static final int receiver0Offset;
  static final int count0Offset;
  static final int receiverTypeRowCellCount;
  static {
    receiver0Offset = counterCellCount;
    count0Offset = receiver0Offset + 1;
    receiverTypeRowCellCount = (count0Offset + 1) - receiver0Offset;
  }
  final MethodDataInterface<K,M> methodData;

  public ReceiverTypeData(MethodDataInterface<K,M> methodData, DataLayout layout) {
    super(layout);
    this.methodData = methodData;
  }

  boolean isReceivertypedata() { return true; }

  static int staticCellCount() {
    int cellCount = counterCellCount + MethodData.TypeProfileWidth * receiverTypeRowCellCount;
    return cellCount;
  }

  public int cellCount() {
    return staticCellCount();
  }

  public static int rowLimit() {
    return MethodData.TypeProfileWidth;
  }
  public static int receiverCellIndex(int row) {
    return receiver0Offset + row * receiverTypeRowCellCount;
  }
  public static int receiverCountCellIndex(int row) {
    return count0Offset + row * receiverTypeRowCellCount;
  }

  K receiverUnchecked(int row) {
    Address recv = addressAt(receiverCellIndex(row));
    return methodData.getKlassAtAddress(recv);
  }

  public K receiver(int row) {
    K recv = receiverUnchecked(row);
    return recv;
  }

  public int receiverCount(int row) {
    return uintAt(receiverCountCellIndex(row));
  }

  static int receiverOffset(int row) {
    return cellOffset(receiverCellIndex(row));
  }
  static int receiverCountOffset(int row) {
    return cellOffset(receiverCountCellIndex(row));
  }
  static int receiverTypeDataSize() {
    return cellOffset(staticCellCount());
  }

  void printReceiverDataOn(PrintStream st) {
    int row;
    int entries = 0;
    for (row = 0; row < rowLimit(); row++) {
      if (receiver(row) != null)  entries++;
    }
    st.println("count(" + count() + ") entries(" + entries + ")");
    for (row = 0; row < rowLimit(); row++) {
      if (receiver(row) != null) {
        tab(st);
        methodData.printKlassValueOn(receiver(row), st);
        st.println("(" + receiverCount(row) + ")");
      }
    }
  }
  public void printDataOn(PrintStream st) {
    printShared(st, "ReceiverTypeData");
    printReceiverDataOn(st);
  }
}
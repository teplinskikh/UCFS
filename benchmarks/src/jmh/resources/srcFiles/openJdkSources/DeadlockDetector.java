/*
 * Copyright (c) 2004, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.oops.*;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;

/** Prints information about Java-level deadlocks in supplied 'tty'. */

public class DeadlockDetector {

    public static void print(PrintStream tty) {
        print(tty, true);
    }

    /** prints zero or more deadlocks into 'tty' taking current
     snapshot of Java threads and locks */
    public static void print(PrintStream tty, boolean concurrentLocks) {
        tty.println("Deadlock Detection:");
        tty.println();

        int globalDfn = 0, thisDfn;
        int numberOfDeadlocks = 0;
        JavaThread currentThread = null, previousThread = null;
        ObjectMonitor waitingToLockMonitor = null;
        Oop waitingToLockBlocker = null;

        threads = VM.getVM().getThreads();
        heap = VM.getVM().getObjectHeap();
        createThreadTable();

        for (Entry<JavaThread, Integer> e : threadTable.entrySet()) {
            if (e.getValue() >= 0) {
                continue;
            }

            thisDfn = globalDfn;
            JavaThread thread = e.getKey();
            previousThread = thread;

            try {
                waitingToLockMonitor = thread.getCurrentPendingMonitor();
            } catch (RuntimeException re) {
                tty.println("This version of HotSpot VM doesn't support deadlock detection.");
                return;
            }

            Klass abstractOwnableSyncKlass = null;
            if (concurrentLocks) {
                waitingToLockBlocker = thread.getCurrentParkBlocker();
                SystemDictionary sysDict = VM.getVM().getSystemDictionary();
                abstractOwnableSyncKlass = sysDict.getAbstractOwnableSynchronizerKlass();
            }

            while (waitingToLockMonitor != null ||
                   waitingToLockBlocker != null) {
                if (waitingToLockMonitor != null) {
                    currentThread = threads.owningThreadFromMonitor(waitingToLockMonitor);
                } else {
                    if (concurrentLocks) {
                        if (waitingToLockBlocker.isA(abstractOwnableSyncKlass)) {
                            Oop threadOop = OopUtilities.abstractOwnableSynchronizerGetOwnerThread(waitingToLockBlocker);
                            if (threadOop != null) {
                                currentThread = OopUtilities.threadOopGetJavaThread(threadOop);
                            }
                        }
                    }
                }
                if (currentThread == null) {
                    break;
                }
                if (dfn(currentThread) < 0) {
                    threadTable.put(currentThread, globalDfn++);
                } else if (dfn(currentThread) < thisDfn) {
                    break;
                } else if (currentThread == previousThread) {
                    break;
                } else {
                    numberOfDeadlocks ++;
                    printOneDeadlock(tty, currentThread, concurrentLocks);
                    break;
                }
                previousThread = currentThread;
                waitingToLockMonitor = currentThread.getCurrentPendingMonitor();
                if (concurrentLocks) {
                    waitingToLockBlocker = currentThread.getCurrentParkBlocker();
                }
            }
        }

        switch (numberOfDeadlocks) {
            case 0:
                tty.println("No deadlocks found.");
                break;
            case 1:
                tty.println("Found a total of 1 deadlock.");
                break;
            default:
                tty.println("Found a total of " + numberOfDeadlocks + " deadlocks.");
                break;
        }
        tty.println();
    }

    private static Threads threads;
    private static ObjectHeap heap;
    private static HashMap<JavaThread, Integer> threadTable;

    private static void createThreadTable() {
        threadTable = new HashMap<>();
        Threads threads = VM.getVM().getThreads();
        for (int i = 0; i < threads.getNumberOfThreads(); i++) {
            JavaThread cur = threads.getJavaThreadAt(i);
            threadTable.put(cur, -1);
        }
    }

    private static int dfn(JavaThread thread) {
        Object obj = threadTable.get(thread);
        if (obj != null) {
            return ((Integer)obj).intValue();
        }
        return -1;
    }

    private static void printOneDeadlock(PrintStream tty, JavaThread thread,
                                         boolean concurrentLocks) {
        tty.println("Found one Java-level deadlock:");
        tty.println("=============================");
        ObjectMonitor waitingToLockMonitor = null;
        Oop waitingToLockBlocker = null;
        JavaThread currentThread = thread;
        do {
            tty.println();
            tty.println("\"" + currentThread.getThreadName() + "\":");
            waitingToLockMonitor = currentThread.getCurrentPendingMonitor();
            if (waitingToLockMonitor != null) {
                tty.print("  waiting to lock Monitor@" + waitingToLockMonitor.getAddress());
                OopHandle obj = waitingToLockMonitor.object();
                Oop oop = heap.newOop(obj);
                if (obj != null) {
                    tty.print(" (Object@");
                    Oop.printOopAddressOn(oop, tty);
                    tty.print(", a " + oop.getKlass().getName().asString() + ")" );
                    tty.print(",\n  which is held by");
                } else {
                    tty.print(" (raw monitor),\n  which is held by");
                }
                currentThread = threads.owningThreadFromMonitor(waitingToLockMonitor);
                tty.print(" \"" + currentThread.getThreadName() + "\"");
            } else if (concurrentLocks) {
                waitingToLockBlocker = currentThread.getCurrentParkBlocker();
                tty.print(" waiting for ownable synchronizer ");
                Oop.printOopAddressOn(waitingToLockBlocker, tty);
                tty.print(", (a " + waitingToLockBlocker.getKlass().getName().asString() + ")" );
                Oop threadOop = OopUtilities.abstractOwnableSynchronizerGetOwnerThread(waitingToLockBlocker);
                currentThread = OopUtilities.threadOopGetJavaThread(threadOop);
                tty.print(",\n which is held by");
                tty.print(" \"" + currentThread.getThreadName() + "\"");
            }
        } while (!currentThread.equals(thread));
        tty.println();
        tty.println();
    }
}
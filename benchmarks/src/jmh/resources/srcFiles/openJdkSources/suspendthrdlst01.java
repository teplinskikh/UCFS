/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 *
 * @summary converted from VM Testbase nsk/jvmti/SuspendThreadList/suspendthrdlst001.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     This JVMTI test exercises JVMTI thread function SuspendThreadList().
 *     This tests checks that for each thread suspended by SuspendThreadList()
 *     function GetThreadState() returns JVMTI_THREAD_STATE_SUSPENDED.
 * COMMENTS
 *     Modified due to fix of the RFE
 *     5001769 TEST_RFE: remove usage of deprecated GetThreadStatus function
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:suspendthrdlst01=-waittime=5,threads=10 suspendthrdlst01
 */

import jdk.test.lib.jvmti.DebugeeClass;


public class suspendthrdlst01 extends DebugeeClass {

    static {
        System.loadLibrary("suspendthrdlst01");
    }

    public static void main(String argv[]) {
        int result = new suspendthrdlst01().runIt();
        if (result != 0) {
            throw new RuntimeException("check failed with result " + result);
        }
    }

    /* =================================================================== */
    long timeout = 0;
    int status = DebugeeClass.TEST_PASSED;

    public static final int THREADS_COUNT = 10;

    suspendthrdlst01Thread threads[] = null;

    public int runIt() {
        timeout = 60 * 1000; 

        threads = new suspendthrdlst01Thread[THREADS_COUNT];
        for (int i = 0; i < THREADS_COUNT; i++) {
            threads[i] = new suspendthrdlst01Thread("TestedThread #" + i);
        }

        System.out.println("Staring tested threads");
        try {
            for (int i = 0; i < THREADS_COUNT; i++) {
                threads[i].start();
                if (!threads[i].checkReady()) {
                    throw new RuntimeException("Unable to prepare tested thread: " + threads[i]);
                }
            }

            System.out.println("Sync: thread started");
            status = checkStatus(status);
        } finally {
            for (int i = 0; i < THREADS_COUNT; i++) {
                threads[i].letFinish();
            }
        }

        System.out.println("Finishing tested threads");
        try {
            for (int i = 0; i < THREADS_COUNT; i++) {
                threads[i].join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Sync: thread finished");
        status = checkStatus(status);

        return status;
    }
}

/* =================================================================== */

class suspendthrdlst01Thread extends Thread {
    private volatile boolean threadReady = false;
    private volatile boolean shouldFinish = false;

    public suspendthrdlst01Thread(String name) {
        super(name);
    }

    public void run() {
        threadReady = true;
        int i = 0;
        int n = 1000;
        while (!shouldFinish) {
            if (n <= 0) {
                n = 1000;
            }
            if (i > n) {
                i = 0;
                n = n - 1;
            }
            i = i + 1;
        }
    }

    public boolean checkReady() {
        try {
            while (!threadReady) {
                sleep(1000);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interruption while preparing tested thread: \n\t" + e);
        }
        return threadReady;
    }

    public void letFinish() {
        shouldFinish = true;
    }
}
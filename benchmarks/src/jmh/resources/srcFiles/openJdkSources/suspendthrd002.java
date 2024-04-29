/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jvmti.SuspendThread;

import java.io.PrintStream;

import nsk.share.*;
import nsk.share.jvmti.*;

public class suspendthrd002 extends DebugeeClass {

    static {
        System.loadLibrary("suspendthrd002");
    }

    public static void main(String argv[]) {
        argv = nsk.share.jvmti.JVMTITest.commonInit(argv);

        System.exit(run(argv, System.out) + Consts.JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream out) {
        return new suspendthrd002().runIt(argv, out);
    }

    /* =================================================================== */

    ArgumentHandler argHandler = null;
    Log log = null;
    long timeout = 0;
    int status = Consts.TEST_PASSED;

    suspendthrd002Thread thread = null;

    public int runIt(String argv[], PrintStream out) {
        argHandler = new ArgumentHandler(argv);
        log = new Log(out, argHandler);
        timeout = argHandler.getWaitTime() * 60 * 1000; 

        thread = new suspendthrd002Thread("TestedThread");

        log.display("Staring tested thread");
        try {
            thread.start();
            if (!thread.checkReady()) {
                throw new Failure("Unable to prepare tested thread: " + thread);
            }

            log.display("Sync: thread started");
            status = checkStatus(status);
        } finally {
            thread.letFinish();
        }

        log.display("Finishing tested thread");
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new Failure(e);
        }

        log.display("Sync: thread finished");
        status = checkStatus(status);

        return status;
    }
}

/* =================================================================== */

class suspendthrd002Thread extends Thread {
    private volatile boolean threadReady = false;
    private volatile boolean shouldFinish = false;

    public suspendthrd002Thread(String name) {
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
            Thread.yield();
        }
    }

    public boolean checkReady() {
        try {
            while (!threadReady) {
                sleep(1000);
            }
        } catch (InterruptedException e) {
            throw new Failure("Interruption while preparing tested thread: \n\t" + e);
        }
        return threadReady;
    }

    public void letFinish() {
        shouldFinish = true;
    }
}
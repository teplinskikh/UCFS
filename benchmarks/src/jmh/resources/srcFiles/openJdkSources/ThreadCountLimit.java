/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Stress test that reaches the process limit for thread count, or time limit.
 * @requires os.family != "aix"
 * @key stress
 * @library /test/lib
 * @run main/othervm -Xmx1g ThreadCountLimit
 */

/**
 * @test
 * @summary Stress test that reaches the process limit for thread count, or time limit.
 * @requires os.family == "aix"
 * @key stress
 * @library /test/lib
 * @run main/othervm -Xmx1g -XX:MaxExpectedDataSegmentSize=16g ThreadCountLimit
 */

import java.util.concurrent.CountDownLatch;
import java.util.ArrayList;

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ThreadCountLimit {

  static final int TIME_LIMIT_MS = 5000; 

  static class Worker extends Thread {
    private final CountDownLatch startSignal;

    Worker(CountDownLatch startSignal) {
      this.startSignal = startSignal;
    }

    @Override
    public void run() {
      try {
        startSignal.await();
      } catch (InterruptedException e) {
        throw new Error("Unexpected", e);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      if (Platform.isLinux()) {

        final String ULIMIT_CMD = "ulimit -u 4096";
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(ThreadCountLimit.class.getName());
        String javaCmd = ProcessTools.getCommandLine(pb);
        ProcessTools.executeCommand("bash", "-c", ULIMIT_CMD + " && " + javaCmd + " dummy")
                    .shouldHaveExitValue(0);
      } else {
        test();
      }
    } else {
      test();
    }
  }

  static void test() {
    CountDownLatch startSignal = new CountDownLatch(1);
    ArrayList<Worker> workers = new ArrayList<Worker>();

    boolean reachedNativeOOM = false;


    int count = 0;
    long start = System.currentTimeMillis();
    try {
      while (true) {
        Worker w = new Worker(startSignal);
        w.start();
        workers.add(w);
        count++;

        long end = System.currentTimeMillis();
        if ((end - start) > TIME_LIMIT_MS) {
          break;
        }
      }
    } catch (OutOfMemoryError e) {
      if (e.getMessage().contains("unable to create native thread")) {
        reachedNativeOOM = true;
      } else {
        throw e;
      }
    }

    startSignal.countDown();

    try {
      for (Worker w : workers) {
        w.join();
      }
    } catch (InterruptedException e) {
      throw new Error("Unexpected", e);
    }

    if (reachedNativeOOM) {
      System.out.println("INFO: reached this process thread count limit with " +
                         count + " threads created");
    } else {
      System.out.println("INFO: reached the time limit " + TIME_LIMIT_MS +
                         " ms, with " + count + " threads created");
    }
  }
}
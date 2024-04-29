/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat Inc.
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
 * @test id=ENABLED
 * @bug 8303215 8312182
 * @summary On THP=always systems, we prevent THPs from forming within thread stacks
 * @library /test/lib
 * @requires vm.flagless
 * @requires os.family == "linux"
 * @requires vm.debug
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="aarch64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver THPsInThreadStackPreventionTest PATCH-ENABLED
 */

/*
 * @test id=DISABLED
 * @bug 8303215 8312182
 * @summary On THP=always systems, we prevent THPs from forming within thread stacks (negative test)
 * @library /test/lib
 * @requires vm.flagless
 * @requires os.family == "linux"
 * @requires vm.debug
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="aarch64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/manual THPsInThreadStackPreventionTest  PATCH-DISABLED
 */
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class THPsInThreadStackPreventionTest {


    static final int numThreads = 1000;
    static final long threadStackSizeMB = 2; 
    static final long heapSizeMB = 64;
    static final long basicRSSOverheadMB = heapSizeMB + 150;
    static final long acceptableRSSPerThreadStack = 128 * 1024;
    static final long acceptableRSSForAllThreadStacks = numThreads * acceptableRSSPerThreadStack;
    static final long acceptableRSSLimitMB = (acceptableRSSForAllThreadStacks / (1024 * 1024)) + basicRSSOverheadMB;

    private static class TestMain {

        static class Sleeper extends Thread {
            CyclicBarrier barrier;
            public Sleeper(CyclicBarrier barrier) {
                this.barrier = barrier;
            }
            @Override
            public void run() {
                try {
                    barrier.await(); 
                    barrier.await(); 
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }
        }

        public static void main(String[] args) throws BrokenBarrierException, InterruptedException {

            Sleeper[] threads = new Sleeper[numThreads];
            CyclicBarrier barrier = new CyclicBarrier(numThreads + 1);

            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Sleeper(barrier);
                threads[i].start();
            }

            barrier.await();

            String file = "/proc/self/status";
            try (FileReader fr = new FileReader(file);
                 BufferedReader reader = new BufferedReader(fr)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException | NumberFormatException e) { /* ignored */ }

            barrier.await();

        }
    }

    static class ProcSelfStatus {

        public long rssMB;
        public long swapMB;
        public int numLifeThreads;

        public static ProcSelfStatus parse(OutputAnalyzer o) {
            ProcSelfStatus status = new ProcSelfStatus();
            String s = o.firstMatch("Threads:\\s*(\\d+)", 1);
            Objects.requireNonNull(s);
            status.numLifeThreads = Integer.parseInt(s);
            s = o.firstMatch("VmRSS:\\s*(\\d+) kB", 1);
            Objects.requireNonNull(s);
            status.rssMB = Long.parseLong(s) / 1024;
            s = o.firstMatch("VmSwap:\\s*(\\d+) kB", 1);
            Objects.requireNonNull(s);
            status.swapMB = Long.parseLong(s) / 1024;
            return status;
        }
    }

    public static void main(String[] args) throws Exception {

        HugePageConfiguration config = HugePageConfiguration.readFromOS();
        if (config.getThpMode() != HugePageConfiguration.THPMode.always) {
            throw new SkippedException("Test only makes sense in THP \"always\" mode");
        }

        String[] defaultArgs = {
            "-Xlog:pagesize",
            "-Xmx" + heapSizeMB + "m", "-Xms" + heapSizeMB + "m", "-XX:+AlwaysPreTouch", 
            "-Xss" + threadStackSizeMB + "m",
            "-XX:-CreateCoredumpOnCrash",
            "-XX:ActiveProcessorCount=2",
            "-XX:+DelayThreadStartALot"
        };
        ArrayList<String> finalargs = new ArrayList<>(Arrays.asList(defaultArgs));

        switch (args[0]) {
            case "PATCH-ENABLED": {
                finalargs.add(TestMain.class.getName());
                ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(finalargs);

                OutputAnalyzer output = new OutputAnalyzer(pb.start());
                output.shouldHaveExitValue(0);

                output.shouldContain("[pagesize] JVM will attempt to prevent THPs in thread stacks.");

                ProcSelfStatus status = ProcSelfStatus.parse(output);
                if (status.numLifeThreads < numThreads) {
                    throw new RuntimeException("Number of live threads lower than expected: " + status.numLifeThreads + ", expected " + numThreads);
                } else {
                    System.out.println("Found " + status.numLifeThreads + " to be alive. Ok.");
                }

                long rssPlusSwapMB = status.swapMB + status.rssMB;

                if (rssPlusSwapMB > acceptableRSSLimitMB) {
                    throw new RuntimeException("RSS+Swap larger than expected: " + rssPlusSwapMB + "m, expected at most " + acceptableRSSLimitMB + "m");
                } else {
                    if (rssPlusSwapMB < heapSizeMB) { 
                        throw new RuntimeException("RSS+Swap suspiciously low: " + rssPlusSwapMB + "m, expected at least " + heapSizeMB + "m");
                    }
                    System.out.println("Okay: RSS+Swap=" + rssPlusSwapMB + ", within acceptable limit of " + acceptableRSSLimitMB);
                }
            }
            break;

            case "PATCH-DISABLED": {


                finalargs.add("-XX:+UnlockDiagnosticVMOptions");
                finalargs.add("-XX:-THPStackMitigation");

                finalargs.add(TestMain.class.getName());
                ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(finalargs);
                OutputAnalyzer output = new OutputAnalyzer(pb.start());

                output.shouldHaveExitValue(0);

                output.shouldContain("[pagesize] JVM will *not* prevent THPs in thread stacks. This may cause high RSS.");

                ProcSelfStatus status = ProcSelfStatus.parse(output);
                if (status.numLifeThreads < numThreads) {
                    throw new RuntimeException("Number of live threads lower than expected (" + status.numLifeThreads + ", expected " + numThreads +")");
                } else {
                    System.out.println("Found " + status.numLifeThreads + " to be alive. Ok.");
                }

                long rssPlusSwapMB = status.swapMB + status.rssMB;

                if (rssPlusSwapMB < acceptableRSSLimitMB) {
                    throw new RuntimeException("RSS+Swap lower than expected: " + rssPlusSwapMB + "m, expected more than " + acceptableRSSLimitMB + "m");
                }
                break;
            }

            default: throw new RuntimeException("Bad argument: " + args[0]);
        }
    }
}
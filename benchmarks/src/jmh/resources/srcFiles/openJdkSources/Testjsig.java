/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test Testjsig.java
 * @bug 8017498
 * @bug 8020791
 * @bug 8021296
 * @bug 8022301
 * @bug 8025519
 * @summary sigaction(sig) results in process hang/timed-out if sig is much greater than SIGRTMAX
 * @requires os.family != "windows"
 * @library /test/lib
 * @compile TestJNI.java
 * @run driver Testjsig
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class Testjsig {

    public static void main(String[] args) throws Throwable {

        String libpath = System.getProperty("java.library.path");

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Djava.library.path=" + libpath + ":.",
            "TestJNI",
            "100");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldContain("old handler");
    }
}
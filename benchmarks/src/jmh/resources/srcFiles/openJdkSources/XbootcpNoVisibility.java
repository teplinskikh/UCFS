/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Ensure that a class defined within a java.base package can not
 *          be located via -Xbootclasspath/a
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver XbootcpNoVisibility
 */

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class XbootcpNoVisibility {
    public static void main(String args[]) throws Exception {

        String Vis3_B_src =
                "package sun.util;" +
                "public class Vis3_B { public void m() { System.out.println(\"In B's m()\"); } }";

        ClassFileInstaller.writeClassToDisk("sun/util/Vis3_B",
            InMemoryJavaCompiler.compile("sun.util.Vis3_B", Vis3_B_src), System.getProperty("test.classes"));

        String Vis3_A_src =
                "import sun.util.*;" +
                "public class Vis3_A {" +
                "    public static void main(String args[]) throws Exception {" +
                "       try {" +
                "               sun.util.Vis3_B b = new sun.util.Vis3_B();" +
                "       } catch (NoClassDefFoundError e) {" +
                "               System.out.println(\"XbootcpNoVisibility PASSED - " +
                                                "test should throw exception\\n\");" +
                "               return;" +
                "       }" +
                "       throw new RuntimeException(\"XbootcpNoVisibility FAILED - " +
                                                    "test should have thrown exception\");" +
                "    }" +
                "}";

        ClassFileInstaller.writeClassToDisk("Vis3_A",
                InMemoryJavaCompiler.compile("Vis3_A", Vis3_A_src), System.getProperty("test.classes"));

        new OutputAnalyzer(ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xbootclasspath/a:.",
                "Vis3_A")
            .start())
            .shouldHaveExitValue(0)
            .shouldContain("XbootcpNoVisibility PASSED");
    }
}
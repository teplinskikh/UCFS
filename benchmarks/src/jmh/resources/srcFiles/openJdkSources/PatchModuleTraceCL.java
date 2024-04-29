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
 * @test
 * @bug 8069469
 * @summary Make sure -Xlog:class+load=info works properly with "modules" jimage,
            --patch-module, and with -Xbootclasspath/a
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @compile PatchModuleMain.java
 * @run driver PatchModuleTraceCL
 */

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.ProcessTools;

public class PatchModuleTraceCL {

    public static void main(String[] args) throws Exception {
        String source = "package javax.naming.spi; "                +
                        "public class NamingManager { "             +
                        "    static { "                             +
                        "        System.out.println(\"I pass!\"); " +
                        "    } "                                    +
                        "}";

        ClassFileInstaller.writeClassToDisk("javax/naming/spi/NamingManager",
             InMemoryJavaCompiler.compile("javax.naming.spi.NamingManager", source, "--patch-module=java.naming"),
             "mods/java.naming");

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("--patch-module=java.naming=mods/java.naming",
             "-Xlog:class+load=info", "PatchModuleMain", "javax.naming.spi.NamingManager");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldContain("[class,load] java.lang.Thread source: jrt:/java.base");
        output.shouldContain("[class,load] javax.naming.spi.NamingManager source: mods/java.naming");
        output.shouldContain("[class,load] PatchModuleMain source: file");

        source = "package PatchModuleTraceCL_pkg; "                 +
                 "public class ItIsI { "                          +
                 "    static { "                                  +
                 "        System.out.println(\"I also pass!\"); " +
                 "    } "                                         +
                 "}";

        ClassFileInstaller.writeClassToDisk("PatchModuleTraceCL_pkg/ItIsI",
             InMemoryJavaCompiler.compile("PatchModuleTraceCL_pkg.ItIsI", source),
             "xbcp");

        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xbootclasspath/a:xbcp",
             "-Xlog:class+load=info", "PatchModuleMain", "PatchModuleTraceCL_pkg.ItIsI");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("[class,load] PatchModuleTraceCL_pkg.ItIsI source: xbcp");
        output.shouldHaveExitValue(0);
    }
}
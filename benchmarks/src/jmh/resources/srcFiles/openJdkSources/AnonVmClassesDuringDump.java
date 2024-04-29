/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary When dumping the CDS archive, try to load VM anonymous classes to make sure they
 *          are handled properly. Note: these are not "anonymous inner classes" in the Java source code,
 *          but rather classes that are not recorded in any ClassLoaderData::dictionary(),
 *          such as classes that are generated for Lambda expressions.
 *          See https:
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @requires vm.cds
 * @requires vm.jvmti
 * @run driver AnonVmClassesDuringDump
 */

import jdk.test.lib.helpers.ClassFileInstaller;

public class AnonVmClassesDuringDump {
    public static String appClasses[] = {
        Hello.class.getName(),
    };
    public static String agentClasses[] = {
        AnonVmClassesDuringDumpTransformer.class.getName(),
    };

    public static String cdsDiagnosticOption = "-XX:+AllowArchivingWithJavaAgent";

    public static final boolean dynamicMode =
        Boolean.getBoolean(System.getProperty("test.dynamic.cds.archive", "false"));

    public static void main(String[] args) throws Throwable {
        String agentJar =
            ClassFileInstaller.writeJar("AnonVmClassesDuringDumpTransformer.jar",
                                        ClassFileInstaller.Manifest.fromSourceFile("AnonVmClassesDuringDumpTransformer.mf"),
                                        agentClasses);

        String appJar =
            ClassFileInstaller.writeJar("AnonVmClassesDuringDumpApp.jar", appClasses);

        TestCommon.testDump(appJar, TestCommon.list(Hello.class.getName()),
                            "-javaagent:" + agentJar,
                            "-XX:+UnlockDiagnosticVMOptions", cdsDiagnosticOption,
                            "-Djava.lang.invoke.MethodHandle.DUMP_CLASS_FILES=true");

        String prefix = ".class.load. ";
        String class_pattern = ".*Lambda/([0-9]+).*";
        String suffix = ".*source: shared objects file.*";
        String pattern = prefix + class_pattern + suffix;
        TestCommon.run("-cp", appJar,
            "-XX:+UnlockDiagnosticVMOptions", cdsDiagnosticOption, Hello.class.getName())
            .assertNormalExit(dynamicMode ?
                output -> output.shouldMatch(pattern) :
                output -> output.shouldNotMatch(pattern));

        TestCommon.run("-cp", appJar,
            "-XX:+UnlockDiagnosticVMOptions", cdsDiagnosticOption,
            "-XX:+PrintSharedArchiveAndExit", Hello.class.getName())
            .assertNormalExit(dynamicMode ?
                output -> output.shouldMatch(pattern) :
                output -> output.shouldNotMatch(pattern));
    }
}

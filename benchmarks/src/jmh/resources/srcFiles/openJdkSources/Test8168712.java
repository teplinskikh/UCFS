/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=with-dtrace
 * @requires vm.debug
 * @requires vm.hasDTrace
 * @bug 8168712
 *
 * @run main/othervm -XX:CompileCommand=compileonly,Test8168712.*
 *                   -XX:CompileCommand=compileonly,*Object.*
 *                   -XX:+DTraceMethodProbes
 *                   -XX:-UseOnStackReplacement
 *                   -XX:+DeoptimizeRandom
 *                   compiler.runtime.Test8168712
 */

/**
 * @test id=without-dtrace
 * @requires vm.debug
 * @bug 8168712
 *
 * @run main/othervm -XX:CompileCommand=compileonly,Test8168712.*
 *                   -XX:CompileCommand=compileonly,*Object.*
 *                   -XX:-UseOnStackReplacement
 *                   -XX:+DeoptimizeRandom
 *                   compiler.runtime.Test8168712
 */
package compiler.runtime;

import java.lang.ref.Cleaner;
import java.util.*;

public class Test8168712 {
    static HashSet<Test8168712> m = new HashSet<>();

    static Cleaner cleaner = Cleaner.create();

    public Test8168712() {
        cleaner.register(this, () -> cleanup());
    }

    public static void main(String args[]) {
        int i = 0;
        while (i++<15000) {
            test();
        }
    }

    static Test8168712 test() {
        return new Test8168712();
    }

    public void cleanup() {
        m.add(this);
    }
}
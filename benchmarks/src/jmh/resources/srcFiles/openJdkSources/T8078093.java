/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8078093
 * @summary Exponential performance regression Java 8 compiler compared to Java 7 compiler
 * @compile T8078093.java
 */

import java.util.LinkedHashMap;
import java.util.Map;

class T8078093 {
    public static void test() {
        Map<Integer, String> a = x(x(x(x(x(x(x(x(x(x(x(x(
                                    new LinkedHashMap<Integer, String>(),
                                    1, "a"), 2, "b"), 3, "c"), 4, "d"),
                                    5, "e"), 6, "f"), 7, "g"), 8, "h"),
                                    9, "i"), 10, "j"), 11, "k"), 12, "l");
    }

    @SuppressWarnings("unused")
    public static <K, V> Map<K, V> x(Map<K, V> m, K k, V v) {
        return null;
    }
}
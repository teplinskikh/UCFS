/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     5025230
 * @summary Tests to see that a set nested in initialValue works OK
 * @author  Pete Soper
 */
public class InitialValue implements Runnable {

    static ThreadLocal<String> other;
    static boolean passed;

    public class MyLocal extends ThreadLocal<String> {
        String val;
        protected String initialValue() {
            other = new ThreadLocal<String>();
            other.set("Other");
            return "Initial";
        }
    }

    public void run() {
        MyLocal l = new MyLocal();
        String s1 = l.get();
        String s2 = other.get();
        if ((s2 != null) && s2.equals("Other")) {
            passed = true;
        }
    }

    public static void main(String[] args) {
        Thread t = new Thread(new InitialValue());
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            throw new RuntimeException("Test Interrupted: failed");
        }
        if (!passed) {
            throw new RuntimeException("Test Failed");
        }
    }
}
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

package nsk.jdi.ReferenceType.methods;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

/**
 * The debugged application of the test.
 */
public class methods006a {


    private static int exitStatus;
    private static ArgumentHandler argHandler;
    private static Log log;
    private static IOPipe pipe;


    static void display(String msg) {
        log.display("debuggee > " + msg);
    }

    static void complain(String msg) {
        log.complain("debuggee FAILURE > " + msg);
    }

    public static void receiveSignal(String signal) {
        String line = pipe.readln();

        if ( !line.equals(signal) )
            throw new Failure("UNEXPECTED debugger's signal " + line);

        display("debugger's <" + signal + "> signal received.");
    }



    static methods006Enum1 f1 = methods006Enum1.e2;
    static methods006Enum2 f2 = methods006Enum2.e1;
    static methods006Enum1.Enum1_ f3 = methods006Enum1.Enum1_.e1;


    public static void main (String argv[]) {
        exitStatus = Consts.TEST_FAILED;
        argHandler = new ArgumentHandler(argv);
        log = new Log(System.err, argHandler);
        pipe = argHandler.createDebugeeIOPipe(log);

        pipe.println(methods006.SIGNAL_READY);
        receiveSignal(methods006.SIGNAL_QUIT);

        display("completed succesfully.");
        System.exit(Consts.TEST_PASSED + Consts.JCK_STATUS_BASE);
    }


}


enum methods006Enum1 implements methods006i1, methods006i2 {
    e1, e2;
    public int foo() {return 1;}

    enum Enum1_ implements methods006i1, methods006i2 {
        e1, e2;
        public int foo() {return 3;}
    }
}

enum methods006Enum2 implements methods006i1, methods006i2 {
    e1 {
       int val() {return 1;}
    },

    e2 {
       int val() {return 2;}
    };

    abstract int val();
    public int foo() {return 2;}
}

interface methods006i1 {
    public final int i1 = 1;
    public abstract int foo();
}

interface methods006i2 {
    public final int i1 = 1;
    public abstract int foo();
}
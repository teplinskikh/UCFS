/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.ClassType.allInterfaces;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;

import java.io.*;
import java.util.*;

/**
 * The debugger application of the test.
 */
public class allinterfaces002 {


    final static String SIGNAL_READY = "ready";
    final static String SIGNAL_GO    = "go";
    final static String SIGNAL_QUIT  = "quit";

    private static int waitTime;
    private static int exitStatus;
    private static ArgumentHandler     argHandler;
    private static Log                 log;
    private static Debugee             debuggee;
    private static ReferenceType       debuggeeClass;


    private final static String prefix = "nsk.jdi.ClassType.allInterfaces.";
    private final static String className = "allinterfaces002";
    private final static String debuggerName = prefix + className;
    private final static String debuggeeName = debuggerName + "a";


    private final static String[] testedFieldNames = {"f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8"};


    public static void main(String argv[]) {
        int result = run(argv,System.out);
        if (result != 0) {
            throw new RuntimeException("TEST FAILED with result " + result);
        }
    }

    private static void display(String msg) {
        log.display("debugger > " + msg);
    }

    private static void complain(String msg) {
        log.complain("debugger FAILURE > " + msg);
    }

    public static int run(String argv[], PrintStream out) {

        exitStatus = Consts.TEST_PASSED;

        argHandler = new ArgumentHandler(argv);
        log = new Log(out, argHandler);
        waitTime = argHandler.getWaitTime() * 60000;

        debuggee = Debugee.prepareDebugee(argHandler, log, debuggeeName);

        debuggeeClass = debuggee.classByName(debuggeeName);
        if ( debuggeeClass == null ) {
            complain("Class '" + debuggeeName + "' not found.");
            exitStatus = Consts.TEST_FAILED;
        }

        execTest();

        debuggee.quit();

        return exitStatus;
    }


    private static void execTest() {

        for (int i=0; i < testedFieldNames.length; i++) {
            check(testedFieldNames[i]);
            display("");
        }

        display("Checking completed!");
    }


    private static void check (String fieldName) {
        try {
            ClassType checkedClass = (ClassType)debuggeeClass.fieldByName(fieldName).type();
            String className = checkedClass.name();

            boolean isComparable = false;
            boolean isSerializable = false;
            boolean isCloneable = false;
            boolean is_allinterfaces002i = false;

            Iterator<InterfaceType> it = checkedClass.allInterfaces().iterator();
            while (it.hasNext()) {
                InterfaceType i = it.next();
                if (i.name().equals("java.lang.Comparable")) {
                    isComparable = true;
                } else if (i.name().equals("java.io.Serializable")) {
                    isSerializable = true;
                } else if (i.name().equals("java.lang.Cloneable")) {
                    isCloneable = false;
                } else if (i.name().equals("nsk.jdi.ClassType.allInterfaces.allinterfaces002i")) {
                    is_allinterfaces002i = true;
                }

            }

            if (isComparable) {
                display("CHECK1 PASSED: " + className + " implements Comparable");
            } else {
                complain("CHECK1 FAILED: " + className + " does not implement Comparable");
                exitStatus = Consts.TEST_FAILED;
            }

            if (isSerializable) {
                display("CHECK2 PASSED: " + className + " implements Serializable");
            } else {
                complain("CHECK2 FAILED: " + className + " does not implement Serializable");
                exitStatus = Consts.TEST_FAILED;
            }

            if (!isCloneable) {
                display("CHECK3 PASSED: " + className + " does not implement Cloneable");
            } else {
                complain("CHECK3 FAILED: " + className + " implements Cloneable");
                exitStatus = Consts.TEST_FAILED;
            }

            if (fieldName.equals("f3") || fieldName.equals("f7")) {
                if (is_allinterfaces002i) {
                    display("CHECK4 PASSED: " + className + " implements allinterfaces002i");
                } else {
                    complain("CHECK4 FAILED: " + className + " does not implement allinterfaces002i");
                    exitStatus = Consts.TEST_FAILED;
                }
            }

        } catch (Exception e) {
            complain("Unexpected exception while checking of " + className + ": " + e);
            e.printStackTrace(System.out);
            exitStatus = Consts.TEST_FAILED;
        }
    }
}
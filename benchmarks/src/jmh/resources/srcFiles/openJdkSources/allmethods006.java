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

package nsk.jdi.ReferenceType.allMethods;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;

import java.io.*;
import java.util.*;

/**
 * The debugger application of the test.
 */
public class allmethods006 {


    final static String SIGNAL_READY = "ready";
    final static String SIGNAL_GO    = "go";
    final static String SIGNAL_QUIT  = "quit";

    private static int waitTime;
    private static int exitStatus;
    private static ArgumentHandler     argHandler;
    private static Log                 log;
    private static Debugee             debuggee;
    private static ReferenceType       debuggeeClass;


    private final static String prefix = "nsk.jdi.ReferenceType.allMethods.";
    private final static String className = "allmethods006";
    private final static String debuggerName = prefix + className;
    private final static String debuggeeName = debuggerName + "a";


    private final static String[] testedFieldNames = {"f1", "f2", "f3"};

    private final static String[] testedMethodNames = {
        "clone",
        "compareTo",
        "equals",
        "getDeclaringClass",
        "hashCode",
        "name",
        "ordinal",
        "toString"
        };


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

            List<Method> l = checkedClass.allMethods();
            if (l.isEmpty()) {
                complain("\t ReferenceType.allMethods() returned empty list for type: " + className);
                exitStatus = Consts.TEST_FAILED;
            } else {
                Vector<String> methodNames = new Vector<String>();
                Iterator<Method> it = l.iterator();
                while (it.hasNext()) {
                    methodNames.add(it.next().name());
                }

                for (int i = 0; i < testedMethodNames.length; i++) {
                    String methodName = testedMethodNames[i];
                    if (methodNames.contains(methodName)) {
                        display("CHECK" + (i+1) + " PASSED: " +  className + " has method " + methodName);
                    } else {
                        complain("CHECK" + (i+1) + " FAILED: " + className + " does not have method " + methodName);
                        exitStatus = Consts.TEST_FAILED;
                    }
                }
            }

        } catch (Exception e) {
            complain("Unexpected exception while checking of " + className + ": " + e);
            e.printStackTrace(System.out);
            exitStatus = Consts.TEST_FAILED;
        }
    }
}
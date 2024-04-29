/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.CharValue.equals;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import java.util.*;
import java.io.*;

/**
 * The test for the implementation of an object of the type     <BR>
 * CharValue.                                                   <BR>
 *                                                              <BR>
 * The test checks up that results of the method                <BR>
 * <code>com.sun.jdi.CharValue.equals()</code>                  <BR>
 * complies with its spec.                                      <BR>
 * <BR>
 * The cases for testing are as follows :               <BR>
 *                                                      <BR>
 * when a gebuggee executes the following :             <BR>
 *      public static char  char_a_1  = 'a';            <BR>
 *      public static char  char_a_2  = 'a';            <BR>
 *      public static char  char_b    = 'b';            <BR>
 *      public static short shortchar_a_3  = (short) 'a'; <BR>
 *                                                      <BR>
 * which a debugger mirros as :                         <BR>
 *                                                      <BR>
 *      CharValue  cvchar_a_1;                          <BR>
 *      CharValue  cvchar_a_2;                          <BR>
 *      CharValue  cvchar_b;                            <BR>
 *      ShortValue svchar_a_3;                          <BR>
 *                                                      <BR>
 * the following is true:                               <BR>
 *                                                      <BR>
 *       cvchar_a_1 == cvchar_a_2                       <BR>
 *       cvchar_a_1 != cvchar_b                         <BR>
 *       cvchar_a_1 != svchar_a_3                       <BR>
 * <BR>
 */

public class equals001 {

    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int PASS_BASE = 95;

    static final String
    sHeader1 = "\n==> nsk/jdi/CharValue/equals/equals001",
    sHeader2 = "--> equals001: ",
    sHeader3 = "##> equals001: ";


    public static void main (String argv[]) {
        int result = run(argv, System.out);
        if (result != 0) {
            throw new RuntimeException("TEST FAILED with result " + result);
        }
    }

    public static int run (String argv[], PrintStream out) {
        return new equals001().runThis(argv, out);
    }


    private static boolean verbMode = false;

    private static Log  logHandler;

    private static void log1(String message) {
        logHandler.display(sHeader1 + message);
    }
    private static void log2(String message) {
        logHandler.display(sHeader2 + message);
    }
    private static void log3(String message) {
        logHandler.complain(sHeader3 + message);
    }


    private String debuggeeName =
        "nsk.jdi.CharValue.equals.equals001a";


    static ArgumentHandler      argsHandler;
    static int                  testExitCode = PASSED;


    private int runThis (String argv[], PrintStream out) {

        Debugee debuggee;

        argsHandler     = new ArgumentHandler(argv);
        logHandler      = new Log(out, argsHandler);
        Binder binder   = new Binder(argsHandler, logHandler);

        if (argsHandler.verbose()) {
            debuggee = binder.bindToDebugee(debuggeeName + " -vbs"); 
        } else {
            debuggee = binder.bindToDebugee(debuggeeName);           
        }

        IOPipe pipe     = new IOPipe(debuggee);

        debuggee.redirectStderr(out);
        log2("equals001a debuggee launched");
        debuggee.resume();

        String line = pipe.readln();
        if ((line == null) || !line.equals("ready")) {
            log3("signal received is not 'ready' but: " + line);
            return FAILED;
        } else {
            log2("'ready' recieved");
        }

        VirtualMachine vm = debuggee.VM();

        log1("      TESTING BEGINS");

        for (int i = 0; ; i++) {
            pipe.println("newcheck");
            line = pipe.readln();

            if (line.equals("checkend")) {
                log2("     : returned string is 'checkend'");
                break ;
            } else if (!line.equals("checkready")) {
                log3("ERROR: returned string is not 'checkready'");
                testExitCode = FAILED;
                break ;
            }

            log1("new check: #" + i);


            List listOfDebuggeeExecClasses = vm.classesByName(debuggeeName);
            if (listOfDebuggeeExecClasses.size() != 1) {
                testExitCode = FAILED;
                log3("ERROR: listOfDebuggeeExecClasses.size() != 1");
                break ;
            }
            ReferenceType execClass =
                        (ReferenceType) listOfDebuggeeExecClasses.get(0);

            Field fcchar_a_1 = execClass.fieldByName("char_a_1");
            Field fcchar_a_2 = execClass.fieldByName("char_a_2");
            Field fcchar_b   = execClass.fieldByName("char_b");
            Field fschar_a_3 = execClass.fieldByName("shortchar_a_3");

            CharValue  cvchar_a_1 = (CharValue)  execClass.getValue(fcchar_a_1);
            CharValue  cvchar_a_2 = (CharValue)  execClass.getValue(fcchar_a_2);
            CharValue  cvchar_b   = (CharValue)  execClass.getValue(fcchar_b);
            ShortValue svchar_a_3 = (ShortValue) execClass.getValue(fschar_a_3);


            int i2;

            for (i2 = 0; ; i2++) {

                int expresult = 0;

                log2("new check: #" + i2);

                switch (i2) {

                case 0: if (!cvchar_a_1.equals(cvchar_a_2))
                            expresult = 1;
                        break;

                case 1: if (cvchar_a_1.equals(cvchar_b))
                            expresult = 1;
                        break;

                case 2: if (cvchar_a_1.equals(svchar_a_3))
                            expresult = 1;
                        break;


                default: expresult = 2;
                         break ;
                }

                if (expresult == 2) {
                    log2("      test cases finished");
                    break ;
                } else if (expresult == 1) {
                    log3("ERROR: expresult != true;  check # = " + i);
                    testExitCode = FAILED;
                }
            }
        }
        log1("      TESTING ENDS");


        pipe.println("quit");
        log2("waiting for the debuggee finish ...");
        debuggee.waitFor();

        int status = debuggee.getStatus();
        if (status != PASSED + PASS_BASE) {
            log3("debuggee returned UNEXPECTED exit status: " +
                   status + " != PASS_BASE");
            testExitCode = FAILED;
        } else {
            log2("debuggee returned expected exit status: " +
                   status + " == PASS_BASE");
        }

        if (testExitCode != PASSED) {
            logHandler.complain("TEST FAILED");
        }
        return testExitCode;
    }
}
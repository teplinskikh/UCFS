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

package nsk.jdi.VirtualMachine.mirrorOf_int;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import java.util.*;
import java.io.*;

/**
 * The test for the implementation of an object of the type     <BR>
 * VirtualMachine.                                              <BR>
 *                                                              <BR>
 * The test checks up that results of the method                <BR>
 * <code>com.sun.jdi.VirtualMachine.mirrorOf_int()</code>       <BR>
 * complies with the spec for                                   <BR>
 * <code>com.sun.jdi.IntegerValue</code> methods                <BR>
 * <BR>
 * The cases for testing are as follows         <BR>
 * ('val_i' means 'integer_value_i') :           <BR>
 *                                              <BR>
 * 
 *                                                              <BR>
 *      val_i.value(Integer.MIN_VALUE)  == Integer.MIN_VALUE    <BR>
 *      val_i.value(Integer.MAX_VALUE)  == Integer.MAX_VALUE    <BR>
 *      val_i.value(0)     ==  0                                <BR>
 *      val_i.value(-1)    == -1                                <BR>
 *      val_i.value(+1)    == +1                                <BR>
 *                                              <BR>
 * 
 *                                              <BR>
 *      val_i.value(1) == val_j.value(1)        <BR>
 *      val_i.value(1) != val_j.value(-1)       <BR>
 *                                              <BR>
 *      val_i.value(1) != integerValue.value(1) <BR>
 *                                              <BR>
 * 
 *                                              <BR>
 *      val_i.hashCode() == val_i.hashCode()    <BR>
 *                                              <BR>
 *      if (val_i.value() == val_j.value()) {   <BR>
 *          val_i.hashCode() == val_j.hashCode()<BR>
 *      }                                       <BR>
 * <BR>
 */

public class mirrorof_int001 {

    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int PASS_BASE = 95;

    static final String
    sHeader1 = "\n==> nsk/jdi/VirtualMachine/mirrorOf_int/mirrorof_int001",
    sHeader2 = "--> mirrorof_int001: ",
    sHeader3 = "##> mirrorof_int001: ";


    public static void main (String argv[]) {
        int result = run(argv, System.out);
        if (result != 0) {
            throw new RuntimeException("TEST FAILED with result " + result);
        }
    }

    public static int run (String argv[], PrintStream out) {
        return new mirrorof_int001().runThis(argv, out);
    }



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


    private String debugeeName =
        "nsk.jdi.VirtualMachine.mirrorOf_int.mirrorof_int001a";


    static ArgumentHandler      argsHandler;
    static int                  testExitCode = PASSED;


    private int runThis (String argv[], PrintStream out) {

        Debugee debugee;

        argsHandler     = new ArgumentHandler(argv);
        logHandler      = new Log(out, argsHandler);
        Binder binder   = new Binder(argsHandler, logHandler);

        if (argsHandler.verbose()) {
            debugee = binder.bindToDebugee(debugeeName + " -vbs");  
        } else {
            debugee = binder.bindToDebugee(debugeeName);            
        }

        IOPipe pipe     = new IOPipe(debugee);

        debugee.redirectStderr(out);
        log2("mirrorof_int001a debugee launched");
        debugee.resume();

        String line = pipe.readln();
        if ((line == null) || !line.equals("ready")) {
            log3("signal received is not 'ready' but: " + line);
            return FAILED;
        } else {
            log2("'ready' recieved");
        }

        VirtualMachine vm = debugee.VM();

        log1("      TESTING BEGINS");


        int smallest    =  Integer.MIN_VALUE;
        int zero        =  0;
        int largest     =  Integer.MAX_VALUE;
        int plus1       =  +1;
        int minus1      =  -1;

        IntegerValue val_1 = vm.mirrorOf(smallest);
        IntegerValue val_2 = vm.mirrorOf(zero);
        IntegerValue val_3 = vm.mirrorOf(largest);
        IntegerValue val_4 = vm.mirrorOf(plus1);
        IntegerValue val_5 = vm.mirrorOf(plus1);
        IntegerValue val_6 = vm.mirrorOf(minus1);

        LongValue val_7 = vm.mirrorOf((long)plus1);

        int i;

        for (i = 0; ; i++) {

            int expresult = 0;

            log2("     new check: #" + i);

            switch (i) {


            case 0: if (val_1.value() != smallest)
                        expresult = 1;
                    break;

            case 1: if (val_2.value() != zero)
                        expresult = 1;
                    break;

            case 2: if (val_3.value() != largest)
                        expresult = 1;
                    break;



            case 3: if (!val_4.equals(val_5))
                        expresult = 1;
                    break;

            case 4: if (val_4.equals(val_6))
                        expresult = 1;
                    break;

            case 5: if (val_4.equals(val_7))
                        expresult = 1;
                    break;



            case 6: if (val_1.hashCode() != val_1.hashCode())
                        expresult = 1;
                    break;

            case 7: if (val_4.hashCode() != val_5.hashCode())
                        expresult = 1;
                    break;


            default: expresult = 2;
                     break ;
            }

            if (expresult == 2) {
                log2("      test cases finished");
                break ;
            } else if (expresult == 1) {;
                log3("ERROR: expresult != true;  check # = " + i);
                testExitCode = FAILED;
            }

        }
        log1("      TESTING ENDS");





        pipe.println("quit");
        log2("waiting for the debugee finish ...");
        debugee.waitFor();

        int status = debugee.getStatus();
        if (status != PASSED + PASS_BASE) {
            log3("debugee returned UNEXPECTED exit status: " +
                   status + " != PASS_BASE");
            testExitCode = FAILED;
        } else {
            log2("debugee returned expected exit status: " +
                   status + " == PASS_BASE");
        }

        if (testExitCode != PASSED) {
            logHandler.complain("TEST FAILED");
        }
        return testExitCode;
    }
}
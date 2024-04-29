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

package nsk.jdi.ReferenceType.methodsByName_s;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import java.util.*;
import java.io.*;

/**
 * This test checks the method <code>methodsByName(String name)</code>
 * of the JDI interface <code>ReferenceType</code> of com.sun.jdi package
 */

public class methbyname_s003 extends Log {
    static java.io.PrintStream out_stream;
    static boolean verbose_mode = false;  

    /** The main class names of the debugger & debugee applications. */
    private final static String
        package_prefix = "nsk.jdi.ReferenceType.methodsByName_s.",
        thisClassName = package_prefix + "methbyname_s003",
        debugeeName   = thisClassName + "a";

    /** Debugee's class for check **/
    private final static String checked_class = package_prefix + "methbyname_s003b";



    public static void main (String argv[]) {
        int result = run(argv,System.out);
        if (result != 0) {
            throw new RuntimeException("TEST FAILED with result " + result);
        }

    }

    /**
     * JCK-like entry point to the test: perform testing, and
     * return exit code 0 (PASSED) or either 2 (FAILED).
     */
    public static int run (String argv[], PrintStream out) {
        out_stream = out;

        int v_test_result = new methbyname_s003().runThis(argv,out_stream);
        if ( v_test_result == 2/*STATUS_FAILED*/ ) {
            out_stream.println
                ("\n==> nsk/jdi/ReferenceType/methodsByName_s/methbyname_s003 test FAILED");
        }
        else {
            out_stream.println
                ("\n==> nsk/jdi/ReferenceType/methodsByName_s/methbyname_s003 test PASSED");
        }
        return v_test_result;
    }

    private void print_log_on_verbose(String message) {
        display(message);
    }

    private static void print_log_without_verbose(String message) {
        if ( ! verbose_mode ) {
            out_stream.println(message);
        }
    }

    /**
     * Non-static variant of the method <code>run(args,out)</code>
     */
    private int runThis (String argv[], PrintStream out) {
        if ( out_stream == null ) {
            out_stream = out;
        }

        out_stream.println("==> nsk/jdi/ReferenceType/methodsByName_s/methbyname_s003 test LOG:");
        out_stream.println("--> test checks methodsByName(String name) method of ReferenceType interface ");
        out_stream.println("    of the com.sun.jdi package for UNLOADED class\n");

        ArgumentHandler argHandler = new ArgumentHandler(argv);
        verbose_mode = argHandler.verbose();
        argv = argHandler.getArguments();

        String debugee_launch_command = debugeeName;
        if (verbose_mode) {
            logTo(out_stream);
        }

        Binder binder = new Binder(argHandler,this);
        Debugee debugee = binder.bindToDebugee(debugee_launch_command);
        IOPipe pipe = debugee.createIOPipe();

        debugee.redirectStderr(out);
        print_log_on_verbose("--> methbyname_s003: methbyname_s003a debugee launched");
        debugee.resume();

        String debugee_signal = pipe.readln();
        if (debugee_signal == null) {
            out_stream.println
                ("##> methbyname_s003: UNEXPECTED debugee's signal (not \"ready0\") - " + debugee_signal);
            return 2/*STATUS_FAILED*/;
        }
        if (!debugee_signal.equals("ready0")) {
            out_stream.println
                ("##> methbyname_s003: UNEXPECTED debugee's signal (not \"ready0\") - " + debugee_signal);
            return 2/*STATUS_FAILED*/;
        }
        else {
            print_log_on_verbose("--> methbyname_s003: debugee's \"ready0\" signal recieved!");
        }

        debugee_signal = pipe.readln();
        if (debugee_signal == null) {
            out_stream.println
                ("##> methbyname_s003: UNEXPECTED debugee's signal (not \"ready1\") - " + debugee_signal);
            return 2/*STATUS_FAILED*/;
        }
        if (!debugee_signal.equals("ready1")) {
            out_stream.println
                ("##> methbyname_s003: UNEXPECTED debugee's signal (not \"ready1\") - " + debugee_signal);
            return 2/*STATUS_FAILED*/;
        }
        else {
            print_log_on_verbose("--> methbyname_s003: debugee's \"ready1\" signal recieved!");
        }

        boolean class_not_found_error = false;
        boolean methodsByName_s_method_error = false;

        while ( true ) {
            print_log_on_verbose
                ("--> methbyname_s003: getting ReferenceType object for loaded checked class...");
            ReferenceType refType = debugee.classByName(checked_class);
            if (refType == null) {
                print_log_without_verbose
                    ("--> methbyname_s003: getting ReferenceType object for loaded checked class...");
                out_stream.println("##> methbyname_s003: FAILED: Could NOT FIND checked class: " + checked_class);
                class_not_found_error = true;
                break;
            }
            else {
                print_log_on_verbose("--> methbyname_s003: checked class FOUND: " + checked_class);
            }
            print_log_on_verbose
                ("--> methbyname_s003: waiting for \"ready2\" or \"not_unloaded\" signal from debugee...");
            pipe.println("continue");
            debugee_signal = pipe.readln();
            if (debugee_signal == null) {
                out_stream.println
                    ("##> methbyname_s003: UNEXPECTED debugee's signal - " + debugee_signal);
                return 2/*STATUS_FAILED*/;
            }
            if ( debugee_signal.equals("not_unloaded")) {
                out_stream.println
                    ("--> methbyname_s003: debugee's \"not_unloaded\" signal recieved!");
                print_log_without_verbose
                    ("-->                  checked class may be NOT unloaded!");
                out_stream.println
                    ("-->                  ReferenceType.methodsByName_s() method can NOT be checked!");
                break;
            }
            if (!debugee_signal.equals("ready2")) {
                out_stream.println
                    ("##> methbyname_s003: UNEXPECTED debugee's signal (not \"ready2\") - " + debugee_signal);
                return 2/*STATUS_FAILED*/;
            }
            else {
                print_log_on_verbose("--> methbyname_s003: debugee's \"ready2\" signal recieved!");
            }
            print_log_on_verbose
                ("--> methbyname_s003: check that checked class has been unloaded realy...");
            ReferenceType refType2 = debugee.classByName(checked_class);
            if (refType2 == null) {
                print_log_on_verbose
                    ("--> methbyname_s003: checked class has been unloaded realy: " + checked_class);
            }
            else {
                print_log_without_verbose
                    ("--> methbyname_s003: check that checked class has been unloaded realy...");
                out_stream.println
                    ("--> methbyname_s003: checked class FOUND: " + checked_class
                    + " => it has NOT been unloaded!");
                out_stream.println
                    ("-->                  ReferenceType.methodsByName_s() method can NOT be checked!");
                break;
            }

            out_stream.println
                ("--> methbyname_s003: check ReferenceType.methodsByName(...) method for unloaded class...");
            List methods_byname_list = null;
            try {
                methods_byname_list = refType.methodsByName("dummy_method_name");
            }
            catch (Exception expt) {
                if (expt instanceof com.sun.jdi.ObjectCollectedException) {
                    out_stream.println
                        ("--> methbyname_s003: PASSED: expected Exception thrown - " + expt.toString());
                }
                else {
                    out_stream.println
                        ("##> methbyname_s003: FAILED: unexpected Exception thrown - " + expt.toString());
                    out_stream.println
                        ("##>                  expected Exception - com.sun.jdi.ObjectCollectedException");
                    methodsByName_s_method_error = true;
                }
            }
            break;
        }
        int v_test_result = 0/*STATUS_PASSED*/;
        if ( class_not_found_error || methodsByName_s_method_error ) {
            v_test_result = 2/*STATUS_FAILED*/;
        }

        print_log_on_verbose("--> methbyname_s003: waiting for debugee finish...");
        pipe.println("quit");
        debugee.waitFor();

        int status = debugee.getStatus();
        if (status != 0/*STATUS_PASSED*/ + 95/*STATUS_TEMP*/) {
            out_stream.println
                ("##> methbyname_s003: UNEXPECTED Debugee's exit status (not 95) - " + status);
            v_test_result = 2/*STATUS_FAILED*/;
        }
        else {
            print_log_on_verbose
                ("--> methbyname_s003: expected Debugee's exit status - " + status);
        }

        return v_test_result;
    }
}  
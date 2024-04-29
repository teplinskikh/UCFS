/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdwp.ThreadReference.CurrentContendedMonitor;

import java.io.*;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdwp.*;

/**
 * Test for JDWP command: ThreadReference.CurrentContendedMonitor.
 *
 * See curcontmonitor001.README for description of test execution.
 *
 * This class represents debugger part of the test.
 * Test is executed by invoking method runIt().
 * JDWP command is tested in the method testCommand().
 *
 * @see #runIt()
 * @see #testCommand()
 */
public class curcontmonitor001 {

    static final int JCK_STATUS_BASE = 95;
    static final int PASSED = 0;
    static final int FAILED = 2;

    static final String READY = "ready";
    static final String ERROR = "error";
    static final String QUIT = "quit";

    static final String PACKAGE_NAME = "nsk.jdwp.ThreadReference.CurrentContendedMonitor";
    static final String TEST_CLASS_NAME = PACKAGE_NAME + "." + "curcontmonitor001";
    static final String DEBUGEE_CLASS_NAME = TEST_CLASS_NAME + "a";

    static final int VM_CAPABILITY_NUMBER = JDWP.Capability.CAN_GET_CURRENT_CONTENDED_MONITOR;
    static final String VM_CAPABILITY_NAME = "canGetCurrentContendedMonitor";

    static final String JDWP_COMMAND_NAME = "ThreadReference.CurrentContendedMonitor";
    static final int JDWP_COMMAND_ID = JDWP.Command.ThreadReference.CurrentContendedMonitor;

    static final String TESTED_CLASS_NAME = DEBUGEE_CLASS_NAME + "$" + "TestedClass";
    static final String TESTED_CLASS_SIGNATURE = "L" + TESTED_CLASS_NAME.replace('.', '/') + ";";

    static final String TESTED_THREAD_NAME = curcontmonitor001a.THREAD_NAME;
    static final String THREAD_FIELD_NAME = curcontmonitor001a.THREAD_FIELD_NAME;
    static final String MONITOR_FIELD_NAME = curcontmonitor001a.MONITOR_FIELD_NAME;

    ArgumentHandler argumentHandler = null;
    Log log = null;
    Binder binder = null;
    Debugee debugee = null;
    Transport transport = null;
    IOPipe pipe = null;

    boolean success = true;


    /**
     * Start test from command line.
     */
    public static void main (String argv[]) {
        System.exit(run(argv,System.out) + JCK_STATUS_BASE);
    }

    /**
     * Start JCK-compilant test.
     */
    public static int run(String argv[], PrintStream out) {
        return new curcontmonitor001().runIt(argv, out);
    }


    /**
     * Perform test execution.
     */
    public int runIt(String argv[], PrintStream out) {

        argumentHandler = new ArgumentHandler(argv);
        log = new Log(out, argumentHandler);

        try {
            log.display("\n>>> Preparing debugee for testing \n");

            binder = new Binder(argumentHandler, log);
            log.display("Launching debugee");
            debugee = binder.bindToDebugee(DEBUGEE_CLASS_NAME);
            transport = debugee.getTransport();
            pipe = debugee.createIOPipe();

            prepareDebugee();

            long threadID = 0;
            try {
                log.display("\n>>> Checking VM capability \n");

                log.display("Checking VM capability: " + VM_CAPABILITY_NAME);
                if (!debugee.getCapability(VM_CAPABILITY_NUMBER, VM_CAPABILITY_NAME)) {
                    out.println("TEST PASSED: unsupported VM capability: "
                                + VM_CAPABILITY_NAME);
                    return PASSED;
                }

                log.display("\n>>> Obtaining requred data from debugee \n");

                log.display("Getting classID by signature:\n"
                            + "  " + TESTED_CLASS_SIGNATURE);
                long classID = debugee.getReferenceTypeID(TESTED_CLASS_SIGNATURE);
                log.display("  got classID: " + classID);

                log.display("Getting threadID value from static field: "
                            + THREAD_FIELD_NAME);
                threadID = queryObjectID(classID, THREAD_FIELD_NAME, JDWP.Tag.THREAD);
                log.display("  got threadID: " + threadID);

                log.display("Getting objectID value for owned monitor from static field: "
                            + MONITOR_FIELD_NAME);
                long monitorID = queryObjectID(classID,
                            MONITOR_FIELD_NAME, JDWP.Tag.OBJECT);
                log.display("  got objectID: " + monitorID);

                log.display("Suspending all threads into debuggee");
                debugee.suspend();
                log.display("  debuggee suspended");

                log.display("\n>>> Testing JDWP command \n");
                testCommand(threadID, monitorID);

            } finally {
                log.display("\n>>> Finishing test \n");

                log.display("resuming all threads into debuggee");
                debugee.resume();
                log.display("  debuggee resumed");

                quitDebugee();
            }

        } catch (Failure e) {
            log.complain("TEST FAILED: " + e.getMessage());
            success = false;
        } catch (Exception e) {
            e.printStackTrace(out);
            log.complain("Caught unexpected exception while running the test:\n\t" + e);
            success = false;
        }

        if (!success) {
            log.complain("TEST FAILED");
            return FAILED;
        }

        out.println("TEST PASSED");
        return PASSED;

    }

    /**
     * Prepare debugee for testing and waiting for ready signal.
     */
    void prepareDebugee() {
        log.display("Waiting for VM_INIT event");
        debugee.waitForVMInit();

        log.display("Querying for IDSizes");
        debugee.queryForIDSizes();

        log.display("Resuming debugee VM");
        debugee.resume();

        log.display("Waiting for signal from debugee: " + READY);
        String signal = pipe.readln();
        log.display("Received signal from debugee: " + signal);
        if (signal == null) {
            throw new TestBug("Null signal received from debugee: " + signal
                            + " (expected: " + READY + ")");
        } else if (signal.equals(ERROR)) {
            throw new TestBug("Debugee was not able to start tested thread"
                            + " (received signal: " + signal + ")");
        } else if (!signal.equals(READY)) {
            throw new TestBug("Unexpected signal received from debugee: " + signal
                            + " (expected: " + READY + ")");
        }
    }

    /**
     * Sending debugee signal to quit and waiting for it exits.
     */
    void quitDebugee() {
        log.display("Sending signal to debugee: " + QUIT);
        pipe.println(QUIT);

        log.display("Waiting for debugee exits");
        int code = debugee.waitFor();

        if (code == JCK_STATUS_BASE + PASSED) {
            log.display("Debugee PASSED with exit code: " + code);
        } else {
            log.complain("Debugee FAILED with exit code: " + code);
            success = false;
        }
    }

    /**
     * Query debuggee for objectID value of static class field.
     */
    long queryObjectID(long classID, String fieldName, byte tag) {
        long fieldID = debugee.getClassFieldID(classID, fieldName, true);
        JDWP.Value value = debugee.getStaticFieldValue(classID, fieldID);

        if (value.getTag() != tag) {
            throw new Failure("Wrong objectID tag received from field \"" + fieldName
                            + "\": " + value.getTag() + " (expected: " + tag + ")");
        }

        long objectID = ((Long)value.getValue()).longValue();
        return objectID;
    }

    /**
     * Perform testing JDWP command for specified threadID.
     */
    void testCommand(long threadID, long monitorID) {
        log.display("Create command packet:");
        log.display("Command: " + JDWP_COMMAND_NAME);
        CommandPacket command = new CommandPacket(JDWP_COMMAND_ID);
        log.display("  threadID: " + threadID);
        command.addObjectID(threadID);
        command.setLength();

        try {
            log.display("Sending command packet:\n" + command);
            transport.write(command);
        } catch (IOException e) {
            log.complain("Unable to send command packet:\n\t" + e);
            success = false;
            return;
        }

        ReplyPacket reply = new ReplyPacket();

        try {
            log.display("Waiting for reply packet");
            transport.read(reply);
            log.display("Reply packet received:\n" + reply);
        } catch (IOException e) {
            log.complain("Unable to read reply packet:\n\t" + e);
            success = false;
            return;
        }

        try{
            log.display("Checking reply packet header");
            reply.checkHeader(command.getPacketID());
        } catch (BoundException e) {
            log.complain("Bad header of reply packet:\n\t" + e.getMessage());
            success = false;
            return;
        }

        log.display("Parsing reply packet:");
        reply.resetPosition();

        byte tag = (byte)0;
        try {
            tag = reply.getByte();
            log.display("    tag: " + tag);
        } catch (BoundException e) {
            log.complain("Unable to extract tag for contended monitor object from reply packet:\n\t"
                    + e.getMessage());
            success = false;
            return;
        }

        long objectID = 0;
        try {
            objectID = reply.getObjectID();
            log.display("    objectID: " + objectID);
        } catch (BoundException e) {
            log.complain("Unable to extract contended monitor objectID from reply packet:\n\t"
                    + e.getMessage());
            success = false;
            return;
        }

        if (tag != JDWP.Tag.OBJECT) {
            log.complain("Unexpected tag for monitor object received:" + tag
                        + " (expected" + JDWP.Tag.OBJECT);
            success = false;
        }

        if (objectID < 0) {
            log.complain("Negative value of objectID received: " + objectID);
            success = false;
        }

        if (objectID != monitorID) {
            log.display("Unexpected monitor objectID received: " + objectID
                        + " (expected" + monitorID);
            success = false;
        }

        if (!reply.isParsed()) {
            log.complain("Extra trailing bytes found in reply packet at: "
                        + reply.offsetString());
            success = false;
        }

    }

}
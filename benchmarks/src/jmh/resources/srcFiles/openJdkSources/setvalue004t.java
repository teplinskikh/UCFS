/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.ObjectReference.setValue;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

/**
 * This is a debuggee class.
 */
public class setvalue004t {
    static final byte    sByteFld = 127;
    static final short   sShortFld = -32768;
    static final int     sIntFld = 2147483647;
    static final long    sLongFld = 9223372036854775807L;
    static final float   sFloatFld = 5.1F;
    static final double  sDoubleFld = 6.2D;
    static final char    sCharFld = 'a';
    static final boolean sBooleanFld = false;
    static final String  sStrFld = "instance field";

    final byte    iByteFld = 127;
    final short   iShortFld = -32768;
    final int     iIntFld = 2147483647;
    final long    iLongFld = 9223372036854775807L;
    final float   iFloatFld = 5.1F;
    final double  iDoubleFld = 6.2D;
    final char    iCharFld = 'a';
    final boolean iBooleanFld = false;
    final String  iStrFld = "instance field";

    public static void main(String args[]) {
        System.exit(run(args) + Consts.JCK_STATUS_BASE);
    }

    public static int run(String args[]) {
        return new setvalue004t().runIt(args);
    }

    private int runIt(String args[]) {
        ArgumentHandler argHandler = new ArgumentHandler(args);
        IOPipe setvalue004tPipe = argHandler.createDebugeeIOPipe();

        Thread.currentThread().setName(setvalue004.DEBUGGEE_THRNAME);

        setvalue004tPipe.println(setvalue004.COMMAND_READY);
        String cmd = setvalue004tPipe.readln();
        if (!cmd.equals(setvalue004.COMMAND_QUIT)) {
            System.err.println("TEST BUG: unknown debugger command: "
                + cmd);
            System.exit(Consts.JCK_STATUS_BASE +
                Consts.TEST_FAILED);
        }
        return Consts.TEST_PASSED;
    }
}
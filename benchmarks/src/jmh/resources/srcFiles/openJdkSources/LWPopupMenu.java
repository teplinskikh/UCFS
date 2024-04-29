/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @key headful
  @bug 4811096
  @summary Tests whether a LW menu correctly overlaps a HW button
  @author anthony.petrov@...: area=awt.mixing
  @library ../regtesthelpers
  @build Util
  @run main LWPopupMenu
*/


/**
 * LWPopupMenu.java
 *
 * summary:  Tests whether a LW menu correctly overlaps a HW button
 */

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import test.java.awt.regtesthelpers.Util;



public class LWPopupMenu
{


    static volatile boolean failed = true;

    private static void init()
    {
        JFrame f = new JFrame("LW menu test");

        JMenuBar menubar = new JMenuBar();
        f.setJMenuBar(menubar);

        JMenu lmenu = new JMenu("Lite Menu");
        lmenu.add("Salad");
        lmenu.add( new AbstractAction("Fruit Plate") {
            public void actionPerformed(ActionEvent e) {
                failed = false;
            }
        });
        lmenu.add("Water");
        menubar.add(lmenu);

        Button heavy = new Button("  Heavyweight Button  ");

        Box box = Box.createVerticalBox();
        box.add(Box.createVerticalStrut(20));
        box.add(heavy);
        box.add(Box.createVerticalStrut(20));

        f.getContentPane().add("Center", box);

        f.pack();
        f.show();

        Robot robot = Util.createRobot();
        robot.setAutoDelay(20);

        Util.waitForIdle(robot);

        Point lLoc = lmenu.getLocationOnScreen();
        robot.mouseMove(lLoc.x + 5, lLoc.y + 5);

        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        Util.waitForIdle(robot);


        Point bLoc = heavy.getLocationOnScreen();
        robot.mouseMove(bLoc.x + 10, bLoc.y + 5);

        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        Util.waitForIdle(robot);

        if (failed) {
            LWPopupMenu.fail("The LW menu item did not received the click.");
        } else {
            LWPopupMenu.pass();
        }
    }



    /*****************************************************
     * Standard Test Machinery Section
     * DO NOT modify anything in this section -- it's a
     * standard chunk of code which has all of the
     * synchronisation necessary for the test harness.
     * By keeping it the same in all tests, it is easier
     * to read and understand someone else's test, as
     * well as insuring that all tests behave correctly
     * with the test harness.
     * There is a section following this for test-
     * classes
     ******************************************************/
    private static boolean theTestPassed = false;
    private static boolean testGeneratedInterrupt = false;
    private static String failureMessage = "";

    private static Thread mainThread = null;

    private static int sleepTime = 300000;

    public static void main( String args[] ) throws InterruptedException
    {
        mainThread = Thread.currentThread();
        try
        {
            init();
        }
        catch( TestPassedException e )
        {
            return;
        }

        try
        {
            Thread.sleep( sleepTime );
            throw new RuntimeException( "Timed out after " + sleepTime/1000 + " seconds" );
        }
        catch (InterruptedException e)
        {
            if( ! testGeneratedInterrupt ) throw e;

            testGeneratedInterrupt = false;

            if ( theTestPassed == false )
            {
                throw new RuntimeException( failureMessage );
            }
        }

    }

    public static synchronized void setTimeoutTo( int seconds )
    {
        sleepTime = seconds * 1000;
    }

    public static synchronized void pass()
    {
        System.out.println( "The test passed." );
        System.out.println( "The test is over, hit  Ctl-C to stop Java VM" );
        if ( mainThread == Thread.currentThread() )
        {
            theTestPassed = true;
            throw new TestPassedException();
        }
        theTestPassed = true;
        testGeneratedInterrupt = true;
        mainThread.interrupt();
    }

    public static synchronized void fail()
    {
        fail( "it just plain failed! :-)" );
    }

    public static synchronized void fail( String whyFailed )
    {
        System.out.println( "The test failed: " + whyFailed );
        System.out.println( "The test is over, hit  Ctl-C to stop Java VM" );
        if ( mainThread == Thread.currentThread() )
        {
            throw new RuntimeException( whyFailed );
        }
        theTestPassed = false;
        testGeneratedInterrupt = true;
        failureMessage = whyFailed;
        mainThread.interrupt();
    }

}

class TestPassedException extends RuntimeException
{
}
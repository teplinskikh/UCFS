/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

/*
 * @test
 * @summary To test SystemFlavorMap method:
 *          setFlavorsForNative(String nat, DataFlavors[] flavors)
 *          with valid natives and DataFlavors. This stress test will
 *          define numerous mappings of valid String natives and
 *          DataFlavors.  The mappings will be verified by examining
 *          that all entries are present, and order is maintained.
 * @author Rick Reynaga (rick.reynaga@eng.sun.com) area=Clipboard
 * @modules java.datatransfer
 * @run main SetNativesTest
 */

public class SetNativesTest {

    SystemFlavorMap flavorMap;
    Hashtable hashVerify;

    Map mapFlavors;
    Map mapNatives;

    Hashtable hashFlavors;
    Hashtable hashNatives;

    public static void main (String[] args){
        new SetNativesTest().doTest();
    }

    public void doTest() {
        flavorMap = (SystemFlavorMap)SystemFlavorMap.getDefaultFlavorMap();

        mapFlavors = flavorMap.getNativesForFlavors(null);
        mapNatives = flavorMap.getFlavorsForNatives(null);

        hashFlavors = new Hashtable(mapFlavors);
        hashNatives = new Hashtable(mapNatives);


        String key;
        hashVerify = new Hashtable();

        for (Enumeration e = hashNatives.keys() ; e.hasMoreElements() ;) {
            key = (String)e.nextElement();

            java.util.List listFlavors = flavorMap.getFlavorsForNative(key);
            Vector vectorFlavors = new Vector(listFlavors);
            DataFlavor[] arrayFlavors = (DataFlavor[])vectorFlavors.toArray(new DataFlavor[0]);

            key = key.concat("TEST");   
            flavorMap.setFlavorsForNative(key, arrayFlavors);
            hashVerify.put(key, vectorFlavors);
        }

        verifyNewMappings();
    }

    public void verifyNewMappings() {
        for (Enumeration e = hashVerify.keys() ; e.hasMoreElements() ;) {
            String key = (String)e.nextElement();

            java.util.List listFlavors = flavorMap.getFlavorsForNative(key);
            Vector vectorFlavors = new Vector(listFlavors);

            if ( !vectorFlavors.equals((Vector)hashVerify.get(key))) {
                throw new RuntimeException("\n*** Error in verifyNewMappings()" +
                    "\nmethod1: setFlavorsForNative(String nat, DataFlavors[] flavors)" +
                    "\nmethod2: List getFlavorsForNative(String nat)" +
                    "\nString native: " + key +
                    "\nThe Returned List did not match the original set of DataFlavors.");
            }
        }
        System.out.println("*** native size = " + hashVerify.size());
    }
}
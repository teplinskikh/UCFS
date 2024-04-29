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
import java.util.*;

/*
 * @test
 * @summary To test SystemFlavorMap methods
 *          List getFlavorsForNative(String nat)
 *          List getNativesForFlavor(DataFlavor flav)
 *          with valid natives and DataFlavors, including null.
 *          This test will verify that the returned mappings
 *          include all entries and that the correct order is
 *          maintained.
 * @author Rick Reynaga (rick.reynaga@eng.sun.com) area=Clipboard
 * @modules java.datatransfer
 * @run main ManyFlavorMapTest
 */

public class ManyFlavorMapTest {

    SystemFlavorMap flavorMap;

    Map mapFlavors;
    Map mapNatives;

    Hashtable hashFlavors;
    Hashtable hashNatives;

    public static void main (String[] args) {
        new ManyFlavorMapTest().doTest();
    }

    public void doTest() {
        flavorMap = (SystemFlavorMap)SystemFlavorMap.getDefaultFlavorMap();

        mapFlavors = flavorMap.getNativesForFlavors(null);
        mapNatives = flavorMap.getFlavorsForNatives(null);

        hashFlavors = new Hashtable(mapFlavors);
        hashNatives = new Hashtable(mapNatives);

        List listNatives = flavorMap.getNativesForFlavor(null);
        verifyListAllNativeEntries(listNatives);

        List listFlavors = flavorMap.getFlavorsForNative(null);
        verifyListAllDataFlavorEntries(listFlavors);

        verifyListNativeEntries();

        verifyListDataFlavorEntries();
    }

    public void verifyListDataFlavorEntries() {
        for (Enumeration e = hashNatives.keys() ; e.hasMoreElements() ;) {
            String key = (String)e.nextElement();

            DataFlavor value = (DataFlavor)hashNatives.get(key);

            java.util.List listFlavors = flavorMap.getFlavorsForNative(key);
            Vector vectorFlavors = new Vector(listFlavors);

            DataFlavor prefFlavor = (DataFlavor)vectorFlavors.firstElement();
            if ( value != prefFlavor ) {
                throw new RuntimeException("\n*** Error in verifyListDataFlavorEntries()" +
                    "\nAPI Test: List getFlavorsForNative(String nat)" +
                    "\native: " + key +
                    "\nSystemFlavorMap preferred native: " + value.getMimeType() +
                    "\nList first entry: " + prefFlavor.getMimeType() +
                    "\nTest failed because List first entry does not match preferred");
            }
        }
        System.out.println("*** native size = " + hashNatives.size());
    }

    public void verifyListNativeEntries() {
        for (Enumeration e = hashFlavors.keys() ; e.hasMoreElements() ;) {
            DataFlavor key = (DataFlavor)e.nextElement();

            String value = (String)hashFlavors.get(key);

            java.util.List listNatives = flavorMap.getNativesForFlavor(key);
            Vector vectorNatives = new Vector(listNatives);

            String prefNative = (String)vectorNatives.firstElement();
            if ( value != prefNative ) {
                throw new RuntimeException("\n*** Error in verifyListNativeEntries()" +
                    "\nAPI Test: List getNativesForFlavor(DataFlavor flav)" +
                    "\nDataFlavor: " + key.getMimeType() +
                    "\nSystemFlavorMap preferred native: " + value +
                    "\nList first entry: " + prefNative +
                    "\nTest failed because List first entry does not match preferred");
            }
        }
        System.out.println("*** DataFlavor size = " + hashFlavors.size());
    }

    public void verifyListAllNativeEntries(java.util.List listNatives) {

        HashSet hashSetMap = new HashSet(mapNatives.keySet());
        HashSet hashSetList = new HashSet(listNatives);

        System.out.println("*** hashSetMap size = " + hashSetMap.size());
        System.out.println("*** hashSetList size = " + hashSetList.size());

        if (!hashSetMap.equals(hashSetList)) {
            throw new RuntimeException("\n*** Error in verifyListAllNativeEntries()" +
                "\nAPI Test: List getNativesForFlavor(null)" +
                "\nTest failed because the returned List does not exactly" +
                "\nmatch objects returned from SystemFlavorMap.");
        }
    }

    public void verifyListAllDataFlavorEntries(java.util.List listFlavors) {

        HashSet hashSetMap = new HashSet(mapFlavors.keySet());
        HashSet hashSetList = new HashSet(listFlavors);

        System.out.println("*** hashSetMap size = " + hashSetMap.size());
        System.out.println("*** hashSetList size = " + hashSetList.size());

        if (!hashSetMap.equals(hashSetList)) {
            throw new RuntimeException("\n*** Error in verifyListAllDataFlavorEntries()" +
                "\nAPI Test: List getFlavorsForNative(null)" +
                "\nTest failed because the returned List does not exactly" +
                "\nmatch objects returned from SystemFlavorMap.");
        }
    }
}
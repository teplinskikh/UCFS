/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary test International Decimal Format API
 * @run junit IntlTestDecimalFormatAPI
 */
/*
(C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
(C) Copyright IBM Corp. 1996, 1997 - All Rights Reserved

  The original version of this source code and documentation is copyrighted and
owned by Taligent, Inc., a wholly-owned subsidiary of IBM. These materials are
provided under terms of a License Agreement between Taligent and Sun. This
technology is protected by multiple US and International patents. This notice and
attribution to Taligent may not be removed.
  Taligent is a registered trademark of Taligent, Inc.
*/

import java.text.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class IntlTestDecimalFormatAPI
{
    @Test
    public void TestAPI()
    {
        Locale reservedLocale = Locale.getDefault();
        try {
            System.out.println("DecimalFormat API test---"); System.out.println("");
            Locale.setDefault(Locale.ENGLISH);


            System.out.println("Testing DecimalFormat constructors");

            DecimalFormat def = new DecimalFormat();

            final String pattern = new String("#,##0.# FF");
            DecimalFormat pat = null;
            try {
                pat = new DecimalFormat(pattern);
            }
            catch (IllegalArgumentException e) {
                fail("ERROR: Could not create DecimalFormat (pattern)");
            }

            DecimalFormatSymbols symbols =
                    new DecimalFormatSymbols(Locale.FRENCH);

            DecimalFormat cust1 = new DecimalFormat(pattern, symbols);


            System.out.println("Testing clone() and equality operators");

            Format clone = (Format) def.clone();
            if( ! def.equals(clone)) {
                fail("ERROR: Clone() failed");
            }


            System.out.println("Testing various format() methods");

            final double d = -10456.00370000000000; 
            final long l = 100000000;
            System.out.println("" + d + " is the double value");

            StringBuffer res1 = new StringBuffer();
            StringBuffer res2 = new StringBuffer();
            StringBuffer res3 = new StringBuffer();
            StringBuffer res4 = new StringBuffer();
            FieldPosition pos1 = new FieldPosition(0);
            FieldPosition pos2 = new FieldPosition(0);
            FieldPosition pos3 = new FieldPosition(0);
            FieldPosition pos4 = new FieldPosition(0);

            res1 = def.format(d, res1, pos1);
            System.out.println("" + d + " formatted to " + res1);

            res2 = pat.format(l, res2, pos2);
            System.out.println("" + l + " formatted to " + res2);

            res3 = cust1.format(d, res3, pos3);
            System.out.println("" + d + " formatted to " + res3);

            res4 = cust1.format(l, res4, pos4);
            System.out.println("" + l + " formatted to " + res4);


            System.out.println("Testing parse()");

            String text = new String("-10,456.0037");
            ParsePosition pos = new ParsePosition(0);
            String patt = new String("#,##0.#");
            pat.applyPattern(patt);
            double d2 = pat.parse(text, pos).doubleValue();
            if(d2 != d) {
                fail("ERROR: Roundtrip failed (via parse(" +
                    d2 + " != " + d + ")) for " + text);
            }
            System.out.println(text + " parsed into " + (long) d2);


            System.out.println("Testing getters and setters");

            final DecimalFormatSymbols syms = pat.getDecimalFormatSymbols();
            def.setDecimalFormatSymbols(syms);
            if(!pat.getDecimalFormatSymbols().equals(
                    def.getDecimalFormatSymbols())) {
                fail("ERROR: set DecimalFormatSymbols() failed");
            }

            String posPrefix;
            pat.setPositivePrefix("+");
            posPrefix = pat.getPositivePrefix();
            System.out.println("Positive prefix (should be +): " + posPrefix);
            if(posPrefix != "+") {
                fail("ERROR: setPositivePrefix() failed");
            }

            String negPrefix;
            pat.setNegativePrefix("-");
            negPrefix = pat.getNegativePrefix();
            System.out.println("Negative prefix (should be -): " + negPrefix);
            if(negPrefix != "-") {
                fail("ERROR: setNegativePrefix() failed");
            }

            String posSuffix;
            pat.setPositiveSuffix("_");
            posSuffix = pat.getPositiveSuffix();
            System.out.println("Positive suffix (should be _): " + posSuffix);
            if(posSuffix != "_") {
                fail("ERROR: setPositiveSuffix() failed");
            }

            String negSuffix;
            pat.setNegativeSuffix("~");
            negSuffix = pat.getNegativeSuffix();
            System.out.println("Negative suffix (should be ~): " + negSuffix);
            if(negSuffix != "~") {
                fail("ERROR: setNegativeSuffix() failed");
            }

            long multiplier = 0;
            pat.setMultiplier(8);
            multiplier = pat.getMultiplier();
            System.out.println("Multiplier (should be 8): " + multiplier);
            if(multiplier != 8) {
                fail("ERROR: setMultiplier() failed");
            }

            int groupingSize = 0;
            pat.setGroupingSize(2);
            groupingSize = pat.getGroupingSize();
            System.out.println("Grouping size (should be 2): " + (long) groupingSize);
            if(groupingSize != 2) {
                fail("ERROR: setGroupingSize() failed");
            }

            pat.setDecimalSeparatorAlwaysShown(true);
            boolean tf = pat.isDecimalSeparatorAlwaysShown();
            System.out.println("DecimalSeparatorIsAlwaysShown (should be true) is " +
                                                (tf ? "true" : "false"));
            if(tf != true) {
                fail("ERROR: setDecimalSeparatorAlwaysShown() failed");
            }

            String funkyPat;
            funkyPat = pat.toPattern();
            System.out.println("Pattern is " + funkyPat);

            String locPat;
            locPat = pat.toLocalizedPattern();
            System.out.println("Localized pattern is " + locPat);


            System.out.println("Testing applyPattern()");

            String p1 = new String("#,##0.0#;(#,##0.0#)");
            System.out.println("Applying pattern " + p1);
            pat.applyPattern(p1);
            String s2;
            s2 = pat.toPattern();
            System.out.println("Extracted pattern is " + s2);
            if( ! s2.equals(p1) ) {
                fail("ERROR: toPattern() result did not match " +
                        "pattern applied");
            }

            String p2 = new String("#,##0.0# FF;(#,##0.0# FF)");
            System.out.println("Applying pattern " + p2);
            pat.applyLocalizedPattern(p2);
            String s3;
            s3 = pat.toLocalizedPattern();
            System.out.println("Extracted pattern is " + s3);
            if( ! s3.equals(p2) ) {
                fail("ERROR: toLocalizedPattern() result did not match " +
                        "pattern applied");
            }




        } finally {
            Locale.setDefault(reservedLocale);
        }
    }
}
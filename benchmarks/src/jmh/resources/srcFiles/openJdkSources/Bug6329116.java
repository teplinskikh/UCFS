/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6329116 6756569 6757131 6758988 6764308 6796489 6834474 6609737 6507067
 *      7039469 7090843 7103108 7103405 7158483 8008577 8059206 8064560 8072042
 *      8077685 8151876 8166875 8169191 8170316 8176044 8174269
 * @summary Make sure that timezone short display names are identical to Olson's data.
 * @run junit Bug6329116
 */

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class Bug6329116 {


    static Locale[] locales = {Locale.US};
    static String[] timezones = TimeZone.getAvailableIDs();

    @Test
    public void bug6329116() throws IOException {
        boolean err = false;

        HashMap<String, String> aliasTable = new HashMap<>();
        HashSet<String> timezoneTable = new HashSet<>();
        for (String t : timezones) {
            timezoneTable.add(t);
        }

        String line, key, value;
        StringTokenizer st;

        try (TextFileReader in = new TextFileReader("aliases.txt")) {
            while ((line = in.readLine()) != null) {
                st = new StringTokenizer(line);
                st.nextToken();
                key = st.nextToken();
                value = st.nextToken();

                if (!value.equals("ROC")) {
                    if (aliasTable.containsKey(key)) {
                        aliasTable.put(key, aliasTable.get(key) + " " + value);
                    } else {
                        aliasTable.put(key, value);
                    }
                }
            }
        }

        try (TextFileReader in = new TextFileReader("displaynames.txt")) {
            String timezoneID, expected, expected_DST, got;
            String[] aliases, tzs;
            TimeZone tz;
            while ((line = in.readLine()) != null) {
                st = new StringTokenizer(line);
                timezoneID = st.nextToken();
                expected = st.nextToken();
                if (st.hasMoreTokens()) {
                    expected_DST = st.nextToken();
                } else {
                    expected_DST = null;
                }

                if (aliasTable.containsKey(timezoneID)) {
                    aliases = aliasTable.get(timezoneID).split(" ");
                    tzs = new String[1 + aliases.length];
                    System.arraycopy(aliases, 0, tzs, 1, aliases.length);
                    aliasTable.remove(timezoneID);
                } else {
                    tzs = new String[1];
                }
                tzs[0] = timezoneID;

                for (int j = 0; j < tzs.length; j++) {
                    tz = TimeZone.getTimeZone(tzs[j]);

                    if (!tzs[j].equals(tz.getID())) {
                        System.err.println(tzs[j] + " may not be a valid Timezone ID and \"" + tz.getID() + "\" was returned. Please check it.");
                        err = true;
                    }

                    timezoneTable.remove(tzs[j]);

                    for (int i = 0; i < locales.length; i++) {
                        got = tz.getDisplayName(false, TimeZone.SHORT, locales[i]);
                        if (!expected.equals(got) &&
                            !expected.startsWith(got + "/") &&
                            !expected.endsWith("/" + got)) {
                            if (useLocalizedShortDisplayName(tz, locales[i], got, false)) {
/*
                                System.out.println(tzs[j] +
                                                   ((j > 0) ? "(Alias of \"" + tzs[0] + "\")" : "") +
                                                   " seems to use a localized short display name" +
                                                   ": original: " + expected +
                                                   ": got: " + got + " for non-DST in " +
                                                   locales[i] + " locale.");
*/
                            } else {
                                System.err.println(tzs[j] +
                                                   ((j > 0) ? "(Alias of \"" + tzs[0] + "\")" : "") +
                                                   ": expected: " + expected +
                                                   ": got: " + got + " for non-DST in " +
                                                   locales[i] + " locale.");
                                err = true;
                            }
                        }

                        got = tz.getDisplayName(true, TimeZone.SHORT, locales[i]);
                        if (expected_DST != null) {
                            if (!expected_DST.equals(got) &&
                                !expected_DST.startsWith(got + "/") &&
                                !expected_DST.endsWith("/" + got)) {
                                if (tzs[j].equals("Europe/London") &&
                                    locales[i].equals(new Locale("en", "IE"))) {
                                    continue;
                                } else if (useLocalizedShortDisplayName(tz, locales[i], got, true)) {
/*
                                System.out.println(tzs[j] +
                                    ((j > 0) ? "(Alias of \"" + tzs[0] + "\")" : "") +
                                    " seems to use a localized short display name" +
                                    ": original: " + expected_DST +
                                    ": got: " + got + " for DST in " +
                                    locales[i] + " locale.");
*/
                                    continue;
                                }
                                System.err.println(tzs[j] +
                                                   ((j > 0) ? "(Alias of \"" + tzs[0] + "\")" : "") +
                                                   ": expected: " + expected_DST +
                                                   ": got: " + got + " for DST in " +
                                                   locales[i] + " locale.");
                                err = true;
                            }
                        } else {
                            if (!expected.equals(got) &&
                                !expected.startsWith(got + "/") &&
                                !expected.endsWith("/" + got)) {
/*
                                System.out.println("## " + tzs[j] +
                                                   ((j > 0) ? "(Alias of \"" + tzs[0] + "\")" : "") +
                                                   ": expected: " + expected +
                                                   ": got: " + got + " for DST in " +
                                                   locales[i] + " locale.");
*/
                            }
                        }
                    }
                }
            }
        }

        if (!timezoneTable.isEmpty()) {
            System.out.println("# Timezone(s) valid in JRE but untested in this test program:");
            Iterator<String> it = timezoneTable.iterator();
            while (it.hasNext()) {
                System.out.println(it.next());
            }
            System.out.println();
        }

        if (!aliasTable.isEmpty()) {
            System.out.println("# Timezone(s) exists in Olson's data as Link but unused in JRE:");
            for (Map.Entry<String, String> entry : aliasTable.entrySet()) {
                System.out.println(entry);
            }
        }

        if (err) {
            fail("At least one timezone display name is incorrect.");
        }
    }

    static boolean useLocalizedShortDisplayName(TimeZone tz,
                                               Locale locale,
                                               String got,
                                               boolean inDST) {

        if (tz.getDisplayName(Locale.ENGLISH).startsWith("GMT+") || tz.getDisplayName(Locale.ENGLISH).startsWith("GMT-")) {
            return tz.getDisplayName().substring(0, 3).equals(got.substring(0, 3));
        }

        return false;
    }

}
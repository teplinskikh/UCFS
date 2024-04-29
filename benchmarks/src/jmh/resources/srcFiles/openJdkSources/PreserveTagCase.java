/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8032842 8175539
 * @summary Checks that the filterTags() and lookup() methods
 *          preserve the case of matching language tag(s).
 *          Before 8032842 fix these methods return the matching
 *          language tag(s) in lowercase.
 *          Also, checks the filterTags() to return only unique
 *          (ignoring case considerations) matching tags.
 * @run junit PreserveTagCase
 */

import java.util.List;
import java.util.Locale;
import java.util.Locale.FilteringMode;
import java.util.Locale.LanguageRange;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PreserveTagCase {

    /**
     * This test ensures that Locale.filterTags() preserves the case of matching
     * language tag(s).
     */
    @ParameterizedTest
    @MethodSource("filterProvider")
    public static void testFilterTags(String ranges, List<String> tags,
                                  List<String> expected, FilteringMode mode) {
        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        List<String> actual = Locale.filterTags(priorityList, tags, mode);
        assertEquals(actual, expected, String.format("[filterTags() failed for " +
                "the language range: %s, Expected: %s, Found: %s]", ranges, expected, actual));
    }

    /**
     * This test ensures that Locale.lookupTag() preserves the case of matching
     * language tag(s).
     */
    @ParameterizedTest
    @MethodSource("lookupProvider")
    public static void testLookupTag(String ranges, List<String> tags,
                                  String expected) {
        List<LanguageRange> priorityList = LanguageRange.parse(ranges);
        String actual = Locale.lookupTag(priorityList, tags);
        assertEquals(actual, expected, String.format("[lookupTags() failed for " +
                "the language range: %s, Expected: %s, Found: %s]", ranges, expected, actual));
    }

    private static Stream<Arguments> filterProvider() {
        return Stream.of(
                Arguments.of("*",
                        List.of("de-CH", "hi-in", "En-GB", "ja-Latn-JP", "JA-JP", "en-GB"),
                        List.of("de-CH", "hi-in", "En-GB", "ja-Latn-JP", "JA-JP"),
                        FilteringMode.AUTOSELECT_FILTERING),
                Arguments.of("mtm-RU, en-GB",
                        List.of("En-Gb", "mTm-RU", "en-US", "en-latn", "en-GB"),
                        List.of("mTm-RU", "En-Gb"),
                        FilteringMode.AUTOSELECT_FILTERING),
                Arguments.of("*",
                        List.of("de-CH", "hi-in", "En-GB", "hi-IN", "ja-Latn-JP", "JA-JP"),
                        List.of("de-CH", "hi-in", "En-GB", "ja-Latn-JP", "JA-JP"),
                        FilteringMode.EXTENDED_FILTERING),
                Arguments.of("*-ch;q=0.5, *-Latn;q=0.4",
                        List.of("fr-CH", "de-Ch", "en-latn", "en-US", "en-Latn"),
                        List.of("fr-CH", "de-Ch", "en-latn"),
                        FilteringMode.EXTENDED_FILTERING)
        );
    }

    private static Stream<Arguments> lookupProvider() {
        return Stream.of(
                Arguments.of("*-ch;q=0.5", List.of("en", "fR-cH"), "fR-cH"),
                Arguments.of("*-Latn;q=0.4", List.of("en", "fR-LATn"), "fR-LATn")
        );
    }
}
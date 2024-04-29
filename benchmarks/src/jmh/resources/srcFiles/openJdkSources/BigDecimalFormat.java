/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4018937 8008577 8174269
 * @summary Confirm that methods which are newly added to support BigDecimal and BigInteger work as expected.
 * @run junit BigDecimalFormat
 */

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class BigDecimalFormat {

    static final String nonsep_int =
        "123456789012345678901234567890123456789012345678901234567890" +
        "123456789012345678901234567890123456789012345678901234567890" +
        "123456789012345678901234567890123456789012345678901234567890" +
        "123456789012345678901234567890123456789012345678901234567890" +
        "123456789012345678901234567890123456789012345678901234567890" +
        "123456789012345678901234567890123456789012345678901234567890";

    static final String sep_int =
        "123,456,789,012,345,678,901,234,567,890," +
        "123,456,789,012,345,678,901,234,567,890," +
        "123,456,789,012,345,678,901,234,567,890," +
        "123,456,789,012,345,678,901,234,567,890," +
        "123,456,789,012,345,678,901,234,567,890," +
        "123,456,789,012,345,678,901,234,567,890," +
        "123,456,789,012,345,678,901,234,567,890," +
        "123,456,789,012,345,678,901,234,567,890," +
        "123,456,789,012,345,678,901,234,567,890," +
        "123,456,789,012,345,678,901,234,567,890," +
        "123,456,789,012,345,678,901,234,567,890," +
        "123,456,789,012,345,678,901,234,567,890";

    static final String nonsep_zero =
        "000000000000000000000000000000000000000000000000000000000000" +
        "000000000000000000000000000000000000000000000000000000000000" +
        "000000000000000000000000000000000000000000000000000000000000" +
        "000000000000000000000000000000000000000000000000000000000000" +
        "000000000000000000000000000000000000000000000000000000000000" +
        "000000000000000000000000000000000000000000000000000000000000";

    static final String sep_zero =
        "000,000,000,000,000,000,000,000,000,000," +
        "000,000,000,000,000,000,000,000,000,000," +
        "000,000,000,000,000,000,000,000,000,000," +
        "000,000,000,000,000,000,000,000,000,000," +
        "000,000,000,000,000,000,000,000,000,000," +
        "000,000,000,000,000,000,000,000,000,000," +
        "000,000,000,000,000,000,000,000,000,000," +
        "000,000,000,000,000,000,000,000,000,000," +
        "000,000,000,000,000,000,000,000,000,000," +
        "000,000,000,000,000,000,000,000,000,000," +
        "000,000,000,000,000,000,000,000,000,000," +
        "000,000,000,000,000,000,000,000,000,000";

    static final String fra =
        "012345678901234567890123456789012345678901234567890123456789" +
        "012345678901234567890123456789012345678901234567890123456789" +
        "012345678901234567890123456789012345678901234567890123456789" +
        "012345678901234567890123456789012345678901234567890123456789" +
        "012345678901234567890123456789012345678901234567890123456789" +
        "012345678901234567890123456789012345678901234567890123456789";


    StringBuffer formatted = new StringBuffer(1000);
    FieldPosition fp;

    /**
     * Test for normal big numbers which have the fraction part
     */
    @Test
    void test_Format_in_NumberFormat_BigDecimal() {
        String from, to;

        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        ((DecimalFormat)nf).applyPattern("#,##0.###");
        setDigits(nf, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 0);

        formatted.setLength(0);
        from = "0." + nonsep_zero + "123456789";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, from, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        fp = new FieldPosition(NumberFormat.Field.SIGN);
        formatted.setLength(0);
        from = "-0." + nonsep_zero + "123456789";
        nf.format(new BigDecimal(from), formatted, fp);
        checkFormat(from, formatted, from, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 0, 1);

        /* ------------------------------------------------------------------ */

        fp = new FieldPosition(DecimalFormat.INTEGER_FIELD);
        formatted.setLength(0);
        from = nonsep_int + "." + fra;
        to   = sep_int    + "." + fra;
        nf.format(new BigDecimal(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 0, 479);

        /* ------------------------------------------------------------------ */

        fp = new FieldPosition(DecimalFormat.FRACTION_FIELD);
        formatted.setLength(0);
        from = "-" + nonsep_int + "." + fra;
        to   = "-" + sep_int    + "." + fra;
        nf.format(new BigDecimal(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 481, 841);

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = nonsep_int + nonsep_zero + "." + nonsep_zero + fra;
        to   = sep_int + "," + sep_zero + "." + nonsep_zero + fra;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-" + nonsep_int + nonsep_zero + "." + nonsep_zero + fra;
        to   = "-" + sep_int + "," + sep_zero + "." + nonsep_zero + fra;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = nonsep_int + nonsep_zero;
        to   = sep_int + "," + sep_zero;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-" + nonsep_int + nonsep_zero;
        to   = "-" + sep_int + "," + sep_zero;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = nonsep_int + nonsep_zero + "." + nonsep_zero;
        to   = sep_int + "," + sep_zero;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-" + nonsep_int + nonsep_zero + "." + nonsep_zero;
        to   = "-" + sep_int + "," + sep_zero;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = nonsep_zero;
        to   = "0";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-" + nonsep_zero;
        to   = "0";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = nonsep_zero + "1234";
        to   = "1,234";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        fp = new FieldPosition(NumberFormat.Field.GROUPING_SEPARATOR);
        formatted.setLength(0);
        from = "-" + nonsep_zero + "1234";
        to   = "-1,234";
        nf.format(new BigDecimal(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 2, 3);

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = nonsep_zero + "." + nonsep_zero;
        to   = "0";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("#,##0.0");
        setDigits(nf, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 1);

        formatted.setLength(0);
        from = "-" + nonsep_zero + "." + nonsep_zero;
        to   = "0.0";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = nonsep_int + "." + fra + nonsep_zero;
        to   = sep_int    + "." + fra;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-" + nonsep_int + "." + fra + nonsep_zero;
        to   = "-" + sep_int    + "." + fra;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("0.###E0");
        setDigits(nf, 1, 1, Integer.MAX_VALUE, 0);

        fp = new FieldPosition(NumberFormat.Field.EXPONENT);
        formatted.setLength(0);
        from = "1"  + nonsep_int + "." + fra;
        to   = "1." + nonsep_int       + fra + "E360";
        nf.format(new BigDecimal(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 723, 726);

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-1"  + nonsep_int + "." + fra;
        to   = "-1." + nonsep_int       + fra + "E360";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("0.###E0");
        setDigits(nf, 1, 1, Integer.MAX_VALUE, 0);

        formatted.setLength(0);
        from = "0." + nonsep_zero + "1" + fra;
        to   = "1."                  + fra + "E-361";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-0." + nonsep_zero + "1"  + fra;
        to   = "-1."                  + fra + "E-361";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "1"  + nonsep_int + "." + fra + nonsep_zero;
        to   = "1." + nonsep_int       + fra + "E360";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        fp = new FieldPosition(NumberFormat.Field.EXPONENT_SYMBOL);
        formatted.setLength(0);
        from = "-1"  + nonsep_int + "." + fra + nonsep_zero;
        to   = "-1." + nonsep_int       + fra + "E360";
        nf.format(new BigDecimal(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 723, 724);

        /* ------------------------------------------------------------------ */

        fp = new FieldPosition(NumberFormat.Field.EXPONENT_SIGN);
        formatted.setLength(0);
        from = "0." + nonsep_zero + "1" + fra + nonsep_zero;
        to   = "1."                  + fra + "E-361";
        nf.format(new BigDecimal(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 363, 364);

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-0." + nonsep_zero + "1"  + fra + nonsep_zero;
        to   = "-1."                  + fra + "E-361";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted = new StringBuffer("ABC");
        from = "1"     + nonsep_int + "."  + fra;
        to   = "ABC1." + nonsep_int     + fra + "E360";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        fp = new FieldPosition(NumberFormat.Field.DECIMAL_SEPARATOR);
        formatted = new StringBuffer("ABC");
        from = "-1"     + nonsep_int + "."  + fra;
        to   = "ABC-1." + nonsep_int    + fra + "E360";
        nf.format(new BigDecimal(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 5, 6);

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("#,##0.###");
        setDigits(nf, Integer.MAX_VALUE, 1, 726, 0);

        formatted.setLength(0);
        from = "0." + nonsep_zero + fra + fra;
        to   = "0." + nonsep_zero + fra + "012346";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        ((DecimalFormat)nf).applyPattern("#,##0.###");
        setDigits(nf, Integer.MAX_VALUE, 1, 723, 0);

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-0." + nonsep_zero + fra + fra;
        to   = "-0." + nonsep_zero + fra + "012";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("00000.###E0");
        setDigits(nf, 5, 5, 370, 0);

        formatted.setLength(0);
        from = "1234567890." + fra + "0123456789";
        to   = "12345.67890" + fra + "01235E5";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("0.###E0");
        setDigits(nf, 1, 1, 364, 0);

        formatted.setLength(0);
        from = "-0." + nonsep_zero + "1" + fra + "0123456789";
        to   = "-1."                 + fra + "0123E-361";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("0.###E0");
        setDigits(nf, 1, 1, 366, 0);

        formatted.setLength(0);
        from = "1"  + fra + "0123456789";
        to   = "1." + fra + "012346E370";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("0.###E0");
        setDigits(nf, 1, 1, 363, 0);

        formatted.setLength(0);
        from = "-1"  + fra + "0123456789";
        to   = "-1." + fra + "012E370";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("#,##0.###");
        setDigits(nf, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 720);

        formatted.setLength(0);
        from = nonsep_int + nonsep_zero + "." + nonsep_zero + nonsep_zero;
        to   = sep_int + "," + sep_zero + "." + nonsep_zero + nonsep_zero;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-" + nonsep_int + nonsep_zero + "." + nonsep_zero + nonsep_zero;
        to   = "-" + sep_int + "," + sep_zero + "." + nonsep_zero + nonsep_zero;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
    }

    /**
     * Test for normal big numbers which have the fraction part with multiplier
     */
    @Test
    void test_Format_in_NumberFormat_BigDecimal_usingMultiplier() {
        String from, to;

        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        ((DecimalFormat)nf).applyPattern("#,##0.###");
        setDigits(nf, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 0);
        ((DecimalFormat)nf).setMultiplier(250000000);
        ((DecimalFormat)nf).setDecimalSeparatorAlwaysShown(true);

        formatted.setLength(0);
        from = "1"          + nonsep_zero + "." + nonsep_zero;
        to   = "250,000,000," + sep_zero    + ".";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).setDecimalSeparatorAlwaysShown(false);

        formatted.setLength(0);
        from = "-1"         + nonsep_zero + "." + nonsep_zero;
        to   = "-250,000,000," + sep_zero;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("#,##0.###");
        setDigits(nf, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 0);
        ((DecimalFormat)nf).setMultiplier(-250000000);
        ((DecimalFormat)nf).setDecimalSeparatorAlwaysShown(true);

        formatted.setLength(0);
        from = "1"          + nonsep_zero + "." + nonsep_zero;
        to   = "-250,000,000," + sep_zero    + ".";
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).setDecimalSeparatorAlwaysShown(false);

        formatted.setLength(0);
        from = "-1"         + nonsep_zero + "." + nonsep_zero;
        to   = "250,000,000," + sep_zero;
        nf.format(new BigDecimal(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
    }

    /**
     * Test for normal big numbers which don't have the fraction part
     */
    @Test
    void test_Format_in_NumberFormat_BigInteger() {
        String from, to;

        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        if (!(nf instanceof DecimalFormat)) {
            throw new RuntimeException("Couldn't get DecimalFormat instance.");
        }

        ((DecimalFormat)nf).applyPattern("#,##0.###");
        setDigits(nf, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 0);

        formatted.setLength(0);
        from = nonsep_int;
        to   = sep_int;
        nf.format(new BigInteger(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        fp = new FieldPosition(DecimalFormat.INTEGER_FIELD);
        formatted.setLength(0);
        from = "-" + nonsep_int;
        to   = "-" + sep_int;
        nf.format(new BigInteger(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 1, 480);

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = nonsep_zero + nonsep_int;
        to   = sep_int;
        nf.format(new BigInteger(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        fp = new FieldPosition(NumberFormat.Field.SIGN);
        formatted.setLength(0);
        from = "-" + nonsep_zero + nonsep_int;
        to   = "-" + sep_int;
        nf.format(new BigInteger(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 0, 1);

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = nonsep_zero;
        to   = "0";
        nf.format(new BigInteger(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("#,##0.0");
        setDigits(nf, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 1);

        fp = new FieldPosition(NumberFormat.Field.DECIMAL_SEPARATOR);
        formatted.setLength(0);
        from = "-" + nonsep_zero;
        to   = "0.0";
        nf.format(new BigInteger(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 1, 2);

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("0.###E0");
        setDigits(nf, 1, 1, Integer.MAX_VALUE, 0);

        fp = new FieldPosition(NumberFormat.Field.EXPONENT);
        formatted.setLength(0);
        from = "1"  + fra;
        to   = "1." + fra + "E360";
        nf.format(new BigInteger(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 363, 366);

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-1"  + fra;
        to   = "-1." + fra + "E360";
        nf.format(new BigInteger(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("00000.###E0");
        setDigits(nf, 5, 5, Integer.MAX_VALUE, 720);

        fp = new FieldPosition(NumberFormat.Field.EXPONENT);
        formatted.setLength(0);
        from = "12345"  + fra + nonsep_zero;
        to   = "12345." + fra + nonsep_zero + "E720";
        nf.format(new BigInteger(from), formatted, fp);
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        checkFieldPosition(from, fp, 727, 730);

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("00000.###E0");
        setDigits(nf, 5, 5, Integer.MAX_VALUE, 365);

        formatted.setLength(0);
        from = "-1234567890"  + fra;
        to   = "-12345.67890" + fra + "E365";
        nf.format(new BigInteger(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
    }

    /**
     * Test for normal big numbers which don't have the fraction part with
     * multiplier
     */
    @Test
    void test_Format_in_NumberFormat_BigInteger_usingMultiplier() {
        String from, to;

        NumberFormat nf = NumberFormat.getInstance(Locale.US);

        ((DecimalFormat)nf).applyPattern("#,##0.###");
        ((DecimalFormat)nf).setMultiplier(250000000);
        setDigits(nf, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 0);

        formatted.setLength(0);
        from = "1" + nonsep_zero;
        to   = "250,000,000," + sep_zero;
        nf.format(new BigInteger(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-1" + nonsep_zero;
        to   = "-250,000,000," + sep_zero;
        nf.format(new BigInteger(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("#,##0.###");
        ((DecimalFormat)nf).setMultiplier(-250000000);
        setDigits(nf, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 0);

        formatted.setLength(0);
        from = "1" + nonsep_zero;
        to   = "-250,000,000," + sep_zero;
        nf.format(new BigInteger(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        formatted.setLength(0);
        from = "-1" + nonsep_zero;
        to   = "250,000,000," + sep_zero;
        nf.format(new BigInteger(from), formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
    }

    /**
     * Test for normal Long numbers when maximum and minimum digits are
     * specified
     */
    @Test
    void test_Format_in_NumberFormat_Long_checkDigits() {
        String from, to;

        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        if (!(nf instanceof DecimalFormat)) {
            throw new RuntimeException("Couldn't get DecimalFormat instance.");
        }

        ((DecimalFormat)nf).applyPattern("#,##0.###");
        setDigits(nf, Integer.MAX_VALUE, 360, Integer.MAX_VALUE, 0);

        formatted.setLength(0);
        from = "123456789";
        to   = sep_zero.substring(0, 399) + ",123,456,789";
        nf.format(123456789L, formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("##0.###");
        ((DecimalFormat)nf).setMultiplier(-1);
        setDigits(nf, Integer.MAX_VALUE, 360, Integer.MAX_VALUE, 360);

        formatted.setLength(0);
        from = "123456789";
        to   = "-" + nonsep_zero.substring(0, 300) + "123456789." +
               nonsep_zero.substring(0, 340);
        nf.format(123456789L, formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("#,##0.###");
        ((DecimalFormat)nf).setMultiplier(Integer.MAX_VALUE);
        setDigits(nf, Integer.MAX_VALUE, 360, Integer.MAX_VALUE, 0);

        formatted.setLength(0);
        from = Long.toString(Long.MAX_VALUE);
        to   = sep_zero.substring(0, 373) +
               "19,807,040,619,342,712,359,383,728,129";
        nf.format(Long.MAX_VALUE, formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("0.###E0");
        ((DecimalFormat)nf).setMultiplier(Integer.MIN_VALUE);
        setDigits(nf, 1, 1, Integer.MAX_VALUE, 360);

        formatted.setLength(0);
        from = Long.toString(Long.MAX_VALUE);
        to   = "-1.9807040628566084396238503936" +
               nonsep_zero.substring(0, 312) + "E28";
        nf.format(Long.MAX_VALUE, formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("##0.###E0");
        ((DecimalFormat)nf).setMultiplier(Integer.MAX_VALUE);
        setDigits(nf, Integer.MAX_VALUE, 360, Integer.MAX_VALUE, 360);

        formatted.setLength(0);
        from = Long.toString(Long.MIN_VALUE);
        to   = "-19807040619342712361531211776" +
               nonsep_zero.substring(0, 280) + "." +
               nonsep_zero.substring(0, 340) + "E-280";
        nf.format(Long.MIN_VALUE, formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());

        /* ------------------------------------------------------------------ */

        ((DecimalFormat)nf).applyPattern("#,##0.###");
        ((DecimalFormat)nf).setMultiplier(Integer.MIN_VALUE);
        setDigits(nf, Integer.MAX_VALUE, 360, Integer.MAX_VALUE, 360);

        formatted.setLength(0);
        from = Long.toString(Long.MIN_VALUE);
        to   = sep_zero.substring(0, 373) +
               "19,807,040,628,566,084,398,385,987,584." +
               nonsep_zero.substring(0, 340);
        nf.format(Long.MIN_VALUE, formatted, new FieldPosition(0));
        checkFormat(from, formatted, to, ((DecimalFormat)nf).getMultiplier());
    }

    /**
     * Test for special numbers
     *    Double.NaN
     *    Double.POSITIVE_INFINITY
     *    Double.NEGATIVE_INFINITY
     */
    @Test
    void test_Format_in_NumberFormat_SpecialNumber() {
        String from, to;

        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        if (!(nf instanceof DecimalFormat)) {
            throw new RuntimeException("Couldn't get DecimalFormat instance.");
        }

        ((DecimalFormat)nf).applyPattern("#,##0.###");
        setDigits(nf, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 0);

        double[] numbers = {
            -0.0, 0.0, Double.NaN,
            Double.POSITIVE_INFINITY, 5.1, 5.0,
            Double.NEGATIVE_INFINITY, -5.1, -5.0,
        };
        int multipliers[] = {0, 5, -5};
        String[][] expected = {
            {"-0", "0", "NaN", "NaN", "0", "0", "NaN", "-0", "-0"},
            {"-0", "0", "NaN", "\u221e", "25.5", "25", "-\u221e", "-25.5",
             "-25"},
            {"0", "-0", "NaN", "-\u221e", "-25.5", "-25", "\u221e", "25.5",
             "25"},
        };

        for (int i = 0; i < multipliers.length; i++) {
            ((DecimalFormat)nf).setMultiplier(multipliers[i]);
            for (int j = 0; j < numbers.length; j++) {
                formatted.setLength(0);
                from = String.valueOf(numbers[j]);
                nf.format(numbers[j], formatted, new FieldPosition(0));
                checkFormat(from, formatted, expected[i][j],
                            ((DecimalFormat)nf).getMultiplier());
            }
        }
    }

    /**
     * Test for Long.MIN_VALUE
     *   (Formatting Long.MIN_VALUE w/ multiplier=-1 used to return a wrong
     *    number.)
     */
    @Test
    void test_Format_in_NumberFormat_Other() {
        String from, to;

        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        if (!(nf instanceof DecimalFormat)) {
            throw new RuntimeException("Couldn't get DecimalFormat instance.");
        }

        long[] numbers = {
            Long.MIN_VALUE,
        };
        int multipliers[] = {1, -1};
        String[][] expected = {
            {"-9,223,372,036,854,775,808"},     
            {"9,223,372,036,854,775,808"},      
        };

        for (int i = 0; i < multipliers.length; i++) {
            ((DecimalFormat)nf).setMultiplier(multipliers[i]);
            for (int j = 0; j < numbers.length; j++) {
                formatted.setLength(0);
                from = String.valueOf(numbers[j]);
                nf.format(numbers[j], formatted, new FieldPosition(0));
                checkFormat(from, formatted, expected[i][j],
                            ((DecimalFormat)nf).getMultiplier());
            }
        }
    }

    /**
     * Test for MessageFormat
     */
    @Test
    void test_Format_in_MessageFormat() {
        MessageFormat mf = new MessageFormat(
            "  {0, number}\n" +
            "  {0, number, integer}\n" +
            "  {0, number, currency}\n" +
            "  {0, number, percent}\n" +
            "  {0, number,0.###########E0}\n" +

            "  {1, number}\n" +
            "  {1, number, integer}\n" +
            "  {1, number, currency}\n" +
            "  {1, number, percent}\n" +
            "  {1, number,0.#######E0}\n",
            Locale.forLanguageTag("en-US-u-cf-account")
        );
        Object[] testArgs = {
            new BigInteger("9876543210987654321098765432109876543210"),
            new BigDecimal("-12345678901234567890.98765432109876543210987654321"),
        };
        String expected =
            "  9,876,543,210,987,654,321,098,765,432,109,876,543,210\n" +
            "  9,876,543,210,987,654,321,098,765,432,109,876,543,210\n" +
            "  $9,876,543,210,987,654,321,098,765,432,109,876,543,210.00\n" +
            "  987,654,321,098,765,432,109,876,543,210,987,654,321,000%\n" +
            "  9.87654321099E39\n" +

            "  -12,345,678,901,234,567,890.988\n" +
            "  -12,345,678,901,234,567,891\n" +
            "  ($12,345,678,901,234,567,890.99)\n" +
            "  -1,234,567,890,123,456,789,099%\n" +
            "  -1.2345679E19\n"
        ;

        if (!expected.equals(mf.format(testArgs))) {
            fail("Wrong format.\n      got:\n" + mf.format(testArgs) +
                  "     expected:\n" + expected);
        }
    }

    private void setDigits(NumberFormat nf,
                           int i_max, int i_min, int f_max, int f_min) {
        nf.setMaximumIntegerDigits(i_max);
        nf.setMinimumIntegerDigits(i_min);
        nf.setMaximumFractionDigits(f_max);
        nf.setMinimumFractionDigits(f_min);
    }

    private void checkFormat(String orig, StringBuffer got, String expected,
                             int multiplier) {
        if (!expected.equals(new String(got))) {
            fail("Formatting... failed." +
                  "\n   original:   " + orig +
                  "\n   multiplier: " + multiplier +
                  "\n   formatted:  " + got +
                  "\n   expected:   " + expected + "\n");
        }
    }

    private void checkFieldPosition(String orig, FieldPosition fp, int begin,
                                    int end) {
        int position;

        if ((position = fp.getBeginIndex()) != begin) {
            fail("Formatting... wrong Begin index returned for " +
                  fp.getFieldAttribute() + "." +
                  "\n   original: " + orig +
                  "\n   got:      " + position +
                  "\n   expected: " + begin + "\n");
        }
        if ((position = fp.getEndIndex()) != end) {
            fail("Formatting... wrong End index returned for " +
                  fp.getFieldAttribute() + "." +
                  "\n   original: " + orig +
                  "\n   got:      " + position +
                  "\n   expected: " + end + "\n");
        }
    }
}
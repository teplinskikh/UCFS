/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.nio.cs.ext;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import sun.nio.cs.DelegatableDecoder;
import sun.nio.cs.HistoricallyNamedCharset;
import sun.nio.cs.*;
import static java.lang.Character.UnicodeBlock;

import jdk.internal.util.OperatingSystem;

public class JISAutoDetect
    extends Charset
    implements HistoricallyNamedCharset
{

    private static final int EUCJP_MASK       = 0x01;
    private static final int SJIS2B_MASK      = 0x02;
    private static final int SJIS1B_MASK      = 0x04;
    private static final int EUCJP_KANA1_MASK = 0x08;
    private static final int EUCJP_KANA2_MASK = 0x10;

    public JISAutoDetect() {
        super("x-JISAutoDetect", ExtendedCharsets.aliasesFor("x-JISAutoDetect"));
    }

    public boolean contains(Charset cs) {
        return ((cs.name().equals("US-ASCII"))
                || (cs instanceof SJIS)
                || (cs instanceof EUC_JP)
                || (cs instanceof ISO2022_JP)
                || (cs instanceof JISAutoDetect));
    }

    public boolean canEncode() {
        return false;
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public String historicalName() {
        return "JISAutoDetect";
    }

    public CharsetEncoder newEncoder() {
        throw new UnsupportedOperationException();
    }

    private static boolean looksLikeJapanese(CharBuffer cb) {
        int hiragana = 0;       
        int katakana = 0;       
        while (cb.hasRemaining()) {
            char c = cb.get();
            if (0x3040 <= c && c <= 0x309f && ++hiragana > 1) return true;
            if (0xff65 <= c && c <= 0xff9f && ++katakana > 1) return true;
        }
        return false;
    }

    private static class Decoder extends CharsetDecoder {

        private static final String SJISName = getSJISName();
        private static final String EUCJPName = "EUC_JP";
        private DelegatableDecoder detectedDecoder = null;

        public Decoder(Charset cs) {
            super(cs, 0.5f, 1.0f);
        }

        private static boolean isPlainASCII(byte b) {
            return b >= 0 && b != 0x1b;
        }

        private static void copyLeadingASCII(ByteBuffer src, CharBuffer dst) {
            int start = src.position();
            int limit = start + Math.min(src.remaining(), dst.remaining());
            int p;
            byte b;
            for (p = start; p < limit && isPlainASCII(b = src.get(p)); p++)
                dst.put((char)(b & 0xff));
            src.position(p);
        }

        private CoderResult decodeLoop(DelegatableDecoder decoder,
                                       ByteBuffer src, CharBuffer dst) {
            ((CharsetDecoder)decoder).reset();
            detectedDecoder = decoder;
            return detectedDecoder.decodeLoop(src, dst);
        }

        protected CoderResult decodeLoop(ByteBuffer src, CharBuffer dst) {
            if (detectedDecoder == null) {
                copyLeadingASCII(src, dst);

                if (! src.hasRemaining())
                    return CoderResult.UNDERFLOW;
                if (!dst.hasRemaining() &&
                    isPlainASCII(src.get(src.position())))
                    return CoderResult.OVERFLOW;

                int cbufsiz = (int)(src.limit() * (double)maxCharsPerByte());
                CharBuffer sandbox = CharBuffer.allocate(cbufsiz);

                Charset cs2022 = Charset.forName("ISO-2022-JP");
                DelegatableDecoder dd2022
                    = (DelegatableDecoder) cs2022.newDecoder();
                ByteBuffer src2022 = src.asReadOnlyBuffer();
                CoderResult res2022 = dd2022.decodeLoop(src2022, sandbox);
                if (! res2022.isError())
                    return decodeLoop(dd2022, src, dst);

                Charset csEUCJ = Charset.forName(EUCJPName);
                Charset csSJIS = Charset.forName(SJISName);

                DelegatableDecoder ddEUCJ
                    = (DelegatableDecoder) csEUCJ.newDecoder();
                DelegatableDecoder ddSJIS
                    = (DelegatableDecoder) csSJIS.newDecoder();

                ByteBuffer srcEUCJ = src.asReadOnlyBuffer();
                sandbox.clear();
                CoderResult resEUCJ = ddEUCJ.decodeLoop(srcEUCJ, sandbox);
                if (resEUCJ.isError())
                    return decodeLoop(ddSJIS, src, dst);
                ByteBuffer srcSJIS = src.asReadOnlyBuffer();
                CharBuffer sandboxSJIS = CharBuffer.allocate(cbufsiz);
                CoderResult resSJIS = ddSJIS.decodeLoop(srcSJIS, sandboxSJIS);
                if (resSJIS.isError())
                    return decodeLoop(ddEUCJ, src, dst);


                if (srcEUCJ.position() > srcSJIS.position())
                    return decodeLoop(ddEUCJ, src, dst);

                if (srcEUCJ.position() < srcSJIS.position())
                    return decodeLoop(ddSJIS, src, dst);

                if (src.position() == srcEUCJ.position())
                    return CoderResult.UNDERFLOW;

                sandbox.flip();
                return decodeLoop(looksLikeJapanese(sandbox) ? ddEUCJ : ddSJIS,
                                  src, dst);
            }

            return detectedDecoder.decodeLoop(src, dst);
        }

        protected void implReset() {
            detectedDecoder = null;
        }

        protected CoderResult implFlush(CharBuffer out) {
            if (detectedDecoder != null)
                return detectedDecoder.implFlush(out);
            else
                return super.implFlush(out);
        }

        public boolean isAutoDetecting() {
            return true;
        }

        public boolean isCharsetDetected() {
            return detectedDecoder != null;
        }

        public Charset detectedCharset() {
            if (detectedDecoder == null)
                throw new IllegalStateException("charset not yet detected");
            return ((CharsetDecoder) detectedDecoder).charset();
        }


        /**
         * Returned Shift_JIS Charset name is OS dependent
         */
        private static String getSJISName() {
            if (OperatingSystem.isWindows())
                return("windows-31J");
            else
                return("Shift_JIS");
        }

    }
}
/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.cs;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/* Legal CESU-8 Byte Sequences
 *
 * #    Code Points      Bits   Bit/Byte pattern
 * 1                     7      0xxxxxxx
 *      U+0000..U+007F          00..7F
 *
 * 2                     11     110xxxxx    10xxxxxx
 *      U+0080..U+07FF          C2..DF      80..BF
 *
 * 3                     16     1110xxxx    10xxxxxx    10xxxxxx
 *      U+0800..U+0FFF          E0          A0..BF      80..BF
 *      U+1000..U+FFFF          E1..EF      80..BF      80..BF
 *
 */

class CESU_8 extends Unicode
{
    public CESU_8() {
        super("CESU-8", StandardCharsets.aliases_CESU_8());
    }

    public String historicalName() {
        return "CESU8";
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    private static final void updatePositions(Buffer src, int sp,
                                              Buffer dst, int dp) {
        src.position(sp - src.arrayOffset());
        dst.position(dp - dst.arrayOffset());
    }

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private static class Decoder extends CharsetDecoder
                                 implements ArrayDecoder {

        private Decoder(Charset cs) {
            super(cs, 1.0f, 1.0f);
        }

        private static boolean isNotContinuation(int b) {
            return (b & 0xc0) != 0x80;
        }

        private static boolean isMalformed3(int b1, int b2, int b3) {
            return (b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
                   (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80;
        }

        private static boolean isMalformed3_2(int b1, int b2) {
            return (b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
                   (b2 & 0xc0) != 0x80;
        }

        private static CoderResult malformedN(ByteBuffer src, int nb) {
            switch (nb) {
            case 1:
            case 2:                    
                return CoderResult.malformedForLength(1);
            case 3:
                int b1 = src.get();
                int b2 = src.get();    
                return CoderResult.malformedForLength(
                    ((b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
                     isNotContinuation(b2)) ? 1 : 2);
            case 4:  
                b1 = src.get() & 0xff;
                b2 = src.get() & 0xff;
                if (b1 > 0xf4 ||
                    (b1 == 0xf0 && (b2 < 0x90 || b2 > 0xbf)) ||
                    (b1 == 0xf4 && (b2 & 0xf0) != 0x80) ||
                    isNotContinuation(b2))
                    return CoderResult.malformedForLength(1);
                if (isNotContinuation(src.get()))
                    return CoderResult.malformedForLength(2);
                return CoderResult.malformedForLength(3);
            default:
                assert false;
                return null;
            }
        }

        private static CoderResult malformed(ByteBuffer src, int sp,
                                             CharBuffer dst, int dp,
                                             int nb)
        {
            src.position(sp - src.arrayOffset());
            CoderResult cr = malformedN(src, nb);
            updatePositions(src, sp, dst, dp);
            return cr;
        }


        private static CoderResult malformed(ByteBuffer src,
                                             int mark, int nb)
        {
            src.position(mark);
            CoderResult cr = malformedN(src, nb);
            src.position(mark);
            return cr;
        }

        private static CoderResult malformedForLength(ByteBuffer src,
                                                      int sp,
                                                      CharBuffer dst,
                                                      int dp,
                                                      int malformedNB)
        {
            updatePositions(src, sp, dst, dp);
            return CoderResult.malformedForLength(malformedNB);
        }

        private static CoderResult malformedForLength(ByteBuffer src,
                                                      int mark,
                                                      int malformedNB)
        {
            src.position(mark);
            return CoderResult.malformedForLength(malformedNB);
        }


        private static CoderResult xflow(Buffer src, int sp, int sl,
                                         Buffer dst, int dp, int nb) {
            updatePositions(src, sp, dst, dp);
            return (nb == 0 || sl - sp < nb)
                   ? CoderResult.UNDERFLOW : CoderResult.OVERFLOW;
        }

        private static CoderResult xflow(Buffer src, int mark, int nb) {
            src.position(mark);
            return (nb == 0 || src.remaining() < nb)
                   ? CoderResult.UNDERFLOW : CoderResult.OVERFLOW;
        }

        private CoderResult decodeArrayLoop(ByteBuffer src,
                                            CharBuffer dst)
        {
            byte[] sa = src.array();
            int soff = src.arrayOffset();
            int sp = soff + src.position();
            int sl = soff + src.limit();

            char[] da = dst.array();
            int doff = dst.arrayOffset();
            int dp = doff + dst.position();
            int dl = doff + dst.limit();

            int n = JLA.decodeASCII(sa, sp, da, dp, Math.min(sl - sp, dl - dp));
            sp += n;
            dp += n;

            while (sp < sl) {
                int b1 = sa[sp];
                if (b1 >= 0) {
                    if (dp >= dl)
                        return xflow(src, sp, sl, dst, dp, 1);
                    da[dp++] = (char) b1;
                    sp++;
                } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                    if (sl - sp < 2 || dp >= dl)
                        return xflow(src, sp, sl, dst, dp, 2);
                    int b2 = sa[sp + 1];
                    if (isNotContinuation(b2))
                        return malformedForLength(src, sp, dst, dp, 1);
                    da[dp++] = (char) (((b1 << 6) ^ b2)
                                       ^
                                       (((byte) 0xC0 << 6) ^
                                        ((byte) 0x80 << 0)));
                    sp += 2;
                } else if ((b1 >> 4) == -2) {
                    int srcRemaining = sl - sp;
                    if (srcRemaining < 3 || dp >= dl) {
                        if (srcRemaining > 1 && isMalformed3_2(b1, sa[sp + 1]))
                            return malformedForLength(src, sp, dst, dp, 1);
                        return xflow(src, sp, sl, dst, dp, 3);
                    }
                    int b2 = sa[sp + 1];
                    int b3 = sa[sp + 2];
                    if (isMalformed3(b1, b2, b3))
                        return malformed(src, sp, dst, dp, 3);
                    da[dp++] = (char)
                        ((b1 << 12) ^
                         (b2 <<  6) ^
                         (b3 ^
                          (((byte) 0xE0 << 12) ^
                           ((byte) 0x80 <<  6) ^
                           ((byte) 0x80 <<  0))));
                    sp += 3;
                } else {
                    return malformed(src, sp, dst, dp, 1);
                }
            }
            return xflow(src, sp, sl, dst, dp, 0);
        }

        private CoderResult decodeBufferLoop(ByteBuffer src,
                                             CharBuffer dst)
        {
            int mark = src.position();
            int limit = src.limit();
            while (mark < limit) {
                int b1 = src.get();
                if (b1 >= 0) {
                    if (dst.remaining() < 1)
                        return xflow(src, mark, 1); 
                    dst.put((char) b1);
                    mark++;
                } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                    if (limit - mark < 2|| dst.remaining() < 1)
                        return xflow(src, mark, 2);
                    int b2 = src.get();
                    if (isNotContinuation(b2))
                        return malformedForLength(src, mark, 1);
                    dst.put((char) (((b1 << 6) ^ b2)
                                    ^
                                    (((byte) 0xC0 << 6) ^
                                     ((byte) 0x80 << 0))));
                    mark += 2;
                } else if ((b1 >> 4) == -2) {
                    int srcRemaining = limit - mark;
                    if (srcRemaining < 3 || dst.remaining() < 1) {
                        if (srcRemaining > 1 && isMalformed3_2(b1, src.get()))
                            return malformedForLength(src, mark, 1);
                        return xflow(src, mark, 3);
                    }
                    int b2 = src.get();
                    int b3 = src.get();
                    if (isMalformed3(b1, b2, b3))
                        return malformed(src, mark, 3);
                    dst.put((char)
                            ((b1 << 12) ^
                             (b2 <<  6) ^
                             (b3 ^
                              (((byte) 0xE0 << 12) ^
                               ((byte) 0x80 <<  6) ^
                               ((byte) 0x80 <<  0)))));
                    mark += 3;
                } else {
                    return malformed(src, mark, 1);
                }
            }
            return xflow(src, mark, 0);
        }

        protected CoderResult decodeLoop(ByteBuffer src,
                                         CharBuffer dst)
        {
            if (src.hasArray() && dst.hasArray())
                return decodeArrayLoop(src, dst);
            else
                return decodeBufferLoop(src, dst);
        }

        private static ByteBuffer getByteBuffer(ByteBuffer bb, byte[] ba, int sp)
        {
            if (bb == null)
                bb = ByteBuffer.wrap(ba);
            bb.position(sp);
            return bb;
        }

        public int decode(byte[] sa, int sp, int len, char[] da) {
            final int sl = sp + len;
            int dp = 0;
            int dlASCII = Math.min(len, da.length);
            ByteBuffer bb = null;  

            while (dp < dlASCII && sa[sp] >= 0)
                da[dp++] = (char) sa[sp++];

            while (sp < sl) {
                int b1 = sa[sp++];
                if (b1 >= 0) {
                    da[dp++] = (char) b1;
                } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                    if (sp < sl) {
                        int b2 = sa[sp++];
                        if (isNotContinuation(b2)) {
                            if (malformedInputAction() != CodingErrorAction.REPLACE)
                                return -1;
                            da[dp++] = replacement().charAt(0);
                            sp--;            
                        } else {
                            da[dp++] = (char) (((b1 << 6) ^ b2)^
                                           (((byte) 0xC0 << 6) ^
                                            ((byte) 0x80 << 0)));
                        }
                        continue;
                    }
                    if (malformedInputAction() != CodingErrorAction.REPLACE)
                        return -1;
                    da[dp++] = replacement().charAt(0);
                    return dp;
                } else if ((b1 >> 4) == -2) {
                    if (sp + 1 < sl) {
                        int b2 = sa[sp++];
                        int b3 = sa[sp++];
                        if (isMalformed3(b1, b2, b3)) {
                            if (malformedInputAction() != CodingErrorAction.REPLACE)
                                return -1;
                            da[dp++] = replacement().charAt(0);
                            sp -=3;
                            bb = getByteBuffer(bb, sa, sp);
                            sp += malformedN(bb, 3).length();
                        } else {
                            da[dp++] = (char)((b1 << 12) ^
                                              (b2 <<  6) ^
                                              (b3 ^
                                              (((byte) 0xE0 << 12) ^
                                              ((byte) 0x80 <<  6) ^
                                              ((byte) 0x80 <<  0))));
                        }
                        continue;
                    }
                    if (malformedInputAction() != CodingErrorAction.REPLACE)
                        return -1;
                    if (sp  < sl && isMalformed3_2(b1, sa[sp])) {
                        da[dp++] = replacement().charAt(0);
                        continue;

                    }
                    da[dp++] = replacement().charAt(0);
                    return dp;
                } else {
                    if (malformedInputAction() != CodingErrorAction.REPLACE)
                        return -1;
                    da[dp++] = replacement().charAt(0);
                }
            }
            return dp;
        }
    }

    private static class Encoder extends CharsetEncoder
                                 implements ArrayEncoder {

        private Encoder(Charset cs) {
            super(cs, 1.1f, 3.0f);
        }

        public boolean canEncode(char c) {
            return !Character.isSurrogate(c);
        }

        public boolean isLegalReplacement(byte[] repl) {
            return ((repl.length == 1 && repl[0] >= 0) ||
                    super.isLegalReplacement(repl));
        }

        private static CoderResult overflow(CharBuffer src, int sp,
                                            ByteBuffer dst, int dp) {
            updatePositions(src, sp, dst, dp);
            return CoderResult.OVERFLOW;
        }

        private static CoderResult overflow(CharBuffer src, int mark) {
            src.position(mark);
            return CoderResult.OVERFLOW;
        }

        private static void to3Bytes(byte[] da, int dp, char c) {
            da[dp] = (byte)(0xe0 | ((c >> 12)));
            da[dp + 1] = (byte)(0x80 | ((c >>  6) & 0x3f));
            da[dp + 2] = (byte)(0x80 | (c & 0x3f));
        }

        private static void to3Bytes(ByteBuffer dst, char c) {
            dst.put((byte)(0xe0 | ((c >> 12))));
            dst.put((byte)(0x80 | ((c >>  6) & 0x3f)));
            dst.put((byte)(0x80 | (c & 0x3f)));
        }

        private Surrogate.Parser sgp;
        private CoderResult encodeArrayLoop(CharBuffer src,
                                            ByteBuffer dst)
        {
            char[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();

            byte[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();

            int n = JLA.encodeASCII(sa, sp, da, dp, Math.min(sl - sp, dl - dp));
            sp += n;
            dp += n;

            while (sp < sl) {
                char c = sa[sp];
                if (c < 0x80) {
                    if (dp >= dl)
                        return overflow(src, sp, dst, dp);
                    da[dp++] = (byte)c;
                } else if (c < 0x800) {
                    if (dl - dp < 2)
                        return overflow(src, sp, dst, dp);
                    da[dp++] = (byte)(0xc0 | (c >> 6));
                    da[dp++] = (byte)(0x80 | (c & 0x3f));
                } else if (Character.isSurrogate(c)) {
                    if (sgp == null)
                        sgp = new Surrogate.Parser();
                    int uc = sgp.parse(c, sa, sp, sl);
                    if (uc < 0) {
                        updatePositions(src, sp, dst, dp);
                        return sgp.error();
                    }
                    if (dl - dp < 6)
                        return overflow(src, sp, dst, dp);
                    to3Bytes(da, dp, Character.highSurrogate(uc));
                    dp += 3;
                    to3Bytes(da, dp, Character.lowSurrogate(uc));
                    dp += 3;
                    sp++;  
                } else {
                    if (dl - dp < 3)
                        return overflow(src, sp, dst, dp);
                    to3Bytes(da, dp, c);
                    dp += 3;
                }
                sp++;
            }
            updatePositions(src, sp, dst, dp);
            return CoderResult.UNDERFLOW;
        }

        private CoderResult encodeBufferLoop(CharBuffer src,
                                             ByteBuffer dst)
        {
            int mark = src.position();
            while (src.hasRemaining()) {
                char c = src.get();
                if (c < 0x80) {
                    if (!dst.hasRemaining())
                        return overflow(src, mark);
                    dst.put((byte)c);
                } else if (c < 0x800) {
                    if (dst.remaining() < 2)
                        return overflow(src, mark);
                    dst.put((byte)(0xc0 | (c >> 6)));
                    dst.put((byte)(0x80 | (c & 0x3f)));
                } else if (Character.isSurrogate(c)) {
                    if (sgp == null)
                        sgp = new Surrogate.Parser();
                    int uc = sgp.parse(c, src);
                    if (uc < 0) {
                        src.position(mark);
                        return sgp.error();
                    }
                    if (dst.remaining() < 6)
                        return overflow(src, mark);
                    to3Bytes(dst, Character.highSurrogate(uc));
                    to3Bytes(dst, Character.lowSurrogate(uc));
                    mark++;  
                } else {
                    if (dst.remaining() < 3)
                        return overflow(src, mark);
                    to3Bytes(dst, c);
                }
                mark++;
            }
            src.position(mark);
            return CoderResult.UNDERFLOW;
        }

        protected final CoderResult encodeLoop(CharBuffer src,
                                               ByteBuffer dst)
        {
            if (src.hasArray() && dst.hasArray())
                return encodeArrayLoop(src, dst);
            else
                return encodeBufferLoop(src, dst);
        }

        public int encode(char[] sa, int sp, int len, byte[] da) {
            int sl = sp + len;
            int dp = 0;

            int n = JLA.encodeASCII(sa, sp, da, dp, Math.min(len, da.length));
            sp += n;
            dp += n;

            while (sp < sl) {
                char c = sa[sp++];
                if (c < 0x80) {
                    da[dp++] = (byte)c;
                } else if (c < 0x800) {
                    da[dp++] = (byte)(0xc0 | (c >> 6));
                    da[dp++] = (byte)(0x80 | (c & 0x3f));
                } else if (Character.isSurrogate(c)) {
                    if (sgp == null)
                        sgp = new Surrogate.Parser();
                    int uc = sgp.parse(c, sa, sp - 1, sl);
                    if (uc < 0) {
                        if (malformedInputAction() != CodingErrorAction.REPLACE)
                            return -1;
                        da[dp++] = replacement()[0];
                    } else {
                        to3Bytes(da, dp, Character.highSurrogate(uc));
                        dp += 3;
                        to3Bytes(da, dp, Character.lowSurrogate(uc));
                        dp += 3;
                        sp++;  
                    }
                } else {
                    to3Bytes(da, dp, c);
                    dp += 3;
                }
            }
            return dp;
        }
    }
}
/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 */

package sun.nio.cs;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;

import jdk.internal.misc.InternalLock;

public class StreamDecoder extends Reader {

    private static final int MIN_BYTE_BUFFER_SIZE = 32;
    private static final int DEFAULT_BYTE_BUFFER_SIZE = 8192;

    private volatile boolean closed;

    private void ensureOpen() throws IOException {
        if (closed)
            throw new IOException("Stream closed");
    }

    private boolean haveLeftoverChar = false;
    private char leftoverChar;



    public static StreamDecoder forInputStreamReader(InputStream in,
                                                     Object lock,
                                                     String charsetName)
        throws UnsupportedEncodingException
    {
        try {
            return new StreamDecoder(in, lock, Charset.forName(charsetName));
        } catch (IllegalCharsetNameException | UnsupportedCharsetException x) {
            throw new UnsupportedEncodingException (charsetName);
        }
    }

    public static StreamDecoder forInputStreamReader(InputStream in,
                                                     Object lock,
                                                     Charset cs)
    {
        return new StreamDecoder(in, lock, cs);
    }

    public static StreamDecoder forInputStreamReader(InputStream in,
                                                     Object lock,
                                                     CharsetDecoder dec)
    {
        return new StreamDecoder(in, lock, dec);
    }



    public static StreamDecoder forDecoder(ReadableByteChannel ch,
                                           CharsetDecoder dec,
                                           int minBufferCap)
    {
        return new StreamDecoder(ch, dec, minBufferCap);
    }




    public String getEncoding() {
        if (isOpen())
            return encodingName();
        return null;
    }

    public int read() throws IOException {
        return read0();
    }

    private int read0() throws IOException {
        Object lock = this.lock;
        if (lock instanceof InternalLock locker) {
            locker.lock();
            try {
                return lockedRead0();
            } finally {
                locker.unlock();
            }
        } else {
            synchronized (lock) {
                return lockedRead0();
            }
        }
    }

    @SuppressWarnings("fallthrough")
    private int lockedRead0() throws IOException {
        if (haveLeftoverChar) {
            haveLeftoverChar = false;
            return leftoverChar;
        }

        char[] cb = new char[2];
        int n = read(cb, 0, 2);
        switch (n) {
        case -1:
            return -1;
        case 2:
            leftoverChar = cb[1];
            haveLeftoverChar = true;
        case 1:
            return cb[0];
        default:
            assert false : n;
            return -1;
        }
    }

    public int read(char[] cbuf, int offset, int length) throws IOException {
        Object lock = this.lock;
        if (lock instanceof InternalLock locker) {
            locker.lock();
            try {
                return lockedRead(cbuf, offset, length);
            } finally {
                locker.unlock();
            }
        } else {
            synchronized (lock) {
                return lockedRead(cbuf, offset, length);
            }
        }
    }

    private int lockedRead(char[] cbuf, int offset, int length) throws IOException {
        int off = offset;
        int len = length;

        ensureOpen();
        if ((off < 0) || (off > cbuf.length) || (len < 0) ||
            ((off + len) > cbuf.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0)
            return 0;

        int n = 0;

        if (haveLeftoverChar) {
            cbuf[off] = leftoverChar;
            off++; len--;
            haveLeftoverChar = false;
            n = 1;
            if ((len == 0) || !implReady())
                return n;
        }

        if (len == 1) {
            int c = read0();
            if (c == -1)
                return (n == 0) ? -1 : n;
            cbuf[off] = (char)c;
            return n + 1;
        }

        int nr = implRead(cbuf, off, off + len);

        return (nr < 0) ? (n == 1 ? 1 : nr) : (n + nr);
    }

    public boolean ready() throws IOException {
        Object lock = this.lock;
        if (lock instanceof InternalLock locker) {
            locker.lock();
            try {
                return lockedReady();
            } finally {
                locker.unlock();
            }
        } else {
            synchronized (lock) {
                return lockedReady();
            }
        }
    }

    private boolean lockedReady() throws IOException {
        ensureOpen();
        return haveLeftoverChar || implReady();
    }

    public void close() throws IOException {
        Object lock = this.lock;
        if (lock instanceof InternalLock locker) {
            locker.lock();
            try {
                lockedClose();
            } finally {
                locker.unlock();
            }
        } else {
            synchronized (lock) {
                lockedClose();
            }
        }
    }

    private void lockedClose() throws IOException {
        if (closed)
            return;
        try {
            implClose();
        } finally {
            closed = true;
        }
    }

    private boolean isOpen() {
        return !closed;
    }

    public void fillZeroToPosition() throws IOException {
        Object lock = this.lock;
        if (lock instanceof InternalLock locker) {
            locker.lock();
            try {
                lockedFillZeroToPosition();
            } finally {
                locker.unlock();
            }
        } else {
            synchronized (lock) {
                lockedFillZeroToPosition();
            }
        }
    }

    private void lockedFillZeroToPosition() {
        Arrays.fill(bb.array(), bb.arrayOffset(), bb.arrayOffset() + bb.position(), (byte)0);
    }


    private final Charset cs;
    private final CharsetDecoder decoder;
    private final ByteBuffer bb;

    private final InputStream in;
    private final ReadableByteChannel ch;

    StreamDecoder(InputStream in, Object lock, Charset cs) {
        this(in, lock,
            cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE));
    }

    StreamDecoder(InputStream in, Object lock, CharsetDecoder dec) {
        super(lock);
        this.cs = dec.charset();
        this.decoder = dec;
        this.in = in;
        this.ch = null;
        this.bb = ByteBuffer.allocate(DEFAULT_BYTE_BUFFER_SIZE);
        bb.flip();                      
    }

    StreamDecoder(ReadableByteChannel ch, CharsetDecoder dec, int mbc) {
        this.in = null;
        this.ch = ch;
        this.decoder = dec;
        this.cs = dec.charset();
        this.bb = ByteBuffer.allocate(mbc < 0
                                  ? DEFAULT_BYTE_BUFFER_SIZE
                                  : (mbc < MIN_BYTE_BUFFER_SIZE
                                     ? MIN_BYTE_BUFFER_SIZE
                                     : mbc));
        bb.flip();
    }

    private int readBytes() throws IOException {
        bb.compact();
        try {
            if (ch != null) {
                int n = ch.read(bb);
                if (n < 0)
                    return n;
            } else {
                int lim = bb.limit();
                int pos = bb.position();
                assert (pos <= lim);
                int rem = (pos <= lim ? lim - pos : 0);
                int n = in.read(bb.array(), bb.arrayOffset() + pos, rem);
                if (n < 0)
                    return n;
                if (n == 0)
                    throw new IOException("Underlying input stream returned zero bytes");
                assert (n <= rem) : "n = " + n + ", rem = " + rem;
                bb.position(pos + n);
            }
        } finally {
            bb.flip();
        }

        int rem = bb.remaining();
        assert (rem != 0) : rem;
        return rem;
    }

    int implRead(char[] cbuf, int off, int end) throws IOException {

        assert (end - off > 1);

        CharBuffer cb = CharBuffer.wrap(cbuf, off, end - off);
        if (cb.position() != 0) {
            cb = cb.slice();
        }

        boolean eof = false;
        for (;;) {
            CoderResult cr = decoder.decode(bb, cb, eof);
            if (cr.isUnderflow()) {
                if (eof)
                    break;
                if (!cb.hasRemaining())
                    break;
                if ((cb.position() > 0) && !inReady())
                    break;          
                int n = readBytes();
                if (n < 0) {
                    eof = true;
                    if ((cb.position() == 0) && (!bb.hasRemaining()))
                        break;
                }
                continue;
            }
            if (cr.isOverflow()) {
                assert cb.position() > 0;
                break;
            }
            cr.throwException();
        }

        if (eof) {
            decoder.reset();
        }

        if (cb.position() == 0) {
            if (eof) {
                return -1;
            }
            assert false;
        }
        return cb.position();
    }

    String encodingName() {
        return ((cs instanceof HistoricallyNamedCharset)
            ? ((HistoricallyNamedCharset)cs).historicalName()
            : cs.name());
    }

    private boolean inReady() {
        try {
            return (((in != null) && (in.available() > 0))
                    || (ch instanceof FileChannel)); 
        } catch (IOException x) {
            return false;
        }
    }

    boolean implReady() {
        return bb.hasRemaining() || inReady();
    }

    void implClose() throws IOException {
        if (ch != null) {
            ch.close();
        } else {
            in.close();
        }
    }
}
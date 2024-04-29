/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.common.bytes;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefIterator;
import org.elasticsearch.common.io.stream.ByteBufferStreamInput;
import org.elasticsearch.common.io.stream.StreamInput;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A StreamInput that reads off a {@link BytesRefIterator}. This is used to provide
 * generic stream access to {@link BytesReference} instances without materializing the
 * underlying bytes.
 */
class BytesReferenceStreamInput extends StreamInput {

    private static final ByteBuffer EMPTY = ByteBuffer.wrap(new byte[0]);

    protected final BytesReference bytesReference;
    private BytesRefIterator iterator;
    private ByteBuffer slice;
    private int totalOffset; 
    private int mark = 0;

    BytesReferenceStreamInput(BytesReference bytesReference) throws IOException {
        this.bytesReference = bytesReference;
        this.iterator = bytesReference.iterator();
        this.slice = convertToByteBuffer(iterator.next());
        this.totalOffset = 0;
    }

    static ByteBuffer convertToByteBuffer(BytesRef bytesRef) {
        if (bytesRef == null) {
            return EMPTY;
        }
        return ByteBuffer.wrap(bytesRef.bytes, bytesRef.offset, bytesRef.length).slice();
    }

    @Override
    public byte readByte() throws IOException {
        maybeNextSlice();
        return slice.get();
    }

    @Override
    public short readShort() throws IOException {
        if (slice.remaining() >= 2) {
            return slice.getShort();
        } else {
            return super.readShort();
        }
    }

    @Override
    public int readInt() throws IOException {
        if (slice.remaining() >= 4) {
            return slice.getInt();
        } else {
            return super.readInt();
        }
    }

    @Override
    public long readLong() throws IOException {
        if (slice.remaining() >= 8) {
            return slice.getLong();
        } else {
            return super.readLong();
        }
    }

    @Override
    public String readString() throws IOException {
        final int chars = readArraySize();
        if (slice.hasArray()) {
            final String string = tryReadStringFromBytes(
                slice.array(),
                slice.position() + slice.arrayOffset(),
                slice.limit() + slice.arrayOffset(),
                chars
            );
            if (string != null) {
                return string;
            }
        }
        return doReadString(chars);
    }

    @Override
    public int readVInt() throws IOException {
        if (slice.remaining() >= 5) {
            return ByteBufferStreamInput.readVInt(slice);
        }
        return super.readVInt();
    }

    @Override
    public long readVLong() throws IOException {
        if (slice.remaining() >= 10) {
            return ByteBufferStreamInput.readVLong(slice);
        } else {
            return super.readVLong();
        }
    }

    protected int offset() {
        return totalOffset + slice.position();
    }

    private void maybeNextSlice() throws IOException {
        if (slice.hasRemaining() == false) {
            moveToNextSlice();
        }
    }

    private void moveToNextSlice() throws IOException {
        totalOffset += slice.limit();
        BytesRef bytesRef = iterator.next();
        while (bytesRef != null && bytesRef.length == 0) {
            bytesRef = iterator.next();
        }
        if (bytesRef == null) {
            throw new EOFException();
        }
        slice = convertToByteBuffer(bytesRef);
        assert slice.position() == 0;
    }

    @Override
    public void readBytes(byte[] b, int bOffset, int len) throws IOException {
        Objects.checkFromIndexSize(offset(), len, bytesReference.length());
        final int bytesRead = read(b, bOffset, len);
        assert bytesRead == len : bytesRead + " vs " + len;
    }

    @Override
    public int read() throws IOException {
        if (offset() >= bytesReference.length()) {
            return -1;
        }
        return Byte.toUnsignedInt(readByte());
    }

    @Override
    public int read(final byte[] b, final int bOffset, final int len) throws IOException {
        if (slice.remaining() >= len) {
            slice.get(b, bOffset, len);
            return len;
        }
        return readFromMultipleSlices(b, bOffset, len);
    }

    private int readFromMultipleSlices(byte[] b, int bOffset, int len) throws IOException {
        final int length = bytesReference.length();
        final int offset = offset();
        if (offset >= length) {
            return -1;
        }
        final int numBytesToCopy = Math.min(len, length - offset);
        int remaining = numBytesToCopy; 
        int destOffset = bOffset;
        while (remaining > 0) {
            maybeNextSlice();
            final int currentLen = Math.min(remaining, slice.remaining());
            assert currentLen > 0 : "length has to be > 0 to make progress but was: " + currentLen;
            slice.get(b, destOffset, currentLen);
            destOffset += currentLen;
            remaining -= currentLen;
            assert remaining >= 0 : "remaining: " + remaining;
        }
        return numBytesToCopy;
    }

    @Override
    public void close() {
    }

    @Override
    public int available() {
        return bytesReference.length() - offset();
    }

    @Override
    protected void ensureCanReadBytes(int bytesToRead) throws EOFException {
        int bytesAvailable = bytesReference.length() - offset();
        if (bytesAvailable < bytesToRead) {
            throwEOF(bytesToRead, bytesAvailable);
        }
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0L) {
            return 0L;
        }
        if (n <= slice.remaining()) {
            slice.position(slice.position() + (int) n);
            return n;
        }
        return skipMultiple(n);
    }

    private int skipMultiple(long n) throws IOException {
        assert offset() <= bytesReference.length() : offset() + " vs " + bytesReference.length();
        final int numBytesSkipped = (int) Math.min(n, bytesReference.length() - offset());
        int remaining = numBytesSkipped;
        while (remaining > 0) {
            maybeNextSlice();
            int currentLen = Math.min(remaining, slice.remaining());
            remaining -= currentLen;
            slice.position(slice.position() + currentLen);
            assert remaining >= 0 : "remaining: " + remaining;
        }
        return numBytesSkipped;
    }

    @Override
    public void reset() throws IOException {
        if (totalOffset <= mark) {
            slice.position(mark - totalOffset);
        } else {
            iterator = bytesReference.iterator();
            slice = convertToByteBuffer(iterator.next());
            totalOffset = 0;
            final long skipped = skip(mark);
            assert skipped == mark : skipped + " vs " + mark;
        }
    }

    @Override
    public BytesReference readSlicedBytesReference() throws IOException {
        int len = readVInt();
        int pos = offset();
        if (len != skip(len)) {
            throw new EOFException();
        }
        return bytesReference.slice(pos, len);
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void mark(int readLimit) {
        this.mark = offset();
    }
}
/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javax.sound.sampled;

import java.io.IOException;
import java.io.InputStream;

/**
 * An audio input stream is an input stream with a specified audio format and
 * length. The length is expressed in sample frames, not bytes. Several methods
 * are provided for reading a certain number of bytes from the stream, or an
 * unspecified number of bytes. The audio input stream keeps track of the last
 * byte that was read. You can skip over an arbitrary number of bytes to get to
 * a later position for reading. An audio input stream may support marks. When
 * you set a mark, the current position is remembered so that you can return to
 * it later.
 * <p>
 * The {@code AudioSystem} class includes many methods that manipulate
 * {@code AudioInputStream} objects. For example, the methods let you:
 * <ul>
 *   <li>obtain an audio input stream from an external audio file, stream, or
 *   {@code URL}
 *   <li>write an external file from an audio input stream
 *   <li>convert an audio input stream to a different audio format
 * </ul>
 *
 * @author David Rivas
 * @author Kara Kytle
 * @author Florian Bomers
 * @see AudioSystem
 * @see Clip#open(AudioInputStream)
 * @since 1.3
 */
public class AudioInputStream extends InputStream {

    /**
     * The {@code InputStream} from which this {@code AudioInputStream} object
     * was constructed.
     */
    private final InputStream stream;

    /**
     * The format of the audio data contained in the stream.
     */
    protected AudioFormat format;

    /**
     * This stream's length, in sample frames.
     */
    protected long frameLength;

    /**
     * The size of each frame, in bytes.
     */
    protected int frameSize;

    /**
     * The current position in this stream, in sample frames (zero-based).
     */
    protected long framePos;

    /**
     * The position where a mark was set.
     */
    private long markpos;

    /**
     * When the underlying stream could only return a non-integral number of
     * frames, store the remainder in a temporary buffer.
     */
    private byte[] pushBackBuffer = null;

    /**
     * number of valid bytes in the pushBackBuffer.
     */
    private int pushBackLen = 0;

    /**
     * MarkBuffer at mark position.
     */
    private byte[] markPushBackBuffer = null;

    /**
     * number of valid bytes in the markPushBackBuffer.
     */
    private int markPushBackLen = 0;

    /**
     * Constructs an audio input stream that has the requested format and length
     * in sample frames, using audio data from the specified input stream.
     *
     * @param  stream the stream on which this {@code AudioInputStream} object
     *         is based
     * @param  format the format of this stream's audio data
     * @param  length the length in sample frames of the data in this stream
     */
    public AudioInputStream(InputStream stream, AudioFormat format, long length) {

        super();

        this.format = format;
        this.frameLength = length;
        this.frameSize = format.getFrameSize();

        if( this.frameSize == AudioSystem.NOT_SPECIFIED || frameSize <= 0) {
            this.frameSize = 1;
        }

        this.stream = stream;
        framePos = 0;
        markpos = 0;
    }

    /**
     * Constructs an audio input stream that reads its data from the target data
     * line indicated. The format of the stream is the same as that of the
     * target data line, and the length is {@code AudioSystem#NOT_SPECIFIED}.
     *
     * @param  line the target data line from which this stream obtains its data
     * @see AudioSystem#NOT_SPECIFIED
     */
    public AudioInputStream(TargetDataLine line) {

        TargetDataLineInputStream tstream = new TargetDataLineInputStream(line);
        format = line.getFormat();
        frameLength = AudioSystem.NOT_SPECIFIED;
        frameSize = format.getFrameSize();

        if( frameSize == AudioSystem.NOT_SPECIFIED || frameSize <= 0) {
            frameSize = 1;
        }
        this.stream = tstream;
        framePos = 0;
        markpos = 0;
    }

    /**
     * Obtains the audio format of the sound data in this audio input stream.
     *
     * @return an audio format object describing this stream's format
     */
    public AudioFormat getFormat() {
        return format;
    }

    /**
     * Obtains the length of the stream, expressed in sample frames rather than
     * bytes.
     *
     * @return the length in sample frames
     */
    public long getFrameLength() {
        return frameLength;
    }

    /**
     * Reads the next byte of data from the audio input stream. The audio input
     * stream's frame size must be one byte, or an {@code IOException} will be
     * thrown.
     *
     * @return {@inheritDoc}
     * @throws IOException if an input or output error occurs
     * @see #read(byte[], int, int)
     * @see #read(byte[])
     * @see #available
     */
    @Override
    public int read() throws IOException {
        if( frameSize != 1 ) {
            throw new IOException("cannot read a single byte if frame size > 1");
        }

        byte[] data = new byte[1];
        int temp = read(data);
        if (temp <= 0) {
            return -1;
        }
        return data[0] & 0xFF;
    }

    /**
     * Reads some number of bytes from the audio input stream and stores them
     * into the buffer array {@code b}. The number of bytes actually read is
     * returned as an integer. This method blocks until input data is available,
     * the end of the stream is detected, or an exception is thrown.
     * <p>
     * This method will always read an integral number of frames. If the length
     * of the array is not an integral number of frames, a maximum of
     * {@code b.length - (b.length % frameSize)} bytes will be read.
     *
     * @param  b {@inheritDoc}
     * @return {@inheritDoc}
     * @throws IOException if an input or output error occurs
     * @see #read(byte[], int, int)
     * @see #read()
     * @see #available
     */
    @Override
    public int read(byte[] b) throws IOException {
        return read(b,0,b.length);
    }

    /**
     * Reads up to a specified maximum number of bytes of data from the audio
     * stream, putting them into the given byte array.
     * <p>
     * This method will always read an integral number of frames. If {@code len}
     * does not specify an integral number of frames, a maximum of
     * {@code len - (len % frameSize)} bytes will be read.
     *
     * @param  b {@inheritDoc}
     * @param  off {@inheritDoc}
     * @param  len {@inheritDoc}
     * @return {@inheritDoc}
     * @throws IOException if an input or output error occurs
     * @see #read(byte[])
     * @see #read()
     * @see #skip
     * @see #available
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        final int reminder = len % frameSize;
        if (reminder != 0) {
            len -= reminder;
            if (len == 0) {
                return 0;
            }
        }

        if( frameLength != AudioSystem.NOT_SPECIFIED ) {
            if( framePos >= frameLength ) {
                return -1;
            } else {

                if( (len/frameSize) > (frameLength-framePos) ) {
                    len = (int) (frameLength-framePos) * frameSize;
                }
            }
        }

        int bytesRead = 0;
        int thisOff = off;

        if (pushBackLen > 0 && len >= pushBackLen) {
            System.arraycopy(pushBackBuffer, 0,
                             b, off, pushBackLen);
            thisOff += pushBackLen;
            len -= pushBackLen;
            bytesRead += pushBackLen;
            pushBackLen = 0;
        }

        int thisBytesRead = stream.read(b, thisOff, len);
        if (thisBytesRead == -1) {
            return -1;
        }
        if (thisBytesRead > 0) {
            bytesRead += thisBytesRead;
        }
        if (bytesRead > 0) {
            pushBackLen = bytesRead % frameSize;
            if (pushBackLen > 0) {
                if (pushBackBuffer == null) {
                    pushBackBuffer = new byte[frameSize];
                }
                System.arraycopy(b, off + bytesRead - pushBackLen,
                                 pushBackBuffer, 0, pushBackLen);
                bytesRead -= pushBackLen;
            }
            framePos += bytesRead/frameSize;
        }
        return bytesRead;
    }

    /**
     * Skips over and discards a specified number of bytes from this audio input
     * stream.
     * <p>
     * This method will always skip an integral number of frames. If {@code n}
     * does not specify an integral number of frames, a maximum of
     * {@code n - (n % frameSize)} bytes will be skipped.
     *
     * @param  n the requested number of bytes to be skipped
     * @return the actual number of bytes skipped
     * @throws IOException if an input or output error occurs
     * @see #read
     * @see #available
     */
    @Override
    public long skip(long n) throws IOException {
        final long reminder = n % frameSize;
        if (reminder != 0) {
            n -= reminder;
        }
        if (n <= 0) {
            return 0;
        }

        if (frameLength != AudioSystem.NOT_SPECIFIED) {
            if ((n / frameSize) > (frameLength - framePos)) {
                n = (frameLength - framePos) * frameSize;
            }
        }
        long remaining = n;
        while (remaining > 0) {
            long ret = Math.min(stream.skip(remaining), remaining);
            if (ret == 0) {
                if (stream.read() == -1) {
                    break;
                }
                ret = 1;
            } else if (ret < 0) {
                break;
            }
            remaining -= ret;
        }
        final long temp =  n - remaining;

        if (temp % frameSize != 0) {
            throw new IOException("Could not skip an integer number of frames.");
        }
        framePos += temp/frameSize;
        return temp;
    }

    /**
     * Returns the maximum number of bytes that can be read (or skipped over)
     * from this audio input stream without blocking. This limit applies only to
     * the next invocation of a {@code read} or {@code skip} method for this
     * audio input stream; the limit can vary each time these methods are
     * invoked. Depending on the underlying stream, an {@code IOException} may
     * be thrown if this stream is closed.
     *
     * @return the number of bytes that can be read from this audio input stream
     *         without blocking
     * @throws IOException if an input or output error occurs
     * @see #read(byte[], int, int)
     * @see #read(byte[])
     * @see #read()
     * @see #skip
     */
    @Override
    public int available() throws IOException {

        int temp = stream.available();

        if( (frameLength != AudioSystem.NOT_SPECIFIED) && ( (temp/frameSize) > (frameLength-framePos)) ) {
            return (int) (frameLength-framePos) * frameSize;
        } else {
            return temp;
        }
    }

    /**
     * Closes this audio input stream and releases any system resources
     * associated with the stream.
     *
     * @throws IOException if an input or output error occurs
     */
    @Override
    public void close() throws IOException {
        stream.close();
    }

    /**
     * Marks the current position in this audio input stream.
     *
     * @param  readlimit the maximum number of bytes that can be read before the
     *         mark position becomes invalid
     * @see #reset
     * @see #markSupported
     */
    @Override
    public void mark(int readlimit) {

        stream.mark(readlimit);
        if (markSupported()) {
            markpos = framePos;
            markPushBackLen = pushBackLen;
            if (markPushBackLen > 0) {
                if (markPushBackBuffer == null) {
                    markPushBackBuffer = new byte[frameSize];
                }
                System.arraycopy(pushBackBuffer, 0, markPushBackBuffer, 0, markPushBackLen);
            }
        }
    }

    /**
     * Repositions this audio input stream to the position it had at the time
     * its {@code mark} method was last invoked.
     *
     * @throws IOException if an input or output error occurs
     * @see #mark
     * @see #markSupported
     */
    @Override
    public void reset() throws IOException {

        stream.reset();
        framePos = markpos;
        pushBackLen = markPushBackLen;
        if (pushBackLen > 0) {
            if (pushBackBuffer == null) {
                pushBackBuffer = new byte[frameSize - 1];
            }
            System.arraycopy(markPushBackBuffer, 0, pushBackBuffer, 0, pushBackLen);
        }
    }

    /**
     * Tests whether this audio input stream supports the {@code mark} and
     * {@code reset} methods.
     *
     * @return {@code true} if this stream supports the {@code mark} and
     *         {@code reset} methods; {@code false} otherwise
     * @see #mark
     * @see #reset
     */
    @Override
    public boolean markSupported() {

        return stream.markSupported();
    }

    /**
     * Private inner class that makes a TargetDataLine look like an InputStream.
     */
    private static class TargetDataLineInputStream extends InputStream {

        /**
         * The TargetDataLine on which this TargetDataLineInputStream is based.
         */
        TargetDataLine line;

        TargetDataLineInputStream(TargetDataLine line) {
            super();
            this.line = line;
        }

        @Override
        public int available() throws IOException {
            return line.available();
        }

        @Override
        public void close() throws IOException {
            if (line.isActive()) {
                line.flush();
                line.stop();
            }
            line.close();
        }

        @Override
        public int read() throws IOException {

            byte[] b = new byte[1];

            int value = read(b, 0, 1);

            if (value == -1) {
                return -1;
            }

            value = (int)b[0];

            if (line.getFormat().getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) {
                value += 128;
            }

            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                return line.read(b, off, len);
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage());
            }
        }
    }
}
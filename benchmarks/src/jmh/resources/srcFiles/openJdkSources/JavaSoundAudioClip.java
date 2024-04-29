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

package com.sun.media.sound;

import java.applet.AudioClip;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiFileFormat;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Java Sound audio clip;
 *
 * @author Arthur van Hoff, Kara Kytle, Jan Borgersen
 * @author Florian Bomers
 */
@SuppressWarnings({"deprecation", "removal"})
public final class JavaSoundAudioClip implements AudioClip, MetaEventListener, LineListener {

    private long lastPlayCall = 0;
    private static final int MINIMUM_PLAY_DELAY = 30;

    private byte[] loadedAudio = null;
    private int loadedAudioByteLength = 0;
    private AudioFormat loadedAudioFormat = null;

    private AutoClosingClip clip = null;
    private boolean clipLooping = false;

    private DataPusher datapusher = null;

    private Sequencer sequencer = null;
    private Sequence sequence = null;
    private boolean sequencerloop = false;
    private volatile boolean success;

    /**
     * used for determining how many samples is the
     * threshold between playing as a Clip and streaming
     * from the file.
     *
     * $$jb: 11.07.99: the engine has a limit of 1M
     * samples to play as a Clip, so compare this number
     * with the number of samples in the stream.
     *
     */
    private static final long CLIP_THRESHOLD = 1048576;
    private static final int STREAM_BUFFER_SIZE = 1024;

    public static JavaSoundAudioClip create(final URLConnection uc) {
        JavaSoundAudioClip clip = new JavaSoundAudioClip();
        try {
            clip.init(uc.getInputStream());
        } catch (final Exception ignored) {
        }
        return clip;
    }

    public static JavaSoundAudioClip create(final URL url) {
        JavaSoundAudioClip clip = new JavaSoundAudioClip();
        try {
            clip.init(url.openStream());
        } catch (final Exception ignored) {
        }
        return clip;
    }

    private void init(InputStream in) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(in, STREAM_BUFFER_SIZE);
        bis.mark(STREAM_BUFFER_SIZE);
        try {
            AudioInputStream as = AudioSystem.getAudioInputStream(bis);
            success = loadAudioData(as);

            if (success) {
                success = false;
                if (loadedAudioByteLength < CLIP_THRESHOLD) {
                    success = createClip();
                }
                if (!success) {
                    success = createSourceDataLine();
                }
            }
        } catch (UnsupportedAudioFileException e) {
            try {
                MidiFileFormat mff = MidiSystem.getMidiFileFormat(bis);
                success = createSequencer(bis);
            } catch (InvalidMidiDataException e1) {
                success = false;
            }
        }
    }

    @Override
    public synchronized void play() {
        if (!success) {
            return;
        }
        startImpl(false);
    }

    @Override
    public synchronized void loop() {
        if (!success) {
            return;
        }
        startImpl(true);
    }

    private synchronized void startImpl(boolean loop) {
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - lastPlayCall;
        if (diff < MINIMUM_PLAY_DELAY) {
            return;
        }
        lastPlayCall = currentTime;
        try {
            if (clip != null) {
                clip.setAutoClosing(false);
                try {
                    if (!clip.isOpen()) {
                        clip.open(loadedAudioFormat, loadedAudio, 0,
                                  loadedAudioByteLength);
                    } else {
                        clip.flush();
                        if (loop != clipLooping) {
                            clip.stop();
                        }
                    }
                    clip.setFramePosition(0);
                    if (loop) {
                        clip.loop(Clip.LOOP_CONTINUOUSLY);
                    } else {
                        clip.start();
                    }
                    clipLooping = loop;
                } finally {
                    clip.setAutoClosing(true);
                }
            } else if (datapusher != null ) {
                datapusher.start(loop);

            } else if (sequencer != null) {
                sequencerloop = loop;
                if (sequencer.isRunning()) {
                    sequencer.setMicrosecondPosition(0);
                }
                if (!sequencer.isOpen()) {
                    try {
                        sequencer.open();
                        sequencer.setSequence(sequence);

                    } catch (InvalidMidiDataException | MidiUnavailableException e) {
                        if (Printer.err) e.printStackTrace();
                    }
                }
                sequencer.addMetaEventListener(this);
                try {
                    sequencer.start();
                } catch (Exception e) {
                    if (Printer.err) e.printStackTrace();
                }
            }
        } catch (Exception e) {
            if (Printer.err) e.printStackTrace();
        }
    }

    @Override
    public synchronized void stop() {
        if (!success) {
            return;
        }
        lastPlayCall = 0;

        if (clip != null) {
            try {
                clip.flush();
            } catch (Exception e1) {
                if (Printer.err) e1.printStackTrace();
            }
            try {
                clip.stop();
            } catch (Exception e2) {
                if (Printer.err) e2.printStackTrace();
            }
        } else if (datapusher != null) {
            datapusher.stop();
        } else if (sequencer != null) {
            try {
                sequencerloop = false;
                sequencer.removeMetaEventListener(this);
                sequencer.stop();
            } catch (Exception e3) {
                if (Printer.err) e3.printStackTrace();
            }
            try {
                sequencer.close();
            } catch (Exception e4) {
                if (Printer.err) e4.printStackTrace();
            }
        }
    }


    @Override
    public synchronized void update(LineEvent event) {
    }


    @Override
    public synchronized void meta(MetaMessage message) {
        if( message.getType() == 47 ) {
            if (sequencerloop){
                sequencer.setMicrosecondPosition(0);
                loop();
            } else {
                stop();
            }
        }
    }

    @Override
    public String toString() {
        return getClass().toString();
    }

    @Override
    @SuppressWarnings("removal")
    protected void finalize() {

        if (clip != null) {
            clip.close();
        }

        if (datapusher != null) {
            datapusher.close();
        }

        if (sequencer != null) {
            sequencer.close();
        }
    }


    private boolean loadAudioData(AudioInputStream as)  throws IOException, UnsupportedAudioFileException {
        as = Toolkit.getPCMConvertedAudioInputStream(as);
        if (as == null) {
            return false;
        }

        loadedAudioFormat = as.getFormat();
        long frameLen = as.getFrameLength();
        int frameSize = loadedAudioFormat.getFrameSize();
        long byteLen = AudioSystem.NOT_SPECIFIED;
        if (frameLen != AudioSystem.NOT_SPECIFIED
            && frameLen > 0
            && frameSize != AudioSystem.NOT_SPECIFIED
            && frameSize > 0) {
            byteLen = frameLen * frameSize;
        }
        if (byteLen != AudioSystem.NOT_SPECIFIED) {
            readStream(as, byteLen);
        } else {
            readStream(as);
        }

        return true;
    }

    private void readStream(AudioInputStream as, long byteLen) throws IOException {
        int intLen;
        if (byteLen > 2147483647) {
            intLen = 2147483647;
        } else {
            intLen = (int) byteLen;
        }
        loadedAudio = new byte[intLen];
        loadedAudioByteLength = 0;

        while (true) {
            int bytesRead = as.read(loadedAudio, loadedAudioByteLength, intLen - loadedAudioByteLength);
            if (bytesRead <= 0) {
                as.close();
                break;
            }
            loadedAudioByteLength += bytesRead;
        }
    }

    private void readStream(AudioInputStream as) throws IOException {

        DirectBAOS baos = new DirectBAOS();
        int totalBytesRead;
        try (as) {
            totalBytesRead = (int) as.transferTo(baos);
        }
        loadedAudio = baos.getInternalBuffer();
        loadedAudioByteLength = totalBytesRead;
    }


    private boolean createClip() {
        try {
            DataLine.Info info = new DataLine.Info(Clip.class, loadedAudioFormat);
            if (!(AudioSystem.isLineSupported(info)) ) {
                if (Printer.err) Printer.err("Clip not supported: "+loadedAudioFormat);
                return false;
            }
            Object line = AudioSystem.getLine(info);
            if (!(line instanceof AutoClosingClip)) {
                if (Printer.err) Printer.err("Clip is not auto closing!"+clip);
                return false;
            }
            clip = (AutoClosingClip) line;
            clip.setAutoClosing(true);
        } catch (Exception e) {
            if (Printer.err) e.printStackTrace();
            return false;
        }

        if (clip==null) {
            return false;
        }
        return true;
    }

    private boolean createSourceDataLine() {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, loadedAudioFormat);
            if (!(AudioSystem.isLineSupported(info)) ) {
                if (Printer.err) Printer.err("Line not supported: "+loadedAudioFormat);
                return false;
            }
            SourceDataLine source = (SourceDataLine) AudioSystem.getLine(info);
            datapusher = new DataPusher(source, loadedAudioFormat, loadedAudio, loadedAudioByteLength);
        } catch (Exception e) {
            if (Printer.err) e.printStackTrace();
            return false;
        }

        if (datapusher==null) {
            return false;
        }
        return true;
    }

    private boolean createSequencer(BufferedInputStream in) throws IOException {
        try {
            sequencer = MidiSystem.getSequencer( );
        } catch(MidiUnavailableException me) {
            if (Printer.err) me.printStackTrace();
            return false;
        }
        if (sequencer==null) {
            return false;
        }

        try {
            sequence = MidiSystem.getSequence(in);
            if (sequence == null) {
                return false;
            }
        } catch (InvalidMidiDataException e) {
            if (Printer.err) e.printStackTrace();
            return false;
        }
        return true;
    }

    /*
     * private inner class representing a ByteArrayOutputStream
     * which allows retrieval of the internal array
     */
    private static class DirectBAOS extends ByteArrayOutputStream {
        DirectBAOS() {
            super();
        }

        public byte[] getInternalBuffer() {
            return buf;
        }

    } 
}
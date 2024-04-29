/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4313887
 * @summary Unit test for java.nio.file.Files.newByteChannel
 * @library ..
 * @modules jdk.unsupported
 */

import java.nio.ByteBuffer;
import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;
import static com.sun.nio.file.ExtendedOpenOption.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.channels.*;
import java.io.IOException;
import java.util.*;

public class SBC {

    static boolean supportsSymbolicLinks;

    public static void main(String[] args) throws Exception {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            supportsSymbolicLinks = TestUtil.supportsSymbolicLinks(dir);

            createTests(dir);
            appendTests(dir);
            truncateExistingTests(dir);
            noFollowLinksTests(dir);

            sizeTruncatePositionTests(dir);

            if (System.getProperty("os.name").startsWith("Windows"))
                dosSharingOptionTests(dir);

            badCombinations(dir);
            unsupportedOptions(dir);
            nullTests(dir);

        } finally {
            TestUtil.removeAll(dir);
        }
    }

    static void createTests(Path dir) throws Exception {
        Path file = dir.resolve("foo");

        try {
            Files.newByteChannel(file, CREATE, WRITE).close();
            if (Files.notExists(file))
                throw new RuntimeException("File not created");

            Files.newByteChannel(file, CREATE, WRITE).close();

            if (supportsSymbolicLinks) {
                Path link = Files.createSymbolicLink(dir.resolve("link"), file);
                try {
                    Files.newByteChannel(link, CREATE, WRITE).close();

                    Files.delete(file);
                    Files.newByteChannel(link, CREATE, WRITE).close();
                    if (Files.notExists(file))
                        throw new RuntimeException("File not created");

                } finally {
                    TestUtil.deleteUnchecked(link);
                }
            }

        } finally {
            TestUtil.deleteUnchecked(file);
        }

        try {
            Files.newByteChannel(file, CREATE_NEW, WRITE).close();
            if (Files.notExists(file))
                throw new RuntimeException("File not created");

            try {
                SeekableByteChannel sbc =
                    Files.newByteChannel(file, CREATE_NEW, WRITE);
                sbc.close();
                throw new RuntimeException("FileAlreadyExistsException not thrown");
            } catch (FileAlreadyExistsException x) { }

            if (supportsSymbolicLinks) {
                Path link = dir.resolve("link");
                Path target = dir.resolve("thisDoesNotExist");
                Files.createSymbolicLink(link, target);
                try {

                    try {
                        SeekableByteChannel sbc =
                            Files.newByteChannel(file, CREATE_NEW, WRITE);
                        sbc.close();
                        throw new RuntimeException("FileAlreadyExistsException not thrown");
                    } catch (FileAlreadyExistsException x) { }

                } finally {
                    TestUtil.deleteUnchecked(link);
                }
            }


        } finally {
            TestUtil.deleteUnchecked(file);
        }

        try {
            try (SeekableByteChannel sbc = Files.newByteChannel(file, CREATE_NEW, WRITE, SPARSE)) {
                final long hole = 2L * 1024L * 1024L * 1024L;
                sbc.position(hole);
                write(sbc, "hello");
                long size = sbc.size();
                if (size != (hole + 5))
                    throw new RuntimeException("Unexpected size");
            }
        } finally {
            TestUtil.deleteUnchecked(file);
        }
    }

    static void appendTests(Path dir) throws Exception {
        Path file = dir.resolve("foo");
        try {
            try (SeekableByteChannel sbc = Files.newByteChannel(file, CREATE_NEW, WRITE, APPEND)) {
                write(sbc, "hello ");
                sbc.position(0L);
                write(sbc, "there");
            }

            try (Scanner s = new Scanner(file)) {
                String line = s.nextLine();
                if (!line.equals("hello there"))
                    throw new RuntimeException("Unexpected file contents");
            }

            try (SeekableByteChannel sbc = Files.newByteChannel(file, APPEND)) {
                sbc.read(ByteBuffer.allocate(100));
            } catch (NonReadableChannelException x) {
            }
        } finally {
            TestUtil.deleteUnchecked(file);
        }
    }

    static void truncateExistingTests(Path dir) throws Exception {
        Path file = dir.resolve("foo");
        try {
            try (SeekableByteChannel sbc = Files.newByteChannel(file, CREATE_NEW, WRITE)) {
                write(sbc, "Have a nice day!");
            }

            try (SeekableByteChannel sbc = Files.newByteChannel(file, WRITE, TRUNCATE_EXISTING)) {
                write(sbc, "Hello there!");
            }
            try (Scanner s = new Scanner(file)) {
                String line = s.nextLine();
                if (!line.equals("Hello there!"))
                    throw new RuntimeException("Unexpected file contents");
            }

            try (SeekableByteChannel sbc = Files.newByteChannel(file, WRITE, CREATE, TRUNCATE_EXISTING)) {
                long size = ((FileChannel)sbc).size();
                if (size != 0L)
                    throw new RuntimeException("File not truncated");
            }

        } finally {
            TestUtil.deleteUnchecked(file);
        }

    }

    static void noFollowLinksTests(Path dir) throws Exception {
        if (!supportsSymbolicLinks)
            return;
        Path file = Files.createFile(dir.resolve("foo"));
        try {
            Path link = dir.resolve("link");
            Files.createSymbolicLink(link, file);

            try {
                Files.newByteChannel(link, READ, LinkOption.NOFOLLOW_LINKS);
                throw new RuntimeException();
            } catch (IOException | UnsupportedOperationException x) {
            } finally {
                TestUtil.deleteUnchecked(link);
            }

        } finally {
            TestUtil.deleteUnchecked(file);
        }
    }

    static void sizeTruncatePositionTests(Path dir) throws Exception {
        Path file = dir.resolve("foo");
        try {
            try (SeekableByteChannel sbc = Files.newByteChannel(file, CREATE_NEW, READ, WRITE)) {
                if (sbc.size() != 0L)
                    throw new RuntimeException("Unexpected size");

                write(sbc, "hello");
                if (sbc.size() != 5L)
                    throw new RuntimeException("Unexpected size");

                sbc.truncate(4L);
                if (sbc.size() != 4L)
                    throw new RuntimeException("Unexpected size");
                if (sbc.position() != 4L)
                    throw new RuntimeException("Unexpected position");

                sbc.position(2L).truncate(3L);
                if (sbc.size() != 3L)
                    throw new RuntimeException("Unexpected size");
                if (sbc.position() != 2L)
                    throw new RuntimeException("Unexpected position");
            }
        } finally {
            TestUtil.deleteUnchecked(file);
        }
    }

    static void dosSharingOptionTests(Path dir) throws Exception {
        Path file = Files.createFile(dir.resolve("foo"));
        try {
            try (SeekableByteChannel ch = Files.newByteChannel(file, READ, NOSHARE_READ,
                                                               NOSHARE_WRITE, NOSHARE_DELETE))
            {
                try {
                    Files.newByteChannel(file, READ);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                try {
                    Files.newByteChannel(file, WRITE);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                try {
                    Files.delete(file);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
            }

            try (SeekableByteChannel ch = Files.newByteChannel(file, READ, NOSHARE_WRITE, NOSHARE_DELETE)) {
                Files.newByteChannel(file, READ).close();
                try {
                    Files.newByteChannel(file, WRITE);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                try {
                    Files.delete(file);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
            }

            try (SeekableByteChannel ch = Files.newByteChannel(file, READ, NOSHARE_READ, NOSHARE_DELETE)) {
                try {
                    Files.newByteChannel(file, READ);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                Files.newByteChannel(file, WRITE).close();
                try {
                    Files.delete(file);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
            }

            try (SeekableByteChannel ch = Files.newByteChannel(file, READ, NOSHARE_READ, NOSHARE_WRITE)) {
                try {
                    Files.newByteChannel(file, READ);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                try {
                    Files.newByteChannel(file, WRITE);
                    throw new RuntimeException("Sharing violation expected");
                } catch (IOException ignore) { }
                Files.delete(file);
            }

        } finally {
            TestUtil.deleteUnchecked(file);
        }
    }

    static void badCombinations(Path dir) throws Exception {
        Path file = dir.resolve("bad");

        try {
            Files.newByteChannel(file, READ, APPEND);
            throw new RuntimeException("IllegalArgumentException expected");
        } catch (IllegalArgumentException x) { }

        try {
            Files.newByteChannel(file, WRITE, APPEND, TRUNCATE_EXISTING);
            throw new RuntimeException("IllegalArgumentException expected");
        } catch (IllegalArgumentException x) { }
    }

    static void unsupportedOptions(Path dir) throws Exception {
        Path file = dir.resolve("bad");

        OpenOption badOption = new OpenOption() { };
        try {
            Files.newByteChannel(file, badOption);
            throw new RuntimeException("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException e) { }
        try {
            Files.newByteChannel(file, READ, WRITE, badOption);
            throw new RuntimeException("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException e) { }
    }

    static void nullTests(Path dir) throws Exception {
        Path file = dir.resolve("foo");

        try {
            OpenOption[] opts = { READ, null };
            Files.newByteChannel((Path)null, opts);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }

        try {
            Files.newByteChannel(file, (OpenOption[])null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }

        try {
            OpenOption[] opts = { READ, null };
            Files.newByteChannel(file, opts);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }

        try {
            Files.newByteChannel(file, (Set<OpenOption>)null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }

        try {
            Set<OpenOption> opts = new HashSet<>();
            opts.add(READ);
            opts.add(null);
            Files.newByteChannel(file, opts);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }

        try {
            EnumSet<StandardOpenOption> opts = EnumSet.of(READ);
            Files.newByteChannel(file, opts, (FileAttribute[])null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }

        try {
            EnumSet<StandardOpenOption> opts = EnumSet.of(READ);
            FileAttribute[] attrs = { null };
            Files.newByteChannel(file, opts, attrs);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException x) { }
    }

    static void write(WritableByteChannel wbc, String msg) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(msg.getBytes());
        while (buf.hasRemaining())
            wbc.write(buf);
    }
}
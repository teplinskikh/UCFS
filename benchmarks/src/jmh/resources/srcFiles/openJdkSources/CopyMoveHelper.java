/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.nio.file;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.spi.FileSystemProvider;

/**
 * Helper class to support copying or moving files when the source and target
 * are associated with different providers.
 */

class CopyMoveHelper {
    private CopyMoveHelper() { }

    /**
     * Parses the arguments for a file copy operation.
     */
    private static class CopyOptions {
        boolean replaceExisting = false;
        boolean copyAttributes = false;
        boolean followLinks = true;

        private CopyOptions() { }

        static CopyOptions parse(CopyOption... options) {
            CopyOptions result = new CopyOptions();
            for (CopyOption option: options) {
                if (option == StandardCopyOption.REPLACE_EXISTING) {
                    result.replaceExisting = true;
                    continue;
                }
                if (option == LinkOption.NOFOLLOW_LINKS) {
                    result.followLinks = false;
                    continue;
                }
                if (option == StandardCopyOption.COPY_ATTRIBUTES) {
                    result.copyAttributes = true;
                    continue;
                }
                if (option == null)
                    throw new NullPointerException();
                throw new UnsupportedOperationException("'" + option +
                    "' is not a recognized copy option");
            }
            return result;
        }
    }

    /**
     * Converts the given array of options for moving a file to options suitable
     * for copying the file when a move is implemented as copy + delete.
     */
    private static CopyOption[] convertMoveToCopyOptions(CopyOption... options)
        throws AtomicMoveNotSupportedException
    {
        int len = options.length;
        CopyOption[] newOptions = new CopyOption[len+2];
        for (int i=0; i<len; i++) {
            CopyOption option = options[i];
            if (option == StandardCopyOption.ATOMIC_MOVE) {
                throw new AtomicMoveNotSupportedException(null, null,
                    "Atomic move between providers is not supported");
            }
            newOptions[i] = option;
        }
        newOptions[len] = LinkOption.NOFOLLOW_LINKS;
        newOptions[len+1] = StandardCopyOption.COPY_ATTRIBUTES;
        return newOptions;
    }

    /**
     * Simple copy for use when source and target are associated with different
     * providers
     */
    static void copyToForeignTarget(Path source, Path target,
                                    CopyOption... options)
        throws IOException
    {
        CopyOptions opts = CopyOptions.parse(options);
        LinkOption[] linkOptions = (opts.followLinks) ? new LinkOption[0] :
            new LinkOption[] { LinkOption.NOFOLLOW_LINKS };

        final PosixFileAttributeView sourcePosixView =
            Files.getFileAttributeView(source, PosixFileAttributeView.class);

        BasicFileAttributes sourceAttrs = null;
        if (sourcePosixView != null) {
            try {
                sourceAttrs = Files.readAttributes(source,
                                                   PosixFileAttributes.class,
                                                   linkOptions);
            } catch (SecurityException ignored) {
            }
        }
        if (sourceAttrs == null)
            sourceAttrs = Files.readAttributes(source,
                                               BasicFileAttributes.class,
                                               linkOptions);
        assert sourceAttrs != null;

        if (sourceAttrs.isSymbolicLink())
            throw new IOException("Copying of symbolic links not supported");

        FileSystemProvider provider = source.getFileSystem().provider();
        provider.checkAccess(source, AccessMode.READ);

        if (opts.replaceExisting)
            Files.deleteIfExists(target);
        else if (Files.exists(target))
            throw new FileAlreadyExistsException(target.toString());

        if (sourceAttrs.isDirectory()) {
            Files.createDirectory(target);
        } else {
            try (InputStream in = Files.newInputStream(source)) {
                Files.copy(in, target);
            }
        }

        if (opts.copyAttributes) {
            BasicFileAttributeView targetView = null;
            if (sourcePosixView != null) {
                targetView = Files.getFileAttributeView(target,
                                                     PosixFileAttributeView.class);
            }

            if (targetView == null) {
                targetView = Files.getFileAttributeView(target,
                                                     BasicFileAttributeView.class);
            }

            try {
                targetView.setTimes(sourceAttrs.lastModifiedTime(),
                                 sourceAttrs.lastAccessTime(),
                                 sourceAttrs.creationTime());

                if (sourceAttrs instanceof PosixFileAttributes sourcePosixAttrs &&
                    targetView instanceof PosixFileAttributeView targetPosixView) {
                    try {
                        targetPosixView.setPermissions(sourcePosixAttrs.permissions());
                    } catch (SecurityException ignored) {
                    }
                }
            } catch (Throwable x) {
                try {
                    Files.delete(target);
                } catch (Throwable suppressed) {
                    x.addSuppressed(suppressed);
                }
                throw x;
            }
        }
    }

    /**
     * Simple move implements as copy+delete for use when source and target are
     * associated with different providers
     */
    static void moveToForeignTarget(Path source, Path target,
                                    CopyOption... options) throws IOException
    {
        copyToForeignTarget(source, target, convertMoveToCopyOptions(options));
        Files.delete(source);
    }
}
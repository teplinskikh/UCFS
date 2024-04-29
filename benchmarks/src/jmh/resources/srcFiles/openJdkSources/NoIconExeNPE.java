/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;

/*
 * @test
 * @bug 8320692
 * @requires (os.family == "windows")
 * @summary NullPointerException is thrown for .exe file without icon
 * @run main/othervm NoIconExeNPE
 */
public class NoIconExeNPE {

    /**
     * Bytes of a short {@code Hello.exe} which has no icon.
     */
    private static final byte[] bytes = {
            (byte) 0x4d, (byte) 0x5a, (byte) 0x80, (byte) 0x00, (byte) 0x01,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x00,
            (byte) 0x10, (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0x00,
            (byte) 0x00, (byte) 0x40, (byte) 0x01, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0e,
            (byte) 0x1f, (byte) 0xba, (byte) 0x0e, (byte) 0x00, (byte) 0xb4,
            (byte) 0x09, (byte) 0xcd, (byte) 0x21, (byte) 0xb8, (byte) 0x01,
            (byte) 0x4c, (byte) 0xcd, (byte) 0x21, (byte) 0x54, (byte) 0x68,
            (byte) 0x69, (byte) 0x73, (byte) 0x20, (byte) 0x70, (byte) 0x72,
            (byte) 0x6f, (byte) 0x67, (byte) 0x72, (byte) 0x61, (byte) 0x6d,
            (byte) 0x20, (byte) 0x63, (byte) 0x61, (byte) 0x6e, (byte) 0x6e,
            (byte) 0x6f, (byte) 0x74, (byte) 0x20, (byte) 0x62, (byte) 0x65,
            (byte) 0x20, (byte) 0x72, (byte) 0x75, (byte) 0x6e, (byte) 0x20,
            (byte) 0x69, (byte) 0x6e, (byte) 0x20, (byte) 0x44, (byte) 0x4f,
            (byte) 0x53, (byte) 0x20, (byte) 0x6d, (byte) 0x6f, (byte) 0x64,
            (byte) 0x65, (byte) 0x2e, (byte) 0x0d, (byte) 0x0a, (byte) 0x24,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x50, (byte) 0x45,
            (byte) 0x00, (byte) 0x00, (byte) 0x4c, (byte) 0x01, (byte) 0x01,
            (byte) 0x00, (byte) 0x55, (byte) 0x96, (byte) 0x96, (byte) 0x65,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xe0, (byte) 0x00,
            (byte) 0x0f, (byte) 0x01, (byte) 0x0b, (byte) 0x01, (byte) 0x01,
            (byte) 0x49, (byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x10,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x10, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x40, (byte) 0x00, (byte) 0x00,
            (byte) 0x10, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
            (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x20,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x00,
            (byte) 0x00, (byte) 0xac, (byte) 0xa3, (byte) 0x00, (byte) 0x00,
            (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00,
            (byte) 0x10, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x10,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x10,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x40, (byte) 0x10, (byte) 0x00, (byte) 0x00,
            (byte) 0x86, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x2e, (byte) 0x74, (byte) 0x65, (byte) 0x78,
            (byte) 0x74, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xc6,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x10,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x00,
            (byte) 0x60, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x6a, (byte) 0x00, (byte) 0x68,
            (byte) 0x29, (byte) 0x10, (byte) 0x40, (byte) 0x00, (byte) 0x68,
            (byte) 0x1c, (byte) 0x10, (byte) 0x40, (byte) 0x00, (byte) 0x6a,
            (byte) 0x00, (byte) 0xff, (byte) 0x15, (byte) 0x88, (byte) 0x10,
            (byte) 0x40, (byte) 0x00, (byte) 0x6a, (byte) 0x00, (byte) 0xff,
            (byte) 0x15, (byte) 0x80, (byte) 0x10, (byte) 0x40, (byte) 0x00,
            (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f,
            (byte) 0x20, (byte) 0x57, (byte) 0x6f, (byte) 0x72, (byte) 0x6c,
            (byte) 0x64, (byte) 0x21, (byte) 0x00, (byte) 0x48, (byte) 0x65,
            (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x20, (byte) 0x66,
            (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x00, (byte) 0x90,
            (byte) 0x90, (byte) 0x90, (byte) 0x90, (byte) 0x90, (byte) 0x90,
            (byte) 0x90, (byte) 0x90, (byte) 0x90, (byte) 0x90, (byte) 0x90,
            (byte) 0x90, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x90, (byte) 0x10,
            (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x10, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x9d, (byte) 0x10,
            (byte) 0x00, (byte) 0x00, (byte) 0x88, (byte) 0x10, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x90, (byte) 0x90, (byte) 0x90, (byte) 0x90,
            (byte) 0xa8, (byte) 0x10, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xb8, (byte) 0x10,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x6b, (byte) 0x65, (byte) 0x72, (byte) 0x6e,
            (byte) 0x65, (byte) 0x6c, (byte) 0x33, (byte) 0x32, (byte) 0x2e,
            (byte) 0x64, (byte) 0x6c, (byte) 0x6c, (byte) 0x00, (byte) 0x75,
            (byte) 0x73, (byte) 0x65, (byte) 0x72, (byte) 0x33, (byte) 0x32,
            (byte) 0x2e, (byte) 0x64, (byte) 0x6c, (byte) 0x6c, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x45, (byte) 0x78, (byte) 0x69,
            (byte) 0x74, (byte) 0x50, (byte) 0x72, (byte) 0x6f, (byte) 0x63,
            (byte) 0x65, (byte) 0x73, (byte) 0x73, (byte) 0x00, (byte) 0x90,
            (byte) 0x90, (byte) 0x00, (byte) 0x00, (byte) 0x4d, (byte) 0x65,
            (byte) 0x73, (byte) 0x73, (byte) 0x61, (byte) 0x67, (byte) 0x65,
            (byte) 0x42, (byte) 0x6f, (byte) 0x78, (byte) 0x41, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };

    public static void main(String[] args) throws Exception {
        final Path temp = Files.createTempDirectory("no-icon");
        final Path hello = temp.resolve("Hello.exe");

        try {
            try (OutputStream out = Files.newOutputStream(hello)) {
                out.write(bytes);
            }

            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            FileSystemView fsv = FileSystemView.getFileSystemView();
            Icon icon = fsv.getSystemIcon(hello.toFile());
            if (icon == null) {
                throw new RuntimeException("Null icon returned by FileSystemView.getSystemIcon()");
            }
        } finally {
            Files.deleteIfExists(hello);
            Files.delete(temp);
        }
    }
}
/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8142968 8300228
 * @library /test/lib
 * @modules java.base/jdk.internal.module
 *          jdk.compiler
 *          jdk.jlink
 * @build ModuleReaderTest
 *        jdk.test.lib.compiler.CompilerUtils
 *        jdk.test.lib.util.JarUtils
 * @run testng ModuleReaderTest
 * @summary Basic tests for java.lang.module.ModuleReader
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import jdk.internal.module.ModulePath;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.util.JarUtils;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ModuleReaderTest {
    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path USER_DIR   = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_DIR    = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR   = Paths.get("mods");

    private static final String BASE_MODULE = "java.base";

    private static final String TEST_MODULE = "m";

    private static final String[] BASE_RESOURCES = {
        "java/lang/Object.class"
    };

    private static final String[] MAYBE_BASE_RESOURCES = {
        "java",
        "java/",
        "java/lang",
        "java/lang/",
    };

    private static final String[] NOT_BASE_RESOURCES = {
        "NotFound",
        "/java",
        "
        "/java/lang",
        "
        "java
        "/java/lang/Object.class",
        "
        "java/lang/Object.class/",
        "java
        "./java/lang/Object.class",
        "java/./lang/Object.class",
        "java/lang/./Object.class",
        "../java/lang/Object.class",
        "java/../lang/Object.class",
        "java/lang/../Object.class",

        "java\u0000",
        "C:java",
        "C:\\java",
        "java\\lang\\Object.class"
    };

    private static final String[] TEST_RESOURCES = {
        "p/Main.class"
    };

    private static final String[] MAYBE_TEST_RESOURCES = {
        "p",
        "p/"
    };

    private static final String[] NOT_TEST_RESOURCES = {
        "NotFound",
        "/p",
        "
        "/p/Main.class",
        "
        "p/Main.class/",
        "p
        "./p/Main.class",
        "p/./Main.class",
        "../p/Main.class",
        "p/../p/Main.class",

        "p\u0000",
        "C:p",
        "C:\\p",
        "p\\Main.class"
    };

    @BeforeTest
    public void compileTestModule() throws Exception {
        boolean compiled = CompilerUtils.compile(SRC_DIR.resolve(TEST_MODULE),
                                                 MODS_DIR.resolve(TEST_MODULE));
        assertTrue(compiled, "test module did not compile");
    }

    /**
     * Test ModuleReader with module in runtime image.
     */
    @Test
    public void testImage() throws IOException {
        ModuleFinder finder = ModuleFinder.ofSystem();
        ModuleReference mref = finder.find(BASE_MODULE).get();
        ModuleReader reader = mref.open();

        try (reader) {

            for (String name : BASE_RESOURCES) {
                byte[] expectedBytes;
                Module baseModule = Object.class.getModule();
                try (InputStream in = baseModule.getResourceAsStream(name)) {
                    expectedBytes = in.readAllBytes();
                }

                testFind(reader, name, expectedBytes);
                testOpen(reader, name, expectedBytes);
                testRead(reader, name, expectedBytes);
                testList(reader, name);
            }

            for (String name : MAYBE_BASE_RESOURCES) {
                Optional<URI> ouri = reader.find(name);
                ouri.ifPresent(uri -> {
                    if (name.endsWith("/"))
                        assertTrue(uri.toString().endsWith("/"));
                });
            }

            for (String name : NOT_BASE_RESOURCES) {
                assertFalse(reader.find(name).isPresent());
                assertFalse(reader.open(name).isPresent());
                assertFalse(reader.read(name).isPresent());
            }

            try {
                reader.find(null);
                assertTrue(false);
            } catch (NullPointerException expected) { }

            try {
                reader.open(null);
                assertTrue(false);
            } catch (NullPointerException expected) { }

            try {
                reader.read(null);
                assertTrue(false);
            } catch (NullPointerException expected) { }

            try {
                reader.release(null);
                assertTrue(false);
            } catch (NullPointerException expected) { }

        }

        try {
            reader.open(BASE_RESOURCES[0]);
            assertTrue(false);
        } catch (IOException expected) { }


        try {
            reader.read(BASE_RESOURCES[0]);
            assertTrue(false);
        } catch (IOException expected) { }
    }

    /**
     * Test ModuleReader with exploded module.
     */
    @Test
    public void testExplodedModule() throws IOException {
        test(MODS_DIR);
    }

    /**
     * Test ModuleReader with module in modular JAR.
     */
    @Test
    public void testModularJar() throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mlib");

        JarUtils.createJarFile(dir.resolve("m.jar"),
                               MODS_DIR.resolve(TEST_MODULE));

        test(dir);
    }

    /**
     * Test ModuleReader with module in a JMOD file.
     */
    @Test
    public void testJMod() throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mlib");

        String cp = MODS_DIR.resolve(TEST_MODULE).toString();
        String jmod = dir.resolve("m.jmod").toString();
        String[] args = { "create", "--class-path", cp, jmod };
        ToolProvider jmodTool = ToolProvider.findFirst("jmod")
            .orElseThrow(() ->
                new RuntimeException("jmod tool not found")
            );
        assertEquals(jmodTool.run(System.out, System.out, args), 0);

        test(dir);
    }

    /**
     * The test module is found on the given module path. Open a ModuleReader
     * to the test module and test the reader.
     */
    void test(Path mp) throws IOException {
        ModuleFinder finder = ModulePath.of(Runtime.version(), true, mp);
        ModuleReference mref = finder.find(TEST_MODULE).get();
        ModuleReader reader = mref.open();

        try (reader) {

            for (String name : TEST_RESOURCES) {
                System.out.println("resource: " + name);
                byte[] expectedBytes
                    = Files.readAllBytes(MODS_DIR
                        .resolve(TEST_MODULE)
                        .resolve(name.replace('/', File.separatorChar)));

                testFind(reader, name, expectedBytes);
                testOpen(reader, name, expectedBytes);
                testRead(reader, name, expectedBytes);
                testList(reader, name);
            }

            for (String name : MAYBE_TEST_RESOURCES) {
                System.out.println("resource: " + name);
                Optional<URI> ouri = reader.find(name);
                ouri.ifPresent(uri -> {
                    if (name.endsWith("/"))
                        assertTrue(uri.toString().endsWith("/"));
                });
            }

            for (String name : NOT_TEST_RESOURCES) {
                System.out.println("resource: " + name);
                assertFalse(reader.find(name).isPresent());
                assertFalse(reader.open(name).isPresent());
                assertFalse(reader.read(name).isPresent());
            }

            try {
                reader.find(null);
                assertTrue(false);
            } catch (NullPointerException expected) { }

            try {
                reader.open(null);
                assertTrue(false);
            } catch (NullPointerException expected) { }

            try {
                reader.read(null);
                assertTrue(false);
            } catch (NullPointerException expected) { }

            try {
                reader.release(null);
                throw new RuntimeException();
            } catch (NullPointerException expected) { }

        }

        try {
            reader.open(TEST_RESOURCES[0]);
            assertTrue(false);
        } catch (IOException expected) { }


        try {
            reader.read(TEST_RESOURCES[0]);
            assertTrue(false);
        } catch (IOException expected) { }

        try {
            reader.list();
            assertTrue(false);
        } catch (IOException expected) { }
    }

    /**
     * Test ModuleReader#find
     */
    void testFind(ModuleReader reader, String name, byte[] expectedBytes)
        throws IOException
    {
        Optional<URI> ouri = reader.find(name);
        assertTrue(ouri.isPresent());

        URL url = ouri.get().toURL();
        if (!url.getProtocol().equalsIgnoreCase("jmod")) {
            URLConnection uc = url.openConnection();
            uc.setUseCaches(false);
            try (InputStream in = uc.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                assertTrue(Arrays.equals(bytes, expectedBytes));
            }
        }
    }

    /**
     * Test ModuleReader#open
     */
    void testOpen(ModuleReader reader, String name, byte[] expectedBytes)
        throws IOException
    {
        Optional<InputStream> oin = reader.open(name);
        assertTrue(oin.isPresent());

        InputStream in = oin.get();
        try (in) {
            byte[] bytes = in.readAllBytes();
            assertTrue(Arrays.equals(bytes, expectedBytes));
        }
    }

    /**
     * Test ModuleReader#read
     */
    void testRead(ModuleReader reader, String name, byte[] expectedBytes)
        throws IOException
    {
        Optional<ByteBuffer> obb = reader.read(name);
        assertTrue(obb.isPresent());

        ByteBuffer bb = obb.get();
        try {
            int rem = bb.remaining();
            assertTrue(rem == expectedBytes.length);
            byte[] bytes = new byte[rem];
            bb.get(bytes);
            assertTrue(Arrays.equals(bytes, expectedBytes));
        } finally {
            reader.release(bb);
        }
    }

    /**
     * Test ModuleReader#list
     */
    void testList(ModuleReader reader, String name) throws IOException {
        final List<String> list;
        try (Stream<String> stream = reader.list()) {
            list = stream.toList();
        }
        Set<String> names = new HashSet<>(list);
        assertTrue(names.size() == list.size()); 

        assertTrue(names.contains("module-info.class"));
        assertTrue(names.contains(name));

        for (String e : names) {
            assertTrue(reader.find(e).isPresent());
        }
    }

}
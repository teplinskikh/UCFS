/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4085756
 * @summary Ensure readObject() works when InputStream.available() is not implemented.
 *          In JDK 1.1.x, Win32 System Console available() thows IOException
 *          to denote that it is not implemented.
 */

import java.io.*;

public class NotAvailable {
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream baos;
        ObjectOutput out;
        try {
            baos = new ByteArrayOutputStream();
            out = new ObjectOutputStream(baos);
            out.writeObject(new Class1(22,33));
            out.writeObject(new Class1(22,33));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }

        ObjectInputStream in = null;
        try {
            ByteArrayInputStream bois =
                new ByteArrayInputStream(baos.toByteArray()) {
                  /* simulate available() not being implemented. */
                public int available() {
                      throw new Error("available() is not implemented");
                  }
                };
            in = new ObjectInputStream(bois);
            Class1 cc1 = (Class1) in.readObject();
            Class1 cc2 = (Class1) in.readObject();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
        }
    }
}

class Class1 implements Serializable {
    private static final long serialVersionUID = 1L;

    int a, b;

    public Class1(int aa, int bb) {
        a = aa;
        b = bb;
    }
}
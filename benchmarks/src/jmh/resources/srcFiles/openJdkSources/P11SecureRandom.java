/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs11;

import java.io.*;
import java.security.*;
import sun.security.pkcs11.wrapper.*;

/**
 * SecureRandom implementation class. Some tokens support only
 * C_GenerateRandom() and not C_SeedRandom(). In order not to lose an
 * application specified seed, we create a SHA1PRNG that we mix with in that
 * case.
 *
 * Note that since SecureRandom is thread safe, we only need one
 * instance per PKCS#11 token instance. It is created on demand and cached
 * in the SunPKCS11 class.
 *
 * Also note that we obtain the PKCS#11 session on demand, no need to tie one
 * up.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class P11SecureRandom extends SecureRandomSpi {

    @Serial
    private static final long serialVersionUID = -8939510236124553291L;

    private final Token token;

    private volatile SecureRandom mixRandom;

    private byte[] mixBuffer;

    private int buffered;

    /*
     * we buffer data internally for efficiency but limit the lifetime
     * to avoid using stale bits.
     */
    private static final long MAX_IBUFFER_TIME = 100;

    private static final int IBUFFER_SIZE = 32;

    private transient byte[] iBuffer = new byte[IBUFFER_SIZE];

    private transient int ibuffered = 0;

    private transient long lastRead = 0L;

    P11SecureRandom(Token token) {
        this.token = token;
    }

    @Override
    protected synchronized void engineSetSeed(byte[] seed) {
        Session session = null;
        try {
            session = token.getOpSession();
            token.p11.C_SeedRandom(session.id(), seed);
        } catch (PKCS11Exception e) {
            SecureRandom random = mixRandom;
            if (random != null) {
                random.setSeed(seed);
            } else {
                try {
                    mixBuffer = new byte[20];
                    random = SecureRandom.getInstance("SHA1PRNG");
                    random.setSeed(seed);
                    mixRandom = random;
                } catch (NoSuchAlgorithmException ee) {
                    throw new ProviderException(ee);
                }
            }
        } finally {
            token.releaseSession(session);
        }
    }

    @Override
    protected void engineNextBytes(byte[] bytes) {
        if (bytes.length == 0) {
            return;
        }
        if (bytes.length <= IBUFFER_SIZE)  {
            int ofs = 0;
            synchronized (iBuffer) {
                while (ofs < bytes.length) {
                    long time = System.currentTimeMillis();
                    if ((ibuffered == 0) ||
                            !(time - lastRead < MAX_IBUFFER_TIME)) {
                        lastRead = time;
                        implNextBytes(iBuffer);
                        ibuffered = IBUFFER_SIZE;
                    }
                    while ((ofs < bytes.length) && (ibuffered > 0)) {
                        bytes[ofs++] = iBuffer[IBUFFER_SIZE - ibuffered--];
                    }
                }
            }
        } else {
            implNextBytes(bytes);
        }

    }

    @Override
    protected byte[] engineGenerateSeed(int numBytes) {
        byte[] b = new byte[numBytes];
        engineNextBytes(b);
        return b;
    }

    private void mix(byte[] b) {
        SecureRandom random = mixRandom;
        if (random == null) {
            return;
        }
        synchronized (this) {
            int ofs = 0;
            int len = b.length;
            while (len-- > 0) {
                if (buffered == 0) {
                    random.nextBytes(mixBuffer);
                    buffered = mixBuffer.length;
                }
                b[ofs++] ^= mixBuffer[mixBuffer.length - buffered];
                buffered--;
            }
        }
    }

    private void implNextBytes(byte[] bytes) {
        Session session = null;
        try {
            session = token.getOpSession();
            token.p11.C_GenerateRandom(session.id(), bytes);
            mix(bytes);
        } catch (PKCS11Exception e) {
            throw new ProviderException("nextBytes() failed", e);
        } finally {
            token.releaseSession(session);
        }
    }

    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        iBuffer = new byte[IBUFFER_SIZE];
        ibuffered = 0;
        lastRead = 0L;
    }
}
/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Set;

/**
 * This class delegates to a primary or secondary keystore implementation.
 *
 * @since 9
 */

public class KeyStoreDelegator extends KeyStoreSpi {

    private static final String KEYSTORE_TYPE_COMPAT = "keystore.type.compat";
    private static final Debug debug = Debug.getInstance("keystore");

    private final String primaryType;   
    private final String secondaryType; 
    private final Class<? extends KeyStoreSpi> primaryKeyStore;
    private final Class<? extends KeyStoreSpi> secondaryKeyStore;
    private String type; 
    private KeyStoreSpi keystore; 
    private final boolean compatModeEnabled;

    public KeyStoreDelegator(
        String primaryType,
        Class<? extends KeyStoreSpi> primaryKeyStore,
        String secondaryType,
        Class<? extends KeyStoreSpi> secondaryKeyStore) {

        @SuppressWarnings("removal")
        var prop = AccessController.doPrivileged((PrivilegedAction<String>) () ->
                        Security.getProperty(KEYSTORE_TYPE_COMPAT));
        compatModeEnabled = "true".equalsIgnoreCase(prop);

        if (compatModeEnabled) {
            this.primaryType = primaryType;
            this.secondaryType = secondaryType;
            this.primaryKeyStore = primaryKeyStore;
            this.secondaryKeyStore = secondaryKeyStore;
        } else {
            this.primaryType = primaryType;
            this.secondaryType = null;
            this.primaryKeyStore = primaryKeyStore;
            this.secondaryKeyStore = null;

            if (debug != null) {
                debug.println("WARNING: compatibility mode disabled for " +
                    primaryType + " and " + secondaryType + " keystore types");
            }
        }
    }

    @Override
    public Key engineGetKey(String alias, char[] password)
        throws NoSuchAlgorithmException, UnrecoverableKeyException {
        return keystore.engineGetKey(alias, password);
    }

    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
        return keystore.engineGetCertificateChain(alias);
    }

    @Override
    public Certificate engineGetCertificate(String alias) {
        return keystore.engineGetCertificate(alias);
    }

    @Override
    public Date engineGetCreationDate(String alias) {
        return keystore.engineGetCreationDate(alias);
    }

    @Override
    public void engineSetKeyEntry(String alias, Key key, char[] password,
        Certificate[] chain) throws KeyStoreException {
        keystore.engineSetKeyEntry(alias, key, password, chain);
    }

    @Override
    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain)
        throws KeyStoreException {
        keystore.engineSetKeyEntry(alias, key, chain);
    }

    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert)
        throws KeyStoreException {
        keystore.engineSetCertificateEntry(alias, cert);
    }

    @Override
    public void engineDeleteEntry(String alias) throws KeyStoreException {
        keystore.engineDeleteEntry(alias);
    }

    @Override
    public Set<KeyStore.Entry.Attribute> engineGetAttributes(String alias) {
        return keystore.engineGetAttributes(alias);
    }

    @Override
    public Enumeration<String> engineAliases() {
        return keystore.engineAliases();
    }

    @Override
    public boolean engineContainsAlias(String alias) {
        return keystore.engineContainsAlias(alias);
    }

    @Override
    public int engineSize() {
        return keystore.engineSize();
    }

    @Override
    public boolean engineIsKeyEntry(String alias) {
        return keystore.engineIsKeyEntry(alias);
    }

    @Override
    public boolean engineIsCertificateEntry(String alias) {
        return keystore.engineIsCertificateEntry(alias);
    }

    @Override
    public String engineGetCertificateAlias(Certificate cert) {
        return keystore.engineGetCertificateAlias(cert);
    }

    @Override
    public KeyStore.Entry engineGetEntry(String alias,
        KeyStore.ProtectionParameter protParam)
            throws KeyStoreException, NoSuchAlgorithmException,
                UnrecoverableEntryException {
        return keystore.engineGetEntry(alias, protParam);
    }

    @Override
    public void engineSetEntry(String alias, KeyStore.Entry entry,
        KeyStore.ProtectionParameter protParam)
            throws KeyStoreException {
        keystore.engineSetEntry(alias, entry, protParam);
    }

    @Override
    public boolean engineEntryInstanceOf(String alias,
        Class<? extends KeyStore.Entry> entryClass) {
        return keystore.engineEntryInstanceOf(alias, entryClass);
    }

    @Override
    public void engineStore(OutputStream stream, char[] password)
        throws IOException, NoSuchAlgorithmException, CertificateException {

        if (debug != null) {
            debug.println("Storing keystore in " + type + " format");
        }
        keystore.engineStore(stream, password);
    }

    @Override
    public void engineLoad(InputStream stream, char[] password)
        throws IOException, NoSuchAlgorithmException, CertificateException {

        if (stream == null) {
            try {
                @SuppressWarnings("deprecation")
                KeyStoreSpi tmp = primaryKeyStore.newInstance();
                keystore = tmp;
            } catch (InstantiationException | IllegalAccessException e) {
            }
            type = primaryType;

            if (debug != null) {
                debug.println("Creating a new keystore in " + type + " format");
            }
            keystore.engineLoad(stream, password);

        } else {
            InputStream bufferedStream = new BufferedInputStream(stream);
            bufferedStream.mark(Integer.MAX_VALUE);

            try {
                @SuppressWarnings("deprecation")
                KeyStoreSpi tmp = primaryKeyStore.newInstance();
                tmp.engineLoad(bufferedStream, password);
                keystore = tmp;
                type = primaryType;

            } catch (Exception e) {

                if (e instanceof IOException &&
                    e.getCause() instanceof UnrecoverableKeyException) {
                    throw (IOException)e;
                }

                try {
                    if (!compatModeEnabled) {
                        throw e;
                    }

                    @SuppressWarnings("deprecation")
                    KeyStoreSpi tmp = secondaryKeyStore.newInstance();
                    bufferedStream.reset();
                    tmp.engineLoad(bufferedStream, password);
                    keystore = tmp;
                    type = secondaryType;

                    if (debug != null) {
                        debug.println("WARNING: switching from " +
                          primaryType + " to " + secondaryType +
                          " keystore file format has altered the " +
                          "keystore security level");
                    }

                } catch (InstantiationException |
                    IllegalAccessException e2) {

                } catch (IOException |
                    NoSuchAlgorithmException |
                    CertificateException e3) {

                    if (e3 instanceof IOException &&
                        e3.getCause() instanceof UnrecoverableKeyException) {
                        throw (IOException)e3;
                    }
                    if (e instanceof IOException) {
                        throw (IOException)e;
                    } else if (e instanceof CertificateException) {
                        throw (CertificateException)e;
                    } else if (e instanceof NoSuchAlgorithmException) {
                        throw (NoSuchAlgorithmException)e;
                    } else if (e instanceof RuntimeException){
                        throw (RuntimeException)e;
                    }
                }
            }

            if (debug != null) {
                debug.println("Loaded a keystore in " + type + " format");
            }
        }
    }

    /**
     * Probe the first few bytes of the keystore data stream for a valid
     * keystore encoding. Only the primary keystore implementation is probed.
     */
    @Override
    public boolean engineProbe(InputStream stream) throws IOException {

        boolean result = false;

        try {
            @SuppressWarnings("deprecation")
            KeyStoreSpi tmp = primaryKeyStore.newInstance();
            keystore = tmp;
            type = primaryType;
            result = keystore.engineProbe(stream);

        } catch (Exception e) {
            throw new IOException(e);

        } finally {
            if (!result) {
                type = null;
                keystore = null;
            }
        }

        return result;
    }
}
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

package com.sun.crypto.provider;

import java.util.Arrays;
import java.util.Locale;

import java.security.*;
import java.security.interfaces.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.MGF1ParameterSpec;

import javax.crypto.*;
import javax.crypto.spec.PSource;
import javax.crypto.spec.OAEPParameterSpec;

import sun.security.rsa.*;
import sun.security.jca.Providers;
import sun.security.internal.spec.TlsRsaPremasterSecretParameterSpec;
import sun.security.util.KeyUtil;

/**
 * RSA cipher implementation. Supports RSA en/decryption and signing/verifying
 * using both PKCS#1 v1.5 and OAEP (v2.2) paddings and without padding (raw RSA).
 * Note that raw RSA is supported mostly for completeness and should only be
 * used in rare cases.
 *
 * Objects should be instantiated by calling Cipher.getInstance() using the
 * following algorithm names:
 *  . "RSA/ECB/PKCS1Padding" (or "RSA") for PKCS#1 v1.5 padding.
 *  . "RSA/ECB/OAEPwith<hash>andMGF1Padding" (or "RSA/ECB/OAEPPadding") for
 *    PKCS#1 v2.2 padding.
 *  . "RSA/ECB/NoPadding" for rsa RSA.
 *
 * We only do one RSA operation per doFinal() call. If the application passes
 * more data via calls to update() or doFinal(), we throw an
 * IllegalBlockSizeException when doFinal() is called (see JCE API spec).
 * Bulk encryption using RSA does not make sense and is not standardized.
 *
 * Note: RSA keys should be at least 512 bits long
 *
 * @since   1.5
 * @author  Andreas Sterbenz
 */
public final class RSACipher extends CipherSpi {

    private static final byte[] B0 = new byte[0];

    private static final int MODE_ENCRYPT = 1;
    private static final int MODE_DECRYPT = 2;
    private static final int MODE_SIGN    = 3;
    private static final int MODE_VERIFY  = 4;

    private static final String PAD_NONE  = "NoPadding";
    private static final String PAD_PKCS1 = "PKCS1Padding";
    private static final String PAD_OAEP_MGF1  = "OAEP";

    private int mode;

    private String paddingType;

    private RSAPadding padding;

    private AlgorithmParameterSpec spec = null;
    private boolean forTlsPremasterSecret = false;

    private byte[] buffer;
    private int bufOfs;

    private int outputSize;

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    private String oaepHashAlgorithm = "SHA-1";

    private SecureRandom random;

    public RSACipher() {
        paddingType = PAD_PKCS1;
    }

    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        if (mode.equalsIgnoreCase("ECB") == false) {
            throw new NoSuchAlgorithmException("Unsupported mode " + mode);
        }
    }

    protected void engineSetPadding(String paddingName)
            throws NoSuchPaddingException {
        if (paddingName.equalsIgnoreCase(PAD_NONE)) {
            paddingType = PAD_NONE;
        } else if (paddingName.equalsIgnoreCase(PAD_PKCS1)) {
            paddingType = PAD_PKCS1;
        } else {
            String lowerPadding = paddingName.toLowerCase(Locale.ENGLISH);
            if (lowerPadding.equals("oaeppadding")) {
                paddingType = PAD_OAEP_MGF1;
            } else if (lowerPadding.startsWith("oaepwith") &&
                       lowerPadding.endsWith("andmgf1padding")) {
                paddingType = PAD_OAEP_MGF1;
                oaepHashAlgorithm =
                        paddingName.substring(8, paddingName.length() - 14);
                if (Providers.getProviderList().getService
                        ("MessageDigest", oaepHashAlgorithm) == null) {
                    throw new NoSuchPaddingException
                        ("MessageDigest not available for " + paddingName);
                }
            } else {
                throw new NoSuchPaddingException
                    ("Padding " + paddingName + " not supported");
            }
        }
    }

    protected int engineGetBlockSize() {
        return 0;
    }

    protected int engineGetOutputSize(int inputLen) {
        return outputSize;
    }

    protected byte[] engineGetIV() {
        return null;
    }

    protected AlgorithmParameters engineGetParameters() {
        if (spec != null && spec instanceof OAEPParameterSpec) {
            try {
                AlgorithmParameters params =
                    AlgorithmParameters.getInstance("OAEP",
                        SunJCE.getInstance());
                params.init(spec);
                return params;
            } catch (NoSuchAlgorithmException nsae) {
                throw new RuntimeException("Cannot find OAEP " +
                    " AlgorithmParameters implementation in SunJCE provider");
            } catch (InvalidParameterSpecException ipse) {
                throw new RuntimeException("OAEPParameterSpec not supported");
            }
        } else {
            return null;
        }
    }

    protected void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
        try {
            init(opmode, key, random, null);
        } catch (InvalidAlgorithmParameterException iape) {
            throw new InvalidKeyException("Wrong parameters", iape);
        }
    }

    protected void engineInit(int opmode, Key key,
            AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        init(opmode, key, random, params);
    }

    protected void engineInit(int opmode, Key key,
            AlgorithmParameters params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (params == null) {
            init(opmode, key, random, null);
        } else {
            try {
                OAEPParameterSpec spec =
                        params.getParameterSpec(OAEPParameterSpec.class);
                init(opmode, key, random, spec);
            } catch (InvalidParameterSpecException ipse) {
                throw new InvalidAlgorithmParameterException("Wrong parameter", ipse);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void init(int opmode, Key key, SecureRandom random,
            AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        boolean encrypt;
        switch (opmode) {
        case Cipher.ENCRYPT_MODE:
        case Cipher.WRAP_MODE:
            encrypt = true;
            break;
        case Cipher.DECRYPT_MODE:
        case Cipher.UNWRAP_MODE:
            encrypt = false;
            break;
        default:
            throw new AssertionError("Unknown mode: " + opmode);
        }
        RSAKey rsaKey = RSAKeyFactory.toRSAKey(key);
        if (rsaKey instanceof RSAPublicKey) {
            mode = encrypt ? MODE_ENCRYPT : MODE_VERIFY;
            publicKey = (RSAPublicKey)rsaKey;
            privateKey = null;
        } else { 
            mode = encrypt ? MODE_SIGN : MODE_DECRYPT;
            privateKey = (RSAPrivateKey)rsaKey;
            publicKey = null;
        }
        int n = RSACore.getByteLength(rsaKey.getModulus());
        outputSize = n;
        bufOfs = 0;
        if (paddingType == PAD_NONE) {
            if (params != null) {
                throw new InvalidAlgorithmParameterException
                ("Parameters not supported");
            }
            padding = RSAPadding.getInstance(RSAPadding.PAD_NONE, n, random);
            buffer = new byte[n];
        } else if (paddingType == PAD_PKCS1) {
            if (params != null) {
                if (!(params instanceof TlsRsaPremasterSecretParameterSpec)) {
                    throw new InvalidAlgorithmParameterException(
                            "Parameters not supported");
                }

                spec = params;
                forTlsPremasterSecret = true;
                this.random = random;   
            }
            int blockType = (mode <= MODE_DECRYPT) ? RSAPadding.PAD_BLOCKTYPE_2
                                                   : RSAPadding.PAD_BLOCKTYPE_1;
            padding = RSAPadding.getInstance(blockType, n, random);
            if (encrypt) {
                int k = padding.getMaxDataSize();
                buffer = new byte[k];
            } else {
                buffer = new byte[n];
            }
        } else { 
            if ((mode == MODE_SIGN) || (mode == MODE_VERIFY)) {
                throw new InvalidKeyException
                        ("OAEP cannot be used to sign or verify signatures");
            }
            if (params != null) {
                if (!(params instanceof OAEPParameterSpec)) {
                    throw new InvalidAlgorithmParameterException
                        ("Wrong Parameters for OAEP Padding");
                }
                spec = params;
            } else {
                spec = new OAEPParameterSpec(oaepHashAlgorithm, "MGF1",
                    MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
            }
            padding = RSAPadding.getInstance(RSAPadding.PAD_OAEP_MGF1, n,
                random, (OAEPParameterSpec)spec);
            if (encrypt) {
                int k = padding.getMaxDataSize();
                buffer = new byte[k];
            } else {
                buffer = new byte[n];
            }
        }
    }

    private void update(byte[] in, int inOfs, int inLen) {
        if ((inLen == 0) || (in == null)) {
            return;
        }
        if (inLen > (buffer.length - bufOfs)) {
            bufOfs = buffer.length + 1;
            return;
        }
        System.arraycopy(in, inOfs, buffer, bufOfs, inLen);
        bufOfs += inLen;
    }

    private byte[] doFinal() throws BadPaddingException,
            IllegalBlockSizeException {
        if (bufOfs > buffer.length) {
            throw new IllegalBlockSizeException("Data must not be longer "
                + "than " + buffer.length + " bytes");
        }
        byte[] paddingCopy = null;
        byte[] result = null;
        try {
            switch (mode) {
            case MODE_SIGN:
                paddingCopy = padding.pad(buffer, 0, bufOfs);
                if (paddingCopy != null) {
                    result = RSACore.rsa(paddingCopy, privateKey, true);
                } else {
                    throw new BadPaddingException("Padding error in signing");
                }
                break;
            case MODE_VERIFY:
                byte[] verifyBuffer = RSACore.convert(buffer, 0, bufOfs);
                paddingCopy = RSACore.rsa(verifyBuffer, publicKey);
                result = padding.unpad(paddingCopy);
                if (result == null) {
                    throw new BadPaddingException
                            ("Padding error in verification");
                }
                break;
            case MODE_ENCRYPT:
                paddingCopy = padding.pad(buffer, 0, bufOfs);
                if (paddingCopy != null) {
                    result = RSACore.rsa(paddingCopy, publicKey);
                } else {
                    throw new BadPaddingException
                            ("Padding error in encryption");
                }
                break;
            case MODE_DECRYPT:
                byte[] decryptBuffer = RSACore.convert(buffer, 0, bufOfs);
                paddingCopy = RSACore.rsa(decryptBuffer, privateKey, false);
                result = padding.unpad(paddingCopy);
                if (result == null && !forTlsPremasterSecret) {
                    throw new BadPaddingException
                            ("Padding error in decryption");
                }
                break;
            default:
                throw new AssertionError("Internal error");
            }
            return result;
        } finally {
            Arrays.fill(buffer, 0, bufOfs, (byte)0);
            bufOfs = 0;
            if (paddingCopy != null
                    && paddingCopy != buffer    
                    && paddingCopy != result) { 
                Arrays.fill(paddingCopy, (byte)0);
            }
        }
    }

    protected byte[] engineUpdate(byte[] in, int inOfs, int inLen) {
        update(in, inOfs, inLen);
        return B0;
    }

    protected int engineUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) {
        update(in, inOfs, inLen);
        return 0;
    }

    protected byte[] engineDoFinal(byte[] in, int inOfs, int inLen)
            throws BadPaddingException, IllegalBlockSizeException {
        update(in, inOfs, inLen);
        return doFinal();
    }

    protected int engineDoFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException, BadPaddingException,
            IllegalBlockSizeException {
        if (outputSize > out.length - outOfs) {
            throw new ShortBufferException
                ("Need " + outputSize + " bytes for output");
        }
        update(in, inOfs, inLen);
        byte[] result = doFinal();
        int n = result.length;
        System.arraycopy(result, 0, out, outOfs, n);
        Arrays.fill(result, (byte)0);
        return n;
    }

    protected byte[] engineWrap(Key key) throws InvalidKeyException,
            IllegalBlockSizeException {
        byte[] encoded = key.getEncoded();
        if ((encoded == null) || (encoded.length == 0)) {
            throw new InvalidKeyException("Could not obtain encoded key");
        }
        try {
            if (encoded.length > buffer.length) {
                throw new InvalidKeyException("Key is too long for wrapping");
            }
            update(encoded, 0, encoded.length);
            try {
                return doFinal();
            } catch (BadPaddingException e) {
                throw new InvalidKeyException("Wrapping failed", e);
            }
        } finally {
            Arrays.fill(encoded, (byte)0);
        }
    }

    @SuppressWarnings("deprecation")
    protected Key engineUnwrap(byte[] wrappedKey, String algorithm,
            int type) throws InvalidKeyException, NoSuchAlgorithmException {
        if (wrappedKey.length > buffer.length) {
            throw new InvalidKeyException("Key is too long for unwrapping");
        }

        boolean isTlsRsaPremasterSecret =
                algorithm.equals("TlsRsaPremasterSecret");
        byte[] encoded = null;

        update(wrappedKey, 0, wrappedKey.length);
        try {
            encoded = doFinal();
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new InvalidKeyException("Unwrapping failed", e);
        }

        try {
            if (isTlsRsaPremasterSecret) {
                if (!forTlsPremasterSecret) {
                    throw new IllegalStateException(
                            "No TlsRsaPremasterSecretParameterSpec specified");
                }

                encoded = KeyUtil.checkTlsPreMasterSecretKey(
                        ((TlsRsaPremasterSecretParameterSpec) spec).getClientVersion(),
                        ((TlsRsaPremasterSecretParameterSpec) spec).getServerVersion(),
                        random, encoded, encoded == null);
            }

            return ConstructKeys.constructKey(encoded, algorithm, type);
        } finally {
            if (encoded != null) {
                Arrays.fill(encoded, (byte) 0);
            }
        }
    }

    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        RSAKey rsaKey = RSAKeyFactory.toRSAKey(key);
        return rsaKey.getModulus().bitLength();
    }
}
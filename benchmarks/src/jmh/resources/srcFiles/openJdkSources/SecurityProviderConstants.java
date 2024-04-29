/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.PatternSyntaxException;
import java.security.InvalidParameterException;
import java.security.ProviderException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.spec.DHParameterSpec;
import sun.security.action.GetPropertyAction;

/**
 * Various constants such as version number, default key length, used by
 * the JDK security/crypto providers.
 */
public final class SecurityProviderConstants {
    private SecurityProviderConstants () {}

    private static final Debug debug =
        Debug.getInstance("jca", "ProviderConfig");

    private static final ConcurrentHashMap<String, List<String>> aliasesMap;

    private static List<String> store(String stdName, KnownOIDs oid,
            String ... extraAliases) {
        List<String> value;
        if (oid == null && extraAliases.length != 0) {
            value = List.of(extraAliases);
        } else {
            value = new ArrayList<>();
            if (oid != null) {
                value.add("OID." + oid.value());
                value.add(oid.value());
                String[] knownAliases = oid.aliases();
                if (knownAliases != null) {
                    value.addAll(Arrays.asList(knownAliases));
                }
            }
            value.addAll(Arrays.asList(extraAliases));
        }
        aliasesMap.put(stdName, value);
        return value;
    }

    public static List<String> getAliases(String o) {
        List<String> res = aliasesMap.get(o);
        if (res == null) {
            KnownOIDs e = KnownOIDs.findMatch(o);
            if (e != null) {
                return store(o, e);
            }
            throw new ProviderException("Cannot find aliases for " + o);
        }
        return res;
    }

    public static final int getDefDSASubprimeSize(int primeSize) {
        if (primeSize <= 1024) {
            return 160;
        } else if (primeSize == 2048) {
            return 224;
        } else if (primeSize == 3072) {
            return 256;
        } else {
            throw new InvalidParameterException("Invalid DSA Prime Size: " +
                primeSize);
        }
    }

    public static final int getDefDHPrivateExpSize(DHParameterSpec spec) {

        int dhGroupSize = spec.getP().bitLength();

        if (spec instanceof SafeDHParameterSpec) {
            if (dhGroupSize >= 15360) {
                return 512;
            } else if (dhGroupSize >= 8192) {
                return 400;
            } else if (dhGroupSize >= 7680) {
                return 384;
            } else if (dhGroupSize >= 6144) {
                return 352;
            } else if (dhGroupSize >= 4096) {
                return 304;
            } else if (dhGroupSize >= 3072) {
                return 256;
            } else if (dhGroupSize >= 2048) {
                return 224;
            } else {
                return 160;
            }
        } else {
            return Math.max((dhGroupSize >= 2048 ? 1024 : dhGroupSize >> 1),
                    384);
        }

    }

    public static final int getDefAESKeySize() {
        int currVal = DEF_AES_KEY_SIZE.get();
        if (currVal == -1) {
            int v = 256; 
            try {
                int max = Cipher.getMaxAllowedKeyLength("AES");
                if (v > max)  {
                    v = max;
                }
            } catch (NoSuchAlgorithmException ne) {
            }
            DEF_AES_KEY_SIZE.compareAndSet(-1, v);
            currVal = v;
        }
        return currVal;
    }

    public static final int DEF_DSA_KEY_SIZE;
    public static final int DEF_RSA_KEY_SIZE;
    public static final int DEF_RSASSA_PSS_KEY_SIZE;
    public static final int DEF_DH_KEY_SIZE;
    public static final int DEF_EC_KEY_SIZE;
    public static final int DEF_ED_KEY_SIZE;
    public static final int DEF_XEC_KEY_SIZE;
    private static final AtomicInteger DEF_AES_KEY_SIZE;

    private static final String KEY_LENGTH_PROP =
        "jdk.security.defaultKeySize";

    static {
        String keyLengthStr = GetPropertyAction.privilegedGetProperty
            (KEY_LENGTH_PROP);
        int dsaKeySize = 2048;
        int rsaKeySize = 3072;
        int rsaSsaPssKeySize = rsaKeySize; 
        int dhKeySize = 3072;
        int ecKeySize = 384;
        int edKeySize = 255;
        int xecKeySize = 255;
        int aesKeySize = -1; 

        if (keyLengthStr != null) {
            try {
                String[] pairs = keyLengthStr.split(",");
                for (String p : pairs) {
                    String[] algoAndValue = p.split(":");
                    if (algoAndValue.length != 2) {
                        if (debug != null) {
                            debug.println("Ignoring invalid pair in " +
                                KEY_LENGTH_PROP + " property: " + p);
                        }
                        continue;
                    }
                    String algoName =
                            algoAndValue[0].trim().toUpperCase(Locale.ENGLISH);
                    int value;
                    try {
                        value = Integer.parseInt(algoAndValue[1].trim());
                    } catch (NumberFormatException nfe) {
                        if (debug != null) {
                            debug.println("Ignoring invalid value in " +
                                KEY_LENGTH_PROP + " property: " + p);
                        }
                        continue;
                    }
                    if (algoName.equals("DSA")) {
                        dsaKeySize = value;
                    } else if (algoName.equals("RSA")) {
                        rsaKeySize = value;
                    } else if (algoName.equals("RSASSA-PSS")) {
                        rsaSsaPssKeySize = value;
                    } else if (algoName.equals("DH")) {
                        dhKeySize = value;
                    } else if (algoName.equals("EC")) {
                        ecKeySize = value;
                    } else if (algoName.equalsIgnoreCase("EdDSA")) {
                        edKeySize = value;
                    } else if (algoName.equals("XDH")) {
                        xecKeySize = value;
                    } else if (algoName.equals("AES")) {
                        aesKeySize = value;
                    } else {
                        if (debug != null) {
                            debug.println("Ignoring unsupported algo in " +
                                KEY_LENGTH_PROP + " property: " + p);
                        }
                        continue;
                    }
                    if (debug != null) {
                        debug.println("Overriding default " + algoName +
                            " keysize with value from " +
                            KEY_LENGTH_PROP + " property: " + value);
                    }
                }
            } catch (PatternSyntaxException pse) {
                if (debug != null) {
                    debug.println("Unexpected exception while parsing " +
                        KEY_LENGTH_PROP + " property: " + pse);
                }
            }
        }
        DEF_DSA_KEY_SIZE = dsaKeySize;
        DEF_RSA_KEY_SIZE = rsaKeySize;
        DEF_RSASSA_PSS_KEY_SIZE = rsaSsaPssKeySize;
        DEF_DH_KEY_SIZE = dhKeySize;
        DEF_EC_KEY_SIZE = ecKeySize;
        DEF_ED_KEY_SIZE = edKeySize;
        DEF_XEC_KEY_SIZE = xecKeySize;
        DEF_AES_KEY_SIZE = new AtomicInteger(aesKeySize);

        aliasesMap = new ConcurrentHashMap<>();

        store("SHA1withDSA", KnownOIDs.SHA1withDSA,
                KnownOIDs.OIW_JDK_SHA1withDSA.value(),
                KnownOIDs.OIW_SHA1withDSA.value(),
                "DSA", "SHA/DSA", "SHA-1/DSA",
                "SHA1/DSA", "SHAwithDSA", "DSAWithSHA1");

        store("DSA", KnownOIDs.DSA, KnownOIDs.OIW_DSA.value());

        store("SHA1withRSA", KnownOIDs.SHA1withRSA,
                KnownOIDs.OIW_SHA1withRSA.value());

        store("SHA-1", KnownOIDs.SHA_1);

        store("PBEWithMD5AndDES", KnownOIDs.PBEWithMD5AndDES, "PBE");

        store("DiffieHellman", KnownOIDs.DiffieHellman);

        store("EC", KnownOIDs.EC, "EllipticCurve");

        store("X.509", null, "X509");
        store("NONEwithDSA", null, "RawDSA");
        store("DESede", null, "TripleDES");
        store("ARCFOUR", KnownOIDs.ARCFOUR);
        store("PKCS1", KnownOIDs.PKCS1, KnownOIDs.RSA.value());

        store("AES/KW/NoPadding", null, "AESWrap");
        store("AES/KWP/NoPadding", null, "AESWrapPad");
    }
}
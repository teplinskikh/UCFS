/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.lang.ref.*;
import java.net.Socket;
import java.security.AlgorithmConstraints;
import java.security.KeyStore;
import java.security.KeyStore.Builder;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.*;
import sun.security.provider.certpath.AlgorithmChecker;
import sun.security.validator.Validator;
import sun.security.util.KnownOIDs;

/**
 * The new X509 key manager implementation. The main differences to the
 * old SunX509 key manager are:
 *  . it is based around the KeyStore.Builder API. This allows it to use
 *    other forms of KeyStore protection or password input (e.g. a
 *    CallbackHandler) or to have keys within one KeyStore protected by
 *    different keys.
 *  . it can use multiple KeyStores at the same time.
 *  . it is explicitly designed to accommodate KeyStores that change over
 *    the lifetime of the process.
 *  . it makes an effort to choose the key that matches best, i.e. one that
 *    is not expired and has the appropriate certificate extensions.
 *
 * Note that this code is not explicitly performance optimized yet.
 *
 * @author  Andreas Sterbenz
 */
final class X509KeyManagerImpl extends X509ExtendedKeyManager
        implements X509KeyManager {

    private static Date verificationDate;

    private final List<Builder> builders;

    private final AtomicLong uidCounter;

    private final Map<String,Reference<PrivateKeyEntry>> entryCacheMap;

    X509KeyManagerImpl(Builder builder) {
        this(Collections.singletonList(builder));
    }

    X509KeyManagerImpl(List<Builder> builders) {
        this.builders = builders;
        uidCounter = new AtomicLong();
        entryCacheMap = Collections.synchronizedMap
                        (new SizedMap<>());
    }

    private static class SizedMap<K,V> extends LinkedHashMap<K,V> {
        @java.io.Serial
        private static final long serialVersionUID = -8211222668790986062L;

        @Override protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
            return size() > 10;
        }
    }


    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        PrivateKeyEntry entry = getEntry(alias);
        return entry == null ? null :
                (X509Certificate[])entry.getCertificateChain();
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        PrivateKeyEntry entry = getEntry(alias);
        return entry == null ? null : entry.getPrivateKey();
    }

    @Override
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers,
            Socket socket) {
        return chooseAlias(getKeyTypes(keyTypes), issuers, CheckType.CLIENT,
                        getAlgorithmConstraints(socket));
    }

    @Override
    public String chooseEngineClientAlias(String[] keyTypes,
            Principal[] issuers, SSLEngine engine) {
        return chooseAlias(getKeyTypes(keyTypes), issuers, CheckType.CLIENT,
                        getAlgorithmConstraints(engine));
    }

    @Override
    public String chooseServerAlias(String keyType,
            Principal[] issuers, Socket socket) {
        return chooseAlias(getKeyTypes(keyType), issuers, CheckType.SERVER,
            getAlgorithmConstraints(socket),
            X509TrustManagerImpl.getRequestedServerNames(socket),
            "HTTPS");    
    }

    @Override
    public String chooseEngineServerAlias(String keyType,
            Principal[] issuers, SSLEngine engine) {
        return chooseAlias(getKeyTypes(keyType), issuers, CheckType.SERVER,
            getAlgorithmConstraints(engine),
            X509TrustManagerImpl.getRequestedServerNames(engine),
            "HTTPS");    
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return getAliases(keyType, issuers, CheckType.CLIENT, null);
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return getAliases(keyType, issuers, CheckType.SERVER, null);
    }


    private AlgorithmConstraints getAlgorithmConstraints(Socket socket) {
        if (socket != null && socket.isConnected() &&
                socket instanceof SSLSocket sslSocket) {

            SSLSession session = sslSocket.getHandshakeSession();

            if (session != null) {
                if (ProtocolVersion.useTLS12PlusSpec(session.getProtocol())) {
                    String[] peerSupportedSignAlgs = null;

                    if (session instanceof ExtendedSSLSession extSession) {
                        peerSupportedSignAlgs =
                            extSession.getPeerSupportedSignatureAlgorithms();
                    }

                    return SSLAlgorithmConstraints.forSocket(
                        sslSocket, peerSupportedSignAlgs, true);
                }
            }

            return SSLAlgorithmConstraints.forSocket(sslSocket, true);
        }

        return SSLAlgorithmConstraints.DEFAULT;
    }

    private AlgorithmConstraints getAlgorithmConstraints(SSLEngine engine) {
        if (engine != null) {
            SSLSession session = engine.getHandshakeSession();
            if (session != null) {
                if (ProtocolVersion.useTLS12PlusSpec(session.getProtocol())) {
                    String[] peerSupportedSignAlgs = null;

                    if (session instanceof ExtendedSSLSession extSession) {
                        peerSupportedSignAlgs =
                            extSession.getPeerSupportedSignatureAlgorithms();
                    }

                    return SSLAlgorithmConstraints.forEngine(
                        engine, peerSupportedSignAlgs, true);
                }
            }
        }

        return SSLAlgorithmConstraints.forEngine(engine, true);
    }

    private String makeAlias(EntryStatus entry) {
        return uidCounter.incrementAndGet() + "." + entry.builderIndex + "."
                + entry.alias;
    }

    private PrivateKeyEntry getEntry(String alias) {
        if (alias == null) {
            return null;
        }

        Reference<PrivateKeyEntry> ref = entryCacheMap.get(alias);
        PrivateKeyEntry entry = (ref != null) ? ref.get() : null;
        if (entry != null) {
            return entry;
        }

        int firstDot = alias.indexOf('.');
        int secondDot = alias.indexOf('.', firstDot + 1);
        if ((firstDot == -1) || (secondDot == firstDot)) {
            return null;
        }
        try {
            int builderIndex = Integer.parseInt
                                (alias.substring(firstDot + 1, secondDot));
            String keyStoreAlias = alias.substring(secondDot + 1);
            Builder builder = builders.get(builderIndex);
            KeyStore ks = builder.getKeyStore();
            Entry newEntry = ks.getEntry(keyStoreAlias,
                    builder.getProtectionParameter(keyStoreAlias));
            if (!(newEntry instanceof PrivateKeyEntry)) {
                return null;
            }
            entry = (PrivateKeyEntry)newEntry;
            entryCacheMap.put(alias, new SoftReference<>(entry));
            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    private static class KeyType {

        final String keyAlgorithm;

        final String sigKeyAlgorithm;

        KeyType(String algorithm) {
            int k = algorithm.indexOf('_');
            if (k == -1) {
                keyAlgorithm = algorithm;
                sigKeyAlgorithm = null;
            } else {
                keyAlgorithm = algorithm.substring(0, k);
                sigKeyAlgorithm = algorithm.substring(k + 1);
            }
        }

        boolean matches(Certificate[] chain) {
            if (!chain[0].getPublicKey().getAlgorithm().equals(keyAlgorithm)) {
                return false;
            }
            if (sigKeyAlgorithm == null) {
                return true;
            }
            if (chain.length > 1) {
                return sigKeyAlgorithm.equals(
                        chain[1].getPublicKey().getAlgorithm());
            } else {
                X509Certificate issuer = (X509Certificate)chain[0];
                String sigAlgName =
                        issuer.getSigAlgName().toUpperCase(Locale.ENGLISH);
                String pattern =
                        "WITH" + sigKeyAlgorithm.toUpperCase(Locale.ENGLISH);
                return sigAlgName.contains(pattern);
            }
        }
    }

    private static List<KeyType> getKeyTypes(String ... keyTypes) {
        if ((keyTypes == null) ||
                (keyTypes.length == 0) || (keyTypes[0] == null)) {
            return null;
        }
        List<KeyType> list = new ArrayList<>(keyTypes.length);
        for (String keyType : keyTypes) {
            list.add(new KeyType(keyType));
        }
        return list;
    }

    /*
     * Return the best alias that fits the given parameters.
     * The algorithm we use is:
     *   . scan through all the aliases in all builders in order
     *   . as soon as we find a perfect match, return
     *     (i.e. a match with a cert that has appropriate key usage,
     *      qualified endpoint identity, and is not expired).
     *   . if we do not find a perfect match, keep looping and remember
     *     the imperfect matches
     *   . at the end, sort the imperfect matches. we prefer expired certs
     *     with appropriate key usage to certs with the wrong key usage.
     *     return the first one of them.
     */
    private String chooseAlias(List<KeyType> keyTypeList, Principal[] issuers,
            CheckType checkType, AlgorithmConstraints constraints) {

        return chooseAlias(keyTypeList, issuers,
                                    checkType, constraints, null, null);
    }

    private String chooseAlias(List<KeyType> keyTypeList, Principal[] issuers,
            CheckType checkType, AlgorithmConstraints constraints,
            List<SNIServerName> requestedServerNames, String idAlgorithm) {

        if (keyTypeList == null || keyTypeList.isEmpty()) {
            return null;
        }

        Set<Principal> issuerSet = getIssuerSet(issuers);
        List<EntryStatus> allResults = null;
        for (int i = 0, n = builders.size(); i < n; i++) {
            try {
                List<EntryStatus> results = getAliases(i, keyTypeList,
                            issuerSet, false, checkType, constraints,
                            requestedServerNames, idAlgorithm);
                if (results != null) {
                    for (EntryStatus status : results) {
                        if (status.checkResult == CheckResult.OK) {
                            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                                SSLLogger.fine("KeyMgr: choosing key: " + status);
                            }
                            return makeAlias(status);
                        }
                    }
                    if (allResults == null) {
                        allResults = new ArrayList<>();
                    }
                    allResults.addAll(results);
                }
            } catch (Exception e) {
            }
        }
        if (allResults == null) {
            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                SSLLogger.fine("KeyMgr: no matching key found");
            }
            return null;
        }
        Collections.sort(allResults);
        if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
            SSLLogger.fine(
                    "KeyMgr: no good matching key found, "
                    + "returning best match out of", allResults);
        }
        return makeAlias(allResults.get(0));
    }

    /*
     * Return all aliases that (approximately) fit the parameters.
     * These are perfect matches plus imperfect matches (expired certificates
     * and certificates with the wrong extensions).
     * The perfect matches will be first in the array.
     */
    public String[] getAliases(String keyType, Principal[] issuers,
            CheckType checkType, AlgorithmConstraints constraints) {
        if (keyType == null) {
            return null;
        }

        Set<Principal> issuerSet = getIssuerSet(issuers);
        List<KeyType> keyTypeList = getKeyTypes(keyType);
        List<EntryStatus> allResults = null;
        for (int i = 0, n = builders.size(); i < n; i++) {
            try {
                List<EntryStatus> results = getAliases(i, keyTypeList,
                                    issuerSet, true, checkType, constraints,
                                    null, null);
                if (results != null) {
                    if (allResults == null) {
                        allResults = new ArrayList<>();
                    }
                    allResults.addAll(results);
                }
            } catch (Exception e) {
            }
        }
        if (allResults == null || allResults.isEmpty()) {
            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                SSLLogger.fine("KeyMgr: no matching alias found");
            }
            return null;
        }
        Collections.sort(allResults);
        if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
            SSLLogger.fine("KeyMgr: getting aliases", allResults);
        }
        return toAliases(allResults);
    }

    private String[] toAliases(List<EntryStatus> results) {
        String[] s = new String[results.size()];
        int i = 0;
        for (EntryStatus result : results) {
            s[i++] = makeAlias(result);
        }
        return s;
    }

    private Set<Principal> getIssuerSet(Principal[] issuers) {
        if ((issuers != null) && (issuers.length != 0)) {
            return new HashSet<>(Arrays.asList(issuers));
        } else {
            return null;
        }
    }

    private static class EntryStatus implements Comparable<EntryStatus> {

        final int builderIndex;
        final int keyIndex;
        final String alias;
        final CheckResult checkResult;

        EntryStatus(int builderIndex, int keyIndex, String alias,
                Certificate[] chain, CheckResult checkResult) {
            this.builderIndex = builderIndex;
            this.keyIndex = keyIndex;
            this.alias = alias;
            this.checkResult = checkResult;
        }

        @Override
        public int compareTo(EntryStatus other) {
            int result = this.checkResult.compareTo(other.checkResult);
            return (result == 0) ? (this.keyIndex - other.keyIndex) : result;
        }

        @Override
        public String toString() {
            String s = alias + " (verified: " + checkResult + ")";
            if (builderIndex == 0) {
                return s;
            } else {
                return "Builder #" + builderIndex + ", alias: " + s;
            }
        }
    }

    private enum CheckType {

        NONE(Collections.emptySet()),

        CLIENT(new HashSet<>(List.of(
                KnownOIDs.anyExtendedKeyUsage.value(),
                KnownOIDs.clientAuth.value()
        ))),

        SERVER(new HashSet<>(List.of(
                KnownOIDs.anyExtendedKeyUsage.value(),
                KnownOIDs.serverAuth.value(),
                KnownOIDs.NETSCAPE_ExportApproved.value(),
                KnownOIDs.MICROSOFT_ExportApproved.value()
        )));

        final Set<String> validEku;

        CheckType(Set<String> validEku) {
            this.validEku = validEku;
        }

        private static boolean getBit(boolean[] keyUsage, int bit) {
            return (bit < keyUsage.length) && keyUsage[bit];
        }

        CheckResult check(X509Certificate cert, Date date,
                List<SNIServerName> serverNames, String idAlgorithm) {

            if (this == NONE) {
                return CheckResult.OK;
            }

            try {
                List<String> certEku = cert.getExtendedKeyUsage();
                if ((certEku != null) &&
                        Collections.disjoint(validEku, certEku)) {
                    return CheckResult.EXTENSION_MISMATCH;
                }

                boolean[] ku = cert.getKeyUsage();
                if (ku != null) {
                    String algorithm = cert.getPublicKey().getAlgorithm();
                    boolean supportsDigitalSignature = getBit(ku, 0);
                    switch (algorithm) {
                        case "RSA":
                            if (!supportsDigitalSignature) {
                                if (this == CLIENT || !getBit(ku, 2)) {
                                    return CheckResult.EXTENSION_MISMATCH;
                                }
                            }
                            break;
                        case "RSASSA-PSS":
                            if (!supportsDigitalSignature && (this == SERVER)) {
                                return CheckResult.EXTENSION_MISMATCH;
                            }
                            break;
                        case "DSA":
                            if (!supportsDigitalSignature) {
                                return CheckResult.EXTENSION_MISMATCH;
                            }
                            break;
                        case "DH":
                            if (!getBit(ku, 4)) {
                                return CheckResult.EXTENSION_MISMATCH;
                            }
                            break;
                        case "EC":
                            if (!supportsDigitalSignature) {
                                return CheckResult.EXTENSION_MISMATCH;
                            }
                            if (this == SERVER && !getBit(ku, 4)) {
                                return CheckResult.EXTENSION_MISMATCH;
                            }
                            break;
                    }
                }
            } catch (CertificateException e) {
                return CheckResult.EXTENSION_MISMATCH;
            }

            try {
                cert.checkValidity(date);
            } catch (CertificateException e) {
                return CheckResult.EXPIRED;
            }

            if (serverNames != null && !serverNames.isEmpty()) {
                for (SNIServerName serverName : serverNames) {
                    if (serverName.getType() ==
                                StandardConstants.SNI_HOST_NAME) {
                        if (!(serverName instanceof SNIHostName)) {
                            try {
                                serverName =
                                    new SNIHostName(serverName.getEncoded());
                            } catch (IllegalArgumentException iae) {
                                if (SSLLogger.isOn &&
                                        SSLLogger.isOn("keymanager")) {
                                    SSLLogger.fine(
                                       "Illegal server name: " + serverName);
                                }

                                return CheckResult.INSENSITIVE;
                            }
                        }
                        String hostname =
                                ((SNIHostName)serverName).getAsciiName();

                        try {
                            X509TrustManagerImpl.checkIdentity(hostname,
                                                        cert, idAlgorithm);
                        } catch (CertificateException e) {
                            if (SSLLogger.isOn &&
                                    SSLLogger.isOn("keymanager")) {
                                SSLLogger.fine(
                                    "Certificate identity does not match " +
                                    "Server Name Indication (SNI): " +
                                    hostname);
                            }
                            return CheckResult.INSENSITIVE;
                        }

                        break;
                    }
                }
            }

            return CheckResult.OK;
        }

        public String getValidator() {
            if (this == CLIENT) {
                return Validator.VAR_TLS_CLIENT;
            } else if (this == SERVER) {
                return Validator.VAR_TLS_SERVER;
            }
            return Validator.VAR_GENERIC;
        }
    }

    private enum CheckResult {
        OK,                     
        INSENSITIVE,            
        EXPIRED,                
        EXTENSION_MISMATCH,     
    }

    /*
     * Return a List of all candidate matches in the specified builder
     * that fit the parameters.
     * We exclude entries in the KeyStore if they are not:
     *  . private key entries
     *  . the certificates are not X509 certificates
     *  . the algorithm of the key in the EE cert doesn't match one of keyTypes
     *  . none of the certs is issued by a Principal in issuerSet
     * Using those entries would not be possible or they would almost
     * certainly be rejected by the peer.
     *
     * In addition to those checks, we also check the extensions in the EE
     * cert and its expiration. Even if there is a mismatch, we include
     * such certificates because they technically work and might be accepted
     * by the peer. This leads to more graceful failure and better error
     * messages if the cert expires from one day to the next.
     *
     * The return values are:
     *   . null, if there are no matching entries at all
     *   . if 'findAll' is 'false' and there is a perfect match, a List
     *     with a single element (early return)
     *   . if 'findAll' is 'false' and there is NO perfect match, a List
     *     with all the imperfect matches (expired, wrong extensions)
     *   . if 'findAll' is 'true', a List with all perfect and imperfect
     *     matches
     */
    private List<EntryStatus> getAliases(int builderIndex,
            List<KeyType> keyTypes, Set<Principal> issuerSet,
            boolean findAll, CheckType checkType,
            AlgorithmConstraints constraints,
            List<SNIServerName> requestedServerNames,
            String idAlgorithm) throws Exception {

        Builder builder = builders.get(builderIndex);
        KeyStore ks = builder.getKeyStore();
        List<EntryStatus> results = null;
        Date date = verificationDate;
        boolean preferred = false;
        for (Enumeration<String> e = ks.aliases(); e.hasMoreElements(); ) {
            String alias = e.nextElement();
            if (!ks.isKeyEntry(alias)) {
                continue;
            }

            Certificate[] chain = ks.getCertificateChain(alias);
            if ((chain == null) || (chain.length == 0)) {
                continue;
            }

            boolean incompatible = false;
            for (Certificate cert : chain) {
                if (!(cert instanceof X509Certificate)) {
                    incompatible = true;
                    break;
                }
            }
            if (incompatible) {
                continue;
            }

            int keyIndex = -1;
            int j = 0;
            for (KeyType keyType : keyTypes) {
                if (keyType.matches(chain)) {
                    keyIndex = j;
                    break;
                }
                j++;
            }
            if (keyIndex == -1) {
                if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                    SSLLogger.fine("Ignore alias " + alias
                                + ": key algorithm does not match");
                }
                continue;
            }
            if (issuerSet != null) {
                boolean found = false;
                for (Certificate cert : chain) {
                    X509Certificate xcert = (X509Certificate)cert;
                    if (issuerSet.contains(xcert.getIssuerX500Principal())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                        SSLLogger.fine(
                                "Ignore alias " + alias
                                + ": issuers do not match");
                    }
                    continue;
                }
            }

            if (constraints != null &&
                    !conformsToAlgorithmConstraints(constraints, chain,
                            checkType.getValidator())) {

                if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                    SSLLogger.fine("Ignore alias " + alias +
                            ": certificate list does not conform to " +
                            "algorithm constraints");
                }
                continue;
            }

            if (date == null) {
                date = new Date();
            }
            CheckResult checkResult =
                    checkType.check((X509Certificate)chain[0], date,
                                    requestedServerNames, idAlgorithm);
            EntryStatus status =
                    new EntryStatus(builderIndex, keyIndex,
                                        alias, chain, checkResult);
            if (!preferred && checkResult == CheckResult.OK && keyIndex == 0) {
                preferred = true;
            }
            if (preferred && !findAll) {
                return Collections.singletonList(status);
            } else {
                if (results == null) {
                    results = new ArrayList<>();
                }
                results.add(status);
            }
        }
        return results;
    }

    private static boolean conformsToAlgorithmConstraints(
            AlgorithmConstraints constraints, Certificate[] chain,
            String variant) {

        AlgorithmChecker checker = new AlgorithmChecker(constraints, variant);
        try {
            checker.init(false);
        } catch (CertPathValidatorException cpve) {
            if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                SSLLogger.fine(
                    "Cannot initialize algorithm constraints checker", cpve);
            }

            return false;
        }

        for (int i = chain.length - 1; i >= 0; i--) {
            Certificate cert = chain[i];
            try {
                checker.check(cert, Collections.emptySet());
            } catch (CertPathValidatorException cpve) {
                if (SSLLogger.isOn && SSLLogger.isOn("keymanager")) {
                    SSLLogger.fine("Certificate does not conform to " +
                            "algorithm constraints", cert, cpve);
                }

                return false;
            }
        }

        return true;
    }
}
/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.validator;

import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.security.auth.x500.X500Principal;
import sun.security.action.GetBooleanAction;
import sun.security.provider.certpath.AlgorithmChecker;
import sun.security.provider.certpath.PKIXExtendedParameters;
import sun.security.util.SecurityProperties;

/**
 * Validator implementation built on the PKIX CertPath API. This
 * implementation will be emphasized going forward.
 * <p>
 * Note that the validate() implementation tries to use a PKIX validator
 * if that appears possible and a PKIX builder otherwise. This increases
 * performance and currently also leads to better exception messages
 * in case of failures.
 * <p>
 * {@code PKIXValidator} objects are immutable once they have been created.
 * Please DO NOT add methods that can change the state of an instance once
 * it has been created.
 *
 * @author Andreas Sterbenz
 */
public final class PKIXValidator extends Validator {

    /**
     * Flag indicating whether to enable revocation check for the PKIX trust
     * manager. Typically, this will only work if the PKIX implementation
     * supports CRL distribution points as we do not manually set up CertStores.
     */
    private static final boolean checkTLSRevocation = GetBooleanAction
            .privilegedGetProperty("com.sun.net.ssl.checkRevocation");

    /**
     * System or security property that if set (or set to "true"), allows trust
     * anchor certificates to be used if they do not have the proper CA
     * extensions. Set to false if prop is not set, or set to any other value.
     */
    private static final boolean ALLOW_NON_CA_ANCHOR = allowNonCaAnchor();
    private static boolean allowNonCaAnchor() {
        String prop = SecurityProperties
                .privilegedGetOverridable("jdk.security.allowNonCaAnchor");
        return prop != null && (prop.isEmpty() || prop.equalsIgnoreCase("true"));
    }

    private final Set<X509Certificate> trustedCerts;
    private final PKIXBuilderParameters parameterTemplate;
    private int certPathLength = -1;

    private final Map<X500Principal, List<PublicKey>> trustedSubjects;
    private final CertificateFactory factory;

    PKIXValidator(String variant, Collection<X509Certificate> trustedCerts) {
        super(TYPE_PKIX, variant);
        this.trustedCerts = (trustedCerts instanceof Set) ?
                (Set<X509Certificate>)trustedCerts :
                new HashSet<>(trustedCerts);

        Set<TrustAnchor> trustAnchors = new HashSet<>();
        for (X509Certificate cert : trustedCerts) {
            trustAnchors.add(new TrustAnchor(cert, null));
        }

        try {
            parameterTemplate = new PKIXBuilderParameters(trustAnchors, null);
            factory = CertificateFactory.getInstance("X.509");
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Unexpected error: " + e.toString(), e);
        } catch (CertificateException e) {
            throw new RuntimeException("Internal error", e);
        }

        setDefaultParameters(variant);

        trustedSubjects = setTrustedSubjects();
    }

    PKIXValidator(String variant, PKIXBuilderParameters params) {
        super(TYPE_PKIX, variant);
        trustedCerts = new HashSet<>();
        for (TrustAnchor anchor : params.getTrustAnchors()) {
            X509Certificate cert = anchor.getTrustedCert();
            if (cert != null) {
                trustedCerts.add(cert);
            }
        }
        parameterTemplate = params;

        try {
            factory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Internal error", e);
        }

        trustedSubjects = setTrustedSubjects();
    }

    /**
     * Populate the trustedSubjects Map using the DN and public keys from
     * the list of trusted certificates
     *
     * @return Map containing each subject DN and one or more public keys
     *    tied to those DNs.
     */
    private Map<X500Principal, List<PublicKey>> setTrustedSubjects() {
        Map<X500Principal, List<PublicKey>> subjectMap = new HashMap<>();

        for (X509Certificate cert : trustedCerts) {
            X500Principal dn = cert.getSubjectX500Principal();
            List<PublicKey> keys;
            if (subjectMap.containsKey(dn)) {
                keys = subjectMap.get(dn);
            } else {
                keys = new ArrayList<>();
                subjectMap.put(dn, keys);
            }
            keys.add(cert.getPublicKey());
        }

        return subjectMap;
    }

    @Override
    public Collection<X509Certificate> getTrustedCertificates() {
        return trustedCerts;
    }

    /**
     * Returns the length of the last certification path that is validated by
     * CertPathValidator. This is intended primarily as a callback mechanism
     * for PKIXCertPathCheckers to determine the length of the certification
     * path that is being validated. It is necessary since engineValidate()
     * may modify the length of the path.
     *
     * @return the length of the last certification path passed to
     *   CertPathValidator.validate, or -1 if it has not been invoked yet
     */
    public int getCertPathLength() { 
        return certPathLength;
    }

    /**
     * Set J2SE global default PKIX parameters. Currently, hardcoded to disable
     * revocation checking. In the future, this should be configurable.
     */
    private void setDefaultParameters(String variant) {
        if ((variant == Validator.VAR_TLS_SERVER) ||
                (variant == Validator.VAR_TLS_CLIENT)) {
            parameterTemplate.setRevocationEnabled(checkTLSRevocation);
        } else {
            parameterTemplate.setRevocationEnabled(false);
        }
    }

    /**
     * Return the PKIX parameters used by this instance. An application may
     * modify the parameters but must make sure not to perform any concurrent
     * validations.
     */
    public PKIXBuilderParameters getParameters() { 
        return parameterTemplate;
    }

    @Override
    X509Certificate[] engineValidate(X509Certificate[] chain,
            Collection<X509Certificate> otherCerts,
            List<byte[]> responseList,
            AlgorithmConstraints constraints,
            Object parameter) throws CertificateException {
        if ((chain == null) || (chain.length == 0)) {
            throw new CertificateException
                ("null or zero-length certificate chain");
        }


        PKIXBuilderParameters pkixParameters = null;
        try {
            pkixParameters = new PKIXExtendedParameters(
                    (PKIXBuilderParameters) parameterTemplate.clone(),
                    (parameter instanceof Timestamp) ?
                            (Timestamp) parameter : null,
                    variant);
        } catch (InvalidAlgorithmParameterException e) {
        }

        if (constraints != null) {
            pkixParameters.addCertPathChecker(
                    new AlgorithmChecker(constraints, variant));
        }

        if (!responseList.isEmpty()) {
            addResponses(pkixParameters, chain, responseList);
        }

        X500Principal prevIssuer = null;
        for (int i = 0; i < chain.length; i++) {
            X509Certificate cert = chain[i];
            X500Principal dn = cert.getSubjectX500Principal();

            if (i == 0) {
                if (trustedCerts.contains(cert)) {
                    return new X509Certificate[] {chain[0]};
                }
            } else {
                if (!dn.equals(prevIssuer)) {
                    return doBuild(chain, otherCerts, pkixParameters);
                }
                if (trustedCerts.contains(cert) ||          
                        (trustedSubjects.containsKey(dn) && 
                         trustedSubjects.get(dn).contains(  
                            cert.getPublicKey()))) {
                    X509Certificate[] newChain = new X509Certificate[i];
                    System.arraycopy(chain, 0, newChain, 0, i);
                    return doValidate(newChain, pkixParameters);
                }
            }
            prevIssuer = cert.getIssuerX500Principal();
        }

        X509Certificate last = chain[chain.length - 1];
        X500Principal issuer = last.getIssuerX500Principal();
        X500Principal subject = last.getSubjectX500Principal();
        if (trustedSubjects.containsKey(issuer)) {
            return doValidate(chain, pkixParameters);
        }

        return doBuild(chain, otherCerts, pkixParameters);
    }

    private static X509Certificate[] toArray(CertPath path, TrustAnchor anchor)
            throws CertificateException {
        X509Certificate trustedCert = anchor.getTrustedCert();
        if (trustedCert == null) {
            throw new ValidatorException
                ("TrustAnchor must be specified as certificate");
        }

        verifyTrustAnchor(trustedCert);

        List<? extends java.security.cert.Certificate> list =
                                                path.getCertificates();
        X509Certificate[] chain = new X509Certificate[list.size() + 1];
        list.toArray(chain);
        chain[chain.length - 1] = trustedCert;
        return chain;
    }

    /**
     * Set the check date (for debugging).
     */
    private void setDate(PKIXBuilderParameters params) {
        @SuppressWarnings("deprecation")
        Date date = validationDate;
        if (date != null) {
            params.setDate(date);
        }
    }

    private X509Certificate[] doValidate(X509Certificate[] chain,
            PKIXBuilderParameters params) throws CertificateException {
        try {
            setDate(params);

            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            CertPath path = factory.generateCertPath(Arrays.asList(chain));
            certPathLength = chain.length;
            PKIXCertPathValidatorResult result =
                (PKIXCertPathValidatorResult)validator.validate(path, params);

            return toArray(path, result.getTrustAnchor());
        } catch (GeneralSecurityException e) {
            throw new ValidatorException
                ("PKIX path validation failed: " + e.toString(), e);
        }
    }

    /**
     * Verify that a trust anchor certificate is a CA certificate.
     */
    private static void verifyTrustAnchor(X509Certificate trustedCert)
        throws ValidatorException {

        if (ALLOW_NON_CA_ANCHOR) {
            return;
        }

        if (trustedCert.getVersion() < 3) {
            return;
        }

        if (trustedCert.getBasicConstraints() == -1) {
            throw new ValidatorException
                ("TrustAnchor with subject \"" +
                 trustedCert.getSubjectX500Principal() +
                 "\" is not a CA certificate");
        }

        boolean[] keyUsageBits = trustedCert.getKeyUsage();
        if (keyUsageBits != null && !keyUsageBits[5]) {
            throw new ValidatorException
                ("TrustAnchor with subject \"" +
                 trustedCert.getSubjectX500Principal() +
                 "\" does not have keyCertSign bit set in KeyUsage extension");
        }
    }

    private X509Certificate[] doBuild(X509Certificate[] chain,
        Collection<X509Certificate> otherCerts,
        PKIXBuilderParameters params) throws CertificateException {

        try {
            setDate(params);

            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(chain[0]);
            params.setTargetCertConstraints(selector);

            Collection<X509Certificate> certs =
                    new ArrayList<>();
            certs.addAll(Arrays.asList(chain));
            if (otherCerts != null) {
                certs.addAll(otherCerts);
            }
            CertStore store = CertStore.getInstance("Collection",
                                new CollectionCertStoreParameters(certs));
            params.addCertStore(store);

            CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
            PKIXCertPathBuilderResult result =
                (PKIXCertPathBuilderResult)builder.build(params);

            return toArray(result.getCertPath(), result.getTrustAnchor());
        } catch (GeneralSecurityException e) {
            throw new ValidatorException
                ("PKIX path building failed: " + e.toString(), e);
        }
    }

    /**
     * For OCSP Stapling, add responses that came in during the handshake
     * into a {@code PKIXRevocationChecker} so we can evaluate them.
     *
     * @param pkixParams the pkixParameters object that will be used in
     * path validation.
     * @param chain the chain of certificates to verify
     * @param responseList a {@code List} of zero or more byte arrays, each
     * one being a DER-encoded OCSP response (per RFC 6960).  Entries
     * in the List must match the order of the certificates in the
     * chain parameter.
     */
    private static void addResponses(PKIXBuilderParameters pkixParams,
            X509Certificate[] chain, List<byte[]> responseList) {
        try {
            boolean createdRevChk = false;

            PKIXRevocationChecker revChecker = null;
            List<PKIXCertPathChecker> checkerList =
                    pkixParams.getCertPathCheckers();

            for (PKIXCertPathChecker checker : checkerList) {
                if (checker instanceof PKIXRevocationChecker) {
                    revChecker = (PKIXRevocationChecker)checker;
                    break;
                }
            }

            if (revChecker == null) {
                if (pkixParams.isRevocationEnabled()) {
                    revChecker = (PKIXRevocationChecker)CertPathValidator.
                            getInstance("PKIX").getRevocationChecker();
                    createdRevChk = true;
                } else {
                    return;
                }
            }

            Map<X509Certificate, byte[]> responseMap =
                    revChecker.getOcspResponses();
            int limit = Integer.min(chain.length, responseList.size());
            for (int idx = 0; idx < limit; idx++) {
                byte[] respBytes = responseList.get(idx);
                if (respBytes != null && respBytes.length > 0 &&
                        !responseMap.containsKey(chain[idx])) {
                    responseMap.put(chain[idx], respBytes);
                }
            }
            revChecker.setOcspResponses(responseMap);

            if (createdRevChk) {
                pkixParams.addCertPathChecker(revChecker);
            } else {
                pkixParams.setCertPathCheckers(checkerList);
            }
        } catch (NoSuchAlgorithmException exc) {
        }
    }
}
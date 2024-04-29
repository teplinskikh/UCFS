/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider.certpath.ssl;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Provider;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;

/**
 * A CertStore that retrieves an SSL server's certificate chain.
 */
public final class SSLServerCertStore extends CertStoreSpi {

    private final URI uri;
    private static final GetChainTrustManager trustManager;
    private static final SSLSocketFactory socketFactory;
    private static final HostnameVerifier hostnameVerifier;

    static {
        trustManager = new GetChainTrustManager();
        hostnameVerifier = (hostname, session) -> true;

        SSLSocketFactory tempFactory;
        try {
            SSLContext context = SSLContext.getInstance("SSL");
            context.init(null, new TrustManager[] { trustManager }, null);
            tempFactory = context.getSocketFactory();
        } catch (GeneralSecurityException gse) {
            tempFactory = null;
        }

        socketFactory = tempFactory;
    }

    SSLServerCertStore(URI uri) throws InvalidAlgorithmParameterException {
        super(null);
        this.uri = uri;
    }

    public Collection<X509Certificate> engineGetCertificates
            (CertSelector selector) throws CertStoreException {

        try {
            URLConnection urlConn = uri.toURL().openConnection();
            if (urlConn instanceof HttpsURLConnection https) {
                if (socketFactory == null) {
                    throw new CertStoreException(
                        "No initialized SSLSocketFactory");
                }

                https.setSSLSocketFactory(socketFactory);
                https.setHostnameVerifier(hostnameVerifier);
                synchronized (trustManager) {
                    try {
                        https.connect();
                        return getMatchingCerts(
                            trustManager.serverChain, selector);
                    } catch (IOException ioe) {
                        if (trustManager.exchangedServerCerts) {
                            return getMatchingCerts(
                                trustManager.serverChain, selector);
                        }

                        throw ioe;
                    } finally {
                        trustManager.cleanup();
                    }
                }
            }
        } catch (IOException ioe) {
            throw new CertStoreException(ioe);
        }

        return Collections.emptySet();
    }

    private static List<X509Certificate> getMatchingCerts
        (List<X509Certificate> certs, CertSelector selector)
    {
        if (selector == null) {
            return certs;
        }
        List<X509Certificate> matchedCerts = new ArrayList<>(certs.size());
        for (X509Certificate cert : certs) {
            if (selector.match(cert)) {
                matchedCerts.add(cert);
            }
        }
        return matchedCerts;
    }

    public Collection<X509CRL> engineGetCRLs(CRLSelector selector)
        throws CertStoreException
    {
        throw new UnsupportedOperationException();
    }

    public static CertStore getInstance(URI uri)
        throws InvalidAlgorithmParameterException
    {
        return new CS(new SSLServerCertStore(uri), null, "SSLServer", null);
    }

    /*
     * An X509ExtendedTrustManager that ignores the server certificate
     * validation.
     */
    private static class GetChainTrustManager
            extends X509ExtendedTrustManager {

        private List<X509Certificate> serverChain =
                        Collections.emptyList();
        private boolean exchangedServerCerts = false;

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain,
                String authType) throws CertificateException {

            throw new UnsupportedOperationException();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType,
                Socket socket) throws CertificateException {

            throw new UnsupportedOperationException();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType,
                SSLEngine engine) throws CertificateException {

            throw new UnsupportedOperationException();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain,
                String authType) throws CertificateException {

            exchangedServerCerts = true;
            this.serverChain = (chain == null)
                           ? Collections.emptyList()
                           : Arrays.asList(chain);

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType,
                Socket socket) throws CertificateException {

            checkServerTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType,
                SSLEngine engine) throws CertificateException {

            checkServerTrusted(chain, authType);
        }

        void cleanup() {
            exchangedServerCerts = false;
            serverChain = Collections.emptyList();
        }
    }

    /**
     * This class allows the SSLServerCertStore to be accessed as a CertStore.
     */
    private static class CS extends CertStore {
        protected CS(CertStoreSpi spi, Provider p, String type,
                     CertStoreParameters params)
        {
            super(spi, p, type, params);
        }
    }
}
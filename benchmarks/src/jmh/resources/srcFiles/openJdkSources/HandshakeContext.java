/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.AlgorithmConstraints;
import java.security.CryptoPrimitive;
import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import javax.crypto.SecretKey;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLHandshakeException;
import javax.security.auth.x500.X500Principal;
import sun.security.ssl.NamedGroup.NamedGroupSpec;
import static sun.security.ssl.NamedGroup.NamedGroupSpec.*;

abstract class HandshakeContext implements ConnectionContext {

    static final boolean allowUnsafeRenegotiation =
            Utilities.getBooleanProperty(
                    "sun.security.ssl.allowUnsafeRenegotiation", false);

    static final boolean allowLegacyHelloMessages =
            Utilities.getBooleanProperty(
                    "sun.security.ssl.allowLegacyHelloMessages", true);

    LinkedHashMap<Byte, SSLConsumer>  handshakeConsumers;
    final HashMap<Byte, HandshakeProducer>  handshakeProducers;

    final SSLContextImpl                    sslContext;
    final TransportContext                  conContext;
    final SSLConfiguration                  sslConfig;

    final List<ProtocolVersion>             activeProtocols;
    final List<CipherSuite>                 activeCipherSuites;
    final AlgorithmConstraints              algorithmConstraints;
    final ProtocolVersion                   maximumActiveProtocol;

    final HandshakeOutStream                handshakeOutput;

    final HandshakeHash                     handshakeHash;

    SSLSessionImpl                          handshakeSession;
    boolean                                 handshakeFinished;

    boolean                                 kickstartMessageDelivered;

    boolean                                 isResumption;
    SSLSessionImpl                          resumingSession;
    boolean                                 statelessResumption;

    final Queue<Map.Entry<Byte, ByteBuffer>> delegatedActions;
    volatile boolean                        taskDelegated;
    volatile Exception                      delegatedThrown;

    ProtocolVersion                         negotiatedProtocol;
    CipherSuite                             negotiatedCipherSuite;
    final List<SSLPossession>               handshakePossessions;
    final List<SSLCredentials>              handshakeCredentials;
    SSLKeyDerivation                        handshakeKeyDerivation;
    SSLKeyExchange                          handshakeKeyExchange;
    SecretKey                               baseReadSecret;
    SecretKey                               baseWriteSecret;

    int                                     clientHelloVersion;
    String                                  applicationProtocol;

    RandomCookie                            clientHelloRandom;
    RandomCookie                            serverHelloRandom;
    byte[]                                  certRequestContext;


    final Map<SSLExtension, SSLExtension.SSLExtensionSpec>
                                            handshakeExtensions;

    int                                     maxFragmentLength;

    List<SignatureScheme>                   localSupportedSignAlgs;
    List<SignatureScheme>                   peerRequestedSignatureSchemes;
    List<SignatureScheme>                   peerRequestedCertSignSchemes;

    X500Principal[]                         peerSupportedAuthorities = null;

    List<NamedGroup>                        clientRequestedNamedGroups;

    NamedGroup                              serverSelectedNamedGroup;

    List<SNIServerName>                     requestedServerNames;
    SNIServerName                           negotiatedServerName;

    boolean                                 staplingActive = false;

    protected HandshakeContext(SSLContextImpl sslContext,
            TransportContext conContext) throws IOException {
        this.sslContext = sslContext;
        this.conContext = conContext;
        this.sslConfig = (SSLConfiguration)conContext.sslConfig.clone();

        this.algorithmConstraints = SSLAlgorithmConstraints.wrap(
                sslConfig.userSpecifiedAlgorithmConstraints);
        this.activeProtocols =
                getActiveProtocols(sslConfig, algorithmConstraints);
        if (activeProtocols.isEmpty()) {
            throw new SSLHandshakeException(
                "No appropriate protocol (protocol is disabled or " +
                "cipher suites are inappropriate)");
        }

        ProtocolVersion maximumVersion = ProtocolVersion.NONE;
        for (ProtocolVersion pv : this.activeProtocols) {
            if (maximumVersion == ProtocolVersion.NONE ||
                    pv.compare(maximumVersion) > 0) {
                maximumVersion = pv;
            }
        }
        this.maximumActiveProtocol = maximumVersion;
        this.activeCipherSuites = getActiveCipherSuites(sslConfig,
                this.activeProtocols, algorithmConstraints);
        if (activeCipherSuites.isEmpty()) {
            throw new SSLHandshakeException("No appropriate cipher suite");
        }

        this.handshakeConsumers = new LinkedHashMap<>();
        this.handshakeProducers = new HashMap<>();
        this.handshakeHash = conContext.inputRecord.handshakeHash;
        this.handshakeOutput = new HandshakeOutStream(conContext.outputRecord);

        this.handshakeFinished = false;
        this.kickstartMessageDelivered = false;

        this.delegatedActions = new LinkedList<>();
        this.handshakeExtensions = new HashMap<>();
        this.handshakePossessions = new LinkedList<>();
        this.handshakeCredentials = new LinkedList<>();
        this.requestedServerNames = null;
        this.negotiatedServerName = null;
        this.negotiatedCipherSuite = conContext.cipherSuite;
        initialize();
    }

    /**
     * Constructor for PostHandshakeContext
     */
    protected HandshakeContext(TransportContext conContext) {
        this.sslContext = conContext.sslContext;
        this.conContext = conContext;
        this.sslConfig = conContext.sslConfig;

        this.negotiatedProtocol = conContext.protocolVersion;
        this.negotiatedCipherSuite = conContext.cipherSuite;
        this.handshakeOutput = new HandshakeOutStream(conContext.outputRecord);
        this.delegatedActions = new LinkedList<>();

        this.handshakeConsumers = new LinkedHashMap<>();
        this.handshakeProducers = null;
        this.handshakeHash = null;
        this.activeProtocols = null;
        this.activeCipherSuites = null;
        this.algorithmConstraints = null;
        this.maximumActiveProtocol = null;
        this.handshakeExtensions = Collections.emptyMap();  
        this.handshakePossessions = null;
        this.handshakeCredentials = null;
    }

    private void initialize() {
        ProtocolVersion inputHelloVersion;
        ProtocolVersion outputHelloVersion;
        if (conContext.isNegotiated) {
            inputHelloVersion = conContext.protocolVersion;
            outputHelloVersion = conContext.protocolVersion;
        } else {
            if (activeProtocols.contains(ProtocolVersion.SSL20Hello)) {
                inputHelloVersion = ProtocolVersion.SSL20Hello;

                if (maximumActiveProtocol.useTLS13PlusSpec()) {
                    outputHelloVersion = maximumActiveProtocol;
                } else {
                    outputHelloVersion = ProtocolVersion.SSL20Hello;
                }
            } else {
                inputHelloVersion = maximumActiveProtocol;
                outputHelloVersion = maximumActiveProtocol;
            }
        }

        conContext.inputRecord.setHelloVersion(inputHelloVersion);
        conContext.outputRecord.setHelloVersion(outputHelloVersion);

        if (!conContext.isNegotiated) {
            conContext.protocolVersion = maximumActiveProtocol;
        }
        conContext.outputRecord.setVersion(conContext.protocolVersion);
    }

    private static List<ProtocolVersion> getActiveProtocols(
            SSLConfiguration sslConfig,
            AlgorithmConstraints algorithmConstraints) {
        boolean enabledSSL20Hello = false;
        ArrayList<ProtocolVersion> protocols = new ArrayList<>(4);
        for (ProtocolVersion protocol : sslConfig.enabledProtocols) {
            if (!enabledSSL20Hello && protocol == ProtocolVersion.SSL20Hello) {
                enabledSSL20Hello = true;
                continue;
            }

            if (!algorithmConstraints.permits(
                    EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                    protocol.name, null)) {
                continue;
            }

            boolean found = false;
            Map<NamedGroupSpec, Boolean> cachedStatus =
                    new EnumMap<>(NamedGroupSpec.class);
            for (CipherSuite suite : sslConfig.enabledCipherSuites) {
                if (suite.isAvailable() && suite.supports(protocol)) {
                    if (isActivatable(sslConfig, suite,
                            algorithmConstraints, cachedStatus)) {
                        protocols.add(protocol);
                        found = true;
                        break;
                    }
                } else if (SSLLogger.isOn && SSLLogger.isOn("verbose")) {
                    SSLLogger.fine(
                        "Ignore unsupported cipher suite: " + suite +
                             " for " + protocol.name);
                }
            }

            if (!found && (SSLLogger.isOn) && SSLLogger.isOn("handshake")) {
                SSLLogger.fine(
                    "No available cipher suite for " + protocol.name);
            }
        }

        if (!protocols.isEmpty()) {
            if (enabledSSL20Hello) {
                protocols.add(ProtocolVersion.SSL20Hello);
            }
            Collections.sort(protocols);
        }

        return Collections.unmodifiableList(protocols);
    }

    private static List<CipherSuite> getActiveCipherSuites(
            SSLConfiguration sslConfig,
            List<ProtocolVersion> enabledProtocols,
            AlgorithmConstraints algorithmConstraints) {

        List<CipherSuite> suites = new LinkedList<>();
        if (enabledProtocols != null && !enabledProtocols.isEmpty()) {
            Map<NamedGroupSpec, Boolean> cachedStatus =
                    new EnumMap<>(NamedGroupSpec.class);
            for (CipherSuite suite : sslConfig.enabledCipherSuites) {
                if (!suite.isAvailable()) {
                    continue;
                }

                boolean isSupported = false;
                for (ProtocolVersion protocol : enabledProtocols) {
                    if (!suite.supports(protocol)) {
                        continue;
                    }
                    if (isActivatable(sslConfig, suite,
                            algorithmConstraints, cachedStatus)) {
                        suites.add(suite);
                        isSupported = true;
                        break;
                    }
                }

                if (!isSupported &&
                        SSLLogger.isOn && SSLLogger.isOn("verbose")) {
                    SSLLogger.finest(
                            "Ignore unsupported cipher suite: " + suite);
                }
            }
        }

        return Collections.unmodifiableList(suites);
    }

    /**
     * Parse the handshake record and return the contentType
     */
    static byte getHandshakeType(TransportContext conContext,
            Plaintext plaintext) throws IOException {

        if (plaintext.contentType != ContentType.HANDSHAKE.id) {
            throw conContext.fatal(Alert.INTERNAL_ERROR,
                "Unexpected operation for record: " + plaintext.contentType);
        }

        if (plaintext.fragment == null || plaintext.fragment.remaining() < 4) {
            throw conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    "Invalid handshake message: insufficient data");
        }

        byte handshakeType = (byte)Record.getInt8(plaintext.fragment);
        int handshakeLen = Record.getInt24(plaintext.fragment);
        if (handshakeLen != plaintext.fragment.remaining()) {
            throw conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    "Invalid handshake message: insufficient handshake body");
        }

        return handshakeType;
    }

    void dispatch(byte handshakeType, Plaintext plaintext) throws IOException {
        if (conContext.transport.useDelegatedTask()) {
            boolean hasDelegated = !delegatedActions.isEmpty();
            if (hasDelegated ||
                   (handshakeType != SSLHandshake.FINISHED.id &&
                    handshakeType != SSLHandshake.KEY_UPDATE.id &&
                    handshakeType != SSLHandshake.NEW_SESSION_TICKET.id)) {
                if (!hasDelegated) {
                    taskDelegated = false;
                    delegatedThrown = null;
                }

                ByteBuffer fragment = ByteBuffer.wrap(
                        new byte[plaintext.fragment.remaining()]);
                fragment.put(plaintext.fragment);
                fragment = fragment.rewind();

                delegatedActions.add(new SimpleImmutableEntry<>(
                        handshakeType,
                        fragment
                    ));

                if (hasDelegated &&
                        !conContext.sslConfig.isClientMode &&
                        handshakeType == SSLHandshake.FINISHED.id) {
                    conContext.hasDelegatedFinished = true;
                }
            } else {
                dispatch(handshakeType, plaintext.fragment);
            }
        } else {
            dispatch(handshakeType, plaintext.fragment);
        }
    }

    void dispatch(byte handshakeType,
            ByteBuffer fragment) throws IOException {
        SSLConsumer consumer;
        if (handshakeType == SSLHandshake.HELLO_REQUEST.id) {

            consumer = conContext.sslConfig.isClientMode ?
                    SSLHandshake.HELLO_REQUEST : null;
        } else {
            consumer = handshakeConsumers.get(handshakeType);
        }

        if (consumer == null) {
            throw conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    "Unexpected handshake message: " +
                    SSLHandshake.nameOf(handshakeType));
        }

        try {
            consumer.consume(this, fragment);
        } catch (UnsupportedOperationException unsoe) {
            throw conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    "Unsupported handshake message: " +
                    SSLHandshake.nameOf(handshakeType), unsoe);
        } catch (BufferUnderflowException | BufferOverflowException be) {
            throw conContext.fatal(Alert.DECODE_ERROR,
                    "Illegal handshake message: " +
                    SSLHandshake.nameOf(handshakeType), be);
        }

        handshakeHash.consume();
    }

    abstract void kickstart() throws IOException;

    /**
     * Check if the given cipher suite is enabled and available within
     * the current active cipher suites.
     *
     * Does not check if the required server certificates are available.
     */
    boolean isNegotiable(CipherSuite cs) {
        return isNegotiable(activeCipherSuites, cs);
    }

    /**
     * Check if the given cipher suite is enabled and available within
     * the proposed cipher suite list.
     *
     * Does not check if the required server certificates are available.
     */
    static final boolean isNegotiable(
            List<CipherSuite> proposed, CipherSuite cs) {
        return proposed.contains(cs) && cs.isNegotiable();
    }

    /**
     * Check if the given cipher suite is enabled and available within
     * the proposed cipher suite list and specific protocol version.
     *
     * Does not check if the required server certificates are available.
     */
    static final boolean isNegotiable(List<CipherSuite> proposed,
            ProtocolVersion protocolVersion, CipherSuite cs) {
        return proposed.contains(cs) &&
                cs.isNegotiable() && cs.supports(protocolVersion);
    }

    /**
     * Check if the given protocol version is enabled and available.
     */
    boolean isNegotiable(ProtocolVersion protocolVersion) {
        return activeProtocols.contains(protocolVersion);
    }

    private static boolean isActivatable(
            SSLConfiguration sslConfig,
            CipherSuite suite,
            AlgorithmConstraints algorithmConstraints,
            Map<NamedGroupSpec, Boolean> cachedStatus) {

        if (algorithmConstraints.permits(
                EnumSet.of(CryptoPrimitive.KEY_AGREEMENT), suite.name, null)) {
            if (suite.keyExchange == null) {
                return true;
            }

            boolean groupAvailable, retval = false;
            NamedGroupSpec[] groupTypes = suite.keyExchange.groupTypes;
            for (NamedGroupSpec groupType : groupTypes) {
                if (groupType != NAMED_GROUP_NONE) {
                    Boolean checkedStatus = cachedStatus.get(groupType);
                    if (checkedStatus == null) {
                        groupAvailable = NamedGroup.isActivatable(
                                sslConfig, algorithmConstraints, groupType);
                        cachedStatus.put(groupType, groupAvailable);

                        if (!groupAvailable &&
                                SSLLogger.isOn && SSLLogger.isOn("verbose")) {
                            SSLLogger.fine(
                                    "No activated named group in " + groupType);
                        }
                    } else {
                        groupAvailable = checkedStatus;
                    }

                    retval |= groupAvailable;
                } else {
                    retval = true;
                }
            }

            if (!retval && SSLLogger.isOn && SSLLogger.isOn("verbose")) {
                SSLLogger.fine("No active named group(s), ignore " + suite);
            }

            return retval;

        } else if (SSLLogger.isOn && SSLLogger.isOn("verbose")) {
            SSLLogger.fine("Ignore disabled cipher suite: " + suite);
        }

        return false;
    }

    List<SNIServerName> getRequestedServerNames() {
        return Objects.requireNonNullElse(requestedServerNames,
                Collections.emptyList());
    }
}

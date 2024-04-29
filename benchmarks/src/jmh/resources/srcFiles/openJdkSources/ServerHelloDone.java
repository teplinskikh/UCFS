/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import sun.security.ssl.SSLHandshake.HandshakeMessage;

/**
 * Pack of the ServerHelloDone handshake message.
 */
final class ServerHelloDone {
    static final SSLConsumer handshakeConsumer =
        new ServerHelloDoneConsumer();
    static final HandshakeProducer handshakeProducer =
        new ServerHelloDoneProducer();

    /**
     * The ServerHelloDone handshake message.
     */
    static final class ServerHelloDoneMessage extends HandshakeMessage {
        ServerHelloDoneMessage(HandshakeContext handshakeContext) {
            super(handshakeContext);
        }

        ServerHelloDoneMessage(HandshakeContext handshakeContext,
                ByteBuffer m) throws IOException {
            super(handshakeContext);
            if (m.hasRemaining()) {
                throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Error parsing ServerHelloDone message: not empty");
            }
        }

        @Override
        public SSLHandshake handshakeType() {
            return SSLHandshake.SERVER_HELLO_DONE;
        }

        @Override
        public int messageLength() {
            return 0;
        }

        @Override
        public void send(HandshakeOutStream s) throws IOException {
        }

        @Override
        public String toString() {
            return "<empty>";
        }
    }

    /**
     * The "ServerHelloDone" handshake message producer.
     */
    private static final
            class ServerHelloDoneProducer implements HandshakeProducer {
        private ServerHelloDoneProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            ServerHelloDoneMessage shdm = new ServerHelloDoneMessage(shc);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Produced ServerHelloDone handshake message", shdm);
            }

            shdm.write(shc.handshakeOutput);
            shc.handshakeOutput.flush();

            shc.handshakeConsumers.put(SSLHandshake.CLIENT_KEY_EXCHANGE.id,
                    SSLHandshake.CLIENT_KEY_EXCHANGE);
            shc.conContext.consumers.put(ContentType.CHANGE_CIPHER_SPEC.id,
                    ChangeCipherSpec.t10Consumer);
            shc.handshakeConsumers.put(SSLHandshake.FINISHED.id,
                    SSLHandshake.FINISHED);

            return null;
        }
    }

    /**
     * The "ServerHelloDone" handshake message consumer.
     */
    private static final
            class ServerHelloDoneConsumer implements SSLConsumer {
        private ServerHelloDoneConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            SSLConsumer certStatCons = chc.handshakeConsumers.remove(
                    SSLHandshake.CERTIFICATE_STATUS.id);
            if (certStatCons != null) {
                CertificateStatus.handshakeAbsence.absent(context, null);
            }

            chc.handshakeConsumers.clear();

            ServerHelloDoneMessage shdm =
                    new ServerHelloDoneMessage(chc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Consuming ServerHelloDone handshake message", shdm);
            }


            chc.handshakeProducers.put(SSLHandshake.CLIENT_KEY_EXCHANGE.id,
                    SSLHandshake.CLIENT_KEY_EXCHANGE);
            chc.handshakeProducers.put(SSLHandshake.FINISHED.id,
                    SSLHandshake.FINISHED);
            SSLHandshake[] probableHandshakeMessages = new SSLHandshake[] {
                SSLHandshake.CERTIFICATE,
                SSLHandshake.CLIENT_KEY_EXCHANGE,
                SSLHandshake.CERTIFICATE_VERIFY,
                SSLHandshake.FINISHED
            };

            for (SSLHandshake hs : probableHandshakeMessages) {
                HandshakeProducer handshakeProducer =
                        chc.handshakeProducers.remove(hs.id);
                if (handshakeProducer != null) {
                    handshakeProducer.produce(context, null);
                }
            }
        }
    }
}
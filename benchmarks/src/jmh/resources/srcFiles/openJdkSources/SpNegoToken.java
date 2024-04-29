/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.jgss.spnego;

import org.ietf.jgss.GSSException;
import sun.security.jgss.GSSToken;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.ObjectIdentifier;

import java.io.IOException;

/**
 * Abstract class for SPNEGO tokens.
 * Implementation is based on RFC 2478
 *
 * NegotiationToken ::= CHOICE {
 *      negTokenInit  [0]        NegTokenInit,
 *      negTokenTarg  [1]        NegTokenTarg }
 *
 *
 * @author Seema Malkani
 * @since 1.6
 */

abstract class SpNegoToken extends GSSToken {

    static final int NEG_TOKEN_INIT_ID = 0x00;
    static final int NEG_TOKEN_TARG_ID = 0x01;

    enum NegoResult {
        ACCEPT_COMPLETE,
        ACCEPT_INCOMPLETE,
        REJECT,
    }

    private final int tokenType;

    /**
     * The object identifier corresponding to the SPNEGO GSS-API
     * mechanism.
     */
    public static ObjectIdentifier OID;

    static {
        try {
            OID = ObjectIdentifier.of(SpNegoMechFactory.
                                       GSS_SPNEGO_MECH_OID.toString());
        } catch (IOException ioe) {
        }
    }

    /**
     * Creates SPNEGO token of the specified type.
     */
    protected SpNegoToken(int tokenType) {
        this.tokenType = tokenType;
    }

    /**
     * Returns the individual encoded SPNEGO token
     *
     * @return the encoded token
     * @exception GSSException
     */
    abstract byte[] encode() throws GSSException;

    /**
     * Returns the encoded SPNEGO token
     * Note: inserts the required CHOICE tags
     *
     * @return the encoded token
     * @exception GSSException
     */
    byte[] getEncoded() throws IOException, GSSException {

        DerOutputStream token = new DerOutputStream();
        token.write(encode());

        switch (tokenType) {
            case NEG_TOKEN_INIT_ID:
                DerOutputStream initToken = new DerOutputStream();
                initToken.write(DerValue.createTag(DerValue.TAG_CONTEXT,
                                true, (byte) NEG_TOKEN_INIT_ID), token);
                return initToken.toByteArray();

            case NEG_TOKEN_TARG_ID:
                DerOutputStream targToken = new DerOutputStream();
                targToken.write(DerValue.createTag(DerValue.TAG_CONTEXT,
                                true, (byte) NEG_TOKEN_TARG_ID), token);
                return targToken.toByteArray();
            default:
                return token.toByteArray();
        }
    }

    /**
     * Returns the SPNEGO token type
     *
     * @return the token type
     */
    final int getType() {
        return tokenType;
    }

    /**
     * Returns a string representing the token type.
     *
     * @param tokenType the token type for which a string name is desired
     * @return the String name of this token type
     */
    static String getTokenName(int type) {
        switch (type) {
            case NEG_TOKEN_INIT_ID:
                return "SPNEGO NegTokenInit";
            case NEG_TOKEN_TARG_ID:
                return "SPNEGO NegTokenTarg";
            default:
                return "SPNEGO Mechanism Token";
        }
    }

    /**
     * Returns a string representing the negotiation result.
     *
     * @param result the negotiated result
     * @return the String message of this negotiated result
     */
    static String getNegoResultString(int result) {
        switch (result) {
        case 0:
                return "Accept Complete";
        case 1:
                return "Accept InComplete";
        case 2:
                return "Reject";
        default:
                return ("Unknown Negotiated Result: " + result);
        }
    }

    /**
     * Checks if the context tag in a sequence is in correct order. The "last"
     * value must be smaller than "current".
     * @param last the last tag seen
     * @param current the current tag
     * @return the current tag, used as the next value for last
     * @throws GSSException if there's a wrong order
     */
    static int checkNextField(int last, int current) throws GSSException {
        if (last < current) {
            return current;
        } else {
            throw new GSSException(GSSException.DEFECTIVE_TOKEN, -1,
                "Invalid SpNegoToken token : wrong order");
        }
    }
}
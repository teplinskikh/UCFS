/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import java.security.*;

import sun.security.pkcs11.wrapper.*;

/**
 * A session object. Sessions are obtained via the SessionManager,
 * see there for details. Most code will only ever need one method in
 * this class, the id() method to obtain the session id.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class Session implements Comparable<Session> {

    private static final long MAX_IDLE_TIME = 3 * 60 * 1000;

    final Token token;

    private final long id;

    private final AtomicInteger createdObjects;

    private long lastAccess;

    private final SessionRef sessionRef;

    Session(Token token, long id) {
        this.token = token;
        this.id = id;
        createdObjects = new AtomicInteger();
        id();
        sessionRef = new SessionRef(this, id, token);
    }

    public int compareTo(Session other) {
        if (this.lastAccess == other.lastAccess) {
            return 0;
        } else {
            return (this.lastAccess < other.lastAccess) ? -1 : 1;
        }
    }

    boolean isLive(long currentTime) {
        return currentTime - lastAccess < MAX_IDLE_TIME;
    }

    long id() {
        if (!token.isPresent(this.id)) {
            throw new ProviderException("Token has been removed");
        }
        lastAccess = System.currentTimeMillis();
        return id;
    }

    void addObject() {
        int n = createdObjects.incrementAndGet();
    }

    void removeObject() {
        int n = createdObjects.decrementAndGet();
        if (n == 0) {
            token.sessionManager.demoteObjSession(this);
        } else if (n < 0) {
            throw new ProviderException("Internal error: objects created " + n);
        }
    }

    boolean hasObjects() {
        return createdObjects.get() != 0;
    }

    void close() {
        close(true);
    }

    void kill() {
        close(false);
    }

    private void close(boolean checkObjCtr) {
        if (hasObjects() && checkObjCtr) {
            throw new ProviderException(
                    "Internal error: close session with active objects");
        }
        sessionRef.dispose();
    }

    static boolean drainRefQueue() {
        boolean found = false;
        SessionRef next;
        while ((next = (SessionRef) SessionRef.REF_QUEUE.poll())!= null) {
            found = true;
            next.dispose();
        }
        return found;
    }
}
/*
 * NOTE: Use PhantomReference here and not WeakReference
 * otherwise the sessions maybe closed before other objects
 * which are still being finalized.
 */
final class SessionRef extends PhantomReference<Session>
        implements Comparable<SessionRef> {

    static final ReferenceQueue<Session> REF_QUEUE = new ReferenceQueue<>();

    private static final Set<SessionRef> REF_LIST =
        Collections.synchronizedSortedSet(new TreeSet<>());

    private final long id;
    private final Token token;

    SessionRef(Session session, long id, Token token) {
        super(session, REF_QUEUE);
        this.id = id;
        this.token = token;
        REF_LIST.add(this);
    }

    void dispose() {
        REF_LIST.remove(this);
        try {
            if (token.isPresent(id)) {
                token.p11.C_CloseSession(id);
            }
        } catch (PKCS11Exception | ProviderException e1) {
        } finally {
            this.clear();
        }
    }

    public int compareTo(SessionRef other) {
        if (this.id == other.id) {
            return 0;
        } else {
            return (this.id < other.id) ? -1 : 1;
        }
    }
}
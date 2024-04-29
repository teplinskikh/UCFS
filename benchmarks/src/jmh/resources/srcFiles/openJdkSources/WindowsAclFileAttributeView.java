/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.fs;

import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.*;
import java.util.*;
import java.io.IOException;

import static sun.nio.fs.WindowsNativeDispatcher.*;
import static sun.nio.fs.WindowsConstants.*;

/**
 * Windows implementation of AclFileAttributeView.
 */

class WindowsAclFileAttributeView
    extends AbstractAclFileAttributeView
{
    /**
     * typedef struct _SECURITY_DESCRIPTOR {
     *     BYTE  Revision;
     *     BYTE  Sbz1;
     *     SECURITY_DESCRIPTOR_CONTROL Control;
     *     PSID Owner;
     *     PSID Group;
     *     PACL Sacl;
     *     PACL Dacl;
     * } SECURITY_DESCRIPTOR;
     */
    private static final short SIZEOF_SECURITY_DESCRIPTOR   = 20;

    private final WindowsPath file;
    private final boolean followLinks;

    WindowsAclFileAttributeView(WindowsPath file, boolean followLinks) {
        this.file = file;
        this.followLinks = followLinks;
    }

    private void checkAccess(WindowsPath file,
                             boolean checkRead,
                             boolean checkWrite)
    {
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            if (checkRead)
                sm.checkRead(file.getPathForPermissionCheck());
            if (checkWrite)
                sm.checkWrite(file.getPathForPermissionCheck());
            sm.checkPermission(new RuntimePermission("accessUserInformation"));
        }
    }

    static NativeBuffer getFileSecurity(String path, int request)
        throws IOException
    {
        int size = 0;
        try {
            size = GetFileSecurity(path, request, 0L, 0);
        } catch (WindowsException x) {
            x.rethrowAsIOException(path);
        }
        assert size > 0;

        NativeBuffer buffer = NativeBuffers.getNativeBuffer(size);
        try {
            for (;;) {
                int newSize = GetFileSecurity(path, request, buffer.address(), size);
                if (newSize <= size)
                    return buffer;

                buffer.release();
                buffer = NativeBuffers.getNativeBuffer(newSize);
                size = newSize;
            }
        } catch (WindowsException x) {
            buffer.release();
            x.rethrowAsIOException(path);
            return null;
        }
    }

    @Override
    public UserPrincipal getOwner()
        throws IOException
    {
        checkAccess(file, true, false);

        String path = WindowsLinkSupport.getFinalPath(file, followLinks);
        try (NativeBuffer buffer = getFileSecurity(path, OWNER_SECURITY_INFORMATION)) {
            long sidAddress = GetSecurityDescriptorOwner(buffer.address());
            if (sidAddress == 0L)
                throw new IOException("no owner");
            return WindowsUserPrincipals.fromSid(sidAddress);
        } catch (WindowsException x) {
            x.rethrowAsIOException(file);
            return null;
        }
    }

    @Override
    public List<AclEntry> getAcl()
        throws IOException
    {
        checkAccess(file, true, false);

        String path = WindowsLinkSupport.getFinalPath(file, followLinks);

        try (NativeBuffer buffer = getFileSecurity(path, DACL_SECURITY_INFORMATION)) {
            return WindowsSecurityDescriptor.getAcl(buffer.address());
        }
    }

    @Override
    public void setOwner(UserPrincipal obj)
        throws IOException
    {
        if (obj == null)
            throw new NullPointerException("'owner' is null");
        if (!(obj instanceof WindowsUserPrincipals.User))
            throw new ProviderMismatchException();
        WindowsUserPrincipals.User owner = (WindowsUserPrincipals.User)obj;

        checkAccess(file, false, true);

        String path = WindowsLinkSupport.getFinalPath(file, followLinks);

        long pOwner;
        try {
            pOwner = ConvertStringSidToSid(owner.sidString());
        } catch (WindowsException x) {
            throw new IOException("Failed to get SID for " + owner.getName()
                + ": " + x.errorString());
        }

        try (NativeBuffer buffer = NativeBuffers.getNativeBuffer(SIZEOF_SECURITY_DESCRIPTOR)) {
            InitializeSecurityDescriptor(buffer.address());
            SetSecurityDescriptorOwner(buffer.address(), pOwner);
            WindowsSecurity.Privilege priv =
                WindowsSecurity.enablePrivilege("SeRestorePrivilege");
            try {
                SetFileSecurity(path,
                                OWNER_SECURITY_INFORMATION,
                                buffer.address());
            } finally {
                priv.drop();
            }
        } catch (WindowsException x) {
            x.rethrowAsIOException(file);
        } finally {
            LocalFree(pOwner);
        }
    }

    @Override
    public void setAcl(List<AclEntry> acl) throws IOException {
        checkAccess(file, false, true);

        String path = WindowsLinkSupport.getFinalPath(file, followLinks);
        WindowsSecurityDescriptor sd = WindowsSecurityDescriptor.create(acl);
        try {
            SetFileSecurity(path, DACL_SECURITY_INFORMATION, sd.address());
        } catch (WindowsException x) {
             x.rethrowAsIOException(file);
        } finally {
            sd.release();
        }
    }
}
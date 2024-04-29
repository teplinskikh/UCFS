/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.invoke;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Hidden;

final class VarHandleGuards {

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final Object guard_L_L(VarHandle handle, Object arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            Object r = MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
            return ad.returnType.cast(r);
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LL_V(VarHandle handle, Object arg0, Object arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LLL_Z(VarHandle handle, Object arg0, Object arg1, Object arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final Object guard_LLL_L(VarHandle handle, Object arg0, Object arg1, Object arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            Object r = MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
            return ad.returnType.cast(r);
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final Object guard_LL_L(VarHandle handle, Object arg0, Object arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            Object r = MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
            return ad.returnType.cast(r);
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final int guard_L_I(VarHandle handle, Object arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (int) MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (int) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LI_V(VarHandle handle, Object arg0, int arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LII_Z(VarHandle handle, Object arg0, int arg1, int arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final int guard_LII_I(VarHandle handle, Object arg0, int arg1, int arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (int) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (int) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final int guard_LI_I(VarHandle handle, Object arg0, int arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (int) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (int) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final long guard_L_J(VarHandle handle, Object arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (long) MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (long) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LJ_V(VarHandle handle, Object arg0, long arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LJJ_Z(VarHandle handle, Object arg0, long arg1, long arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final long guard_LJJ_J(VarHandle handle, Object arg0, long arg1, long arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (long) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (long) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final long guard_LJ_J(VarHandle handle, Object arg0, long arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (long) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (long) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final float guard_L_F(VarHandle handle, Object arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (float) MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (float) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LF_V(VarHandle handle, Object arg0, float arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LFF_Z(VarHandle handle, Object arg0, float arg1, float arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final float guard_LFF_F(VarHandle handle, Object arg0, float arg1, float arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (float) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (float) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final float guard_LF_F(VarHandle handle, Object arg0, float arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (float) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (float) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final double guard_L_D(VarHandle handle, Object arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (double) MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (double) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LD_V(VarHandle handle, Object arg0, double arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LDD_Z(VarHandle handle, Object arg0, double arg1, double arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final double guard_LDD_D(VarHandle handle, Object arg0, double arg1, double arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (double) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (double) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final double guard_LD_D(VarHandle handle, Object arg0, double arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (double) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (double) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final Object guard__L(VarHandle handle, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            Object r = MethodHandle.linkToStatic(handle, handle.vform.getMemberName(ad.mode));
            return ad.returnType.cast(r);
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect());
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_L_V(VarHandle handle, Object arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LL_Z(VarHandle handle, Object arg0, Object arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final int guard__I(VarHandle handle, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (int) MethodHandle.linkToStatic(handle, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (int) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect());
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_I_V(VarHandle handle, int arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_II_Z(VarHandle handle, int arg0, int arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final int guard_II_I(VarHandle handle, int arg0, int arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (int) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (int) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final int guard_I_I(VarHandle handle, int arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (int) MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (int) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final long guard__J(VarHandle handle, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (long) MethodHandle.linkToStatic(handle, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (long) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect());
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_J_V(VarHandle handle, long arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_JJ_Z(VarHandle handle, long arg0, long arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final long guard_JJ_J(VarHandle handle, long arg0, long arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (long) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (long) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final long guard_J_J(VarHandle handle, long arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (long) MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (long) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final float guard__F(VarHandle handle, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (float) MethodHandle.linkToStatic(handle, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (float) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect());
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_F_V(VarHandle handle, float arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_FF_Z(VarHandle handle, float arg0, float arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final float guard_FF_F(VarHandle handle, float arg0, float arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (float) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (float) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final float guard_F_F(VarHandle handle, float arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (float) MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (float) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final double guard__D(VarHandle handle, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (double) MethodHandle.linkToStatic(handle, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (double) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect());
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_D_V(VarHandle handle, double arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_DD_Z(VarHandle handle, double arg0, double arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final double guard_DD_D(VarHandle handle, double arg0, double arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (double) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (double) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final double guard_D_D(VarHandle handle, double arg0, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (double) MethodHandle.linkToStatic(handle, arg0, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (double) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final Object guard_LI_L(VarHandle handle, Object arg0, int arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            Object r = MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
            return ad.returnType.cast(r);
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LIL_V(VarHandle handle, Object arg0, int arg1, Object arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LILL_Z(VarHandle handle, Object arg0, int arg1, Object arg2, Object arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final Object guard_LILL_L(VarHandle handle, Object arg0, int arg1, Object arg2, Object arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            Object r = MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
            return ad.returnType.cast(r);
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final Object guard_LIL_L(VarHandle handle, Object arg0, int arg1, Object arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            Object r = MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
            return ad.returnType.cast(r);
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LII_V(VarHandle handle, Object arg0, int arg1, int arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LIII_Z(VarHandle handle, Object arg0, int arg1, int arg2, int arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final int guard_LIII_I(VarHandle handle, Object arg0, int arg1, int arg2, int arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (int) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (int) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final long guard_LI_J(VarHandle handle, Object arg0, int arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (long) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (long) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LIJ_V(VarHandle handle, Object arg0, int arg1, long arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LIJJ_Z(VarHandle handle, Object arg0, int arg1, long arg2, long arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final long guard_LIJJ_J(VarHandle handle, Object arg0, int arg1, long arg2, long arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (long) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (long) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final long guard_LIJ_J(VarHandle handle, Object arg0, int arg1, long arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (long) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (long) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final float guard_LI_F(VarHandle handle, Object arg0, int arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (float) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (float) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LIF_V(VarHandle handle, Object arg0, int arg1, float arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LIFF_Z(VarHandle handle, Object arg0, int arg1, float arg2, float arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final float guard_LIFF_F(VarHandle handle, Object arg0, int arg1, float arg2, float arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (float) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (float) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final float guard_LIF_F(VarHandle handle, Object arg0, int arg1, float arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (float) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (float) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final double guard_LI_D(VarHandle handle, Object arg0, int arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (double) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (double) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LID_V(VarHandle handle, Object arg0, int arg1, double arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LIDD_Z(VarHandle handle, Object arg0, int arg1, double arg2, double arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final double guard_LIDD_D(VarHandle handle, Object arg0, int arg1, double arg2, double arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (double) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (double) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final double guard_LID_D(VarHandle handle, Object arg0, int arg1, double arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (double) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (double) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final int guard_LJ_I(VarHandle handle, Object arg0, long arg1, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (int) MethodHandle.linkToStatic(handle, arg0, arg1, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (int) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LJI_V(VarHandle handle, Object arg0, long arg1, int arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LJII_Z(VarHandle handle, Object arg0, long arg1, int arg2, int arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final int guard_LJII_I(VarHandle handle, Object arg0, long arg1, int arg2, int arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (int) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (int) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final int guard_LJI_I(VarHandle handle, Object arg0, long arg1, int arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (int) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (int) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final void guard_LJJ_V(VarHandle handle, Object arg0, long arg1, long arg2, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
            MethodHandle.linkToStatic(handle, arg0, arg1, arg2, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final boolean guard_LJJJ_Z(VarHandle handle, Object arg0, long arg1, long arg2, long arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (boolean) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (boolean) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

    @ForceInline
    @LambdaForm.Compiled
    @Hidden
    static final long guard_LJJJ_J(VarHandle handle, Object arg0, long arg1, long arg2, long arg3, VarHandle.AccessDescriptor ad) throws Throwable {
        boolean direct = handle.checkAccessModeThenIsDirect(ad);
        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
            return (long) MethodHandle.linkToStatic(handle, arg0, arg1, arg2, arg3, handle.vform.getMemberName(ad.mode));
        } else {
            MethodHandle mh = handle.getMethodHandle(ad.mode);
            return (long) mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(handle.asDirect(), arg0, arg1, arg2, arg3);
        }
    }

}
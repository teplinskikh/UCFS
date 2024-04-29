/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file, and Oracle licenses the original version of this file under the BSD
 * license:
 */
/*
   Copyright 2009-2013 Attila Szegedi

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:
   * Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
   * Neither the name of the copyright holder nor the names of
     contributors may be used to endorse or promote products derived from
     this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
   IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
   TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
   PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
   BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
   CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
   SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
   BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
   WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
   OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
   ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jdk.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.StringTokenizer;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.support.Guards;
import jdk.dynalink.linker.support.Lookup;

/**
 * Base class for dynamic methods that dispatch to a single target Java method or constructor. Handles adaptation of the
 * target method to a call site type (including mapping variable arity methods to a call site signature with different
 * arity).
 */
abstract class SingleDynamicMethod extends DynamicMethod {
    private static final MethodHandle CAN_CONVERT_TO = Lookup.findOwnStatic(MethodHandles.lookup(), "canConvertTo", boolean.class, LinkerServices.class, Class.class, Object.class);

    SingleDynamicMethod(final String name) {
        super(name);
    }

    /**
     * Returns true if this method is variable arity.
     * @return true if this method is variable arity.
     */
    abstract boolean isVarArgs();

    /**
     * Returns this method's native type.
     * @return this method's native type.
     */
    abstract MethodType getMethodType();

    /**
     * Given a specified call site descriptor, returns a method handle to this method's target. The target
     * should only depend on the descriptor's lookup, and it should only retrieve it (as a privileged
     * operation) when it is absolutely needed.
     * @param desc the call site descriptor to use.
     * @return the handle to this method's target method.
     */
    abstract MethodHandle getTarget(CallSiteDescriptor desc);

    @Override
    MethodHandle getInvocation(final CallSiteDescriptor callSiteDescriptor, final LinkerServices linkerServices) {
        return linkerServices.getWithLookup(()->getInvocation(getTarget(callSiteDescriptor),
                callSiteDescriptor.getMethodType(), linkerServices), callSiteDescriptor);
    }

    @Override
    SingleDynamicMethod getMethodForExactParamTypes(final String paramTypes) {
        return typeMatchesDescription(paramTypes, getMethodType()) ? this : null;
    }

    @Override
    boolean contains(final SingleDynamicMethod method) {
        return getMethodType().parameterList().equals(method.getMethodType().parameterList());
    }

    static String getMethodNameWithSignature(final MethodType type, final String methodName, final boolean withReturnType) {
        final String typeStr = type.toString();
        final int retTypeIndex = typeStr.lastIndexOf(')') + 1;
        int secondParamIndex = typeStr.indexOf(',') + 1;
        if(secondParamIndex == 0) {
            secondParamIndex = retTypeIndex - 1;
        }
        final StringBuilder b = new StringBuilder();
        if (withReturnType) {
            b.append(typeStr, retTypeIndex, typeStr.length()).append(' ');
        }
        return b.append(methodName).append('(').append(typeStr, secondParamIndex, retTypeIndex).toString();
    }

    /**
     * Given a method handle and a call site type, adapts the method handle to the call site type. Performs type
     * conversions as needed using the specified linker services, and in case that the method handle is a vararg
     * collector, matches it to the arity of the call site. The type of the return value is only changed if it can be
     * converted using a conversion that loses neither precision nor magnitude, see
     * {@link LinkerServices#asTypeLosslessReturn(MethodHandle, MethodType)}.
     * @param target the method handle to adapt
     * @param callSiteType the type of the call site
     * @param linkerServices the linker services used for type conversions
     * @return the adapted method handle.
     */
    static MethodHandle getInvocation(final MethodHandle target, final MethodType callSiteType, final LinkerServices linkerServices) {
        final MethodHandle filteredTarget = linkerServices.filterInternalObjects(target);
        final MethodType methodType = filteredTarget.type();
        final int paramsLen = methodType.parameterCount();
        final boolean varArgs = target.isVarargsCollector();
        final MethodHandle fixTarget = varArgs ? filteredTarget.asFixedArity() : filteredTarget;
        final int fixParamsLen = varArgs ? paramsLen - 1 : paramsLen;
        final int argsLen = callSiteType.parameterCount();
        if(argsLen < fixParamsLen) {
            return null;
        }
        if(argsLen == fixParamsLen) {
            final MethodHandle matchedMethod;
            if(varArgs) {
                matchedMethod = MethodHandles.insertArguments(fixTarget, fixParamsLen, Array.newInstance(
                        methodType.parameterType(fixParamsLen).getComponentType(), 0));
            } else {
                matchedMethod = fixTarget;
            }
            return createConvertingInvocation(matchedMethod, linkerServices, callSiteType);
        }

        if(!varArgs) {
            return null;
        }

        final Class<?> varArgType = methodType.parameterType(fixParamsLen);
        if(argsLen == paramsLen) {
            final Class<?> callSiteLastArgType = callSiteType.parameterType(fixParamsLen);
            if(varArgType.isAssignableFrom(callSiteLastArgType)) {
                return createConvertingInvocation(filteredTarget, linkerServices, callSiteType).asVarargsCollector(
                        callSiteLastArgType);
            }

            final MethodHandle varArgCollectingInvocation = createConvertingInvocation(collectArguments(fixTarget,
                    argsLen), linkerServices, callSiteType);

            final boolean isAssignableFromArray = callSiteLastArgType.isAssignableFrom(varArgType);
            final boolean isCustomConvertible = linkerServices.canConvert(callSiteLastArgType, varArgType);
            if(!isAssignableFromArray && !isCustomConvertible) {
                return varArgCollectingInvocation;
            }

            final MethodHandle arrayConvertingInvocation = createConvertingInvocation(MethodHandles.filterArguments(
                    fixTarget, fixParamsLen, linkerServices.getTypeConverter(callSiteLastArgType, varArgType)),
                    linkerServices, callSiteType);

            final MethodHandle canConvertArgToArray = MethodHandles.insertArguments(CAN_CONVERT_TO, 0, linkerServices,
                    varArgType);

            final MethodHandle canConvertLastArgToArray = MethodHandles.dropArguments(canConvertArgToArray, 0,
                    MethodType.genericMethodType(fixParamsLen).parameterList()).asType(callSiteType.changeReturnType(boolean.class));

            final MethodHandle convertToArrayWhenPossible = MethodHandles.guardWithTest(canConvertLastArgToArray,
                    arrayConvertingInvocation, varArgCollectingInvocation);

            if(isAssignableFromArray) {
                return MethodHandles.guardWithTest(
                        Guards.isInstance(varArgType, fixParamsLen, callSiteType),
                        createConvertingInvocation(fixTarget, linkerServices, callSiteType),
                        isCustomConvertible ? convertToArrayWhenPossible : varArgCollectingInvocation);
            }

            assert isCustomConvertible;
            return convertToArrayWhenPossible;
        }

        return createConvertingInvocation(collectArguments(fixTarget, argsLen), linkerServices, callSiteType);
    }

    @SuppressWarnings("unused")
    private static boolean canConvertTo(final LinkerServices linkerServices, final Class<?> to, final Object obj) {
        return obj != null && linkerServices.canConvert(obj.getClass(), to);
    }

    /**
     * Creates a method handle out of the original target that will collect the varargs for the exact component type of
     * the varArg array. Note that this will nicely trigger language-specific type converters for exactly those varargs
     * for which it is necessary when later passed to linkerServices.convertArguments().
     *
     * @param target the original method handle
     * @param parameterCount the total number of arguments in the new method handle
     * @return a collecting method handle
     */
    static MethodHandle collectArguments(final MethodHandle target, final int parameterCount) {
        final MethodType methodType = target.type();
        final int fixParamsLen = methodType.parameterCount() - 1;
        final Class<?> arrayType = methodType.parameterType(fixParamsLen);
        return target.asCollector(arrayType, parameterCount - fixParamsLen);
    }

    private static MethodHandle createConvertingInvocation(final MethodHandle sizedMethod,
            final LinkerServices linkerServices, final MethodType callSiteType) {
        return linkerServices.asTypeLosslessReturn(sizedMethod, callSiteType);
    }

    private static boolean typeMatchesDescription(final String paramTypes, final MethodType type) {
        final StringTokenizer tok = new StringTokenizer(paramTypes, ", ");
        for(int i = 1; i < type.parameterCount(); ++i) { 
            if(!(tok.hasMoreTokens() && typeNameMatches(tok.nextToken(), type.parameterType(i)))) {
                return false;
            }
        }
        return !tok.hasMoreTokens();
    }

    private static boolean typeNameMatches(final String typeName, final Class<?> type) {
        return  typeName.equals(typeName.indexOf('.') == -1 ? type.getSimpleName() : type.getCanonicalName());
    }
}
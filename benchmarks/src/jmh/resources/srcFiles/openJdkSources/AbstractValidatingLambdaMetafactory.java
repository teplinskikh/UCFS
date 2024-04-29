/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

import sun.invoke.util.Wrapper;

import java.lang.reflect.Modifier;

import static java.lang.invoke.MethodHandleInfo.*;
import static sun.invoke.util.Wrapper.forPrimitiveType;
import static sun.invoke.util.Wrapper.forWrapperType;
import static sun.invoke.util.Wrapper.isWrapperType;

/**
 * Abstract implementation of a lambda metafactory which provides parameter
 * unrolling and input validation.
 *
 * @see LambdaMetafactory
 */
/* package */ abstract class AbstractValidatingLambdaMetafactory {

    /*
     * For context, the comments for the following fields are marked in quotes
     * with their values, given this program:
     * interface II<T> {  Object foo(T x); }
     * interface JJ<R extends Number> extends II<R> { }
     * class CC {  String impl(int i) { return "impl:"+i; }}
     * class X {
     *     public static void main(String[] args) {
     *         JJ<Integer> iii = (new CC())::impl;
     *         System.out.printf(">>> %s\n", iii.foo(44));
     * }}
     */
    final MethodHandles.Lookup caller;        
    final Class<?> targetClass;               
    final MethodType factoryType;             
    final Class<?> interfaceClass;            
    final String interfaceMethodName;         
    final MethodType interfaceMethodType;     
    final MethodHandle implementation;        
    final MethodType implMethodType;          
    final MethodHandleInfo implInfo;          
    final int implKind;                       
    final boolean implIsInstanceMethod;       
    final Class<?> implClass;                 
    final MethodType dynamicMethodType;       
    final boolean isSerializable;             
    final Class<?>[] altInterfaces;           
    final MethodType[] altMethods;            


    /**
     * Meta-factory constructor.
     *
     * @param caller Stacked automatically by VM; represents a lookup context
     *               with the accessibility privileges of the caller.
     * @param factoryType Stacked automatically by VM; the signature of the
     *                    invoked method, which includes the expected static
     *                    type of the returned lambda object, and the static
     *                    types of the captured arguments for the lambda.  In
     *                    the event that the implementation method is an
     *                    instance method, the first argument in the invocation
     *                    signature will correspond to the receiver.
     * @param interfaceMethodName Name of the method in the functional interface to
     *                            which the lambda or method reference is being
     *                            converted, represented as a String.
     * @param interfaceMethodType Type of the method in the functional interface to
     *                            which the lambda or method reference is being
     *                            converted, represented as a MethodType.
     * @param implementation The implementation method which should be called
     *                       (with suitable adaptation of argument types, return
     *                       types, and adjustment for captured arguments) when
     *                       methods of the resulting functional interface instance
     *                       are invoked.
     * @param dynamicMethodType The signature of the primary functional
     *                          interface method after type variables are
     *                          substituted with their instantiation from
     *                          the capture site
     * @param isSerializable Should the lambda be made serializable?  If set,
     *                       either the target type or one of the additional SAM
     *                       types must extend {@code Serializable}.
     * @param altInterfaces Additional interfaces which the lambda object
     *                      should implement.
     * @param altMethods Method types for additional signatures to be
     *                   implemented by invoking the implementation method
     * @throws LambdaConversionException If any of the meta-factory protocol
     *         invariants are violated
     * @throws SecurityException If a security manager is present, and it
     *         <a href="MethodHandles.Lookup.html#secmgr">denies access</a>
     *         from {@code caller} to the package of {@code implementation}.
     */
    AbstractValidatingLambdaMetafactory(MethodHandles.Lookup caller,
                                        MethodType factoryType,
                                        String interfaceMethodName,
                                        MethodType interfaceMethodType,
                                        MethodHandle implementation,
                                        MethodType dynamicMethodType,
                                        boolean isSerializable,
                                        Class<?>[] altInterfaces,
                                        MethodType[] altMethods)
            throws LambdaConversionException {
        if (!caller.hasFullPrivilegeAccess()) {
            throw new LambdaConversionException(String.format(
                    "Invalid caller: %s",
                    caller.lookupClass().getName()));
        }
        this.caller = caller;
        this.targetClass = caller.lookupClass();
        this.factoryType = factoryType;

        this.interfaceClass = factoryType.returnType();

        this.interfaceMethodName = interfaceMethodName;
        this.interfaceMethodType  = interfaceMethodType;

        this.implementation = implementation;
        this.implMethodType = implementation.type();
        try {
            this.implInfo = caller.revealDirect(implementation); 
        } catch (IllegalArgumentException e) {
            throw new LambdaConversionException(implementation + " is not direct or cannot be cracked");
        }
        switch (implInfo.getReferenceKind()) {
            case REF_invokeVirtual:
            case REF_invokeInterface:
                this.implClass = implMethodType.parameterType(0);
                this.implKind = implClass.isInterface() ? REF_invokeInterface : REF_invokeVirtual;
                this.implIsInstanceMethod = true;
                break;
            case REF_invokeSpecial:
                this.implClass = implInfo.getDeclaringClass();
                this.implIsInstanceMethod = true;

                if (targetClass == implClass && Modifier.isPrivate(implInfo.getModifiers())) {
                    this.implKind = implClass.isInterface() ? REF_invokeInterface : REF_invokeVirtual;
                } else {
                    this.implKind = REF_invokeSpecial;
                }
                break;
            case REF_invokeStatic:
            case REF_newInvokeSpecial:
                this.implClass = implInfo.getDeclaringClass();
                this.implKind = implInfo.getReferenceKind();
                this.implIsInstanceMethod = false;
                break;
            default:
                throw new LambdaConversionException(String.format("Unsupported MethodHandle kind: %s", implInfo));
        }

        this.dynamicMethodType = dynamicMethodType;
        this.isSerializable = isSerializable;
        this.altInterfaces = altInterfaces;
        this.altMethods = altMethods;

        if (interfaceMethodName.isEmpty() ||
                interfaceMethodName.indexOf('.') >= 0 ||
                interfaceMethodName.indexOf(';') >= 0 ||
                interfaceMethodName.indexOf('[') >= 0 ||
                interfaceMethodName.indexOf('/') >= 0 ||
                interfaceMethodName.indexOf('<') >= 0 ||
                interfaceMethodName.indexOf('>') >= 0) {
            throw new LambdaConversionException(String.format(
                    "Method name '%s' is not legal",
                    interfaceMethodName));
        }

        if (!interfaceClass.isInterface()) {
            throw new LambdaConversionException(String.format(
                    "%s is not an interface",
                    interfaceClass.getName()));
        }

        for (Class<?> c : altInterfaces) {
            if (!c.isInterface()) {
                throw new LambdaConversionException(String.format(
                        "%s is not an interface",
                        c.getName()));
            }
        }
    }

    /**
     * Build the CallSite.
     *
     * @return a CallSite, which, when invoked, will return an instance of the
     * functional interface
     * @throws LambdaConversionException
     */
    abstract CallSite buildCallSite()
            throws LambdaConversionException;

    /**
     * Check the meta-factory arguments for errors
     * @throws LambdaConversionException if there are improper conversions
     */
    void validateMetafactoryArgs() throws LambdaConversionException {
        final int implArity = implMethodType.parameterCount();
        final int capturedArity = factoryType.parameterCount();
        final int samArity = interfaceMethodType.parameterCount();
        final int dynamicArity = dynamicMethodType.parameterCount();
        if (implArity != capturedArity + samArity) {
            throw new LambdaConversionException(
                    String.format("Incorrect number of parameters for %s method %s; %d captured parameters, %d functional interface method parameters, %d implementation parameters",
                                  implIsInstanceMethod ? "instance" : "static", implInfo,
                                  capturedArity, samArity, implArity));
        }
        if (dynamicArity != samArity) {
            throw new LambdaConversionException(
                    String.format("Incorrect number of parameters for %s method %s; %d dynamic parameters, %d functional interface method parameters",
                                  implIsInstanceMethod ? "instance" : "static", implInfo,
                                  dynamicArity, samArity));
        }
        for (MethodType bridgeMT : altMethods) {
            if (bridgeMT.parameterCount() != samArity) {
                throw new LambdaConversionException(
                        String.format("Incorrect number of parameters for bridge signature %s; incompatible with %s",
                                      bridgeMT, interfaceMethodType));
            }
        }

        final int capturedStart; 
        final int samStart; 
        if (implIsInstanceMethod) {
            final Class<?> receiverClass;

            if (capturedArity == 0) {
                capturedStart = 0;
                samStart = 1;
                receiverClass = dynamicMethodType.parameterType(0);
            } else {
                capturedStart = 1;
                samStart = capturedArity;
                receiverClass = factoryType.parameterType(0);
            }

            if (!implClass.isAssignableFrom(receiverClass)) {
                throw new LambdaConversionException(
                        String.format("Invalid receiver type %s; not a subtype of implementation type %s",
                                      receiverClass, implClass));
            }
        } else {
            capturedStart = 0;
            samStart = capturedArity;
        }

        for (int i=capturedStart; i<capturedArity; i++) {
            Class<?> implParamType = implMethodType.parameterType(i);
            Class<?> capturedParamType = factoryType.parameterType(i);
            if (!capturedParamType.equals(implParamType)) {
                throw new LambdaConversionException(
                        String.format("Type mismatch in captured lambda parameter %d: expecting %s, found %s",
                                      i, capturedParamType, implParamType));
            }
        }
        for (int i=samStart; i<implArity; i++) {
            Class<?> implParamType = implMethodType.parameterType(i);
            Class<?> dynamicParamType = dynamicMethodType.parameterType(i - capturedArity);
            if (!isAdaptableTo(dynamicParamType, implParamType, true)) {
                throw new LambdaConversionException(
                        String.format("Type mismatch for lambda argument %d: %s is not convertible to %s",
                                      i, dynamicParamType, implParamType));
            }
        }

        Class<?> expectedType = dynamicMethodType.returnType();
        Class<?> actualReturnType = implMethodType.returnType();
        if (!isAdaptableToAsReturn(actualReturnType, expectedType)) {
            throw new LambdaConversionException(
                    String.format("Type mismatch for lambda return: %s is not convertible to %s",
                                  actualReturnType, expectedType));
        }

        checkDescriptor(interfaceMethodType);
        for (MethodType bridgeMT : altMethods) {
            checkDescriptor(bridgeMT);
        }
    }

    /** Validate that the given descriptor's types are compatible with {@code dynamicMethodType} **/
    private void checkDescriptor(MethodType descriptor) throws LambdaConversionException {
        for (int i = 0; i < dynamicMethodType.parameterCount(); i++) {
            Class<?> dynamicParamType = dynamicMethodType.parameterType(i);
            Class<?> descriptorParamType = descriptor.parameterType(i);
            if (!descriptorParamType.isAssignableFrom(dynamicParamType)) {
                String msg = String.format("Type mismatch for dynamic parameter %d: %s is not a subtype of %s",
                                           i, dynamicParamType, descriptorParamType);
                throw new LambdaConversionException(msg);
            }
        }

        Class<?> dynamicReturnType = dynamicMethodType.returnType();
        Class<?> descriptorReturnType = descriptor.returnType();
        if (!isAdaptableToAsReturnStrict(dynamicReturnType, descriptorReturnType)) {
            String msg = String.format("Type mismatch for lambda expected return: %s is not convertible to %s",
                                       dynamicReturnType, descriptorReturnType);
            throw new LambdaConversionException(msg);
        }
    }

    /**
     * Check type adaptability for parameter types.
     * @param fromType Type to convert from
     * @param toType Type to convert to
     * @param strict If true, do strict checks, else allow that fromType may be parameterized
     * @return True if 'fromType' can be passed to an argument of 'toType'
     */
    private boolean isAdaptableTo(Class<?> fromType, Class<?> toType, boolean strict) {
        if (fromType.equals(toType)) {
            return true;
        }
        if (fromType.isPrimitive()) {
            Wrapper wfrom = forPrimitiveType(fromType);
            if (toType.isPrimitive()) {
                Wrapper wto = forPrimitiveType(toType);
                return wto.isConvertibleFrom(wfrom);
            } else {
                return toType.isAssignableFrom(wfrom.wrapperType());
            }
        } else {
            if (toType.isPrimitive()) {
                Wrapper wfrom;
                if (isWrapperType(fromType) && (wfrom = forWrapperType(fromType)).primitiveType().isPrimitive()) {
                    Wrapper wto = forPrimitiveType(toType);
                    return wto.isConvertibleFrom(wfrom);
                } else {
                    return !strict;
                }
            } else {
                return !strict || toType.isAssignableFrom(fromType);
            }
        }
    }

    /**
     * Check type adaptability for return types --
     * special handling of void type) and parameterized fromType
     * @return True if 'fromType' can be converted to 'toType'
     */
    private boolean isAdaptableToAsReturn(Class<?> fromType, Class<?> toType) {
        return toType.equals(void.class)
               || !fromType.equals(void.class) && isAdaptableTo(fromType, toType, false);
    }
    private boolean isAdaptableToAsReturnStrict(Class<?> fromType, Class<?> toType) {
        if (fromType.equals(void.class) || toType.equals(void.class)) return fromType.equals(toType);
        else return isAdaptableTo(fromType, toType, true);
    }


    /*********** Logging support -- for debugging only, uncomment as needed
    static final Executor logPool = Executors.newSingleThreadExecutor();
    protected static void log(final String s) {
        MethodHandleProxyLambdaMetafactory.logPool.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println(s);
            }
        });
    }

    protected static void log(final String s, final Throwable e) {
        MethodHandleProxyLambdaMetafactory.logPool.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println(s);
                e.printStackTrace(System.out);
            }
        });
    }
    ***********************/

}
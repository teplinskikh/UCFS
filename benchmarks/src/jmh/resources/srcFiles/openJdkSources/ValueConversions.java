/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.invoke.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import jdk.internal.vm.annotation.Stable;

public class ValueConversions {
    private static final Class<?> THIS_CLASS = ValueConversions.class;
    private static final Lookup IMPL_LOOKUP = MethodHandles.lookup();

    /**
     * Thread-safe canonicalized mapping from Wrapper to MethodHandle
     * with unsynchronized reads and synchronized writes.
     * It's safe to publish MethodHandles by data race because they are immutable.
     */
    private static class WrapperCache {
        @Stable
        private final MethodHandle[] map = new MethodHandle[Wrapper.COUNT];

        public MethodHandle get(Wrapper w) {
            return map[w.ordinal()];
        }
        public synchronized MethodHandle put(final Wrapper w, final MethodHandle mh) {
            MethodHandle prev = map[w.ordinal()];
            if (prev != null) {
                return prev;
            } else {
                map[w.ordinal()] = mh;
                return mh;
            }
        }
    }

    private static WrapperCache[] newWrapperCaches(int n) {
        WrapperCache[] caches = new WrapperCache[n];
        for (int i = 0; i < n; i++)
            caches[i] = new WrapperCache();
        return caches;
    }



    static int unboxInteger(Integer x) {
        return x;
    }
    static int unboxInteger(Object x, boolean cast) {
        if (x instanceof Integer i)
            return i;
        return primitiveConversion(Wrapper.INT, x, cast).intValue();
    }

    static byte unboxByte(Byte x) {
        return x;
    }
    static byte unboxByte(Object x, boolean cast) {
        if (x instanceof Byte b)
            return b;
        return primitiveConversion(Wrapper.BYTE, x, cast).byteValue();
    }

    static short unboxShort(Short x) {
        return x;
    }
    static short unboxShort(Object x, boolean cast) {
        if (x instanceof Short s)
            return s;
        return primitiveConversion(Wrapper.SHORT, x, cast).shortValue();
    }

    static boolean unboxBoolean(Boolean x) {
        return x;
    }
    static boolean unboxBoolean(Object x, boolean cast) {
        if (x instanceof Boolean b)
            return b;
        return (primitiveConversion(Wrapper.BOOLEAN, x, cast).intValue() & 1) != 0;
    }

    static char unboxCharacter(Character x) {
        return x;
    }
    static char unboxCharacter(Object x, boolean cast) {
        if (x instanceof Character c)
            return c;
        return (char) primitiveConversion(Wrapper.CHAR, x, cast).intValue();
    }

    static long unboxLong(Long x) {
        return x;
    }
    static long unboxLong(Object x, boolean cast) {
        if (x instanceof Long l)
            return l;
        return primitiveConversion(Wrapper.LONG, x, cast).longValue();
    }

    static float unboxFloat(Float x) {
        return x;
    }
    static float unboxFloat(Object x, boolean cast) {
        if (x instanceof Float f)
            return f;
        return primitiveConversion(Wrapper.FLOAT, x, cast).floatValue();
    }

    static double unboxDouble(Double x) {
        return x;
    }
    static double unboxDouble(Object x, boolean cast) {
        if (x instanceof Double d)
            return d;
        return primitiveConversion(Wrapper.DOUBLE, x, cast).doubleValue();
    }

    private static MethodType unboxType(Wrapper wrap, int kind) {
        if (kind == 0)
            return MethodType.methodType(wrap.primitiveType(), wrap.wrapperType());
        return MethodType.methodType(wrap.primitiveType(), Object.class, boolean.class);
    }

    private static final WrapperCache[] UNBOX_CONVERSIONS = newWrapperCaches(4);

    private static MethodHandle unbox(Wrapper wrap, int kind) {
        WrapperCache cache = UNBOX_CONVERSIONS[kind];
        MethodHandle mh = cache.get(wrap);
        if (mh != null) {
            return mh;
        }
        switch (wrap) {
            case OBJECT:
            case VOID:
                throw new IllegalArgumentException("unbox "+wrap);
        }
        String name = "unbox" + wrap.wrapperSimpleName();
        MethodType type = unboxType(wrap, kind);
        try {
            mh = IMPL_LOOKUP.findStatic(THIS_CLASS, name, type);
        } catch (ReflectiveOperationException ex) {
            mh = null;
        }
        if (mh != null) {
            if (kind > 0) {
                boolean cast = (kind != 2);
                mh = MethodHandles.insertArguments(mh, 1, cast);
            }
            if (kind == 1) {  
                mh = mh.asType(unboxType(wrap, 0));
            }
            return cache.put(wrap, mh);
        }
        throw new IllegalArgumentException("cannot find unbox adapter for " + wrap
                + (kind <= 1 ? " (exact)" : kind == 3 ? " (cast)" : ""));
    }

    /** Return an exact unboxer for the given primitive type. */
    public static MethodHandle unboxExact(Wrapper type) {
        return unbox(type, 0);
    }

    /** Return an exact unboxer for the given primitive type, with optional null-to-zero conversion.
     *  The boolean says whether to throw an NPE on a null value (false means unbox a zero).
     *  The type of the unboxer is of a form like (Integer)int.
     */
    public static MethodHandle unboxExact(Wrapper type, boolean throwNPE) {
        return unbox(type, throwNPE ? 0 : 1);
    }

    /** Return a widening unboxer for the given primitive type.
     *  Widen narrower primitive boxes to the given type.
     *  Do not narrow any primitive values or convert null to zero.
     *  The type of the unboxer is of a form like (Object)int.
     */
    public static MethodHandle unboxWiden(Wrapper type) {
        return unbox(type, 2);
    }

    /** Return a casting unboxer for the given primitive type.
     *  Widen or narrow primitive values to the given type, or convert null to zero, as needed.
     *  The type of the unboxer is of a form like (Object)int.
     */
    public static MethodHandle unboxCast(Wrapper type) {
        return unbox(type, 3);
    }

    private static final Integer ZERO_INT = 0, ONE_INT = 1;

    /**
     * Produce a Number which represents the given value {@code x}
     * according to the primitive type of the given wrapper {@code wrap}.
     * Caller must invoke intValue, byteValue, longValue (etc.) on the result
     * to retrieve the desired primitive value.
     */
    public static Number primitiveConversion(Wrapper wrap, Object x, boolean cast) {
        Number res;
        if (x == null) {
            if (!cast)  return null;
            return ZERO_INT;
        }
        if (x instanceof Number n) {
            res = n;
        } else if (x instanceof Boolean b) {
            res = b ? ONE_INT : ZERO_INT;
        } else if (x instanceof Character c) {
            res = (int) c;
        } else {
            res = (Number) x;
        }
        Wrapper xwrap = Wrapper.findWrapperType(x.getClass());
        if (xwrap == null || !cast && !wrap.isConvertibleFrom(xwrap))
            return (Number) wrap.wrapperType().cast(x);
        return res;
    }

    /**
     * The JVM verifier allows boolean, byte, short, or char to widen to int.
     * Support exactly this conversion, from a boxed value type Boolean,
     * Byte, Short, Character, or Integer.
     */
    public static int widenSubword(Object x) {
        if (x instanceof Integer i)
            return i;
        else if (x instanceof Boolean b)
            return fromBoolean(b);
        else if (x instanceof Character c)
            return c;
        else if (x instanceof Short s)
            return s;
        else if (x instanceof Byte b)
            return b;
        else
            return (int) x;
    }


    static Integer boxInteger(int x) {
        return x;
    }

    static Byte boxByte(byte x) {
        return x;
    }

    static Short boxShort(short x) {
        return x;
    }

    static Boolean boxBoolean(boolean x) {
        return x;
    }

    static Character boxCharacter(char x) {
        return x;
    }

    static Long boxLong(long x) {
        return x;
    }

    static Float boxFloat(float x) {
        return x;
    }

    static Double boxDouble(double x) {
        return x;
    }

    private static MethodType boxType(Wrapper wrap) {
        Class<?> boxType = wrap.wrapperType();
        return MethodType.methodType(boxType, wrap.primitiveType());
    }

    private static final WrapperCache[] BOX_CONVERSIONS = newWrapperCaches(1);

    public static MethodHandle boxExact(Wrapper wrap) {
        WrapperCache cache = BOX_CONVERSIONS[0];
        MethodHandle mh = cache.get(wrap);
        if (mh != null) {
            return mh;
        }
        String name = "box" + wrap.wrapperSimpleName();
        MethodType type = boxType(wrap);
        try {
            mh = IMPL_LOOKUP.findStatic(THIS_CLASS, name, type);
        } catch (ReflectiveOperationException ex) {
            mh = null;
        }
        if (mh != null) {
            return cache.put(wrap, mh);
        }
        throw new IllegalArgumentException("cannot find box adapter for " + wrap);
    }


    static void ignore(Object x) {
    }

    private static class Handles {
        static final MethodHandle IGNORE;
        static {
            try {
                MethodType idType = MethodType.genericMethodType(1);
                MethodType ignoreType = idType.changeReturnType(void.class);
                IGNORE = IMPL_LOOKUP.findStatic(THIS_CLASS, "ignore", ignoreType);
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw newInternalError("uncaught exception", ex);
            }
        }
    }

    public static MethodHandle ignore() {
        return Handles.IGNORE;
    }



    static float doubleToFloat(double x) {  
        return (float) x;
    }
    static long doubleToLong(double x) {  
        return (long) x;
    }
    static int doubleToInt(double x) {  
        return (int) x;
    }
    static short doubleToShort(double x) {  
        return (short) x;
    }
    static char doubleToChar(double x) {  
        return (char) x;
    }
    static byte doubleToByte(double x) {  
        return (byte) x;
    }
    static boolean doubleToBoolean(double x) {
        return toBoolean((byte) x);
    }

    static double floatToDouble(float x) {  
        return x;
    }
    static long floatToLong(float x) {  
        return (long) x;
    }
    static int floatToInt(float x) {  
        return (int) x;
    }
    static short floatToShort(float x) {  
        return (short) x;
    }
    static char floatToChar(float x) {  
        return (char) x;
    }
    static byte floatToByte(float x) {  
        return (byte) x;
    }
    static boolean floatToBoolean(float x) {
        return toBoolean((byte) x);
    }

    static double longToDouble(long x) {  
        return x;
    }
    static float longToFloat(long x) {  
        return x;
    }
    static int longToInt(long x) {  
        return (int) x;
    }
    static short longToShort(long x) {  
        return (short) x;
    }
    static char longToChar(long x) {  
        return (char) x;
    }
    static byte longToByte(long x) {  
        return (byte) x;
    }
    static boolean longToBoolean(long x) {
        return toBoolean((byte) x);
    }

    static double intToDouble(int x) {  
        return x;
    }
    static float intToFloat(int x) {  
        return x;
    }
    static long intToLong(int x) {  
        return x;
    }
    static short intToShort(int x) {  
        return (short) x;
    }
    static char intToChar(int x) {  
        return (char) x;
    }
    static byte intToByte(int x) {  
        return (byte) x;
    }
    static boolean intToBoolean(int x) {
        return toBoolean((byte) x);
    }

    static double shortToDouble(short x) {  
        return x;
    }
    static float shortToFloat(short x) {  
        return x;
    }
    static long shortToLong(short x) {  
        return x;
    }
    static int shortToInt(short x) {  
        return x;
    }
    static char shortToChar(short x) {  
        return (char)x;
    }
    static byte shortToByte(short x) {  
        return (byte)x;
    }
    static boolean shortToBoolean(short x) {
        return toBoolean((byte) x);
    }

    static double charToDouble(char x) {  
        return x;
    }
    static float charToFloat(char x) {  
        return x;
    }
    static long charToLong(char x) {  
        return x;
    }
    static int charToInt(char x) {  
        return x;
    }
    static short charToShort(char x) {  
        return (short)x;
    }
    static byte charToByte(char x) {  
        return (byte)x;
    }
    static boolean charToBoolean(char x) {
        return toBoolean((byte) x);
    }

    static double byteToDouble(byte x) {  
        return x;
    }
    static float byteToFloat(byte x) {  
        return x;
    }
    static long byteToLong(byte x) {  
        return x;
    }
    static int byteToInt(byte x) {  
        return x;
    }
    static short byteToShort(byte x) {  
        return (short)x;
    }
    static char byteToChar(byte x) {  
        return (char)x;
    }
    static boolean byteToBoolean(byte x) {
        return toBoolean(x);
    }

    static double booleanToDouble(boolean x) {
        return fromBoolean(x);
    }
    static float booleanToFloat(boolean x) {
        return fromBoolean(x);
    }
    static long booleanToLong(boolean x) {
        return fromBoolean(x);
    }
    static int booleanToInt(boolean x) {
        return fromBoolean(x);
    }
    static short booleanToShort(boolean x) {
        return fromBoolean(x);
    }
    static char booleanToChar(boolean x) {
        return (char)fromBoolean(x);
    }
    static byte booleanToByte(boolean x) {
        return fromBoolean(x);
    }

    static boolean toBoolean(byte x) {
        return ((x & 1) != 0);
    }
    static byte fromBoolean(boolean x) {
        return (x ? (byte)1 : (byte)0);
    }

    private static final WrapperCache[] CONVERT_PRIMITIVE_FUNCTIONS = newWrapperCaches(Wrapper.COUNT);

    public static MethodHandle convertPrimitive(Wrapper wsrc, Wrapper wdst) {
        WrapperCache cache = CONVERT_PRIMITIVE_FUNCTIONS[wsrc.ordinal()];
        MethodHandle mh = cache.get(wdst);
        if (mh != null) {
            return mh;
        }
        Class<?> src = wsrc.primitiveType();
        Class<?> dst = wdst.primitiveType();
        MethodType type = MethodType.methodType(dst, src);
        if (wsrc == wdst) {
            mh = MethodHandles.identity(src);
        } else {
            assert(src.isPrimitive() && dst.isPrimitive());
            try {
                mh = IMPL_LOOKUP.findStatic(THIS_CLASS, src.getSimpleName()+"To"+capitalize(dst.getSimpleName()), type);
            } catch (ReflectiveOperationException ex) {
                mh = null;
            }
        }
        if (mh != null) {
            assert(mh.type() == type) : mh;
            return cache.put(wdst, mh);
        }

        throw new IllegalArgumentException("cannot find primitive conversion function for " +
                                           src.getSimpleName()+" -> "+dst.getSimpleName());
    }

    public static MethodHandle convertPrimitive(Class<?> src, Class<?> dst) {
        return convertPrimitive(Wrapper.forPrimitiveType(src), Wrapper.forPrimitiveType(dst));
    }

    private static String capitalize(String x) {
        return Character.toUpperCase(x.charAt(0))+x.substring(1);
    }

    private static InternalError newInternalError(String message, Throwable cause) {
        return new InternalError(message, cause);
    }
}
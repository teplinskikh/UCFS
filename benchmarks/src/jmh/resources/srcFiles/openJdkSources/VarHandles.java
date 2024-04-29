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

import jdk.internal.foreign.Utils;
import sun.invoke.util.Wrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static java.lang.invoke.MethodHandleStatics.UNSAFE;
import static java.lang.invoke.MethodHandleStatics.VAR_HANDLE_SEGMENT_FORCE_EXACT;
import static java.lang.invoke.MethodHandleStatics.VAR_HANDLE_IDENTITY_ADAPT;
import static java.lang.invoke.MethodHandleStatics.newIllegalArgumentException;

final class VarHandles {

    static ClassValue<ConcurrentMap<Integer, MethodHandle>> ADDRESS_FACTORIES = new ClassValue<>() {
        @Override
        protected ConcurrentMap<Integer, MethodHandle> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    static VarHandle makeFieldHandle(MemberName f, Class<?> refc, boolean isWriteAllowedOnFinalFields) {
        if (!f.isStatic()) {
            long foffset = MethodHandleNatives.objectFieldOffset(f);
            Class<?> type = f.getFieldType();
            if (!type.isPrimitive()) {
                return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleReferences.FieldInstanceReadOnly(refc, foffset, type)
                       : new VarHandleReferences.FieldInstanceReadWrite(refc, foffset, type));
            }
            else if (type == boolean.class) {
                return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleBooleans.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleBooleans.FieldInstanceReadWrite(refc, foffset));
            }
            else if (type == byte.class) {
                return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleBytes.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleBytes.FieldInstanceReadWrite(refc, foffset));
            }
            else if (type == short.class) {
                return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleShorts.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleShorts.FieldInstanceReadWrite(refc, foffset));
            }
            else if (type == char.class) {
                return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleChars.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleChars.FieldInstanceReadWrite(refc, foffset));
            }
            else if (type == int.class) {
                return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleInts.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleInts.FieldInstanceReadWrite(refc, foffset));
            }
            else if (type == long.class) {
                return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleLongs.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleLongs.FieldInstanceReadWrite(refc, foffset));
            }
            else if (type == float.class) {
                return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleFloats.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleFloats.FieldInstanceReadWrite(refc, foffset));
            }
            else if (type == double.class) {
                return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleDoubles.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleDoubles.FieldInstanceReadWrite(refc, foffset));
            }
            else {
                throw new UnsupportedOperationException();
            }
        }
        else {
            Class<?> decl = f.getDeclaringClass();
            var vh = makeStaticFieldVarHandle(decl, f, isWriteAllowedOnFinalFields);
            return maybeAdapt(UNSAFE.shouldBeInitialized(decl)
                    ? new LazyInitializingVarHandle(vh, decl)
                    : vh);
        }
    }

    static VarHandle makeStaticFieldVarHandle(Class<?> decl, MemberName f, boolean isWriteAllowedOnFinalFields) {
        Object base = MethodHandleNatives.staticFieldBase(f);
        long foffset = MethodHandleNatives.staticFieldOffset(f);
        Class<?> type = f.getFieldType();
        if (!type.isPrimitive()) {
            return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                    ? new VarHandleReferences.FieldStaticReadOnly(decl, base, foffset, type)
                    : new VarHandleReferences.FieldStaticReadWrite(decl, base, foffset, type));
        }
        else if (type == boolean.class) {
            return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                    ? new VarHandleBooleans.FieldStaticReadOnly(decl, base, foffset)
                    : new VarHandleBooleans.FieldStaticReadWrite(decl, base, foffset));
        }
        else if (type == byte.class) {
            return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                    ? new VarHandleBytes.FieldStaticReadOnly(decl, base, foffset)
                    : new VarHandleBytes.FieldStaticReadWrite(decl, base, foffset));
        }
        else if (type == short.class) {
            return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                    ? new VarHandleShorts.FieldStaticReadOnly(decl, base, foffset)
                    : new VarHandleShorts.FieldStaticReadWrite(decl, base, foffset));
        }
        else if (type == char.class) {
            return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                    ? new VarHandleChars.FieldStaticReadOnly(decl, base, foffset)
                    : new VarHandleChars.FieldStaticReadWrite(decl, base, foffset));
        }
        else if (type == int.class) {
            return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                    ? new VarHandleInts.FieldStaticReadOnly(decl, base, foffset)
                    : new VarHandleInts.FieldStaticReadWrite(decl, base, foffset));
        }
        else if (type == long.class) {
            return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                    ? new VarHandleLongs.FieldStaticReadOnly(decl, base, foffset)
                    : new VarHandleLongs.FieldStaticReadWrite(decl, base, foffset));
        }
        else if (type == float.class) {
            return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                    ? new VarHandleFloats.FieldStaticReadOnly(decl, base, foffset)
                    : new VarHandleFloats.FieldStaticReadWrite(decl, base, foffset));
        }
        else if (type == double.class) {
            return maybeAdapt(f.isFinal() && !isWriteAllowedOnFinalFields
                    ? new VarHandleDoubles.FieldStaticReadOnly(decl, base, foffset)
                    : new VarHandleDoubles.FieldStaticReadWrite(decl, base, foffset));
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    static Field getFieldFromReceiverAndOffset(Class<?> receiverType,
                                               long offset,
                                               Class<?> fieldType) {
        for (Field f : receiverType.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;

            if (offset == UNSAFE.objectFieldOffset(f)) {
                assert f.getType() == fieldType;
                return f;
            }
        }
        throw new InternalError("Field not found at offset");
    }

    static Field getStaticFieldFromBaseAndOffset(Class<?> declaringClass,
                                                 long offset,
                                                 Class<?> fieldType) {
        for (Field f : declaringClass.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;

            if (offset == UNSAFE.staticFieldOffset(f)) {
                assert f.getType() == fieldType;
                return f;
            }
        }
        throw new InternalError("Static field not found at offset");
    }

    static VarHandle makeArrayElementHandle(Class<?> arrayClass) {
        if (!arrayClass.isArray())
            throw new IllegalArgumentException("not an array: " + arrayClass);

        Class<?> componentType = arrayClass.getComponentType();

        int aoffset = UNSAFE.arrayBaseOffset(arrayClass);
        int ascale = UNSAFE.arrayIndexScale(arrayClass);
        int ashift = 31 - Integer.numberOfLeadingZeros(ascale);

        if (!componentType.isPrimitive()) {
            return maybeAdapt(new VarHandleReferences.Array(aoffset, ashift, arrayClass));
        }
        else if (componentType == boolean.class) {
            return maybeAdapt(new VarHandleBooleans.Array(aoffset, ashift));
        }
        else if (componentType == byte.class) {
            return maybeAdapt(new VarHandleBytes.Array(aoffset, ashift));
        }
        else if (componentType == short.class) {
            return maybeAdapt(new VarHandleShorts.Array(aoffset, ashift));
        }
        else if (componentType == char.class) {
            return maybeAdapt(new VarHandleChars.Array(aoffset, ashift));
        }
        else if (componentType == int.class) {
            return maybeAdapt(new VarHandleInts.Array(aoffset, ashift));
        }
        else if (componentType == long.class) {
            return maybeAdapt(new VarHandleLongs.Array(aoffset, ashift));
        }
        else if (componentType == float.class) {
            return maybeAdapt(new VarHandleFloats.Array(aoffset, ashift));
        }
        else if (componentType == double.class) {
            return maybeAdapt(new VarHandleDoubles.Array(aoffset, ashift));
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    static VarHandle byteArrayViewHandle(Class<?> viewArrayClass,
                                         boolean be) {
        if (!viewArrayClass.isArray())
            throw new IllegalArgumentException("not an array: " + viewArrayClass);

        Class<?> viewComponentType = viewArrayClass.getComponentType();

        if (viewComponentType == long.class) {
            return maybeAdapt(new VarHandleByteArrayAsLongs.ArrayHandle(be));
        }
        else if (viewComponentType == int.class) {
            return maybeAdapt(new VarHandleByteArrayAsInts.ArrayHandle(be));
        }
        else if (viewComponentType == short.class) {
            return maybeAdapt(new VarHandleByteArrayAsShorts.ArrayHandle(be));
        }
        else if (viewComponentType == char.class) {
            return maybeAdapt(new VarHandleByteArrayAsChars.ArrayHandle(be));
        }
        else if (viewComponentType == double.class) {
            return maybeAdapt(new VarHandleByteArrayAsDoubles.ArrayHandle(be));
        }
        else if (viewComponentType == float.class) {
            return maybeAdapt(new VarHandleByteArrayAsFloats.ArrayHandle(be));
        }

        throw new UnsupportedOperationException();
    }

    static VarHandle makeByteBufferViewHandle(Class<?> viewArrayClass,
                                              boolean be) {
        if (!viewArrayClass.isArray())
            throw new IllegalArgumentException("not an array: " + viewArrayClass);

        Class<?> viewComponentType = viewArrayClass.getComponentType();

        if (viewComponentType == long.class) {
            return maybeAdapt(new VarHandleByteArrayAsLongs.ByteBufferHandle(be));
        }
        else if (viewComponentType == int.class) {
            return maybeAdapt(new VarHandleByteArrayAsInts.ByteBufferHandle(be));
        }
        else if (viewComponentType == short.class) {
            return maybeAdapt(new VarHandleByteArrayAsShorts.ByteBufferHandle(be));
        }
        else if (viewComponentType == char.class) {
            return maybeAdapt(new VarHandleByteArrayAsChars.ByteBufferHandle(be));
        }
        else if (viewComponentType == double.class) {
            return maybeAdapt(new VarHandleByteArrayAsDoubles.ByteBufferHandle(be));
        }
        else if (viewComponentType == float.class) {
            return maybeAdapt(new VarHandleByteArrayAsFloats.ByteBufferHandle(be));
        }

        throw new UnsupportedOperationException();
    }

    /**
     * Creates a memory segment view var handle.
     *
     * The resulting var handle will take a memory segment as first argument (the segment to be dereferenced),
     * and a {@code long} as second argument (the offset into the segment).
     *
     * @param carrier the Java carrier type.
     * @param alignmentMask alignment requirement to be checked upon access. In bytes. Expressed as a mask.
     * @param byteOrder the byte order.
     * @return the created VarHandle.
     */
    static VarHandle memorySegmentViewHandle(Class<?> carrier, long alignmentMask,
                                             ByteOrder byteOrder) {
        if (!carrier.isPrimitive() || carrier == void.class || carrier == boolean.class) {
            throw new IllegalArgumentException("Invalid carrier: " + carrier.getName());
        }
        long size = Utils.byteWidthOfPrimitive(carrier);
        boolean be = byteOrder == ByteOrder.BIG_ENDIAN;
        boolean exact = VAR_HANDLE_SEGMENT_FORCE_EXACT;

        if (carrier == byte.class) {
            return maybeAdapt(new VarHandleSegmentAsBytes(be, size, alignmentMask, exact));
        } else if (carrier == char.class) {
            return maybeAdapt(new VarHandleSegmentAsChars(be, size, alignmentMask, exact));
        } else if (carrier == short.class) {
            return maybeAdapt(new VarHandleSegmentAsShorts(be, size, alignmentMask, exact));
        } else if (carrier == int.class) {
            return maybeAdapt(new VarHandleSegmentAsInts(be, size, alignmentMask, exact));
        } else if (carrier == float.class) {
            return maybeAdapt(new VarHandleSegmentAsFloats(be, size, alignmentMask, exact));
        } else if (carrier == long.class) {
            return maybeAdapt(new VarHandleSegmentAsLongs(be, size, alignmentMask, exact));
        } else if (carrier == double.class) {
            return maybeAdapt(new VarHandleSegmentAsDoubles(be, size, alignmentMask, exact));
        } else {
            throw new IllegalStateException("Cannot get here");
        }
    }

    private static VarHandle maybeAdapt(VarHandle target) {
        if (!VAR_HANDLE_IDENTITY_ADAPT) return target;
        target = filterValue(target,
                        MethodHandles.identity(target.varType()), MethodHandles.identity(target.varType()));
        MethodType mtype = target.accessModeType(VarHandle.AccessMode.GET);
        for (int i = 0 ; i < mtype.parameterCount() ; i++) {
            target = filterCoordinates(target, i, MethodHandles.identity(mtype.parameterType(i)));
        }
        return target;
    }

    public static VarHandle filterValue(VarHandle target, MethodHandle pFilterToTarget, MethodHandle pFilterFromTarget) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(pFilterToTarget);
        Objects.requireNonNull(pFilterFromTarget);
        MethodHandle filterToTarget = adaptForCheckedExceptions(pFilterToTarget);
        MethodHandle filterFromTarget = adaptForCheckedExceptions(pFilterFromTarget);

        List<Class<?>> newCoordinates = new ArrayList<>();
        List<Class<?>> additionalCoordinates = new ArrayList<>();
        newCoordinates.addAll(target.coordinateTypes());

        if (filterFromTarget.type().parameterCount() != filterToTarget.type().parameterCount()) {
            throw newIllegalArgumentException("filterFromTarget and filterToTarget have different arity", filterFromTarget.type(), filterToTarget.type());
        } else if (filterFromTarget.type().parameterCount() < 1) {
            throw newIllegalArgumentException("filterFromTarget filter type has wrong arity", filterFromTarget.type());
        } else if (filterToTarget.type().parameterCount() < 1) {
            throw newIllegalArgumentException("filterToTarget filter type has wrong arity", filterFromTarget.type());
        } else if (filterFromTarget.type().lastParameterType() != filterToTarget.type().returnType() ||
                filterToTarget.type().lastParameterType() != filterFromTarget.type().returnType()) {
            throw newIllegalArgumentException("filterFromTarget and filterToTarget filter types do not match", filterFromTarget.type(), filterToTarget.type());
        } else if (target.varType() != filterFromTarget.type().lastParameterType()) {
            throw newIllegalArgumentException("filterFromTarget filter type does not match target var handle type", filterFromTarget.type(), target.varType());
        } else if (target.varType() != filterToTarget.type().returnType()) {
            throw newIllegalArgumentException("filterFromTarget filter type does not match target var handle type", filterToTarget.type(), target.varType());
        } else if (filterFromTarget.type().parameterCount() > 1) {
            for (int i = 0 ; i < filterFromTarget.type().parameterCount() - 1 ; i++) {
                if (filterFromTarget.type().parameterType(i) != filterToTarget.type().parameterType(i)) {
                    throw newIllegalArgumentException("filterFromTarget and filterToTarget filter types do not match", filterFromTarget.type(), filterToTarget.type());
                } else {
                    newCoordinates.add(filterFromTarget.type().parameterType(i));
                    additionalCoordinates.add((filterFromTarget.type().parameterType(i)));
                }
            }
        }

        return new IndirectVarHandle(target, filterFromTarget.type().returnType(), newCoordinates.toArray(new Class<?>[0]),
                (mode, modeHandle) -> {
                    int lastParameterPos = modeHandle.type().parameterCount() - 1;
                    return switch (mode.at) {
                        case GET -> MethodHandles.collectReturnValue(modeHandle, filterFromTarget);
                        case SET -> MethodHandles.collectArguments(modeHandle, lastParameterPos, filterToTarget);
                        case GET_AND_UPDATE -> {
                            MethodHandle adapter = MethodHandles.collectReturnValue(modeHandle, filterFromTarget);
                            MethodHandle res = MethodHandles.collectArguments(adapter, lastParameterPos, filterToTarget);
                            if (additionalCoordinates.size() > 0) {
                                res = joinDuplicateArgs(res, lastParameterPos,
                                        lastParameterPos + additionalCoordinates.size() + 1,
                                        additionalCoordinates.size());
                            }
                            yield res;
                        }
                        case COMPARE_AND_EXCHANGE -> {
                            MethodHandle adapter = MethodHandles.collectReturnValue(modeHandle, filterFromTarget);
                            adapter = MethodHandles.collectArguments(adapter, lastParameterPos, filterToTarget);
                            if (additionalCoordinates.size() > 0) {
                                adapter = joinDuplicateArgs(adapter, lastParameterPos,
                                        lastParameterPos + additionalCoordinates.size() + 1,
                                        additionalCoordinates.size());
                            }
                            MethodHandle res = MethodHandles.collectArguments(adapter, lastParameterPos - 1, filterToTarget);
                            if (additionalCoordinates.size() > 0) {
                                res = joinDuplicateArgs(res, lastParameterPos - 1,
                                        lastParameterPos + additionalCoordinates.size(),
                                        additionalCoordinates.size());
                            }
                            yield res;
                        }
                        case COMPARE_AND_SET -> {
                            MethodHandle adapter = MethodHandles.collectArguments(modeHandle, lastParameterPos, filterToTarget);
                            MethodHandle res = MethodHandles.collectArguments(adapter, lastParameterPos - 1, filterToTarget);
                            if (additionalCoordinates.size() > 0) {
                                res = joinDuplicateArgs(res, lastParameterPos - 1,
                                        lastParameterPos + additionalCoordinates.size(),
                                        additionalCoordinates.size());
                            }
                            yield res;
                        }
                    };
                });
    }

    private static MethodHandle joinDuplicateArgs(MethodHandle handle, int originalStart, int dropStart, int length) {
        int[] perms = new int[handle.type().parameterCount()];
        for (int i = 0 ; i < dropStart; i++) {
            perms[i] = i;
        }
        for (int i = 0 ; i < length ; i++) {
            perms[dropStart + i] = originalStart + i;
        }
        for (int i = dropStart + length ; i < perms.length ; i++) {
            perms[i] = i - length;
        }
        return MethodHandles.permuteArguments(handle,
                handle.type().dropParameterTypes(dropStart, dropStart + length),
                perms);
    }

    public static VarHandle filterCoordinates(VarHandle target, int pos, MethodHandle... filters) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(filters);

        List<Class<?>> targetCoordinates = target.coordinateTypes();
        if (pos < 0 || pos >= targetCoordinates.size()) {
            throw newIllegalArgumentException("Invalid position " + pos + " for coordinate types", targetCoordinates);
        } else if (pos + filters.length > targetCoordinates.size()) {
            throw new IllegalArgumentException("Too many filters");
        }

        if (filters.length == 0) return target;

        List<Class<?>> newCoordinates = new ArrayList<>(targetCoordinates);
        for (int i = 0 ; i < filters.length ; i++) {
            MethodHandle filter = Objects.requireNonNull(filters[i]);
            filter = adaptForCheckedExceptions(filter);
            MethodType filterType = filter.type();
            if (filterType.parameterCount() != 1) {
                throw newIllegalArgumentException("Invalid filter type " + filterType);
            } else if (newCoordinates.get(pos + i) != filterType.returnType()) {
                throw newIllegalArgumentException("Invalid filter type " + filterType + " for coordinate type " + newCoordinates.get(i));
            }
            newCoordinates.set(pos + i, filters[i].type().parameterType(0));
        }

        return new IndirectVarHandle(target, target.varType(), newCoordinates.toArray(new Class<?>[0]),
                (mode, modeHandle) -> MethodHandles.filterArguments(modeHandle, 1 + pos, filters));
    }

    public static VarHandle insertCoordinates(VarHandle target, int pos, Object... values) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(values);

        List<Class<?>> targetCoordinates = target.coordinateTypes();
        if (pos < 0 || pos >= targetCoordinates.size()) {
            throw newIllegalArgumentException("Invalid position " + pos + " for coordinate types", targetCoordinates);
        } else if (pos + values.length > targetCoordinates.size()) {
            throw new IllegalArgumentException("Too many values");
        }

        if (values.length == 0) return target;

        List<Class<?>> newCoordinates = new ArrayList<>(targetCoordinates);
        for (int i = 0 ; i < values.length ; i++) {
            Class<?> pt = newCoordinates.get(pos);
            if (pt.isPrimitive()) {
                Wrapper w = Wrapper.forPrimitiveType(pt);
                w.convert(values[i], pt);
            } else {
                pt.cast(values[i]);
            }
            newCoordinates.remove(pos);
        }

        return new IndirectVarHandle(target, target.varType(), newCoordinates.toArray(new Class<?>[0]),
                (mode, modeHandle) -> MethodHandles.insertArguments(modeHandle, 1 + pos, values));
    }

    public static VarHandle permuteCoordinates(VarHandle target, List<Class<?>> newCoordinates, int... reorder) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(newCoordinates);
        Objects.requireNonNull(reorder);

        List<Class<?>> targetCoordinates = target.coordinateTypes();
        MethodHandles.permuteArgumentChecks(reorder,
                MethodType.methodType(void.class, newCoordinates),
                MethodType.methodType(void.class, targetCoordinates));

        return new IndirectVarHandle(target, target.varType(), newCoordinates.toArray(new Class<?>[0]),
                (mode, modeHandle) ->
                        MethodHandles.permuteArguments(modeHandle,
                                methodTypeFor(mode.at, modeHandle.type(), targetCoordinates, newCoordinates),
                                reorderArrayFor(mode.at, newCoordinates, reorder)));
    }

    private static int numTrailingArgs(VarHandle.AccessType at) {
        return switch (at) {
            case GET -> 0;
            case GET_AND_UPDATE, SET -> 1;
            case COMPARE_AND_SET, COMPARE_AND_EXCHANGE -> 2;
        };
    }

    private static int[] reorderArrayFor(VarHandle.AccessType at, List<Class<?>> newCoordinates, int[] reorder) {
        int numTrailingArgs = numTrailingArgs(at);
        int[] adjustedReorder = new int[reorder.length + 1 + numTrailingArgs];
        adjustedReorder[0] = 0;
        for (int i = 0 ; i < reorder.length ; i++) {
            adjustedReorder[i + 1] = reorder[i] + 1;
        }
        for (int i = 0 ; i < numTrailingArgs ; i++) {
            adjustedReorder[i + reorder.length + 1] = i + newCoordinates.size() + 1;
        }
        return adjustedReorder;
    }

    private static MethodType methodTypeFor(VarHandle.AccessType at, MethodType oldType, List<Class<?>> oldCoordinates, List<Class<?>> newCoordinates) {
        int numTrailingArgs = numTrailingArgs(at);
        MethodType adjustedType = MethodType.methodType(oldType.returnType(), oldType.parameterType(0));
        adjustedType = adjustedType.appendParameterTypes(newCoordinates);
        for (int i = 0 ; i < numTrailingArgs ; i++) {
            adjustedType = adjustedType.appendParameterTypes(oldType.parameterType(1 + oldCoordinates.size() + i));
        }
        return adjustedType;
    }

    public static VarHandle collectCoordinates(VarHandle target, int pos, MethodHandle pFilter) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(pFilter);
        MethodHandle filter = adaptForCheckedExceptions(pFilter);

        List<Class<?>> targetCoordinates = target.coordinateTypes();
        if (pos < 0 || pos >= targetCoordinates.size()) {
            throw newIllegalArgumentException("Invalid position " + pos + " for coordinate types", targetCoordinates);
        } else if (filter.type().returnType() != void.class && filter.type().returnType() != targetCoordinates.get(pos)) {
            throw newIllegalArgumentException("Invalid filter type " + filter.type() + " for coordinate type " + targetCoordinates.get(pos));
        }

        List<Class<?>> newCoordinates = new ArrayList<>(targetCoordinates);
        if (filter.type().returnType() != void.class) {
            newCoordinates.remove(pos);
        }
        newCoordinates.addAll(pos, filter.type().parameterList());

        return new IndirectVarHandle(target, target.varType(), newCoordinates.toArray(new Class<?>[0]),
                (mode, modeHandle) -> MethodHandles.collectArguments(modeHandle, 1 + pos, filter));
    }

    public static VarHandle dropCoordinates(VarHandle target, int pos, Class<?>... valueTypes) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(valueTypes);

        List<Class<?>> targetCoordinates = target.coordinateTypes();
        if (pos < 0 || pos > targetCoordinates.size()) {
            throw newIllegalArgumentException("Invalid position " + pos + " for coordinate types", targetCoordinates);
        }

        if (valueTypes.length == 0) return target;

        List<Class<?>> newCoordinates = new ArrayList<>(targetCoordinates);
        newCoordinates.addAll(pos, List.of(valueTypes));

        return new IndirectVarHandle(target, target.varType(), newCoordinates.toArray(new Class<?>[0]),
                (mode, modeHandle) -> MethodHandles.dropArguments(modeHandle, 1 + pos, valueTypes));
    }

    private static MethodHandle adaptForCheckedExceptions(MethodHandle target) {
        Class<?>[] exceptionTypes = exceptionTypes(target);
        if (exceptionTypes != null) { 
            if (Stream.of(exceptionTypes).anyMatch(VarHandles::isCheckedException)) {
                throw newIllegalArgumentException("Cannot adapt a var handle with a method handle which throws checked exceptions");
            }
            return target; 
        } else {
            MethodHandle handler = MethodHandleImpl.getConstantHandle(MethodHandleImpl.MH_VarHandles_handleCheckedExceptions);
            MethodHandle zero = MethodHandles.zero(target.type().returnType()); 
            handler = MethodHandles.collectArguments(zero, 0, handler);
            return MethodHandles.catchException(target, Throwable.class, handler);
        }
    }

    static void handleCheckedExceptions(Throwable throwable) throws Throwable {
        if (isCheckedException(throwable.getClass())) {
            throw new IllegalStateException("Adapter handle threw checked exception", throwable);
        }
        throw throwable;
    }

    static Class<?>[] exceptionTypes(MethodHandle handle) {
        if (handle instanceof DirectMethodHandle directHandle) {
            byte refKind = directHandle.member.getReferenceKind();
            MethodHandleInfo info = new InfoFromMemberName(
                    MethodHandles.Lookup.IMPL_LOOKUP,
                    directHandle.member,
                    refKind);
            if (MethodHandleNatives.refKindIsMethod(refKind)) {
                return info.reflectAs(Method.class, MethodHandles.Lookup.IMPL_LOOKUP)
                        .getExceptionTypes();
            } else if (MethodHandleNatives.refKindIsField(refKind)) {
                return new Class<?>[0];
            } else if (MethodHandleNatives.refKindIsConstructor(refKind)) {
                return info.reflectAs(Constructor.class, MethodHandles.Lookup.IMPL_LOOKUP)
                        .getExceptionTypes();
            } else {
                throw new AssertionError("Cannot get here");
            }
        } else if (handle instanceof DelegatingMethodHandle delegatingMh) {
            return exceptionTypes(delegatingMh.getTarget());
        } else if (handle instanceof NativeMethodHandle) {
            return new Class<?>[0];
        }

        assert handle instanceof BoundMethodHandle : "Unexpected handle type: " + handle;
        return null;
    }

    private static boolean isCheckedException(Class<?> clazz) {
        return Throwable.class.isAssignableFrom(clazz) &&
                !RuntimeException.class.isAssignableFrom(clazz) &&
                !Error.class.isAssignableFrom(clazz);
    }

}
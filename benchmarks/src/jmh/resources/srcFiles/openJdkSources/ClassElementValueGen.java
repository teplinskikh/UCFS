/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.generic;

import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.classfile.ClassElementValue;
import com.sun.org.apache.bcel.internal.classfile.ConstantUtf8;
import com.sun.org.apache.bcel.internal.classfile.ElementValue;

/**
 * @since 6.0
 */
public class ClassElementValueGen extends ElementValueGen {
    private final int idx;

    public ClassElementValueGen(final ClassElementValue value, final ConstantPoolGen cpool, final boolean copyPoolEntries) {
        super(CLASS, cpool);
        if (copyPoolEntries) {
            idx = cpool.addUtf8(value.getClassString());
        } else {
            idx = value.getIndex();
        }
    }

    protected ClassElementValueGen(final int typeIdx, final ConstantPoolGen cpool) {
        super(ElementValueGen.CLASS, cpool);
        this.idx = typeIdx;
    }

    public ClassElementValueGen(final ObjectType t, final ConstantPoolGen cpool) {
        super(ElementValueGen.CLASS, cpool);
        idx = cpool.addUtf8(t.getSignature());
    }

    @Override
    public void dump(final DataOutputStream dos) throws IOException {
        dos.writeByte(super.getElementValueType()); 
        dos.writeShort(idx);
    }

    public String getClassString() {
        final ConstantUtf8 cu8 = (ConstantUtf8) getConstantPool().getConstant(idx);
        return cu8.getBytes();
    }

    /**
     * Return immutable variant of this ClassElementValueGen
     */
    @Override
    public ElementValue getElementValue() {
        return new ClassElementValue(super.getElementValueType(), idx, getConstantPool().getConstantPool());
    }

    public int getIndex() {
        return idx;
    }

    @Override
    public String stringifyValue() {
        return getClassString();
    }
}
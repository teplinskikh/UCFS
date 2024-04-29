/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.org.apache.xalan.internal.xsltc.compiler.util;
import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.generic.ConstantPoolGen;
import com.sun.org.apache.bcel.internal.generic.Instruction;
import com.sun.org.apache.bcel.internal.generic.Visitor;

/**
 * A special abstract dummy subclass of
 * {@link org.apache.bcel.generic.Instruction} used to mark locations of
 * interest in an {@link com.sun.org.apache.bcel.internal.generic.InstructionList}.  It and
 * its subclasses are only used as placeholders, and do not contribute to the
 * actual byte code instruction stream.
 */
abstract class MarkerInstruction extends Instruction {
    /**
     * Zero-argument constructor.  Sets the opcode to an invalid value and
     * sets the length to zero, as it will not be written as part of the
     * generated byte code.
     */
    public MarkerInstruction() {
        super(Const.UNDEFINED, (short) 0);
    }

    /**
     * {@link com.sun.org.apache.bcel.internal.generic.Visitor}s will know nothing about this
     * kind of {@link org.apche.bcel.generic.Instruction}, so this method does
     * nothing.
     */
    public void accept(Visitor v) {
    }

    /**
     * The number of JVM stack entries consumed by the instruction.
     * This instruction is just a place holder, so it does not consume any
     * stack entries.
     * @param cpg The {@link com.sun.org.apache.bcel.internal.generic.ConstantPoolGen} for the
     * current {@link com.sun.org.apache.bcel.internal.generic.ClassGen}
     * @return <code>0</code> always
     */
    final public int consumeStack(ConstantPoolGen cpg) {
        return 0;
    }
    /**
     * The number of JVM stack entries produced by the instruction.
     * This instruction is just a place holder, so it does not produce any
     * stack entries.
     * @param cpg The {@link com.sun.org.apache.bcel.internal.generic.ConstantPoolGen} for the
     * current {@link com.sun.org.apache.bcel.internal.generic.ClassGen}
     * @return <code>0</code> always
     */
    final public int produceStack(ConstantPoolGen cpg) {
        return 0;
    }

    /**
     * Produce a copy of the instruction.  By default a
     * {@link MarkerInstruction} has no parameters, so the base implementation
     * of {@link #copy()} returns the instruction itself.
     * @return The instruction itself.
     */
    public Instruction copy() {
        return this;
    }
    /**
     * Dump instruction as byte code to stream out.  A {@link MarkerInstruction}
     * has no effect on the generated byte code so it is never emitted to the
     * output stream.
     * @param out Output stream
     */
    final public void dump(DataOutputStream out) throws IOException {
    }
}
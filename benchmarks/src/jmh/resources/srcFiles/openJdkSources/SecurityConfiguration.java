/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.org.apache.xerces.internal.parsers;

import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.xni.grammars.XMLGrammarPool;
import com.sun.org.apache.xerces.internal.xni.parser.XMLComponentManager;
import com.sun.org.apache.xerces.internal.util.SymbolTable;
import jdk.xml.internal.XMLSecurityManager;

/**
 * This configuration allows Xerces to behave in a security-conscious manner; that is,
 * it permits applications to instruct Xerces to limit certain
 * operations that could be exploited by malicious document authors to cause a denail-of-service
 * attack when the document is parsed.
 *
 * In addition to the features and properties recognized by the base
 * parser configuration, this class recognizes these additional
 * features and properties:
 * <ul>
 * <li>Properties
 *  <ul>
 *   <li>http:
 *  </ul>
 * </ul>
 *
 * @author Neil Graham, IBM
 * @LastModified: July 2023
 */
public class SecurityConfiguration extends XIncludeAwareParserConfiguration
{


    protected static final String SECURITY_MANAGER_PROPERTY =
        Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY;


    /** Default constructor. */
    public SecurityConfiguration () {
        this(null, null, null);
    } 

    /**
     * Constructs a parser configuration using the specified symbol table.
     *
     * @param symbolTable The symbol table to use.
     */
    public SecurityConfiguration (SymbolTable symbolTable) {
        this(symbolTable, null, null);
    } 

    /**
     * Constructs a parser configuration using the specified symbol table and
     * grammar pool.
     * <p>
     * <strong>REVISIT:</strong>
     * Grammar pool will be updated when the new validation engine is
     * implemented.
     *
     * @param symbolTable The symbol table to use.
     * @param grammarPool The grammar pool to use.
     */
    public SecurityConfiguration (SymbolTable symbolTable,
                                         XMLGrammarPool grammarPool) {
        this(symbolTable, grammarPool, null);
    } 

    /**
     * Constructs a parser configuration using the specified symbol table,
     * grammar pool, and parent settings.
     * <p>
     * <strong>REVISIT:</strong>
     * Grammar pool will be updated when the new validation engine is
     * implemented.
     *
     * @param symbolTable    The symbol table to use.
     * @param grammarPool    The grammar pool to use.
     * @param parentSettings The parent settings.
     */
    public SecurityConfiguration (SymbolTable symbolTable,
                                         XMLGrammarPool grammarPool,
                                         XMLComponentManager parentSettings) {
        super(symbolTable, grammarPool, parentSettings);

        setProperty(SECURITY_MANAGER_PROPERTY, new XMLSecurityManager(true));
    } 

} 
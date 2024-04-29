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

package com.sun.org.apache.xerces.internal.impl.dtd;

import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.xni.parser.XMLComponentManager;

/**
 * This allows the validator to correctlyhandle XML 1.1
 * documents.
 *
 * @xerces.internal
 *
 * @author Neil Graham
 */
public class XML11DTDValidator extends XMLDTDValidator {


    protected final static String DTD_VALIDATOR_PROPERTY =
        Constants.XERCES_PROPERTY_PREFIX+Constants.DTD_VALIDATOR_PROPERTY;


    /** Default constructor. */
    public XML11DTDValidator() {

        super();
    } 

    public void reset(XMLComponentManager manager) {
        XMLDTDValidator curr = null;
        if((curr = (XMLDTDValidator)manager.getProperty(DTD_VALIDATOR_PROPERTY)) != null &&
                curr != this) {
            fGrammarBucket = curr.getGrammarBucket();
        }
        super.reset(manager);
    } 

    protected void init() {
        if(fValidation || fDynamicValidation) {
            super.init();

            try {
                fValID       = fDatatypeValidatorFactory.getBuiltInDV("XML11ID");
                fValIDRef    = fDatatypeValidatorFactory.getBuiltInDV("XML11IDREF");
                fValIDRefs   = fDatatypeValidatorFactory.getBuiltInDV("XML11IDREFS");
                fValNMTOKEN  = fDatatypeValidatorFactory.getBuiltInDV("XML11NMTOKEN");
                fValNMTOKENS = fDatatypeValidatorFactory.getBuiltInDV("XML11NMTOKENS");

            }
            catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    } 

} 
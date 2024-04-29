/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.org.apache.xerces.internal.dom.AttrImpl;
import com.sun.org.apache.xerces.internal.dom.CoreDocumentImpl;
import com.sun.org.apache.xerces.internal.dom.DOMErrorImpl;
import com.sun.org.apache.xerces.internal.dom.DOMMessageFormatter;
import com.sun.org.apache.xerces.internal.dom.DeferredDocumentImpl;
import com.sun.org.apache.xerces.internal.dom.DocumentImpl;
import com.sun.org.apache.xerces.internal.dom.DocumentTypeImpl;
import com.sun.org.apache.xerces.internal.dom.ElementDefinitionImpl;
import com.sun.org.apache.xerces.internal.dom.ElementImpl;
import com.sun.org.apache.xerces.internal.dom.ElementNSImpl;
import com.sun.org.apache.xerces.internal.dom.EntityImpl;
import com.sun.org.apache.xerces.internal.dom.EntityReferenceImpl;
import com.sun.org.apache.xerces.internal.dom.NodeImpl;
import com.sun.org.apache.xerces.internal.dom.NotationImpl;
import com.sun.org.apache.xerces.internal.dom.PSVIAttrNSImpl;
import com.sun.org.apache.xerces.internal.dom.PSVIDocumentImpl;
import com.sun.org.apache.xerces.internal.dom.PSVIElementNSImpl;
import com.sun.org.apache.xerces.internal.dom.TextImpl;
import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.impl.dv.XSSimpleType;
import com.sun.org.apache.xerces.internal.util.DOMErrorHandlerWrapper;
import com.sun.org.apache.xerces.internal.utils.ObjectFactory;
import com.sun.org.apache.xerces.internal.xni.Augmentations;
import com.sun.org.apache.xerces.internal.xni.NamespaceContext;
import com.sun.org.apache.xerces.internal.xni.QName;
import com.sun.org.apache.xerces.internal.xni.XMLAttributes;
import com.sun.org.apache.xerces.internal.xni.XMLLocator;
import com.sun.org.apache.xerces.internal.xni.XMLResourceIdentifier;
import com.sun.org.apache.xerces.internal.xni.XMLString;
import com.sun.org.apache.xerces.internal.xni.XNIException;
import com.sun.org.apache.xerces.internal.xni.parser.XMLParserConfiguration;
import com.sun.org.apache.xerces.internal.xs.AttributePSVI;
import com.sun.org.apache.xerces.internal.xs.ElementPSVI;
import com.sun.org.apache.xerces.internal.xs.XSTypeDefinition;
import java.util.Locale;
import java.util.Stack;
import jdk.xml.internal.JdkXmlUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMError;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.w3c.dom.ls.LSParserFilter;
import org.w3c.dom.traversal.NodeFilter;
import org.xml.sax.SAXException;

/**
 * This is the base class of all DOM parsers. It implements the XNI
 * callback methods to create the DOM tree. After a successful parse of
 * an XML document, the DOM Document object can be queried using the
 * <code>getDocument</code> method. The actual pipeline is defined in
 * parser configuration.
 *
 * @author Arnaud Le Hors, IBM
 * @author Andy Clark, IBM
 * @author Elena Litani, IBM
 *
 * @LastModified: July 2021
 */
public class AbstractDOMParser extends AbstractXMLDocumentParser {



    /** Feature id: namespace. */
    protected static final String NAMESPACES =
    Constants.SAX_FEATURE_PREFIX+Constants.NAMESPACES_FEATURE;

    /** Feature id: create entity ref nodes. */
    protected static final String CREATE_ENTITY_REF_NODES =
    Constants.XERCES_FEATURE_PREFIX + Constants.CREATE_ENTITY_REF_NODES_FEATURE;

    /** Feature id: include comments. */
    protected static final String INCLUDE_COMMENTS_FEATURE =
    Constants.XERCES_FEATURE_PREFIX + Constants.INCLUDE_COMMENTS_FEATURE;

    /** Feature id: create cdata nodes. */
    protected static final String CREATE_CDATA_NODES_FEATURE =
    Constants.XERCES_FEATURE_PREFIX + Constants.CREATE_CDATA_NODES_FEATURE;

    /** Feature id: include ignorable whitespace. */
    protected static final String INCLUDE_IGNORABLE_WHITESPACE =
    Constants.XERCES_FEATURE_PREFIX + Constants.INCLUDE_IGNORABLE_WHITESPACE;

    /** Feature id: defer node expansion. */
    protected static final String DEFER_NODE_EXPANSION =
    Constants.XERCES_FEATURE_PREFIX + Constants.DEFER_NODE_EXPANSION_FEATURE;


    /** Recognized features. */
    private static final String[] RECOGNIZED_FEATURES = {
        NAMESPACES,
        CREATE_ENTITY_REF_NODES,
        INCLUDE_COMMENTS_FEATURE,
        CREATE_CDATA_NODES_FEATURE,
        INCLUDE_IGNORABLE_WHITESPACE,
        DEFER_NODE_EXPANSION
    };


    /** Property id: document class name. */
    protected static final String DOCUMENT_CLASS_NAME =
    Constants.XERCES_PROPERTY_PREFIX + Constants.DOCUMENT_CLASS_NAME_PROPERTY;

    protected static final String  CURRENT_ELEMENT_NODE=
    Constants.XERCES_PROPERTY_PREFIX + Constants.CURRENT_ELEMENT_NODE_PROPERTY;


    /** Recognized properties. */
    private static final String[] RECOGNIZED_PROPERTIES = {
        DOCUMENT_CLASS_NAME,
        CURRENT_ELEMENT_NODE,
    };


    /** Default document class name. */
    protected static final String DEFAULT_DOCUMENT_CLASS_NAME =
    "com.sun.org.apache.xerces.internal.dom.DocumentImpl";

    protected static final String CORE_DOCUMENT_CLASS_NAME =
    "com.sun.org.apache.xerces.internal.dom.CoreDocumentImpl";

    protected static final String PSVI_DOCUMENT_CLASS_NAME =
    "com.sun.org.apache.xerces.internal.dom.PSVIDocumentImpl";

    /**
     * If the user stops the process, this exception will be thrown.
     */
    static final class Abort extends RuntimeException {
        private static final long serialVersionUID = 1687848994976808490L;
        static final Abort INSTANCE = new Abort();
        private Abort() {}
        public Throwable fillInStackTrace() {
            return this;
        }
    }


    private static final boolean DEBUG_EVENTS = false;
    private static final boolean DEBUG_BASEURI = false;


    /** DOM L3 error handler */
    protected DOMErrorHandlerWrapper fErrorHandler = null;

    /** True if inside DTD. */
    protected boolean fInDTD;


    /** Create entity reference nodes. */
    protected boolean fCreateEntityRefNodes;

    /** Include ignorable whitespace. */
    protected boolean fIncludeIgnorableWhitespace;

    /** Include Comments. */
    protected boolean fIncludeComments;

    /** Create cdata nodes. */
    protected boolean fCreateCDATANodes;


    /** The document. */
    protected Document fDocument;

    /** The default Xerces document implementation, if used. */
    protected CoreDocumentImpl fDocumentImpl;

    /** Whether to store PSVI information in DOM tree. */
    protected boolean fStorePSVI;

    /** The document class name to use. */
    protected String  fDocumentClassName;

    /** The document type node. */
    protected DocumentType fDocumentType;

    /** Current node. */
    protected Node fCurrentNode;
    protected CDATASection fCurrentCDATASection;
    protected EntityImpl fCurrentEntityDecl;
    protected int fDeferredEntityDecl;

    /** Character buffer */
    protected final StringBuilder fStringBuilder = new StringBuilder (50);


    /** Internal subset buffer. */
    protected StringBuilder fInternalSubset;


    protected boolean              fDeferNodeExpansion;
    protected boolean              fNamespaceAware;
    protected DeferredDocumentImpl fDeferredDocumentImpl;
    protected int                  fDocumentIndex;
    protected int                  fDocumentTypeIndex;
    protected int                  fCurrentNodeIndex;
    protected int                  fCurrentCDATASectionIndex;


    /** True if inside DTD external subset. */
    protected boolean fInDTDExternalSubset;

    /** Root element node. */
    protected Node fRoot;

    /** True if inside CDATA section. */
    protected boolean fInCDATASection;

    /** True if saw the first chunk of characters*/
    protected boolean fFirstChunk = false;


    /** LSParserFilter: specifies that element with given QNAME and all its children
     * must be rejected */
    protected boolean fFilterReject = false;


    /** Base uri stack*/
    protected final Stack<String> fBaseURIStack = new Stack<>();

    /** LSParserFilter: tracks the element depth within a rejected subtree. */
    protected int fRejectedElementDepth = 0;

    /** LSParserFilter: store depth of skipped elements */
    protected Stack<Boolean> fSkippedElemStack = null;

    /** LSParserFilter: true if inside entity reference */
    protected boolean fInEntityRef = false;

    /** Attribute QName. */
    private final QName fAttrQName = new QName();

    /** Document locator. */
    private XMLLocator fLocator;


    protected LSParserFilter fDOMFilter = null;


    /** Default constructor. */
    protected AbstractDOMParser (XMLParserConfiguration config) {

        super (config);


        fConfiguration.addRecognizedFeatures (RECOGNIZED_FEATURES);

        fConfiguration.setFeature (CREATE_ENTITY_REF_NODES, true);
        fConfiguration.setFeature (INCLUDE_IGNORABLE_WHITESPACE, true);
        fConfiguration.setFeature (DEFER_NODE_EXPANSION, true);
        fConfiguration.setFeature (INCLUDE_COMMENTS_FEATURE, true);
        fConfiguration.setFeature (CREATE_CDATA_NODES_FEATURE, true);

        fConfiguration.addRecognizedProperties (RECOGNIZED_PROPERTIES);

        fConfiguration.setProperty (DOCUMENT_CLASS_NAME,
        DEFAULT_DOCUMENT_CLASS_NAME);

    } 

    /**
     * This method retrieves the name of current document class.
     */
    protected String getDocumentClassName () {
        return fDocumentClassName;
    }

    /**
     * This method allows the programmer to decide which document
     * factory to use when constructing the DOM tree. However, doing
     * so will lose the functionality of the default factory. Also,
     * a document class other than the default will lose the ability
     * to defer node expansion on the DOM tree produced.
     *
     * @param documentClassName The fully qualified class name of the
     *                      document factory to use when constructing
     *                      the DOM tree.
     *
     * @see #getDocumentClassName
     * @see #DEFAULT_DOCUMENT_CLASS_NAME
     */
    protected void setDocumentClassName (String documentClassName) {

        if (documentClassName == null) {
            documentClassName = DEFAULT_DOCUMENT_CLASS_NAME;
        }

        if (!documentClassName.equals(DEFAULT_DOCUMENT_CLASS_NAME) &&
            !documentClassName.equals(PSVI_DOCUMENT_CLASS_NAME)) {
            try {
                Class<?> _class = ObjectFactory.findProviderClass (documentClassName, true);
                if (!Document.class.isAssignableFrom (_class)) {
                    throw new IllegalArgumentException (
                        DOMMessageFormatter.formatMessage(
                        DOMMessageFormatter.DOM_DOMAIN,
                        "InvalidDocumentClassName", new Object [] {documentClassName}));
                }
            }
            catch (ClassNotFoundException e) {
                throw new IllegalArgumentException (
                    DOMMessageFormatter.formatMessage(
                    DOMMessageFormatter.DOM_DOMAIN,
                    "MissingDocumentClassName", new Object [] {documentClassName}));
            }
        }

        fDocumentClassName = documentClassName;
        if (!documentClassName.equals (DEFAULT_DOCUMENT_CLASS_NAME)) {
            fDeferNodeExpansion = false;
        }

    } 


    /** Returns the DOM document object. */
    public Document getDocument () {
        return fDocument;
    } 

    /**
     * Drops all references to the last DOM which was built by this parser.
     */
    public final void dropDocumentReferences() {
        fDocument = null;
        fDocumentImpl = null;
        fDeferredDocumentImpl = null;
        fDocumentType = null;
        fCurrentNode = null;
        fCurrentCDATASection = null;
        fCurrentEntityDecl = null;
        fRoot = null;
    } 


    /**
     * Resets the parser state.
     *
     * @throws SAXException Thrown on initialization error.
     */
    public void reset () throws XNIException {
        super.reset ();


        fCreateEntityRefNodes =
        fConfiguration.getFeature (CREATE_ENTITY_REF_NODES);

        fIncludeIgnorableWhitespace =
        fConfiguration.getFeature (INCLUDE_IGNORABLE_WHITESPACE);

        fDeferNodeExpansion =
        fConfiguration.getFeature (DEFER_NODE_EXPANSION);

        fNamespaceAware = fConfiguration.getFeature (NAMESPACES);

        fIncludeComments = fConfiguration.getFeature (INCLUDE_COMMENTS_FEATURE);

        fCreateCDATANodes = fConfiguration.getFeature (CREATE_CDATA_NODES_FEATURE);

        setDocumentClassName ((String)
        fConfiguration.getProperty (DOCUMENT_CLASS_NAME));

        fDocument = null;
        fDocumentImpl = null;
        fStorePSVI = false;
        fDocumentType = null;
        fDocumentTypeIndex = -1;
        fDeferredDocumentImpl = null;
        fCurrentNode = null;

        fStringBuilder.setLength (0);

        fRoot = null;
        fInDTD = false;
        fInDTDExternalSubset = false;
        fInCDATASection = false;
        fFirstChunk = false;
        fCurrentCDATASection = null;
        fCurrentCDATASectionIndex = -1;

        fBaseURIStack.removeAllElements ();


    } 

    /**
     * Set the locale to use for messages.
     *
     * @param locale The locale object to use for localization of messages.
     *
     */
    public void setLocale (Locale locale) {
        fConfiguration.setLocale (locale);

    } 


    /**
     * This method notifies the start of a general entity.
     * <p>
     * <strong>Note:</strong> This method is not called for entity references
     * appearing as part of attribute values.
     *
     * @param name     The name of the general entity.
     * @param identifier The resource identifier.
     * @param encoding The auto-detected IANA encoding name of the entity
     *                 stream. This value will be null in those situations
     *                 where the entity encoding is not auto-detected (e.g.
     *                 internal entities or a document entity that is
     *                 parsed from a java.io.Reader).
     * @param augs     Additional information that may include infoset augmentations
     *
     * @exception XNIException Thrown by handler to signal an error.
     */
    public void startGeneralEntity (String name,
    XMLResourceIdentifier identifier,
    String encoding, Augmentations augs)
    throws XNIException {
        if (DEBUG_EVENTS) {
            System.out.println ("==>startGeneralEntity ("+name+")");
            if (DEBUG_BASEURI) {
                System.out.println ("   expandedSystemId( **baseURI): " +
                        identifier == null ? null : identifier.getExpandedSystemId());
                System.out.println ("   baseURI:" +
                        identifier == null ? null : identifier.getBaseSystemId());
            }
        }

        if (!fDeferNodeExpansion) {
            if (fFilterReject) {
                return;
            }
            setCharacterData (true);
            EntityReference er = fDocument.createEntityReference (name);
            if (fDocumentImpl != null) {

                EntityReferenceImpl erImpl =(EntityReferenceImpl)er;

                erImpl.setBaseURI (identifier == null ? null : identifier.getExpandedSystemId());
                if (fDocumentType != null) {
                    NamedNodeMap entities = fDocumentType.getEntities ();
                    fCurrentEntityDecl = (EntityImpl) entities.getNamedItem (name);
                    if (fCurrentEntityDecl != null) {
                        fCurrentEntityDecl.setInputEncoding (encoding);
                    }

                }
                erImpl.needsSyncChildren (false);
            }
            fInEntityRef = true;
            fCurrentNode.appendChild (er);

            if (!fCreateEntityRefNodes) {
                fCurrentNode = er;
            } else {
                ((NodeImpl)er).setReadOnly (true, true);
            }
        }
        else {

            int er = fDeferredDocumentImpl.createDeferredEntityReference (name,
                    identifier == null ? null : identifier.getExpandedSystemId ());
            if (fDocumentTypeIndex != -1) {
                int node = fDeferredDocumentImpl.getLastChild (fDocumentTypeIndex, false);
                while (node != -1) {
                    short nodeType = fDeferredDocumentImpl.getNodeType (node, false);
                    if (nodeType == Node.ENTITY_NODE) {
                        String nodeName =
                        fDeferredDocumentImpl.getNodeName (node, false);
                        if (nodeName.equals (name)) {
                            fDeferredEntityDecl = node;
                            fDeferredDocumentImpl.setInputEncoding (node, encoding);
                            break;
                        }
                    }
                    node = fDeferredDocumentImpl.getRealPrevSibling (node, false);
                }
            }
            fDeferredDocumentImpl.appendChild (fCurrentNodeIndex, er);

            if (!fCreateEntityRefNodes) {
                fCurrentNodeIndex = er;
            }
        }

    } 

    /**
     * Notifies of the presence of a TextDecl line in an entity. If present,
     * this method will be called immediately following the startEntity call.
     * <p>
     * <strong>Note:</strong> This method will never be called for the
     * document entity; it is only called for external general entities
     * referenced in document content.
     * <p>
     * <strong>Note:</strong> This method is not called for entity references
     * appearing as part of attribute values.
     *
     * @param version  The XML version, or null if not specified.
     * @param encoding The IANA encoding name of the entity.
     * @param augs       Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void textDecl (String version, String encoding, Augmentations augs) throws XNIException {
        if (fInDTD){
            return;
        }
        if (!fDeferNodeExpansion) {
            if (fCurrentEntityDecl != null && !fFilterReject) {
                fCurrentEntityDecl.setXmlEncoding (encoding);
                if (version != null)
                    fCurrentEntityDecl.setXmlVersion (version);
            }
        }
        else {
            if (fDeferredEntityDecl !=-1) {
                fDeferredDocumentImpl.setEntityInfo (fDeferredEntityDecl, version, encoding);
            }
        }
    } 

    /**
     * A comment.
     *
     * @param text The text in the comment.
     * @param augs       Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by application to signal an error.
     */
    @SuppressWarnings("fallthrough") 
    public void comment (XMLString text, Augmentations augs) throws XNIException {
        if (fInDTD) {
            if (fInternalSubset != null && !fInDTDExternalSubset) {
                fInternalSubset.append ("<!--");
                if (text.length > 0) {
                    fInternalSubset.append (text.ch, text.offset, text.length);
                }
                fInternalSubset.append ("-->");
            }
            return;
        }
        if (!fIncludeComments || fFilterReject) {
            return;
        }
        if (!fDeferNodeExpansion) {
            Comment comment = fDocument.createComment (text.toString ());

            setCharacterData (false);
            fCurrentNode.appendChild (comment);
            if (fDOMFilter !=null && !fInEntityRef &&
            (fDOMFilter.getWhatToShow () & NodeFilter.SHOW_COMMENT)!= 0) {
                short code = fDOMFilter.acceptNode (comment);
                switch (code) {
                    case LSParserFilter.FILTER_INTERRUPT:{
                        throw Abort.INSTANCE;
                    }
                    case LSParserFilter.FILTER_REJECT:{

                    }
                    case LSParserFilter.FILTER_SKIP: {
                        fCurrentNode.removeChild (comment);
                        fFirstChunk = true;
                        return;
                    }

                    default: {
                    }
                }
            }

        }
        else {
            int comment =
            fDeferredDocumentImpl.createDeferredComment (text.toString ());
            fDeferredDocumentImpl.appendChild (fCurrentNodeIndex, comment);
        }

    } 

    /**
     * A processing instruction. Processing instructions consist of a
     * target name and, optionally, text data. The data is only meaningful
     * to the application.
     * <p>
     * Typically, a processing instruction's data will contain a series
     * of pseudo-attributes. These pseudo-attributes follow the form of
     * element attributes but are <strong>not</strong> parsed or presented
     * to the application as anything other than text. The application is
     * responsible for parsing the data.
     *
     * @param target The target.
     * @param data   The data or null if none specified.
     * @param augs       Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @SuppressWarnings("fallthrough") 
    public void processingInstruction (String target, XMLString data, Augmentations augs)
    throws XNIException {

        if (fInDTD) {
            if (fInternalSubset != null && !fInDTDExternalSubset) {
                fInternalSubset.append ("<?");
                fInternalSubset.append (target);
                if (data.length > 0) {
                    fInternalSubset.append (' ').append (data.ch, data.offset, data.length);
                }
                fInternalSubset.append ("?>");
            }
            return;
        }

        if (DEBUG_EVENTS) {
            System.out.println ("==>processingInstruction ("+target+")");
        }
        if (!fDeferNodeExpansion) {
            if (fFilterReject) {
                return;
            }
            ProcessingInstruction pi =
            fDocument.createProcessingInstruction (target, data.toString ());


            setCharacterData (false);
            fCurrentNode.appendChild (pi);
            if (fDOMFilter !=null && !fInEntityRef &&
            (fDOMFilter.getWhatToShow () & NodeFilter.SHOW_PROCESSING_INSTRUCTION)!= 0) {
                short code = fDOMFilter.acceptNode (pi);
                switch (code) {
                    case LSParserFilter.FILTER_INTERRUPT:{
                        throw Abort.INSTANCE;
                    }
                    case LSParserFilter.FILTER_REJECT:{
                    }
                    case LSParserFilter.FILTER_SKIP: {
                        fCurrentNode.removeChild (pi);
                        fFirstChunk = true;
                        return;
                    }
                    default: {
                    }
                }
            }
        }
        else {
            int pi = fDeferredDocumentImpl.
            createDeferredProcessingInstruction (target, data.toString ());
            fDeferredDocumentImpl.appendChild (fCurrentNodeIndex, pi);
        }

    } 

    /**
     * The start of the document.
     *
     * @param locator The system identifier of the entity if the entity
     *                 is external, null otherwise.
     * @param encoding The auto-detected IANA encoding name of the entity
     *                 stream. This value will be null in those situations
     *                 where the entity encoding is not auto-detected (e.g.
     *                 internal entities or a document entity that is
     *                 parsed from a java.io.Reader).
     * @param namespaceContext
     *                 The namespace context in effect at the
     *                 start of this document.
     *                 This object represents the current context.
     *                 Implementors of this class are responsible
     *                 for copying the namespace bindings from the
     *                 the current context (and its parent contexts)
     *                 if that information is important.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startDocument (XMLLocator locator, String encoding,
    NamespaceContext namespaceContext, Augmentations augs)
    throws XNIException {

        fLocator = locator;
        if (!fDeferNodeExpansion) {
            if (fDocumentClassName.equals (DEFAULT_DOCUMENT_CLASS_NAME)) {
                fDocument = new DocumentImpl ();
                fDocumentImpl = (CoreDocumentImpl)fDocument;
                fDocumentImpl.setStrictErrorChecking (false);
                fDocumentImpl.setInputEncoding (encoding);
                fDocumentImpl.setDocumentURI (locator.getExpandedSystemId ());
            }
            else if (fDocumentClassName.equals (PSVI_DOCUMENT_CLASS_NAME)) {
                fDocument = new PSVIDocumentImpl();
                fDocumentImpl = (CoreDocumentImpl)fDocument;
                fStorePSVI = true;
                fDocumentImpl.setStrictErrorChecking (false);
                fDocumentImpl.setInputEncoding (encoding);
                fDocumentImpl.setDocumentURI (locator.getExpandedSystemId ());
            }
            else {
                try {
                    Class<?> documentClass = ObjectFactory.findProviderClass (fDocumentClassName, true);
                    fDocument = (Document)documentClass.getConstructor().newInstance();

                    Class<?> defaultDocClass =
                    ObjectFactory.findProviderClass (CORE_DOCUMENT_CLASS_NAME, true);
                    if (defaultDocClass.isAssignableFrom (documentClass)) {
                        fDocumentImpl = (CoreDocumentImpl)fDocument;

                        Class<?> psviDocClass = ObjectFactory.findProviderClass (PSVI_DOCUMENT_CLASS_NAME, true);
                        if (psviDocClass.isAssignableFrom (documentClass)) {
                            fStorePSVI = true;
                        }

                        fDocumentImpl.setStrictErrorChecking (false);
                        fDocumentImpl.setInputEncoding (encoding);
                        if (locator != null) {
                            fDocumentImpl.setDocumentURI (locator.getExpandedSystemId ());
                        }
                    }
                }
                catch (ClassNotFoundException e) {
                }
                catch (Exception e) {
                    throw new RuntimeException (
                        DOMMessageFormatter.formatMessage(
                        DOMMessageFormatter.DOM_DOMAIN,
                        "CannotCreateDocumentClass",
                        new Object [] {fDocumentClassName}));
                }
            }
            fCurrentNode = fDocument;
        }
        else {
            fDeferredDocumentImpl = new DeferredDocumentImpl (fNamespaceAware);
            fDocument = fDeferredDocumentImpl;
            fDocumentIndex = fDeferredDocumentImpl.createDeferredDocument ();

            fDeferredDocumentImpl.setInputEncoding (encoding);
            fDeferredDocumentImpl.setDocumentURI (locator.getExpandedSystemId ());
            fCurrentNodeIndex = fDocumentIndex;

        }

    } 

    /**
     * Notifies of the presence of an XMLDecl line in the document. If
     * present, this method will be called immediately following the
     * startDocument call.
     *
     * @param version    The XML version.
     * @param encoding   The IANA encoding name of the document, or null if
     *                   not specified.
     * @param standalone The standalone value, or null if not specified.
     * @param augs       Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void xmlDecl (String version, String encoding, String standalone,
    Augmentations augs)
    throws XNIException {
        if (!fDeferNodeExpansion) {
            if (fDocumentImpl != null) {
                if (version != null)
                    fDocumentImpl.setXmlVersion (version);
                fDocumentImpl.setXmlEncoding (encoding);
                fDocumentImpl.setXmlStandalone ("yes".equals (standalone));
            }
        }
        else {
            if (version != null)
                fDeferredDocumentImpl.setXmlVersion (version);
            fDeferredDocumentImpl.setXmlEncoding (encoding);
            fDeferredDocumentImpl.setXmlStandalone ("yes".equals (standalone));
        }
    } 

    /**
     * Notifies of the presence of the DOCTYPE line in the document.
     *
     * @param rootElement The name of the root element.
     * @param publicId    The public identifier if an external DTD or null
     *                    if the external DTD is specified using SYSTEM.
     * @param systemId    The system identifier if an external DTD, null
     *                    otherwise.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void doctypeDecl (String rootElement,
    String publicId, String systemId, Augmentations augs)
    throws XNIException {

        if (!fDeferNodeExpansion) {
            if (fDocumentImpl != null) {
                fDocumentType = fDocumentImpl.createDocumentType (
                rootElement, publicId, systemId);
                fCurrentNode.appendChild (fDocumentType);
            }
        }
        else {
            fDocumentTypeIndex = fDeferredDocumentImpl.
            createDeferredDocumentType (rootElement, publicId, systemId);
            fDeferredDocumentImpl.appendChild (fCurrentNodeIndex, fDocumentTypeIndex);
        }

    } 

    /**
     * The start of an element. If the document specifies the start element
     * by using an empty tag, then the startElement method will immediately
     * be followed by the endElement method, with no intervening methods.
     *
     * @param element    The name of the element.
     * @param attributes The element attributes.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startElement (QName element, XMLAttributes attributes, Augmentations augs)
    throws XNIException {
        if (DEBUG_EVENTS) {
            System.out.println ("==>startElement ("+element.rawname+")");
        }
        if (!fDeferNodeExpansion) {
            if (fFilterReject) {
                ++fRejectedElementDepth;
                return;
            }
            Element el = createElementNode (element);
            int attrCount = attributes.getLength ();
            boolean seenSchemaDefault = false;
            for (int i = 0; i < attrCount; i++) {
                attributes.getName (i, fAttrQName);
                Attr attr = createAttrNode (fAttrQName);

                String attrValue = attributes.getValue (i);

                AttributePSVI attrPSVI =(AttributePSVI) attributes.getAugmentations (i).getItem (Constants.ATTRIBUTE_PSVI);
                if (fStorePSVI && attrPSVI != null){
                    ((PSVIAttrNSImpl) attr).setPSVI (attrPSVI);
                }

                attr.setValue (attrValue);
                boolean specified = attributes.isSpecified(i);
                if (!specified && (seenSchemaDefault || (fAttrQName.uri != null &&
                    fAttrQName.uri != NamespaceContext.XMLNS_URI && fAttrQName.prefix == null))) {
                    el.setAttributeNodeNS(attr);
                    seenSchemaDefault = true;
                }
                else {
                    el.setAttributeNode(attr);
                }
                if (fDocumentImpl != null) {
                    AttrImpl attrImpl = (AttrImpl) attr;
                    Object type = null;
                    boolean id = false;

                    if (attrPSVI != null && fNamespaceAware) {
                        type = attrPSVI.getMemberTypeDefinition ();
                        if (type == null) {
                            type = attrPSVI.getTypeDefinition ();
                            if (type != null) {
                                id = ((XSSimpleType) type).isIDType ();
                                attrImpl.setType (type);
                            }
                        }
                        else {
                            id = ((XSSimpleType) type).isIDType ();
                            attrImpl.setType (type);
                        }
                    }
                    else {
                        boolean isDeclared = Boolean.TRUE.equals (attributes.getAugmentations (i).getItem (Constants.ATTRIBUTE_DECLARED));
                        if (isDeclared) {
                            type = attributes.getType (i);
                            id = "ID".equals (type);
                        }
                        attrImpl.setType (type);
                    }

                    if (id) {
                        ((ElementImpl) el).setIdAttributeNode (attr, true);
                    }

                    attrImpl.setSpecified (specified);
                }
            }
            setCharacterData (false);

            if (augs != null) {
                ElementPSVI elementPSVI = (ElementPSVI)augs.getItem (Constants.ELEMENT_PSVI);
                if (elementPSVI != null && fNamespaceAware) {
                    XSTypeDefinition type = elementPSVI.getMemberTypeDefinition ();
                    if (type == null) {
                        type = elementPSVI.getTypeDefinition ();
                    }
                    ((ElementNSImpl)el).setType (type);
                }
            }


            if (fDOMFilter != null && !fInEntityRef) {
                if (fRoot == null) {
                    fRoot = el;
                } else {
                    short code = fDOMFilter.startElement(el);
                    switch (code) {
                        case LSParserFilter.FILTER_INTERRUPT :
                            {
                                throw Abort.INSTANCE;
                            }
                        case LSParserFilter.FILTER_REJECT :
                            {
                                fFilterReject = true;
                                fRejectedElementDepth = 0;
                                return;
                            }
                        case LSParserFilter.FILTER_SKIP :
                            {
                                fFirstChunk = true;
                                fSkippedElemStack.push(Boolean.TRUE);
                                return;
                            }
                        default :
                            {
                                if (!fSkippedElemStack.isEmpty()) {
                                    fSkippedElemStack.push(Boolean.FALSE);
                                }
                            }
                    }
                }
            }
            fCurrentNode.appendChild (el);
            fCurrentNode = el;
        }
        else {
            int el = fDeferredDocumentImpl.createDeferredElement (fNamespaceAware ?
                    element.uri : null, element.rawname);
            Object type = null;
            int attrCount = attributes.getLength ();
            for (int i = attrCount - 1; i >= 0; --i) {

                AttributePSVI attrPSVI = (AttributePSVI)attributes.getAugmentations (i).getItem (Constants.ATTRIBUTE_PSVI);
                boolean id = false;

                if (attrPSVI != null && fNamespaceAware) {
                    type = attrPSVI.getMemberTypeDefinition ();
                    if (type == null) {
                        type = attrPSVI.getTypeDefinition ();
                        if (type != null){
                            id = ((XSSimpleType) type).isIDType ();
                        }
                    }
                    else {
                        id = ((XSSimpleType) type).isIDType ();
                    }
                }
                else {
                    boolean isDeclared = Boolean.TRUE.equals (attributes.getAugmentations (i).getItem (Constants.ATTRIBUTE_DECLARED));
                    if (isDeclared) {
                        type = attributes.getType (i);
                        id = "ID".equals (type);
                    }
                }

                fDeferredDocumentImpl.setDeferredAttribute (
                el,
                attributes.getQName (i),
                attributes.getURI (i),
                attributes.getValue (i),
                attributes.isSpecified (i),
                id,
                type);
            }

            fDeferredDocumentImpl.appendChild (fCurrentNodeIndex, el);
            fCurrentNodeIndex = el;
        }
    } 


    /**
     * An empty element.
     *
     * @param element    The name of the element.
     * @param attributes The element attributes.
     * @param augs   Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void emptyElement (QName element, XMLAttributes attributes, Augmentations augs)
    throws XNIException {

        startElement (element, attributes, augs);
        endElement (element, augs);

    } 

    /**
     * Character content.
     *
     * @param text The content.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void characters (XMLString text, Augmentations augs) throws XNIException {

        if (DEBUG_EVENTS) {
            System.out.println ("==>characters(): "+text.toString ());
        }

        if (!fDeferNodeExpansion) {

            if (fFilterReject) {
                return;
            }
            if (fInCDATASection && fCreateCDATANodes) {
                if (fCurrentCDATASection == null) {
                    fCurrentCDATASection =
                    fDocument.createCDATASection (text.toString ());
                    fCurrentNode.appendChild (fCurrentCDATASection);
                    fCurrentNode = fCurrentCDATASection;
                }
                else {
                    fCurrentCDATASection.appendData (text.toString ());
                }
            }
            else if (!fInDTD) {
                if (text.length == 0) {
                    return;
                }

                Node child = fCurrentNode.getLastChild ();
                if (child != null && child.getNodeType () == Node.TEXT_NODE) {
                    if (fFirstChunk) {
                        if (fDocumentImpl != null) {
                            fStringBuilder.append (((TextImpl)child).removeData ());
                        } else {
                            fStringBuilder.append (((Text)child).getData ());
                            ((Text)child).setNodeValue (null);
                        }
                        fFirstChunk = false;
                    }
                    if (text.length > 0) {
                        fStringBuilder.append (text.ch, text.offset, text.length);
                    }
                }
                else {
                    fFirstChunk = true;
                    Text textNode = fDocument.createTextNode (text.toString());
                    fCurrentNode.appendChild (textNode);
                }

            }
        }
        else {
            if (fInCDATASection && fCreateCDATANodes) {
                if (fCurrentCDATASectionIndex == -1) {
                    int cs = fDeferredDocumentImpl.
                    createDeferredCDATASection (text.toString ());

                    fDeferredDocumentImpl.appendChild (fCurrentNodeIndex, cs);
                    fCurrentCDATASectionIndex = cs;
                    fCurrentNodeIndex = cs;
                }
                else {
                    int txt = fDeferredDocumentImpl.
                    createDeferredTextNode (text.toString (), false);
                    fDeferredDocumentImpl.appendChild (fCurrentNodeIndex, txt);
                }
            } else if (!fInDTD) {
                if (text.length == 0) {
                    return;
                }

                String value = text.toString ();
                int txt = fDeferredDocumentImpl.
                createDeferredTextNode (value, false);
                fDeferredDocumentImpl.appendChild (fCurrentNodeIndex, txt);

            }
        }
    } 

    /**
     * Ignorable whitespace. For this method to be called, the document
     * source must have some way of determining that the text containing
     * only whitespace characters should be considered ignorable. For
     * example, the validator can determine if a length of whitespace
     * characters in the document are ignorable based on the element
     * content model.
     *
     * @param text The ignorable whitespace.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void ignorableWhitespace (XMLString text, Augmentations augs) throws XNIException {

        if (!fIncludeIgnorableWhitespace || fFilterReject) {
            return;
        }
        if (!fDeferNodeExpansion) {
            Node child = fCurrentNode.getLastChild ();
            if (child != null && child.getNodeType () == Node.TEXT_NODE) {
                Text textNode = (Text)child;
                textNode.appendData (text.toString ());
            }
            else {
                Text textNode = fDocument.createTextNode (text.toString ());
                if (fDocumentImpl != null) {
                    TextImpl textNodeImpl = (TextImpl)textNode;
                    textNodeImpl.setIgnorableWhitespace (true);
                }
                fCurrentNode.appendChild (textNode);
            }
        }
        else {
            int txt = fDeferredDocumentImpl.
            createDeferredTextNode (text.toString (), true);
            fDeferredDocumentImpl.appendChild (fCurrentNodeIndex, txt);
        }

    } 

    /**
     * The end of an element.
     *
     * @param element The name of the element.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endElement (QName element, Augmentations augs) throws XNIException {
        if (DEBUG_EVENTS) {
            System.out.println ("==>endElement ("+element.rawname+")");
        }
        if (!fDeferNodeExpansion) {

            if (augs != null && fDocumentImpl != null && (fNamespaceAware || fStorePSVI)) {
                ElementPSVI elementPSVI = (ElementPSVI) augs.getItem(Constants.ELEMENT_PSVI);
                if (elementPSVI != null) {
                    if (fNamespaceAware) {
                        XSTypeDefinition type = elementPSVI.getMemberTypeDefinition();
                        if (type == null) {
                            type = elementPSVI.getTypeDefinition();
                        }
                        ((ElementNSImpl)fCurrentNode).setType(type);
                    }
                    if (fStorePSVI) {
                        ((PSVIElementNSImpl)fCurrentNode).setPSVI (elementPSVI);
                    }
                }
            }

            if (fDOMFilter != null) {
                if (fFilterReject) {
                    if (fRejectedElementDepth-- == 0) {
                        fFilterReject = false;
                    }
                    return;
                }
                if (!fSkippedElemStack.isEmpty()) {
                    if (fSkippedElemStack.pop() == Boolean.TRUE) {
                        return;
                    }
                }
                setCharacterData (false);
                if ((fCurrentNode != fRoot) && !fInEntityRef && (fDOMFilter.getWhatToShow () & NodeFilter.SHOW_ELEMENT)!=0) {
                    short code = fDOMFilter.acceptNode (fCurrentNode);
                    switch (code) {
                        case LSParserFilter.FILTER_INTERRUPT:{
                            throw Abort.INSTANCE;
                        }
                        case LSParserFilter.FILTER_REJECT:{
                            Node parent = fCurrentNode.getParentNode ();
                            parent.removeChild (fCurrentNode);
                            fCurrentNode = parent;
                            return;
                        }
                        case LSParserFilter.FILTER_SKIP: {
                            fFirstChunk = true;

                            Node parent = fCurrentNode.getParentNode ();
                            NodeList ls = fCurrentNode.getChildNodes ();
                            int length = ls.getLength ();

                            for (int i=0;i<length;i++) {
                                parent.appendChild (ls.item (0));
                            }
                            parent.removeChild (fCurrentNode);
                            fCurrentNode = parent;

                            return;
                        }

                        default: { }
                    }
                }
                fCurrentNode = fCurrentNode.getParentNode ();

            } 
            else {
                setCharacterData (false);
                fCurrentNode = fCurrentNode.getParentNode ();
            }

        }
        else {
            if (augs != null) {
                ElementPSVI elementPSVI = (ElementPSVI) augs.getItem(Constants.ELEMENT_PSVI);
                if (elementPSVI != null) {
                    XSTypeDefinition type = elementPSVI.getMemberTypeDefinition();
                    if (type == null) {
                        type = elementPSVI.getTypeDefinition();
                    }
                    fDeferredDocumentImpl.setTypeInfo(fCurrentNodeIndex, type);
                }
            }
            fCurrentNodeIndex =
                fDeferredDocumentImpl.getParentNode (fCurrentNodeIndex, false);
        }


    } 


    /**
     * The start of a CDATA section.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startCDATA (Augmentations augs) throws XNIException {

        fInCDATASection = true;
        if (!fDeferNodeExpansion) {
            if (fFilterReject) {
                return;
            }
            if (fCreateCDATANodes) {
                setCharacterData (false);
            }
        }
    } 

    /**
     * The end of a CDATA section.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @SuppressWarnings("fallthrough") 
    public void endCDATA (Augmentations augs) throws XNIException {

        fInCDATASection = false;
        if (!fDeferNodeExpansion) {

            if (fFilterReject) {
                return;
            }

            if (fCurrentCDATASection !=null) {

                if (fDOMFilter !=null && !fInEntityRef &&
                (fDOMFilter.getWhatToShow () & NodeFilter.SHOW_CDATA_SECTION)!= 0) {
                    short code = fDOMFilter.acceptNode (fCurrentCDATASection);
                    switch (code) {
                        case LSParserFilter.FILTER_INTERRUPT:{
                            throw Abort.INSTANCE;
                        }
                        case LSParserFilter.FILTER_REJECT:{
                        }
                        case LSParserFilter.FILTER_SKIP: {
                            Node parent = fCurrentNode.getParentNode ();
                            parent.removeChild (fCurrentCDATASection);
                            fCurrentNode = parent;
                            return;
                        }

                        default: {
                        }
                    }
                }

                fCurrentNode = fCurrentNode.getParentNode ();
                fCurrentCDATASection = null;
            }
        }
        else {
            if (fCurrentCDATASectionIndex !=-1) {
                fCurrentNodeIndex =
                fDeferredDocumentImpl.getParentNode (fCurrentNodeIndex, false);
                fCurrentCDATASectionIndex = -1;
            }
        }

    } 

    /**
     * The end of the document.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endDocument (Augmentations augs) throws XNIException {

        if (!fDeferNodeExpansion) {
            if (fDocumentImpl != null) {
                if (fLocator != null) {
                    if (fLocator.getEncoding() != null)
                        fDocumentImpl.setInputEncoding (fLocator.getEncoding());
                }
                fDocumentImpl.setStrictErrorChecking (true);
            }
            fCurrentNode = null;
        }
        else {
            if (fLocator != null) {
                if (fLocator.getEncoding() != null)
                    fDeferredDocumentImpl.setInputEncoding (fLocator.getEncoding());
            }
            fCurrentNodeIndex = -1;
        }

    } 

    /**
     * This method notifies the end of a general entity.
     * <p>
     * <strong>Note:</strong> This method is not called for entity references
     * appearing as part of attribute values.
     *
     * @param name   The name of the entity.
     * @param augs   Additional information that may include infoset augmentations
     *
     * @exception XNIException
     *                   Thrown by handler to signal an error.
     */
    public void endGeneralEntity (String name, Augmentations augs) throws XNIException {
        if (DEBUG_EVENTS) {
            System.out.println ("==>endGeneralEntity: ("+name+")");
        }
        if (!fDeferNodeExpansion) {

            if (fFilterReject) {
                return;
            }
            setCharacterData (true);

            if (fDocumentType != null) {
                NamedNodeMap entities = fDocumentType.getEntities ();
                fCurrentEntityDecl = (EntityImpl) entities.getNamedItem (name);
                if (fCurrentEntityDecl != null) {
                    if (fCurrentEntityDecl != null && fCurrentEntityDecl.getFirstChild () == null) {
                        fCurrentEntityDecl.setReadOnly (false, true);
                        Node child = fCurrentNode.getFirstChild ();
                        while (child != null) {
                            Node copy = child.cloneNode (true);
                            fCurrentEntityDecl.appendChild (copy);
                            child = child.getNextSibling ();
                        }
                        fCurrentEntityDecl.setReadOnly (true, true);

                    }
                    fCurrentEntityDecl = null;
                }

            }
            fInEntityRef = false;
            boolean removeEntityRef = false;
            if (fCreateEntityRefNodes) {
                if (fDocumentImpl != null) {
                    ((NodeImpl)fCurrentNode).setReadOnly (true, true);
                }

                if (fDOMFilter !=null &&
                (fDOMFilter.getWhatToShow () & NodeFilter.SHOW_ENTITY_REFERENCE)!= 0) {
                    short code = fDOMFilter.acceptNode (fCurrentNode);
                    switch (code) {
                        case LSParserFilter.FILTER_INTERRUPT:{
                            throw Abort.INSTANCE;
                        }
                        case LSParserFilter.FILTER_REJECT:{
                            Node parent = fCurrentNode.getParentNode ();
                            parent.removeChild (fCurrentNode);
                            fCurrentNode = parent;
                            return;

                        }
                        case LSParserFilter.FILTER_SKIP: {
                            fFirstChunk = true;
                            removeEntityRef = true;
                            break;
                        }

                        default: {
                            fCurrentNode = fCurrentNode.getParentNode ();
                        }
                    }
                } else {
                    fCurrentNode = fCurrentNode.getParentNode ();
                }
            }

            if (!fCreateEntityRefNodes || removeEntityRef) {
                NodeList children = fCurrentNode.getChildNodes ();
                Node parent = fCurrentNode.getParentNode ();
                int length = children.getLength ();
                if (length > 0) {

                    Node node = fCurrentNode.getPreviousSibling ();
                    Node child = children.item (0);
                    if (node != null && node.getNodeType () == Node.TEXT_NODE &&
                    child.getNodeType () == Node.TEXT_NODE) {
                        ((Text)node).appendData (child.getNodeValue ());
                        fCurrentNode.removeChild (child);

                    } else {
                        node = parent.insertBefore (child, fCurrentNode);
                        handleBaseURI (node);
                    }

                    for (int i=1;i <length;i++) {
                        node = parent.insertBefore (children.item (0), fCurrentNode);
                        handleBaseURI (node);
                    }
                } 
                parent.removeChild (fCurrentNode);
                fCurrentNode = parent;
            }
        }
        else {

            if (fDocumentTypeIndex != -1) {
                int node = fDeferredDocumentImpl.getLastChild (fDocumentTypeIndex, false);
                while (node != -1) {
                    short nodeType = fDeferredDocumentImpl.getNodeType (node, false);
                    if (nodeType == Node.ENTITY_NODE) {
                        String nodeName =
                        fDeferredDocumentImpl.getNodeName (node, false);
                        if (nodeName.equals (name)) {
                            fDeferredEntityDecl = node;
                            break;
                        }
                    }
                    node = fDeferredDocumentImpl.getRealPrevSibling (node, false);
                }
            }

            if (fDeferredEntityDecl != -1 &&
            fDeferredDocumentImpl.getLastChild (fDeferredEntityDecl, false) == -1) {
                int prevIndex = -1;
                int childIndex = fDeferredDocumentImpl.getLastChild (fCurrentNodeIndex, false);
                while (childIndex != -1) {
                    int cloneIndex = fDeferredDocumentImpl.cloneNode (childIndex, true);
                    fDeferredDocumentImpl.insertBefore (fDeferredEntityDecl, cloneIndex, prevIndex);
                    prevIndex = cloneIndex;
                    childIndex = fDeferredDocumentImpl.getRealPrevSibling (childIndex, false);
                }
            }
            if (fCreateEntityRefNodes) {
                fCurrentNodeIndex =
                fDeferredDocumentImpl.getParentNode (fCurrentNodeIndex,
                false);
            } else { 

                int childIndex = fDeferredDocumentImpl.getLastChild (fCurrentNodeIndex, false);
                int parentIndex =
                fDeferredDocumentImpl.getParentNode (fCurrentNodeIndex,
                false);

                int prevIndex = fCurrentNodeIndex;
                int lastChild = childIndex;
                int sibling = -1;
                while (childIndex != -1) {
                    handleBaseURI (childIndex);
                    sibling = fDeferredDocumentImpl.getRealPrevSibling (childIndex, false);
                    fDeferredDocumentImpl.insertBefore (parentIndex, childIndex, prevIndex);
                    prevIndex = childIndex;
                    childIndex = sibling;
                }
                if(lastChild != -1)
                    fDeferredDocumentImpl.setAsLastChild (parentIndex, lastChild);
                else{
                    sibling = fDeferredDocumentImpl.getRealPrevSibling (prevIndex, false);
                    fDeferredDocumentImpl.setAsLastChild (parentIndex, sibling);
                }
                fCurrentNodeIndex = parentIndex;
            }
            fDeferredEntityDecl = -1;
        }


    } 


    /**
     * Record baseURI information for the Element (by adding xml:base attribute)
     * or for the ProcessingInstruction (by setting a baseURI field)
     * Non deferred DOM.
     *
     * @param node
     */
    protected final void handleBaseURI (Node node){
        if (fDocumentImpl != null) {

            String baseURI = null;
            short nodeType = node.getNodeType ();

            if (nodeType == Node.ELEMENT_NODE) {
                if (fNamespaceAware) {
                    if (((Element)node).getAttributeNodeNS ("http:
                        return;
                    }
                } else if (((Element)node).getAttributeNode ("xml:base") != null) {
                    return;
                }
                baseURI = ((EntityReferenceImpl)fCurrentNode).getBaseURI ();
                if (baseURI !=null && !baseURI.equals (fDocumentImpl.getDocumentURI ())) {
                    if (fNamespaceAware) {
                        ((Element)node).setAttributeNS ("http:
                    } else {
                        ((Element)node).setAttribute ("xml:base", baseURI);
                    }
                }
            }
            else if (nodeType == Node.PROCESSING_INSTRUCTION_NODE) {

                baseURI = ((EntityReferenceImpl)fCurrentNode).getBaseURI ();
                if (baseURI !=null && fErrorHandler != null) {
                    DOMErrorImpl error = new DOMErrorImpl ();
                    error.fType = "pi-base-uri-not-preserved";
                    error.fRelatedData = baseURI;
                    error.fSeverity = DOMError.SEVERITY_WARNING;
                    fErrorHandler.getErrorHandler ().handleError (error);
                }
            }
        }
    }

    /**
     *
     * Record baseURI information for the Element (by adding xml:base attribute)
     * or for the ProcessingInstruction (by setting a baseURI field)
     * Deferred DOM.
     *
     * @param node
     */
    protected final void handleBaseURI (int node){
        short nodeType = fDeferredDocumentImpl.getNodeType (node, false);

        if (nodeType == Node.ELEMENT_NODE) {
            String baseURI = fDeferredDocumentImpl.getNodeValueString (fCurrentNodeIndex, false);
            if (baseURI == null) {
                baseURI = fDeferredDocumentImpl.getDeferredEntityBaseURI (fDeferredEntityDecl);
            }
            if (baseURI !=null && !baseURI.equals (fDeferredDocumentImpl.getDocumentURI ())) {
                fDeferredDocumentImpl.setDeferredAttribute (node,
                "xml:base",
                "http:
                baseURI,
                true,
                false,
                null);
            }
        }
        else if (nodeType == Node.PROCESSING_INSTRUCTION_NODE) {


            String baseURI = fDeferredDocumentImpl.getNodeValueString (fCurrentNodeIndex, false);

            if (baseURI == null) {
                baseURI = fDeferredDocumentImpl.getDeferredEntityBaseURI (fDeferredEntityDecl);
            }

            if (baseURI != null && fErrorHandler != null) {
                DOMErrorImpl error = new DOMErrorImpl ();
                error.fType = "pi-base-uri-not-preserved";
                error.fRelatedData = baseURI;
                error.fSeverity = DOMError.SEVERITY_WARNING;
                fErrorHandler.getErrorHandler ().handleError (error);
            }
        }
    }



    /**
     * The start of the DTD.
     *
     * @param locator  The document locator, or null if the document
     *                 location cannot be reported during the parsing of
     *                 the document DTD. However, it is <em>strongly</em>
     *                 recommended that a locator be supplied that can
     *                 at least report the base system identifier of the
     *                 DTD.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startDTD (XMLLocator locator, Augmentations augs) throws XNIException {
        if (DEBUG_EVENTS) {
            System.out.println ("==>startDTD");
            if (DEBUG_BASEURI) {
                System.out.println ("   expandedSystemId: "+locator.getExpandedSystemId ());
                System.out.println ("   baseURI:"+ locator.getBaseSystemId ());
            }
        }

        fInDTD = true;
        if (locator != null) {
            fBaseURIStack.push (locator.getBaseSystemId ());
        }
        if (fDeferNodeExpansion || fDocumentImpl != null) {
            fInternalSubset = new StringBuilder (1024);
        }
    } 


    /**
     * The end of the DTD.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endDTD (Augmentations augs) throws XNIException {
        if (DEBUG_EVENTS) {
            System.out.println ("==>endDTD()");
        }
        fInDTD = false;
        if (!fBaseURIStack.isEmpty ()) {
            fBaseURIStack.pop ();
        }
        String internalSubset = fInternalSubset != null && fInternalSubset.length () > 0
        ? fInternalSubset.toString () : null;
        if (fDeferNodeExpansion) {
            if (internalSubset != null) {
                fDeferredDocumentImpl.setInternalSubset (fDocumentTypeIndex, internalSubset);
            }
        }
        else if (fDocumentImpl != null) {
            if (internalSubset != null) {
                ((DocumentTypeImpl)fDocumentType).setInternalSubset (internalSubset);
            }
        }
    } 

    /**
     * The start of a conditional section.
     *
     * @param type The type of the conditional section. This value will
     *             either be CONDITIONAL_INCLUDE or CONDITIONAL_IGNORE.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     *
     * @see #CONDITIONAL_INCLUDE
     * @see #CONDITIONAL_IGNORE
     */
    public void startConditional (short type, Augmentations augs) throws XNIException  {
    } 

    /**
     * The end of a conditional section.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endConditional (Augmentations augs) throws XNIException {
    } 


    /**
     * The start of the DTD external subset.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startExternalSubset (XMLResourceIdentifier identifier,
    Augmentations augs) throws XNIException {
        if (DEBUG_EVENTS) {
            System.out.println ("==>startExternalSubset");
            if (DEBUG_BASEURI) {
                System.out.println ("   expandedSystemId: "+identifier.getExpandedSystemId ());
                System.out.println ("   baseURI:"+ identifier.getBaseSystemId ());
            }
        }
        fBaseURIStack.push (identifier.getBaseSystemId ());
        fInDTDExternalSubset = true;
    } 

    /**
     * The end of the DTD external subset.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endExternalSubset (Augmentations augs) throws XNIException {
        fInDTDExternalSubset = false;
        fBaseURIStack.pop ();
    } 

    /**
     * An internal entity declaration.
     *
     * @param name The name of the entity. Parameter entity names start with
     *             '%', whereas the name of a general entity is just the
     *             entity name.
     * @param text The value of the entity.
     * @param nonNormalizedText The non-normalized value of the entity. This
     *             value contains the same sequence of characters that was in
     *             the internal entity declaration, without any entity
     *             references expanded.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void internalEntityDecl (String name, XMLString text,
    XMLString nonNormalizedText,
    Augmentations augs) throws XNIException {

        if (DEBUG_EVENTS) {
            System.out.println ("==>internalEntityDecl: "+name);
            if (DEBUG_BASEURI) {
                System.out.println ("   baseURI:"+ fBaseURIStack.peek ());
            }
        }
        if (fInternalSubset != null && !fInDTDExternalSubset) {
            fInternalSubset.append ("<!ENTITY ");
            if (name.startsWith ("%")) {
                fInternalSubset.append ("% ");
                fInternalSubset.append (name.substring (1));
            }
            else {
                fInternalSubset.append (name);
            }
            fInternalSubset.append (' ');
            String value = nonNormalizedText.toString ();
            boolean singleQuote = value.indexOf ('\'') == -1;
            fInternalSubset.append (singleQuote ? '\'' : '"');
            fInternalSubset.append (value);
            fInternalSubset.append (singleQuote ? '\'' : '"');
            fInternalSubset.append (">\n");
        }


        if(name.startsWith ("%"))
            return;
        if (fDocumentType != null) {
            NamedNodeMap entities = fDocumentType.getEntities ();
            EntityImpl entity = (EntityImpl)entities.getNamedItem (name);
            if (entity == null) {
                entity = (EntityImpl)fDocumentImpl.createEntity (name);
                entity.setBaseURI (fBaseURIStack.peek ());
                entities.setNamedItem (entity);
            }
        }

        if (fDocumentTypeIndex != -1) {
            boolean found = false;
            int node = fDeferredDocumentImpl.getLastChild (fDocumentTypeIndex, false);
            while (node != -1) {
                short nodeType = fDeferredDocumentImpl.getNodeType (node, false);
                if (nodeType == Node.ENTITY_NODE) {
                    String nodeName = fDeferredDocumentImpl.getNodeName (node, false);
                    if (nodeName.equals (name)) {
                        found = true;
                        break;
                    }
                }
                node = fDeferredDocumentImpl.getRealPrevSibling (node, false);
            }
            if (!found) {
                int entityIndex =
                fDeferredDocumentImpl.createDeferredEntity (name, null, null, null, fBaseURIStack.peek ());
                fDeferredDocumentImpl.appendChild (fDocumentTypeIndex, entityIndex);
            }
        }

    } 

    /**
     * An external entity declaration.
     *
     * @param name     The name of the entity. Parameter entity names start
     *                 with '%', whereas the name of a general entity is just
     *                 the entity name.
     * @param identifier    An object containing all location information
     *                      pertinent to this notation.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void externalEntityDecl (String name, XMLResourceIdentifier identifier,
    Augmentations augs) throws XNIException {


        if (DEBUG_EVENTS) {
            System.out.println ("==>externalEntityDecl: "+name);
            if (DEBUG_BASEURI) {
                System.out.println ("   expandedSystemId:"+ identifier.getExpandedSystemId ());
                System.out.println ("   baseURI:"+ identifier.getBaseSystemId ());
            }
        }
        String publicId = identifier.getPublicId ();
        String literalSystemId = identifier.getLiteralSystemId ();
        if (fInternalSubset != null && !fInDTDExternalSubset) {
            fInternalSubset.append ("<!ENTITY ");
            if (name.startsWith ("%")) {
                fInternalSubset.append ("% ");
                fInternalSubset.append (name.substring (1));
            }
            else {
                fInternalSubset.append (name);
            }
            fInternalSubset.append (JdkXmlUtils.getDTDExternalDecl(publicId, literalSystemId));
            fInternalSubset.append (">\n");
        }


        if(name.startsWith ("%"))
            return;
        if (fDocumentType != null) {
            NamedNodeMap entities = fDocumentType.getEntities ();
            EntityImpl entity = (EntityImpl)entities.getNamedItem (name);
            if (entity == null) {
                entity = (EntityImpl)fDocumentImpl.createEntity (name);
                entity.setPublicId (publicId);
                entity.setSystemId (literalSystemId);
                entity.setBaseURI (identifier.getBaseSystemId ());
                entities.setNamedItem (entity);
            }
        }

        if (fDocumentTypeIndex != -1) {
            boolean found = false;
            int nodeIndex = fDeferredDocumentImpl.getLastChild (fDocumentTypeIndex, false);
            while (nodeIndex != -1) {
                short nodeType = fDeferredDocumentImpl.getNodeType (nodeIndex, false);
                if (nodeType == Node.ENTITY_NODE) {
                    String nodeName = fDeferredDocumentImpl.getNodeName (nodeIndex, false);
                    if (nodeName.equals (name)) {
                        found = true;
                        break;
                    }
                }
                nodeIndex = fDeferredDocumentImpl.getRealPrevSibling (nodeIndex, false);
            }
            if (!found) {
                int entityIndex = fDeferredDocumentImpl.createDeferredEntity (
                name, publicId, literalSystemId, null, identifier.getBaseSystemId ());
                fDeferredDocumentImpl.appendChild (fDocumentTypeIndex, entityIndex);
            }
        }

    } 


    /**
     * This method notifies of the start of a parameter entity. The parameter
     * entity name start with a '%' character.
     *
     * @param name     The name of the parameter entity.
     * @param identifier The resource identifier.
     * @param encoding The auto-detected IANA encoding name of the entity
     *                 stream. This value will be null in those situations
     *                 where the entity encoding is not auto-detected (e.g.
     *                 internal parameter entities).
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startParameterEntity (String name,
    XMLResourceIdentifier identifier,
    String encoding,
    Augmentations augs) throws XNIException {
        if (DEBUG_EVENTS) {
            System.out.println ("==>startParameterEntity: "+name);
            if (DEBUG_BASEURI) {
                System.out.println ("   expandedSystemId: "+identifier.getExpandedSystemId ());
                System.out.println ("   baseURI:"+ identifier.getBaseSystemId ());
            }
        }
        if (augs != null && fInternalSubset != null &&
            !fInDTDExternalSubset &&
            Boolean.TRUE.equals(augs.getItem(Constants.ENTITY_SKIPPED))) {
            fInternalSubset.append(name).append(";\n");
        }
        fBaseURIStack.push (identifier.getExpandedSystemId ());
    }


    /**
     * This method notifies the end of a parameter entity. Parameter entity
     * names begin with a '%' character.
     *
     * @param name The name of the parameter entity.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endParameterEntity (String name, Augmentations augs) throws XNIException {

        if (DEBUG_EVENTS) {
            System.out.println ("==>endParameterEntity: "+name);
        }
        fBaseURIStack.pop ();
    }

    /**
     * An unparsed entity declaration.
     *
     * @param name     The name of the entity.
     * @param identifier    An object containing all location information
     *                      pertinent to this entity.
     * @param notation The name of the notation.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void unparsedEntityDecl (String name, XMLResourceIdentifier identifier,
    String notation, Augmentations augs)
    throws XNIException {

        if (DEBUG_EVENTS) {
            System.out.println ("==>unparsedEntityDecl: "+name);
            if (DEBUG_BASEURI) {
                System.out.println ("   expandedSystemId:"+ identifier.getExpandedSystemId ());
                System.out.println ("   baseURI:"+ identifier.getBaseSystemId ());
            }
        }
        String publicId = identifier.getPublicId ();
        String literalSystemId = identifier.getLiteralSystemId ();
        if (fInternalSubset != null && !fInDTDExternalSubset) {
            fInternalSubset.append ("<!ENTITY ");
            fInternalSubset.append (name);
            fInternalSubset.append (JdkXmlUtils.getDTDExternalDecl(publicId, literalSystemId));
            fInternalSubset.append (" NDATA ");
            fInternalSubset.append (notation);
            fInternalSubset.append (">\n");
        }


        if (fDocumentType != null) {
            NamedNodeMap entities = fDocumentType.getEntities ();
            EntityImpl entity = (EntityImpl)entities.getNamedItem (name);
            if (entity == null) {
                entity = (EntityImpl)fDocumentImpl.createEntity (name);
                entity.setPublicId (publicId);
                entity.setSystemId (literalSystemId);
                entity.setNotationName (notation);
                entity.setBaseURI (identifier.getBaseSystemId ());
                entities.setNamedItem (entity);
            }
        }

        if (fDocumentTypeIndex != -1) {
            boolean found = false;
            int nodeIndex = fDeferredDocumentImpl.getLastChild (fDocumentTypeIndex, false);
            while (nodeIndex != -1) {
                short nodeType = fDeferredDocumentImpl.getNodeType (nodeIndex, false);
                if (nodeType == Node.ENTITY_NODE) {
                    String nodeName = fDeferredDocumentImpl.getNodeName (nodeIndex, false);
                    if (nodeName.equals (name)) {
                        found = true;
                        break;
                    }
                }
                nodeIndex = fDeferredDocumentImpl.getRealPrevSibling (nodeIndex, false);
            }
            if (!found) {
                int entityIndex = fDeferredDocumentImpl.createDeferredEntity (
                name, publicId, literalSystemId, notation, identifier.getBaseSystemId ());
                fDeferredDocumentImpl.appendChild (fDocumentTypeIndex, entityIndex);
            }
        }

    } 

    /**
     * A notation declaration
     *
     * @param name     The name of the notation.
     * @param identifier    An object containing all location information
     *                      pertinent to this notation.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void notationDecl (String name, XMLResourceIdentifier identifier,
    Augmentations augs) throws XNIException {

        String publicId = identifier.getPublicId ();
        String literalSystemId = identifier.getLiteralSystemId ();
        if (fInternalSubset != null && !fInDTDExternalSubset) {
            fInternalSubset.append ("<!NOTATION ");
            fInternalSubset.append (name);
            fInternalSubset.append (JdkXmlUtils.getDTDExternalDecl(publicId, literalSystemId));
            fInternalSubset.append (">\n");
        }


        if (fDocumentImpl !=null && fDocumentType != null) {
            NamedNodeMap notations = fDocumentType.getNotations ();
            if (notations.getNamedItem (name) == null) {
                NotationImpl notation = (NotationImpl)fDocumentImpl.createNotation (name);
                notation.setPublicId (publicId);
                notation.setSystemId (literalSystemId);
                notation.setBaseURI (identifier.getBaseSystemId ());
                notations.setNamedItem (notation);
            }
        }

        if (fDocumentTypeIndex != -1) {
            boolean found = false;
            int nodeIndex = fDeferredDocumentImpl.getLastChild (fDocumentTypeIndex, false);
            while (nodeIndex != -1) {
                short nodeType = fDeferredDocumentImpl.getNodeType (nodeIndex, false);
                if (nodeType == Node.NOTATION_NODE) {
                    String nodeName = fDeferredDocumentImpl.getNodeName (nodeIndex, false);
                    if (nodeName.equals (name)) {
                        found = true;
                        break;
                    }
                }
                nodeIndex = fDeferredDocumentImpl.getPrevSibling (nodeIndex, false);
            }
            if (!found) {
                int notationIndex = fDeferredDocumentImpl.createDeferredNotation (
                name, publicId, literalSystemId, identifier.getBaseSystemId ());
                fDeferredDocumentImpl.appendChild (fDocumentTypeIndex, notationIndex);
            }
        }

    } 

    /**
     * Characters within an IGNORE conditional section.
     *
     * @param text The ignored text.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void ignoredCharacters (XMLString text, Augmentations augs) throws XNIException {
    } 


    /**
     * An element declaration.
     *
     * @param name         The name of the element.
     * @param contentModel The element content model.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void elementDecl (String name, String contentModel, Augmentations augs)
    throws XNIException {

        if (fInternalSubset != null && !fInDTDExternalSubset) {
            fInternalSubset.append ("<!ELEMENT ");
            fInternalSubset.append (name);
            fInternalSubset.append (' ');
            fInternalSubset.append (contentModel);
            fInternalSubset.append (">\n");
        }

    } 

    /**
     * An attribute declaration.
     *
     * @param elementName   The name of the element that this attribute
     *                      is associated with.
     * @param attributeName The name of the attribute.
     * @param type          The attribute type. This value will be one of
     *                      the following: "CDATA", "ENTITY", "ENTITIES",
     *                      "ENUMERATION", "ID", "IDREF", "IDREFS",
     *                      "NMTOKEN", "NMTOKENS", or "NOTATION".
     * @param enumeration   If the type has the value "ENUMERATION" or
     *                      "NOTATION", this array holds the allowed attribute
     *                      values; otherwise, this array is null.
     * @param defaultType   The attribute default type. This value will be
     *                      one of the following: "#FIXED", "#IMPLIED",
     *                      "#REQUIRED", or null.
     * @param defaultValue  The attribute default value, or null if no
     *                      default value is specified.
     * @param nonNormalizedDefaultValue  The attribute default value with no normalization
     *                      performed, or null if no default value is specified.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void attributeDecl (String elementName, String attributeName,
    String type, String[] enumeration,
    String defaultType, XMLString defaultValue,
    XMLString nonNormalizedDefaultValue, Augmentations augs) throws XNIException {

        if (fInternalSubset != null && !fInDTDExternalSubset) {
            fInternalSubset.append ("<!ATTLIST ");
            fInternalSubset.append (elementName);
            fInternalSubset.append (' ');
            fInternalSubset.append (attributeName);
            fInternalSubset.append (' ');
            if (type.equals ("ENUMERATION")) {
                fInternalSubset.append ('(');
                for (int i = 0; i < enumeration.length; i++) {
                    if (i > 0) {
                        fInternalSubset.append ('|');
                    }
                    fInternalSubset.append (enumeration[i]);
                }
                fInternalSubset.append (')');
            }
            else {
                fInternalSubset.append (type);
            }
            if (defaultType != null) {
                fInternalSubset.append (' ');
                fInternalSubset.append (defaultType);
            }
            if (defaultValue != null) {
                fInternalSubset.append (" '");
                for (int i = 0; i < defaultValue.length; i++) {
                    char c = defaultValue.ch[defaultValue.offset + i];
                    if (c == '\'') {
                        fInternalSubset.append ("&apos;");
                    }
                    else {
                        fInternalSubset.append (c);
                    }
                }
                fInternalSubset.append ('\'');
            }
            fInternalSubset.append (">\n");
        }

        if (fDeferredDocumentImpl != null) {

            if (defaultValue != null) {

                int elementDefIndex  = fDeferredDocumentImpl.lookupElementDefinition (elementName);

                if (elementDefIndex == -1) {
                    elementDefIndex = fDeferredDocumentImpl.createDeferredElementDefinition (elementName);
                    fDeferredDocumentImpl.appendChild (fDocumentTypeIndex, elementDefIndex);
                }
                boolean nsEnabled = fNamespaceAware;
                String namespaceURI = null;
                if (nsEnabled) {
                    if (attributeName.startsWith("xmlns:") ||
                        attributeName.equals("xmlns")) {
                        namespaceURI = NamespaceContext.XMLNS_URI;
                    }
                    else if (attributeName.startsWith("xml:")) {
                        namespaceURI = NamespaceContext.XML_URI;
                    }
                }
                int attrIndex = fDeferredDocumentImpl.createDeferredAttribute (
                        attributeName, namespaceURI, defaultValue.toString(), false);
                if ("ID".equals (type)) {
                    fDeferredDocumentImpl.setIdAttribute (attrIndex);
                }
                fDeferredDocumentImpl.appendChild (elementDefIndex, attrIndex);
            }

        } 

        else if (fDocumentImpl != null) {

            if (defaultValue != null) {

                NamedNodeMap elements = ((DocumentTypeImpl)fDocumentType).getElements ();
                ElementDefinitionImpl elementDef = (ElementDefinitionImpl)elements.getNamedItem (elementName);
                if (elementDef == null) {
                    elementDef = fDocumentImpl.createElementDefinition (elementName);
                    ((DocumentTypeImpl)fDocumentType).getElements ().setNamedItem (elementDef);
                }


                boolean nsEnabled = fNamespaceAware;
                AttrImpl attr;
                if (nsEnabled) {
                    String namespaceURI = null;
                    if (attributeName.startsWith("xmlns:") ||
                        attributeName.equals("xmlns")) {
                        namespaceURI = NamespaceContext.XMLNS_URI;
                    }
                    else if (attributeName.startsWith("xml:")) {
                        namespaceURI = NamespaceContext.XML_URI;
                    }
                    attr = (AttrImpl)fDocumentImpl.createAttributeNS (namespaceURI,
                    attributeName);
                }
                else {
                    attr = (AttrImpl)fDocumentImpl.createAttribute (attributeName);
                }
                attr.setValue (defaultValue.toString ());
                attr.setSpecified (false);
                attr.setIdAttribute ("ID".equals (type));

                if (nsEnabled){
                    elementDef.getAttributes ().setNamedItemNS (attr);
                }
                else {
                    elementDef.getAttributes ().setNamedItem (attr);
                }
            }

        } 

    } 


    /**
     * The start of an attribute list.
     *
     * @param elementName The name of the element that this attribute
     *                    list is associated with.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startAttlist (String elementName, Augmentations augs) throws XNIException {
    } 


    /**
     * The end of an attribute list.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endAttlist (Augmentations augs) throws XNIException {
    } 


    protected Element createElementNode (QName element) {
        Element el = null;

        if (fNamespaceAware) {
            if (fDocumentImpl != null) {
                el = fDocumentImpl.createElementNS (element.uri, element.rawname,
                element.localpart);
            }
            else {
                el = fDocument.createElementNS (element.uri, element.rawname);
            }
        }
        else {
            el = fDocument.createElement (element.rawname);
        }

        return el;
    }

    protected Attr createAttrNode (QName attrQName) {
        Attr attr = null;

        if (fNamespaceAware) {
            if (fDocumentImpl != null) {
                attr = fDocumentImpl.createAttributeNS (attrQName.uri,
                attrQName.rawname,
                attrQName.localpart);
            }
            else {
                attr = fDocument.createAttributeNS (attrQName.uri,
                attrQName.rawname);
            }
        }
        else {
            attr = fDocument.createAttribute (attrQName.rawname);
        }

        return attr;
    }

    /*
     * When the first characters() call is received, the data is stored in
     * a new Text node. If right after the first characters() we receive another chunk of data,
     * the data from the Text node, following the new characters are appended
     * to the fStringBuffer and the text node data is set to empty.
     *
     * This function is called when the state is changed and the
     * data must be appended to the current node.
     *
     * Note: if DOMFilter is set, you must make sure that if Node is skipped,
     * or removed fFistChunk must be set to true, otherwise some data can be lost.
     *
     */
    @SuppressWarnings("fallthrough") 
    protected void  setCharacterData (boolean sawChars){

        fFirstChunk = sawChars;



        Node child = fCurrentNode.getLastChild ();
        if (child != null) {
            if (fStringBuilder.length () > 0) {
                if (child.getNodeType () == Node.TEXT_NODE) {
                    if (fDocumentImpl != null) {
                        ((TextImpl)child).replaceData (fStringBuilder.toString ());
                    }
                    else {
                        ((Text)child).setData (fStringBuilder.toString ());
                    }
                }
                fStringBuilder.setLength (0);
            }

            if (fDOMFilter !=null && !fInEntityRef) {
                if ( (child.getNodeType () == Node.TEXT_NODE ) &&
                ((fDOMFilter.getWhatToShow () & NodeFilter.SHOW_TEXT)!= 0) ) {
                    short code = fDOMFilter.acceptNode (child);
                    switch (code) {
                        case LSParserFilter.FILTER_INTERRUPT:{
                            throw Abort.INSTANCE;
                        }
                        case LSParserFilter.FILTER_REJECT:{
                        }
                        case LSParserFilter.FILTER_SKIP: {
                            fCurrentNode.removeChild (child);
                            return;
                        }
                        default: {
                        }
                    }
                }
            }   

        } 
    }


    /**
     * @see org.w3c.dom.ls.LSParser#abort()
     */
    public void abort () {
        throw Abort.INSTANCE;
    }


} 
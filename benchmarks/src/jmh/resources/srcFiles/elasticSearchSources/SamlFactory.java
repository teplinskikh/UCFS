/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.idp.saml.support;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.hash.MessageDigests;
import org.elasticsearch.core.SuppressForbidden;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.X509Credential;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.StringWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import static org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport.getUnmarshallerFactory;

/**
 * Utility object for constructing new objects and values in a SAML 2.0 / OpenSAML context
 */
public class SamlFactory {

    private final XMLObjectBuilderFactory builderFactory;
    private final SecureRandom random;
    private static final Logger LOGGER = LogManager.getLogger(SamlFactory.class);

    public SamlFactory() {
        SamlInit.initialize();
        builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
        random = new SecureRandom();
    }

    public <T extends XMLObject> T object(Class<T> type, QName elementName) {
        final XMLObject obj = builderFactory.getBuilder(elementName).buildObject(elementName);
        return cast(type, elementName, obj);
    }

    public <T extends XMLObject> T object(Class<T> type, QName elementName, QName schemaType) {
        final XMLObject obj = builderFactory.getBuilder(schemaType).buildObject(elementName, schemaType);
        return cast(type, elementName, obj);
    }

    private static <T extends XMLObject> T cast(Class<T> type, QName elementName, XMLObject obj) {
        if (type.isInstance(obj)) {
            return type.cast(obj);
        } else {
            throw new IllegalArgumentException(
                "Object for element " + elementName.getLocalPart() + " is of type " + obj.getClass() + " not " + type
            );
        }
    }

    public String secureIdentifier() {
        return randomNCName(20);
    }

    private String randomNCName(int numberBytes) {
        final byte[] randomBytes = new byte[numberBytes];
        random.nextBytes(randomBytes);
        return "_".concat(MessageDigests.toHexString(randomBytes));
    }

    public <T extends XMLObject> T buildObject(Class<T> type, QName elementName) {
        final XMLObject obj = builderFactory.getBuilder(elementName).buildObject(elementName);
        if (type.isInstance(obj)) {
            return type.cast(obj);
        } else {
            throw new IllegalArgumentException(
                "Object for element " + elementName.getLocalPart() + " is of type " + obj.getClass() + " not " + type
            );
        }
    }

    public String toString(Element element, boolean pretty) {
        try {
            StringWriter writer = new StringWriter();
            print(element, writer, pretty);
            return writer.toString();
        } catch (TransformerException e) {
            return "[" + element.getNamespaceURI() + "]" + element.getLocalName();
        }
    }

    public static <T extends XMLObject> T buildXmlObject(Element element, Class<T> type) {
        try {
            UnmarshallerFactory unmarshallerFactory = getUnmarshallerFactory();
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
            if (unmarshaller == null) {
                throw new ElasticsearchSecurityException(
                    "XML element [{}] cannot be unmarshalled to SAML type [{}] (no unmarshaller)",
                    element.getTagName(),
                    type
                );
            }
            final XMLObject object = unmarshaller.unmarshall(element);
            if (type.isInstance(object)) {
                return type.cast(object);
            }
            Object[] args = new Object[] { element.getTagName(), type.getName(), object.getClass().getName() };
            throw new ElasticsearchSecurityException("SAML object [{}] is incorrect type. Expected [{}] but was [{}]", args);
        } catch (UnmarshallingException e) {
            throw new ElasticsearchSecurityException("Failed to unmarshall SAML content [{}]", e, element.getTagName());
        }
    }

    void print(Element element, Writer writer, boolean pretty) throws TransformerException {
        final Transformer serializer = getHardenedXMLTransformer();
        if (pretty) {
            serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        }
        serializer.transform(new DOMSource(element), new StreamResult(writer));
    }

    public String getXmlContent(SAMLObject object) {
        return getXmlContent(object, false);
    }

    public String getXmlContent(SAMLObject object, boolean prettyPrint) {
        try {
            return toString(XMLObjectSupport.marshall(object), prettyPrint);
        } catch (MarshallingException e) {
            LOGGER.info("Error marshalling SAMLObject ", e);
            return "_unserializable_";
        }
    }

    public static boolean elementNameMatches(Element element, String namespace, String localName) {
        return localName.equals(element.getLocalName()) && namespace.equals(element.getNamespaceURI());
    }

    public static String text(Element dom, int length) {
        return text(dom, length, 0);
    }

    protected static String text(Element dom, int prefixLength, int suffixLength) {

        final String text = dom.getTextContent().trim();
        final int totalLength = prefixLength + suffixLength;
        if (text.length() > totalLength) {
            final String prefix = Strings.cleanTruncate(text, prefixLength) + "...";
            if (suffixLength == 0) {
                return prefix;
            }
            int suffixIndex = text.length() - suffixLength;
            if (Character.isHighSurrogate(text.charAt(suffixIndex))) {
                suffixIndex++;
            }
            return prefix + text.substring(suffixIndex);
        } else {
            return text;
        }
    }

    public static String describeCredentials(Collection<? extends Credential> credentials) {
        return credentials.stream().map(c -> {
            if (c == null) {
                return "<null>";
            }
            byte[] encoded;
            if (c instanceof X509Credential x) {
                try {
                    encoded = x.getEntityCertificate().getEncoded();
                } catch (CertificateEncodingException e) {
                    encoded = c.getPublicKey().getEncoded();
                }
            } else {
                encoded = c.getPublicKey().getEncoded();
            }
            return Base64.getEncoder().encodeToString(encoded).substring(0, 64) + "...";
        }).collect(Collectors.joining(","));
    }

    public static Element toDomElement(XMLObject object) {
        try {
            return XMLObjectSupport.marshall(object);
        } catch (MarshallingException e) {
            throw new ElasticsearchSecurityException("failed to marshall SAML object to DOM element", e);
        }
    }

    @SuppressForbidden(reason = "This is the only allowed way to construct a Transformer")
    public static Transformer getHardenedXMLTransformer() throws TransformerConfigurationException {
        final TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        tfactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tfactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        tfactory.setAttribute("indent-number", 2);
        Transformer transformer = tfactory.newTransformer();
        transformer.setErrorListener(new SamlFactory.TransformerErrorListener());
        return transformer;
    }

    /**
     * Constructs a DocumentBuilder with all the necessary features for it to be secure
     *
     * @throws ParserConfigurationException if one of the features can't be set on the DocumentBuilderFactory
     */
    @SuppressForbidden(reason = "This is the only allowed way to construct a DocumentBuilder")
    public static DocumentBuilder getHardenedBuilder(String[] schemaFiles) throws ParserConfigurationException {
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setValidating(true);
        dbf.setFeature("http:
        dbf.setFeature("http:
        dbf.setFeature("http:
        dbf.setFeature("http:
        dbf.setFeature("http:
        dbf.setFeature("http:
        dbf.setIgnoringComments(true);
        dbf.setFeature("http:
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "file,jar");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");
        dbf.setFeature("http:
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setAttribute("http:
        dbf.setAttribute("http:
        dbf.setAttribute("http:
        dbf.setAttribute("http:
        DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
        documentBuilder.setErrorHandler(new SamlFactory.DocumentBuilderErrorHandler());
        return documentBuilder;
    }

    public static String getJavaAlorithmNameFromUri(String sigAlg) {
        return switch (sigAlg) {
            case "http:
            case "http:
            case "http:
            case "http:
            case "http:
            default -> throw new IllegalArgumentException("Unsupported signing algorithm identifier: " + sigAlg);
        };
    }

    private static String[] resolveSchemaFilePaths(String[] relativePaths) {

        return Arrays.stream(relativePaths).map(file -> {
            try {
                return SamlFactory.class.getResource(file).toURI().toString();
            } catch (URISyntaxException e) {
                LOGGER.warn("Error resolving schema file path", e);
                return null;
            }
        }).filter(Objects::nonNull).toArray(String[]::new);
    }

    private static class DocumentBuilderErrorHandler implements org.xml.sax.ErrorHandler {
        /**
         * Enabling schema validation with `setValidating(true)` in our
         * DocumentBuilderFactory requires that we provide our own
         * ErrorHandler implementation
         *
         * @throws SAXException If the document we attempt to parse is not valid according to the specified schema.
         */
        @Override
        public void warning(SAXParseException e) throws SAXException {
            LOGGER.debug("XML Parser error ", e);
            throw e;
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            warning(e);
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            warning(e);
        }
    }

    private static class TransformerErrorListener implements javax.xml.transform.ErrorListener {

        @Override
        public void warning(TransformerException e) throws TransformerException {
            LOGGER.debug("XML transformation error", e);
            throw e;
        }

        @Override
        public void error(TransformerException e) throws TransformerException {
            warning(e);
        }

        @Override
        public void fatalError(TransformerException e) throws TransformerException {
            warning(e);
        }
    }

}
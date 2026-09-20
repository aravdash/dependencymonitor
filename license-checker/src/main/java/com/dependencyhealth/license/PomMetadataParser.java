package com.dependencyhealth.license;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

@Component
public class PomMetadataParser {
    public record Coordinates(String group, String artifact, String version) {
        public Coordinates {
            for (String part : List.of(group, artifact, version)) {
                if (!part.matches("[A-Za-z0-9_][A-Za-z0-9_.+-]*") || part.contains("..")) {
                    throw new IllegalArgumentException("Unsupported Maven coordinate: " + part);
                }
            }
        }
        public String repositoryPath() {
            return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".pom";
        }
    }
    public record PomMetadata(List<String> licenses, Coordinates parent) {}

    public PomMetadata parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element project = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
            if (!"project".equals(project.getLocalName())) throw new IllegalArgumentException("Expected Maven project XML");
            var licenses = new ArrayList<String>();
            Element container = child(project, "licenses");
            if (container != null) {
                for (Node node = container.getFirstChild(); node != null; node = node.getNextSibling()) {
                    if (node instanceof Element license && "license".equals(license.getLocalName())) {
                        String name = text(license, "name");
                        String url = text(license, "url");
                        if (!name.isBlank()) licenses.add(name);
                        else if (!url.isBlank()) licenses.add(url);
                    }
                }
            }
            // Parent interpolation is intentionally unsupported: fail the scan instead of claiming no license.
            Element parent = child(project, "parent");
            Coordinates coordinates = parent == null || !licenses.isEmpty() ? null
                    : new Coordinates(text(parent, "groupId"), text(parent, "artifactId"), text(parent, "version"));
            return new PomMetadata(List.copyOf(licenses), coordinates);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cannot parse Maven license metadata safely", ex);
        }
    }

    private static Element child(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getLocalName())) return element;
        }
        return null;
    }

    private static String text(Element element, String name) {
        Element node = child(element, name);
        return node == null ? "" : node.getTextContent().trim();
    }
}

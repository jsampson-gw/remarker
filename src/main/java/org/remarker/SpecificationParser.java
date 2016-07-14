package org.remarker;

import static java.util.Arrays.*;
import static javax.xml.xpath.XPathConstants.NODESET;
import static org.remarker.AttributeDefinition.Type.*;
import static org.remarker.ElementDefinition.DTD.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.xml.xpath.*;

import org.remarker.AttributeDefinition.*;
import org.remarker.ElementDefinition.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class SpecificationParser
{
    public static final Map<String, CharacterDefinition> CHARACTERS;
    public static final Map<String, ElementDefinition> ELEMENTS;
    public static final Map<String, AttributeDefinition> ATTRIBUTES;

    static
    {
        try
        {
            CHARACTERS = Collections.unmodifiableMap(parseCharacters());
            ELEMENTS = Collections.unmodifiableMap(parseElements());
            ATTRIBUTES = Collections.unmodifiableMap(parseAttributes());
        }
        catch (Exception impossible)
        {
            throw new AssertionError(impossible);
        }
    }

    public static Map<String, CharacterDefinition> parseCharacters() throws Exception
    {
        Map<String, CharacterDefinition> characters = new TreeMap<>();
        characters.put("apos", new CharacterDefinition("apos", '\''));
        Pattern pattern = Pattern.compile("<!ENTITY ([A-Za-z0-9]+) +CDATA \"&#([0-9]+);\"");
        NodeList nodes = load("characters.html", "//pre");
        for (int index = 0; index < nodes.getLength(); index++)
        {
            Element pre = (Element) nodes.item(index);
            String text = pre.getTextContent();
            Matcher matcher = pattern.matcher(text);
            while (matcher.find())
            {
                String name = matcher.group(1);
                String code = matcher.group(2);
                char value = (char) Integer.parseInt(code);
                characters.put(name, new CharacterDefinition(name, value));
            }
        }
        return characters;
    }

    public static Map<String, ElementDefinition> parseElements() throws Exception
    {
        // definition of "inline" elements from the HTML 4.01 DTD
        Set<String> fontstyle = new HashSet<>(asList("TT", "I", "B", "BIG", "SMALL"));
        Set<String> phrase = new HashSet<>(asList("EM", "STRONG", "DFN", "CODE", "SAMP", "KBD", "VAR", "CITE", "ABBR",
                "ACRONYM"));
        Set<String> special = new HashSet<>(asList("A", "IMG", "OBJECT", "BR", "SCRIPT", "MAP", "Q", "SUB", "SUP", "SPAN",
                "BDO"));
        Set<String> formctrl = new HashSet<>(asList("INPUT", "SELECT", "TEXTAREA", "LABEL", "BUTTON"));

        Set<String> inline = new HashSet<>();
        inline.addAll(fontstyle);
        inline.addAll(phrase);
        inline.addAll(special);
        inline.addAll(formctrl);
        // remove BR and SCRIPT so that they are formatted on their own lines
        inline.remove("BR");
        inline.remove("SCRIPT");

        Map<String, ElementDefinition> elements = new TreeMap<>();
        NodeList nodes = load("elements.html", "//tr[td[1]/@title='Name']");
        for (int index = 0; index < nodes.getLength(); index++)
        {
            Element tr = (Element) nodes.item(index);
            String name = getCellValue(tr, 0);
            String empty = getCellValue(tr, 3);
            String dtd = getCellValue(tr, 5);
            elements.put(name.toLowerCase().intern(), new ElementDefinition(name, inline.contains(name), empty.equals("E"),
                    parseDTD(dtd)));
        }
        return elements;
    }

    public static Map<String, AttributeDefinition> parseAttributes() throws Exception
    {
        String elementNamesRegex = "([A-Z0-9]+(,[ \n\r]+[A-Z0-9]+)*)";
        Pattern elementNamesPattern = Pattern.compile("^" + elementNamesRegex + "$");
        Pattern allButElementNamesPattern = Pattern.compile("^All elements but " + elementNamesRegex + "$");
        Map<String, AttributeDefinition> attributes = new TreeMap<>();
        NodeList nodes = load("attributes.html", "//tr[td[1]/@title='Name']");
        for (int index = 0; index < nodes.getLength(); index++)
        {
            Element tr = (Element) nodes.item(index);
            String name = getCellValue(tr, 0).toLowerCase().intern();
            String elements = getCellValue(tr, 1);
            String typeCode = getCellValue(tr, 2);
            String dtdCode = getCellValue(tr, 5);
            Type type = typeCode.equals("(" + name + ")") ? BOOLEAN : typeCode.equals("NUMBER") ? NUMBER : STRING;
            DTD dtd = parseDTD(dtdCode);
            Map<String, Type> typesByElement = new HashMap<>();
            Map<String, DTD> dtdsByElement = new HashMap<>();
            if (attributes.containsKey(name))
            {
                AttributeDefinition oldDefinition = attributes.get(name);
                typesByElement.putAll(oldDefinition.typesByElement);
                dtdsByElement.putAll(oldDefinition.dtdsByElement);
            }
            Matcher elementNamesMatcher = elementNamesPattern.matcher(elements);
            Matcher allButElementNamesMatcher = allButElementNamesPattern.matcher(elements);
            if (elementNamesMatcher.matches())
            {
                putTypeAndDTD(elementNamesMatcher, typesByElement, dtdsByElement, type, dtd);
            }
            else if (allButElementNamesMatcher.matches())
            {
                typesByElement.put("*", type);
                dtdsByElement.put("*", dtd);
                putTypeAndDTD(allButElementNamesMatcher, typesByElement, dtdsByElement, null, null);
            }
            else
            {
                throw new IllegalStateException(elements);
            }
            attributes.put(name, new AttributeDefinition(name, typesByElement, dtdsByElement));
        }
        return attributes;
    }

    private static NodeList load(String filename, String xpath) throws Exception
    {
        try (InputStream in = SpecificationParser.class.getResourceAsStream("/org/remarker/" + filename))
        {
            return (NodeList) XPathFactory.newInstance().newXPath().evaluate(xpath, new InputSource(in), NODESET);
        }
    }

    private static String getCellValue(Element tr, int i)
    {
        NodeList children = tr.getChildNodes();
        int elementIndex = -1;
        for (int childIndex = 0; childIndex < children.getLength(); childIndex++)
        {
            if (children.item(childIndex) instanceof Element)
            {
                elementIndex++;
                if (elementIndex == i)
                {
                    return children.item(childIndex).getTextContent().trim();
                }
            }
        }
        throw new NoSuchElementException();
    }

    private static DTD parseDTD(String dtd)
    {
        return dtd.equals("L") ? LOOSE : dtd.equals("F") ? FRAMESET : STRICT;
    }

    private static void putTypeAndDTD(Matcher elementNamesMatcher, Map<String, Type> typesByElement,
            Map<String, DTD> dtdsByElement, Type type, DTD dtd)
    {
        String[] elementNames = getElementNames(elementNamesMatcher);
        for (String elementName : elementNames)
        {
            String elementNameInterned = elementName.toLowerCase().intern();
            typesByElement.put(elementNameInterned, type);
            dtdsByElement.put(elementNameInterned, dtd);
        }
    }

    private static String[] getElementNames(Matcher elementNamesMatcher)
    {
        return elementNamesMatcher.group(1).split(",[ \n\r]+");
    }
}

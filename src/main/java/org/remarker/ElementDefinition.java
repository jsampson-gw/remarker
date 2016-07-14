package org.remarker;

class ElementDefinition
{
    public final String lowercase;
    public final String uppercase;
    public final boolean inline;
    public final boolean empty;

    public ElementDefinition(String name, boolean inline, boolean empty)
    {
        this.lowercase = name.toLowerCase().intern();
        this.uppercase = name.toUpperCase().intern();
        this.inline = inline;
        this.empty = empty;
    }

    private String javaName()
    {
        return uppercase;
    }

    private String xmlName()
    {
        return lowercase;
    }

    public void generateCode()
    {
        System.out.println("    public static Element " + javaName() + "(Object... contents)");
        System.out.println("    {");
        System.out.println("        return element(\"" + xmlName() + "\", contents);");
        System.out.println("    }");
    }
}

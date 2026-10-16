package com.bingchunmoli.codegen;

import javax.lang.model.SourceVersion;

final class Names {
    private Names() { }
    static String camel(String name, boolean upper) {
        StringBuilder result = new StringBuilder();
        boolean capitalize = upper;
        for (char c : name.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') { capitalize = true; continue; }
            result.append(capitalize ? Character.toUpperCase(c) : Character.toLowerCase(c));
            capitalize = false;
        }
        return identifier(result.toString(), "generated name");
    }
    static String identifier(String name, String location) {
        if (name == null || !SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name) || java.util.Set.of("_", "var", "record", "yield", "sealed", "permits").contains(name))
            throw new CodegenException("Invalid Java identifier at " + location + "; configure an explicit name");
        return name;
    }
    static String javaType(String name, String location) {
        String component = name == null ? "" : name.endsWith("[]") ? name.substring(0, name.length() - 2) : name;
        boolean primitiveArray = name != null && name.endsWith("[]") && java.util.Set.of("byte", "short", "int", "long", "float", "double", "char", "boolean").contains(component);
        if (name == null || !name.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*(\\[\\])?") || !primitiveArray && !SourceVersion.isName(component))
            throw new CodegenException("Invalid Java type at " + location);
        return name;
    }
    static String comment(String text) {
        return text == null ? "" : text.replace("*/", "* /").replace("\\u", "\\ u").replace('\r', ' ').replace('\n', ' ');
    }
    static String literal(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
    static String beanSuffix(String property) {
        if (property.length() > 1 && Character.isUpperCase(property.charAt(1))) return property;
        return Character.toUpperCase(property.charAt(0)) + property.substring(1);
    }
}

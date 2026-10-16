package com.bingchunmoli.codegen;

import freemarker.cache.ClassTemplateLoader;
import freemarker.core.TemplateClassResolver;
import freemarker.template.*;
import java.io.*;
import java.util.Map;

/** Renders only a data model; templates cannot access application objects or Java constructors. */
public class TemplateRenderer {
    private final Configuration engine;
    public TemplateRenderer() {
        engine = new Configuration(Configuration.VERSION_2_3_34);
        engine.setDefaultEncoding("UTF-8");
        engine.setTemplateLoader(new ClassTemplateLoader(TemplateRenderer.class, "/codegen-templates"));
        engine.setObjectWrapper(new SimpleObjectWrapper(Configuration.VERSION_2_3_34));
        engine.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        engine.setAPIBuiltinEnabled(false);
        engine.setLogTemplateExceptions(false);
        engine.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        engine.setNumberFormat("computer");
        engine.setBooleanFormat("c");
    }
    public String render(String name, String source, Map<String, Object> context) {
        try {
            Template template = new Template(name, new StringReader(source), engine);
            StringWriter writer = new StringWriter();
            template.process(context, writer);
            return writer.toString().replace("\r\n", "\n");
        } catch (TemplateException e) {
            throw new CodegenException("Template " + name + ": error at line " + e.getLineNumber() + ", column " + e.getColumnNumber());
        } catch (IOException e) { throw new CodegenException("Template " + name + ": cannot parse template"); }
    }
    public String builtin(String orm, String artifact) {
        String path = "/codegen-templates/" + (artifact.equals("xml") || artifact.equals("mapper") || artifact.equals("service") || artifact.equals("serviceImpl") || artifact.equals("entity") ? orm : "shared") + "/" + artifact + ".ftl";
        try (InputStream stream = TemplateRenderer.class.getResourceAsStream(path)) {
            if (stream == null) throw new CodegenException("Unknown built-in template: " + artifact);
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) { throw new CodegenException("Cannot load template: " + artifact); }
    }
}

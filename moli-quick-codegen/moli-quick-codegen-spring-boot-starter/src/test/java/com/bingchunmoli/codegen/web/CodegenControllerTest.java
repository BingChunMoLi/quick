package com.bingchunmoli.codegen.web;

import com.bingchunmoli.codegen.*;
import com.bingchunmoli.codegen.jdbc.ConnectionProvider;
import com.bingchunmoli.codegen.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CodegenControllerTest {
    private final List<GeneratedFile> files = List.of(new GeneratedFile("src/main/java/example/User.java", "public class User {}"));
    private final CodeGenerator generator = mock(CodeGenerator.class);
    private final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
    private final CodegenController controller = new CodegenController(generator, beans);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new CodegenExceptionHandler()).build();
    private String json(GenerationConfig config) throws Exception { return new ObjectMapper().writeValueAsString(config); }
    private GenerationConfig config() { var config = new GenerationConfig(); config.datasource.url = "jdbc:mysql://127.0.0.1:3306/demo"; return config; }

    @Test void previewAndZipUseTheSameGeneratedFiles() throws Exception {
        when(generator.preview(any(), any(Path.class), any(ConnectionProvider.class))).thenReturn(files);
        var config = config();
        mvc.perform(post("/moli-codegen/api/preview").contentType(MediaType.APPLICATION_JSON).content(json(config)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.files[0].relativePath").value(files.get(0).relativePath()));
        byte[] zip = mvc.perform(post("/moli-codegen/api/export").contentType(MediaType.APPLICATION_JSON).content(json(config)))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", "attachment; filename=codegen.zip")).andReturn().getResponse().getContentAsByteArray();
        assertThat(zip).isEqualTo(FileOutputs.zip(files));
    }
    @Test void webCannotLoadAnExternalFileTemplate() throws Exception {
        var config = config(); var template = new GenerationConfig.Template(); template.file = "private.txt"; config.templates.put("entity", template);
        mvc.perform(post("/moli-codegen/api/preview").contentType(MediaType.APPLICATION_JSON).content(json(config)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.diagnostics[0]").value(org.hamcrest.Matchers.containsString("inline content")));
        verifyNoInteractions(generator);
    }
    @Test void exportedYamlOmitsPlainPasswordAndRetainsInlineEdits() throws Exception {
        var config = config(); config.author = "测试作者"; config.datasource.password = "plain-secret"; var template = new GenerationConfig.Template(); template.content = "${className}"; config.templates.put("entity", template);
        var response = mvc.perform(post("/moli-codegen/api/config/export").contentType(MediaType.APPLICATION_JSON).content(json(config)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String yaml = new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(yaml).doesNotContain("plain-secret").contains("${className}").contains("测试作者");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        mvc.perform(post("/moli-codegen/api/config/import").contentType("text/plain;charset=UTF-8").content(yaml))
                .andExpect(status().isOk()).andExpect(jsonPath("$.templates.entity.content").value("${className}"));
    }
    @Test void namedDataSourceIsPassedToTheSharedGenerator() throws Exception {
        DataSource source = mock(DataSource.class); beans.registerSingleton("businessDatabase", source);
        var config = config(); config.datasource.beanName = "businessDatabase";
        when(generator.tables(any(), any())).thenAnswer(invocation -> { invocation.<ConnectionProvider>getArgument(1).open(); return List.of(); });
        controller.tables(config);
        verify(source).getConnection();
        assertThat(controller.datasources()).containsExactly("businessDatabase");
    }
    @Test void unexpectedFailuresDoNotEchoSecrets() throws Exception {
        when(generator.preview(any(), any(), any())).thenThrow(new IllegalStateException("password=plain-secret"));
        String body = mvc.perform(post("/moli-codegen/api/preview").contentType(MediaType.APPLICATION_JSON).content(json(config())))
                .andExpect(status().isInternalServerError()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("plain-secret");
    }
    @Test void malformedJsonAndNullTableSelectionsReturnBadRequest() throws Exception {
        mvc.perform(post("/moli-codegen/api/preview").contentType(MediaType.APPLICATION_JSON).content("{bad-json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.diagnostics[0]").value("Invalid JSON configuration"));
        var config = config(); config.tables.add(null);
        mvc.perform(post("/moli-codegen/api/columns").contentType(MediaType.APPLICATION_JSON).content(json(config)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(generator);
    }
}

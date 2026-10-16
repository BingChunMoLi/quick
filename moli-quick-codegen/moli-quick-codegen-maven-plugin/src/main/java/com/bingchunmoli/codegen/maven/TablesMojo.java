package com.bingchunmoli.codegen.maven;

import com.bingchunmoli.codegen.jdbc.ConnectionProvider;
import com.bingchunmoli.codegen.model.GenerationConfig;
import org.apache.maven.plugins.annotations.Mojo;
import java.nio.file.Path;

@Mojo(name = "tables", threadSafe = true)
public class TablesMojo extends AbstractCodegenMojo {
    @Override protected void run(GenerationConfig settings, Path base, ConnectionProvider provider) {
        generator.tables(settings, provider).forEach(table -> getLog().info(table.name()));
    }
}

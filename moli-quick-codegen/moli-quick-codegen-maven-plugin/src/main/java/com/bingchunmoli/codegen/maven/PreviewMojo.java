package com.bingchunmoli.codegen.maven;

import com.bingchunmoli.codegen.jdbc.ConnectionProvider;
import com.bingchunmoli.codegen.model.GenerationConfig;
import org.apache.maven.plugins.annotations.Mojo;
import java.nio.file.Path;

@Mojo(name = "preview", threadSafe = true)
public class PreviewMojo extends AbstractCodegenMojo {
    @Override protected void run(GenerationConfig settings, Path base, ConnectionProvider provider) {
        render(settings, base, provider).forEach(file -> { getLog().info(file.relativePath()); getLog().info(file.content()); });
    }
}

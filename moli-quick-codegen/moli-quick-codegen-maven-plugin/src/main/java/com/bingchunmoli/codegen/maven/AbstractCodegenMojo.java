package com.bingchunmoli.codegen.maven;

import com.bingchunmoli.codegen.*;
import com.bingchunmoli.codegen.jdbc.*;
import com.bingchunmoli.codegen.model.*;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Parameter;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public abstract class AbstractCodegenMojo extends AbstractMojo {
    @Parameter(defaultValue = "${basedir}", readonly = true, required = true) protected File basedir;
    @Parameter(property = "codegen.config", defaultValue = "codegen.yml") protected String config;
    @Parameter(property = "codegen.overwrite", defaultValue = "false") protected boolean overwrite;
    @Parameter(property = "codegen.output") protected String output;
    @Parameter(property = "codegen.zip") protected String zip;
    protected final CodeGenerator generator = new CodeGenerator();

    @Override public final void execute() throws MojoExecutionException {
        try {
            Path file = basedir.toPath().resolve(config).toAbsolutePath().normalize();
            GenerationConfig settings = new ConfigCodec().read(file);
            run(settings, file.getParent(), JdbcConnections.of(settings.datasource));
        } catch (CodegenException e) { throw new MojoExecutionException(e.getMessage()); }
        catch (RuntimeException e) { throw new MojoExecutionException("Generation failed; check configuration and output permissions"); }
    }
    protected abstract void run(GenerationConfig settings, Path base, ConnectionProvider provider);
    protected List<GeneratedFile> render(GenerationConfig settings, Path base, ConnectionProvider provider) { return generator.preview(settings, base, provider); }
}

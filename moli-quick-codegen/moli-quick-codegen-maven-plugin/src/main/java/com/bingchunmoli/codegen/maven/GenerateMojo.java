package com.bingchunmoli.codegen.maven;

import com.bingchunmoli.codegen.*;
import com.bingchunmoli.codegen.jdbc.ConnectionProvider;
import com.bingchunmoli.codegen.model.GenerationConfig;
import org.apache.maven.plugins.annotations.Mojo;
import java.nio.file.Path;

@Mojo(name = "generate", threadSafe = true)
public class GenerateMojo extends AbstractCodegenMojo {
    @Override protected void run(GenerationConfig settings, Path base, ConnectionProvider provider) {
        if (zip != null && output != null) throw new CodegenException("Choose either codegen.zip or codegen.output");
        var files = render(settings, base, provider);
        if (zip != null) FileOutputs.writeZip(files, base.resolve(zip), overwrite || settings.output.overwrite);
        else FileOutputs.write(files, base.resolve(output == null ? settings.output.directory : output), overwrite || settings.output.overwrite);
        getLog().info("Generated " + files.size() + " files");
    }
}

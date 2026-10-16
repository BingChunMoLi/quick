package com.bingchunmoli.codegen.cli;

import com.bingchunmoli.codegen.*;
import com.bingchunmoli.codegen.jdbc.*;
import com.bingchunmoli.codegen.model.*;
import picocli.CommandLine;
import picocli.CommandLine.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "codegen", mixinStandardHelpOptions = true, subcommands = {CodegenCommand.Tables.class, CodegenCommand.Preview.class, CodegenCommand.Generate.class}, description = "MyBatis and MyBatis-Plus code generator")
public class CodegenCommand implements Runnable {
    @Spec Model.CommandSpec spec;
    @Override public void run() { spec.commandLine().usage(spec.commandLine().getOut()); }
    public static void main(String[] args) { System.exit(commandLine().execute(args)); }
    public static CommandLine commandLine() {
        return new CommandLine(new CodegenCommand()).setExecutionExceptionHandler((error, command, parsed) -> {
            command.getErr().println(error instanceof CodegenException ? error.getMessage() : "Generation failed; check configuration and output permissions");
            return 1;
        });
    }
    public abstract static class Base implements Callable<Integer> {
        @Option(names = {"--config", "-c"}, required = true, description = "YAML configuration file") Path configFile;
        @Spec Model.CommandSpec spec;
        final CodeGenerator generator = new CodeGenerator();
        GenerationConfig read() { return new ConfigCodec().read(configFile); }
        Path base() { return configFile.toAbsolutePath().normalize().getParent(); }
        List<GeneratedFile> render(GenerationConfig config) { return generator.preview(config, base(), JdbcConnections.of(config.datasource)); }
    }
    @Command(name = "tables", mixinStandardHelpOptions = true, description = "List tables in configured catalog/schema")
    public static class Tables extends Base {
        @Override public Integer call() {
            var config = read();
            generator.tables(config, JdbcConnections.of(config.datasource)).forEach(table -> spec.commandLine().getOut().println(table.name()));
            return 0;
        }
    }
    @Command(name = "preview", mixinStandardHelpOptions = true, description = "Render to stdout without writing files")
    public static class Preview extends Base {
        @Override public Integer call() {
            for (GeneratedFile file : render(read())) {
                spec.commandLine().getOut().println("--- " + file.relativePath());
                spec.commandLine().getOut().println(file.content());
            }
            return 0;
        }
    }
    @Command(name = "generate", mixinStandardHelpOptions = true, description = "Generate into a directory or ZIP")
    public static class Generate extends Base {
        @Option(names = "--overwrite", description = "Explicitly replace existing files") boolean overwrite;
        @Option(names = "--output", description = "Output directory, relative to config file") Path output;
        @Option(names = "--zip", description = "Write ZIP instead of project files, relative to config file") Path zip;
        @Override public Integer call() {
            GenerationConfig config = read();
            if (zip != null && output != null) throw new CodegenException("Choose either --zip or --output");
            List<GeneratedFile> files = render(config);
            if (zip != null) {
                Path target = base().resolve(zip).normalize();
                FileOutputs.writeZip(files, target, overwrite || config.output.overwrite);
            } else FileOutputs.write(files, base().resolve(output == null ? Path.of(config.output.directory) : output), overwrite || config.output.overwrite);
            spec.commandLine().getOut().println("Generated " + files.size() + " files");
            return 0;
        }
    }
}

package com.bingchunmoli.codegen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class CodegenCommandTest {
    @TempDir Path directory;
    @Test void invalidConfigFailsWithoutEchoingCredentials() throws Exception {
        Path config = directory.resolve("codegen.yml");
        Files.writeString(config, "datasource:\n  url: jdbc:unknown://host\n  password: plain-secret\n");
        var command = CodegenCommand.commandLine(); var error = new StringWriter(); command.setErr(new PrintWriter(error));
        assertThat(command.execute("generate", "--config", config.toString())).isEqualTo(1);
        assertThat(error.toString()).contains("MySQL and PostgreSQL").doesNotContain("plain-secret");
        assertThat(directory.resolve("src")).doesNotExist();
    }
    @Test void missingConfigArgumentIsAUsageFailure() {
        var command = CodegenCommand.commandLine(); command.setErr(new PrintWriter(new StringWriter()));
        assertThat(command.execute("generate")).isEqualTo(2);
    }
}

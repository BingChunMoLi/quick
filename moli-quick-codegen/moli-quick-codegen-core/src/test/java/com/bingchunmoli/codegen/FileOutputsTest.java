package com.bingchunmoli.codegen;

import com.bingchunmoli.codegen.model.GeneratedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileOutputsTest {
    @TempDir Path directory;
    @Test void conflictsAreDetectedBeforeWritingAnyFile() throws Exception {
        Files.writeString(directory.resolve("z.txt"), "existing");
        var files = List.of(new GeneratedFile("a.txt", "new"), new GeneratedFile("z.txt", "replacement"));
        assertThatThrownBy(() -> FileOutputs.write(files, directory, false)).hasMessageContaining("z.txt");
        assertThat(directory.resolve("a.txt")).doesNotExist();
        assertThat(Files.readString(directory.resolve("z.txt"))).isEqualTo("existing");
        FileOutputs.write(files, directory, true);
        assertThat(Files.readString(directory.resolve("z.txt"))).isEqualTo("replacement");
    }
    @Test void zipIsStableAndExistingZipIsPreservedOnConflict() throws Exception {
        var files = List.of(new GeneratedFile("b.txt", "乙"), new GeneratedFile("a.txt", "甲"));
        assertThat(FileOutputs.zip(files)).isEqualTo(FileOutputs.zip(List.of(files.get(1), files.get(0))));
        var destination = directory.resolve("output.zip");
        FileOutputs.writeZip(files, destination, false);
        byte[] original = Files.readAllBytes(destination);
        assertThatThrownBy(() -> FileOutputs.writeZip(List.of(new GeneratedFile("c.txt", "x")), destination, false)).isInstanceOf(CodegenException.class);
        assertThat(Files.readAllBytes(destination)).isEqualTo(original);
    }
    @Test void traversalAbsolutePathsAndDuplicatePathsAreRejected() {
        for (String path : List.of("../escape.txt", "/tmp/escape.txt", "C:\\escape.txt", "a/../../escape.txt", "\\\\server\\share"))
            assertThatThrownBy(() -> FileOutputs.zip(List.of(new GeneratedFile(path, "bad")))).isInstanceOf(CodegenException.class);
        assertThatThrownBy(() -> FileOutputs.zip(List.of(new GeneratedFile("a.txt", "one"), new GeneratedFile("./a.txt", "two")))).hasMessageContaining("Duplicate");
    }
    @Test void symlinkCannotRedirectOutputOutsideRoot() throws Exception {
        Path outside = Files.createDirectory(directory.resolve("outside")), root = Files.createDirectory(directory.resolve("root"));
        try { Files.createSymbolicLink(root.resolve("link"), outside); }
        catch (Exception e) {
            if (!System.getProperty("os.name").startsWith("Windows")) assumeTrue(false, "OS does not permit test symlinks");
            String command = "New-Item -ItemType Junction -Path '" + root.resolve("link").toString().replace("'", "''") + "' -Target '" + outside.toString().replace("'", "''") + "' | Out-Null";
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            assumeTrue(finished && process.exitValue() == 0, "OS does not permit test links");
        }
        try {
            assertThatThrownBy(() -> FileOutputs.write(List.of(new GeneratedFile("link/file.txt", "bad")), root, false)).hasMessageContaining("link");
            assertThatThrownBy(() -> FileOutputs.write(List.of(new GeneratedFile("file.txt", "bad")), root.resolve("link/new-root"), false)).hasMessageContaining("Link");
            assertThat(outside.resolve("file.txt")).doesNotExist();
            assertThat(outside.resolve("new-root")).doesNotExist();
        } finally { Files.deleteIfExists(root.resolve("link")); }
    }
    @Test void fileAndChildPathConflictFailsBeforeCreatingOutputDirectory() {
        var files = List.of(new GeneratedFile("a", "file"), new GeneratedFile("a/b.txt", "child"));
        Path output = directory.resolve("output");
        assertThatThrownBy(() -> FileOutputs.write(files, output, false)).hasMessageContaining("conflict");
        assertThat(output).doesNotExist();
        assertThatThrownBy(() -> FileOutputs.zip(files)).isInstanceOf(CodegenException.class);
    }
    @Test void normalizedPathsHaveTheSameDirectoryAndZipNames() throws Exception {
        var files = List.of(new GeneratedFile("dir\\nested/./file.txt", "content"));
        FileOutputs.write(files, directory, false);
        assertThat(Files.readString(directory.resolve("dir/nested/file.txt"))).isEqualTo("content");
    }
    @Test void windowsDeviceAndAliasedNamesAreRejectedBeforeWriting() {
        assumeTrue(java.io.File.separatorChar == '\\');
        for (String name : List.of("CON.java", "LPT1.txt", "directory./file.txt", "trailing "))
            assertThatThrownBy(() -> FileOutputs.zip(List.of(new GeneratedFile(name, "bad")))).isInstanceOf(CodegenException.class);
    }
}

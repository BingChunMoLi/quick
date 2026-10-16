package com.bingchunmoli.codegen;

import com.bingchunmoli.codegen.model.GeneratedFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Shared collision and containment checks for ZIP and directory output. */
public final class FileOutputs {
    private FileOutputs() { }
    public static String relative(String value) {
        if (value == null || value.isBlank()) throw new CodegenException("Empty output path");
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":") || normalized.contains("\u0000") || Arrays.asList(normalized.split("/")).contains(".."))
            throw new CodegenException("Output path must stay relative to the output directory");
        if (File.separatorChar == '\\') for (String part : normalized.split("/")) {
            if (part.equals(".")) continue;
            if (part.endsWith(".") || part.endsWith(" ") || part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))
                throw new CodegenException("Invalid Windows output path: " + normalized);
        }
        try { return Path.of(normalized).normalize().toString().replace('\\', '/'); }
        catch (InvalidPathException e) { throw new CodegenException("Invalid output path"); }
    }
    public static byte[] zip(List<GeneratedFile> files) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                for (GeneratedFile file : checked(files)) {
                    ZipEntry entry = new ZipEntry(relative(file.relativePath()));
                    entry.setTime(0);
                    zip.putNextEntry(entry);
                    zip.write(file.content().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) { throw new CodegenException("Cannot create ZIP output"); }
    }
    public static void write(List<GeneratedFile> files, Path directory, boolean overwrite) {
        List<GeneratedFile> checked = checked(files);
        Path root = directory.toAbsolutePath().normalize();
        try {
            List<String> conflicts = new ArrayList<>();
            for (GeneratedFile file : checked) {
                Path target = root.resolve(relative(file.relativePath())).normalize();
                contain(root, target);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && (!overwrite || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))) conflicts.add(file.relativePath());
            }
            if (!conflicts.isEmpty()) throw new CodegenException("Output conflicts: " + String.join(", ", conflicts));
            Files.createDirectories(root);
            Map<Path, Path> staged = new LinkedHashMap<>();
            try {
                for (GeneratedFile file : checked) {
                    Path target = root.resolve(file.relativePath());
                    contain(root, target);
                    Files.createDirectories(target.getParent());
                    contain(root, target);
                    Path temp = Files.createTempFile(target.getParent(), ".codegen-", ".tmp");
                    staged.put(temp, target);
                    Files.writeString(temp, file.content(), StandardCharsets.UTF_8);
                }
                for (var entry : staged.entrySet()) {
                    contain(root, entry.getValue());
                    if (overwrite) Files.move(entry.getKey(), entry.getValue(), StandardCopyOption.REPLACE_EXISTING);
                    else Files.move(entry.getKey(), entry.getValue());
                }
            } finally { for (Path temp : staged.keySet()) Files.deleteIfExists(temp); }
        } catch (IOException e) { throw new CodegenException("Cannot write generated files; check output permissions"); }
    }
    public static void writeZip(List<GeneratedFile> files, Path destination, boolean overwrite) {
        byte[] bytes = zip(files);
        Path target = destination.toAbsolutePath().normalize();
        Path root = target.getParent();
        if (root == null) throw new CodegenException("ZIP output must name a file");
        try {
            contain(root, target);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && (!overwrite || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)))
                throw new CodegenException("ZIP output already exists");
            Files.createDirectories(root);
            contain(root, target);
            Path temp = Files.createTempFile(root, ".codegen-", ".zip.tmp");
            try {
                Files.write(temp, bytes);
                contain(root, target);
                if (overwrite) Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(temp, target);
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException e) { throw new CodegenException("Cannot write ZIP output"); }
    }
    public static List<GeneratedFile> validate(List<GeneratedFile> files) { return checked(files); }
    private static List<GeneratedFile> checked(List<GeneratedFile> files) {
        if (files == null) throw new CodegenException("Generated files cannot be null");
        Set<String> paths = new HashSet<>();
        List<GeneratedFile> normalized = new ArrayList<>();
        for (GeneratedFile file : files) {
            if (file == null || file.content() == null) throw new CodegenException("Generated file and content cannot be null");
            String path = relative(file.relativePath());
            String key = File.separatorChar == '\\' ? path.toLowerCase(Locale.ROOT) : path;
            if (path.isBlank() || path.equals(".") || !paths.add(key)) throw new CodegenException("Duplicate or empty generated path: " + path);
            normalized.add(new GeneratedFile(path, file.content()));
        }
        for (String path : paths) {
            int slash = path.lastIndexOf('/');
            while (slash >= 0) {
                if (paths.contains(path.substring(0, slash))) throw new CodegenException("Generated file/directory path conflict: " + path);
                slash = path.lastIndexOf('/', slash - 1);
            }
        }
        return normalized.stream().sorted(Comparator.comparing(GeneratedFile::relativePath)).toList();
    }
    private static void contain(Path root, Path target) throws IOException {
        if (!target.startsWith(root)) throw new CodegenException("Output path escapes output directory");
        Path current = root;
        Path ancestor = root;
        while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
        if (ancestor != null && !ancestor.toRealPath().equals(ancestor)) throw new CodegenException("Link in output directory or its ancestors");
        if (Files.isSymbolicLink(root)) throw new CodegenException("Output directory cannot be a symbolic link");
        Path realRoot = Files.exists(root) ? root.toRealPath() : null;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new CodegenException("Symbolic link in output path");
            if (realRoot != null && Files.exists(current) && !current.toRealPath().startsWith(realRoot))
                throw new CodegenException("Output path escapes output directory through a link");
            if (!current.equals(target) && Files.exists(current) && !Files.isDirectory(current)) throw new CodegenException("Output parent is not a directory");
        }
    }
}

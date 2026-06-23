package com.bix.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class WorldFolderPackager {
    static final long MAX_UNCOMPRESSED_BYTES = 2L * 1024L * 1024L * 1024L;
    static final int MAX_ENTRIES = 100_000;
    static final int MAX_DEPTH = 64;

    interface Node {
        String getName();
        boolean isDirectory();
        List<Node> listChildren() throws IOException;
        InputStream open() throws IOException;
    }

    private WorldFolderPackager() {
    }

    static void packageWorld(Node root, OutputStream output) throws IOException {
        packageWorld(
                root,
                output,
                MAX_UNCOMPRESSED_BYTES,
                MAX_ENTRIES,
                MAX_DEPTH
        );
    }

    static void packageFolder(Node root, OutputStream output) throws IOException {
        if (root == null || !root.isDirectory()) {
            throw new IOException("The selected add-on is not a folder.");
        }
        Limits limits = new Limits(MAX_UNCOMPRESSED_BYTES, MAX_ENTRIES, MAX_DEPTH);
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeChildren(root, "", 0, zip, limits);
        }
    }

    static void packageAddon(List<? extends Node> packs, OutputStream output) throws IOException {
        if (packs == null || packs.size() < 2) {
            throw new IOException("Choose at least two unpacked pack folders for a combined add-on.");
        }
        Limits limits = new Limits(MAX_UNCOMPRESSED_BYTES, MAX_ENTRIES, MAX_DEPTH);
        Set<String> outputNames = new HashSet<>();
        try (ZipOutputStream addon = new ZipOutputStream(output)) {
            for (Node pack : packs) {
                if (pack == null || !pack.isDirectory() || !containsManifest(pack)) {
                    throw new IOException("Every combined pack folder must contain manifest.json at its root.");
                }
                String entryName = uniquePackName(pack.getName(), outputNames);
                addon.putNextEntry(new ZipEntry(entryName));
                ZipOutputStream nested = new ZipOutputStream(new NonClosingOutputStream(addon));
                writeChildren(pack, "", 0, nested, limits);
                nested.finish();
                nested.flush();
                addon.closeEntry();
            }
        }
    }

    static void packageWorld(
            Node root,
            OutputStream output,
            long maxBytes,
            int maxEntries,
            int maxDepth
    ) throws IOException {
        if (root == null || !root.isDirectory()) {
            throw new IOException("The selected level is not a folder.");
        }
        if (!containsLevelDat(root)) {
            throw new IOException("The selected folder does not contain level.dat.");
        }

        Limits limits = new Limits(maxBytes, maxEntries, maxDepth);
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeChildren(root, "", 0, zip, limits);
        }
    }

    private static boolean containsLevelDat(Node root) throws IOException {
        for (Node child : root.listChildren()) {
            if (!child.isDirectory() && "level.dat".equals(child.getName())) return true;
        }
        return false;
    }

    private static boolean containsManifest(Node root) throws IOException {
        for (Node child : root.listChildren()) {
            if (!child.isDirectory() && "manifest.json".equals(child.getName())) return true;
        }
        return false;
    }

    private static String uniquePackName(String name, Set<String> used) throws IOException {
        String base = safeName(name == null ? "Bedrock Pack" : name).trim();
        if (base.toLowerCase(Locale.ROOT).endsWith(".mcpack")) {
            base = base.substring(0, base.length() - ".mcpack".length());
        }
        if (base.isEmpty()) base = "Bedrock Pack";
        String candidate = base + ".mcpack";
        int suffix = 2;
        while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + " " + suffix++ + ".mcpack";
        }
        return candidate;
    }

    private static void writeChildren(
            Node parent,
            String prefix,
            int depth,
            ZipOutputStream zip,
            Limits limits
    ) throws IOException {
        if (depth > limits.maxDepth) {
            throw new IOException("The level folder is nested too deeply.");
        }

        for (Node child : parent.listChildren()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Level import was cancelled.");
            }

            String name = safeName(child.getName());
            String entryName = prefix + name;
            limits.countEntry();

            if (child.isDirectory()) {
                zip.putNextEntry(new ZipEntry(entryName + "/"));
                zip.closeEntry();
                writeChildren(child, entryName + "/", depth + 1, zip, limits);
            } else {
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream input = child.open()) {
                    if (input == null) throw new IOException("Could not read " + name);
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        limits.addBytes(read);
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static String safeName(String name) throws IOException {
        if (name == null
                || name.trim().isEmpty()
                || ".".equals(name)
                || "..".equals(name)
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0) {
            throw new IOException("The level contains an unsafe file name.");
        }
        return name;
    }

    private static final class Limits {
        final long maxBytes;
        final int maxEntries;
        final int maxDepth;
        long bytes;
        int entries;

        Limits(long maxBytes, int maxEntries, int maxDepth) {
            this.maxBytes = maxBytes;
            this.maxEntries = maxEntries;
            this.maxDepth = maxDepth;
        }

        void addBytes(int count) throws IOException {
            bytes += count;
            if (bytes > maxBytes) {
                throw new IOException("The level is too large to package safely.");
            }
        }

        void countEntry() throws IOException {
            entries++;
            if (entries > maxEntries) {
                throw new IOException("The level contains too many files.");
            }
        }
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {
        NonClosingOutputStream(OutputStream output) {
            super(output);
        }

        @Override public void close() throws IOException {
            flush();
        }
    }
}

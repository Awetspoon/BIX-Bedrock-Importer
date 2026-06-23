package com.bix.importer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

final class SafeArchiveValidator {
    static final long MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L * 1024L;
    static final long MAX_EXPANDED_BYTES = 4L * 1024L * 1024L * 1024L;
    static final int MAX_ENTRIES = 100_000;

    enum Type {
        MCPACK(".mcpack", "application/vnd.minecraft.mcpack"),
        MCADDON(".mcaddon", "application/vnd.minecraft.mcaddon"),
        MCWORLD(".mcworld", "application/vnd.minecraft.mcworld"),
        ZIP(".zip", "application/zip");

        final String extension;
        final String mimeType;

        Type(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        static Type fromFileName(String name) throws IOException {
            String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jar")) {
                throw new IOException("Java Edition mod detected. .jar files cannot run in Minecraft Bedrock on Android.");
            }
            for (Type type : values()) if (lower.endsWith(type.extension)) return type;
            throw new IOException("Choose a .mcaddon, .mcpack, .mcworld, or .zip file.");
        }
    }

    private SafeArchiveValidator() { }

    static void validate(File archive, Type type) throws IOException {
        if (archive == null || !archive.isFile() || archive.length() == 0) {
            throw new IOException("The selected file is empty or unreadable.");
        }
        if (archive.length() > MAX_ARCHIVE_BYTES) throw new IOException("The selected file is too large.");

        if (type == Type.MCADDON) validateAddon(archive);
        else validateSingleArchive(archive, type);
    }

    private static void validateSingleArchive(File archive, Type type) throws IOException {
        int count = 0;
        long expanded = 0;
        ZipEntry rootManifest = null;
        boolean hasLevelDat = false;
        Set<String> names = new HashSet<>();

        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                count++;
                if (count > MAX_ENTRIES) throw new IOException("The archive contains too many files.");
                String name = normalizedEntry(entry.getName());
                rejectDuplicate(names, name);
                long size = entry.getSize();
                if (size > 0) {
                    expanded += size;
                    if (expanded > MAX_EXPANDED_BYTES) throw new IOException("The archive expands beyond the safety limit.");
                }
                if ("manifest.json".equals(name)) rootManifest = entry;
                if ("level.dat".equals(name)) hasLevelDat = true;
            }

            if (count == 0) throw new IOException("The selected archive is empty.");
            if (type == Type.MCPACK) {
                if (rootManifest == null) throw new IOException("This pack has no manifest.json at its root.");
                try (InputStream input = zip.getInputStream(rootManifest)) {
                    BedrockManifestValidator.validate(input);
                }
            } else if (type == Type.MCWORLD && !hasLevelDat) {
                throw new IOException("This world has no level.dat at its root.");
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("The selected file is not a valid Bedrock archive.");
        }
    }

    private static void validateAddon(File archive) throws IOException {
        int outerEntries = 0;
        long[] expanded = { 0 };
        int[] nestedEntries = { 0 };
        boolean supported = false;
        Set<String> names = new HashSet<>();
        Set<java.util.UUID> packUuids = new HashSet<>();

        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                outerEntries++;
                if (outerEntries > MAX_ENTRIES) throw new IOException("The add-on contains too many files.");
                String name = normalizedEntry(entry.getName());
                rejectDuplicate(names, name);
                if (entry.getSize() > 0) addExpanded(expanded, entry.getSize());
                if (entry.isDirectory()) continue;
                String lower = name.toLowerCase(Locale.ROOT);
                boolean atRoot = name.indexOf('/') < 0;
                if (atRoot && lower.endsWith(Type.MCPACK.extension)) {
                    supported = true;
                    try (InputStream input = zip.getInputStream(entry)) {
                        BedrockManifestValidator.Info info =
                                validateNested(input, Type.MCPACK, expanded, nestedEntries);
                        if (info != null && !packUuids.add(info.headerUuid)) {
                            throw new IOException("The add-on contains packs with duplicate header UUIDs.");
                        }
                    }
                } else if (atRoot && lower.endsWith(Type.MCWORLD.extension)) {
                    supported = true;
                    try (InputStream input = zip.getInputStream(entry)) {
                        validateNested(input, Type.MCWORLD, expanded, nestedEntries);
                    }
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("The selected file is not a valid Bedrock add-on.");
        }

        if (!supported) {
            throw new IOException("This .mcaddon must contain .mcpack or .mcworld files at its root.");
        }
    }

    private static BedrockManifestValidator.Info validateNested(
            InputStream source, Type type, long[] expanded, int[] totalEntries) throws IOException {
        int count = 0;
        boolean hasManifest = false;
        boolean hasLevelDat = false;
        BedrockManifestValidator.Info manifestInfo = null;
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                count++;
                if (count > MAX_ENTRIES) throw new IOException("A nested Bedrock archive contains too many files.");
                totalEntries[0]++;
                if (totalEntries[0] > MAX_ENTRIES) {
                    throw new IOException("The add-on contains too many nested files.");
                }
                String name = normalizedEntry(entry.getName());
                rejectDuplicate(names, name);
                boolean manifest = "manifest.json".equals(name);
                if (manifest) hasManifest = true;
                if ("level.dat".equals(name)) hasLevelDat = true;

                if (manifest && type == Type.MCPACK) {
                    byte[] bytes = readNestedEntry(zip, expanded, BedrockManifestValidator.MAX_MANIFEST_BYTES);
                    manifestInfo = BedrockManifestValidator.validate(new java.io.ByteArrayInputStream(bytes));
                } else {
                    drainNestedEntry(zip, expanded);
                }
                zip.closeEntry();
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("A nested Bedrock archive is damaged or invalid.");
        }
        if (count == 0) throw new IOException("A nested Bedrock archive is empty.");
        if (type == Type.MCPACK && !hasManifest) {
            throw new IOException("A nested .mcpack has no manifest.json at its root.");
        }
        if (type == Type.MCWORLD && !hasLevelDat) {
            throw new IOException("A nested .mcworld has no level.dat at its root.");
        }
        return manifestInfo;
    }

    private static byte[] readNestedEntry(ZipInputStream input, long[] expanded, int limit) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            addExpanded(expanded, read);
            if (output.size() + read > limit) throw new IOException("manifest.json is too large.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void drainNestedEntry(ZipInputStream input, long[] expanded) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) addExpanded(expanded, read);
    }

    private static void addExpanded(long[] expanded, long count) throws IOException {
        expanded[0] += count;
        if (expanded[0] > MAX_EXPANDED_BYTES) {
            throw new IOException("The add-on expands beyond the safety limit.");
        }
    }

    private static void rejectDuplicate(Set<String> names, String name) throws IOException {
        String key = name.toLowerCase(Locale.ROOT);
        if (!names.add(key)) throw new IOException("The archive contains duplicate file paths.");
    }

    private static String normalizedEntry(String name) throws IOException {
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0 || name.indexOf('\\') >= 0
                || name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            throw new IOException("The archive contains an unsafe file path.");
        }
        String[] parts = name.split("/");
        for (String part : parts) {
            if ("..".equals(part)) throw new IOException("The archive contains an unsafe file path.");
        }
        return name;
    }
}

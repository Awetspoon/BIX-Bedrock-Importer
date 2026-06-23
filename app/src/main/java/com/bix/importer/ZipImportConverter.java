package com.bix.importer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class ZipImportConverter {
    static final class Plan {
        final SafeArchiveValidator.Type type;
        final String stripPrefix;
        final List<String> packPrefixes;

        Plan(SafeArchiveValidator.Type type, String stripPrefix) {
            this(type, stripPrefix, new ArrayList<>());
        }

        Plan(SafeArchiveValidator.Type type, String stripPrefix, List<String> packPrefixes) {
            this.type = type;
            this.stripPrefix = stripPrefix;
            this.packPrefixes = packPrefixes;
        }

        boolean bundlesLoosePacks() {
            return !packPrefixes.isEmpty();
        }
    }

    private ZipImportConverter() { }

    static Plan inspect(File source) throws IOException {
        List<String> names = readSafeNames(source);
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if ("pack.mcmeta".equals(lower) || lower.endsWith("/pack.mcmeta")) {
                throw new IOException("Java Edition content detected. It cannot run in Minecraft Bedrock on Android.");
            }
        }

        if (contains(names, "level.dat")) return new Plan(SafeArchiveValidator.Type.MCWORLD, "");
        if (contains(names, "manifest.json")) return new Plan(SafeArchiveValidator.Type.MCPACK, "");

        String wrapper = oneCommonTopFolder(names);
        if (wrapper != null) {
            if (contains(names, wrapper + "level.dat")) return new Plan(SafeArchiveValidator.Type.MCWORLD, wrapper);
            if (contains(names, wrapper + "manifest.json")) return new Plan(SafeArchiveValidator.Type.MCPACK, wrapper);
        }

        String canonicalPrefix = wrapper == null ? "" : wrapper;
        if (containsNestedBedrockArchive(names, canonicalPrefix)) {
            return new Plan(SafeArchiveValidator.Type.MCADDON, canonicalPrefix);
        }
        if (!canonicalPrefix.isEmpty() && containsNestedBedrockArchive(names, "")) {
            return new Plan(SafeArchiveValidator.Type.MCADDON, "");
        }

        List<String> packPrefixes = manifestParents(names, canonicalPrefix);
        if (packPrefixes.size() >= 2) {
            return new Plan(SafeArchiveValidator.Type.MCADDON, canonicalPrefix, packPrefixes);
        }
        if (!canonicalPrefix.isEmpty()) {
            packPrefixes = manifestParents(names, "");
            if (packPrefixes.size() >= 2) {
                return new Plan(SafeArchiveValidator.Type.MCADDON, "", packPrefixes);
            }
        }
        throw new IOException("The ZIP does not contain a clear Bedrock add-on or world.");
    }

    static void convert(File source, File output, Plan plan) throws IOException {
        try {
            if (plan.bundlesLoosePacks()) bundleLoosePacks(source, output, plan);
            else copyArchive(source, output, plan.stripPrefix);
        } catch (Exception e) {
            output.delete();
            throw e;
        }
    }

    private static List<String> readSafeNames(File source) throws IOException {
        List<String> names = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        long expanded = 0;
        try (ZipFile zip = new ZipFile(source)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = safeEntry(entry.getName());
                if (!unique.add(name.toLowerCase(Locale.ROOT))) {
                    throw new IOException("The ZIP contains duplicate file paths.");
                }
                names.add(name);
                if (names.size() > SafeArchiveValidator.MAX_ENTRIES) {
                    throw new IOException("The ZIP contains too many files.");
                }
                if (entry.getSize() > 0) {
                    expanded += entry.getSize();
                    if (expanded > SafeArchiveValidator.MAX_EXPANDED_BYTES) {
                        throw new IOException("The ZIP expands beyond the safety limit.");
                    }
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("The selected ZIP is damaged or invalid.");
        }
        if (names.isEmpty()) throw new IOException("The selected ZIP is empty.");
        return names;
    }

    private static void copyArchive(File source, File output, String stripPrefix) throws IOException {
        long written = 0;
        int count = 0;
        Set<String> names = new HashSet<>();
        try (ZipFile input = new ZipFile(source);
             ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            Enumeration<? extends ZipEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry original = entries.nextElement();
                String name = relativeName(safeEntry(original.getName()), stripPrefix);
                if (name == null || name.isEmpty()) continue;
                if (!names.add(name.toLowerCase(Locale.ROOT))) {
                    throw new IOException("The ZIP contains duplicate file paths.");
                }
                count++;
                if (count > SafeArchiveValidator.MAX_ENTRIES) throw new IOException("The ZIP contains too many files.");
                ZipEntry target = new ZipEntry(name);
                zip.putNextEntry(target);
                if (!original.isDirectory()) {
                    written = copyLimited(input.getInputStream(original), zip, written);
                }
                zip.closeEntry();
            }
        }
    }

    private static void bundleLoosePacks(File source, File output, Plan plan) throws IOException {
        long written = 0;
        int count = 0;
        Set<String> outerNames = new HashSet<>();
        try (ZipFile input = new ZipFile(source);
             ZipOutputStream addon = new ZipOutputStream(new FileOutputStream(output))) {
            for (String packPrefix : plan.packPrefixes) {
                String packName = uniquePackName(packPrefix, outerNames);
                addon.putNextEntry(new ZipEntry(packName));
                ZipOutputStream pack = new ZipOutputStream(new NonClosingOutputStream(addon));
                Set<String> innerNames = new HashSet<>();
                Enumeration<? extends ZipEntry> entries = input.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry original = entries.nextElement();
                    String relative = relativeName(safeEntry(original.getName()), plan.stripPrefix);
                    if (relative == null || !relative.startsWith(packPrefix)) continue;
                    String name = relative.substring(packPrefix.length());
                    if (name.isEmpty()) continue;
                    if (!innerNames.add(name.toLowerCase(Locale.ROOT))) {
                        throw new IOException("A pack contains duplicate file paths.");
                    }
                    count++;
                    if (count > SafeArchiveValidator.MAX_ENTRIES) throw new IOException("The ZIP contains too many files.");
                    pack.putNextEntry(new ZipEntry(name));
                    if (!original.isDirectory()) {
                        written = copyLimited(input.getInputStream(original), pack, written);
                    }
                    pack.closeEntry();
                }
                pack.finish();
                pack.flush();
                addon.closeEntry();
            }
        }
    }

    private static long copyLimited(InputStream input, OutputStream output, long written) throws IOException {
        try (InputStream stream = input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                written += read;
                if (written > SafeArchiveValidator.MAX_EXPANDED_BYTES) {
                    throw new IOException("The ZIP expands beyond the safety limit.");
                }
                output.write(buffer, 0, read);
            }
        }
        return written;
    }

    private static String relativeName(String name, String prefix) {
        if (prefix.isEmpty()) return name;
        if (!name.startsWith(prefix)) return null;
        return name.substring(prefix.length());
    }

    private static List<String> manifestParents(List<String> names, String stripPrefix) {
        List<String> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String original : names) {
            String name = relativeName(original, stripPrefix);
            if (name == null) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith("/manifest.json")) continue;
            String parent = name.substring(0, name.length() - "manifest.json".length());
            String key = parent.toLowerCase(Locale.ROOT);
            if (unique.add(key)) result.add(parent);
        }
        return result;
    }

    private static boolean containsNestedBedrockArchive(List<String> names, String stripPrefix) {
        for (String original : names) {
            String name = relativeName(original, stripPrefix);
            if (name == null || name.endsWith("/")) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (name.indexOf('/') < 0
                    && (lower.endsWith(".mcpack") || lower.endsWith(".mcworld"))) return true;
        }
        return false;
    }

    private static String uniquePackName(String packPrefix, Set<String> used) throws IOException {
        String value = packPrefix.endsWith("/")
                ? packPrefix.substring(0, packPrefix.length() - 1) : packPrefix;
        int slash = value.lastIndexOf('/');
        String base = slash >= 0 ? value.substring(slash + 1) : value;
        if (base.isEmpty() || base.contains("\\") || base.indexOf('\0') >= 0) {
            throw new IOException("A pack folder has an unsafe name.");
        }
        String candidate = base + ".mcpack";
        int suffix = 2;
        while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + " " + suffix++ + ".mcpack";
        }
        return candidate;
    }

    private static String oneCommonTopFolder(List<String> names) {
        String common = null;
        for (String name : names) {
            int slash = name.indexOf('/');
            if (slash < 0) return null;
            String top = name.substring(0, slash + 1);
            if (common == null) common = top;
            else if (!common.equals(top)) return null;
        }
        return common;
    }

    private static boolean contains(List<String> names, String wanted) {
        for (String name : names) if (wanted.equals(name)) return true;
        return false;
    }

    private static String safeEntry(String name) throws IOException {
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0 || name.indexOf('\\') >= 0
                || name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            throw new IOException("The ZIP contains an unsafe file path.");
        }
        for (String part : name.split("/")) {
            if ("..".equals(part)) throw new IOException("The ZIP contains an unsafe file path.");
        }
        return name;
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {
        NonClosingOutputStream(OutputStream output) { super(output); }
        @Override public void close() throws IOException { flush(); }
    }
}

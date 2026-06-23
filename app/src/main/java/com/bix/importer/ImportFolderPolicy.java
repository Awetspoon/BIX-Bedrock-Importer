package com.bix.importer;

import java.util.Locale;

final class ImportFolderPolicy {
    private ImportFolderPolicy() { }

    static boolean isBroadStorageRoot(String displayName, String documentId) {
        String name = normalize(displayName);
        if ("download".equals(name) || "downloads".equals(name)
                || "documents".equals(name) || "internal storage".equals(name)) {
            return true;
        }

        String id = normalize(documentId).replace('\\', '/');
        while (id.endsWith("/")) id = id.substring(0, id.length() - 1);
        return "primary:".equals(id) || "primary:download".equals(id)
                || "primary:downloads".equals(id) || "primary:documents".equals(id)
                || "downloads".equals(id) || id.endsWith("/download")
                || id.endsWith("/downloads") || id.endsWith("/documents");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

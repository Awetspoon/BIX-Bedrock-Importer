package com.bix.importer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppUpdateChecker {
    private static final Pattern GITHUB_TAG_PATTERN =
            Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([0-9]+(?:\\.[0-9]+){1,3})\"");

    private AppUpdateChecker() { }

    static String latestVersionFromGitHub(String json) {
        if (json == null) return null;
        Matcher matcher = GITHUB_TAG_PATTERN.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    static boolean isNewerVersion(String latest, String current) {
        int[] latestParts = versionParts(latest);
        int[] currentParts = versionParts(current);
        for (int i = 0; i < latestParts.length; i++) {
            if (latestParts[i] > currentParts[i]) return true;
            if (latestParts[i] < currentParts[i]) return false;
        }
        return false;
    }

    private static int[] versionParts(String value) {
        int[] result = new int[] { 0, 0, 0, 0 };
        if (value == null) return result;
        String[] parts = value.replaceFirst("^[vV]", "").split("\\.");
        for (int i = 0; i < parts.length && i < result.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*$", ""));
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }
}

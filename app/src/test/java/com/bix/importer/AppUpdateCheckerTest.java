package com.bix.importer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AppUpdateCheckerTest {
    @Test
    public void parsesGitHubReleaseTag() {
        assertEquals("1.1.5", AppUpdateChecker.latestVersionFromGitHub("{\"tag_name\":\"v1.1.5\"}"));
        assertEquals("2.0.0", AppUpdateChecker.latestVersionFromGitHub("{\"name\":\"BIX\",\"tag_name\":\"2.0.0\"}"));
        assertNull(AppUpdateChecker.latestVersionFromGitHub("{\"tag_name\":\"preview\"}"));
    }

    @Test
    public void detectsNewerSemanticVersion() {
        assertTrue(AppUpdateChecker.isNewerVersion("1.1.5", "1.1.4"));
        assertTrue(AppUpdateChecker.isNewerVersion("1.2.0", "1.1.9"));
        assertTrue(AppUpdateChecker.isNewerVersion("2.0.0", "1.9.9"));
        assertFalse(AppUpdateChecker.isNewerVersion("1.1.4", "1.1.4"));
        assertFalse(AppUpdateChecker.isNewerVersion("1.1.3", "1.1.4"));
    }

    @Test
    public void handlesPrefixedAndSuffixedVersions() {
        assertTrue(AppUpdateChecker.isNewerVersion("v1.1.10", "1.1.9"));
        assertFalse(AppUpdateChecker.isNewerVersion("1.1.4-beta", "1.1.4"));
    }
}

package com.bix.importer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImportFolderPolicyTest {
    @Test public void rejectsDownloadsAndStorageRoots() {
        assertTrue(ImportFolderPolicy.isBroadStorageRoot("Download", "primary:Download"));
        assertTrue(ImportFolderPolicy.isBroadStorageRoot("Internal storage", "primary:"));
        assertTrue(ImportFolderPolicy.isBroadStorageRoot("Documents", "primary:Documents"));
    }

    @Test public void allowsDedicatedPackFolderInsideDownloads() {
        assertFalse(ImportFolderPolicy.isBroadStorageRoot(
                "My Add-on", "primary:Download/My Add-on"));
    }
}

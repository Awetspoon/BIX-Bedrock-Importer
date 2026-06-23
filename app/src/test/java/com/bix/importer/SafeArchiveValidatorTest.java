package com.bix.importer;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SafeArchiveValidatorTest {
    @Test public void recognizesSupportedExtensionsCaseInsensitively() throws Exception {
        assertEquals(SafeArchiveValidator.Type.MCPACK, SafeArchiveValidator.Type.fromFileName("Pack.MCPACK"));
        assertEquals(SafeArchiveValidator.Type.MCADDON, SafeArchiveValidator.Type.fromFileName("bundle.mcaddon"));
        assertEquals(SafeArchiveValidator.Type.MCWORLD, SafeArchiveValidator.Type.fromFileName("world.mcworld"));
        assertEquals(SafeArchiveValidator.Type.ZIP, SafeArchiveValidator.Type.fromFileName("download.ZIP"));
    }

    @Test public void rejectsUnknownExtension() throws Exception {
        expectFailure(() -> SafeArchiveValidator.Type.fromFileName("pack.rar"));
    }

    @Test public void rejectsJavaJarWithClearError() throws Exception {
        try {
            SafeArchiveValidator.Type.fromFileName("example-mod.jar");
            fail("Expected Java Edition rejection");
        } catch (IOException expected) {
            org.junit.Assert.assertTrue(expected.getMessage().contains("Java Edition"));
        }
    }

    @Test public void validatesPackManifest() throws Exception {
        File file = zip("manifest.json", validManifest("resources"));
        SafeArchiveValidator.validate(file, SafeArchiveValidator.Type.MCPACK);
    }

    @Test public void rejectsMalformedManifest() throws Exception {
        File file = zip("manifest.json", "{}");
        expectFailure(() -> SafeArchiveValidator.validate(file, SafeArchiveValidator.Type.MCPACK));
    }

    @Test public void rejectsPackWithoutManifest() throws Exception {
        File file = zip("textures/a.png", "x");
        expectFailure(() -> SafeArchiveValidator.validate(file, SafeArchiveValidator.Type.MCPACK));
    }

    @Test public void rejectsRawPackFoldersInsideAddon() throws Exception {
        File file = File.createTempFile("bix", ".mcaddon");
        file.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("behavior_pack/manifest.json"));
            zip.write(validManifest("data").getBytes("UTF-8"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("resource_pack/manifest.json"));
            zip.write(validManifest("resources").getBytes("UTF-8"));
            zip.closeEntry();
        }
        expectFailure(() -> SafeArchiveValidator.validate(file, SafeArchiveValidator.Type.MCADDON));
    }

    @Test public void validatesCanonicalAddonWithNestedPacks() throws Exception {
        File file = File.createTempFile("bix", ".mcaddon");
        file.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("behavior.mcpack"));
            zip.write(zipBytes("manifest.json", validManifest("data")));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("resources.mcpack"));
            String resourceManifest = validManifest("resources")
                    .replace("11111111-1111-4111-8111-111111111111", "33333333-3333-4333-8333-333333333333")
                    .replace("22222222-2222-4222-8222-222222222222", "44444444-4444-4444-8444-444444444444");
            zip.write(zipBytes("manifest.json", resourceManifest));
            zip.closeEntry();
        }
        SafeArchiveValidator.validate(file, SafeArchiveValidator.Type.MCADDON);
    }

    @Test public void validatesCanonicalAddonWithNestedWorld() throws Exception {
        File file = File.createTempFile("bix", ".mcaddon");
        file.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("world.mcworld"));
            zip.write(zipBytes("level.dat", "bedrock"));
            zip.closeEntry();
        }
        SafeArchiveValidator.validate(file, SafeArchiveValidator.Type.MCADDON);
    }

    @Test public void validatesWorldRoot() throws Exception {
        File file = zip("level.dat", "bedrock");
        SafeArchiveValidator.validate(file, SafeArchiveValidator.Type.MCWORLD);
    }

    @Test public void rejectsNestedWorldRoot() throws Exception {
        File file = zip("wrapped/level.dat", "bedrock");
        expectFailure(() -> SafeArchiveValidator.validate(file, SafeArchiveValidator.Type.MCWORLD));
    }

    @Test public void rejectsTraversal() throws Exception {
        File file = zip("../manifest.json", "{}");
        expectFailure(() -> SafeArchiveValidator.validate(file, SafeArchiveValidator.Type.MCPACK));
    }

    private File zip(String name, String contents) throws Exception {
        File file = File.createTempFile("bix", ".zip");
        file.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(contents.getBytes("UTF-8"));
            zip.closeEntry();
        }
        return file;
    }

    private byte[] zipBytes(String name, String contents) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(contents.getBytes("UTF-8"));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private String validManifest(String moduleType) {
        return "{\"format_version\":2,\"header\":{" +
                "\"name\":\"BIX Test\",\"description\":\"Test\"," +
                "\"uuid\":\"11111111-1111-4111-8111-111111111111\"," +
                "\"version\":[1,0,0],\"min_engine_version\":[1,20,0]}," +
                "\"modules\":[{\"type\":\"" + moduleType + "\"," +
                "\"uuid\":\"22222222-2222-4222-8222-222222222222\"," +
                "\"version\":[1,0,0]}]}";
    }

    private void expectFailure(ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
            fail("Expected IOException");
        } catch (IOException expected) { }
    }

    private interface ThrowingRunnable { void run() throws Exception; }
}

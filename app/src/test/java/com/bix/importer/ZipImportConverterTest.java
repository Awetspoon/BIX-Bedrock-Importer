package com.bix.importer;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class ZipImportConverterTest {
    @Test public void detectsRootPack() throws Exception {
        assertEquals(SafeArchiveValidator.Type.MCPACK,
                ZipImportConverter.inspect(zip("manifest.json")).type);
    }

    @Test public void unwrapsSinglePackFolder() throws Exception {
        File source = zip("My Pack/manifest.json", "My Pack/textures/a.txt");
        ZipImportConverter.Plan plan = ZipImportConverter.inspect(source);
        assertEquals(SafeArchiveValidator.Type.MCPACK, plan.type);
        File output = File.createTempFile("converted", ".mcpack");
        ZipImportConverter.convert(source, output, plan);
        try (ZipFile result = new ZipFile(output)) {
            assertNotNull(result.getEntry("manifest.json"));
            assertNull(result.getEntry("My Pack/manifest.json"));
        }
    }

    @Test public void detectsWrappedWorld() throws Exception {
        ZipImportConverter.Plan plan = ZipImportConverter.inspect(zip("World/level.dat", "World/db/a"));
        assertEquals(SafeArchiveValidator.Type.MCWORLD, plan.type);
        assertEquals("World/", plan.stripPrefix);
    }

    @Test public void convertsCombinedFoldersIntoNestedPacks() throws Exception {
        File source = zipWithContents(
                "Download/behavior_pack/manifest.json",
                validManifest("data", "11111111-1111-4111-8111-111111111111",
                        "22222222-2222-4222-8222-222222222222"),
                "Download/resource_pack/manifest.json",
                validManifest("resources", "33333333-3333-4333-8333-333333333333",
                        "44444444-4444-4444-8444-444444444444")
        );
        ZipImportConverter.Plan plan = ZipImportConverter.inspect(source);
        assertEquals(SafeArchiveValidator.Type.MCADDON, plan.type);
        assertEquals("Download/", plan.stripPrefix);
        assertTrue(plan.bundlesLoosePacks());

        File output = File.createTempFile("converted", ".mcaddon");
        ZipImportConverter.convert(source, output, plan);
        try (ZipFile result = new ZipFile(output)) {
            ZipEntry behavior = result.getEntry("behavior_pack.mcpack");
            ZipEntry resources = result.getEntry("resource_pack.mcpack");
            assertNotNull(behavior);
            assertNotNull(resources);
            assertNestedManifest(result, behavior);
            assertNestedManifest(result, resources);
            assertNull(result.getEntry("behavior_pack/manifest.json"));
        }
        SafeArchiveValidator.validate(output, SafeArchiveValidator.Type.MCADDON);
    }

    @Test public void recognizesCanonicalAddonContainingWorld() throws Exception {
        File source = zip("example.mcworld");
        ZipImportConverter.Plan plan = ZipImportConverter.inspect(source);
        assertEquals(SafeArchiveValidator.Type.MCADDON, plan.type);
        assertFalse(plan.bundlesLoosePacks());
    }

    @Test public void rejectsUnrelatedZip() throws Exception {
        try {
            ZipImportConverter.inspect(zip("photo.jpg"));
            fail("Expected rejection");
        } catch (java.io.IOException expected) { }
    }

    @Test public void rejectsTraversal() throws Exception {
        try {
            ZipImportConverter.inspect(zip("../manifest.json"));
            fail("Expected rejection");
        } catch (java.io.IOException expected) { }
    }

    @Test public void rejectsWrappedJavaEditionPack() throws Exception {
        try {
            ZipImportConverter.inspect(zip("JavaPack/pack.mcmeta", "JavaPack/assets/a.txt"));
            fail("Expected Java Edition rejection");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("Java Edition"));
        }
    }

    private File zip(String... names) throws Exception {
        File file = File.createTempFile("input", ".zip");
        file.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("x".getBytes("UTF-8"));
                zip.closeEntry();
            }
        }
        return file;
    }

    private File zipWithContents(String... namesAndContents) throws Exception {
        File file = File.createTempFile("input", ".zip");
        file.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zip.putNextEntry(new ZipEntry(namesAndContents[i]));
                zip.write(namesAndContents[i + 1].getBytes("UTF-8"));
                zip.closeEntry();
            }
        }
        return file;
    }

    private String validManifest(String type, String headerUuid, String moduleUuid) {
        return "{\"format_version\":2,\"header\":{" +
                "\"name\":\"BIX Test\",\"description\":\"Test\"," +
                "\"uuid\":\"" + headerUuid + "\",\"version\":[1,0,0]}," +
                "\"modules\":[{\"type\":\"" + type + "\",\"uuid\":\"" +
                moduleUuid + "\",\"version\":[1,0,0]}]}";
    }

    private void assertNestedManifest(ZipFile outer, ZipEntry nested) throws Exception {
        File file = File.createTempFile("nested", ".mcpack");
        file.deleteOnExit();
        try (java.io.InputStream input = outer.getInputStream(nested);
             java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        try (ZipFile pack = new ZipFile(file)) {
            assertNotNull(pack.getEntry("manifest.json"));
        }
    }
}

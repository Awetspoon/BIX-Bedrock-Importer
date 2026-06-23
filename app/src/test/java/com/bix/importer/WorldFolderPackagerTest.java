package com.bix.importer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WorldFolderPackagerTest {
    @Test
    public void packagesLooseAddonFolderWithManifestAtRoot() throws Exception {
        FakeNode pack = folder("Loose Pack",
                file("manifest.json", "{}"),
                folder("textures", file("terrain.png", "png"))
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        WorldFolderPackager.packageFolder(pack, output);
        Map<String, byte[]> entries = unzip(output.toByteArray());
        assertTrue(entries.containsKey("manifest.json"));
        assertTrue(entries.containsKey("textures/terrain.png"));
        assertFalse(entries.containsKey("Loose Pack/manifest.json"));
    }

    @Test
    public void packagesCombinedFoldersAsNestedMcpackFiles() throws Exception {
        FakeNode behavior = folder("Behavior Pack", file("manifest.json",
                validManifest("data", "11111111-1111-4111-8111-111111111111",
                        "22222222-2222-4222-8222-222222222222")));
        FakeNode resources = folder("Resource Pack", file("manifest.json",
                validManifest("resources", "33333333-3333-4333-8333-333333333333",
                        "44444444-4444-4444-8444-444444444444")));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        WorldFolderPackager.packageAddon(Arrays.asList(behavior, resources), output);

        Map<String, byte[]> addon = unzip(output.toByteArray());
        assertEquals(2, addon.size());
        assertTrue(addon.containsKey("Behavior Pack.mcpack"));
        assertTrue(addon.containsKey("Resource Pack.mcpack"));
        assertTrue(unzip(addon.get("Behavior Pack.mcpack")).containsKey("manifest.json"));
        assertTrue(unzip(addon.get("Resource Pack.mcpack")).containsKey("manifest.json"));

        File file = File.createTempFile("combined", ".mcaddon");
        file.deleteOnExit();
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(output.toByteArray());
        }
        SafeArchiveValidator.validate(file, SafeArchiveValidator.Type.MCADDON);
    }

    @Test
    public void packagesLevelAndBundledPacksAtArchiveRoot() throws Exception {
        FakeNode world = folder("My World",
                file("level.dat", "level"),
                folder("db", file("CURRENT", "db-data")),
                folder("behavior_packs", folder("bix_test", file("manifest.json", "{}"))),
                folder("resource_packs", folder("bix_test", file("manifest.json", "{}")))
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        WorldFolderPackager.packageWorld(world, output);
        Map<String, byte[]> entries = unzip(output.toByteArray());

        assertArrayEquals(bytes("level"), entries.get("level.dat"));
        assertArrayEquals(bytes("db-data"), entries.get("db/CURRENT"));
        assertTrue(entries.containsKey("behavior_packs/bix_test/manifest.json"));
        assertTrue(entries.containsKey("resource_packs/bix_test/manifest.json"));
    }

    @Test(expected = IOException.class)
    public void rejectsFolderWithoutLevelDat() throws Exception {
        WorldFolderPackager.packageWorld(
                folder("Not a world", file("manifest.json", "{}")),
                new ByteArrayOutputStream()
        );
    }

    @Test(expected = IOException.class)
    public void rejectsUnsafeEntryName() throws Exception {
        WorldFolderPackager.packageWorld(
                folder("World", file("level.dat", "level"), file("../escape", "bad")),
                new ByteArrayOutputStream()
        );
    }

    @Test(expected = IOException.class)
    public void enforcesUncompressedByteLimit() throws Exception {
        WorldFolderPackager.packageWorld(
                folder("World", file("level.dat", "12345")),
                new ByteArrayOutputStream(),
                4,
                10,
                10
        );
    }

    @Test
    public void doesNotWrapWorldInsideSelectedFolderName() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        WorldFolderPackager.packageWorld(
                folder("Selected Folder", file("level.dat", "level")),
                output
        );

        Map<String, byte[]> entries = unzip(output.toByteArray());
        assertEquals(Arrays.asList("level.dat"), new ArrayList<>(entries.keySet()));
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[128];
                    int read;
                    while ((read = zip.read(buffer)) != -1) output.write(buffer, 0, read);
                    entries.put(entry.getName(), output.toByteArray());
                }
                zip.closeEntry();
            }
        }
        return entries;
    }

    private static FakeNode folder(String name, FakeNode... children) {
        return new FakeNode(name, true, null, Arrays.asList(children));
    }

    private static FakeNode file(String name, String value) {
        return new FakeNode(name, false, bytes(value), new ArrayList<>());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String validManifest(String type, String headerUuid, String moduleUuid) {
        return "{\"format_version\":2,\"header\":{" +
                "\"name\":\"BIX Test\",\"description\":\"Test\"," +
                "\"uuid\":\"" + headerUuid + "\",\"version\":[1,0,0]}," +
                "\"modules\":[{\"type\":\"" + type + "\",\"uuid\":\"" +
                moduleUuid + "\",\"version\":[1,0,0]}]}";
    }

    private static final class FakeNode implements WorldFolderPackager.Node {
        private final String name;
        private final boolean directory;
        private final byte[] data;
        private final List<WorldFolderPackager.Node> children;

        FakeNode(String name, boolean directory, byte[] data, List<? extends WorldFolderPackager.Node> children) {
            this.name = name;
            this.directory = directory;
            this.data = data;
            this.children = new ArrayList<>(children);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isDirectory() {
            return directory;
        }

        @Override
        public List<WorldFolderPackager.Node> listChildren() {
            return children;
        }

        @Override
        public InputStream open() {
            return new ByteArrayInputStream(data);
        }
    }
}

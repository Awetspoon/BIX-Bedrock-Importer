package com.bix.importer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class BedrockManifestValidator {
    static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    enum Kind { BEHAVIOR, RESOURCE, OTHER }

    static final class Info {
        final UUID headerUuid;
        final Kind kind;
        final Set<UUID> dependencyUuids;

        Info(UUID headerUuid, Kind kind, Set<UUID> dependencyUuids) {
            this.headerUuid = headerUuid;
            this.kind = kind;
            this.dependencyUuids = dependencyUuids;
        }
    }

    private BedrockManifestValidator() { }

    static Info validate(InputStream input) throws IOException {
        byte[] bytes = readLimited(input);
        try {
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (!root.has("format_version")) {
                throw new IOException("manifest.json has no format_version.");
            }

            JSONObject header = root.getJSONObject("header");
            requiredText(header, "name", "manifest header");
            UUID headerUuid = requiredUuid(header, "uuid", "manifest header");
            requiredVersion(header, "version", "manifest header");

            JSONArray modules = root.getJSONArray("modules");
            if (modules.length() == 0) throw new IOException("manifest.json has no modules.");

            Set<UUID> uuids = new HashSet<>();
            uuids.add(headerUuid);
            boolean behavior = false;
            boolean resource = false;
            for (int i = 0; i < modules.length(); i++) {
                JSONObject module = modules.getJSONObject(i);
                String type = requiredText(module, "type", "manifest module").toLowerCase(Locale.ROOT);
                UUID moduleUuid = requiredUuid(module, "uuid", "manifest module");
                if (!uuids.add(moduleUuid)) throw new IOException("manifest.json contains duplicate UUIDs.");
                requiredVersion(module, "version", "manifest module");
                if ("data".equals(type) || "script".equals(type)) behavior = true;
                if ("resources".equals(type)) resource = true;
            }

            JSONArray dependencies = root.optJSONArray("dependencies");
            Set<UUID> dependencyUuids = new HashSet<>();
            if (dependencies != null) {
                for (int i = 0; i < dependencies.length(); i++) {
                    JSONObject dependency = dependencies.getJSONObject(i);
                    if (dependency.has("uuid")) {
                        dependencyUuids.add(requiredUuid(dependency, "uuid", "manifest dependency"));
                    }
                    else requiredText(dependency, "module_name", "manifest dependency");
                    if (!dependency.has("version")) {
                        throw new IOException("A manifest dependency has no version.");
                    }
                    Object version = dependency.get("version");
                    if (version instanceof JSONArray) validateVersion((JSONArray) version, "manifest dependency");
                    else if (!(version instanceof String) || ((String) version).trim().isEmpty()) {
                        throw new IOException("A manifest dependency has an invalid version.");
                    }
                }
            }

            Kind kind = behavior && !resource ? Kind.BEHAVIOR
                    : resource && !behavior ? Kind.RESOURCE : Kind.OTHER;
            return new Info(headerUuid, kind, dependencyUuids);
        } catch (JSONException e) {
            throw new IOException("manifest.json is malformed or missing required fields.");
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_MANIFEST_BYTES) {
                throw new IOException("manifest.json is too large.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String requiredText(JSONObject object, String key, String area) throws JSONException, IOException {
        String value = object.getString(key).trim();
        if (value.isEmpty()) throw new IOException(area + " has an empty " + key + ".");
        return value;
    }

    private static UUID requiredUuid(JSONObject object, String key, String area) throws JSONException, IOException {
        try {
            return UUID.fromString(requiredText(object, key, area));
        } catch (IllegalArgumentException e) {
            throw new IOException(area + " has an invalid UUID.");
        }
    }

    private static void requiredVersion(JSONObject object, String key, String area) throws JSONException, IOException {
        validateVersion(object.getJSONArray(key), area);
    }

    private static void validateVersion(JSONArray version, String area) throws JSONException, IOException {
        if (version.length() != 3) throw new IOException(area + " version must contain three numbers.");
        for (int i = 0; i < 3; i++) {
            if (version.getInt(i) < 0) throw new IOException(area + " version cannot contain negative numbers.");
        }
    }
}

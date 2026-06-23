package com.bix.importer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class SharedFileProvider extends ContentProvider {
    private static final String ROOT_IMPORTS = "shared_imports";
    private static final String ROOT_WORLDS = "shared_worlds";

    static Uri uriForFile(Context context, File file) throws IOException {
        File cache = context.getCacheDir().getCanonicalFile();
        File canonical = file.getCanonicalFile();
        File imports = new File(cache, ROOT_IMPORTS).getCanonicalFile();
        File worlds = new File(cache, ROOT_WORLDS).getCanonicalFile();
        String root;
        if (canonical.getParentFile().equals(imports)) root = ROOT_IMPORTS;
        else if (canonical.getParentFile().equals(worlds)) root = ROOT_WORLDS;
        else throw new IOException("Refused to share a file outside temporary import storage.");
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + ".files")
                .appendPath(root).appendPath(canonical.getName()).build();
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return "application/octet-stream";
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".mcpack")) return "application/vnd.minecraft.mcpack";
        if (lower.endsWith(".mcaddon")) return "application/vnd.minecraft.mcaddon";
        if (lower.endsWith(".mcworld")) return "application/vnd.minecraft.mcworld";
        return "application/octet-stream";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file;
        try { file = resolve(uri); } catch (FileNotFoundException e) { return null; }
        String[] columns = projection == null
                ? new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE } : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(file.getName());
            else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
            else row.add(null);
        }
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only provider");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (getContext() == null || uri.getPathSegments().size() != 2) throw new FileNotFoundException();
        String root = uri.getPathSegments().get(0);
        String name = uri.getPathSegments().get(1);
        if ((!ROOT_IMPORTS.equals(root) && !ROOT_WORLDS.equals(root))
                || name.isEmpty() || name.contains("/") || name.contains("\\")) {
            throw new FileNotFoundException();
        }
        try {
            File directory = new File(getContext().getCacheDir(), root).getCanonicalFile();
            File file = new File(directory, name).getCanonicalFile();
            if (!directory.equals(file.getParentFile()) || !file.isFile()) throw new FileNotFoundException();
            return file;
        } catch (IOException e) {
            throw new FileNotFoundException();
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only"); }
}

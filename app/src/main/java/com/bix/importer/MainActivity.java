package com.bix.importer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.DocumentsContract;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_ADDON = 1001;
    private static final int REQUEST_PICK_LEVEL = 1002;
    private static final String MINECRAFT_PACKAGE = "com.mojang.minecraftpe";
    private static final String STATE_ADDON_URI = "addon_uri";
    private static final String STATE_ADDON_NAME = "addon_name";
    private static final String STATE_LEVEL_URI = "level_uri";
    private static final long SHARED_FILE_TTL_MS = 24L * 60L * 60L * 1000L;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private TextView statusText;
    private TextView addonText;
    private TextView levelText;
    private Button addonImportButton;
    private Button levelImportButton;
    private Button chooseButton;
    private ProgressBar importProgress;
    private TextView progressText;
    private Uri addonUri;
    private Uri levelUri;
    private String addonName = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        View content = buildUi();
        setContentView(content);
        applySystemBarInsets(content);
        restoreState(state);
        updateButtons();
        checkMinecraft();
        clearExpiredSharedFiles();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (addonUri != null) {
            out.putString(STATE_ADDON_URI, addonUri.toString());
            out.putString(STATE_ADDON_NAME, addonName);
        }
        if (levelUri != null) out.putString(STATE_LEVEL_URI, levelUri.toString());
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101510);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        scroll.addView(root);

        root.addView(text("BIX", 29, 0xFFF2F2EA, true));
        TextView subtitle = text("Bedrock Import eXpress", 17, 0xFFD9B84A, true);
        subtitle.setPadding(0, dp(8), 0, dp(12));
        root.addView(subtitle);
        statusText = text("", 15, 0xFFF2F2EA, false);
        root.addView(statusText);

        root.addView(note("BIX validates the content and gives Minecraft temporary read access managed by Android."));
        root.addView(section("Import into Minecraft"));
        root.addView(note("Choose a Bedrock add-on, world, or ZIP download. BIX checks it and opens Minecraft to import it. Java Edition mods are not supported."));
        addonText = text("Nothing selected.", 14, 0xFFF2F2EA, false);
        root.addView(addonText);
        levelText = addonText;
        chooseButton = button("Choose file, ZIP, or folder", v -> showPickerChoice());
        root.addView(chooseButton);
        addonImportButton = button("Import with Minecraft", v -> importSelected());
        levelImportButton = addonImportButton;
        root.addView(addonImportButton);

        importProgress = new ProgressBar(this);
        importProgress.setIndeterminate(true);
        importProgress.setVisibility(View.GONE);
        root.addView(importProgress);
        progressText = text("", 15, 0xFFD9B84A, true);
        progressText.setGravity(Gravity.CENTER);
        progressText.setPadding(0, dp(8), 0, dp(8));
        progressText.setVisibility(View.GONE);
        root.addView(progressText);

        root.addView(section("Data safety"));
        root.addView(note("BIX cannot edit or delete Minecraft packs, worlds, or global settings. Minecraft controls the final import."));
        root.addView(note("Files are processed on this device only and shared with Minecraft only when you tap Import. BIX is independent and is not affiliated with Mojang or Microsoft."));
        return scroll;
    }

    private void restoreState(Bundle state) {
        if (state == null) return;
        String savedAddon = state.getString(STATE_ADDON_URI);
        if (savedAddon != null) {
            addonUri = Uri.parse(savedAddon);
            addonName = state.getString(STATE_ADDON_NAME, "selected.mcpack");
            addonText.setText("Selected: " + addonName);
        }
        String savedLevel = state.getString(STATE_LEVEL_URI);
        if (savedLevel != null) {
            levelUri = Uri.parse(savedLevel);
            updateLevelLabel();
        }
    }

    private void pickAddon() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_PICK_ADDON);
    }

    private void showPickerChoice() {
        new AlertDialog.Builder(this)
                .setTitle("What did you download?")
                .setMessage("Most add-ons are files: .mcaddon, .mcpack, .mcworld, or .zip. Choose File / ZIP for normal downloads. Choose Extracted Folder only if you already unzipped it and can see manifest.json or level.dat inside.")
                .setPositiveButton("File / ZIP", (dialog, which) -> pickAddon())
                .setNeutralButton("Extracted Folder", (dialog, which) -> pickLevel())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void importSelected() {
        if (addonUri != null) prepareAddonImport();
        else if (levelUri != null) prepareLevelImport();
    }

    private void pickLevel() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_LEVEL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        persistReadPermission(uri, data.getFlags());

        if (requestCode == REQUEST_PICK_ADDON) {
            String name = displayName(uri);
            try {
                SafeArchiveValidator.Type.fromFileName(name);
                addonUri = uri;
                levelUri = null;
                addonName = name;
                addonText.setText("Selected file: " + name);
            } catch (IOException e) {
                addonUri = null;
                addonName = "";
                addonText.setText("No file selected.");
                toast(e.getMessage());
            }
        } else if (requestCode == REQUEST_PICK_LEVEL) {
            try {
                FolderSelection selection = resolveImportSelection(uri);
                levelUri = uri;
                addonUri = null;
                addonName = "";
                levelText.setText("Selected folder: " + selection.displayName());
            } catch (IOException e) {
                levelUri = null;
                levelText.setText("No level folder selected.");
                toast(e.getMessage());
            }
        }
        updateButtons();
    }

    private void prepareAddonImport() {
        if (addonUri == null) return;
        setBusy(addonImportButton, true, "Checking and preparing…", "Import with Minecraft");
        Uri source = addonUri;
        String name = addonName;
        ioExecutor.execute(() -> {
            File shared = null;
            try {
                SafeArchiveValidator.Type selectedType = SafeArchiveValidator.Type.fromFileName(name);
                shared = copyToSharedFile(source, name, "shared_imports");
                ZipImportConverter.Plan plan = ZipImportConverter.inspect(shared);
                SafeArchiveValidator.Type type = plan.type;
                if (selectedType == SafeArchiveValidator.Type.ZIP
                        || selectedType != plan.type
                        || !plan.stripPrefix.isEmpty()
                        || plan.bundlesLoosePacks()) {
                    File converted = uniqueFile(shared.getParentFile(),
                            safeBaseName(name.substring(0,
                                    name.length() - selectedType.extension.length()))
                                    + "_prepared" + plan.type.extension);
                    ZipImportConverter.convert(shared, converted, plan);
                    shared.delete();
                    shared = converted;
                }
                SafeArchiveValidator.validate(shared, type);
                Uri contentUri = SharedFileProvider.uriForFile(this, shared);
                SafeArchiveValidator.Type finalType = type;
                File finalShared = shared;
                runOnUiThread(() -> {
                    setBusy(addonImportButton, false, "", "Import with Minecraft");
                    launchMinecraft(contentUri, finalType.mimeType, finalShared);
                });
            } catch (Exception e) {
                if (shared != null) shared.delete();
                runOnUiThread(() -> {
                    setBusy(addonImportButton, false, "", "Import with Minecraft");
                    toast("Import blocked: " + safeMessage(e));
                });
            }
        });
    }

    private void prepareLevelImport() {
        if (levelUri == null) return;
        setBusy(levelImportButton, true, "Checking and preparing…", "Import with Minecraft");
        Uri selected = levelUri;
        ioExecutor.execute(() -> {
            File archive = null;
            try {
                FolderSelection selection = resolveImportSelection(selected);
                SafeArchiveValidator.Type type = selection.type;
                File directory = sharedDirectory(type == SafeArchiveValidator.Type.MCWORLD
                        ? "shared_worlds" : "shared_imports");
                archive = uniqueFile(directory,
                        safeBaseName(selection.displayName()) + type.extension);
                try (OutputStream output = new FileOutputStream(archive)) {
                    if (type == SafeArchiveValidator.Type.MCWORLD) {
                        WorldFolderPackager.packageWorld(new DocumentNode(selection.root), output);
                    } else if (type == SafeArchiveValidator.Type.MCPACK) {
                        WorldFolderPackager.packageFolder(new DocumentNode(selection.root), output);
                    } else {
                        List<WorldFolderPackager.Node> packs = new ArrayList<>();
                        for (TreeNode pack : selection.packs) packs.add(new DocumentNode(pack));
                        WorldFolderPackager.packageAddon(packs, output);
                    }
                } catch (Exception e) {
                    archive.delete();
                    throw e;
                }
                SafeArchiveValidator.validate(archive, type);
                Uri contentUri = SharedFileProvider.uriForFile(this, archive);
                File finalArchive = archive;
                runOnUiThread(() -> {
                    setBusy(levelImportButton, false, "", "Import with Minecraft");
                    launchMinecraft(contentUri, type.mimeType, finalArchive);
                });
            } catch (Exception e) {
                if (archive != null) archive.delete();
                runOnUiThread(() -> {
                    setBusy(levelImportButton, false, "", "Import with Minecraft");
                    toast("Import blocked: " + safeMessage(e));
                });
            }
        });
    }

    private void launchMinecraft(Uri uri, String mime, File sharedFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mime);
        intent.setClipData(ClipData.newRawUri("BIX import", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setPackage(MINECRAFT_PACKAGE);
        try {
            startActivity(intent);
            addonUri = null;
            levelUri = null;
            addonName = "";
            addonText.setText("Sent to Minecraft. Ready for another import.");
            updateButtons();
        } catch (ActivityNotFoundException e) {
            sharedFile.delete();
            toast("Minecraft did not accept this Bedrock file type.");
        } catch (Exception e) {
            sharedFile.delete();
            toast("Could not open Minecraft: " + safeMessage(e));
        }
    }

    private File copyToSharedFile(Uri uri, String name, String child) throws IOException {
        File output = uniqueFile(sharedDirectory(child), safeFileName(name));
        try (InputStream input = getContentResolver().openInputStream(uri);
             OutputStream stream = new FileOutputStream(output)) {
            if (input == null) throw new IOException("Android could not read the selected file.");
            copyLimited(input, stream, SafeArchiveValidator.MAX_ARCHIVE_BYTES);
        } catch (Exception e) {
            output.delete();
            throw e;
        }
        return output;
    }

    private File sharedDirectory(String child) throws IOException {
        File directory = new File(getCacheDir(), child);
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory()) {
            throw new IOException("Could not create temporary import storage.");
        }
        return directory;
    }

    private void clearExpiredSharedFiles() {
        ioExecutor.execute(() -> {
            long cutoff = System.currentTimeMillis() - SHARED_FILE_TTL_MS;
            clearExpiredDirectory(new File(getCacheDir(), "shared_imports"), cutoff);
            clearExpiredDirectory(new File(getCacheDir(), "shared_worlds"), cutoff);
        });
    }

    private void clearExpiredDirectory(File directory, long cutoff) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.lastModified() < cutoff) file.delete();
            }
        }
    }

    private FolderSelection resolveImportSelection(Uri uri) throws IOException {
        TreeNode selected = treeFromUri(uri);
        if (containsJavaMetadata(selected)) throw javaEditionError();
        boolean rootWorld = containsLevelDat(selected);
        boolean rootPack = containsManifest(selected);
        if (rootWorld || rootPack) {
            if (ImportFolderPolicy.isBroadStorageRoot(selected.getName(), selected.documentId)) {
                throw new IOException("Choose a dedicated add-on or world folder, not Downloads or a storage root.");
            }
            return rootWorld ? FolderSelection.world(selected) : FolderSelection.pack(selected);
        }

        List<TreeNode> packFolders = new ArrayList<>();
        List<TreeNode> worldFolders = new ArrayList<>();
        for (TreeNode child : selected.children()) {
            if (!child.isDirectory()) continue;
            if (containsJavaMetadata(child)) throw javaEditionError();
            if (containsLevelDat(child)) worldFolders.add(child);
            else if (containsManifest(child)) packFolders.add(child);
        }
        if (worldFolders.size() > 1 || (!worldFolders.isEmpty() && !packFolders.isEmpty())) {
            throw new IOException("Choose one specific world folder.");
        }
        if (worldFolders.size() == 1) return FolderSelection.world(worldFolders.get(0));
        if (packFolders.size() == 1) return FolderSelection.pack(packFolders.get(0));
        if (packFolders.size() >= 2) return FolderSelection.addon(selected, packFolders);
        throw new IOException("No manifest.json or level.dat was found in this folder.");
    }

    private boolean containsManifest(TreeNode folder) throws IOException {
        for (TreeNode child : folder.children()) {
            if (!child.isDirectory() && "manifest.json".equalsIgnoreCase(child.getName())) return true;
        }
        return false;
    }

    private boolean containsJavaMetadata(TreeNode folder) throws IOException {
        for (TreeNode child : folder.children()) {
            if (!child.isDirectory() && "pack.mcmeta".equalsIgnoreCase(child.getName())) return true;
        }
        return false;
    }

    private IOException javaEditionError() {
        return new IOException("Java Edition content detected. It cannot run in Minecraft Bedrock on Android.");
    }

    private boolean containsLevelDat(TreeNode folder) throws IOException {
        for (TreeNode child : folder.children()) {
            if (!child.isDirectory() && "level.dat".equals(child.getName())) return true;
        }
        return false;
    }

    private TreeNode treeFromUri(Uri treeUri) throws IOException {
        try {
            String id = DocumentsContract.getTreeDocumentId(treeUri);
            Uri document = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
            String[] columns = { DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE };
            try (Cursor cursor = getContentResolver().query(document, columns, null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()) throw new IOException("The selected folder is not readable.");
                TreeNode node = new TreeNode(document, id, cursor.getString(0), cursor.getString(1));
                if (!node.isDirectory()) throw new IOException("The selected item is not a folder.");
                return node;
            }
        } catch (SecurityException | IllegalArgumentException e) {
            throw new IOException("The selected folder is not readable.");
        }
    }

    private void updateLevelLabel() {
        try {
            levelText.setText("Selected folder: " + resolveImportSelection(levelUri).displayName());
        } catch (IOException e) {
            levelUri = null;
            levelText.setText("No level folder selected.");
        }
    }

    private void persistReadPermission(Uri uri, int flags) {
        if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) return value;
                }
            }
        } catch (Exception ignored) { }
        String value = uri.getLastPathSegment();
        return value == null ? "selected_file" : value.substring(value.lastIndexOf('/') + 1);
    }

    private void checkMinecraft() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(MINECRAFT_PACKAGE, 0);
            statusText.setText("Minecraft " + info.versionName + " detected.");
        } catch (PackageManager.NameNotFoundException e) {
            statusText.setText("Minecraft Bedrock is not installed.");
        }
    }

    private void updateButtons() {
        addonImportButton.setEnabled(addonUri != null || levelUri != null);
    }

    private void setBusy(Button button, boolean busy, String busyText, String readyText) {
        button.setEnabled(!busy);
        button.setText(busy ? busyText : readyText);
        chooseButton.setEnabled(!busy);
        importProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        progressText.setText(busy
                ? "Preparing your import…\nLarge worlds can take a few minutes. Please keep this app open."
                : "");
        progressText.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private String safeFileName(String name) throws IOException {
        if (name == null || name.contains("/") || name.contains("\\") || name.contains("\0")) {
            throw new IOException("The selected file name is unsafe.");
        }
        return name;
    }

    private String safeBaseName(String value) {
        String cleaned = value == null ? "Bedrock_World" : value.replaceAll("[^A-Za-z0-9._ -]", "_");
        return cleaned.isEmpty() ? "Bedrock_World" : cleaned;
    }

    private File uniqueFile(File directory, String name) {
        File candidate = new File(directory, name);
        if (!candidate.exists()) return candidate;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        int suffix = 2;
        do {
            candidate = new File(directory, base + " " + suffix++ + extension);
        } while (candidate.exists());
        return candidate;
    }

    private void copyLimited(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("The selected file is too large.");
            output.write(buffer, 0, read);
        }
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? "unknown error" : e.getMessage();
    }

    private TextView section(String value) {
        TextView view = text("\n" + value, 20, 0xFFD9B84A, true);
        view.setPadding(0, dp(12), 0, dp(6));
        return view;
    }

    private TextView note(String value) {
        TextView view = text(value, 14, 0xFFE8E1BF, false);
        view.setPadding(0, dp(5), 0, dp(8));
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String value, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private void applySystemBarInsets(View content) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) getWindow().setDecorFitsSystemWindows(false);
        int left = content.getPaddingLeft(), top = content.getPaddingTop();
        int right = content.getPaddingRight(), bottom = content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            int l = insets.getSystemWindowInsetLeft(), t = insets.getSystemWindowInsetTop();
            int r = insets.getSystemWindowInsetRight(), b = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    l = Math.max(l, cutout.getSafeInsetLeft()); t = Math.max(t, cutout.getSafeInsetTop());
                    r = Math.max(r, cutout.getSafeInsetRight()); b = Math.max(b, cutout.getSafeInsetBottom());
                }
            }
            view.setPadding(left + l, top + t, right + r, bottom + b);
            return insets;
        });
        content.requestApplyInsets();
    }

    private final class DocumentNode implements WorldFolderPackager.Node {
        private final TreeNode file;
        DocumentNode(TreeNode file) { this.file = file; }
        public String getName() { return file.getName(); }
        public boolean isDirectory() { return file.isDirectory(); }
        public List<WorldFolderPackager.Node> listChildren() throws IOException {
            List<WorldFolderPackager.Node> result = new ArrayList<>();
            for (TreeNode child : file.children()) result.add(new DocumentNode(child));
            return result;
        }
        public InputStream open() throws IOException {
            InputStream input = getContentResolver().openInputStream(file.uri);
            if (input == null) throw new IOException("Could not read " + file.getName());
            return input;
        }
    }

    private static final class FolderSelection {
        final SafeArchiveValidator.Type type;
        final TreeNode root;
        final List<TreeNode> packs;

        private FolderSelection(SafeArchiveValidator.Type type, TreeNode root, List<TreeNode> packs) {
            this.type = type;
            this.root = root;
            this.packs = packs;
        }

        static FolderSelection world(TreeNode root) {
            return new FolderSelection(SafeArchiveValidator.Type.MCWORLD, root, new ArrayList<>());
        }

        static FolderSelection pack(TreeNode root) {
            return new FolderSelection(SafeArchiveValidator.Type.MCPACK, root, new ArrayList<>());
        }

        static FolderSelection addon(TreeNode selected, List<TreeNode> packs) {
            return new FolderSelection(SafeArchiveValidator.Type.MCADDON, selected, packs);
        }

        String displayName() {
            return root.getName();
        }
    }

    private final class TreeNode {
        final Uri uri;
        final String documentId;
        final String name;
        final String mime;

        TreeNode(Uri uri, String documentId, String name, String mime) {
            this.uri = uri;
            this.documentId = documentId;
            this.name = name;
            this.mime = mime;
        }

        static final String DIRECTORY = DocumentsContract.Document.MIME_TYPE_DIR;

        String getName() { return name; }
        boolean isDirectory() { return DIRECTORY.equals(mime); }

        List<TreeNode> children() throws IOException {
            if (!isDirectory()) throw new IOException("A selected world entry is not a folder.");
            String id = DocumentsContract.getDocumentId(uri);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id);
            String[] columns = { DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE };
            List<TreeNode> result = new ArrayList<>();
            try (Cursor cursor = getContentResolver().query(children, columns, null, null, null)) {
                if (cursor == null) throw new IOException("Could not read the selected folder.");
                while (cursor.moveToNext()) {
                    String childId = cursor.getString(0);
                    Uri child = DocumentsContract.buildDocumentUriUsingTree(uri, childId);
                    result.add(new TreeNode(child, childId, cursor.getString(1), cursor.getString(2)));
                }
            } catch (SecurityException e) {
                throw new IOException("Android no longer permits access to this folder.");
            }
            return result;
        }
    }

}

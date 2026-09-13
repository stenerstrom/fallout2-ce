package com.alexbatalov.fallout2ce;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SettingsRepository {
    private final Context context;
    SettingsRepository(Context context) { this.context = context; }

    File gameDirectory() throws IOException {
        File root = context.getExternalFilesDir(null);
        if (root == null) throw new IOException("Spelmappen är inte tillgänglig.");
        return root;
    }

    boolean gameRunning() throws IOException {
        try (GameSession lock = GameSession.tryAcquire(context.getFilesDir())) { return lock == null; }
    }

    boolean hasGameData() throws IOException {
        File root = gameDirectory();
        return !new File(root, BundledGameExtractor.IN_PROGRESS).exists()
                && new File(root, "master.dat").isFile() && new File(root, "critter.dat").isFile() && new File(root, "ce.dat").isFile();
    }

    File configFile(String relative) throws IOException {
        File root = gameDirectory().getCanonicalFile();
        File file = new File(root, relative).getCanonicalFile();
        String lower = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (!file.getPath().startsWith(root.getPath() + File.separator)
                || !(lower.endsWith(".cfg") || lower.endsWith(".ini"))) {
            throw new IOException("Ogiltig konfigurationsfil.");
        }
        return file;
    }

    String read(String relative) throws IOException {
        AtomicFile file = new AtomicFile(configFile(relative));
        // AtomicFile also recovers an interrupted previous write.
        if (!file.getBaseFile().exists()
                && !new File(file.getBaseFile().getPath() + ".bak").exists()) return "";
        return new String(file.readFully(), StandardCharsets.UTF_8);
    }

    void saveChanges(String relative, List<IniDocument.Entry> changes) throws IOException {
        try (GameSession lock = GameSession.tryAcquire(context.getFilesDir())) {
            if (lock == null) throw new IOException("Avsluta spelet via spelets meny innan du ändrar inställningarna.");
            // Re-read to preserve changes made since the editor opened.
            IniDocument document = new IniDocument(read(relative));
            for (IniDocument.Entry change : changes) document.set(change.section, change.key, change.value);
            if (relative.equals("fallout2.cfg")) SettingsValidation.validateScreen(document);
            write(relative, document.toString());
        }
    }

    private void write(String relative, String text) throws IOException {
        File target = configFile(relative);
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Kunde inte skapa konfigurationsmappen.");
        String original = read(relative);
        // Keep a user-recoverable previous version, in addition to AtomicFile's crash recovery.
        atomicWrite(new File(context.getFilesDir(), "config-backup-" + relative.replace('/', '_')), original);
        atomicWrite(target, text);
    }

    private static void atomicWrite(File target, String text) throws IOException {
        AtomicFile file = new AtomicFile(target);
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(text.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (IOException error) {
            if (stream != null) file.failWrite(stream);
            throw error;
        }
    }

    void saveText(String relative, String text) throws IOException {
        try (GameSession lock = GameSession.tryAcquire(context.getFilesDir())) {
            if (lock == null) throw new IOException("Avsluta spelet via spelets meny innan du ändrar inställningarna.");
            if (relative.equals("fallout2.cfg")) SettingsValidation.validateScreen(new IniDocument(text));
            write(relative, text);
        }
    }

    List<String> configFiles() throws IOException {
        List<String> files = new ArrayList<>();
        collect(gameDirectory(), gameDirectory(), files);
        files.sort(Comparator.naturalOrder());
        if (!files.contains("fallout2.cfg")) files.add(0, "fallout2.cfg");
        return files;
    }

    private void collect(File root, File directory, List<String> files) throws IOException {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File file : children) {
            String name = file.getName().toLowerCase(java.util.Locale.ROOT);
            if (!file.getCanonicalPath().equals(file.getAbsolutePath())) continue;
            if (file.isDirectory()) {
                // Configuration roots only: never traverse saves, backups or original disc files.
                String relative = file.getPath().substring(root.getPath().length() + 1);
                if (relative.equals("mods") || relative.startsWith("mods/")
                        || relative.equals("data") || relative.equals("data/config")
                        || relative.startsWith("data/config/")) collect(root, file, files);
            } else if (name.endsWith(".ini") || name.endsWith(".cfg")) {
                String relative = file.getPath().substring(root.getPath().length() + 1);
                configFile(relative); // Reject links escaping the app's data directory.
                files.add(relative);
            }
        }
    }
}

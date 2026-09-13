package com.alexbatalov.fallout2ce;

import java.io.*;
import java.util.*;
import java.util.zip.*;

// Portable save archives. Callers must hold GameSession for export/restore/recovery.
final class SaveGameArchive {
    static final String JOURNAL = ".save-restore.properties";
    static final long MAX_BYTES = 1024L * 1024 * 1024;
    static final int MAX_FILES = 20000;
    private static final String STAGE = ".save-restore-stage-";
    private static final String BACKUP = ".save-restore-backup-";

    static File locate(File gameRoot, String patches) throws IOException {
        File root = gameRoot.getCanonicalFile();
        String relative = patches.replace('\\', '/');
        if (relative.isEmpty()) relative = "data";
        checkedRelative(relative);
        File folder = root;
        for (String part : relative.split("/")) {
            File next = new File(folder, part);
            File[] children = folder.listFiles();
            if (children != null) for (File child : children)
                if (child.getName().equalsIgnoreCase(part)) { next = child; break; }
            folder = next;
        }
        File saves = new File(folder, "SAVEGAME");
        File[] children = folder.listFiles();
        if (children != null) for (File child : children)
            if (child.getName().equalsIgnoreCase("SAVEGAME")) { saves = child; break; }
        within(root, saves);
        return saves;
    }

    static void exportTo(File saves, String profile, OutputStream output) throws IOException {
        if (!saves.isDirectory()) throw new IOException("There are no saved games to export yet.");
        List<File> files = new ArrayList<>();
        collect(saves, saves, files);
        if (files.isEmpty()) throw new IOException("There are no saved games to export yet.");
        Properties info = new Properties();
        info.setProperty("format", "wasteland-saves-1");
        info.setProperty("profile", profile);
        info.setProperty("files", Integer.toString(files.size()));
        long total = 0;
        for (File file : files) {
            total += file.length();
            if (total > MAX_BYTES) throw new IOException("The saved games exceed the 1 GB export limit.");
        }
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("collection-save.properties"));
            info.store(zip, "Wasteland Collection saved games");
            zip.closeEntry();
            byte[] buffer = new byte[65536];
            for (File file : files) {
                String relative = relative(saves.getCanonicalFile(), file);
                checkedRelative(relative);
                zip.putNextEntry(new ZipEntry("saves/" + relative));
                try (InputStream input = new FileInputStream(file)) {
                    int n;
                    while ((n = input.read(buffer)) != -1) zip.write(buffer, 0, n);
                }
                zip.closeEntry();
            }
        }
    }

    private static void collect(File root, File folder, List<File> result) throws IOException {
        File[] entries = folder.listFiles();
        if (entries == null) throw new IOException("Could not read the saved games.");
        Arrays.sort(entries, Comparator.comparing(File::getName));
        for (File file : entries) {
            within(root.getCanonicalFile(), file);
            if (file.isDirectory()) collect(root, file, result);
            else if (file.isFile()) {
                result.add(file);
                if (result.size() > MAX_FILES) throw new IOException("Too many save files.");
            }
        }
    }

    static void restore(File gameRoot, File destination, String profile, InputStream input) throws IOException {
        File root = gameRoot.getCanonicalFile();
        recover(root);
        within(root, destination);
        if (!destination.getName().equalsIgnoreCase("SAVEGAME")) throw new IOException("Invalid save folder.");
        File parent = destination.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not prepare the save folder.");
        String id = System.currentTimeMillis() + "-" + UUID.randomUUID();
        File stage = new File(parent, STAGE + id);
        File backup = new File(parent, BACKUP + id);
        if (!stage.mkdir()) throw new IOException("Could not stage the saved games.");
        boolean journalWritten = false;
        try {
            unpack(stage, profile, input);
            Properties journal = new Properties();
            journal.setProperty("destination", relative(root, destination));
            journal.setProperty("stage", relative(root, stage));
            journal.setProperty("backup", relative(root, backup));
            File temporary = new File(root, JOURNAL + ".tmp");
            try (FileOutputStream stream = new FileOutputStream(temporary)) {
                journal.store(stream, "Pending save restore");
                stream.getFD().sync();
            }
            if (!temporary.renameTo(new File(root, JOURNAL))) throw new IOException("Could not record the restore.");
            journalWritten = true;
            recover(root);
        } finally {
            if (!journalWritten) removeTree(stage);
        }
    }

    private static void unpack(File stage, String profile, InputStream input) throws IOException {
        Set<String> paths = new HashSet<>();
        Properties info = null;
        int count = 0;
        long total = 0;
        boolean hasSave = false;
        byte[] buffer = new byte[65536];
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String path = entry.getName();
                if (entry.isDirectory()) throw new IOException("Unexpected directory entry in save archive.");
                checkedRelative(path);
                if (!paths.add(path.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate file in save archive.");
                if (path.equals("collection-save.properties")) {
                    ByteArrayOutputStream meta = new ByteArrayOutputStream();
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        if (meta.size() + n > 16384) throw new IOException("Invalid save archive metadata.");
                        meta.write(buffer, 0, n);
                    }
                    info = new Properties();
                    info.load(new ByteArrayInputStream(meta.toByteArray()));
                } else {
                    if (!path.startsWith("saves/")) throw new IOException("Unexpected file in save archive.");
                    String relative = path.substring(6);
                    checkedRelative(relative);
                    File target = new File(stage, relative);
                    within(stage.getCanonicalFile(), target);
                    if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs())
                        throw new IOException("Could not prepare a saved game.");
                    if (++count > MAX_FILES) throw new IOException("Too many save files.");
                    try (FileOutputStream stream = new FileOutputStream(target)) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) {
                            total += n;
                            if (total > MAX_BYTES) throw new IOException("The archive exceeds the 1 GB restore limit.");
                            stream.write(buffer, 0, n);
                        }
                        stream.getFD().sync();
                    }
                    if (relative.matches("(?i)SLOT[0-9]+/SAVE\\.DAT") && target.length() > 0) hasSave = true;
                }
                zip.closeEntry(); // checks the ZIP CRC before committing anything
            }
        }
        if (info == null || !"wasteland-saves-1".equals(info.getProperty("format")))
            throw new IOException("This is not a Wasteland Collection save archive.");
        if (!profile.equals(info.getProperty("profile"))) throw new IOException("This archive belongs to a different game. Select that game in the library first.");
        if (!Integer.toString(count).equals(info.getProperty("files")) || !hasSave)
            throw new IOException("The save archive is incomplete.");
    }

    static void recover(File gameRoot) throws IOException {
        File root = gameRoot.getCanonicalFile();
        File file = new File(root, JOURNAL);
        if (!file.exists()) return;
        within(root, file);
        if (file.length() > 16384) throw new IOException("Invalid save restore journal.");
        Properties journal = new Properties();
        try (InputStream input = new FileInputStream(file)) { journal.load(input); }
        File destination = journalPath(root, journal, "destination");
        File stage = journalPath(root, journal, "stage");
        File backup = journalPath(root, journal, "backup");
        if (!destination.getName().equalsIgnoreCase("SAVEGAME")
                || !stage.getName().startsWith(STAGE) || !backup.getName().startsWith(BACKUP)
                || !destination.getParentFile().equals(stage.getParentFile())
                || !destination.getParentFile().equals(backup.getParentFile()))
            throw new IOException("Invalid save restore paths.");
        if (stage.exists()) {
            if (!stage.isDirectory()) throw new IOException("Invalid staged saves.");
            if (destination.exists()) {
                if (!destination.isDirectory() || backup.exists() || !destination.renameTo(backup))
                    throw new IOException("Could not keep the existing saves as a backup.");
            }
            if (!stage.renameTo(destination)) {
                if (backup.exists() && !destination.exists()) backup.renameTo(destination);
                throw new IOException("Could not activate the restored saves. Try again from the library.");
            }
        } else if (!destination.exists()) {
            if (!backup.isDirectory() || !backup.renameTo(destination))
                throw new IOException("The interrupted restore needs recovery.");
        }
        if (!file.delete()) throw new IOException("Could not finish the save restore journal.");
    }

    static File latestBackup(File destination) {
        File[] files = destination.getParentFile().listFiles(file -> file.isDirectory() && file.getName().startsWith(BACKUP));
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files[files.length - 1];
    }

    private static File journalPath(File root, Properties journal, String key) throws IOException {
        String path = journal.getProperty(key);
        checkedRelative(path);
        File file = new File(root, path).getAbsoluteFile();
        within(root, file);
        return file;
    }

    private static String relative(File root, File file) throws IOException {
        within(root, file);
        String path = file.getAbsolutePath().substring(root.getPath().length() + 1).replace(File.separatorChar, '/');
        checkedRelative(path);
        return path;
    }

    private static void checkedRelative(String path) throws IOException {
        if (path == null || path.isEmpty() || path.length() > 512 || path.startsWith("/")
                || path.contains("\\") || path.contains(":") || path.indexOf('\0') >= 0)
            throw new IOException("Invalid path in save archive.");
        for (String part : path.split("/", -1))
            if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Invalid path in save archive.");
    }

    private static void within(File root, File file) throws IOException {
        File canonical = file.getCanonicalFile();
        if (!canonical.getPath().startsWith(root.getPath() + File.separator)
                || !canonical.equals(file.getAbsoluteFile()))
            throw new IOException("Saved games must stay inside their game folder.");
    }

    private static void removeTree(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Could not clean the restore staging folder.");
            for (File child : children) removeTree(child);
        }
        if (!file.delete()) throw new IOException("Could not clean the restore staging folder.");
    }
}

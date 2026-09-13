package com.alexbatalov.fallout2ce;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class SaveArchiveTests {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Save archive check " + checks); }
    private interface Work { void run() throws Exception; }
    private static void rejects(Work work) throws Exception {
        try { work.run(); throw new AssertionError("Expected a rejected archive"); }
        catch (IOException expected) { checks++; }
    }
    private static void put(File root, String path, String value) throws Exception {
        File file = new File(root, path); file.getParentFile().mkdirs();
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }
    private static String read(File root, String path) throws Exception {
        return new String(Files.readAllBytes(new File(root, path).toPath()), StandardCharsets.UTF_8);
    }
    private static byte[] export(File saves, String profile) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); SaveGameArchive.exportTo(saves, profile, out); return out.toByteArray();
    }
    private static byte[] zip(String profile, Map<String, String> files, boolean metadata) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            if (metadata) {
                zip.putNextEntry(new ZipEntry("collection-save.properties"));
                String text = "format=wasteland-saves-1\nprofile=" + profile + "\nfiles=" + files.size() + "\n";
                zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
            for (Map.Entry<String,String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
    private static void restore(File root, File saves, String profile, byte[] bytes) throws Exception {
        SaveGameArchive.restore(root, saves, profile, new ByteArrayInputStream(bytes));
    }
    private static void journal(File root, File saves, File stage, File backup) throws Exception {
        Properties data = new Properties();
        data.setProperty("destination", root.toPath().relativize(saves.toPath()).toString());
        data.setProperty("stage", root.toPath().relativize(stage.toPath()).toString());
        data.setProperty("backup", root.toPath().relativize(backup.toPath()).toString());
        try (OutputStream out = new FileOutputStream(new File(root, SaveGameArchive.JOURNAL))) { data.store(out, "test"); }
    }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("wasteland-save-tests").toFile().getCanonicalFile();
        try {
            File rpu = new File(root, "rpu"); rpu.mkdir();
            File saves = SaveGameArchive.locate(rpu, "data");
            rejects(() -> export(saves, "rpu"));
            put(saves, "SLOT01/SAVE.DAT", "old save");
            put(saves, "SLOT01/sfallgv.sav", "perk state");
            put(saves, "SLOT01/proto/items/00001.pro", "item proto");
            put(saves, "slotdat.ini", "slot=1");
            byte[] archive = export(saves, "rpu");
            check(archive.length > 0);
            put(saves, "SLOT01/SAVE.DAT", "current save");
            put(saves, "SLOT02/SAVE.DAT", "second save");
            File nevada = new File(root, "nevada");
            File other = SaveGameArchive.locate(nevada, "data");
            put(other, "SLOT01/SAVE.DAT", "Nevada save");
            restore(rpu, saves, "rpu", archive);
            check(read(saves, "SLOT01/SAVE.DAT").equals("old save"));
            check(read(saves, "SLOT01/sfallgv.sav").equals("perk state"));
            check(read(saves, "SLOT01/proto/items/00001.pro").equals("item proto"));
            check(!new File(saves, "SLOT02").exists());
            check(read(other, "SLOT01/SAVE.DAT").equals("Nevada save"));
            File backup = SaveGameArchive.latestBackup(saves);
            check(backup != null && read(backup, "SLOT01/SAVE.DAT").equals("current save"));
            check(read(backup, "SLOT02/SAVE.DAT").equals("second save"));
            check(!new File(rpu, SaveGameArchive.JOURNAL).exists());
            byte[] previous = export(backup, "rpu");
            restore(rpu, saves, "rpu", previous);
            check(read(saves, "SLOT01/SAVE.DAT").equals("current save"));
            rejects(() -> restore(nevada, other, "nevada", archive));
            check(read(other, "SLOT01/SAVE.DAT").equals("Nevada save"));
            rejects(() -> SaveGameArchive.locate(rpu, "../nevada"));
            rejects(() -> SaveGameArchive.locate(rpu, "/tmp"));
            check(SaveGameArchive.locate(rpu, "DATA").equals(saves));

            for (String path : new String[]{"../escape", "saves/../../escape", "saves//bad", "saves/./bad",
                    "/saves/absolute", "saves/C:/bad", "saves\\bad", "unexpected.txt"}) {
                byte[] bad = zip("rpu", Collections.singletonMap(path, "bad"), true);
                rejects(() -> restore(rpu, saves, "rpu", bad));
            }
            Map<String,String> duplicateCase = new LinkedHashMap<>();
            duplicateCase.put("saves/SLOT01/SAVE.DAT", "a"); duplicateCase.put("saves/slot01/save.dat", "b");
            rejects(() -> restore(rpu, saves, "rpu", zip("rpu", duplicateCase, true)));
            rejects(() -> restore(rpu, saves, "rpu", zip("rpu", Collections.singletonMap("saves/slotdat.ini", "a"), true)));
            rejects(() -> restore(rpu, saves, "rpu", zip("rpu", Collections.singletonMap("saves/SLOT01/SAVE.DAT", "a"), false)));
            rejects(() -> restore(rpu, saves, "rpu", Arrays.copyOf(archive, archive.length / 2)));
            check(read(saves, "SLOT01/SAVE.DAT").equals("current save"));
            check(!new File(rpu, SaveGameArchive.JOURNAL).exists());

            // Simulate process death before the move, between the two moves, and
            // after activation. Every recovery preserves the previous directory.
            for (int step = 0; step < 3; step++) {
                File stage = new File(saves.getParentFile(), ".save-restore-stage-test" + step);
                File kept = new File(saves.getParentFile(), ".save-restore-backup-test" + step);
                put(stage, "SLOT01/SAVE.DAT", "restored " + step);
                String old = read(saves, "SLOT01/SAVE.DAT");
                journal(rpu, saves, stage, kept);
                if (step >= 1) check(saves.renameTo(kept));
                if (step == 2) check(stage.renameTo(saves));
                SaveGameArchive.recover(rpu);
                check(read(saves, "SLOT01/SAVE.DAT").equals("restored " + step));
                check(read(kept, "SLOT01/SAVE.DAT").equals(old));
                check(!new File(rpu, SaveGameArchive.JOURNAL).exists());
                SaveGameArchive.recover(rpu);
                check(read(saves, "SLOT01/SAVE.DAT").equals("restored " + step));
            }
            File link = new File(saves, "escape-link");
            Files.createSymbolicLink(link.toPath(), other.toPath());
            rejects(() -> export(saves, "rpu"));
            Files.delete(link.toPath());
            Properties malicious = new Properties(); malicious.setProperty("destination", "../nevada/data/SAVEGAME");
            try (OutputStream out = new FileOutputStream(new File(rpu, SaveGameArchive.JOURNAL))) { malicious.store(out, "bad"); }
            rejects(() -> SaveGameArchive.recover(rpu));
            check(read(other, "SLOT01/SAVE.DAT").equals("Nevada save"));
            System.out.println("Save archive tests passed: " + checks + " checks, including isolation, corruption, backups and interrupted restores.");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root.toPath())) {
                for (Path path : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }
}

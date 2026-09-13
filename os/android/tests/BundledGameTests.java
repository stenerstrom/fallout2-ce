package com.alexbatalov.fallout2ce;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class BundledGameTests {
    private static int assertions;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError();
    }
    private static void rejects(Action action) throws Exception {
        assertions++;
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Invalid bundle accepted");
    }
    private static BundledGameExtractor.Entry entry(String path, byte[] bytes) throws Exception {
        StringBuilder digest = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) digest.append(String.format("%02x", b & 255));
        return new BundledGameExtractor.Entry(path, bytes.length, digest.toString());
    }

    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("force-bundle-tests").toFile().getCanonicalFile();
        byte[] first = "original game data".getBytes("UTF-8");
        byte[] second = "mod settings".getBytes("UTF-8");
        List<BundledGameExtractor.Entry> files = Arrays.asList(entry("master.dat", first), entry("mods/upu.ini", second));
        BundledGameExtractor.Progress quiet = (done, total) -> {};
        try {
            // Simulate an interrupted source after one verified file was committed.
            rejects(() -> BundledGameExtractor.extract(dir, files, path -> {
                if (path.equals("mods/upu.ini")) throw new IOException("Simulated interrupted download");
                return new ByteArrayInputStream(first);
            }, quiet));
            check(new File(dir, "master.dat").isFile());
            check(new File(dir, BundledGameExtractor.IN_PROGRESS).isFile());
            check(!new File(dir, "mods/upu.ini.bundle-part").exists());

            // Retry verifies/skips the completed file and resumes the missing file.
            final int[] opened = {0};
            BundledGameExtractor.extract(dir, files, path -> {
                opened[0]++;
                check(path.equals("mods/upu.ini"));
                return new ByteArrayInputStream(second);
            }, (done, total) -> check(done <= total));
            check(opened[0] == 1);
            check(!new File(dir, BundledGameExtractor.IN_PROGRESS).exists());
            check(Arrays.equals(second, Files.readAllBytes(new File(dir, "mods/upu.ini").toPath())));

            // An existing user-modified file must not be overwritten.
            byte[] custom = "custom settings".getBytes("UTF-8");
            Files.write(new File(dir, "mods/upu.ini").toPath(), custom);
            rejects(() -> BundledGameExtractor.extract(dir, files, path -> new ByteArrayInputStream(first), quiet));
            check(Arrays.equals(custom, Files.readAllBytes(new File(dir, "mods/upu.ini").toPath())));

            // A damaged asset is never committed; retry remains possible.
            BundledGameExtractor.Entry damaged = entry("critter.dat", second);
            rejects(() -> BundledGameExtractor.extract(dir, Collections.singletonList(damaged),
                    path -> new ByteArrayInputStream(first), quiet));
            check(!new File(dir, "critter.dat").exists());
            check(!new File(dir, "critter.dat.bundle-part").exists());

            for (String path : new String[]{"../escape", "/absolute", "mods/../../escape", "mods\\escape", "mods/./file"}) {
                rejects(() -> BundledGameExtractor.destination(dir, path));
            }
            File link = new File(dir, "linked");
            Files.createSymbolicLink(link.toPath(), dir.getParentFile().toPath());
            rejects(() -> BundledGameExtractor.destination(dir, "linked/outside"));
            rejects(() -> BundledGameExtractor.extract(dir, Arrays.asList(files.get(0), files.get(0)),
                    path -> new ByteArrayInputStream(first), quiet));
            rejects(() -> BundledGameExtractor.extract(dir, Collections.emptyList(),
                    path -> new ByteArrayInputStream(first), quiet));
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(dir.toPath())) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (IOException error) { throw new RuntimeException(error); }
                });
            }
        }
        System.out.println("Bundled game tests passed: " + assertions + " assertions; resume, corruption, preservation and path checks.");
    }
}

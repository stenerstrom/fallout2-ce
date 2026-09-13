package com.alexbatalov.fallout2ce;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Streaming, resumable extraction. Existing files are verified, never replaced.
final class BundledGameExtractor {
    static final String IN_PROGRESS = ".bundle-installing";
    static final class Entry {
        final String path, sha256;
        final long size;
        Entry(String path, long size, String sha256) {
            this.path = path; this.size = size; this.sha256 = sha256;
        }
    }
    interface Source { InputStream open(String path) throws IOException; }
    interface Progress { void update(long completed, long total); }

    static File destination(File directory, String path) throws IOException {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\")
                || path.startsWith(".") || path.contains("/.") || path.contains("//")) {
            throw new IOException("Invalid path in the game bundle: " + path);
        }
        File file = new File(directory, path);
        String root = directory.getCanonicalPath() + File.separator;
        if (!file.getCanonicalPath().startsWith(root)
                || !file.getCanonicalPath().equals(file.getAbsolutePath())) {
            throw new IOException("Invalid path in the game bundle: " + path);
        }
        return file;
    }

    static void extract(File directory, List<Entry> entries, Source source, Progress progress) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create the game folder.");
        directory = directory.getCanonicalFile();
        Set<String> paths = new HashSet<>();
        long total = 0, missing = 0;
        for (Entry entry : entries) {
            File target = destination(directory, entry.path);
            if (entry.size < 0 || !entry.sha256.matches("[0-9a-f]{64}") || !paths.add(entry.path)) {
                throw new IOException("Invalid game bundle manifest.");
            }
            total = Math.addExact(total, entry.size);
            if (!target.exists()) missing = Math.addExact(missing, entry.size);
        }
        if (entries.isEmpty()) throw new IOException("The game bundle is empty.");
        if (directory.getUsableSpace() < missing + 64L * 1024 * 1024) {
            throw new IOException("More free storage space is needed to prepare the game.");
        }
        File marker = new File(directory, IN_PROGRESS);
        if (!marker.exists() && !marker.createNewFile()) throw new IOException("Could not start preparing the game.");
        long completed = 0;
        byte[] buffer = new byte[256 * 1024];
        progress.update(0, total);
        for (Entry entry : entries) {
            File target = destination(directory, entry.path);
            boolean exists = target.exists();
            File temporary = new File(target.getPath() + ".bundle-part");
            if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) {
                throw new IOException("Could not create the folder: " + entry.path);
            }
            MessageDigest digest = sha256();
            long count = 0;
            try {
                // Files from a previous interrupted attempt must match before we skip them.
                try (InputStream input = exists ? new FileInputStream(target) : source.open(entry.path);
                     FileOutputStream output = exists ? null : new FileOutputStream(temporary)) {
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        count += length;
                        if (count > entry.size) throw new IOException("Incorrect file size: " + entry.path);
                        digest.update(buffer, 0, length);
                        if (output != null) output.write(buffer, 0, length);
                        progress.update(completed + count, total);
                    }
                    if (output != null) output.getFD().sync();
                }
                if (count != entry.size || !hex(digest.digest()).equals(entry.sha256)) {
                    throw new IOException((exists ? "An existing file has different contents: " : "Corrupt file in the game bundle: ") + entry.path);
                }
                if (!exists && (target.exists() || !temporary.renameTo(target))) {
                    throw new IOException("Could not save the file: " + entry.path);
                }
                completed += count;
            } finally {
                if (!exists && temporary.exists()) temporary.delete();
            }
        }
        if (!marker.delete()) throw new IOException("Could not finish preparing the game.");
        progress.update(total, total);
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}

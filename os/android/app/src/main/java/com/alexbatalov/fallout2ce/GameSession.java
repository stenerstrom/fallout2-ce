package com.alexbatalov.fallout2ce;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.HashSet;
import java.util.Set;

// A process-owned lock is released by the OS even if the native engine crashes.
final class GameSession implements Closeable {
    private static final Set<String> ownedPaths = new HashSet<>();
    private final String path;
    private final RandomAccessFile file;
    private final FileLock lock;
    private boolean closed;

    private GameSession(String path, RandomAccessFile file, FileLock lock) {
        this.path = path;
        this.file = file;
        this.lock = lock;
    }

    static synchronized GameSession tryAcquire(File directory) throws IOException {
        String path = new File(directory, "game-session.lock").getCanonicalPath();
        // On POSIX, closing a second descriptor can release this process's first
        // lock. Never open a second descriptor for an already-owned lock file.
        if (ownedPaths.contains(path)) return null;
        RandomAccessFile file = new RandomAccessFile(path, "rw");
        try {
            FileLock lock = file.getChannel().tryLock();
            if (lock != null) {
                ownedPaths.add(path);
                return new GameSession(path, file, lock);
            }
        } catch (IOException | RuntimeException error) {
            file.close();
            throw error;
        }
        file.close();
        return null;
    }

    @Override public void close() throws IOException {
        synchronized (GameSession.class) {
            if (closed) return;
            closed = true;
            try {
                try { lock.release(); } finally { file.close(); }
            } finally {
                ownedPaths.remove(path);
            }
        }
    }
}

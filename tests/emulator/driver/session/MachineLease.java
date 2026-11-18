package ru.voboost.voyemu;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import ru.voboost.tests.RunFiles;

/** Cross-process lease for a canonical machine; Voyemu still establishes remote ownership. */
public final class MachineLease implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private final Path directory;
    private MachineLease(FileChannel channel, FileLock lock, Path directory) {
        this.channel = channel; this.lock = lock; this.directory = directory;
    }

    public static MachineLease acquire(Path root, Path machine) throws IOException {
        if (!root.isAbsolute() || !machine.isAbsolute()) throw new IOException("absolute lease inputs required");
        Path canonical = machine.toRealPath();
        if (!Files.isDirectory(canonical)) throw new IOException("prepared machine directory required");
        Path directory = RunFiles.directory(root);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
            canonical.toString().getBytes(StandardCharsets.UTF_8));
        for (int offset = 0; offset < encoded.length(); offset += 64)
            directory = RunFiles.directory(directory.resolve(encoded.substring(offset, Math.min(offset + 64, encoded.length()))));
        Path file = directory.resolve("machine.lock");
        FileChannel channel = FileChannel.open(file,
            Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        FileLock lock = null;
        try {
            BasicFileAttributes before = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!before.isRegularFile() || ((Number) Files.getAttribute(file, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).intValue() != 1
                    || !Files.getOwner(file).equals(Files.getOwner(directory))
                    || !Files.getPosixFilePermissions(file).equals(PosixFilePermissions.fromString("rw-------")))
                throw new IOException("invalid machine lease file");
            lock = channel.tryLock();
            if (lock == null) throw new IOException("prepared machine is leased by another runner");
            Object after = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
            if (before.fileKey() == null || !Objects.equals(before.fileKey(), after))
                throw new IOException("machine lease inode changed");
            return new MachineLease(channel, lock, directory);
        } catch (IOException | RuntimeException error) {
            try { if (lock != null) lock.release(); }
            catch (IOException cleanup) { error.addSuppressed(cleanup); }
            try { channel.close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            if (error instanceof OverlappingFileLockException) throw new IOException("prepared machine is leased by another runner", error);
            throw error;
        }
    }

    public Path directory() { return directory; }
    @Override public void close() throws IOException {
        try { if (lock.isValid()) lock.release(); } finally { channel.close(); }
    }
}

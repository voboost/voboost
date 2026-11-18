package voyemu.journal;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.*;
import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;

/** One journal directory per mutation domain; the caller must hold this object through dispatch. */
public final class Journal implements AutoCloseable {
    private static final int MAGIC = 0x564a3031, MAX = 16 * 1024 * 1024;
    private final Path dir;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean issued, closed;

    private Journal(Path dir, FileChannel channel, FileLock lock) { this.dir = dir; this.lockChannel = channel; this.lock = lock; }

    /** Directory must already exist and be dedicated to this mutation domain. */
    public static Journal open(Path dir) throws IOException {
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) throw new IOException("journal directory missing or invalid");
        FileChannel channel = FileChannel.open(dir.resolve("lock"), CREATE, WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("journal locked");
            return new Journal(dir, channel, lock);
        } catch (OverlappingFileLockException e) { channel.close(); throw new IOException("journal locked", e); }
        catch (IOException | RuntimeException e) { channel.close(); throw e; }
    }

    /** Persist an irreversible dispatch reservation; return only after file and directory force succeed. */
    public synchronized void reserve(String scope, String identity, String requestId, byte[] request) throws IOException {
        ensureOpen();
        if (issued) throw new IOException("reservation already issued");
        byte[] s = text(scope), i = text(identity), id = text(requestId);
        if (request == null || request.length > MAX) throw new IOException("invalid request");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC); field(out, s); field(out, i); field(out, id); field(out, request); out.writeInt(~MAGIC); out.flush();
        // CREATE_NEW is the permanent fence. Even an empty or truncated file blocks every later dispatch.
        try (FileChannel f = FileChannel.open(dir.resolve("reservation"), CREATE_NEW, WRITE)) {
            ByteBuffer b = ByteBuffer.wrap(bytes.toByteArray());
            while (b.hasRemaining()) f.write(b);
            f.force(true);
        }
        try (FileChannel d = FileChannel.open(dir, READ)) { d.force(true); }
        issued = true;
    }

    /** Only read-only lookup of an existing request is permitted after reopening. */
    public synchronized Record inspect(String scope, String identity) throws IOException {
        ensureOpen();
        text(scope); text(identity);
        try (DataInputStream in = new DataInputStream(Files.newInputStream(dir.resolve("reservation")))) {
            if (in.readInt() != MAGIC) throw new IOException("invalid reservation");
            String s = decode(field(in, 4096));
            String i = decode(field(in, 4096));
            String id = decode(field(in, 4096));
            byte[] request = field(in, MAX);
            if (in.readInt() != ~MAGIC || in.read() != -1 || id.isEmpty() || !s.equals(scope) || !i.equals(identity)) throw new IOException("invalid reservation or identity mismatch");
            return new Record(s, i, id, request);
        } catch (EOFException e) { throw new IOException("partial reservation; manual recovery required", e); }
    }

    /** Consume a fresh reservation before dispatch. Reopened journals permit lookup only. */
    public synchronized void claim(String scope, String identity, String requestId, byte[] request) throws IOException {
        Record record = inspect(scope, identity);
        if (!record.requestId.equals(requestId) || !java.util.Arrays.equals(record.request(), request))
            throw new IOException("reservation does not match request");
        if (!issued) throw new IOException("reservation is not fresh or already claimed");
        // An uncertain write must never be retried, including within this process.
        issued = false;
        try (FileChannel marker = FileChannel.open(dir.resolve("dispatch"), CREATE_NEW, WRITE)) {
            marker.force(true);
        }
        try (FileChannel directory = FileChannel.open(dir, READ)) { directory.force(true); }
    }
    private static byte[] text(String value) throws IOException {
        if (value == null || value.isEmpty()) throw new IOException("empty identity field");
        ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value));
        byte[] b = new byte[encoded.remaining()];
        encoded.get(b);
        if (b.length > 4096) throw new IOException("identity field too long");
        return b;
    }
    private static String decode(byte[] value) throws IOException {
        if (value.length == 0) throw new IOException("empty identity field");
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value)).toString();
    }
    private static void field(DataOutputStream out, byte[] b) throws IOException { out.writeInt(b.length); out.write(b); }
    private static byte[] field(DataInputStream in, int max) throws IOException { int n = in.readInt(); if (n < 0 || n > max) throw new IOException("invalid field length"); byte[] b = new byte[n]; in.readFully(b); return b; }
    private void ensureOpen() throws IOException { if (closed || !lock.isValid()) throw new IOException("journal closed or lock lost"); }
    public synchronized void close() throws IOException { if (!closed) { closed = true; try { lock.release(); } finally { lockChannel.close(); } } }
    public static final class Record {
        public final String scope, identity, requestId;
        private final byte[] request;
        Record(String s, String i, String id, byte[] r) { scope=s; identity=i; requestId=id; request=r.clone(); }
        public byte[] request() { return request.clone(); }
    }
}

package ru.voboost.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

/** Bounded strict input and durable private records for product test runs. */
public final class RunFiles {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private RunFiles() {}

    public static Path directory(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(path, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
            } catch (FileAlreadyExistsException concurrentCreate) {
                // The checks below also validate a directory created by another lane.
            }
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(path).equals(PosixFilePermissions.fromString("rwx------")))
            throw new IOException("private run directory required: " + path);
        return path;
    }

    public static JsonObject read(Path path) throws IOException {
        return read(path, MAX_BYTES);
    }

    public static JsonObject read(Path path, int limit) throws IOException {
        if (limit < 1) throw new IllegalArgumentException("positive input bound required");
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size > limit) throw new IOException("run input exceeds bound");
            ByteBuffer bytes = ByteBuffer.allocate((int) size + 1);
            while (bytes.hasRemaining() && channel.read(bytes) != -1) { }
            if (bytes.position() != size || channel.size() != size)
                throw new IOException("run input changed or exceeds bound");
            bytes.flip();
            return parse(StandardCharsets.UTF_8.newDecoder().decode(bytes).toString(), limit);
        }
    }

    public static JsonObject parse(String input) throws IOException {
        return parse(input, MAX_BYTES);
    }

    private static JsonObject parse(String input, int limit) throws IOException {
        if (input.length() > limit) throw new IOException("run input exceeds bound");
        try (JsonReader reader = new JsonReader(new StringReader(input))) {
            reader.setLenient(false);
            JsonElement value = value(reader, 0);
            if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("one JSON object required");
            return value.getAsJsonObject();
        } catch (IllegalStateException | NumberFormatException error) {
            throw new IOException("invalid run input", error);
        }
    }

    private static JsonElement value(JsonReader reader, int depth) throws IOException {
        if (depth > 32) throw new IOException("run input nesting exceeds bound");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IOException("duplicate run input field");
                    object.add(name, value(reader, depth + 1));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) array.add(value(reader, depth + 1));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("unexpected run input token");
        };
    }

    public static void write(Path path, JsonObject value) throws IOException {
        directory(path.getParent());
        byte[] bytes = (value.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("run record exceeds bound");
        Path temporary = path.resolveSibling(".record-" + UUID.randomUUID());
        boolean created = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                created = true;
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel parent = FileChannel.open(path.getParent(), StandardOpenOption.READ)) { parent.force(true); }
        } finally {
            if (created) Files.deleteIfExists(temporary);
        }
    }
}

package ru.voboost.tests.delivery;

import java.io.ByteArrayInputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Off-line host-side consumer of the produced product manifest (voboost.json).
 *
 * <p>Mirrors the strict producer contract of the Rust release crate
 * ({@code parse_canonical_json} + {@code validate_manifest}): canonical compact
 * field order with exactly one trailing LF, no duplicate object keys, strict
 * UTF-8, and the same artifact classification rules. This class never verifies
 * signatures or opens a real DMG; callers that need trust must use the release
 * verifier and a trusted public root.
 */
public final class DeliveryManifest {
    private DeliveryManifest() {}

    public static final long MAX_MANIFEST_BYTES = 4L * 1024 * 1024;
    public static final long MAX_APK_VERSION_CODE = 2_100_000_000L;
    public static final Set<String> REQUIRED_INVENTORY = Set.of(
            "voboost-app.apk", "voboost-daemon", "voboost-inject", "voboost-install", "voboost-restore",
            "voboost-uninstall", "voboost-selinux", "voboost.rc", "restore.json",
            "restore.json.sig", "libimplant-arm64-v8a.so", "libimplant-armeabi-v7a.so",
            "agents.json",
            "agents.json.sig", "agent-common.dex", "voboost-cluster-daemon",
            "voboost-cluster-startup.sh", "libvoboost-cluster-inject.so",
            "libvoboost-runtime-cluster.so", "libagent-font-cyrillic.so");

    public enum Activation {
        PACKAGE_INSTALLER("package-installer"), NATIVE_RUNTIME("native-runtime"),
        INIT("init"), RESTORE_AUTHORITY("restore-authority"), AGENT_GENERATION("agent-generation");
        public final String json;
        Activation(String json) { this.json = json; }
        public String jsonName() { return json; }
    }
    public enum Abi {
        ARM64_V8A("arm64-v8a"), ARMEABI_V7A("armeabi-v7a"), QNX_AARCH64("qnx-aarch64");
        public final String json;
        Abi(String json) { this.json = json; }
        public String jsonName() { return json; }
    }

    /** Categories that classify() may return for a basename. */
    public enum Role {
        APP("app"), DAEMON("daemon"), INJECT("inject"), INSTALL("install"), RESTORE("restore"),
        UNINSTALL("uninstall"), SELINUX("selinux"), INIT("init"),
        RESTORE_AUTHORIZATION("restore-authorization"), RESTORE_SIGNATURE("restore-signature"),
        IMPLANT("implant"), LSPLANT("lsplant"),
        AGENT_CATALOG("agent-catalog"), AGENT_CATALOG_SIGNATURE("agent-catalog-signature"),
        AGENT_COMMON("agent-common"), AGENT_MODULE("agent-module"),
        QNX_DAEMON("qnx-daemon"), QNX_INIT("qnx-init"), QNX_IMPLANT("qnx-implant"),
        QNX_RUNTIME("qnx-runtime"), QNX_AGENT_MODULE("qnx-agent-module"),
        RESOURCE_APK("resource-apk"), RESOURCE_LEDGER("resource-ledger"),
        QNX_RESOURCE_PACK("qnx-resource-pack"), QNX_RESOURCE_LEDGER("qnx-resource-ledger");
        public final String json;
        Role(String json) { this.json = json; }
        public String jsonName() { return json; }
    }

    /** Role rank used by the producer for canonical artifact ordering. */
    public static int roleRank(Role role) {
        return switch (role) {
            case APP -> 0; case DAEMON -> 1; case INJECT -> 2; case INSTALL -> 3; case RESTORE -> 4;
            case UNINSTALL -> 5; case SELINUX -> 6; case INIT -> 7;
            case RESTORE_AUTHORIZATION -> 8; case RESTORE_SIGNATURE -> 9;
            case IMPLANT -> 10; case LSPLANT -> 11; case AGENT_CATALOG -> 12;
            case AGENT_CATALOG_SIGNATURE -> 13; case AGENT_COMMON -> 14;
            case AGENT_MODULE -> 15; case QNX_DAEMON -> 16; case QNX_INIT -> 17;
            case QNX_IMPLANT -> 18; case QNX_RUNTIME -> 19; case QNX_AGENT_MODULE -> 20;
            case RESOURCE_APK -> 21; case RESOURCE_LEDGER -> 22;
            case QNX_RESOURCE_PACK -> 23; case QNX_RESOURCE_LEDGER -> 24;
        };
    }

    public record Classification(Role role, Activation activation, Abi abi) {}

    /** Canonical root field order; mirrors Manifest of the release crate. */
    private static final List<String> ROOT_FIELDS = List.of(
            "schemaVersion", "releaseIdentity", "compatibility", "artifacts");
    /** Canonical compatibility field order; mirrors CompatibilityManifest. */
    private static final List<String> COMPATIBILITY_FIELDS = List.of(
            "installerProtocol", "operationProtocol", "appDaemonProtocol",
            "agentCatalogSchema", "agentEntryAbi", "implantAbi");
    /** Canonical artifact field order; mirrors Artifact (skip_serializing_if none kept optional). */
    private static final List<String> ARTIFACT_FIELDS = List.of(
            "role", "file", "size", "sha256", "activation", "packageName",
            "versionCode", "certificateSha256", "abi", "resource");
    /** Canonical resource descriptor field order; mirrors ResourceMetadata. */
    private static final List<String> RESOURCE_FIELDS = List.of(
            "owner", "locale", "targetPackage", "compatibilitySchema",
            "overlayPackage", "coverageLedger");
    /** Canonical coverage ledger field order; mirrors ResourceFile. */
    private static final List<String> RESOURCE_FILE_FIELDS = List.of("file", "size", "sha256");

    /** Strictly decoded manifest document; field types are enforced on extraction. */
    public static final class Document {
        private final Map<String, Object> root;
        private Document(Map<String, Object> root) { this.root = root; }

        public int schemaVersion() { return (Integer) field(root, "schemaVersion", Integer.class); }
        public String releaseIdentity() { return (String) field(root, "releaseIdentity", String.class); }

        public List<Artifact> artifacts() {
            Object raw = field(root, "artifacts", List.class);
            List<Artifact> artifacts = new ArrayList<>();
            for (Object entry : (List<?>) raw) {
                Map<String, Object> values = object(entry, "artifacts entry");
                validateFields(values, ARTIFACT_FIELDS, "artifact");
                @SuppressWarnings("unchecked")
                Map<String, Object> resource = (Map<String, Object>) values.get("resource");
                if (resource != null) {
                    validateFields(resource, RESOURCE_FIELDS, "resource descriptor");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ledger = (Map<String, Object>) resource.get("coverageLedger");
                    if (ledger != null) validateFields(ledger, RESOURCE_FILE_FIELDS, "coverage ledger");
                }
                artifacts.add(new Artifact(values));
            }
            return artifacts;
        }

        public int compatibility(String name) {
            Map<String, Object> compatibility = compatibilityObject();
            return (Integer) field(compatibility, name, Integer.class);
        }

        Map<String, Object> compatibilityObject() {
            Map<String, Object> compatibility = object(field(root, "compatibility", Map.class), "compatibility");
            validateFields(compatibility, COMPATIBILITY_FIELDS, "compatibility");
            return compatibility;
        }
    }

    /** One artifact record with producer-enforced types; unresolved fields stay raw and unknown. */
    public static final class Artifact {
        private final Map<String, Object> values;
        private Artifact(Map<String, Object> values) { this.values = values; }

        public String role() { return (String) field(values, "role", String.class); }
        public String file() { return (String) field(values, "file", String.class); }
        public long size() { return ((Number) values.get("size")).longValue(); }
        public String sha256() { return (String) field(values, "sha256", String.class); }
        public String activation() { return (String) field(values, "activation", String.class); }
        public String packageName() { return optional(values, "packageName", String.class); }
        public Long versionCode() { return optional(values, "versionCode", Long.class); }
        public String certificateSha256() { return optional(values, "certificateSha256", String.class); }
        public String abi() { return optional(values, "abi", String.class); }
        public Map<String, Object> resource() { return optional(values, "resource", Map.class); }
        public boolean hasApkMetadata() {
            return packageName() != null || versionCode() != null || certificateSha256() != null;
        }
        public Set<String> keys() { return Collections.unmodifiableSet(values.keySet()); }

        private String linkedLedgerFile;
        private Long linkedLedgerSize;
        private String linkedLedgerSha;

        /** Registers the descriptor ledger identity for the closure check. */
        private void linkCoverageLedger(String file, Long size, String sha256) {
            linkedLedgerFile = file;
            linkedLedgerSize = size;
            linkedLedgerSha = sha256;
        }
    }

    /**
     * Reads and validates {@code voboost.json} the way the contract requires:
     * envelope, canonical bytes, no duplicate keys and producer-side manifest rules.
     * Returns a raw name/value view only after strict checks.
     */
    public static Document verify(Path manifestPath) throws Exception {
        byte[] bytes = Files.readAllBytes(manifestPath);
        if (bytes.length == 0 || bytes.length > MAX_MANIFEST_BYTES) {
            throw invalid("Product manifest byte envelope differs");
        }
        if (bytes[bytes.length - 1] != '\n'
                || (bytes.length > 1 && bytes[bytes.length - 2] == '\n')) {
            throw invalid("canonical JSON must end with exactly one LF");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) {
            throw invalid("Product manifest is not strict UTF-8");
        }
        // Parser requires only the trailing LF stripped from the document body.
        String body = text.substring(0, text.length() - 1);
        Map<String, Object> root = (Map<String, Object>) new Parser(body).parse();
        validateFields(root, ROOT_FIELDS, "manifest");
        if (!canonicalize(root).equals(body)) {
            throw invalid("Product manifest is not canonical compact JSON");
        }
        Document document = new Document(root);
        validate(document);
        return document;
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Product manifest differs: " + reason);
    }

    private static Map<String, Object> object(Object value, String what) {
        if (!(value instanceof Map)) throw invalid(what + " must be a JSON object");
        @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private static Object field(Map<String, Object> container, String key, Class<?> type) {
        Object value = container.get(key);
        if (value == null) throw invalid("missing field " + key);
        // The parser stores all JSON integers as Long; Integer expected fields are numbers by contract.
        if (value instanceof Long number) {
            if (number != number.intValue()) throw invalid("field " + key + " must fit a JSON integer used by the contract");
            return number.intValue();
        }
        if (!type.isInstance(value)) throw invalid("field " + key + " must be " + type.getSimpleName());
        return value;
    }

    /**
     * Mirrors serde {@code deny_unknown_fields} plus the struct field order
     * that {@code parse_canonical_json} re-serialization enforces: the object
     * must declare only known fields, without repetition (the parser already
     * rejects duplicates), in the canonical declaration order.
     */
    static void validateFields(Map<String, Object> value, List<String> canonical, String what) {
        for (String key : value.keySet()) {
            if (!canonical.contains(key)) throw invalid("unknown field `" + key + "` in " + what);
        }
        List<String> keys = new ArrayList<>(value.keySet());
        List<String> ordered = new ArrayList<>(keys);
        ordered.sort(java.util.Comparator.comparingInt(canonical::indexOf));
        if (!ordered.equals(keys)) throw invalid(what + " is not in canonical field order");
    }

    private static <T> T optional(Map<String, Object> container, String key, Class<T> type) {
        Object value = container.get(key);
        if (value == null) return null;
        if (!type.isInstance(value)) throw invalid("field " + key + " must be " + type.getSimpleName());
        return type.cast(value);
    }

    /** Byte-exact canonical re-serialization: compact separators, producer field order. */
    static String canonicalize(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                if (!first) out.append(',');
                first = false;
                writeString(entry.getKey(), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List) {
            out.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) out.append(',');
                first = false;
                write(item, out);
            }
            out.append(']');
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof String) {
            writeString((String) value, out);
        } else if (value instanceof Long || value instanceof Integer) {
            out.append(value);
        } else {
            throw invalid("unsupported canonical JSON value");
        }
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || Character.isSurrogate(c)) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /**
     * Strict JSON parse with duplicate-key rejection, no numbers outside
     * canonical integers, and no escaped forms the producer never emits on
     * object keys or structural characters.
     */
    static final class Parser {
        private final String text;
        private int position;
        Parser(String text) { this.text = text; }

        Object parse() {
            Object value = parseValue();
            if (position != text.length()) throw invalid("trailing bytes after JSON value");
            return value;
        }

        private char peek() { return position < text.length() ? text.charAt(position) : '\0'; }

        private void skipWhitespace() {
            while (position < text.length() && isWhitespace(text.charAt(position))) position++;
        }

        private static boolean isWhitespace(char c) {
            return c == ' ' || c == '\t' || c == '\n' || c == '\r';
        }

        private IllegalArgumentException reject(String reason) {
            return new IllegalArgumentException("Product manifest differs: " + reason);
        }

        private void expect(char expected) {
            if (peek() != expected) throw reject("expected " + expected);
            position++;
        }

        private void consume(char expected) {
            if (peek() != expected) throw reject("expected " + expected);
            position++;
            skipWhitespace();
        }

        private boolean consumeIf(char expected) {
            if (peek() != expected) return false;
            position++;
            skipWhitespace();
            return true;
        }

        private Object parseValue() {
            skipWhitespace();
            return switch (peek()) {
                case 'n' -> keyword("null", null);
                case 't' -> keyword("true", Boolean.TRUE);
                case 'f' -> keyword("false", Boolean.FALSE);
                case '"' -> parseString();
                case '{' -> parseObject();
                case '[' -> parseArray();
                default -> parseInteger();
            };
        }

        private Object keyword(String word, Object value) {
            if (position + word.length() > text.length()
                    || !text.regionMatches(position, word, 0, word.length())) {
                throw reject("keyword " + word);
            }
            position += word.length();
            skipWhitespace();
            return value;
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (position >= text.length()) throw reject("unterminated string");
                char c = text.charAt(position++);
                if (c == '"') break;
                if (c < 0x20) throw reject("raw control character in string");
                if (c != '\\') { out.append(c); continue; }
                if (position >= text.length()) throw reject("dangling escape");
                char escape = text.charAt(position++);
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(parseUnicodeEscape());
                    default -> throw reject("unsupported escape \\" + escape);
                }
            }
            skipWhitespace();
            return out.toString();
        }

        private char parseUnicodeEscape() {
            if (position + 4 > text.length()) throw reject("unicode escape truncated");
            char value = (char) Integer.parseInt(text.substring(position, position + 4), 16);
            position += 4;
            if (Character.isHighSurrogate(value)) {
                if (position + 6 > text.length() || !text.regionMatches(position, "\\u", 0, 2)) {
                    throw reject("unpaired surrogate escape");
                }
                char low = (char) Integer.parseInt(text.substring(position + 2, position + 6), 16);
                position += 6;
                if (!Character.isLowSurrogate(low)) throw reject("not a low surrogate");
                return value;
            }
            if (Character.isLowSurrogate(value)) throw reject("unpaired low surrogate");
            return value;
        }

        private long parseInteger() {
            int start = position;
            boolean negative = false;
            if (peek() == '-') { negative = true; position++; }
            long value = 0;
            int digits = 0;
            while (peek() >= '0' && peek() <= '9') {
                long digit = peek() - '0';
                if (value < (Long.MIN_VALUE + digit) / 10) throw reject("integer overflow");
                value = value * 10 - digit;
                position++;
                digits++;
            }
            if (digits == 0) throw reject("value must be a JSON number");
            if (!negative && value == Long.MIN_VALUE) throw reject("integer overflow");
            if (!negative) value = -value;
            if (peek() == '.' || peek() == 'e' || peek() == 'E') {
                throw reject("non-integer JSON numbers are not part of the contract");
            }
            skipWhitespace();
            return value;
        }

        private Map<String, Object> parseObject() {
            consume('{');
            Map<String, Object> map = new LinkedHashMap<>();
            if (consumeIf('}')) return map;
            while (true) {
                if (peek() == '\0') throw reject("unterminated object");
                String key = parseString();
                if (map.containsKey(key)) throw reject("duplicate key " + key);
                consume(':');
                map.put(key, parseValue());
                if (consumeIf(',')) continue;
                consume('}');
                return map;
            }
        }

        private List<Object> parseArray() {
            consume('[');
            List<Object> list = new ArrayList<>();
            if (consumeIf(']')) return list;
            while (true) {
                list.add(parseValue());
                if (consumeIf(',')) continue;
                consume(']');
                return list;
            }
        }
        private IllegalArgumentException invalid(String reason) {
            return new IllegalArgumentException("Product manifest differs: " + reason);
        }
    }

    /**
     * Producer-side manifest rules mirrored from the release crate
     * (verify_manifest/validate_manifest); rejects shape the verifier would
     * reject before any signature check.
     */
    static void validate(Document document) {
        if (document.schemaVersion() != 1) throw invalid("unsupported voboost.json schemaVersion");
        if (!isCanonicalSemver(document.releaseIdentity())) {
            throw invalid("releaseIdentity must use canonical SemVer spelling");
        }
        for (String name : new String[] {
                "installerProtocol", "operationProtocol", "appDaemonProtocol",
                "agentCatalogSchema", "agentEntryAbi", "implantAbi"}) {
            if (document.compatibility(name) <= 0) throw invalid("compatibility." + name + " must be positive");
        }
        if (document.compatibility("agentCatalogSchema") != 2) {
            throw invalid("compatibility.agentCatalogSchema must be 2");
        }
        List<Artifact> artifacts = document.artifacts();
        if (artifacts.isEmpty()) throw invalid("artifacts must not be empty");

        Set<String> files = new LinkedHashSet<>();
        Map<Role, Integer> roles = new LinkedHashMap<>();
        String previousKey = null;
        boolean hasAgentModule = false;
        for (Artifact artifact : artifacts) {
            validateBasename(artifact.file());
            if (!files.add(artifact.file())) throw invalid("duplicate artifact filename " + artifact.file());

            Classification classification = classify(artifact.file());
            Role role = roleOf(classification.role());
            String declaredRole = artifact.role();
            activationOf(declaredRole, artifact.file(), artifact.activation());
            // The producer rejects a resource descriptor on any non resource-apk
            // role before the APK-metadata rule; mirror that ordering.
            if (artifact.resource() != null && role != Role.RESOURCE_APK) {
                throw invalid("resource descriptor on foreign artifact role");
            }
            if (artifact.hasApkMetadata() && role != Role.APP && role != Role.RESOURCE_APK) {
                throw invalid("APK metadata is only allowed on role app");
            }
            roles.merge(role, 1, Integer::sum);
            if (role == Role.AGENT_MODULE) hasAgentModule = true;

            if (role == Role.RESOURCE_APK) {
                validateResourceDescriptor(artifact);
            }
            if (artifact.size() <= 0) throw invalid("artifact " + artifact.file() + " has zero size");
            if (artifact.size() > maximumSize(role)) {
                throw invalid("artifact " + artifact.file() + " exceeds its role size limit");
            }
            if (!isSha256(artifact.sha256())) throw invalid("artifact " + artifact.file() + " has invalid sha256");
            String abi = artifact.abi();
            boolean abiAllowed = classification.abi() != null;
            if (abiAllowed && (abi == null || !abi.equals(classification.abi().jsonName()))) {
                throw invalid("artifact " + artifact.file() + " metadata does not match its canonical filename");
            }
            if (!abiAllowed && abi != null) {
                throw invalid("artifact " + artifact.file() + " metadata does not match its canonical filename");
            }

            String key = String.format(Locale.ROOT, "%02d:%s", roleRank(role), artifact.file());
            // Numbers with distinct prefix width cannot collide with "0..." keys below rank 10.
            if (previousKey != null && previousKey.compareTo(key) >= 0) {
                throw invalid("artifacts are not in canonical role/name order");
            }
            previousKey = key;

            if (Role.APP == role) {
                if (!artifact.hasApkMetadata()) {
                    throw invalid("app artifact requires complete APK identity metadata");
                }
                if (!"ru.voboost".equals(artifact.packageName())) {
                    throw invalid("app packageName must be ru.voboost");
                }
                Long versionCode = artifact.versionCode();
                if (versionCode == null || versionCode < 1 || versionCode > MAX_APK_VERSION_CODE) {
                    throw invalid("app versionCode must be in 1..=" + MAX_APK_VERSION_CODE);
                }
                if (artifact.certificateSha256() == null || !isSha256(artifact.certificateSha256())) {
                    throw invalid("app certificateSha256 must be lowercase SHA-256");
                }
            }
        }
        for (Artifact artifact : artifacts) {
            if (artifact.linkedLedgerFile == null) continue;
            boolean declared = artifacts.stream().anyMatch(candidate ->
                    candidate.file().equals(artifact.linkedLedgerFile)
                            && roleOf(classify(candidate.file()).role()) == Role.RESOURCE_LEDGER
                            && candidate.size() == artifact.linkedLedgerSize
                            && candidate.sha256().equals(artifact.linkedLedgerSha));
            if (!declared) {
                throw invalid("resource ledger absent or identity differs");
            }
        }
        if (!hasAgentModule) throw invalid("at least one agent module DEX is required");
        // Descriptor set consistency as in validate_manifest.
        java.util.Set<String> overlayPackages = new LinkedHashSet<>();
        for (Artifact artifact : artifacts) {
            Map<String, Object> resource = artifact.resource();
            if (resource == null) continue;
            String overlay = (String) field(resource, "overlayPackage", String.class);
            if (!overlayPackages.add(overlay)) {
                throw invalid("duplicate resource overlay package");
            }
        }
        for (Artifact ledger : artifacts) {
            if (roleOf(classify(ledger.file()).role()) != Role.RESOURCE_LEDGER) continue;
            int owners = 0;
            for (Artifact artifact : artifacts) {
                if (artifact.linkedLedgerFile == null) continue;
                if (ledger.file().equals(artifact.linkedLedgerFile)
                        && ledger.size() == artifact.linkedLedgerSize
                        && ledger.sha256().equals(artifact.linkedLedgerSha)
                        && roleOf(classify(artifact.file()).role()) == Role.RESOURCE_APK) {
                    owners++;
                }
            }
            if (owners != 1) {
                throw invalid("resource ledger must have exactly one resource APK owner");
            }
        }
        for (Artifact artifact : artifacts) {
            Role role = roleOf(classify(artifact.file()).role());
            if (role != Role.QNX_RESOURCE_PACK && role != Role.QNX_RESOURCE_LEDGER) continue;
            String suffix = role == Role.QNX_RESOURCE_PACK ? ".kzb" : ".json";
            String peerSuffix = role == Role.QNX_RESOURCE_PACK ? ".json" : ".kzb";
            Role peerRole = role == Role.QNX_RESOURCE_PACK ? Role.QNX_RESOURCE_LEDGER : Role.QNX_RESOURCE_PACK;
            String peer = artifact.file().substring(
                    0, artifact.file().length() - suffix.length()) + peerSuffix;
            boolean found = artifacts.stream().anyMatch(candidate ->
                    candidate.file().equals(peer)
                    && roleOf(classify(candidate.file()).role()) == peerRole);
            if (!found) {
                throw invalid("QNX resource requires exact same-stem pack and ledger");
            }
        }
        for (Map.Entry<Role, Integer> entry : roles.entrySet()) {
            if (isSingletonRole(entry.getKey()) && entry.getValue() != 1) {
                throw invalid("singleton role " + entry.getKey() + " appears " + entry.getValue() + " times");
            }
        }
        for (String required : REQUIRED_INVENTORY) {
            if (!files.contains(required)) throw invalid("required artifact " + required + " is missing");
        }
    }

    /** Mirrors the resource-apk descriptor rules of validate_manifest exactly. */
    private static void validateResourceDescriptor(Artifact artifact) {
        Map<String, Object> resource = artifact.resource();
        if (resource == null) throw invalid("resource APK requires descriptor");
        String owner = (String) field(resource, "owner", String.class);
        String locale = (String) field(resource, "locale", String.class);
        String targetPackage = (String) field(resource, "targetPackage", String.class);
        int compatibilitySchema = ((Number) field(resource, "compatibilitySchema", Number.class)).intValue();
        String overlayPackage = (String) field(resource, "overlayPackage", String.class);
        String englishStem = strip(artifact.file(), "resource-i18n-en-", ".apk");
        List<String> englishTuple = englishResourceTuple(englishStem);
        boolean owned = englishStem == null
                ? "i18n-ru".equals(owner) && "ru".equals(locale)
                : "i18n-en".equals(owner) && "en".equals(locale) && englishTuple != null
                        && englishTuple.get(0).equals(targetPackage) && englishTuple.get(1).equals(overlayPackage);
        if (!owned
                || !isValidPackageName(targetPackage) || !isValidPackageName(overlayPackage)
                || !overlayPackage.startsWith("ru.voboost.translation.")
                || targetPackage.equals(overlayPackage)
                || compatibilitySchema != 1
                || !overlayPackage.equals(artifact.packageName())
                || artifact.versionCode() == null
                || artifact.versionCode() < 1 || artifact.versionCode() > MAX_APK_VERSION_CODE
                || artifact.certificateSha256() == null
                || !isSha256(artifact.certificateSha256())) {
            throw invalid("resource APK identity rejected");
        }
        Map<String, Object> ledger = object(field(resource, "coverageLedger", Map.class), "coverage ledger");
        String ledgerFile = (String) field(ledger, "file", String.class);
        validateBasename(ledgerFile);
        long ledgerSize = ((Number) field(ledger, "size", Number.class)).longValue();
        String ledgerSha = (String) field(ledger, "sha256", String.class);
        boolean ledgerOwned = englishStem == null
                ? !ledgerFile.startsWith("resource-i18n-en-")
                : ledgerFile.equals("resource-i18n-en-" + englishStem + ".json");
        if (ledgerSize <= 0 || ledgerSize > 1024L * 1024 || !isSha256(ledgerSha) || !ledgerOwned) {
            throw invalid("resource ledger absent or identity differs");
        }
        artifact.linkCoverageLedger(ledgerFile, ledgerSize, ledgerSha);
    }

    private static List<String> englishResourceTuple(String stem) {
        if (stem == null) return null;
        return switch (stem) {
            case "setting-a11" -> List.of("com.qinggan.app.setting", "ru.voboost.translation.setting.a11.en");
            case "launcher-a9" -> List.of("com.qinggan.app.launcher", "ru.voboost.translation.launcher.en");
            case "launcher-a11" -> List.of("com.qinggan.app.launcher", "ru.voboost.translation.launcher.a11.en");
            case "systemui-a9" -> List.of("com.qinggan.systemui", "ru.voboost.translation.systemui.en");
            case "media-a9" -> List.of("com.qinggan.media", "ru.voboost.translation.media.en");
            case "media-a11" -> List.of("com.qinggan.media", "ru.voboost.translation.media.a11.en");
            default -> null;
        };
    }

    private static boolean isValidPackageName(String value) {
        if (value == null || value.isEmpty() || value.length() > 255) return false;
        String[] parts = value.split("\\.", -1);
        if (parts.length < 2) return false;
        for (String part : parts) {
            if (part.isEmpty()) return false;
            char first = part.charAt(0);
            if (!(first >= 'a' && first <= 'z' || first >= 'A' && first <= 'Z')) return false;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                        || c >= '0' && c <= '9' || c == '_')) return false;
            }
        }
        return true;
    }

    private static boolean isSingletonRole(Role role) {
        return !(role == Role.IMPLANT || role == Role.LSPLANT || role == Role.AGENT_MODULE
                || role == Role.QNX_AGENT_MODULE || role == Role.RESOURCE_APK
                || role == Role.RESOURCE_LEDGER || role == Role.QNX_RESOURCE_PACK
                || role == Role.QNX_RESOURCE_LEDGER);
    }

    private static void activationOf(String declared, String file, String activation) {
        Classification classification = classify(file);
        if (!declared.equals(classification.role().jsonName())) {
            throw invalid("artifact " + file + " metadata does not match its canonical filename");
        }
        if (!activation.equals(classification.activation().jsonName())) {
            throw invalid("artifact " + file + " metadata does not match its canonical filename");
        }
    }

    private static Role roleOf(Role role) { return role; }

    static long maximumSize(Role role) {
        return switch (role) {
            case APP -> 128L * 1024 * 1024;
            case RESOURCE_APK, QNX_RESOURCE_PACK -> 16L * 1024 * 1024;
            case RESOURCE_LEDGER, QNX_RESOURCE_LEDGER -> 1024 * 1024;
            case AGENT_CATALOG, RESTORE_AUTHORIZATION -> 4L * 1024 * 1024;
            case AGENT_CATALOG_SIGNATURE, RESTORE_SIGNATURE -> 64;
            case AGENT_COMMON, IMPLANT, LSPLANT, DAEMON, INJECT, INSTALL, RESTORE, UNINSTALL,
                    SELINUX, INIT, AGENT_MODULE, QNX_DAEMON, QNX_IMPLANT, QNX_RUNTIME -> 64L * 1024 * 1024;
            default -> 64L * 1024 * 1024;
        };
    }

    static void validateBasename(String value) {
        if (value.isEmpty() || value.length() > 255 || value.equals(".") || value.equals("..")) {
            throw invalid("artifact file must be a non-special basename");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '\\' || c == '\0') {
                throw invalid("artifact file must be a basename");
            }
        }
    }

    static boolean isSha256(String value) {
        if (value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return false;
        }
        return true;
    }

    /** Canonical strict SemVer, including canonical spelling of every component. */
    static boolean isCanonicalSemver(String value) {
        String core = value;
        String build = null;
        int plus = value.indexOf('+');
        if (plus >= 0) {
            build = value.substring(plus + 1);
            core = value.substring(0, plus);
            if (build.isEmpty() || !build.chars().allMatch(DeliveryManifest::isBuildChar)) return false;
        }
        String numbers = core;
        String pre = null;
        int minus = core.indexOf('-');
        if (minus >= 0) {
            pre = core.substring(minus + 1);
            numbers = core.substring(0, minus);
            if (pre.isEmpty()
                    || !java.util.Arrays.stream(pre.split("\\.", -1))
                            .allMatch(DeliveryManifest::isPreIdentifier)) return false;
        }
        String[] parts = numbers.split("\\.", -1);
        if (parts.length != 3) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 1 && part.charAt(0) == '0') return false;
            for (int i = 0; i < part.length(); i++) {
                if (part.charAt(i) < '0' || part.charAt(i) > '9') return false;
            }
        }
        return true;
    }

    private static boolean isBuildChar(int c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-';
    }

    private static boolean isPreIdentifier(String identifier) {
        if (identifier.isEmpty()) return false;
        if (java.util.Arrays.stream(identifier.split("", -1)).allMatch(s -> s.chars().allMatch(c -> (c >= '0' && c <= '9')))) {
            return !(identifier.length() > 1 && identifier.charAt(0) == '0');
        }
        return identifier.chars().allMatch(c ->
                (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || c == '-');
    }

    /** Producer filename classification; mirrors classify() of the release crate. */
    static Classification classify(String name) {
        for (String[] resource : new String[][] {
                {"qnx-resource-i18n-ru-", ".kzb"},
                {"qnx-resource-i18n-ru-", ".json"}}) {
            String stem = strip(name, resource[0], resource[1]);
            if (stem != null && !stem.isEmpty() && stem.length() <= 96
                    && isLowercaseHyphenStem(stem)) {
                boolean pack = resource[1].equals(".kzb");
                return new Classification(pack ? Role.QNX_RESOURCE_PACK : Role.QNX_RESOURCE_LEDGER,
                        Activation.AGENT_GENERATION, Abi.QNX_AARCH64);
            }
        }
        for (String[] resource : new String[][] {
                {"resource-i18n-ru-", ".apk"},
                {"resource-i18n-ru-", ".json"}}) {
            String stem = strip(name, resource[0], resource[1]);
            if (stem != null && !stem.isEmpty() && stem.length() <= 128
                    && isLowercaseHyphenStem(stem)) {
                boolean apk = resource[1].equals(".apk");
                return new Classification(apk ? Role.RESOURCE_APK : Role.RESOURCE_LEDGER,
                        Activation.AGENT_GENERATION, null);
            }
        }
        for (String suffix : List.of(".apk", ".json")) {
            if (englishResourceTuple(strip(name, "resource-i18n-en-", suffix)) != null) {
                return new Classification(suffix.equals(".apk") ? Role.RESOURCE_APK : Role.RESOURCE_LEDGER,
                        Activation.AGENT_GENERATION, null);
            }
        }
        Classification exact = classifyExact(name);
        if (exact != null) return exact;
        String agent = strip(name, "libagent-", ".so");
        if (agent != null && !agent.isEmpty() && isLowercaseHyphenStem(agent)) {
            return new Classification(Role.QNX_AGENT_MODULE, Activation.AGENT_GENERATION, Abi.QNX_AARCH64);
        }
        String module = strip(name, "agent-", ".dex");
        if (module != null && !module.isEmpty() && isLowercaseHyphenStem(module)) {
            return new Classification(Role.AGENT_MODULE, Activation.AGENT_GENERATION, null);
        }
        throw invalid("unknown product artifact \"" + name + "\"");
    }

    private static Classification classifyExact(String name) {
        return switch (name) {
            case "voboost-cluster-daemon" -> new Classification(Role.QNX_DAEMON, Activation.NATIVE_RUNTIME, Abi.QNX_AARCH64);
            case "voboost-cluster-startup.sh" -> new Classification(Role.QNX_INIT, Activation.INIT, null);
            case "libvoboost-cluster-inject.so" -> new Classification(Role.QNX_IMPLANT, Activation.NATIVE_RUNTIME, Abi.QNX_AARCH64);
            case "libvoboost-runtime-cluster.so" -> new Classification(Role.QNX_RUNTIME, Activation.AGENT_GENERATION, Abi.QNX_AARCH64);
            case "libagent-font-cyrillic.so" -> new Classification(Role.QNX_AGENT_MODULE, Activation.AGENT_GENERATION, Abi.QNX_AARCH64);
            case "voboost-app.apk" -> new Classification(Role.APP, Activation.PACKAGE_INSTALLER, null);
            case "voboost-daemon" -> new Classification(Role.DAEMON, Activation.NATIVE_RUNTIME, null);
            case "voboost-inject" -> new Classification(Role.INJECT, Activation.NATIVE_RUNTIME, null);
            case "voboost-install" -> new Classification(Role.INSTALL, Activation.NATIVE_RUNTIME, null);
            case "voboost-restore" -> new Classification(Role.RESTORE, Activation.RESTORE_AUTHORITY, null);
            case "voboost-uninstall" -> new Classification(Role.UNINSTALL, Activation.RESTORE_AUTHORITY, null);
            case "voboost-selinux" -> new Classification(Role.SELINUX, Activation.NATIVE_RUNTIME, null);
            case "voboost.rc" -> new Classification(Role.INIT, Activation.INIT, null);
            case "restore.json" -> new Classification(Role.RESTORE_AUTHORIZATION, Activation.RESTORE_AUTHORITY, null);
            case "restore.json.sig" -> new Classification(Role.RESTORE_SIGNATURE, Activation.RESTORE_AUTHORITY, null);
            case "agents.json" -> new Classification(Role.AGENT_CATALOG, Activation.AGENT_GENERATION, null);
            case "agents.json.sig" -> new Classification(Role.AGENT_CATALOG_SIGNATURE, Activation.AGENT_GENERATION, null);
            case "agent-common.dex" -> new Classification(Role.AGENT_COMMON, Activation.AGENT_GENERATION, null);
            case "libimplant-arm64-v8a.so" -> new Classification(Role.IMPLANT, Activation.NATIVE_RUNTIME, Abi.ARM64_V8A);
            case "libimplant-armeabi-v7a.so" -> new Classification(Role.IMPLANT, Activation.NATIVE_RUNTIME, Abi.ARMEABI_V7A);
            case "liblsplant-arm64-v8a.so" -> new Classification(Role.LSPLANT, Activation.NATIVE_RUNTIME, Abi.ARM64_V8A);
            case "liblsplant-armeabi-v7a.so" -> new Classification(Role.LSPLANT, Activation.NATIVE_RUNTIME, Abi.ARMEABI_V7A);
            default -> null;
        };
    }

    private static String strip(String name, String prefix, String suffix) {
        if (!name.startsWith(prefix) || !name.endsWith(suffix)) return null;
        return name.substring(prefix.length(), name.length() - suffix.length());
    }

    private static boolean isLowercaseHyphenStem(String stem) {
        for (String part : stem.split("-", -1)) {
            if (part.isEmpty()) return false;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if ((c < 'a' || c > 'z') && (c < '0' || c > '9')) return false;
            }
        }
        return true;
    }
}

package dev.turtywurty.gamedashboard.platform.impl.battle_net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// wire 0 = varint        bool, enum, int32, uint64, etc.
// wire 1 = fixed64       double, fixed64
// wire 2 = length data   string, bytes, nested message
// wire 5 = fixed32       float, fixed32

// in protobuf binary format, each field starts with a "key".
// the key contains 2 pieces of information packed together.
// the low 3 bits are the "wire type", which explains how the value is encoded on the disk
// the rest of the bits are "field number". this references a field in the schema using our following lookup:
// message Database {
//   repeated ProductInstall productInstall = 1;
//   repeated InstallHandshake activeInstalls = 2;
//   repeated ActiveProcess activeProcesses = 3;
//   repeated ProductConfig productConfigs = 4;
//   optional DownloadSettings downloadSettings = 5;
//   optional uint64 versionSummarySeqn = 6;
//   repeated string priorityUidList = 7;
// }

// protobuf varints are stored as an integer in chunks of 7 bits per byte
// bit 7     bits 0-6
// -----     --------
// continue  value bits
// 0xxxxxxx = this is the last byte
// 1xxxxxxx = more bytes follow
public final class BattleNetProductDbParser {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private static final BigInteger TWO_32 = BigInteger.ONE.shiftLeft(32);
    private static final BigInteger TWO_63 = BigInteger.ONE.shiftLeft(63);
    private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);
    private static final BigInteger JS_MAX_SAFE_INTEGER = BigInteger.valueOf(9_007_199_254_740_991L);
    private static final BigInteger JS_MIN_SAFE_INTEGER = BigInteger.valueOf(-9_007_199_254_740_991L);

    private static final Map<BigInteger, String> LANGUAGE_OPTION = enumMap(
            0, "LANGOPTION_NONE",
            1, "LANGOPTION_TEXT",
            2, "LANGOPTION_SPEECH",
            3, "LANGOPTION_TEXT_AND_SPEECH"
    );

    private static final Map<BigInteger, String> LANGUAGE_SETTING_TYPE = enumMap(
            0, "LANGSETTING_NONE",
            1, "LANGSETTING_SINGLE",
            2, "LANGSETTING_SIMPLE",
            3, "LANGSETTING_ADVANCED"
    );

    private static final Map<BigInteger, String> SHORTCUT_OPTION = enumMap(
            0, "SHORTCUT_NONE",
            1, "SHORTCUT_USER",
            2, "SHORTCUT_ALL_USERS"
    );

    private static final Map<BigInteger, String> OPERATION = enumMap(
            -1, "OP_NONE",
            0, "OP_UPDATE",
            1, "OP_BACKFILL",
            2, "OP_REPAIR"
    );

    private static final Map<String, Map<Integer, FieldDef>> SCHEMAS = schemas();

    public static Database parseProductDb(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            Map<String, Object> db = decodeMessage(bytes, "Database");
            Object installs = db.get("productInstall");
            if (installs instanceof List<?> list && !list.isEmpty())
                return toDatabase(db);
        } catch (Exception ignored) {
            // Some sibling files are a bare ProductInstall rather than a full Database wrapper.
        }

        Map<String, Object> wrapper = new LinkedHashMap<>();
        List<Object> installs = new ArrayList<>();
        installs.add(decodeMessage(bytes, "ProductInstall"));
        wrapper.put("productInstall", installs);
        wrapper.put("_note", "Parsed as a single ProductInstall, not a full Database wrapper.");
        return toDatabase(wrapper);
    }

    private static Map<String, Object> decodeMessage(byte[] bytes, String schemaName) {
        Map<Integer, FieldDef> schema = SCHEMAS.get(schemaName);
        if (schema == null)
            throw new IllegalArgumentException("Unknown schema: " + schemaName);

        Map<String, Object> out = new LinkedHashMap<>();
        int pos = 0;
        while (pos < bytes.length) {
            Varint key = readVarint(bytes, pos); // the length of the message
            pos = key.pos; // move to end of varint
            int fieldNo = key.value.shiftRight(3).intValueExact(); // drop the bottom 3 bits as those are the wire type
            int wire = key.value.and(BigInteger.valueOf(7)).intValueExact(); // keeps the bottom 3 bits (7 in binary is 00000111)

            FieldDef field = schema.get(fieldNo);
            if (field == null) {
                pos = skipUnknown(bytes, wire, pos);
                continue;
            }

            Object value;
            switch (field.kind) {
                case STRING -> {
                    Varint len = readVarint(bytes, pos);
                    int start = len.pos;
                    int end = checkedEnd(start, len.value, bytes.length);
                    int actualLength = end - start;
                    value = new String(bytes, start, actualLength, StandardCharsets.UTF_8);
                    pos = end;
                }
                case DOUBLE -> {
                    if (wire != 1)
                        throw new IllegalStateException(schemaName + "." + field.name + " expected fixed64");

                    value = Double.longBitsToDouble(readLittleEndian64(bytes, pos)); // reads in IEEE-754 format, exactly 8 bytes converting the bits (for example 0x3FF0000000000000) into a java double (1.0 in that example)
                    pos += 8; // length of double
                }
                case MESSAGE -> {
                    Varint len = readVarint(bytes, pos);
                    int start = len.pos;
                    int end = checkedEnd(start, len.value, bytes.length);
                    value = decodeMessage(slice(bytes, start, end), field.messageType); // read the message within this message
                    pos = end;
                }
                case BOOL, UINT64, INT32, INT64, ENUM -> {
                    Varint scalar = readVarint(bytes, pos); // scalar is a "simple value". these types are encoded as varints
                    value = scalarToJson(scalar.value, field); // converts that scalar into an appropriate type based on the field
                    pos = scalar.pos;
                }
                default -> throw new IllegalStateException("Unhandled kind: " + field.kind);
            }

            addField(out, field.name, value, field.repeated);
        }


        return out;
    }

    private static Varint readVarint(byte[] bytes, int offset) {
        BigInteger value = BigInteger.ZERO;
        int shift = 0;
        int pos = offset;
        while (pos < bytes.length) {
            int b = bytes[pos++] & 0xFF; // java bytes are signed, but it will be unsigned in the file, this turns the signed java byte into an unsigned int
            value = value.or(BigInteger.valueOf(b & 0x7FL).shiftLeft(shift)); // take the lower 7 bits (remove the "continue" bit) and puts them in the right position. "or" then adds those bits to the result (accumulates the varint)
            if ((b & 0x80) == 0) // (0x80 is 10000000) so we're checking the top 1 bit (the "continue" bit)
                return new Varint(value, pos); // returns if the "continue" bit was 0

            shift += 7; // move 7 bits forward to the next chunk of the varint
            if (shift > 70) // uint64 only needs 64 bits, so we've shifted 10 times and that means we're at 70 bits (larger than uint64 which is the largest varint protobuf supports)
                throw new IllegalStateException("varint too long at offset: " + offset);
        }

        throw new IllegalStateException("Truncated varint at offset: " + offset);
    }

    private static int skipUnknown(byte[] bytes, int wire, int pos) {
        return switch (wire) {
            case 0 -> readVarint(bytes, pos).pos; // varint
            case 1 -> checkedEnd(pos, BigInteger.valueOf(8), bytes.length); // double
            case 2 -> { // length data
                Varint len = readVarint(bytes, pos);
                yield checkedEnd(len.pos, len.value, bytes.length);
            }
            case 5 -> checkedEnd(pos, BigInteger.valueOf(4), bytes.length); // float
            default -> throw new IllegalStateException("Unsupported protobuf wire type: " + wire);
        };
    }

    private static Object scalarToJson(BigInteger value, FieldDef field) {
        return switch (field.kind) {
            case BOOL -> !value.equals(BigInteger.ZERO);
            case INT32 -> signed32(value).intValue();
            case INT64 -> numberOrString(signed64(value));
            case UINT64 -> numberOrString(value);
            case ENUM -> {
                BigInteger signed = signed64(value);
                String label = field.enumMap.get(signed);
                yield label != null ? label : signed.intValue();
            }
            default -> throw new IllegalArgumentException("not a scalar: " + field.kind);
        };
    }

    private static BigInteger signed32(BigInteger value) {
        BigInteger masked = value.and(TWO_32.subtract(BigInteger.ONE));
        return masked.testBit(31) ? masked.subtract(TWO_32) : masked;
    }

    private static BigInteger signed64(BigInteger value) {
        BigInteger masked = value.and(TWO_64.subtract(BigInteger.ONE));
        return masked.compareTo(TWO_63) >= 0 ? masked.subtract(TWO_64) : masked;
    }

    private static Object numberOrString(BigInteger value) {
        if (value.compareTo(JS_MIN_SAFE_INTEGER) >= 0 && value.compareTo(JS_MAX_SAFE_INTEGER) <= 0) {
            return value.longValue();
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private static void addField(Map<String, Object> out, String name, Object value, boolean repeated) {
        if (repeated) {
            List<Object> list = (List<Object>) out.computeIfAbsent(name, ignored -> new ArrayList<>());
            list.add(value);
        } else {
            out.put(name, value);
        }
    }

    private static long readLittleEndian64(byte[] bytes, int pos) {
        if (pos + 8 > bytes.length)
            throw new IllegalStateException("Truncated fixed64 at offset: " + pos);

        long value = 0;
        for (int i = 0; i < 8; i++) { // read 8 bytes
            value |= (long) (bytes[pos + i] & 0xFF) << (8 * i); // (bytes[pos + i] & 0xFF) - 1 unsigned byte, (<< (8 * i)) moves the byte into the correct position by shifting the right number of places (little endian) the OR combines each shifted byte into the final number
        }

        return value;
    }

    private static byte[] slice(byte[] bytes, int start, int end) {
        byte[] out = new byte[end - start];
        System.arraycopy(bytes, start, out, 0, out.length);
        return out;
    }

    private static int checkedEnd(int start, BigInteger len, int max) {
        if (len.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
            throw new IllegalStateException("length too large: " + len);

        int end = start + len.intValue();
        if (end < start || end > max)
            throw new IllegalStateException("truncated length-delimited field");

        return end;
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    private static Map<BigInteger, String> enumMap(Object... values) {
        Map<BigInteger, String> out = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            out.put(BigInteger.valueOf(((Number) values[i]).longValue()), (String) values[i + 1]);
        }

        return out;
    }

    private static Map<String, Map<Integer, FieldDef>> schemas() {
        Map<String, Map<Integer, FieldDef>> schema = new LinkedHashMap<>();

        schema.put("LanguageSetting", map(
                1, new FieldDef("language", Kind.STRING),
                2, new FieldDef("option", LANGUAGE_OPTION)
        ));

        schema.put("UserSettings", map(
                1, new FieldDef("installPath", Kind.STRING),
                2, new FieldDef("playRegion", Kind.STRING),
                3, new FieldDef("desktopShortcut", SHORTCUT_OPTION),
                4, new FieldDef("startmenuShortcut", SHORTCUT_OPTION),
                5, new FieldDef("languageSettings", LANGUAGE_SETTING_TYPE),
                6, new FieldDef("selectedTextLanguage", Kind.STRING),
                7, new FieldDef("selectedSpeechLanguage", Kind.STRING),
                8, new FieldDef("languages", "LanguageSetting", true),
                9, new FieldDef("additionalTags", Kind.STRING),
                10, new FieldDef("versionBranch", Kind.STRING),
                11, new FieldDef("accountCountry", Kind.STRING),
                12, new FieldDef("geoIpCountry", Kind.STRING),
                13, new FieldDef("gameSubfolder", Kind.STRING)
        ));

        schema.put("InstallHandshake", map(
                1, new FieldDef("product", Kind.STRING),
                2, new FieldDef("uid", Kind.STRING),
                3, new FieldDef("settings", "UserSettings")
        ));

        schema.put("BuildConfig", map(
                1, new FieldDef("region", Kind.STRING),
                2, new FieldDef("buildConfig", Kind.STRING)
        ));

        schema.put("BaseProductState", map(
                1, new FieldDef("installed", Kind.BOOL),
                2, new FieldDef("playable", Kind.BOOL),
                3, new FieldDef("updateComplete", Kind.BOOL),
                4, new FieldDef("backgroundDownloadAvailable", Kind.BOOL),
                5, new FieldDef("backgroundDownloadComplete", Kind.BOOL),
                6, new FieldDef("currentVersion", Kind.STRING),
                7, new FieldDef("currentVersionStr", Kind.STRING),
                8, new FieldDef("installedBuildConfig", "BuildConfig", true),
                9, new FieldDef("backgroundDownloadBuildConfig", "BuildConfig", true),
                10, new FieldDef("decryptionKey", Kind.STRING),
                11, new FieldDef("completedInstallActions", Kind.STRING, true),
                12, new FieldDef("completedBuildKeys", Kind.STRING, true),
                13, new FieldDef("completedBgdlKeys", Kind.STRING, true),
                14, new FieldDef("activeBuildKey", Kind.STRING),
                15, new FieldDef("activeBgdlKey", Kind.STRING),
                16, new FieldDef("activeInstallKey", Kind.STRING),
                17, new FieldDef("activeTagString", Kind.STRING),
                18, new FieldDef("incompleteBuildKey", Kind.STRING)
        ));

        schema.put("BackfillProgress", map(
                1, new FieldDef("progress", Kind.DOUBLE),
                2, new FieldDef("backgroundDownload", Kind.BOOL),
                3, new FieldDef("paused", Kind.BOOL),
                4, new FieldDef("downloadLimit", Kind.UINT64)
        ));

        schema.put("RepairProgress", map(
                1, new FieldDef("progress", Kind.DOUBLE)
        ));

        schema.put("UpdateProgress", map(
                1, new FieldDef("lastDiscSetUsed", Kind.STRING),
                2, new FieldDef("progress", Kind.DOUBLE),
                3, new FieldDef("discIgnored", Kind.BOOL),
                4, new FieldDef("totalToDownload", Kind.UINT64),
                5, new FieldDef("downloadRemaining", Kind.UINT64)
        ));

        schema.put("CachedProductState", map(
                1, new FieldDef("baseProductState", "BaseProductState"),
                2, new FieldDef("backfillProgress", "BackfillProgress"),
                3, new FieldDef("repairProgress", "RepairProgress"),
                4, new FieldDef("updateProgress", "UpdateProgress")
        ));

        schema.put("ProductOperations", map(
                1, new FieldDef("activeOperation", OPERATION),
                2, new FieldDef("priority", Kind.UINT64)
        ));

        schema.put("ProductInstall", map(
                1, new FieldDef("uid", Kind.STRING),
                2, new FieldDef("productCode", Kind.STRING),
                3, new FieldDef("settings", "UserSettings"),
                4, new FieldDef("cachedProductState", "CachedProductState"),
                5, new FieldDef("productOperations", "ProductOperations"),
                6, new FieldDef("productFamily", Kind.STRING),
                7, new FieldDef("hidden", Kind.BOOL),
                8, new FieldDef("persistentJsonStorage", Kind.STRING)
        ));

        schema.put("ProductConfig", map(
                1, new FieldDef("productCode", Kind.STRING),
                2, new FieldDef("metadataHash", Kind.STRING)
        ));

        schema.put("ActiveProcess", map(
                1, new FieldDef("processName", Kind.STRING),
                2, new FieldDef("pid", Kind.INT32),
                3, new FieldDef("uri", Kind.STRING, true)
        ));

        schema.put("DownloadSettings", map(
                1, new FieldDef("downloadLimit", Kind.INT64),
                2, new FieldDef("backfillLimit", Kind.INT64),
                3, new FieldDef("unknown3", Kind.INT64)
        ));

        schema.put("Database", map(
                1, new FieldDef("productInstall", "ProductInstall", true),
                2, new FieldDef("activeInstalls", "InstallHandshake", true),
                3, new FieldDef("activeProcesses", "ActiveProcess", true),
                4, new FieldDef("productConfigs", "ProductConfig", true),
                5, new FieldDef("downloadSettings", "DownloadSettings"),
                6, new FieldDef("versionSummarySeqn", Kind.UINT64),
                7, new FieldDef("priorityUidList", Kind.STRING, true)
        ));

        return schema;
    }

    private static Map<Integer, FieldDef> map(Object... values) {
        Map<Integer, FieldDef> out = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            out.put((Integer) values[i], (FieldDef) values[i + 1]);
        }

        return out;
    }

    @SuppressWarnings("unchecked")
    private static Database toDatabase(Map<String, Object> raw) {
        Database db = new Database();

        List<Object> installs = (List<Object>) raw.get("productInstall");
        if (installs != null) {
            for (Object item : installs) {
                db.productInstall.add(toProductInstall((Map<String, Object>) item));
            }
        }

        Object seqn = raw.get("versionSummarySeqn");
        if (seqn instanceof Number n) {
            db.versionSummarySeqn = n.longValue();
        }

        List<Object> priority = (List<Object>) raw.get("priorityUidList");
        if (priority != null) {
            for (Object item : priority) {
                db.priorityUidList.add((String) item);
            }
        }

        return db;
    }

    @SuppressWarnings("unchecked")
    private static ProductInstall toProductInstall(Map<String, Object> raw) {
        var productInstall = new ProductInstall();

        productInstall.uid = (String) raw.get("uid");
        productInstall.productCode = (String) raw.get("productCode");
        productInstall.productFamily = (String) raw.get("productFamily");
        productInstall.hidden = (Boolean) raw.get("hidden");
        productInstall.persistentJsonStorage = (String) raw.get("persistentJsonStorage");

        Map<String, Object> settings = (Map<String, Object>) raw.get("settings");
        if (settings != null) {
            productInstall.settings = toUserSettings(settings);
        }

        return productInstall;
    }

    private static UserSettings toUserSettings(Map<String, Object> raw) {
        var settings = new UserSettings();

        settings.installPath = (String) raw.get("installPath");
        settings.playRegion = (String) raw.get("playRegion");
        settings.desktopShortcut = (String) raw.get("desktopShortcut");
        settings.startmenuShortcut = (String) raw.get("startmenuShortcut");
        settings.languageSettings = (String) raw.get("languageSettings");
        settings.selectedTextLanguage = (String) raw.get("selectedTextLanguage");
        settings.selectedSpeechLanguage = (String) raw.get("selectedSpeechLanguage");
        settings.accountCountry = (String) raw.get("accountCountry");
        settings.geoIpCountry = (String) raw.get("geoIpCountry");

        return settings;
    }

    public static final class Database {
        public final List<ProductInstall> productInstall = new ArrayList<>();
        public DownloadSettings downloadSettings;
        public Long versionSummarySeqn;
        public final List<String> priorityUidList = new ArrayList<>();
    }

    public static final class ProductInstall {
        public String uid;
        public String productCode;
        public UserSettings settings;
        public CachedProductState cachedProductState;
        public ProductOperations productOperations;
        public String productFamily;
        public Boolean hidden;
        public String persistentJsonStorage;
    }

    public static final class UserSettings {
        public String installPath;
        public String playRegion;
        public String desktopShortcut;
        public String startmenuShortcut;
        public String languageSettings;
        public String selectedTextLanguage;
        public String selectedSpeechLanguage;
        public final List<LanguageSetting> languages = new ArrayList<>();
        public String accountCountry;
        public String geoIpCountry;
    }

    public static final class LanguageSetting {
        public String language;
        public String option;
    }

    public static final class BuildConfig {
        public String region;
        public String buildConfig;
    }

    public static final class BaseProductState {
        public Boolean installed;
        public Boolean playable;
        public Boolean updateComplete;
        public Boolean backgroundDownloadAvailable;
        public Boolean backgroundDownloadComplete;
        public String currentVersion;
        public String currentVersionStr;
        public final List<BuildConfig> installedBuildConfig = new ArrayList<>();
        public final List<BuildConfig> backgroundDownloadBuildConfig = new ArrayList<>();
        public String decryptionKey;
        public final List<String> completedInstallActions = new ArrayList<>();
        public final List<String> completedBuildKeys = new ArrayList<>();
        public final List<String> completedBgdlKeys = new ArrayList<>();
        public String activeBuildKey;
        public String activeBgdlKey;
        public String activeInstallKey;
        public String activeTagString;
        public String incompleteBuildKey;
    }

    public static final class BackfillProgress {
        public Double progress;
        public Boolean backgroundDownload;
        public Boolean paused;
        public Long downloadLimit;
    }

    public static final class RepairProgress {
        public Double progress;
    }

    public static final class UpdateProgress {
        public String lastDiscSetUsed;
        public Double progress;
        public Boolean discIgnored;
        public Long totalToDownload;
        public Long downloadRemaining;
    }

    public static final class CachedProductState {
        public BaseProductState baseProductState;
        public BackfillProgress backfillProgress;
        public RepairProgress repairProgress;
        public UpdateProgress updateProgress;
    }

    public static final class ProductOperations {
        public String activeOperation;
        public Long priority;
    }

    public static final class ProductConfig {
        public String productCode;
        public String metadataHash;
    }

    public static final class ActiveProcess {
        public String processName;
        public Integer pid;
        public final List<String> uri = new ArrayList<>();
    }

    public static final class DownloadSettings {
        public Long downloadLimit;
        public Long backfillLimit;
        public Long unknown3;
    }

    private enum Kind {
        STRING,
        BOOL,
        UINT64,
        INT32,
        INT64,
        DOUBLE,
        ENUM,
        MESSAGE
    }

    private record FieldDef(String name, Kind kind, String messageType, Map<BigInteger, String> enumMap,
                            boolean repeated) {
        private FieldDef(String name, Kind kind) {
            this(name, kind, null, null, false);
        }

        private FieldDef(String name, Kind kind, boolean repeated) {
            this(name, kind, null, null, repeated);
        }

        private FieldDef(String name, String messageType) {
            this(name, Kind.MESSAGE, messageType, null, false);
        }

        private FieldDef(String name, String messageType, boolean repeated) {
            this(name, Kind.MESSAGE, messageType, null, repeated);
        }

        private FieldDef(String name, Map<BigInteger, String> enumMap) {
            this(name, Kind.ENUM, null, enumMap, false);
        }

    }

    private record Varint(BigInteger value, int pos) {
    }
}

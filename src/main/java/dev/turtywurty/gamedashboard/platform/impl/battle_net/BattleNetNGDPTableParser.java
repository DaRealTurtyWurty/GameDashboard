package dev.turtywurty.gamedashboard.platform.impl.battle_net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public final class BattleNetNGDPTableParser {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    public record Column(String name, String type, int size) {
        public Column {
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Column name cannot be null or blank");

            if (type == null || type.isBlank())
                throw new IllegalArgumentException("Column type cannot be null or blank");

            if (size < 0)
                throw new IllegalArgumentException("Column size cannot be negative");
        }
    }

    public record Table(List<Column> columns, List<Map<String, Object>> rows, Map<String, String> metadata) {
        public static final String SQL_TABLE_NAME = "ngdp";

        public Table(List<Column> columns, List<Map<String, Object>> rows, Map<String, String> metadata) {
            this.columns = List.copyOf(columns);
            this.rows = List.copyOf(rows);
            this.metadata = Map.copyOf(metadata);
        }

        public Column getColumn(String name) {
            return columns.stream()
                    .filter(column -> column.name().equalsIgnoreCase(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Column not found: " + name));
        }

        public List<Object> getColumnValues(Column column) {
            List<Object> values = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                values.add(row.get(column.name()));
            }

            return values;
        }

        /**
         * Runs a SQLite query against this table using the virtual table name {@value SQL_TABLE_NAME}.
         * Example: SELECT ProductConfig FROM ngdp WHERE Region = 'us'
         */
        public SqlResult querySql(String sql) {
            return querySql(sql, new Object[0]);
        }

        /**
         * Runs a prepared SQLite query against this table using the virtual table name {@value SQL_TABLE_NAME}.
         * Example: SELECT ProductConfig FROM ngdp WHERE Region = ?
         */
        public SqlResult querySql(String sql, Object... parameters) {
            Objects.requireNonNull(sql, "sql");
            Objects.requireNonNull(parameters, "parameters");

            try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
                createSqlTable(connection);
                insertSqlRows(connection);
                return executeSqlQuery(connection, sql, parameters);
            } catch (SQLException exception) {
                throw new IllegalArgumentException("Failed to query NGDP table: " + sql, exception);
            }
        }

        private void createSqlTable(Connection connection) throws SQLException {
            var columnDefinitions = new StringJoiner(", ");
            for (Column column : this.columns) {
                columnDefinitions.add(quoteSqlIdentifier(column.name()) + " " + toSqlType(column));
            }

            if (columnDefinitions.length() == 0) {
                columnDefinitions.add("__empty INTEGER");
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE " + SQL_TABLE_NAME + " (" + columnDefinitions + ")");
            }
        }

        private void insertSqlRows(Connection connection) throws SQLException {
            if (this.columns.isEmpty() || this.rows.isEmpty())
                return;

            var columnNames = new StringJoiner(", ");
            var placeholders = new StringJoiner(", ");
            for (Column column : this.columns) {
                columnNames.add(quoteSqlIdentifier(column.name()));
                placeholders.add("?");
            }

            String sql = "INSERT INTO " + SQL_TABLE_NAME + " (" + columnNames + ") VALUES (" + placeholders + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Map<String, Object> row : this.rows) {
                    for (int index = 0; index < this.columns.size(); index++) {
                        Column column = this.columns.get(index);
                        statement.setObject(index + 1, row.get(column.name()));
                    }

                    statement.addBatch();
                }

                statement.executeBatch();
            }
        }

        private static SqlResult executeSqlQuery(
                Connection connection,
                String sql,
                Object[] parameters
        ) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.length; index++) {
                    statement.setObject(index + 1, parameters[index]);
                }

                try (ResultSet resultSet = statement.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    List<Map<String, Object>> results = new ArrayList<>();

                    while (resultSet.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int index = 1; index <= columnCount; index++) {
                            row.put(metaData.getColumnLabel(index), resultSet.getObject(index));
                        }

                        results.add(row);
                    }

                    return new SqlResult(results);
                }
            }
        }

        private static String toSqlType(Column column) {
            return switch (column.type()) {
                case "INT" -> "INTEGER";
                case "DEC" -> "NUMERIC";
                default -> "TEXT";
            };
        }

        private static String quoteSqlIdentifier(String identifier) {
            return "\"" + identifier.replace("\"", "\"\"") + "\"";
        }

        public record SqlResult(List<Map<String, Object>> rows) {
            public SqlResult {
                rows = List.copyOf(rows);
            }

            public boolean isEmpty() {
                return this.rows.isEmpty();
            }

            public int size() {
                return this.rows.size();
            }

            public Optional<Map<String, Object>> firstRow() {
                return this.rows.isEmpty() ? Optional.empty() : Optional.of(this.rows.getFirst());
            }

            public Optional<Object> firstValue() {
                return firstRow()
                        .filter(row -> !row.isEmpty())
                        .map(row -> row.values().iterator().next());
            }

            public <T> Optional<T> firstValue(Class<T> type) {
                Objects.requireNonNull(type, "type");
                return firstValue().map(value -> convertValue(value, type));
            }

            public List<Object> column(String name) {
                Objects.requireNonNull(name, "name");

                List<Object> values = new ArrayList<>(this.rows.size());
                for (Map<String, Object> row : this.rows) {
                    values.add(row.get(name));
                }

                return values;
            }

            public <T> List<T> column(String name, Class<T> type) {
                Objects.requireNonNull(type, "type");
                return column(name).stream()
                        .map(value -> convertValue(value, type))
                        .toList();
            }

            private static <T> T convertValue(Object value, Class<T> type) {
                if (value == null)
                    return null;

                if (type.isInstance(value))
                    return type.cast(value);

                if (type == String.class)
                    return type.cast(String.valueOf(value));

                if (value instanceof Number number) {
                    Object converted = switch (type.getName()) {
                        case "java.lang.Integer" -> number.intValue();
                        case "java.lang.Long" -> number.longValue();
                        case "java.lang.Double" -> number.doubleValue();
                        case "java.lang.Float" -> number.floatValue();
                        case "java.lang.Short" -> number.shortValue();
                        case "java.lang.Byte" -> number.byteValue();
                        default -> null;
                    };

                    if (converted != null)
                        return type.cast(converted);
                }

                throw new ClassCastException("Cannot convert " + value.getClass().getName() + " to " + type.getName());
            }
        }
    }

    public static Table parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return parse(lines);
    }

    public static Table parse(List<String> lines) {
        String header = null;
        int headerIndex = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = stripBom(lines.get(i)).trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue;

            header = line;
            headerIndex = i;
            break;
        }

        if (header == null)
            throw new IllegalArgumentException("NGDP table is missing a header row");

        List<Column> columns = parseHeader(header);
        Map<String, String> metadata = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = headerIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty())
                continue;

            if (line.startsWith("##")) {
                parseMetadataLine(line, metadata);
                continue;
            }

            if (line.startsWith("#"))
                continue;

            rows.add(parseRow(line, columns));
        }

        return new Table(columns, rows, metadata);
    }

    private static List<Column> parseHeader(String header) {
        String[] parts = header.split("\\|", -1);
        List<Column> columns = new ArrayList<>(parts.length);

        for (String part : parts) {
            int bang = part.indexOf('!');
            int colon = part.lastIndexOf(':');

            if (bang <= 0 || colon <= bang + 1 || colon == part.length() - 1)
                throw new IllegalArgumentException("Invalid column definition: " + part);

            String name = part.substring(0, bang);
            String type = part.substring(bang + 1, colon).toUpperCase(Locale.ROOT);
            int size = Integer.parseInt(part.substring(colon + 1));

            columns.add(new Column(name, type, size));
        }

        return columns;
    }

    private static Map<String, Object> parseRow(String line, List<Column> columns) {
        String[] values = line.split("\\|", -1);
        if (values.length != columns.size())
            throw new IllegalArgumentException("Row has " + values.length + " values, but header has " + columns.size() + " columns: " + line);

        Map<String, Object> row = new LinkedHashMap<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            row.put(column.name(), parseValue(values[i], column));
        }

        return row;
    }

    private static Object parseValue(String raw, Column column) {
        if (raw == null || raw.isEmpty())
            return "";

        return switch (column.type()) {
            case "STRING" -> raw;
            case "INT" -> Integer.parseInt(raw);
            case "DEC" -> parseDecimal(raw);
            case "HEX" -> parseHex(raw);
            default -> throw new IllegalArgumentException("Unknown column type: " + column.type());
        };
    }

    private static Object parseDecimal(String raw) {
        BigInteger value = new BigInteger(raw, 10);
        if (value.bitLength() < 63)
            return value.longValue();

        return value.toString();
    }

    private static Object parseHex(String raw) {
        // NGDP headers use size as bytes, so HEX:16 values are 32 hex characters.
        // Keep hashes/config keys as strings; numeric conversion would make them less useful.
        return raw.toLowerCase(Locale.ROOT);
    }

    private static void parseMetadataLine(String line, Map<String, String> metadata) {
        String body = line.substring(2).trim();
        int equals = body.indexOf('=');
        if (equals < 0) {
            metadata.put(body, "");
            return;
        }

        String key = body.substring(0, equals).trim();
        String value = body.substring(equals + 1).trim();
        metadata.put(key, value);
    }

    private static String stripBom(String str) {
        if (!str.isEmpty() && str.charAt(0) == '\ufeff')
            return str.substring(1);

        return str;
    }

    private static String toJson(Table table) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("columns", table.columns().stream()
                .map(column -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", column.name());
                    item.put("type", column.type());
                    item.put("size", column.size());
                    return item;
                })
                .toList());
        root.put("metadata", table.metadata());
        root.put("rows", table.rows());
        return GSON.toJson(root);
    }
}

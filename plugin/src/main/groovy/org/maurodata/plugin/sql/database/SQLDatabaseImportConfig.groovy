package org.maurodata.plugin.sql.database

import groovy.transform.CompileDynamic
import org.yaml.snakeyaml.Yaml

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

/** Vendor-targeted, shared import policies. SQL values remain the responsibility of the selected dialect. */
@CompileDynamic
class SQLDatabaseImportConfig {
    Map<String, Object> rules = [:]

    static SQLDatabaseImportConfig load(String fileName) {
        if (!fileName) return new SQLDatabaseImportConfig()
        Path path = Path.of(fileName)
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Import YAML does not exist: ${path}")
        Object parsed
        Files.newBufferedReader(path).withCloseable { reader -> parsed = new Yaml().load(reader) }
        if (parsed != null && !(parsed instanceof Map)) throw new IllegalArgumentException('Import YAML root must be a mapping')
        Map<String, Object> loadedRules = (Map<String, Object>)(parsed ?: [:])
        loadedRules.each { String schemaPattern, Object schemaRule ->
            Pattern.compile(schemaPattern)
            if (schemaRule instanceof Map) ((Map)schemaRule).each { String tablePattern, Object tableRule ->
                if (tablePattern != '$') Pattern.compile(tablePattern)
                if (tableRule instanceof Map) ((Map)tableRule).each { String columnPattern, Object ignored ->
                    if (columnPattern != '$') Pattern.compile(columnPattern)
                }
            }
        }
        new SQLDatabaseImportConfig(rules: loadedRules)
    }

    private static boolean matches(String expression, String name) {
        name != null && Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(name).matches()
    }

    Map<String, Object> options(String schema, String table = null, String column = null) {
        Map<String, Object> result = [:]
        (rules ?: [:]).each { String schemaPattern, Object schemaRule ->
            if (schemaRule instanceof Map && matches(schemaPattern, schema)) {
                Map schemaMap = (Map)schemaRule
                applyOptions(result, schemaMap['$'] as Map)
                if (table != null) schemaMap.each { String tablePattern, Object tableRule ->
                    if (tablePattern != '$' && tableRule instanceof Map && matches(tablePattern, table)) {
                        Map tableMap = (Map)tableRule
                        applyOptions(result, tableMap['$'] as Map)
                        applyOptions(result, tableMap)
                        if (column != null) tableMap.each { String columnPattern, Object columnRule ->
                            if (columnPattern != '$' && columnRule instanceof Map && matches(columnPattern, column)) applyOptions(result, (Map)columnRule)
                        }
                    }
                }
            }
        }
        result
    }

    private static void applyOptions(Map<String, Object> target, Map source) {
        if (source != null) source.each { key, value -> if (key != '$' && value != null) target[key.toString()] = value }
    }

    boolean excluded(String schema, String table = null, String column = null) {
        options(schema).exclude == true || (table != null && options(schema, table).exclude == true) ||
            (column != null && options(schema, table, column).exclude == true)
    }
}

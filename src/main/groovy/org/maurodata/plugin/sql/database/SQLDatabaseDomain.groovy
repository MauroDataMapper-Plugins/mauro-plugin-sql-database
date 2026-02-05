package org.maurodata.plugin.sql.database

import groovy.transform.CompileStatic

import java.sql.Connection
import java.sql.PreparedStatement
import javax.sql.DataSource

@CompileStatic
abstract class SQLDatabaseDomain {

    // namespaces
    abstract String getNAMESPACE()

    // Settings
    int getACCEPT_ENUMERATION_VALUES() {
        80
    }

    int getMAX_ENUMERATION_VALUES() {
        20000
    }

    int getMAX_ENUMERATION_VALUE_LENGTH() {
        100
    }

    int getWARN_LARGE_ENUMERATION_TYPE() {
        1000
    }

    long getSUMMARY_METADATA_FLOOR() {
        0L
    }

    int getCHUNK_SIZE() {
        32
    }

    // Connecting
    abstract DataSource getDatasource(final Map<String, Object> params, final String databaseName)

    abstract Connection getConnection(final DataSource dataSource, final Map<String, Object> params)

    abstract String queryForConnectionTest()

    abstract List<Object> connectionTestAssert()

    abstract boolean canReadTable(Connection connection, String catalogName, String schemaName, String tableName)

    // Querying
    abstract PreparedStatement queryForCatalogs(Connection connection, String catalogName)

    abstract PreparedStatement queryForSchema(Connection connection, String catalogName, List<String> schemaNames = [], List<String> excludeSchemaNames = [])

    abstract List<PreparedStatement> queryForTables(Connection connection, String catalogName, List<String> schemaNames, List<String> excludeSchemaNames,
                                              List<String> excludeTablesLike, List<String> includeTablesLike)

    abstract PreparedStatement queryForColumns(Connection connection, String catalogName, List<String> schemaNames, List<String> excludeSchemaNames,
                                               List<String> excludeTablesLike, List<String> includeTablesLike)

    abstract String lookupCodeDescriptionSql(final String display, final String value, final String table, final String identifier)

    abstract String queryForEnumerationValues(String catalogName, String schemaName, String tableName, String columnName, String codeValue, String description,
                                              long MAX_ENUMERATION_VALUES)

    abstract String queryForSummaryMetadataForEnumerations(String catalogName, String schemaName, String tableName, String columnName, String codeValue, String count,
                                                           long MAX_ENUMERATION_VALUES)

    abstract String queryForSummaryMetadataForDateCenturies(String intervalLabel, String count, String century, String catalogName, String schemaName, String tableName,
                                                          String asLabel)

    abstract String queryForSummaryMetadataForDateDecades(String intervalLabel, String count, String decade, String catalogName, String schemaName, String tableName,
                                                          String asLabel)

    abstract String queryForSummaryMetadataForDateYears(String intervalLabel, String count, String year, String catalogName, String schemaName, String tableName,
                                                        String asLabel)

    abstract String queryForSummaryMetadataForDateMonthsYears(String intervalLabel, String count, String year, String month, String catalogName, String schemaName,
                                                              String tableName, String asLabel)

    abstract String queryForSummaryMetadataForDateDaysMonthsYears(String intervalLabel, String count, String year, String month, String day, String catalogName,
                                                                  String schemaName, String tableName, String asLabel)

    abstract String queryForSummaryMetadataForInteger(String intervalLabel, String count, String binStart, String catalogName, String schemaName, String tableName,
                                                      String asLabel)


    // Interpreting results
    abstract Map<String, String> enumerationValuesToMapString(String enumerationValues)

    abstract Map<String, Long> enumerationSummaryMetadataValuesToMapLong(String enumerationSummaryMetadataValues)

    abstract Map<String, Long> enumerationSummaryMetadataIntervalsToMapLong(String enumerationSummaryMetadataIntervals)


    // Query fragments
    String valueExpressionAsLabel(String valueExpression, String asLabel) {
        "${valueExpression} AS ${escapeIdentifier(asLabel)}"
    }

    String countAll() {
        'count(*)'
    }

    String countColumn(String columnName) {
        "count(${escapeIdentifier(columnName)})"
    }

    String countDistinct(String columnName) {
        "count(distinct ${escapeIdentifier(columnName)})"
    }

    String min(String columnName) {
        "min(${escapeIdentifier(columnName)})"
    }

    String max(String columnName) {
        "max(${escapeIdentifier(columnName)})"
    }

    String concat(List items) {
        items = items*.toString(); "concat(${items.join(', ')})"
    }

    String joinSelects(List<String> selectStatements){
        return 'SELECT ' + selectStatements.join(', ')
    }

    abstract String countWhere(String columnName, String whereClause)

    abstract String greatest(final String a, final String b)

    abstract String normaliseEnumerationValueSql(String valueExpression)

    abstract String centuryFromDate(String columnName)

    abstract String decadeFromDate(String columnName)

    abstract String yearFromDate(String columnName)

    abstract String monthFromDate(String columnName)

    abstract String dayFromDate(String columnName)

    abstract String twoDigits(String valueExpression)

    String binStart(Number lowestBinValue, Number binInterval, String columnName) {
        "${lowestBinValue} + floor( (${escapeIdentifier(columnName)} - ${lowestBinValue}) / ${binInterval} ) * ${binInterval}"
    }

    // Data types
    abstract boolean isString(final String label)

    abstract boolean isInteger(final String label)

    abstract boolean isDecimal(final String label)

    abstract boolean isDate(final String label)

    abstract boolean isDateTime(final String label)

    abstract boolean isTime(final String label)

    abstract boolean isLOB(final String label)

    // SQL escapes
    abstract String escapeIdentifier(final String name)

    // Paths
    String pathToTable(String catalogName, String schemaName, String tableName) {"${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}"}
}
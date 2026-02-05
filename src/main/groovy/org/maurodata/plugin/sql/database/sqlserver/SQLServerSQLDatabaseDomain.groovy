package org.maurodata.plugin.sql.database.sqlserver

import org.maurodata.plugin.sql.database.SQLDatabaseDomain
import org.maurodata.plugin.sql.database.SQLDatabaseDomainUtils

import com.microsoft.sqlserver.jdbc.SQLServerDataSource
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

@Slf4j
@CompileStatic
class SQLServerSQLDatabaseDomain extends SQLDatabaseDomain {

    // namespaces
    String getNAMESPACE(){SQLServerSQLDatabaseDomain.packageName}

    // Defaults
    private final static String DEFAULT_DATABASE_HOST='localhost'
    private final static int DEFAULT_DATABASE_PORT=1433
    private final static String DEFAULT_AUTHENTICATION_SCHEME='ntlm'

    // Connecting
    DataSource getDatasource(final Map<String,Object> params, final String databaseName){
        SQLServerDataSource sqlServerDataSource = new SQLServerDataSource()

        sqlServerDataSource.setServerName(params.databaseHost ? params.databaseHost as String: DEFAULT_DATABASE_HOST)
        sqlServerDataSource.setPortNumber(params.databasePort ? (params.databasePort as Integer).intValue() : DEFAULT_DATABASE_PORT)
        sqlServerDataSource.setDatabaseName(databaseName)
        sqlServerDataSource.setTrustServerCertificate(true)

        String authScheme = params.authenticationScheme ?: DEFAULT_AUTHENTICATION_SCHEME
        if (!(authScheme.toLowerCase() in ['nativeauthentication', DEFAULT_AUTHENTICATION_SCHEME, 'javakerberos'])) authScheme = DEFAULT_AUTHENTICATION_SCHEME
        sqlServerDataSource.setAuthenticationScheme(authScheme)

        if(params.integratedSecurity !=null ) {
            sqlServerDataSource.setIntegratedSecurity(params.integratedSecurity as boolean)
        } else {
            sqlServerDataSource.setIntegratedSecurity(false)
        }

        if (params.serverInstance) {sqlServerDataSource.setInstanceName(params.serverInstance as String)}

        if (params.databaseSSL) {
            sqlServerDataSource.setEncrypt('true')
        }

        sqlServerDataSource.setApplicationName('Mauro-Data-Mapper')

        if (params.domain) {
            sqlServerDataSource.setDomain(params.domain as String)
        }

        if (params.username && params.password) {
            sqlServerDataSource.setUser(params.username as String)
            sqlServerDataSource.setPassword(params.password as String)
        }

        return sqlServerDataSource
    }

    Connection getConnection(final DataSource dataSource, final Map<String,Object> params){
        log.warn 'Getting connection'
        return dataSource.getConnection()
    }

    String queryForConnectionTest(){
        'select 1'
    }

    List<Object> connectionTestAssert(){
        [1] as List<Object>
    }

    // Querying
    PreparedStatement queryForCatalogs(Connection connection, String catalogName){
        PreparedStatement catalogStatement = connection.prepareStatement('select name AS CATALOG_NAME, SUSER_SNAME(owner_sid) AS CATALOG_OWNER, * from sys.databases where name = ?')
        catalogStatement.setString(1, catalogName)
        catalogStatement
    }

    PreparedStatement queryForSchema(Connection connection, String catalogName, List<String> schemaNames = [], List<String> excludeSchemaNames = []){
        final StringBuilder sb=new StringBuilder(255)
        sb.append("SELECT * FROM INFORMATION_SCHEMA.SCHEMATA WHERE CATALOG_NAME = ? AND schema_name NOT IN ('INFORMATION_SCHEMA','sys','guest') AND SCHEMA_ID(SCHEMA_NAME) < 16384")

        if (!schemaNames.isEmpty()) {
            final String includePlaceholders = schemaNames.collect { "?" }.join(", ")
            sb.append(" AND schema_name IN (${includePlaceholders})")
        }

        if (!excludeSchemaNames.isEmpty()) {
            final String excludePlaceholders = excludeSchemaNames.collect { "?" }.join(", ")
            sb.append(" AND schema_name NOT IN (${excludePlaceholders})")
        }

        final PreparedStatement schemaStatement = connection.prepareStatement(sb.toString())

        int paramIndex = 1
        schemaStatement.setString(paramIndex++, catalogName)
        schemaNames.forEach { String schema -> schemaStatement.setString(paramIndex++, schema)}
        excludeSchemaNames.forEach { String schema -> schemaStatement.setString(paramIndex++, schema)}
        schemaStatement
    }

    PreparedStatement queryForTables(Connection connection, String catalogName, List<String> schemaNames, List<String> excludeSchemaNames, List<String> excludeTablesLike, List<String> includeTablesLike){
        final StringBuilder sb=new StringBuilder(384)

        sb.append("SELECT t.name AS table_name, s.name AS table_schema, DB_NAME() AS table_catalog, t.*, s.* FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE DB_NAME() = ? AND s.name NOT IN ('INFORMATION_SCHEMA','sys','guest')")

        if (!schemaNames.isEmpty()) {
            final String includePlaceholders = schemaNames.collect { "?" }.join(", ")
            sb.append(" AND s.name IN (${includePlaceholders})")
        }

        if (!excludeSchemaNames.isEmpty()) {
            final String excludePlaceholders = excludeSchemaNames.collect { "?" }.join(", ")
            sb.append(" AND s.name NOT IN (${excludePlaceholders})")
        }

        if( excludeTablesLike!=null && !excludeTablesLike.isEmpty()) {
            final String excludedTablesQueryFragment = excludeTablesLike.collect { " AND t.name not like ?" }.join(" ")
            sb.append(excludedTablesQueryFragment)
        }

        if( includeTablesLike!=null && !includeTablesLike.isEmpty()) {
            final String includedTablesQueryFragment = " AND ("+includeTablesLike.collect { " t.name like ?" }.join(" OR ")+" )"
            sb.append(includedTablesQueryFragment)
        }

        sb.append(" ORDER BY s.name, t.name")

        log.debug(sb.toString())

        PreparedStatement tablesStatement = connection.prepareStatement(sb.toString())

        int paramIndex = 1
        tablesStatement.setString(paramIndex++, catalogName); log.debug("catalog: ${catalogName}")
        schemaNames.forEach { String schema -> tablesStatement.setString(paramIndex++, schema); log.debug("schema: ${schema}")}
        excludeSchemaNames.forEach { String schema -> tablesStatement.setString(paramIndex++, schema); log.debug("not schema: ${schema}")}
        excludeTablesLike?.forEach { namespace -> tablesStatement.setString(paramIndex++, "%${namespace}%"); log.debug("not table: %${namespace}%")}
        includeTablesLike?.forEach{String like -> tablesStatement.setString(paramIndex++, "%${like}%"); log.debug("table: %${like}%")}
        tablesStatement
    }

    boolean canReadTable(Connection connection, String catalogName, String schemaName, String tableName){
        String query = "select TOP 1 1 from ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}"

        try (PreparedStatement checkStatement = connection.prepareStatement(query)) {

            final ResultSet checkRS = checkStatement.executeQuery()
            checkRS.next()
        }
        catch (SQLException sqle) {
            return false
        }

        return true
    }

    PreparedStatement queryForColumns(Connection connection, String catalogName, List<String> schemaNames, List<String> excludeSchemaNames, List<String> excludeTablesLike, List<String> includeTablesLike){
        final StringBuilder sb=new StringBuilder(512)

        sb.append("SELECT * FROM ${escapeIdentifier(catalogName)}.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_CATALOG = ? AND TABLE_SCHEMA NOT IN ('INFORMATION_SCHEMA','sys','guest')")

        if (!schemaNames.isEmpty()) {
            final String includePlaceholders = schemaNames.collect { "?" }.join(", ")
            sb.append(" AND TABLE_SCHEMA IN (${includePlaceholders})")
        }

        if (!excludeSchemaNames.isEmpty()) {
            final String excludePlaceholders = excludeSchemaNames.collect { "?" }.join(", ")
            sb.append(" AND TABLE_SCHEMA NOT IN (${excludePlaceholders})")
        }

        if( excludeTablesLike!=null && !excludeTablesLike.isEmpty()) {
            final String excludedTablesQueryFragment = excludeTablesLike.collect { " AND TABLE_NAME not like ?" }.join(" ")
            sb.append(excludedTablesQueryFragment)
        }

        if( includeTablesLike!=null && !includeTablesLike.isEmpty()) {
            final String includedTablesQueryFragment = " AND ("+includeTablesLike.collect { " TABLE_NAME like ?" }.join(" OR ")+" )"
            sb.append(includedTablesQueryFragment)
        }

        sb.append(" ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION")

        PreparedStatement columnsStatement = connection.prepareStatement(sb.toString())

        int paramIndex = 1
        columnsStatement.setString(paramIndex++, catalogName)
        schemaNames.forEach { String schema -> columnsStatement.setString(paramIndex++, schema)}
        excludeSchemaNames.forEach { String schema -> columnsStatement.setString(paramIndex++, schema)}
        excludeTablesLike?.forEach { namespace -> columnsStatement.setString(paramIndex++, "%$namespace%")}
        includeTablesLike?.forEach{String like -> columnsStatement.setString(paramIndex++, "%$like%")}
        columnsStatement
    }

    String lookupCodeDescriptionSql(final String display, final String value, final String table, final String identifier){
        return """(SELECT TOP 1 ${escapeIdentifier(display)}
FROM ${escapeIdentifier(table)}
WHERE ${escapeIdentifier(value)} = ${identifier} AND ${escapeIdentifier(display)} IS NOT NULL
ORDER BY (SELECT NULL)
)"""
    }

    String queryForEnumerationValues(String catalogName, String schemaName, String tableName, String columnName, String codeValue, String description, long MAX_ENUMERATION_VALUES){
        valueExpressionAsLabel("""
                (
                    SELECT CAST( STRING_AGG(json_row, CHAR(10)) AS NVARCHAR(MAX) )
                    FROM
                    (
                        SELECT CAST( (SELECT cv AS [code_value], d AS [description] FOR JSON PATH, WITHOUT_ARRAY_WRAPPER ) AS NVARCHAR(MAX)) AS json_row
                        FROM
                        (
                            SELECT DISTINCT
                                ${escapeIdentifier(columnName)},
                                ${codeValue} AS [cv],
                                ${description} AS [d]
                            FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                            GROUP BY ${escapeIdentifier(columnName)}
                            ORDER BY ${escapeIdentifier(columnName)}
                            OFFSET 0 ROWS FETCH NEXT ${MAX_ENUMERATION_VALUES} ROWS ONLY
                        ) AS d
                    ) AS t
                )
                """,
            columnName
        )
    }

    String queryForSummaryMetadataForEnumerations(String catalogName, String schemaName, String tableName, String columnName, String codeValue, String count, long MAX_ENUMERATION_VALUES){
        valueExpressionAsLabel("""
        (
            SELECT CAST( STRING_AGG(json_row, CHAR(10)) AS NVARCHAR(MAX) )
            FROM
            (
                SELECT CAST( (SELECT v AS [value], c AS [count] FOR JSON PATH, WITHOUT_ARRAY_WRAPPER ) AS NVARCHAR(MAX)) AS json_row
                FROM
                (
                    SELECT DISTINCT
                        ${escapeIdentifier(columnName)},
                        ${codeValue} AS [v],
                        ${count} AS [c]
                    FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    GROUP BY ${escapeIdentifier(columnName)}
                    ORDER BY ${escapeIdentifier(columnName)}
                    OFFSET 0 ROWS FETCH NEXT ${MAX_ENUMERATION_VALUES} ROWS ONLY
                ) AS d
            ) AS t
        )
        """,
        columnName
        )
    }

    String queryForSummaryMetadataForDateCenturies(String intervalLabel, String count, String century, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT CAST( STRING_AGG(json_row, CHAR(10)) WITHIN GROUP (ORDER BY century) AS NVARCHAR(MAX) )
                FROM
                (
                    SELECT
                        CAST( (SELECT i AS [interval], c AS [count] FOR JSON PATH, WITHOUT_ARRAY_WRAPPER ) AS NVARCHAR(MAX)) AS json_row,
                        century
                    FROM
                    (
                        SELECT
                            ${intervalLabel} AS i,
                            ${count} AS c,
                            century
                        FROM
                        (
                            SELECT
                                century,
                                count(*) count
                            FROM
                            (
                                SELECT ${century} AS century
                                FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                            ) AS x
                            WHERE century IS NOT NULL
                            GROUP BY century
                        ) AS z
                    ) AS d
                ) AS t
            )
            """,
            asLabel)
    }


    String queryForSummaryMetadataForDateDecades(String intervalLabel, String count, String decade, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT CAST( STRING_AGG(json_row, CHAR(10)) WITHIN GROUP (ORDER BY decade) AS NVARCHAR(MAX) )
                FROM
                (
                    SELECT
                        CAST( (SELECT i AS [interval], c AS [count] FOR JSON PATH, WITHOUT_ARRAY_WRAPPER ) AS NVARCHAR(MAX)) AS json_row,
                        decade
                    FROM
                    (
                        SELECT
                            ${intervalLabel} AS i,
                            ${count} AS c,
                            decade
                        FROM
                        (
                            SELECT
                                decade,
                                count(*) count
                            FROM
                            (
                                SELECT ${decade} AS decade
                                FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                            ) AS x
                            WHERE decade IS NOT NULL
                            GROUP BY decade
                        ) AS z
                    ) AS d
                ) AS t
            )
            """,
            asLabel)
    }

    String queryForSummaryMetadataForDateYears(String intervalLabel, String count, String year, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT CAST( STRING_AGG(json_row, CHAR(10)) WITHIN GROUP (ORDER BY [year]) AS NVARCHAR(MAX) )
                FROM
                (
                    SELECT
                        CAST( (SELECT i AS [interval], c AS [count] FOR JSON PATH, WITHOUT_ARRAY_WRAPPER ) AS NVARCHAR(MAX)) AS json_row,
                        [year]
                    FROM
                    (
                        SELECT
                            ${intervalLabel} AS i,
                            ${count} AS c,
                            [year]
                        FROM
                        (
                            SELECT
                                [year],
                                count(*) count
                            FROM
                            (
                                SELECT ${year} AS [year]
                                FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                            ) AS x
                            WHERE [year] IS NOT NULL
                            GROUP BY [year]
                        ) AS z
                    ) AS d
                ) AS t
            )
            """, asLabel)
    }
    String queryForSummaryMetadataForDateMonthsYears(String intervalLabel, String count, String year, String month, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT CAST( STRING_AGG(json_row, CHAR(10)) WITHIN GROUP (ORDER BY [year], [month]) AS NVARCHAR(MAX) )
                FROM
                (
                    SELECT
                        CAST( (SELECT i AS [interval], c AS [count] FOR JSON PATH, WITHOUT_ARRAY_WRAPPER ) AS NVARCHAR(MAX)) AS json_row,
                        [year], [month]
                    FROM
                    (
                        SELECT
                            ${intervalLabel} AS i,
                            ${count} AS c,
                            [year], [month]
                        FROM
                        (
                            SELECT
                                [year],
                                [month],
                                count(*) count
                            FROM
                            (
                                SELECT
                                    ${year} AS [year],
                                    ${month} AS [month]
                                FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                            ) AS x
                            WHERE [year] IS NOT NULL AND [month] IS NOT NULL
                            GROUP BY [year], [month]
                        ) AS z
                    ) AS d
                ) AS t
            )
            """, asLabel)
    }
    String queryForSummaryMetadataForDateDaysMonthsYears(String intervalLabel, String count, String year, String month, String day, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT CAST( STRING_AGG(json_row, CHAR(10)) WITHIN GROUP (ORDER BY [year], [month], [day]) AS NVARCHAR(MAX) )
                FROM
                (
                    SELECT
                        CAST( (SELECT i AS [interval], c AS [count] FOR JSON PATH, WITHOUT_ARRAY_WRAPPER ) AS NVARCHAR(MAX)) AS json_row,
                        [year], [month], [day]
                    FROM
                    (
                        SELECT
                            ${intervalLabel} AS i,
                            ${count} AS c,
                            [year], [month], [day]
                        FROM
                        (
                            SELECT
                                [year],
                                [month],
                                [day],
                                count(*) count
                            FROM
                            (
                                SELECT
                                    ${year} AS [year],
                                    ${month} AS [month],
                                    ${day} AS [day]
                                FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                            ) AS x
                            WHERE [year] IS NOT NULL AND [month] IS NOT NULL AND [day] IS NOT NULL
                            GROUP BY [year], [month], [day]
                        ) AS z
                    ) AS d
                ) AS t
            )
            """, asLabel)
    }

    String queryForSummaryMetadataForInteger(String intervalLabel, String count, String binStart, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT CAST( STRING_AGG(json_row, CHAR(10)) WITHIN GROUP (ORDER BY binStart) AS NVARCHAR(MAX) )
                FROM
                (
                    SELECT
                        CAST( (SELECT i AS [interval], c AS [count] FOR JSON PATH, WITHOUT_ARRAY_WRAPPER ) AS NVARCHAR(MAX)) AS json_row,
                        binStart
                    FROM
                    (
                        SELECT
                            ${intervalLabel} AS i,
                            ${count} AS c,
                            binStart
                        FROM
                        (
                            SELECT
                                binStart,
                                count(*) count
                            FROM
                            (
                                SELECT ${binStart} AS binStart
                                FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                            ) AS x
                            WHERE binStart IS NOT NULL
                            GROUP BY binStart
                        ) AS z
                    ) AS d
                ) AS t
            )
            """, asLabel)
    }

    // Interpreting results

    Map<String,String> enumerationValuesToMapString(String enumerationValues){
        return SQLDatabaseDomainUtils.jsonObjectLinesToMapString(enumerationValues, 'code_value', 'description')
    }

    Map<String,Long> enumerationSummaryMetadataValuesToMapLong(String enumerationSummaryMetadataValues){
        return SQLDatabaseDomainUtils.jsonObjectLinesToMapLong(enumerationSummaryMetadataValues, 'value', 'count')
    }

    Map<String,Long> enumerationSummaryMetadataIntervalsToMapLong(String enumerationSummaryMetadataIntervals){
        return SQLDatabaseDomainUtils.jsonObjectLinesToMapLong(enumerationSummaryMetadataIntervals, 'interval', 'count')
    }

    // Query fragments
    String valueExpressionAsLabel(String valueExpression, String asLabel){"${valueExpression} AS ${escapeIdentifier(asLabel)}"}
    String countAll(){'count(*)'}
    String countColumn(String columnName) {"count(${escapeIdentifier(columnName)})"}
    String countDistinct(String columnName){"count(distinct ${escapeIdentifier(columnName)})"}
    String min(String columnName){"min(${escapeIdentifier(columnName)})"}
    String max(String columnName){"max(${escapeIdentifier(columnName)})"}
    String concat(List items){items = items*.toString(); "concat(${items.join(', ')})"}
    String countWhere(String columnName, String whereClause){
        return "count(CASE WHEN ${whereClause} THEN 1 END)"
    }
    String greatest(final String a, final String b) {
        "(SELECT CASE WHEN ${a} > ${b} THEN ${a} ELSE ${b} END)"
    }

    String normaliseEnumerationValueSql(String valueExpression){
        """REPLACE(
    ISNULL(
        NULLIF(
            ISNULL(
                REPLACE(
                    LEFT(LTRIM(RTRIM(${valueExpression})), ${MAX_ENUMERATION_VALUE_LENGTH}),
                    '\\0', '�'
                ),
                '<null>'
            ),
            ''
        ),
        '<blank>'
    ),
    '\\\\', '\\\\\\\\'
)
"""
    }

    String centuryFromDate(String columnName){"floor(YEAR(${escapeIdentifier(columnName)})/100)*100"}
    String decadeFromDate(String columnName){"floor(YEAR(${escapeIdentifier(columnName)})/10)*10"}
    String yearFromDate(String columnName){"YEAR(${escapeIdentifier(columnName)})"}
    String monthFromDate(String columnName){"MONTH(${escapeIdentifier(columnName)})"}
    String dayFromDate(String columnName){"DAY(${escapeIdentifier(columnName)})"}
    String twoDigits(String valueExpression){"RIGHT('00' + CAST(${valueExpression} AS VARCHAR(2)), 2)"}
    String binStart(Number lowestBinValue, Number binInterval, String columnName){"${lowestBinValue} + floor( (${escapeIdentifier(columnName)} - ${lowestBinValue}) / ${binInterval} ) * ${binInterval}"}

    // Data types
    boolean isString(final String label) {
        label.toUpperCase() in [
            'CHAR', 'NCHAR', 'VARCHAR', 'NVARCHAR', 'TEXT', 'NTEXT'
        ]
    }

    boolean isInteger(final String label) {
        label.toUpperCase() in [
            'BIGINT', 'INT', 'SMALLINT', 'TINYINT'
        ]
    }

    boolean isDecimal(final String label) {
        label.toUpperCase() in [
            'DECIMAL', 'NUMERIC', 'FLOAT', 'REAL', 'MONEY', 'SMALLMONEY'
        ]
    }

    boolean isDate(final String label) {
        label.toUpperCase() in [
            'DATE'
        ]
    }

    boolean isDateTime(final String label) {
        label.toUpperCase() in [
            'DATETIME', 'DATETIME2', 'SMALLDATETIME', 'DATETIMEOFFSET'
        ]
    }

    boolean isTime(final String label) {
        label.toUpperCase() in [
            'TIME'
        ]
    }

    boolean isLOB(final String label) {
        label.toUpperCase() in [
            'TEXT', 'NTEXT', 'IMAGE'
        ]
    }

    // SQL escapes
    String escapeIdentifier(final String name){
        return "[${name.replace(']', ']]')}]"
    }
}

package org.maurodata.plugin.sql.database.oracle

import org.maurodata.plugin.sql.database.SQLDatabaseDomain
import org.maurodata.plugin.sql.database.SQLDatabaseDomainUtils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import oracle.ucp.jdbc.JDBCConnectionPoolStatistics
import oracle.ucp.jdbc.PoolDataSource
import oracle.ucp.jdbc.PoolDataSourceFactory

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

@Slf4j
@CompileStatic
class OracleSQLDatabaseDomain extends SQLDatabaseDomain {

    // namespaces
    String getNAMESPACE(){
        OracleSQLDatabaseDomain.packageName}

    // Defaults
    private final static int MIN_POOL_SIZE=8
    private final static int MAX_POOL_SIZE=96
    private final static String DEFAULT_DATABASE_HOST='localhost'
    private final static int DEFAULT_DATABASE_PORT=1521

    // Connecting
    DataSource getDatasource(final Map<String,Object> params, final String databaseName){

        // databaseName == Service name (points to a Pluggable database) e.g. FREEPDB1, XEPDB1

        log.debug('getDatasource '+databaseName)
        log.debug(params.toString())

        PoolDataSource poolDataSource = PoolDataSourceFactory.getPoolDataSource()

        final String serverName = params.databaseHost ? params.databaseHost as String: DEFAULT_DATABASE_HOST
        final int portNumber = params.databasePort ? (params.databasePort as Integer).intValue() : DEFAULT_DATABASE_PORT

        poolDataSource.setURL("jdbc:oracle:thin:@${URLEncoder.encode(serverName, 'UTF-8')}:${portNumber}/${URLEncoder.encode(databaseName, 'UTF-8')}")

        poolDataSource.setConnectionFactoryClassName('oracle.jdbc.pool.OracleDataSource')
        poolDataSource.setMinPoolSize(MIN_POOL_SIZE)
        poolDataSource.setMaxPoolSize(MAX_POOL_SIZE)
        poolDataSource.setServerName(serverName)
        poolDataSource.setPortNumber(portNumber)
        poolDataSource.setConnectionPoolName(getNAMESPACE())

        if (params.username && params.password) {
            poolDataSource.setUser(params.username as String)
            poolDataSource.setPassword(params.password as String)
        } else {
            log.warn("No username / password configured")
        }

        log.debug(poolDataSource.getURL())

        return poolDataSource
    }

    Connection getConnection(final DataSource dataSource, final Map<String,Object> params){

        PoolDataSource pool = ((PoolDataSource) dataSource)

        JDBCConnectionPoolStatistics statistics = pool.getStatistics()

        if(statistics) {
            if (statistics.getRemainingPoolCapacityCount() == 0) {
                log.warn('Connection pool saturated: threads waiting')
            }
        }

        return dataSource.getConnection()
    }

    String queryForConnectionTest(){
        'SELECT 1 FROM dual'
    }

    List<Object> connectionTestAssert(){
        [1] as List<Object>
    }

    // Querying
    PreparedStatement queryForCatalogs(Connection connection, String catalogName) {
        // The catalogName is the databaseName is the serviceName
        PreparedStatement catalogStatement = connection.prepareStatement("""\
SELECT
    SYS_CONTEXT('USERENV', 'CON_NAME')       AS "catalog_name",
    SYS_CONTEXT('USERENV', 'CON_NAME')       AS "pdb_name",
    SYS_CONTEXT('USERENV', 'SERVICE_NAME')   AS "service_name",
    SYS_CONTEXT('USERENV', 'DB_NAME')        AS "cdb_name",
    SYS_CONTEXT('USERENV', 'INSTANCE_NAME')  AS "instance_name"
FROM dual
""".toString())
        catalogStatement
    }

    PreparedStatement queryForSchema(Connection connection, String catalogName, List<String> schemaNames = [], List<String> excludeSchemaNames = []){

        final StringBuilder sb=new StringBuilder(255)

        sb.append("""select ${escapeString(catalogName)} AS "catalog_name", au.username AS "schema_name", au.*
            from ALL_USERS au
            where
                au.USERNAME <> 'PDBADMIN' AND au.ORACLE_MAINTAINED = 'N'
""")

        if (!schemaNames.isEmpty()) {
            final String includePlaceholders = schemaNames.collect { "?" }.join(", ")
            sb.append(" AND au.USERNAME IN (${includePlaceholders})")
        }

        if (!excludeSchemaNames.isEmpty()) {
            final String excludePlaceholders = excludeSchemaNames.collect { "?" }.join(", ")
            sb.append(" AND au.USERNAME NOT IN (${excludePlaceholders})")
        }

        PreparedStatement schemaStatement = connection.prepareStatement(sb.toString())
        int paramIndex = 1
        schemaNames.forEach { String schema -> schemaStatement.setString(paramIndex++, schema)}
        excludeSchemaNames.forEach { String schema -> schemaStatement.setString(paramIndex++, schema)}

        schemaStatement
    }

    PreparedStatement queryForTables(Connection connection, String catalogName, List<String> schemaNames, List<String> excludeSchemaNames, List<String> excludeTablesLike, List<String> includeTablesLike){

        final StringBuilder sb=new StringBuilder(384)
        sb.append(
            """\
                SELECT ${escapeString(catalogName)} AS "table_catalog", t.OWNER AS "table_schema", t.TABLE_NAME AS "table_name", t.*
                FROM ALL_TABLES t,
                ALL_USERS au
                WHERE
                    t.OWNER = au.username AND au.USERNAME <> 'PDBADMIN' AND au.ORACLE_MAINTAINED = 'N'
                    AND
                    t.OWNER NOT IN (
                        'SYS','SYSTEM','OUTLN','XDB','WMSYS','ORDSYS','CTXSYS',
                        'MDSYS','OLAPSYS','ORDDATA','ORDPLUGINS',
                        'SI_INFORMTN_SCHEMA','DBSNMP','GSMADMIN_INTERNAL','PDBADMIN')
            """.toString()
        )

        if (!schemaNames.isEmpty()) {
            final String includePlaceholders = schemaNames.collect { "?" }.join(", ")
            sb.append(" AND t.OWNER IN (${includePlaceholders})")
        }

        if (!excludeSchemaNames.isEmpty()) {
            final String excludePlaceholders = excludeSchemaNames.collect { "?" }.join(", ")
            sb.append(" AND t.OWNER NOT IN (${excludePlaceholders})")
        }

        if( excludeTablesLike!=null && !excludeTablesLike.isEmpty()) {
            final String excludedTablesQueryFragment = excludeTablesLike.collect { " AND t.TABLE_NAME NOT LIKE ?" }.join(" ")
            sb.append(excludedTablesQueryFragment)
        }

        if( includeTablesLike!=null && !includeTablesLike.isEmpty()) {
            final String includedTablesQueryFragment = includeTablesLike.collect { " AND t.TABLE_NAME NOT LIKE ?" }.join(" ")
            sb.append(includedTablesQueryFragment)
        }

        sb.append("""
                ORDER BY t.OWNER, t.TABLE_NAME
        """.toString())

        PreparedStatement tablesStatement = connection.prepareStatement(sb.toString())

        int paramIndex = 1
        schemaNames.forEach { String schema -> tablesStatement.setString(paramIndex++, schema)}
        excludeSchemaNames.forEach { String schema -> tablesStatement.setString(paramIndex++, schema)}
        excludeTablesLike?.forEach { namespace -> tablesStatement.setString(paramIndex++, "%$namespace%")}
        includeTablesLike?.forEach{String like -> tablesStatement.setString(paramIndex++, "%$like%")}

        tablesStatement
    }

    boolean canReadTable(Connection connection, String catalogName, String schemaName, String tableName){

        String query = "select 1 from ${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)} FETCH FIRST 1 ROWS ONLY"

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
        sb.append("""\
                SELECT
                ${escapeString(catalogName)} AS "table_catalog",
                c.OWNER           AS "table_schema",
                c.TABLE_NAME      AS "table_name",
                c.COLUMN_NAME     AS "column_name",
                c.DATA_TYPE       AS "underlying_data_type",
                c.DATA_LENGTH     AS "data_length",
                c.DATA_PRECISION  AS "data_precision",
                c.DATA_SCALE      AS "data_scale",
                c.NULLABLE        AS "is_nullable",
                c.COLUMN_ID       AS "ordinal_position",
                CASE
                    WHEN c.DATA_TYPE = 'NUMBER' AND NVL(c.DATA_SCALE,0) = 0 THEN 'INTEGER'
                    WHEN c.DATA_TYPE = 'NUMBER' AND NVL(c.DATA_SCALE,0) > 0 THEN 'DECIMAL'
                ELSE
                    c.DATA_TYPE
                END AS data_type
            FROM 
                ALL_TAB_COLUMNS c,
                ALL_USERS au
            WHERE
                C.OWNER = au.USERNAME AND au.USERNAME <> 'PDBADMIN' AND au.ORACLE_MAINTAINED = 'N'
            """.toString())

        if (!schemaNames.isEmpty()) {
            final String includePlaceholders = schemaNames.collect { "?" }.join(", ")
            sb.append(" AND c.OWNER IN (${includePlaceholders})")
        }

        if (!excludeSchemaNames.isEmpty()) {
            final String excludePlaceholders = excludeSchemaNames.collect { "?" }.join(", ")
            sb.append(" AND c.OWNER NOT IN (${excludePlaceholders})")
        }

        if( excludeTablesLike!=null && !excludeTablesLike.isEmpty()) {
            final String excludedTablesQueryFragment = excludeTablesLike.collect { " AND c.TABLE_NAME not like ?" }.join(" ")
            sb.append(excludedTablesQueryFragment)
        }

        if( includeTablesLike!=null && !includeTablesLike.isEmpty()) {
            final String includedTablesQueryFragment = includeTablesLike.collect { " AND c.TABLE_NAME not like ?" }.join(" ")
            sb.append(includedTablesQueryFragment)
        }

        sb.append("""
            ORDER BY 
                c.OWNER,
                c.TABLE_NAME,
                c.COLUMN_ID
                """.toString())

        PreparedStatement columnsStatement = connection.prepareStatement(sb.toString())

        int paramIndex = 1
        schemaNames.forEach { String schema -> columnsStatement.setString(paramIndex++, schema)}
        excludeSchemaNames.forEach { String schema -> columnsStatement.setString(paramIndex++, schema)}
        excludeTablesLike?.forEach { namespace -> columnsStatement.setString(paramIndex++, "%$namespace%")}
        includeTablesLike?.forEach{String like -> columnsStatement.setString(paramIndex++, "%$like%")}

        columnsStatement
    }

    String lookupCodeDescriptionSql(final String display, final String value, final String table, final String identifier){
        return "(SELECT ${escapeIdentifier(display)} FROM ${escapeIdentifier(table)} WHERE ${escapeIdentifier(value)} = ${identifier} AND ${escapeIdentifier(display)} IS NOT NULL ORDER BY (SELECT NULL) FETCH FIRST 1 ROW ONLY)"
    }

    String queryForEnumerationValues(String catalogName, String schemaName, String tableName, String columnName, String codeValue, String description, long MAX_ENUMERATION_VALUES){
        valueExpressionAsLabel("""
                (
                    SELECT JSON_OBJECTAGG(
                        KEY "code_value"
                        VALUE "description"
                        )
                    FROM (
                        SELECT DISTINCT
                            ${codeValue} AS "code_value",
                            ${description} AS "description"
                        FROM ${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                        GROUP BY ${escapeIdentifier(columnName)}
                        ORDER BY "code_value"
                        FETCH FIRST ${MAX_ENUMERATION_VALUES} ROWS ONLY
                    )
                )
                """,
            columnName
        )
    }

    String queryForSummaryMetadataForEnumerations(String catalogName, String schemaName, String tableName, String columnName, String codeValue, String count, long MAX_ENUMERATION_VALUES){
        valueExpressionAsLabel("""
        (
            SELECT JSON_OBJECTAGG(
                KEY ${escapeIdentifier('__value')}
                VALUE GREATEST("count", ${SUMMARY_METADATA_FLOOR})
                )
            FROM
            (
                SELECT
                    ${codeValue} AS ${escapeIdentifier('__value')},
                    COUNT(*) AS "count"
                FROM ${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                GROUP BY ${codeValue}
                ORDER BY ${codeValue}
                FETCH FIRST ${MAX_ENUMERATION_VALUES} ROWS ONLY
            )
        )
        """,
        columnName
        )
    }

    String queryForSummaryMetadataForDateCenturies(String intervalLabel, String count, String century, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT
                    LISTAGG(
                        JSON_OBJECT(
                            'interval' VALUE "key",
                            'count' VALUE "count"
                        )
                       || CHR(10)
                    )
                    WITHIN GROUP (ORDER BY "century") AS json_lines
                FROM
                (
                    SELECT
                        ${intervalLabel} AS "key"
                        "century",
                        COUNT(*) AS "count"
                    FROM
                    (
                        SELECT ${century} AS "century"
                        FROM ${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    )
                    WHERE "century" IS NOT NULL
                    GROUP BY "century"
                )
            )
            """,
            asLabel)
    }

    String queryForSummaryMetadataForDateDecades(String intervalLabel, String count, String decade, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT
                    LISTAGG(
                        JSON_OBJECT(
                            'interval' VALUE "key",
                            'count' VALUE "count"
                        )
                       || CHR(10)
                    )
                    WITHIN GROUP (ORDER BY "decade") AS json_lines
                FROM
                (
                    SELECT
                        ${intervalLabel} AS "key"
                        "decade",
                        COUNT(*) AS "count"
                    FROM
                    (
                        SELECT ${decade} AS "decade"
                        FROM ${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    )
                    WHERE "decade" IS NOT NULL
                    GROUP BY "decade"
                )
            )
            """,
            asLabel)
    }

    String queryForSummaryMetadataForDateYears(String intervalLabel, String count, String year, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT
                    LISTAGG(
                        JSON_OBJECT(
                            'interval' VALUE "key",
                            'count' VALUE "count"
                        )
                        || CHR(10)
                    )
                    WITHIN GROUP (ORDER BY "year") AS json_lines
                FROM
                (
                    SELECT
                        ${intervalLabel} AS "key",
                        "year",
                        COUNT(*) AS "count"
                    FROM
                    (
                        SELECT ${year} AS "year"
                        FROM ${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    )
                    WHERE "year" IS NOT NULL
                    GROUP BY "year"
                )
            )
            """, asLabel)
    }
    String queryForSummaryMetadataForDateMonthsYears(String intervalLabel, String count, String year, String month, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT
                    LISTAGG(
                        JSON_OBJECT(
                            'interval' VALUE "key",
                            'count' VALUE "count"
                        )
                        || CHR(10)
                    )
                    WITHIN GROUP (ORDER BY "year", "month") AS json_lines
                FROM
                (
                    SELECT
                        ${intervalLabel} AS "key",
                        "year",
                        "month",
                        COUNT(*) AS "count"
                    FROM
                    (
                        SELECT
                            ${year} AS "year",
                            ${month} AS "month"
                        FROM ${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    )
                    WHERE "year" IS NOT NULL AND "month" IS NOT NULL
                    GROUP BY "year", "month"
                )
            )
            """, asLabel)
    }
    String queryForSummaryMetadataForDateDaysMonthsYears(String intervalLabel, String count, String year, String month, String day, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT
                    LISTAGG(
                        JSON_OBJECT(
                            'interval' VALUE "key",
                            'count' VALUE "count"
                        )
                        || CHR(10)
                    )
                    WITHIN GROUP (ORDER BY "year", "month", "day") AS json_lines
                FROM
                (
                    SELECT
                        ${intervalLabel} AS "key",
                        "year",
                        "month",
                        "day", 
                        COUNT(*) AS "count"
                    FROM
                    (
                        SELECT
                            ${year} AS "year",
                            ${month} AS "month",
                            ${day} AS "day"
                        FROM ${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    )
                    WHERE "year" IS NOT NULL AND "month" IS NOT NULL AND "day" IS NOT NULL
                    GROUP BY "year", "month", "day"
                )
            )
            """, asLabel)
    }

    String queryForSummaryMetadataForInteger(String intervalLabel, String count, String binStart, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
        (
                SELECT
                    LISTAGG(
                        JSON_OBJECT(
                            'interval' VALUE "key",
                            'count' VALUE "count"
                        )
                        || CHR(10)
                    )
                    WITHIN GROUP (ORDER BY "binStart") AS json_lines
                FROM
                (
                    SELECT
                        ${intervalLabel} AS "key",
                        "binStart",
                        COUNT(*) AS "count"
                    FROM
                    (
                        SELECT ${binStart} AS "binStart"
                        FROM ${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    )
                    WHERE "binStart" IS NOT NULL
                    GROUP BY "binStart"
                )
            )
            """, asLabel)
    }

    // Interpreting results

    Map<String,String> enumerationValuesToMapString(String enumerationValues){
        return SQLDatabaseDomainUtils.jsonObjectToMapString(enumerationValues)
    }

    Map<String,Long> enumerationSummaryMetadataValuesToMapLong(String enumerationSummaryMetadataValues){
        return SQLDatabaseDomainUtils.jsonObjectToMapLong(enumerationSummaryMetadataValues)
    }

    // This is used for the dates, and the integer summary metadata results
    Map<String,Long> enumerationSummaryMetadataIntervalsToMapLong(String enumerationSummaryMetadataIntervals){
        return SQLDatabaseDomainUtils.jsonObjectLinesToMapLong(enumerationSummaryMetadataIntervals, 'interval', 'count')
    }

    // Query fragments
    String valueExpressionAsLabel(String valueExpression, String asLabel){"${valueExpression} ${escapeIdentifier(asLabel)}"}
    String countAll(){'count(*)'}
    String countDistinct(String columnName){"count(distinct ${escapeIdentifier(columnName)})"}
    String min(String columnName){"min(${escapeIdentifier(columnName)})"}
    String max(String columnName){"max(${escapeIdentifier(columnName)})"}
    String concat(List items){items = items*.toString(); "(${items.join(' || ')})"}

    String joinSelects(List<String> selectStatements){
        return 'SELECT ' + selectStatements.join(', ') + ' FROM dual'
    }

    String countWhere(String columnName, String whereClause){
        return "count(CASE WHEN ${whereClause} THEN 1 END)"
    }
    String greatest(final String a, final String b) {
        "GREATEST(${a},${b})"
    }

    String normaliseEnumerationValueSql(String valueExpression){
        """
        REPLACE(
            NVL(
                NULLIF(
                    NVL(
                        TRANSLATE(
                            TRIM(SUBSTR(${valueExpression}, 1, ${MAX_ENUMERATION_VALUE_LENGTH})),
                            '@\$0',
                            '���'
                        ),
                        '<null>'
                    ),
                    ''
                ),
                '<blank>'
            ),
            '\\',
            '\\\\'
        )
        """.toString()
    }

    String centuryFromDate(String columnName){"floor(extract(year from ${escapeIdentifier(columnName)})/100)*100"}
    String decadeFromDate(String columnName){"floor(extract(year from ${escapeIdentifier(columnName)})/10)*10"}
    String yearFromDate(String columnName){"extract(year from ${escapeIdentifier(columnName)})"}
    String monthFromDate(String columnName){"extract(month from ${escapeIdentifier(columnName)})"}
    String dayFromDate(String columnName){"extract(day from ${escapeIdentifier(columnName)})"}
    String twoDigits(String valueExpression){"TO_CHAR(${valueExpression}, 'FM00')"}
    String binStart(long lowestBinValue, long binInterval, String columnName){"${lowestBinValue} + floor( (${escapeIdentifier(columnName)} - ${lowestBinValue}) / ${binInterval} ) * ${binInterval}"}


    // Data types
    boolean isString(final String label) {
        label.toUpperCase() in [
            'CHAR', 'NCHAR', 'VARCHAR', 'VARCHAR2', 'NVARCHAR', 'CLOB', 'NCLOB', 'LONG',
            'CHARACTER','NATIONAL'
        ]
    }

    boolean isInteger(final String label) {
        label.toUpperCase() in [
            'INTEGER',
            'INT','SMALLINT'
        ]
    }

    boolean isDecimal(final String label) {
        label.toUpperCase() in [
            'NUMBER', 'FLOAT',
            'NUMERIC','DECIMAL','DEC',
            'DOUBLE','REAL'
        ]
    }

    boolean isDate(final String label) {
        false
    }

    boolean isDateTime(final String label) {
        label.toUpperCase() in [
            'DATE', 'TIMESTAMP'
        ]
    }

    boolean isTime(final String label) {
        false
    }

    boolean isLOB(final String label) {
        label.toUpperCase() in [
            'BLOB','CLOB','NCLOB','BFILE'
        ]
    }

    // SQL escapes
    String escapeIdentifier(final String name){
        return "\"${name.replace('\"', '\"\"')}\""
    }

    static String escapeString(final String string){
        return "q'[${string}]'"
    }

    String pathToTable(String catalogName, String schemaName, String tableName) {"${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}"}
}

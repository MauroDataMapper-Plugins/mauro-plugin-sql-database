package org.maurodata.plugin.sql.database.databricks

import org.maurodata.plugin.sql.database.SQLDatabaseDomain
import org.maurodata.plugin.sql.database.SQLDatabaseDomainUtils

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariPoolMXBean
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@Slf4j
@CompileStatic
class DatabricksSQLDatabaseDomain extends SQLDatabaseDomain {

    // namespaces
    String getNAMESPACE(){
        DatabricksSQLDatabaseDomain.packageName}

    // Defaults
    private final static int MAX_POOL_SIZE=100

    // Connecting
    DataSource getDatasource(final Map<String,Object> params, final String databaseName){

        HikariConfig config = new HikariConfig()

        config.setJdbcUrl(params.url as String)
        config.addDataSourceProperty('PWD', params.accessToken)
        config.addDataSourceProperty('EnableArrow', '0')
        config.addDataSourceProperty('UseNativeQuery', '1')
        config.setCatalog(escapeIdentifier(databaseName))
        config.setConnectionTimeout(TimeUnit.HOURS.toMillis(1))
        config.setMaxLifetime(0)
        config.setReadOnly(true)
        config.setMaximumPoolSize(MAX_POOL_SIZE)
        return new HikariDataSource(config)
    }

    Connection getConnection(final DataSource dataSource, final Map<String,Object> params){

        HikariPoolMXBean pool = ((HikariDataSource) dataSource).getHikariPoolMXBean()

        log.warn 'Getting connection; {} total connections', pool.totalConnections

        if(pool.getActiveConnections() >= pool.getTotalConnections())
        {
            log.warn("Connection pool saturated: {} threads waiting", pool.getThreadsAwaitingConnection())
        }

        return dataSource.getConnection()
    }

    String queryForConnectionTest(){
        'select size(null), sqrt(-1)'
    }

    List<Object> connectionTestAssert(){
        [null, Double.NaN] as List<Object>
    }

    // Querying
    PreparedStatement queryForCatalogs(Connection connection, String catalogName){
        PreparedStatement catalogStatement = connection.prepareStatement('select * from system.information_schema.catalogs where catalog_name = ?')
        catalogStatement.setString(1, catalogName)
        catalogStatement
    }

    PreparedStatement queryForSchema(Connection connection, String catalogName, List<String> schemaNames = [], List<String> excludeSchemaNames = []){
        PreparedStatement schemaStatement = connection.prepareStatement('select * from system.information_schema.schemata where catalog_name = ? and (? = \'\' or array_contains(split(?, ","), schema_name)) and (? = \'\' or not array_contains(split(?, ","), schema_name)) and schema_name != \'information_schema\'')
        schemaStatement.setString(1, catalogName)
        schemaStatement.setString(2, schemaNames.join(','))
        schemaStatement.setString(3, schemaNames.join(','))
        schemaStatement.setString(4, excludeSchemaNames.join(','))
        schemaStatement.setString(5, excludeSchemaNames.join(','))
        schemaStatement
    }

    PreparedStatement queryForTables(Connection connection, String catalogName, List<String> schemaNames, List<String> excludeSchemaNames, List<String> excludeTablesLike,
                                     List<String> includeTablesLike){
        // Build query fragment for tables that we want to exclude
        String excludedTablesQueryFragment=''
        excludeTablesLike.each {
            excludedTablesQueryFragment += ' AND table_name not like ?'
        }

        String includedTablesQueryFragment = ''
        if( includeTablesLike!=null && !includeTablesLike.isEmpty()) {
            " AND ("+includeTablesLike.collect { " table_name like ?" }.join(" OR ")+" )"
        }

        String tableStatementQuery=
            """
                SELECT * FROM system.information_schema.tables
                WHERE table_catalog = ?
                AND (? = \'\' or array_contains(split(?, ","), table_schema))
                and (? = \'\' or not array_contains(split(?, ","), table_schema))
                and table_schema != \'information_schema\'
                ${excludedTablesQueryFragment}
                ${includedTablesQueryFragment}
                ORDER BY table_schema, table_name
                 """

        PreparedStatement tablesStatement = connection.prepareStatement(tableStatementQuery)
        tablesStatement.setString(1, catalogName)
        tablesStatement.setString(2, schemaNames.join(','))
        tablesStatement.setString(3, schemaNames.join(','))
        tablesStatement.setString(4, excludeSchemaNames.join(','))
        tablesStatement.setString(5, excludeSchemaNames.join(','))

        excludeTablesLike.eachWithIndex { namespace, idx ->
            tablesStatement.setString(6+idx, "%$namespace%")
            log.debug "${6+idx} <- %$namespace%"
        }

        includeTablesLike.eachWithIndex{String like, int idx ->
            tablesStatement.setString(6+excludeTablesLike.size()+idx, "%$like%")
            log.debug "${6+excludeTablesLike.size()+idx} <- %$like%"
        }

        log.info 'tableStatementQuery: {}', tableStatementQuery

        tablesStatement
    }

    boolean canReadTable(Connection connection, String catalogName, String schemaName, String tableName){
        String query = "select 1 from ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)} limit 1"

        try (PreparedStatement checkStatement = connection.prepareStatement(query)) {

            final ResultSet checkRS = checkStatement.executeQuery()
            checkRS.next()
        }
        catch (SQLException sqle) {
            return false
        }

        return true
    }

    PreparedStatement queryForColumns(Connection connection, String catalogName, List<String> schemaNames, List<String> excludeSchemaNames, List<String> excludeTablesLike,
                                      List<String> includeTablesLike){
        String excludedTablesQueryFragment = ''
        excludeTablesLike.each {
            excludedTablesQueryFragment += ' AND table_name not like ?'
        }

        String includedTablesQueryFragment = ''
        if( includeTablesLike!=null && !includeTablesLike.isEmpty()) {
            " AND ("+includeTablesLike.collect { " table_name like ?" }.join(" OR ")+" )"
        }

        String columnsStatementQuery=
            """\
                select * from system.information_schema.columns
                where table_catalog = ? and (? = \'\' or array_contains(split(?, ","), table_schema))
                and (? = \'\' or not array_contains(split(?, ","), table_schema))
                and table_schema != \'information_schema\'
                ${excludedTablesQueryFragment}
                ${includedTablesQueryFragment}
                order by table_schema, table_name, ordinal_position
                """

        PreparedStatement columnsStatement = connection.prepareStatement(columnsStatementQuery)
        columnsStatement.setString(1, catalogName)
        columnsStatement.setString(2, schemaNames.join(','))
        columnsStatement.setString(3, schemaNames.join(','))
        columnsStatement.setString(4, excludeSchemaNames.join(','))
        columnsStatement.setString(5, excludeSchemaNames.join(','))

        excludeTablesLike.eachWithIndex { namespace, idx ->
            columnsStatement.setString(6+idx, "%$namespace%")
        }

        includeTablesLike.eachWithIndex{String like, int idx ->
            columnsStatement.setString(6+excludeTablesLike.size()+idx, "%$like%")
        }

        log.info 'columnsStatementQuery: {}', columnsStatementQuery

        columnsStatement
    }

    String lookupCodeDescriptionSql(final String display, final String value, final String table, final String identifier){
        return '(select first('+display+') from '+table+' where '+value+' = '+identifier+')'
    }

    String queryForEnumerationValues(String catalogName, String schemaName, String tableName, String columnName, String codeValue, String description, long MAX_ENUMERATION_VALUES){
        valueExpressionAsLabel("""
                (
                    select to_json(map_from_entries(array_agg(struct(code_value, description))))
                    from
                    (
                        select distinct
                            ${codeValue} code_value,
                            ${description} description
                        from ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                        group by ${escapeIdentifier(columnName)}
                        order by code_value
                        LIMIT $MAX_ENUMERATION_VALUES
                    )
                )
                """,
            columnName
        )
    }

    String queryForSummaryMetadataForEnumerations(String catalogName, String schemaName, String tableName, String columnName, String codeValue, String count, long MAX_ENUMERATION_VALUES){
        valueExpressionAsLabel("""

        (
            select to_json(map_from_entries(array_agg(struct(__value, greatest(count, ${SUMMARY_METADATA_FLOOR})))))
            from
            (
                select
                    ${codeValue} __value,
                    count(*) count
                from ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                group by __value
                order by __value
                LIMIT ${MAX_ENUMERATION_VALUES}
            )
        )
        """,
        columnName
        )
    }

    String queryForSummaryMetadataForDateCenturies(String intervalLabel, String count, String century, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT to_json( map_from_entries( sort_array( array_agg( struct( ${intervalLabel}, ${count} ) ) ) ) )
                FROM
                (
                    SELECT decade, count(*) count
                    FROM
                    (
                        SELECT ${century} century
                        FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    )
                    WHERE century IS NOT NULL
                    GROUP BY century
                    ORDER BY century
                )
            )
            """,
            asLabel)
    }

    String queryForSummaryMetadataForDateDecades(String intervalLabel, String count, String decade, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT to_json( map_from_entries( sort_array( array_agg( struct( ${intervalLabel}, ${count} ) ) ) ) )
                FROM
                (
                    SELECT decade, count(*) count
                    FROM
                    (
                        SELECT ${decade} decade
                        FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    )
                    WHERE decade IS NOT NULL
                    GROUP BY decade
                    ORDER BY decade
                )
            )
            """,
            asLabel)
    }

    String queryForSummaryMetadataForDateYears(String intervalLabel, String count, String year, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT to_json(map_from_entries(array_agg(struct(${intervalLabel}, ${count}))))
                FROM
                (
                    SELECT
                        year,
                        count(*) count
                    FROM
                    (
                        SELECT ${year} year
                        FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    )
                    WHERE year IS NOT NULL
                    GROUP BY year
                    ORDER BY year
                )
            )
            """, asLabel)
    }
    String queryForSummaryMetadataForDateMonthsYears(String intervalLabel, String count, String year, String month, String catalogName, String schemaName, String tableName,
                                                     String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT to_json(map_from_entries(array_agg(struct(${intervalLabel}, ${count}))))
                FROM
                (
                    SELECT
                        year,
                        month,
                        count(*) count
                    FROM
                    (
                        SELECT
                            ${year} year,
                            ${month} month
                        FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    )
                    WHERE year IS NOT NULL AND month IS NOT NULL
                    GROUP BY year, month
                    ORDER BY year, month
                )
            )
            """, asLabel)
    }
    String queryForSummaryMetadataForDateDaysMonthsYears(String intervalLabel, String count, String year, String month, String day, String catalogName, String schemaName,
                                                         String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT to_json(map_from_entries(array_agg(struct(${intervalLabel}, ${count}))))
                FROM
                (
                    SELECT
                        year,
                        month,
                        day,
                        count(*) count
                    FROM
                    (
                        SELECT
                            ${year} year,
                            ${month} month,
                            ${day} day
                        FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                    )
                    WHERE year IS NOT NULL AND month IS NOT NULL AND day IS NOT NULL
                    GROUP BY year, month, day
                    ORDER BY year, month, day
                )
            )
            """, asLabel)
    }

    String queryForSummaryMetadataForInteger(String intervalLabel, String count, String binStart, String catalogName, String schemaName, String tableName, String asLabel){
        valueExpressionAsLabel("""
            (
                SELECT to_json( map_from_entries( sort_array( array_agg( struct(*) ) ) ) )
                FROM
                (
                    SELECT
                        ${intervalLabel},
                        ${count}
                    FROM
                    (
                        SELECT
                            binStart,
                            count(*) count
                        FROM
                        (
                            SELECT ${binStart} binStart
                            FROM ${escapeIdentifier(catalogName)}.${escapeIdentifier(schemaName)}.${escapeIdentifier(tableName)}
                        )
                        WHERE binStart IS NOT NULL
                        GROUP BY binStart
                        ORDER BY binStart
                    )
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

    Map<String,Long> enumerationSummaryMetadataIntervalsToMapLong(String enumerationSummaryMetadataIntervals){
        return SQLDatabaseDomainUtils.jsonObjectToMapLong(enumerationSummaryMetadataIntervals)
    }

    // Query fragments
    String valueExpressionAsLabel(String valueExpression, String asLabel){"${valueExpression} AS ${escapeIdentifier(asLabel)}"}
    String countAll(){'count(*)'}
    String countDistinct(String columnName){"count(distinct ${escapeIdentifier(columnName)})"}
    String min(String columnName){"min(${escapeIdentifier(columnName)})"}
    String max(String columnName){"max(${escapeIdentifier(columnName)})"}
    String concat(List items){items = items*.toString(); "concat(${items.join(', ')})"}
    String countWhere(String columnName, String whereClause){
        return "count(${escapeIdentifier(columnName)}) FILTER (${whereClause})"
    }
    String greatest(final String a, final String b) {
        "greatest(${a},${b})"
    }

    String normaliseEnumerationValueSql(String valueExpression){
        "replace(nvl(nullif(nvl(translate(trim(substr(${valueExpression}, 1, ${MAX_ENUMERATION_VALUE_LENGTH})), '\\0', '�'), '<null>'), ''), '<blank>'), '\\\\', '\\\\\\\\')"
    }

    String centuryFromDate(String columnName){"floor(extract(year from ${escapeIdentifier(columnName)})/100)*100"}
    String decadeFromDate(String columnName){"floor(extract(year from ${escapeIdentifier(columnName)})/10)*10"}
    String yearFromDate(String columnName){"extract(year from ${escapeIdentifier(columnName)})"}
    String monthFromDate(String columnName){"extract(month from ${escapeIdentifier(columnName)})"}
    String dayFromDate(String columnName){"extract(day from ${escapeIdentifier(columnName)})"}
    String twoDigits(String valueExpression){"LPAD(CAST(${valueExpression} AS STRING), 2, '0')"}
    String binStart(Number lowestBinValue, Number binInterval, String columnName){"${lowestBinValue} + floor( (${escapeIdentifier(columnName)} - ${lowestBinValue}) / ${binInterval} ) * ${binInterval}"}


    // Data types
    boolean isString(final String label) {
        label.toUpperCase() in [
            'STRING'
        ]
    }

    boolean isInteger(final String label) {
        label.toUpperCase() in [
            'BIGINT', 'INT', 'SMALLINT', 'TINYINT', 'LONG'
        ]
    }

    boolean isDecimal(final String label) {
        label.toUpperCase() in [
            'DECIMAL', 'NUMERIC', 'DOUBLE', 'FLOAT'
        ]
    }

    boolean isDate(final String label) {
        label.toUpperCase() in [
            'DATE'
        ]
    }

    boolean isDateTime(final String label) {
        label.toUpperCase() in [
            'TIMESTAMP', 'TIMESTAMP_NTZ'
        ]
    }

    boolean isTime(final String label) {
        false
    }

    boolean isLOB(final String label) {
        label.toUpperCase() in [
            'BINARY','MAP','ARRAY','STRUCT'
        ]
    }

    // SQL escapes
    String escapeIdentifier(final String name){
        return "`${name.replace('`', '``')}`"
    }
}

package org.maurodata.plugin.sql.database

import org.maurodata.plugin.sql.database.databricks.DatabricksSQLDatabaseDomain
import org.maurodata.plugin.sql.database.oracle.OracleSQLDatabaseDomain
import org.maurodata.plugin.sql.database.sqlserver.SQLServerSQLDatabaseDomain
import spock.lang.Specification

class SQLDatabaseLookupDialectSpec extends Specification {

    def 'vendor dialect embeds configured lookup SQL as a derived table'() {
        when:
        String sql = dialect.lookupCodeDescriptionSql('display_name', 'code', "(select display_name, code from lookup_codes where vocabulary = 'ICD10' and retired is null)", 'source.code')

        then:
        sql.toLowerCase().contains('select display_name')
        sql.toLowerCase().contains('from (select display_name, code from lookup_codes where vocabulary =')
        sql.contains("vocabulary = 'ICD10' and retired is null")
        sql.contains('enum_lookup')
        sql.contains('code')

        where:
        dialect << [new SQLServerSQLDatabaseDomain(), new OracleSQLDatabaseDomain(), new DatabricksSQLDatabaseDomain()]
    }

    def 'vendor dialect retains existing physical-table lookup form'() {
        when:
        String sql = dialect.lookupCodeDescriptionSql('display_name', 'code', 'reference.codes', 'source.code')

        then:
        sql.toLowerCase().contains('reference')
        sql.toLowerCase().contains('codes')
        !sql.contains('enum_lookup')

        where:
        dialect << [new SQLServerSQLDatabaseDomain(), new OracleSQLDatabaseDomain(), new DatabricksSQLDatabaseDomain()]
    }
}

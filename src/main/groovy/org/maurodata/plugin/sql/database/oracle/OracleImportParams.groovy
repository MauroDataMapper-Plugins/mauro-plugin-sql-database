package org.maurodata.plugin.sql.database.oracle

import org.maurodata.plugin.sql.database.SQLDatabaseDomainImportParams
import org.maurodata.plugin.importer.config.ImportGroupConfig
import org.maurodata.plugin.importer.config.ImportParameterConfig

import groovy.transform.AutoClone

@AutoClone
class OracleImportParams extends SQLDatabaseDomainImportParams {

    @ImportParameterConfig(
        displayName = 'Database Host',
        description = 'The hostname of the server that is running the database.',
        order = 3,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Oracle DataSource Connection Details',
            order = 0
        ))
    String databaseHost

    @ImportParameterConfig(
        displayName = 'Database Port',
        description = [
            'The port that the database is accessed through.',
            'If not supplied then the default port for the specified type will be used.'],
        order = 4,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Oracle DataSource Connection Details',
            order = 0
        ))
    Integer databasePort

    @ImportParameterConfig(
        displayName = 'Username',
        description = [
            'The username used to connect to the database'],
        order = 5,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Oracle DataSource Connection Details',
            order = 0
        ))
    String username

    @ImportParameterConfig(
        displayName = 'Password',
        description = [
            'The password used to connect to the database'],
        order = 6,
        optional = true,
        password = true,
        group = @ImportGroupConfig(
            name = 'Oracle DataSource Connection Details',
            order = 0
        ))
    String password
}

package org.maurodata.plugin.sql.database.sqlserver

import org.maurodata.plugin.sql.database.SQLDatabaseDomainImportParams
import org.maurodata.plugin.importer.config.ImportGroupConfig
import org.maurodata.plugin.importer.config.ImportParameterConfig

import groovy.transform.AutoClone

@AutoClone
class SQLServerImportParams extends SQLDatabaseDomainImportParams {

    @ImportParameterConfig(
        displayName = 'Authentication Scheme',
        description = ['Authentication scheme to use, options are [nativeAuthentication, ntlm, javaKerberos].',
            'If anything other than nativeAuthentication is used integratedSecurity will be set to "true". Default is NTLM.'],
        order = 1,
        optional = true,
        group = @ImportGroupConfig(
            name = 'SQLServer DataSource Connection Details',
            order = 0
        ))
    String authenticationScheme

    @ImportParameterConfig(
        displayName = 'Integrated Security',
        description = ['Use integrated security?',
            'If anything other than nativeAuthentication is used as authentication schema then integratedSecurity will be set to "true".'],
        order = 2,
        group = @ImportGroupConfig(
            name = 'SQLServer DataSource Connection Details',
            order = 0
        ))
    Boolean integratedSecurity

    @ImportParameterConfig(
        displayName = 'Database Host',
        description = 'The hostname of the server that is running the database.',
        order = 3,
        optional = true,
        group = @ImportGroupConfig(
            name = 'SQLServer DataSource Connection Details',
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
            name = 'SQLServer DataSource Connection Details',
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
            name = 'SQLServer DataSource Connection Details',
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
            name = 'SQLServer DataSource Connection Details',
            order = 0
        ))
    String password

    @ImportParameterConfig(
        displayName = 'SSL',
        description = 'Whether SSL should be used to connect to the database.',
        order = 7,
        group = @ImportGroupConfig(
            name = 'SQLServer DataSource Connection Details',
            order = 0
        ))
    Boolean databaseSSL

    @ImportParameterConfig(
        displayName = 'Domain Name',
        description = 'User domain name. This should be used rather than prefixing the username with <DOMAIN>/<username>.',
        order = 8,
        optional = true,
        group = @ImportGroupConfig(
            name = 'SQLServer DataSource Connection Details',
            order = 0
        ))
    String domain

    @ImportParameterConfig(
        displayName = 'SQL Server Instance',
        description = [
            'The name of the SQL Server Instance.',
            'This only needs to be supplied if the server is running an instance with a different name to the server hostname.'],
        order = 9,
        optional = true,
        group = @ImportGroupConfig(
            name = 'SQLServer DataSource Connection Details',
            order = 0
        ))
    String serverInstance
}

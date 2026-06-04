package org.maurodata.plugin.sql.database.sqlserver

import org.maurodata.plugin.sql.database.SQLDatabaseDomainImportParams
import org.maurodata.plugin.importer.config.ImportGroupConfig
import org.maurodata.plugin.importer.config.ImportParameterConfig

import groovy.transform.AutoClone

@AutoClone
class SQLServerImportParams extends SQLDatabaseDomainImportParams {

    @ImportParameterConfig(
        displayName = 'Authentication Scheme',
        description = ['Authentication scheme to use for Windows/domain authentication. Options are [nativeAuthentication, ntlm, javaKerberos].',
            'Leave blank for SQL Server username/password authentication.',
            'Use nativeAuthentication with integratedSecurity=true to use the Windows identity running Mauro.',
            'Use ntlm with domain, username, and password for explicit Windows/domain credentials.',
            'If ntlm or javaKerberos is selected, integratedSecurity will be set to true.'],
        order = 1,
        optional = true,
        group = @ImportGroupConfig(
            name = 'SQLServer DataSource Connection Details',
            order = 0
        ))
    String authenticationScheme

    @ImportParameterConfig(
        displayName = 'Integrated Security',
        description = ['Use SQL Server integrated security?',
            'This is automatically enabled for ntlm and javaKerberos authentication schemes.',
            'For nativeAuthentication, set this to true to use the Windows identity running Mauro.'],
        order = 2,
        optional = true,
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

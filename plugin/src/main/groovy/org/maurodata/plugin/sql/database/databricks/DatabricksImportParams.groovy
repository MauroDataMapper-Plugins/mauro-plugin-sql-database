package org.maurodata.plugin.sql.database.databricks

import org.maurodata.plugin.sql.database.SQLDatabaseDomainImportParams
import org.maurodata.plugin.importer.config.ImportGroupConfig
import org.maurodata.plugin.importer.config.ImportParameterConfig

import groovy.transform.AutoClone

@AutoClone
class DatabricksImportParams extends SQLDatabaseDomainImportParams {

    @ImportParameterConfig(
        displayName = 'Databricks JDBC URL',
        order = 1,
        group = @ImportGroupConfig(
            name = 'Databricks DataSource Connection Details',
            order = 1
        ),
        description = ['The Databricks connection URL']
    )
    String url

    @ImportParameterConfig(
        displayName = 'Databricks JDBC access token',
        order = 2,
        group = @ImportGroupConfig(
            name = 'Databricks DataSource Connection Details',
            order = 1
        ),
        description = ['The Databricks personal access token (secret)']
    )
    String accessToken
}

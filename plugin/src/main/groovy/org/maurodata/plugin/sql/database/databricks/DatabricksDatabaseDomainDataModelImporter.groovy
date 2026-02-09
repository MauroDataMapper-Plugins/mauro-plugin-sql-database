package org.maurodata.plugin.sql.database.databricks

import org.maurodata.domain.datamodel.DataModel
import org.maurodata.plugin.sql.database.SQLDatabaseDomainImporter
import org.maurodata.plugin.sql.database.SQLDatabaseDomainUtils
import org.maurodata.plugin.importer.DataModelImporterPlugin

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.inject.Singleton

@Slf4j
@Singleton
@CompileStatic
class DatabricksDatabaseDomainDataModelImporter implements DataModelImporterPlugin<DatabricksImportParams>{

    final String version = '1.0.0'

    void setVersion(String version) {
        throw new RuntimeException("Must not set the version")
    }

    final String displayName = 'Databricks DataModel Importer Import Plugin'

    void setDisplayName(String displayName) {
        throw new RuntimeException("Must not set the display name")
    }

    @Override
    Boolean handlesContentType(String contentType) {
        return false
    }

    @Override
    Class<DatabricksImportParams> importParametersClass() {
        return DatabricksImportParams
    }


    @Override
    List<DataModel> importDomain(DatabricksImportParams params) {

        final Map<String,Object> importParams = SQLDatabaseDomainUtils.translateToMapFromImportParameters(params)

        final DatabricksSQLDatabaseDomain serverDatabaseDomain = new DatabricksSQLDatabaseDomain()
        final SQLDatabaseDomainImporter databaseDomainImporter = new SQLDatabaseDomainImporter(serverDatabaseDomain, importParams)

        databaseDomainImporter.importDomain()
    }
}

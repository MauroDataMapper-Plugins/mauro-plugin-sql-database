package org.maurodata.plugin.sql.database.oracle

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
class OracleDatabaseDomainDataModelImporter implements DataModelImporterPlugin<OracleImportParams>{

    final String version = '1.0.0'

    void setVersion(String version) {
        throw new RuntimeException("Must not set the version")
    }

    final String displayName = 'Oracle DataModel Importer Import Plugin'

    void setDisplayName(String displayName) {
        throw new RuntimeException("Must not set the display name")
    }

    @Override
    Boolean handlesContentType(String contentType) {
        return false
    }

    @Override
    Class<OracleImportParams> importParametersClass() {
        return OracleImportParams
    }


    @Override
    List<DataModel> importDomain(OracleImportParams params) {

        final Map<String,Object> importParams = SQLDatabaseDomainUtils.translateToMapFromImportParameters(params)

        final OracleSQLDatabaseDomain serverDatabaseDomain = new OracleSQLDatabaseDomain()
        final SQLDatabaseDomainImporter databaseDomainImporter = new SQLDatabaseDomainImporter(serverDatabaseDomain, importParams)

        databaseDomainImporter.importDomain()
    }
}

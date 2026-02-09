package org.maurodata.plugin.sql.database.oracle

import org.maurodata.domain.datamodel.DataModel
import org.maurodata.plugin.exporter.json.JsonDataModelExporterPlugin

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option

import java.nio.file.Files
import java.nio.file.Path

@Command(name = 'mauro-plugin-database-oracle', description = 'Extract metadata from Oracle schema',
    mixinStandardHelpOptions = true, showAtFileInUsageHelp = true)
@Slf4j
@CompileStatic
class OracleCommand implements Runnable {

    @Option(names = ['--host'], description = 'Database host', required = false)
    String databaseHost

    @Option(names = ['--port'], description = 'Database port', required = false)
    Integer databasePort

    @Option(names = ['-d', '--database'], description = [
        'Database () name(s). A name of a database to connect to, the database name will be used as the DataModel name',
        'unless the DataModel name option is supplied.',
        'If multiple names supplied then DataModel name will be ignored and the database name will be used as the DataModel name,',
        'and the same username and password will be used for all named databases.'])
    List<String> databaseNames = []

    @Option(names = ['-u', '--username'], description = 'The username used to connect to the database', required = false)
    String username

    @Option(names = ['-p', '--password'], description = 'The password used to connect to the database', required = false)
    String password

    @Option(names = ['--catalog-as-datamodel'], description = 'Import catalogs/databases as datamodels', required = false)
    Boolean catalogAsDataModel = true

    @Option(names = ['-s', '--schema'], description = 'Schema name(s) to import')
    List<String> schemaNames = []

    @Option(names = ['-r', '--excluded-schemas'], description = 'Schema name(s) to exclude', required = false)
    List<String> excludeSchemaNames = []

    @Option(names = ['-e', '--excluded-tables'], description = 'Ignore table names like', required = false)
    List<String> excludeTablesLike

    @Option(names = ['--included-tables'], description = 'Include table names like', required = false)
    List<String> includeTablesLike

    @Option(names = ['-l', '--enum-lookup-adornment'], description = 'Adornment added to a column to denote it holds enumerable code values that may be looked up in a look up table to retrieve corresponding display names', required = false)
    List<String> enumLookupAdornment

    @Option(names = ['-g', '--enum-given-adornment'], description = 'Adornment added to a column to denote it holds enumerable code values', required = false)
    List<String> enumGivenAdornment

    @Option(names = ['-q', '--enum-lookup-table'], description = 'Enumeration display value lookup table details to look up display values for a column value via a lookup table. In the form of a comma list: <display>,<value>,<schema|table|view>. e.g. display,code_value,reference.code_value', required = false)
    String enumLookupTable

    @Option(names = ['-i', '--enum-ignore-column'], description = 'Exact match of column names to ignore when detecting enumerations', required = false)
    List<String> enumIgnoreColumn

    @Option(names = ['-j', '--enum-ignore-column-like'], description = 'Match using LIKE of column names to ignore when detecting enumerations', required = false)
    List<String> enumIgnoreColumnLike

    @Option(names = ['-o', '--output'], description = 'Output file', required = false)
    Path output

    static void main(final String[] args) throws Throwable {
        new CommandLine(new OracleCommand()).execute(args)
    }

    void run() {

        final OracleImportParams serverImportParams = new OracleImportParams(
            databaseHost: databaseHost,
            databasePort: databasePort,
            username: username,
            password: password,
            databaseNames: databaseNames,
            catalogAsDataModel: catalogAsDataModel,
            schemaNames: schemaNames,
            excludeSchemaNames: excludeSchemaNames,
            excludeTablesLike: excludeTablesLike,
            includeTablesLike: includeTablesLike,
            enumLookupAdornment: enumLookupAdornment,
            enumGivenAdornment: enumGivenAdornment,
            enumIgnoreColumn: enumIgnoreColumn,
            enumIgnoreColumnLike: enumIgnoreColumnLike,
            enumLookupTable: enumLookupTable
        )

        ApplicationContext applicationContext = ApplicationContext.run()

        try{

            OracleDatabaseDomainDataModelImporter serverDataModelImporter = applicationContext.getBean(OracleDatabaseDomainDataModelImporter)
            log.info 'Importing DataModel...'
            List<DataModel> dataModels = serverDataModelImporter.importDomain(serverImportParams)
            log.debug("${dataModels.toString()}")

            JsonDataModelExporterPlugin jsonDataModelExporterPlugin = applicationContext.getBean(JsonDataModelExporterPlugin)

            byte[] json=jsonDataModelExporterPlugin.exportModel(dataModels.first())

            if(output) {
                log.info "Writing to file ${output.toString()}"
                Files.write(output, json)
            } else {
                log.debug(new String(json,"UTF-8"))
            }

            log.info 'Finished!'
        }
        finally{
            applicationContext.close()
        }

        System.exit(0)
    }
}

package org.maurodata.plugin.sql.database

import org.maurodata.plugin.importer.ImportParameters
import org.maurodata.plugin.importer.config.ImportGroupConfig
import org.maurodata.plugin.importer.config.ImportParameterConfig

import groovy.transform.AutoClone

@AutoClone
class SQLDatabaseDomainImportParams extends ImportParameters {

    @ImportParameterConfig(
        displayName = 'Catalog (Database, Service Name) Name(s)',
        description = [
            'A list of the databases/service names/catalogs to connect to, the name will be used as the DataModel name',
            'unless the DataModel name option is supplied.',
            'If multiple names supplied then DataModel name will be ignored and the database/service name/catalog name will be used as the DataModel name,',
            'and the same authentication will be used for all named databases/service name/catalogs.'],
        order = 1,
        group = @ImportGroupConfig(
            name = 'Source',
            order = 1
        ))
    List<String> databaseNames=[]

    @ImportParameterConfig(
        displayName = 'Import catalog (database, service name) as datamodel',
        order = 2,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Source',
            order = 1
        ),
        description = ['Import catalogs (databases, service names) as datamodels (alternative is to import schemas as datamodels']
    )
    Boolean catalogAsDataModel = true

    @ImportParameterConfig(
        displayName = 'Schemas',
        order = 3,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Source',
            order = 1
        ),
        description = ['The schema(s) to be imported']
    )
    List<String> schemaNames = []

    @ImportParameterConfig(
        displayName = 'Excluded schemas',
        order = 4,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Source',
            order = 1
        ),
        description = ['The schema(s) to be excluded from the import']
    )
    List<String> excludeSchemaNames = []

    @ImportParameterConfig(
        displayName = 'Excluded table namespaces',
        order = 5,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Source',
            order = 1
        ),
        description = ['Exclude tables like these names, if specified']
    )
    List<String> excludeTablesLike = []

    @ImportParameterConfig(
        displayName = 'Included tables',
        order = 6,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Source',
            order = 1
        ),
        description = ['Only import tables like these names, if specified']
    )
    List<String> includeTablesLike = []

    @ImportParameterConfig(
        displayName = 'Enumeration lookup adornment',
        order = 7,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Source',
            order = 1
        ),
        description = ['Adornment added to a column to denote it holds enumerable code values that also may be looked up in a look up table to retrieve corresponding display names. e.g. _CD']
    )
    List<String> enumLookupAdornment = []

    @ImportParameterConfig(
        displayName = 'Enumeration given adornment',
        order = 8,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Source',
            order = 1
        ),
        description = ['Adornment added to a column to denote it holds enumerable code values. e.g. _IND _TZ']
    )
    List<String> enumGivenAdornment = []

    @ImportParameterConfig(
        displayName = 'Enumeration display value lookup table details',
        order = 9,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Source',
            order = 1
        ),
        description = ['Table details to use to look up display values for a column value, in the form of a comma list: <display>,<value>,<schema|table|view>. e.g. display,code_value,reference.code_value. The column value must be an integer.']
    )
    String enumLookupTable


    @ImportParameterConfig(
        displayName = 'Enumeration ignore columns',
        order = 10,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Source',
            order = 1
        ),
        description = ['Exact match of column names to ignore when detecting enumerations. e.g. Resource Resource_Cd']
    )
    List<String> enumIgnoreColumn = []

    @ImportParameterConfig(
        displayName = 'Enumeration ignore columns like',
        order = 11,
        optional = true,
        group = @ImportGroupConfig(
            name = 'Source',
            order = 1
        ),
        description = ['Match using LIKE of column names to ignore when detecting enumerations. e.g. columns may identify someone such as Prsnl Pathologist']
    )
    List<String> enumIgnoreColumnLike = []
}

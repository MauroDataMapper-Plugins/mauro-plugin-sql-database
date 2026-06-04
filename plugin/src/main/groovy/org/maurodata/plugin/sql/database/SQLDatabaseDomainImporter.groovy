package org.maurodata.plugin.sql.database

import org.maurodata.domain.datamodel.DataClass
import org.maurodata.domain.datamodel.DataElement
import org.maurodata.domain.datamodel.DataModel
import org.maurodata.domain.datamodel.DataModelType
import org.maurodata.domain.datamodel.DataType
import org.maurodata.domain.datamodel.EnumerationValue
import org.maurodata.domain.facet.Metadata
import org.maurodata.domain.facet.SummaryMetadata
import org.maurodata.domain.facet.SummaryMetadataReport
import org.maurodata.domain.facet.SummaryMetadataType
import org.maurodata.domain.model.AdministeredItem

import groovy.json.JsonBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.sql.Clob
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

@Slf4j
@CompileStatic
class SQLDatabaseDomainImporter {

    final static String QUERY_BUILDER_NAMESPACE = 'uk.ac.ox.softeng.maurodatamapper.plugins.explorer.querybuilder'
    final static String EXPLORER_NAMESPACE = 'uk.ac.ox.softeng.maurodatamapper.plugins.explorer.research'

    final SQLDatabaseDomain databaseDomain
    final Map<String,Object> importParams
    final Map<Object,String> originalDatabaseIdentifiers = [:]

    SQLDatabaseDomainImporter(final SQLDatabaseDomain databaseDomain, final Map<String,Object> importParams){
        this.databaseDomain=databaseDomain
        this.importParams=importParams
    }

    List<DataModel> importDomain() {

        final List<DataModel> allDataModels=[]

        final List<String> databaseNames = (List<String>) importParams.databaseNames

        if(databaseNames) {
            databaseNames.forEach {String databaseName ->
                final DataSource dataSource = databaseDomain.getDatasource(importParams, databaseName)
                try {
                    List<DataModel> imported = importDomain(dataSource, databaseName)
                    allDataModels.addAll(imported)
                } catch (Throwable throwable) {
                    final Throwable rootCause = unwrapThrowable(throwable)
                    log.error("Import failed for catalog/database {}", databaseName)
                    log.error("Root cause: {}: {}", rootCause.getClass().name, rootCause.message)
                    log.error("Import stack trace", rootCause)
                    throw rootCause
                } finally {
                    if (dataSource instanceof Closeable) {
                        ((Closeable) dataSource).close()
                    }
                }
            }
        }

        return allDataModels
    }

    private Connection getConnection(final DataSource dataSource) {
        try {
            final Connection connection = databaseDomain.getConnection(dataSource, importParams)
            connection.setReadOnly(true)
            return connection
        } catch (Throwable throwable) {
            final Throwable rootCause = unwrapThrowable(throwable)
            log.error("Failed to obtain SQL connection")
            log.error("Root cause: {}: {}", rootCause.getClass().name, rootCause.message)
            log.error("Connection stack trace", rootCause)
            throw rootCause
        }
    }

    private static Throwable unwrapThrowable(final Throwable throwable) {
        if (throwable == null) return null

        Throwable current = throwable
        while (true) {
            if (current instanceof UndeclaredThrowableException && ((UndeclaredThrowableException) current).undeclaredThrowable) {
                current = ((UndeclaredThrowableException) current).undeclaredThrowable
                continue
            }
            if (current instanceof InvocationTargetException && ((InvocationTargetException) current).targetException) {
                current = ((InvocationTargetException) current).targetException
                continue
            }
            if (current.cause && current.cause != current) {
                current = current.cause
                continue
            }
            break
        }
        return current
    }

    List<DataModel> importDomain(final DataSource dataSource, final String databaseName) {

        log.info("Importing catalog/database ${databaseName}")

        long startTime=System.currentTimeMillis()

        Connection connection = getConnection(dataSource)
        logConnection(connection)

        DataModel dataModel // if we're importing a catalog as a data model
        Map<String, DataModel> dataModels = [:] // if we're importing each schema as a datamodel

        // Import catalog as DataModel
        if(importParams.catalogAsDataModel as Boolean) {
            Map<String, Object> catalogResults = queryForCatalogs(connection, databaseName)
            log.debug("catalogResults ${catalogResults.toString()}")
            dataModel = new DataModel(
                modelType: DataModelType.DATA_ASSET,
                label: normaliseLabelCase((String) catalogResults.catalog_name),
                description: catalogResults.comment,
                dataTypes: Collections.synchronizedList([] as List<DataType>),
                dataClasses: Collections.synchronizedList([] as List<DataClass>))
            synchronized (originalDatabaseIdentifiers) {
                originalDatabaseIdentifiers.put(dataModel, (String) catalogResults.catalog_name)
            }
            addResultsAsMetadata(dataModel, catalogResults)
            log.info "Found catalog ${catalogResults.catalog_name}"
        }

        // Import schemas
        List<Map<String, Object>> schemaResults = queryForSchema(connection, databaseName, importParams.schemaNames as List<String>, importParams.excludeSchemaNames as List<String>)
        log.debug("schemaResults ${schemaResults.toString()}")

        schemaResults.each { final Map<String,Object> schemaMap ->
            log.info 'Loading schema for '+(String) schemaMap.schema_name

            if(importParams.catalogAsDataModel as Boolean) {
                log.debug("New DataClass for schema: ${schemaMap.schema_name} with label ${normaliseLabelCase((String) schemaMap.schema_name)}")
                DataClass dataClass = new DataClass(label: normaliseLabelCase((String) schemaMap.schema_name), description: schemaMap.comment)
                synchronized (originalDatabaseIdentifiers) {
                    originalDatabaseIdentifiers.put(dataClass, (String) schemaMap.schema_name)
                }
                addResultsAsMetadata(dataClass, schemaMap)
                dataModel.dataClasses << dataClass
                dataClass.dataModel = dataModel
            } else {
                DataModel schemaDataModel = new DataModel(
                    modelType: DataModelType.DATA_ASSET,
                    label: normaliseLabelCase((String) schemaMap.schema_name),
                    description: schemaMap.comment,
                    dataTypes: Collections.synchronizedList([] as List<DataType>),
                    dataClasses: Collections.synchronizedList([] as List<DataClass>))
                synchronized (originalDatabaseIdentifiers) {
                    originalDatabaseIdentifiers.put(schemaDataModel, (String) schemaMap.schema_name)
                }
                addResultsAsMetadata(schemaDataModel, schemaMap)
                dataModels[(String) schemaMap.schema_name] = schemaDataModel
            }
        }

        List<Map<String, Object>> tableResults = queryForTables(connection, databaseName, importParams.schemaNames as List<String>, importParams.excludeSchemaNames as List<String>, importParams.excludeTablesLike as List<String>, importParams.includeTablesLike as List<String>)
        log.info("tables (${tableResults.size()}) ")
        log.debug("tableResults ${tableResults.toString()}")

        tableResults.parallelStream().forEach {tableMap ->

            boolean canReadTable=databaseDomain.canReadTable(connection,(String) tableMap.table_catalog, (String) tableMap.table_schema, (String) tableMap.table_name)

            if(!canReadTable)
            {
                log.warn("Read pre-check failed, this will not appear in the model: "+(String) tableMap.table_catalog+'.'+(String) tableMap.table_schema+'.'+(String) tableMap.table_name)
            }
            else {
                log.debug("New DataClass for table: ${tableMap.table_name} with label ${normaliseLabelCase((String) tableMap.table_name)}")
                DataClass dataClass = new DataClass(label: normaliseLabelCase((String) tableMap.table_name), description: tableMap.comment?tableMap.comment:'')
                synchronized (originalDatabaseIdentifiers) {
                    originalDatabaseIdentifiers.put(dataClass, (String) tableMap.table_name)
                }
                addResultsAsMetadata(dataClass, tableMap)

                if (importParams.catalogAsDataModel as Boolean) {
                    synchronized (dataModel.dataClasses) {
                        DataClass schemaClass = dataModel.dataClasses.find { DataClass dataClass1 -> dataClass1.metadata.find { Metadata metadata -> metadata.key == 'catalog_name' }.value.equalsIgnoreCase(tableMap.table_catalog as String) && dataClass1.label.equalsIgnoreCase(tableMap.table_schema as String)}
                        if(schemaClass == null) {
                            log.error 'error importing DataClass: parentClass is null for {}', dataClass.label
                        }
                        dataClass.dataModel = schemaClass.dataModel
                        dataClass.parentDataClass = schemaClass
                        synchronized (schemaClass.dataClasses) {
                            schemaClass.dataClasses << dataClass
                        }

                    }
                } else {
                    synchronized (dataModels) {
                        DataModel schemaModel = dataModels[(String) tableMap.table_schema]
                        synchronized (schemaModel.dataClasses) {
                            schemaModel.dataClasses << dataClass
                        }
                        synchronized (dataClass) {
                            dataClass.dataModel = schemaModel
                        }
                    }
                }
            }
        }

        log.debug 'done tableResults'

        List<Map<String, Object>> columnResults = queryForColumns(connection, databaseName, importParams.schemaNames as List<String>, importParams.excludeSchemaNames as List<String>, importParams.excludeTablesLike as List<String>, importParams.includeTablesLike as List<String>)

        connection.close()

        log.debug("columnResults ${columnResults.toString()}")

        // Import DataElements
        columnResults.parallelStream().forEach {columnMap ->
            DataModel parentDataModel = importParams.catalogAsDataModel as Boolean?dataModel:dataModels[(String) columnMap.table_schema]
            DataType dataType = getOrCreateDataTypeFromLabel(parentDataModel, (String) columnMap.data_type)
            DataElement dataElement = new DataElement(label: normaliseLabelCase((String) columnMap.column_name), minMultiplicity: columnMap.is_nullable ? 0 : 1,
                                                      maxMultiplicity: 1, dataType: dataType, description: columnMap.comment, order: ((String) columnMap.ordinal_position).toInteger())
            synchronized (originalDatabaseIdentifiers) {
                originalDatabaseIdentifiers.put(dataElement, (String) columnMap.column_name)
            }
            addResultsAsMetadata(dataElement, columnMap)

            DataClass parentClass
            if(importParams.catalogAsDataModel as Boolean) {
                synchronized (dataModel.dataClasses) {
                    DataClass schemaClass = dataModel.dataClasses.find {it.metadata.find { it.key == 'catalog_name' }.value.equalsIgnoreCase(columnMap.table_catalog as String) && it.label.equalsIgnoreCase(columnMap.table_schema as String) }
                    synchronized (schemaClass.dataClasses) {
                        parentClass = schemaClass.dataClasses.find { it.label.equalsIgnoreCase(columnMap.table_name as String) }
                    }
                }
            } else {
                synchronized (parentDataModel.dataClasses) {
                    parentClass = parentDataModel.dataClasses.find { it.label.toLowerCase() == ((String) columnMap.table_name).toLowerCase() }
                }
            }
            log.info "catalog: [${columnMap.table_catalog}], schema: [${columnMap.table_schema}], table: [${columnMap.table_name}], column: [${columnMap.column_name}]"
            if(parentClass!=null) {
                synchronized (parentClass.dataElements) {
                    parentClass.dataElements << dataElement
                }
            } else {
                log.error 'error importing DataElement: parentClass is null for {} in dataModel {}', dataElement.label, parentDataModel.label
            }
        }
        log.info "${columnResults.table_schema.unique().size()} schemas, ${columnResults.collect {new Tuple2(it.table_schema, it.table_name)}.unique().size()} tables, ${columnResults.size()} columns"

        log.debug 'columnResults'

        List<DataClass> tableDataClasses
        if(importParams.catalogAsDataModel as Boolean) {
            tableDataClasses = dataModel.dataClasses.collectMany {it.dataClasses}
        } else {
            tableDataClasses = dataModels.values().collectMany {it.dataClasses}
        }

        //
        tableDataClasses.parallelStream().forEach {DataClass tableDataClass ->
            String schemaName
            if(importParams.catalogAsDataModel as Boolean) {
                //schemaName = tableDataClass.parentDataClass.label
                synchronized (originalDatabaseIdentifiers) {
                    schemaName = originalDatabaseIdentifiers.get(tableDataClass.parentDataClass)
                }
            } else {
                //schemaName = tableDataClass.dataModel.label
                synchronized (originalDatabaseIdentifiers) {
                    schemaName = originalDatabaseIdentifiers.get(tableDataClass.dataModel)
                }
            }

            synchronized (originalDatabaseIdentifiers) {
                log.info "Importing from ${schemaName}.${originalDatabaseIdentifiers.get(tableDataClass)}"
            }

            // Import table row counts and column distinct counts
            List<DataElementsWithQuery> dataElementsWithQuery= createImportRowCounts(databaseName, schemaName, tableDataClass)
            if(!dataElementsWithQuery.isEmpty()) {
                synchronized (originalDatabaseIdentifiers) {
                    log.info "Import table row counts for ${schemaName} ${originalDatabaseIdentifiers.get(tableDataClass)} in chunks of ${dataElementsWithQuery.size()}"
                }
                dataElementsWithQuery.parallelStream().forEach { DataElementsWithQuery it ->
                    try (Connection threadConnection = getConnection(dataSource)) {
                        importRowCounts(threadConnection, it)
                    } catch (Exception exception) {
                        //exception.printStackTrace()
                        log.error it.query
                        log.error exception.message
                    }
                }
                log.debug 'done dataElementsWithQuery'
            }

            // Import enumeration values
            List<EnumerationColumns> enumerationColumns = determineImportEnumerationColumns(tableDataClass.dataModel, databaseName, schemaName, tableDataClass)
            if(!enumerationColumns.isEmpty()) {
                log.info "Import enumeration values in chunks of ${enumerationColumns.size()}"
                enumerationColumns.parallelStream().forEach { EnumerationColumns it ->
                    try (Connection threadConnection = getConnection(dataSource)) {
                        importEnumerationValues(threadConnection, it)
                    } catch (Exception exception) {
                        //exception.printStackTrace()
                        log.error 'Error importing enumerations for table {}, elements {}', tableDataClass.label, it.enumerationColumns.label.join(',')
                        log.error exception.message
                    }
                }
                log.debug 'done enumerationColumns'
            }

            // Import summary metadata for enumeration values and dates/numbers
            List<SummaryMetadataForEnumerations> summaryMetadataForEnumerations=determineImportSummaryMetadataForEnumerations(databaseName, schemaName, tableDataClass)
            if(!summaryMetadataForEnumerations.isEmpty()) {
                log.info "Import summary metadata for enumeration values in chunks of ${summaryMetadataForEnumerations.size()}"
                summaryMetadataForEnumerations.parallelStream().forEach { SummaryMetadataForEnumerations it ->
                    try (Connection threadConnection = getConnection(dataSource)) {
                        importSummaryMetadataForEnumerations(threadConnection, it)
                    } catch (Exception exception) {
                        //exception.printStackTrace()
                        log.error exception.message
                    }
                }
                log.debug 'done summaryMetadataForEnumerations'
            }

            List<SummaryMetadataForDatesAndNumbers> summaryMetadataForDatesAndNumbers=determineImportSummaryMetadataForDatesAndNumbers(databaseName, schemaName, tableDataClass)
            if(!summaryMetadataForDatesAndNumbers.isEmpty()) {
                log.info "Import summary metadata for dates/numbers in chunks of ${summaryMetadataForDatesAndNumbers.size()}"
                summaryMetadataForDatesAndNumbers.parallelStream().forEach { SummaryMetadataForDatesAndNumbers it ->
                    try (Connection threadConnection = getConnection(dataSource)) {
                        importSummaryMetadataForDatesAndNumbers(threadConnection, it)
                    } catch (Exception exception) {
                        //exception.printStackTrace()
                        log.error exception.message
                    }
                }
                log.debug 'done summaryMetadataForDatesAndNumbers'
            }


            // Make sure every element has a suggestion score for the explorer, if it hasn't been
            synchronized (originalDatabaseIdentifiers) {
                log.info "Ensure every element of ${schemaName}.${originalDatabaseIdentifiers.get(tableDataClass)} has a suggestion score"
            }
            tableDataClass.dataElements.parallelStream().forEach { DataElement dataElement ->
                final Metadata suggestionIndexMetaData=getMetadata(dataElement.metadata,EXPLORER_NAMESPACE,'suggestionIndex')
                if(suggestionIndexMetaData==null)  {
                    // Use the distinct count and the number of rows to create a basic measure of entropy - and this will become the suggestionIndex
                    // imbalance would be 1.0-entropy, but it would be such a crude measure, there is no reason to include it.

                    final Metadata distinctValuesCount=getMetadata(dataElement.metadata,EXPLORER_NAMESPACE,'distinctValuesCount')
                    final Metadata rowCount=getMetadata(dataElement.metadata,EXPLORER_NAMESPACE,'rowCount')

                    if(distinctValuesCount!=null && rowCount!=null) {
                        final float entropy=calculateBasicEntropy(Long.parseLong(distinctValuesCount.value,10), Long.parseLong(rowCount.value,10))

                        addMetadata(dataElement,new Metadata(namespace: EXPLORER_NAMESPACE, key: 'entropy', value: entropy))
                        addMetadata(dataElement,new Metadata(namespace: EXPLORER_NAMESPACE, key: 'suggestionIndex', value: entropy))
                    }
                }
            }
            log.debug 'done suggestion scores'
        }

        log.debug 'done tableDataClasses'

        long endTime = System.currentTimeMillis()

        // How long did that take?

        final long duration = endTime - startTime
        long seconds = (duration / 1000) as long

        final long days = (seconds / (24 * 3600)) as long
        seconds %= (24 * 3600)
        final long hours = (seconds / 3600) as long
        seconds %= 3600
        final long minutes = (seconds / 60) as long
        seconds %= 60

        log.info("${days}d ${String.format('%02d', hours)}h ${String.format('%02d', minutes)}m ${String.format('%02d', seconds)}s")

        if(importParams.catalogAsDataModel as Boolean) {
            return [dataModel]
        } else {
            dataModels.values().each {dm ->
                dm.allDataClasses.collectMany {it.dataElements}.each {
                    it.println("${it.label} : ${it.dataType.label}")
                }
            }
            return dataModels.values() as List
        }
    }

    void logConnection(Connection connection) {
        log.info 'Connection open'
        log.info 'Testing connection...'
        testConnection(connection)
        log.info 'Connection OK'
    }

    void testConnection(Connection connection) {
        String testSql = databaseDomain.queryForConnectionTest()
        PreparedStatement testStatement = connection.prepareStatement(testSql)
        Map<String, Object> testResults = resultSetToList(testStatement.executeQuery()).first()
        assert testResults.values().toList() == databaseDomain.connectionTestAssert()
    }

    static List<Map<String, Object>> resultSetToList(ResultSet resultSet) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData()
        List<Map<String, Object>> results = []
        while (resultSet.next()) {
            Map<String, Object> result=(1..resultSetMetaData.getColumnCount()).collectEntries {Integer i ->
                final Object resultObject = resultSet.getObject(i)
                Object useObject = resultObject
                if(resultObject instanceof Clob){
                    final Clob clob = (Clob) resultObject
                    long clob_length = clob.length()
                    final long max_accepted_length = (long) Integer.MAX_VALUE.div(2)
                    if(clob_length > max_accepted_length){
                        clob_length = max_accepted_length
                    }
                    useObject = clob.getSubString(1, (int) clob_length)
                }
                [resultSetMetaData.getColumnName(i).toLowerCase(), useObject]
            }
            results << result
        }
        results
    }

    Map<String, Object> queryForCatalogs(Connection connection, String catalogName) {
        PreparedStatement catalogStatement = databaseDomain.queryForCatalogs(connection, catalogName)
        return resultSetToList(catalogStatement.executeQuery()).first()
    }

    List<Map<String, Object>> queryForSchema(Connection connection, String catalogName, List<String> schemaNames = [], List<String> excludeSchemaNames = []) {

        final PreparedStatement schemaStatement = databaseDomain.queryForSchema(connection, catalogName, schemaNames, excludeSchemaNames)
        return resultSetToList(schemaStatement.executeQuery())
    }

    List<Map<String, Object>> queryForTables(Connection connection, String catalogName, List<String> schemaNames, List<String> excludeSchemaNames,
                                             List<String> excludeTablesLike, List<String> includeTablesLike) {
        List<PreparedStatement> tablesStatements =
            databaseDomain.queryForTables(connection, catalogName, schemaNames, excludeSchemaNames, excludeTablesLike, includeTablesLike)

        List<Map<String, Object>> all = [] as List<Map<String, Object>>
        tablesStatements.forEach {PreparedStatement tablesStatement ->
            all.addAll(resultSetToList(tablesStatement.executeQuery()))
        }

        return all
    }

    List<Map<String, Object>> queryForColumns(Connection connection, String catalogName, List<String> schemaNames, List<String> excludeSchemaNames, List<String> excludeTablesLike, List<String> includeTablesLike) {
        PreparedStatement columnsStatement = databaseDomain.queryForColumns(connection, catalogName, schemaNames, excludeSchemaNames, excludeTablesLike, includeTablesLike)
        return resultSetToList(columnsStatement.executeQuery())
    }

    static String normaliseLabelCase(final String label) {
        if (!label) return label

        final StringBuilder sb = new StringBuilder(label.length())
        boolean capitaliseNext = true

        for (int i = 0, n=label.length(); i < n; i++) {
            final char c = label.charAt(i)
            if (c == (char) '_') {
                sb.append(c)
                capitaliseNext = true
            } else {
                if (capitaliseNext) {
                    sb.append(Character.toUpperCase(c))
                    capitaliseNext = false
                } else {
                    sb.append(Character.toLowerCase(c))
                }
            }
        }

        return sb.toString()
    }

    AdministeredItem addResultsAsMetadata(AdministeredItem item, Map<String, Object> results) {
        synchronized (item) {
            results.findAll { it.key && it.value }.each {
                addMetadata(item, new Metadata(namespace: databaseDomain.getNAMESPACE(), key: it.key, value: it.value))
            }
        }
        return item
    }

    private static void addMetadata(AdministeredItem item, final Metadata metadata)
    {
        if(!metadata || !metadata.value) {
            return
        }
        synchronized (item) {
            if (!item.metadata) {
                item.metadata = []
            }
            synchronized (item.metadata) {
                if (!item.metadata.find {
                    it.namespace == metadata.namespace && it.key == metadata.key
                }) {
                    item.metadata.add(metadata)
                }
            }
        }
    }

    private DataType getOrCreateDataTypeFromLabel(DataModel dataModel, String label) {
        synchronized (dataModel.dataTypes) {
            DataType dataType = dataModel.dataTypes.find { it.label == label }
            if (!dataType) {
                dataType = createDataTypeFromLabel(label)
                dataModel.dataTypes << dataType
            }
            return dataType
        }
    }

    private DataType createDataTypeFromLabel(String label) {
        DataType dataType = new DataType(dataTypeKind: DataType.DataTypeKind.PRIMITIVE_TYPE, label: label)
        if (databaseDomain.isInteger(label)) {
            addMetadata(dataType, new Metadata(namespace: QUERY_BUILDER_NAMESPACE, key: 'querybuildertype', value: 'integer'))
        } else if (databaseDomain.isDate(label)) {
            addMetadata(dataType,new Metadata(namespace: QUERY_BUILDER_NAMESPACE, key: 'querybuildertype', value: 'date'))
        } else if (databaseDomain.isDateTime(label)) {
            addMetadata(dataType,new Metadata(namespace: QUERY_BUILDER_NAMESPACE, key: 'querybuildertype', value: 'datetime'))
        } else if (databaseDomain.isString(label)) {
            addMetadata(dataType,new Metadata(namespace: QUERY_BUILDER_NAMESPACE, key: 'querybuildertype', value: 'string'))
        } else if (databaseDomain.isDecimal(label)) {
            addMetadata(dataType,new Metadata(namespace: QUERY_BUILDER_NAMESPACE, key: 'querybuildertype', value: 'decimal'))
        }
        return dataType
    }

    boolean isString(DataElement dataElement) {
        databaseDomain.isString(dataElement.dataType.label)
    }

    boolean isNumeric(final String label) {
        databaseDomain.isInteger(label) || databaseDomain.isDecimal(label)
    }

    boolean isNumeric(DataElement dataElement) {
        isNumeric(dataElement.dataType.label)
    }

    boolean isInteger(DataElement dataElement) {
        databaseDomain.isInteger(dataElement.dataType.label)
    }

    boolean isDecimal(DataElement dataElement) {
        databaseDomain.isDecimal(dataElement.dataType.label)
    }

    boolean isDateOrTime(DataElement dataElement) {
        databaseDomain.isDate(dataElement.dataType.label) || databaseDomain.isDateTime(dataElement.dataType.label) || databaseDomain.isTime(dataElement.dataType.label)
    }

    boolean isLOB(DataElement dataElement) {
        databaseDomain.isLOB(dataElement.dataType.label)
    }

    static class DataElementsWithQuery {
        String query
        List<DataElement> dataElements
        DataClass tableClass

        DataElementsWithQuery(String query, List<DataElement> dataElements, DataClass tableClass) {
            this.query = query
            this.dataElements = dataElements
            this.tableClass = tableClass
        }
    }

    List<DataElementsWithQuery> createImportRowCounts(String catalogName, String schemaName, DataClass tableClass) {
        List<DataElementsWithQuery> dataElementsWithQueryList=[]

        tableClass.dataElements.collate(databaseDomain.getCHUNK_SIZE()).each { List<DataElement> dataElements ->

            if(dataElements.size()>0) {
                synchronized (originalDatabaseIdentifiers) {
                    List<String> queryComponents = [databaseDomain.valueExpressionAsLabel(databaseDomain.countAll(), '__count_all')]
                queryComponents.addAll(dataElements.findAll {!isLOB(it) }.collect { DataElement it -> String columnName = originalDatabaseIdentifiers.get(it);  databaseDomain.valueExpressionAsLabel(databaseDomain.countDistinct(columnName), columnName.toLowerCase())})
                queryComponents.addAll(dataElements.findAll {isDateOrTime(it) || isNumeric(it) }.collect { DataElement it -> String columnName = originalDatabaseIdentifiers.get(it); "${databaseDomain.valueExpressionAsLabel(databaseDomain.min(columnName),columnName.toLowerCase() + '_min')}, ${databaseDomain.valueExpressionAsLabel(databaseDomain.max(columnName),columnName.toLowerCase() + '_max')}" as String})
                queryComponents.addAll(
                    dataElements.findAll {!isLOB(it) }.collect
                        { DataElement it ->
                            String columnName = originalDatabaseIdentifiers.get(it)
                            databaseDomain.valueExpressionAsLabel(
                            databaseDomain.countWhere(columnName,
                                                      "${databaseDomain.escapeIdentifier(columnName)} IS NOT NULL " +
                                                      "${isString(it) ? " AND ${databaseDomain.escapeIdentifier(columnName)} <> '<null>' AND ${databaseDomain.escapeIdentifier(columnName)} <> '<no data>' AND ${databaseDomain.escapeIdentifier(columnName)} <> '<blank>' " : ""}" +
                                                      "${isNumeric(it) ? " AND ${databaseDomain.escapeIdentifier(columnName)} <> 0 " : ""}"
                                                      ),
                            columnName.toLowerCase() + '_not_null'
                            )
                        })
                String query = 'select \n ' + queryComponents.join(", \n") + " from ${databaseDomain.pathToTable(catalogName, schemaName, originalDatabaseIdentifiers.get(tableClass))}"

                dataElementsWithQueryList.push(new DataElementsWithQuery(query, dataElements, tableClass))
                }
            }
        }

        return dataElementsWithQueryList
    }

    void importRowCounts(Connection connection, DataElementsWithQuery dataElementsWithQuery) {
        /* select count(*), count(distinct column_1), ..., count(distinct column_n) from table_name */

        final String query = dataElementsWithQuery.query
        final List<DataElement> dataElements = dataElementsWithQuery.dataElements
        final DataClass tableClass = dataElementsWithQuery.tableClass

        PreparedStatement countsStatement = connection.prepareStatement(query)
        Map<String, Object> counts = resultSetToList(countsStatement.executeQuery()).first()

        synchronized (originalDatabaseIdentifiers) {
            log.debug("importRowCounts from ${originalDatabaseIdentifiers.get(tableClass)} ${counts.toString()}")
        }

        addMetadata(tableClass, new Metadata(namespace: databaseDomain.getNAMESPACE(), key: 'row_count', value: counts['__count_all']))
        dataElements.each {DataElement dataElement ->
            synchronized (originalDatabaseIdentifiers) {
                String columnName = originalDatabaseIdentifiers.get(dataElement)
                addMetadata(dataElement, new Metadata(namespace: databaseDomain.getNAMESPACE(), key: 'distinct_values_count', value: counts[columnName.toLowerCase()]))
                addMetadata(dataElement, new Metadata(namespace: EXPLORER_NAMESPACE, key: 'distinctValuesCount', value: counts[columnName.toLowerCase()]))
                addMetadata(dataElement, new Metadata(namespace: EXPLORER_NAMESPACE, key: 'rowCount', value: counts['__count_all']))
                addMetadata(dataElement, new Metadata(namespace: EXPLORER_NAMESPACE, key: 'notNullValuesCount', value: counts[(columnName.toLowerCase() + '_not_null')]))
            }
        }
        dataElements.findAll {isDateOrTime(it) || isNumeric(it)}.each {DataElement dataElement ->
            synchronized (originalDatabaseIdentifiers) {
                String columnName = originalDatabaseIdentifiers.get(dataElement)
                final String[] min_max = new String[]{"min", "max"}
                for (String func : min_max) {
                    final Object funcVal = counts[(columnName.toLowerCase() + '_' + func)]
                    addMetadata(dataElement, new Metadata(namespace: databaseDomain.getNAMESPACE(), key: func + '_value', value: funcVal))
                }
            }
        }
    }

    static class EnumerationColumns {
        List<DataElement> enumerationColumns
        DataModel dataModel
        String catalogName
        String schemaName
        DataClass tableClass
    }

    List<EnumerationColumns> determineImportEnumerationColumns(DataModel dataModel, String catalogName, String schemaName, DataClass tableClass)  {

        List<DataElement> enumerationColumnsFound = []
        tableClass.dataElements.each {DataElement column ->
            boolean isEnumerationColumn = false

            // Does the column end with an enumLookupAdornment or enumGivenAdornment
            (importParams.enumLookupAdornment as List<String>)?.each { if(column.label.toLowerCase().endsWith(it.toLowerCase())) {isEnumerationColumn = true } }

            if(!isEnumerationColumn) {
                (importParams.enumGivenAdornment as List<String>)?.each {
                    if (column.label.toLowerCase().endsWith(it.toLowerCase())) {
                        isEnumerationColumn = true
                    }
                }
            }

            // If we've identified a column that could be an enumerated code and it is an integer of some kind
            // we accept a string version of the same column
            if(!isEnumerationColumn) {
                (importParams.enumLookupAdornment as List<String>)
                    ?.each {
                        if (tableClass.dataElements.label.contains(column.label.toLowerCase() + it.toLowerCase()) && isString(column)) {
                            isEnumerationColumn = true
                        }
                    }
            }

            Metadata distinctValuesMetadata=column.metadata.find {it.key == 'distinct_values_count'}

            if(!isEnumerationColumn && distinctValuesMetadata!=null) {
                long distinctValues = distinctValuesMetadata.value.toLong()

                if (distinctValues <= databaseDomain.getACCEPT_ENUMERATION_VALUES()) {
                    isEnumerationColumn = true
                }
            }

            // Exclude columns

            if(isEnumerationColumn) {
                (importParams.enumIgnoreColumn as List<String>)?.each {
                    if (column.label.toLowerCase() == it.toLowerCase()) {
                        isEnumerationColumn = false
                    }
                }
                (importParams.enumIgnoreColumnLike as List<String>)?.each {
                    if (column.label.toLowerCase().indexOf(it.toLowerCase()) != -1) {
                        isEnumerationColumn = false
                    }
                }
            }

            // Only strings, numbers, or date types
            if(isEnumerationColumn && !isString(column) && !isNumeric(column) && !isDateOrTime(column)){
                isEnumerationColumn = false
            }

            if(isEnumerationColumn) {
                // Check whether the level of nulls is large, or the values are too distinct

                Metadata notNullValuesCountMetadata = column.metadata.find {it.key == 'notNullValuesCount'}
                Metadata rowCountMetadata = column.metadata.find {it.key == 'rowCount'}

                if (notNullValuesCountMetadata != null && rowCountMetadata != null && distinctValuesMetadata != null) {
                    final long notNullValuesCount = notNullValuesCountMetadata.value.toLong()
                    final long rowCount = rowCountMetadata.value.toLong()
                    long distinctValuesIncludingNull = distinctValuesMetadata.value.toLong() + 1

                    if (notNullValuesCount == 0) {
                        isEnumerationColumn = false
                        log.warn('excluding {}.{} as enumeration, column is all null', tableClass.label, column.label)
                        addMetadata(column, new Metadata(namespace: EXPLORER_NAMESPACE, key: 'allNull', value: true))
                    } else {
                        if (notNullValuesCount < 3 * distinctValuesIncludingNull) {
                            isEnumerationColumn = false
                            log.warn('excluding {}.{} as enumeration, values are too distinct', tableClass.label, column.label)
                        }

                        double rowFraction = notNullValuesCount / rowCount
                        double valueFraction = 0.33 * ((distinctValuesIncludingNull - 1) / distinctValuesIncludingNull)

                        if (isEnumerationColumn && rowFraction < valueFraction) {
                            addMetadata(column, new Metadata(namespace: EXPLORER_NAMESPACE, key: 'mostlyNull', value: true))
                        }
                    }
                }
            }

            if (isEnumerationColumn){enumerationColumnsFound << column}
        }

        if (!enumerationColumnsFound) {log.trace("No enumerations detected in "+schemaName)}
        else
        {
            log.trace("Enumerations found in " + schemaName + ":")
            enumerationColumnsFound.forEach { log.trace(it.label) }
            log.trace("")
        }

        List<EnumerationColumns> enumerationColumnsList=[]

        enumerationColumnsFound.collate(databaseDomain.getCHUNK_SIZE()).each{

            if(it.size()>0) {
                EnumerationColumns ec = new EnumerationColumns()

                ec.enumerationColumns = it
                ec.dataModel = dataModel
                ec.catalogName = catalogName
                ec.schemaName = schemaName
                ec.tableClass = tableClass

                enumerationColumnsList.push(ec)
            }
        }

        return enumerationColumnsList
    }

    void importEnumerationValues(Connection connection, EnumerationColumns ec){

        List<DataElement> enumerationColumns=ec.enumerationColumns
        DataModel dataModel=ec.dataModel
        String catalogName=ec.catalogName
        String schemaName=ec.schemaName
        DataClass tableClass=ec.tableClass

        // Lookup table SQL parameters

        String lookup_display=null,lookup_value=null,lookup_table=null
        if(importParams.enumLookupTable!=null) {
            final StringTokenizer st=new StringTokenizer(importParams.enumLookupTable as String,",")
            if(st.countTokens()==3)  {
                lookup_display=st.nextToken().trim()
                lookup_value=st.nextToken().trim()
                lookup_table=st.nextToken().trim()

                if(lookup_display==null || lookup_display.isEmpty() || lookup_value==null || lookup_value.isEmpty() || lookup_table==null || lookup_table.isEmpty()) {
                    lookup_display=null
                    lookup_table=null
                    lookup_value=null
                }
            }
        }

        List<String> subqueries = enumerationColumns.collect {DataElement column ->
            synchronized (originalDatabaseIdentifiers) {

                String columnName = originalDatabaseIdentifiers.get(column)

                boolean useLookup=false

                if(lookup_display!=null && isInteger(column))
                {
                    (importParams.enumLookupAdornment as List<String>).each { if(column.label.toLowerCase().endsWith(it.toLowerCase())) {useLookup = true} }
                }

                databaseDomain.queryForEnumerationValues(
                    catalogName,
                    schemaName,
                    originalDatabaseIdentifiers.get(tableClass),
                    originalDatabaseIdentifiers.get(column),
                    databaseDomain.normaliseEnumerationValueSql(columnName),
                    useLookup?
                        databaseDomain.normaliseEnumerationValueSql(databaseDomain.lookupCodeDescriptionSql(databaseDomain.escapeIdentifier(lookup_display),databaseDomain.escapeIdentifier(lookup_value),databaseDomain.escapeIdentifier(lookup_table),databaseDomain.escapeIdentifier(columnName)))
                        :
                        databaseDomain.normaliseEnumerationValueSql(columnName)
                    ,
                    databaseDomain.getMAX_ENUMERATION_VALUES()
                )
            }
        }

        String query = databaseDomain.joinSelects(subqueries)

        log.trace(query)

        PreparedStatement enumerationValuesStatement = connection.prepareStatement(query)
        Map<String, String> enumerationValues = resultSetToList(enumerationValuesStatement.executeQuery()).first() as Map<String, String>

        Map<String, Map<String, String>> enumerationValuesMaps = [:]
        enumerationValues.each {
            final Map<String,String> valuesMap=databaseDomain.enumerationValuesToMapString(it.value)
            enumerationValuesMaps[it.key] = valuesMap
            log.debug("${it.key} : ${enumerationValuesMaps[it.key].toString()}")
        }
        enumerationColumns.each {DataElement column ->
            DataType enumerationType = new DataType(label: "${databaseDomain.escapeIdentifier(schemaName)}.${databaseDomain.escapeIdentifier(tableClass.label)}.${databaseDomain.escapeIdentifier(column.label)}")
            enumerationType.domainType = DataType.DataTypeKind.ENUMERATION_TYPE
            enumerationValuesMaps[column.label.toLowerCase()].eachWithIndex{String key, String value, int idx ->
                String str = key.toString().replaceAll("[^\\p{Print}]", "�")
                value = value.replaceAll("[^\\p{Print}]", "�")
                if (key != str) {
                    log.warn "importEnumerationValues - non printable character(s) removed from enumeration value string for column ${databaseDomain.escapeIdentifier(schemaName)}.${databaseDomain.escapeIdentifier(tableClass.label)}.${databaseDomain.escapeIdentifier(column.label)}"
                }
                if (str && value) {
                    enumerationType.enumerationValues << new EnumerationValue(key: str, value: value, order: idx)
                } else {
                    log.warn "enumeration value is null/blank! str: [$str], value: [$value] for column ${databaseDomain.escapeIdentifier(schemaName)}.${databaseDomain.escapeIdentifier(tableClass.label)}.${databaseDomain.escapeIdentifier(column.label)}"
                }
            }
            if (enumerationType.enumerationValues) {
                if (enumerationType.enumerationValues.key.size() == enumerationType.enumerationValues.key.toSet().size()) {
                    synchronized (dataModel)
                    {
                        dataModel.dataTypes << enumerationType
                    }
                    synchronized (column)
                    {
                        column.dataType = enumerationType
                    }
                } else {
                    log.warn "Skipping EnumerationType [$enumerationType.label] because keys are not unique"
                }

                if (enumerationType.enumerationValues.key.size() > databaseDomain.WARN_LARGE_ENUMERATION_TYPE) {
                    log.warn "Large EnumerationType! [$enumerationType.label] contains more than ${databaseDomain.WARN_LARGE_ENUMERATION_TYPE} values"
                }
            } else {
                synchronized (column)
                {
                    column.maxMultiplicity = 0
                }
            }
        }
    }

    static class SummaryMetadataForEnumerations {
        List<DataElement> enumerationColumns
        String catalogName
        String schemaName
        DataClass tableClass
    }

    List<SummaryMetadataForEnumerations> determineImportSummaryMetadataForEnumerations(catalogName, String schemaName, DataClass tableClass) {
        List<DataElement> enumerationColumns = tableClass.dataElements.findAll {it.dataType.dataTypeKind == DataType.DataTypeKind.ENUMERATION_TYPE}

        List<SummaryMetadataForEnumerations> summaryMetadataForEnumerationsList=[]

        enumerationColumns.collate(databaseDomain.getCHUNK_SIZE()).each {List<DataElement> it ->

            if(it.size()>0) {
                SummaryMetadataForEnumerations summaryMetadataForEnumerations = new SummaryMetadataForEnumerations()

                summaryMetadataForEnumerations.enumerationColumns = it
                summaryMetadataForEnumerations.catalogName = catalogName
                summaryMetadataForEnumerations.schemaName = schemaName
                summaryMetadataForEnumerations.tableClass = tableClass

                summaryMetadataForEnumerationsList << summaryMetadataForEnumerations
            }
        }

        return summaryMetadataForEnumerationsList
    }

    void importSummaryMetadataForEnumerations(Connection connection, SummaryMetadataForEnumerations summaryMetadataForEnumerations) {
        String catalogName=summaryMetadataForEnumerations.catalogName
        String schemaName=summaryMetadataForEnumerations.schemaName
        DataClass tableClass=summaryMetadataForEnumerations.tableClass

        List<DataElement> enumerationColumns = summaryMetadataForEnumerations.enumerationColumns

        if (!enumerationColumns) return

        // Lookup table SQL parameters

        String lookup_display=null,lookup_value=null,lookup_table=null
        if(importParams.enumLookupTable!=null) {
            final StringTokenizer st=new StringTokenizer(importParams.enumLookupTable as String,",")
            if(st.countTokens()==3) {
                lookup_display=st.nextToken().trim()
                lookup_value=st.nextToken().trim()
                lookup_table=st.nextToken().trim()

                if(lookup_display==null || lookup_display.isEmpty() || lookup_value==null || lookup_value.isEmpty() || lookup_table==null || lookup_table.isEmpty()) {
                    lookup_display=null
                    lookup_table=null
                    lookup_value=null
                }
            }
        }

        List<String> subqueries = enumerationColumns.collect {DataElement column ->
            synchronized (originalDatabaseIdentifiers) {

                String columnName = originalDatabaseIdentifiers.get(column)
                String columnNameEscaped = databaseDomain.escapeIdentifier(columnName)

                boolean useLookup=false

                if(lookup_display!=null) {
                    (importParams.enumLookupAdornment as List<String>)?.each { if(column.label.toLowerCase().endsWith(it.toLowerCase())) {useLookup = true} }
                }

                databaseDomain.queryForSummaryMetadataForEnumerations(
                    catalogName,
                    schemaName,
                    originalDatabaseIdentifiers.get(tableClass),
                    columnName,
                    useLookup?
                        databaseDomain.normaliseEnumerationValueSql(databaseDomain.lookupCodeDescriptionSql(databaseDomain.escapeIdentifier(lookup_display),databaseDomain.escapeIdentifier(lookup_value),databaseDomain.escapeIdentifier(lookup_table),columnNameEscaped))
                        :
                        databaseDomain.normaliseEnumerationValueSql(columnNameEscaped),
                    databaseDomain.greatest(databaseDomain.countColumn(columnName),"${databaseDomain.getSUMMARY_METADATA_FLOOR()}"),
                    databaseDomain.getMAX_ENUMERATION_VALUES()
                )
            }
        }

        String query = databaseDomain.joinSelects(subqueries)

        log.debug(query)

        PreparedStatement summaryMetadataStatement = connection.prepareStatement(query)
        Map<String, String> summaryMetadataJson = resultSetToList(summaryMetadataStatement.executeQuery()).first() as Map<String, String>

        enumerationColumns.each {
            String reportValueFromQuery=summaryMetadataJson[it.label.toLowerCase()]
            // At this point the JSON is in the wrong format
            if(reportValueFromQuery) {

                Map<String,Long> reportValuesMap=databaseDomain.enumerationSummaryMetadataValuesToMapLong(reportValueFromQuery)

                // log.debug("reportValuesMap ${reportValuesMap}")

                final JsonBuilder jsonBuilder = new JsonBuilder(reportValuesMap)
                final String reportValue = jsonBuilder.toString()
                log.debug("histogram reportValue ${reportValue}")

                SummaryMetadata summaryMetadata = new SummaryMetadata(label: it.label, summaryMetadataType: SummaryMetadataType.MAP, summaryMetadataReports: [])
                summaryMetadata.summaryMetadataReports << new SummaryMetadataReport(reportValue: reportValue, reportDate: Instant.now())
                synchronized (it.summaryMetadata) {
                    if (it.summaryMetadata) {
                        log.error 'Adding to summaryMetadata (enumeration) but the size is already {} for {}', it.summaryMetadata.size(), it.label
                    }
                    it.summaryMetadata << summaryMetadata
                }
                synchronized (tableClass.summaryMetadata) {
                    tableClass.summaryMetadata << summaryMetadata
                }
                addMetadata(it, new Metadata(namespace: EXPLORER_NAMESPACE, key: 'entropy', value: calculateEntropy(reportValuesMap)))
                addMetadata(it, new Metadata(namespace: EXPLORER_NAMESPACE, key: 'imbalance', value: calculateImbalance(reportValuesMap)))
                addMetadata(it, new Metadata(namespace: EXPLORER_NAMESPACE, key: 'suggestionIndex', value: calculateSuggestionIndex(reportValuesMap)))
            }
        }
    }

    /* Some stats functions on maps of category -> long (output of binned or enumerated data) */

    static float calculateEntropy(final Map<String, Long> frequencyMap) {
        long sum = 0

        ;{
            Iterator<String> valueLabelIterator = frequencyMap.keySet().iterator();
            while (valueLabelIterator.hasNext()) {
                final String valueLabel = valueLabelIterator.next()
                if (
                    "<null>" == valueLabel ||
                    "<blank>" == valueLabel ||
                    "<no value>" == valueLabel
                ) {
                    continue
                }

                final long frequency = frequencyMap.get(valueLabel)
                sum += frequency
            }
        }

        if (sum == 0) {return 0f}

        double entropy = 0
        double k = 0
        ;{
            Iterator<String> valueLabelIterator = frequencyMap.keySet().iterator()
            while (valueLabelIterator.hasNext()) {
                final String valueLabel = valueLabelIterator.next()
                if (
                    "<null>" == valueLabel ||
                    "<blank>" == valueLabel ||
                    "<no value>" == valueLabel
                ) {
                    continue
                }
                final long frequency = frequencyMap.get(valueLabel)
                final double p = (double) frequency.div((double) sum)
                final double pxlog = (p * Math.log10(p))
                entropy += pxlog
                k++
            }
        }

        // Normalised
        entropy = entropy / Math.log10(k)

        // 2dp
        entropy = Math.round(entropy * 100) / 100.0
        entropy = Math.abs(entropy)

        return (float) entropy
    }

    static float calculateImbalance(final Map<String, Long> frequencyMap) {
        long sum = 0
        long maxFrequency = 0

        ;{
            Iterator<String> valueLabelIterator = frequencyMap.keySet().iterator()
            while (valueLabelIterator.hasNext()) {
                final String valueLabel = valueLabelIterator.next()
                if (
                    "<null>" == valueLabel ||
                    "<blank>" == valueLabel ||
                    "<no value>" == valueLabel
                ) {
                    continue
                }
                final long frequency = frequencyMap.get(valueLabel)
                sum += frequency

                if (frequency > maxFrequency) {maxFrequency = frequency}
            }
        }

        if (sum == 0) {return 1f}

        double imbalance = (double) maxFrequency / (double) sum
        imbalance = Math.round(imbalance * 100) / 100.0

        return (float) (imbalance)
    }

    static float calculateSuggestionIndex(final Map<String, Long> frequencyMap) {
        final float entropy = calculateEntropy(frequencyMap)
        final float imbalance = calculateImbalance(frequencyMap)

        double suggestionIndex = (0.6D * entropy) + (0.4D * (1.0D - imbalance))

        return (float) Math.round(suggestionIndex * 100) / 100.0
    }

    // When the data can't be put into bins, yet we know the number of distinct values
    // as well as the total number of rows, this is an estimate of the entropy
    // Because the imbalance for this case happens to be 1-entropy and the suggestion index is (currently) the
    // weighted sum of the two, the suggestion index equals this basic estimated entropy
    static float calculateBasicEntropy(final long distinctValues, final long totalNumberOfValues) {
        if (distinctValues == 0) {
            return 0
        }
        final double H_est = Math.log10(distinctValues) / Math.log10(totalNumberOfValues)

        double entropy = Math.round(H_est * 100) / 100.0
        entropy = Math.abs(entropy)

        return (float) (entropy)
    }

    /* */

    static class SummaryMetadataForDatesAndNumbers {
        List<DataElement> dateAndNumericElements
        String catalogName
        String schemaName
        DataClass tableClass
    }

    List<SummaryMetadataForDatesAndNumbers> determineImportSummaryMetadataForDatesAndNumbers(String catalogName, String schemaName, DataClass tableClass) {
        final List<DataElement> dateAndNumericElements = new ArrayList<>(tableClass.dataElements.size())
        looking:
        for (DataElement dataElement : tableClass.dataElements) {
            for (String enumIgnoreColumn : importParams.enumIgnoreColumn as List<String>) {
                if (dataElement.label.toLowerCase() == enumIgnoreColumn.toLowerCase()) {
                    continue looking
                }
            }
            for (String enumIgnoreColumnLike : importParams.enumIgnoreColumnLike as List<String>) {
                if (dataElement.label.toLowerCase().indexOf(enumIgnoreColumnLike.toLowerCase()) != -1) {
                    continue looking
                }
            }

            if ((isDateOrTime(dataElement) || isNumeric(dataElement)) && dataElement.dataType.dataTypeKind != DataType.DataTypeKind.ENUMERATION_TYPE) {
                dateAndNumericElements.add(dataElement)
            }
        }

        List<SummaryMetadataForDatesAndNumbers> summaryMetadataForDatesAndNumbersList=[]

        dateAndNumericElements.collate(databaseDomain.getCHUNK_SIZE()).each {List<DataElement> it ->

            if(it.size()>0) {
                SummaryMetadataForDatesAndNumbers summaryMetadataForDatesAndNumbers = new SummaryMetadataForDatesAndNumbers()

                summaryMetadataForDatesAndNumbers.dateAndNumericElements = it
                summaryMetadataForDatesAndNumbers.catalogName = catalogName
                summaryMetadataForDatesAndNumbers.schemaName = schemaName
                summaryMetadataForDatesAndNumbers.tableClass = tableClass

                summaryMetadataForDatesAndNumbersList.push(summaryMetadataForDatesAndNumbers)
            }
        }

        return summaryMetadataForDatesAndNumbersList
    }

    void importSummaryMetadataForDatesAndNumbers(Connection connection, SummaryMetadataForDatesAndNumbers summaryMetadataForDatesAndNumbers) {
        String catalogName=summaryMetadataForDatesAndNumbers.catalogName
        String schemaName=summaryMetadataForDatesAndNumbers.schemaName
        DataClass tableClass=summaryMetadataForDatesAndNumbers.tableClass

        final List<DataElement> dateAndNumericElements=summaryMetadataForDatesAndNumbers.dateAndNumericElements

        synchronized (originalDatabaseIdentifiers) {
                List<String> histogramSelects = dateAndNumericElements.collect {DataElement dataElement ->
                if (isDateOrTime(dataElement)) {

                    Metadata minMetadata=dataElement.metadata.find {it.key == 'min_value'}
                    Metadata maxMetadata=dataElement.metadata.find {it.key == 'max_value'}
                    if(minMetadata !=null && maxMetadata!=null) {
                        String minValue = minMetadata.value
                        String maxValue = maxMetadata.value
                        if (minValue && maxValue) {
                            // java.time.Instant will only accept timestamps and therefore the minimum date it will
                            // accept is 1970-01-01
                            // it will reject a date such as: '0131-08-24Z'
                            // The date column is stored as a date and therefore must be a valid date
                            // So use a date parser rather than a timestamp parser

                            try {
                                LocalDate minDate = parseISO_LOCAL_DATE(minValue)
                                LocalDate maxDate = parseISO_LOCAL_DATE(maxValue)
                                long days = ChronoUnit.DAYS.between(minDate, maxDate)

                                if (days > 70000) {
                                    // group by centuries
                                    databaseDomain.queryForSummaryMetadataForDateCenturies(
                                        databaseDomain.concat([databaseDomain.escapeIdentifier('century'),"'-'","${databaseDomain.escapeIdentifier('century')} + 99"]),
                                        databaseDomain.greatest('count',"${databaseDomain.getSUMMARY_METADATA_FLOOR()}"),
                                        databaseDomain.centuryFromDate(originalDatabaseIdentifiers.get(dataElement)),
                                        catalogName,
                                        schemaName,
                                        originalDatabaseIdentifiers.get(tableClass),
                                        originalDatabaseIdentifiers.get(dataElement)
                                    )
                                } else
                                if (days > 7000) {
                                    // group by decades
                                    databaseDomain.queryForSummaryMetadataForDateDecades(
                                        databaseDomain.concat([databaseDomain.escapeIdentifier('decade'),"'-'","${databaseDomain.escapeIdentifier('decade')} + 9"]),
                                        databaseDomain.greatest('count',"${databaseDomain.getSUMMARY_METADATA_FLOOR()}"),
                                        databaseDomain.decadeFromDate(originalDatabaseIdentifiers.get(dataElement)),
                                        catalogName,
                                        schemaName,
                                        originalDatabaseIdentifiers.get(tableClass),
                                        originalDatabaseIdentifiers.get(dataElement)
                                    )
                                } else
                                if (days > 700) {
                                    // group by years
                                    databaseDomain.queryForSummaryMetadataForDateYears(
                                        databaseDomain.escapeIdentifier('year'),
                                        databaseDomain.greatest('count',"${databaseDomain.getSUMMARY_METADATA_FLOOR()}"),
                                        databaseDomain.yearFromDate(originalDatabaseIdentifiers.get(dataElement)),
                                        catalogName,
                                        schemaName,
                                        originalDatabaseIdentifiers.get(tableClass),
                                        originalDatabaseIdentifiers.get(dataElement)
                                    )
                                } else
                                if (days > 70) {
                                    // Group by month year
                                    databaseDomain.queryForSummaryMetadataForDateMonthsYears(
                                        databaseDomain.concat([databaseDomain.escapeIdentifier('year'),"'-'",databaseDomain.twoDigits(databaseDomain.escapeIdentifier('month'))]),
                                        databaseDomain.greatest('count',"${databaseDomain.getSUMMARY_METADATA_FLOOR()}"),
                                        databaseDomain.yearFromDate(originalDatabaseIdentifiers.get(dataElement)),
                                        databaseDomain.monthFromDate(originalDatabaseIdentifiers.get(dataElement)),
                                        catalogName,
                                        schemaName,
                                        originalDatabaseIdentifiers.get(tableClass),
                                        originalDatabaseIdentifiers.get(dataElement)
                                        )
                                } else {
                                    // Group by days
                                    databaseDomain.queryForSummaryMetadataForDateDaysMonthsYears(
                                        databaseDomain.concat([databaseDomain.escapeIdentifier('year'),"'-'",databaseDomain.twoDigits(databaseDomain.escapeIdentifier('month')),"'-'",databaseDomain.twoDigits(databaseDomain.escapeIdentifier('day'))]),
                                        databaseDomain.greatest('count',"${databaseDomain.getSUMMARY_METADATA_FLOOR()}"),
                                        databaseDomain.yearFromDate(originalDatabaseIdentifiers.get(dataElement)),
                                        databaseDomain.monthFromDate(originalDatabaseIdentifiers.get(dataElement)),
                                        databaseDomain.dayFromDate(originalDatabaseIdentifiers.get(dataElement)),
                                        catalogName,
                                        schemaName,
                                        originalDatabaseIdentifiers.get(tableClass),
                                        originalDatabaseIdentifiers.get(dataElement)
                                    )
                                }
                            }
                            catch (DateTimeParseException dtpe) {
                                log.error("Histogram. Date parse error in " + schemaName + "." + tableClass.label + "." + dataElement.label)
                                log.error("minValue=" + minValue)
                                log.error("maxValue=" + maxValue)
                            }
                        }
                    }
                }
                else
                if(isInteger(dataElement)){
                    Metadata minMetadata=dataElement.metadata.find {it.key == 'min_value'}
                    Metadata maxMetadata=dataElement.metadata.find {it.key == 'max_value'}
                    Metadata distinctValuesCountMetadata=dataElement.metadata.find { it.key == 'distinct_values_count' }

                    if(minMetadata !=null && maxMetadata!=null && distinctValuesCountMetadata!=null) {
                        String minValue = minMetadata.value
                        String maxValue = maxMetadata.value
                        String distinctValuesCount = distinctValuesCountMetadata.value

                        if (minValue && maxValue) {
                            final long minLong = Long.parseLong(minValue, 10)
                            final long maxLong = Long.parseLong(maxValue, 10)

                            final long interval = maxLong - maxLong

                            long distinctValuesCountLong
                            if (distinctValuesCount) {
                                distinctValuesCountLong = Long.parseLong(distinctValuesCount, 10)
                            } else {
                                distinctValuesCountLong = interval
                            }

                            long lowestBinValue = makeLowestIntervalValueForBin(minLong)
                            long highestBinValue = makeHighestIntervalValueForBin(maxLong)
                            long binInterval = makeBinInterval(distinctValuesCountLong, lowestBinValue, highestBinValue)

                            // group by binInterval
                            // The SQL needs to convert the value to a bin: lowestBinValue + (floor(value / binInterval) * binInterval)
                            // The interval is binStart to binStart + binInterval -1

                            databaseDomain.queryForSummaryMetadataForInteger(
                                (binInterval - 1) ?
                                    databaseDomain.concat([databaseDomain.escapeIdentifier('binStart'),"'-'","(${databaseDomain.escapeIdentifier('binStart')} + ${binInterval - 1})"])
                                    :
                                databaseDomain.escapeIdentifier('binStart'),
                                databaseDomain.greatest('count',"${databaseDomain.getSUMMARY_METADATA_FLOOR()}"),
                                databaseDomain.binStart(lowestBinValue,binInterval,originalDatabaseIdentifiers.get(dataElement)),
                                catalogName,
                                schemaName,
                                originalDatabaseIdentifiers.get(tableClass),
                                originalDatabaseIdentifiers.get(dataElement)
                            )
                        }
                    }
                }

            }.findAll() as List<String>

            if (!histogramSelects) return

            String histogramQuery = databaseDomain.joinSelects(histogramSelects)

            log.debug("histogramQuery ${histogramQuery}")

            PreparedStatement histogramStatement = connection.prepareStatement(histogramQuery)
            Map<String, String> histogramJson = resultSetToList(histogramStatement.executeQuery()).first() as Map<String,String>

            log.debug("histogramJson ${histogramJson}")

            dateAndNumericElements.findAll {histogramJson[it.label.toLowerCase()]}.each {

                String reportValueFromQuery=histogramJson[it.label.toLowerCase()]

                Map<String,Long> reportValuesMap=databaseDomain.enumerationSummaryMetadataIntervalsToMapLong(reportValueFromQuery)

                final JsonBuilder jsonBuilder = new JsonBuilder(reportValuesMap)
                final String reportValue = jsonBuilder.toString()
                log.debug("histogram dateAndNumericElements ${it.label.toLowerCase()} reportValue ${reportValue}")

                SummaryMetadata summaryMetadata = new SummaryMetadata(label: it.label, summaryMetadataType: SummaryMetadataType.MAP, summaryMetadataReports: [])
                summaryMetadata.summaryMetadataReports << new SummaryMetadataReport(reportValue: reportValue, reportDate: Instant.now())
                synchronized (it.summaryMetadata) {
                    if (it.summaryMetadata) {
                        log.error 'Adding to summaryMetadata (date/numeric) but the size is already {} for {}', it.summaryMetadata.size(), it.label
                    }
                    it.summaryMetadata << summaryMetadata
                }
                synchronized (tableClass.summaryMetadata) {
                    tableClass.summaryMetadata << summaryMetadata
                }

                addMetadata(it, new Metadata(namespace: EXPLORER_NAMESPACE, key: 'entropy', value: calculateEntropy(reportValuesMap)))
                addMetadata(it, new Metadata(namespace: EXPLORER_NAMESPACE, key: 'imbalance', value: calculateImbalance(reportValuesMap)))
                addMetadata(it, new Metadata(namespace: EXPLORER_NAMESPACE, key: 'suggestionIndex', value: calculateSuggestionIndex(reportValuesMap)))
            }
        }
    }

    // date parsing
    final static DateTimeFormatter iso_local=DateTimeFormatter.ISO_LOCAL_DATE

    static LocalDate parseISO_LOCAL_DATE(final String dateString /* yyyy-MM-dd HH:mm:ss.S */) throws DateTimeParseException {
        // Remove everything after the space

        final int space=dateString.indexOf(' ')
        final String yyyyMMdd

        if(space!=-1) {
            yyyyMMdd=dateString.substring(0, space)
        }
        else
        {
            yyyyMMdd=dateString
        }

        LocalDate date=LocalDate.parse(yyyyMMdd,iso_local)
        return date
    }

    /*
    Functions for getting data bin intervals
     */
    static long makeBinInterval(final long distinctValuesCount,final long lowest_value, final long highest_value) {
        long numberOfBins = (long) Math.max(3,Math.min(8,Math.ceil(Math.log10(distinctValuesCount))))
        long binInterval=makeLowestIntervalValueForBin((highest_value-lowest_value).intdiv(numberOfBins))
        if(binInterval<=0){binInterval=1}
        return binInterval
    }

    static long makeLowestIntervalValueForBin(final long min_value) {
        final double digits=Math.max(1.0D,Math.floor(Math.log10((double) min_value))-1)
        long multiplier=(long) Math.pow(10,digits)

        if(multiplier==0){multiplier=1}

        return min_value.intdiv(multiplier) * multiplier
    }

    static long makeHighestIntervalValueForBin(final long max_value) {
        final double digits=Math.max(1.0D,Math.floor(Math.log10((double) max_value))-1)
        long multiplier=(long) Math.pow(10,digits)

        if(multiplier==0){multiplier=1}

        return (max_value.intdiv(multiplier) * multiplier) + multiplier
    }

    /* Check for existence of metadata */

    static Metadata getMetadata(final List<Metadata> metadataList,final String namespace,final String key) {
        synchronized (metadataList) {
            metadataList.find { it.key == key && it.namespace == namespace }
        }
    }

}

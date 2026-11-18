package org.maurodata.plugin.sql.database

import org.maurodata.domain.datamodel.DataClass
import org.maurodata.domain.datamodel.DataElement
import org.maurodata.domain.datamodel.DataModel
import org.maurodata.domain.datamodel.DataType
import org.maurodata.domain.facet.Metadata
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class SQLDatabaseDomainImporterPolicySpec extends Specification {

    @TempDir Path directory

    private SQLDatabaseDomainImporter importer(Path config, Map domainOverrides = [:]) {
        SQLDatabaseDomain domain = Stub(SQLDatabaseDomain) {
            isString(_) >> true
            isInteger(_) >> false
            isDecimal(_) >> false
            isDate(_) >> false
            isDateTime(_) >> false
            isTime(_) >> false
            isLOB(_) >> false
            getACCEPT_ENUMERATION_VALUES() >> 80
        }
        new SQLDatabaseDomainImporter(domain, [yamlConfigPath: config.toString()] + domainOverrides).tap {
            importConfig = SQLDatabaseImportConfig.load(config.toString())
        }
    }

    private List<SQLDatabaseDomainImporter.EnumerationColumns> selections(SQLDatabaseDomainImporter importer, Map rule) {
        DataElement code = new DataElement(label: 'Status_Code', dataType: new DataType(label: 'VARCHAR'),
            metadata: [new Metadata(key: 'distinct_values_count', value: '120')])
        DataElement ordinary = new DataElement(label: 'Description', dataType: new DataType(label: 'VARCHAR'),
            metadata: [new Metadata(key: 'distinct_values_count', value: '5')])
        DataClass table = new DataClass(label: 'Person', dataElements: [code, ordinary])
        importer.determineImportEnumerationColumns(new DataModel(label: 'Clinical'), 'catalog', 'Clinical', table)
    }

    def 'explicit YAML enumeration choices override heuristic and legacy suffix policy'() {
        given:
        Path yaml = directory.resolve('enum.yaml')
        Files.writeString(yaml, '''
'.*':
  '.*':
    '.*':
      enumerationIfLowCardinalityHeuristic: false
    status_code:
      enumeration: true
    description:
      enumeration: false
''')
        def importer = importer(yaml, [enumGivenAdornment: [], enumLookupAdornment: []])

        when:
        def found = selections(importer, [:]).collectMany { it.enumerationColumns }*.label

        then:
        found == ['Status_Code']
    }

    def 'YAML heuristic suppression does not suppress the existing low-cardinality default when absent'() {
        given:
        Path yaml = directory.resolve('default.yaml')
        Files.writeString(yaml, "'.*': {}")
        def importer = importer(yaml)

        when:
        def found = selections(importer, [:]).collectMany { it.enumerationColumns }*.label

        then:
        found.contains('Description')
    }

    def 'first configured count identifier contributes a distinct count query and its configured label'() {
        given:
        Path yaml = directory.resolve('count.yaml')
        Files.writeString(yaml, '''
Clinical:
  Person:
    person_id:
      countIdentifier: true
      countLabel: patient identifier
    alternate_id:
      countIdentifier: true
      countLabel: secondary identifier
''')
        SQLDatabaseDomain domain = Stub(SQLDatabaseDomain) {
            countAll() >> 'COUNT(*)'
            countDistinct(_) >> { String name -> "COUNT(DISTINCT ${name})" }
            valueExpressionAsLabel(_, _) >> { String expression, String label -> "${expression} AS ${label}" }
            countWhere(_, _) >> 'COUNT(*)'
            isLOB(_) >> false
            isString(_) >> false
            isInteger(_) >> false
            isDecimal(_) >> false
            isDate(_) >> false
            isDateTime(_) >> false
            isTime(_) >> false
        }
        def importer = new SQLDatabaseDomainImporter(domain, [yamlConfigPath: yaml.toString()])
        importer.importConfig = SQLDatabaseImportConfig.load(yaml.toString())
        DataElement first = new DataElement(label: 'Person_Id', dataType: new DataType(label: 'integer'))
        DataElement second = new DataElement(label: 'Alternate_Id', dataType: new DataType(label: 'integer'))
        DataClass schema = new DataClass(label: 'Clinical')
        DataClass table = new DataClass(label: 'Person', parentDataClass: schema, dataElements: [first, second])
        importer.originalDatabaseIdentifiers.put(table, 'Person')
        importer.originalDatabaseIdentifiers.put(first, 'person_id')
        importer.originalDatabaseIdentifiers.put(second, 'alternate_id')

        when:
        String query = importer.createImportRowCounts('catalog', 'Clinical', table).first().query

        then:
        query.contains('COUNT(DISTINCT person_id) AS __count_identifier')
        !query.contains('COUNT(DISTINCT alternate_id) AS __count_identifier')
        importer.importConfig.options('Clinical', 'Person', 'Person_Id').countLabel == 'patient identifier'
    }
}

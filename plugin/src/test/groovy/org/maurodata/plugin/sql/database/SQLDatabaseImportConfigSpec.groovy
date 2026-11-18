package org.maurodata.plugin.sql.database

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class SQLDatabaseImportConfigSpec extends Specification {

    @TempDir Path directory

    def 'loads case-insensitive full-name regex rules and merges matching defaults in document order'() {
        given:
        Path yaml = directory.resolve('vendor-rules.yaml')
        Files.writeString(yaml, '''
'.*':
  '.*':
    '.*':
      enumerationIfLowCardinalityHeuristic: false
      enumLookupEnabled: false
    '.*_CODE':
      enumeration: true
      enumLookupTable: >-
        select label, code
        from reference.codes
        where vocabulary = 'ICD10' and retired is null
      enumDisplayColumn: label
      enumCodeColumn: code
Clinical:
  Person:
    Status_Code:
      enumeration: false
      enumLookupEnabled: true
''')
        when:
        SQLDatabaseImportConfig config = SQLDatabaseImportConfig.load(yaml.toString())
        Map options = config.options('clinical', 'PERSON', 'status_code')

        then:
        options.enumeration == false
        options.enumerationIfLowCardinalityHeuristic == false
        options.enumLookupEnabled == true
        options.enumLookupTable == "select label, code from reference.codes where vocabulary = 'ICD10' and retired is null"
        options.enumDisplayColumn == 'label'
        options.enumCodeColumn == 'code'
        config.options('clinical', 'person', 'other').enumLookupTable == null
    }

    def 'exclusions apply at schema, table, and column scope and parents exclude descendants'() {
        given:
        Path yaml = directory.resolve('exclude.yaml')
        Files.writeString(yaml, '''
private_.*:
  $:
    exclude: true
public:
  audit_.*:
    $:
      exclude: true
  person:
    secret_.*:
      exclude: true
    retained:
      exclude: false
''')
        SQLDatabaseImportConfig config = SQLDatabaseImportConfig.load(yaml.toString())

        expect:
        config.excluded('private_schema')
        config.excluded('public', 'audit_log')
        config.excluded('public', 'person', 'secret_value')
        !config.excluded('public', 'person', 'retained')
        !config.excluded('public', 'person', 'ordinary')
    }

    def 'invalid YAML root and invalid regex patterns fail with configuration errors'() {
        given:
        Path yaml = directory.resolve('invalid.yaml')

        when:
        Files.writeString(yaml, '- not-a-mapping')
        SQLDatabaseImportConfig.load(yaml.toString())

        then:
        thrown(IllegalArgumentException)

        when:
        Files.writeString(yaml, '"[": {}')
        SQLDatabaseImportConfig.load(yaml.toString())

        then:
        thrown(java.util.regex.PatternSyntaxException)
    }
}

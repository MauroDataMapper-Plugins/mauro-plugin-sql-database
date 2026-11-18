package org.maurodata.plugin.sql.database.cli

import picocli.CommandLine
import spock.lang.Specification

class SQLServerCommandSpec extends Specification {

    def 'CLI help exposes the vendor-targeted YAML policy option'() {
        given:
        StringWriter output = new StringWriter()

        when:
        new CommandLine(new SQLServerCommand()).usage(new PrintWriter(output))

        then:
        output.toString().contains('--yaml-config')
        output.toString().contains('Vendor-targeted YAML import policy file')
    }
}

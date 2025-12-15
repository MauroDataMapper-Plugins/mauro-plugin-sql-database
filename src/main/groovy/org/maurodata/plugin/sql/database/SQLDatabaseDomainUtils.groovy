package org.maurodata.plugin.sql.database

import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class SQLDatabaseDomainUtils {

    static JsonSlurper slurper = new JsonSlurper()

    /*
    Convert import parameters to a general Map<String, Object>
     */
    static Map<String,Object> translateToMapFromImportParameters(final SQLDatabaseDomainImportParams params) {
        final Map<String, Object> importParams = params.properties.findAll {it.key != 'class' && it.key instanceof String}
        return importParams
    }

    // Interpreting JSON query results

    /*
    Given JSON Lines format (lines where each individual line is a value JSON object, separated by newline \n
    {<keyField>:<valueField>}\n
    Return a map of {<keyField_value>:<valueField_value>}

    e.g.
    {"interval":"100-199", "count":23}\n
    ->
    {"100-199":"23"}
     */
    static Map<String,String> jsonObjectLinesToMapString(final String jsonValue, final String keyField, final String valueField){
        try {
            final StringTokenizer st=new StringTokenizer(jsonValue,'\n')
            Map<String,String> valuesMap=new LinkedHashMap<>(st.countTokens()+1)
            while(st.hasMoreTokens()) {
                final String valueCountJSON=st.nextToken()
                final Map valueCountJSONMap=slurper.parseText(valueCountJSON) as Map<String,Object>
                valuesMap.put(valueCountJSONMap.get(keyField) as String,valueCountJSONMap.get(valueField).toString())
            }
            return valuesMap
        } catch (JsonException e) {
            log.error "JsonException parsing enumeration values"
            log.error jsonValue
            throw e
        }
    }

    /*
    Same as jsonObjectLinesToMapString, but the values are interpreted as Long rather than String types
     */
    static Map<String,Long> jsonObjectLinesToMapLong(final String jsonValue, final String keyField, final String valueField){
        final StringTokenizer st=new StringTokenizer(jsonValue,'\n')
        Map<String,Long> valuesMap=new LinkedHashMap<>(st.countTokens()+1)
        while(st.hasMoreTokens()) {
            final String valueCountJSON=st.nextToken()
            final Map valueCountJSONMap=slurper.parseText(valueCountJSON) as Map<String,Object>
            valuesMap.put(valueCountJSONMap.get(keyField) as String,Long.parseLong(valueCountJSONMap.get(valueField).toString(),10))
        }
        return valuesMap
    }

    /*
    Given a json object, return a Map<String,String>
     */

    static Map<String,String> jsonObjectToMapString(final String json) {
        return slurper.parseText(json) as Map<String, String>
    }

    /*
    Given a json object, return a Map<String,Long>
     */
    static Map<String,Long> jsonObjectToMapLong(final String json) {
        return slurper.parseText(json) as Map<String, Long>
    }
}

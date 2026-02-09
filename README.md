# mauro-plugin-sql-database

Available as a plugin for mauro-micronaut and also as a CLI tool.

## CLI usage

Pass the CLI args via `--args='...'` using `./gradlew` (Mac/Linux) or `gradlew.bat` (Windows):

```text
% ./gradlew run --args='--help'

> Task :cli:run
Usage: mauro-plugin-database-sqlserver [-hV] [--catalog-as-datamodel]
                                       [--host=<databaseHost>] [-o=<output>]
                                       [-p=<password>] [--port=<databasePort>]
                                       [-q=<enumLookupTable>] [-u=<username>]
                                       [-d=<databaseNames>]...
                                       [-e=<excludeTablesLike>]...
                                       [-g=<enumGivenAdornment>]...
                                       [-i=<enumIgnoreColumn>]...
                                       [--included-tables=<includeTablesLike>]..
                                       . [-j=<enumIgnoreColumnLike>]...
                                       [-l=<enumLookupAdornment>]...
                                       [-r=<excludeSchemaNames>]...
                                       [-s=<schemaNames>]... [@<filename>...]
Extract metadata from SQLServer schema
      [@<filename>...]    One or more argument files containing options.
      --catalog-as-datamodel
                          Import catalogs/databases as datamodels
  -d, --database=<databaseNames>
                          Database name(s). A name of a database to connect to,
                            the database name will be used as the DataModel name
                          unless the DataModel name option is supplied.
                          If multiple names supplied then DataModel name will
                            be ignored and the database name will be used as
                            the DataModel name,
                          and the same username and password will be used for
                            all named databases.
  -e, --excluded-tables=<excludeTablesLike>
                          Ignore table names like
  -g, --enum-given-adornment=<enumGivenAdornment>
                          Adornment added to a column to denote it holds
                            enumerable code values
  -h, --help              Show this help message and exit.
      --host=<databaseHost>
                          Database host
  -i, --enum-ignore-column=<enumIgnoreColumn>
                          Exact match of column names to ignore when detecting
                            enumerations
      --included-tables=<includeTablesLike>
                          Include table names like
  -j, --enum-ignore-column-like=<enumIgnoreColumnLike>
                          Match using LIKE of column names to ignore when
                            detecting enumerations
  -l, --enum-lookup-adornment=<enumLookupAdornment>
                          Adornment added to a column to denote it holds
                            enumerable code values that may be looked up in a
                            look up table to retrieve corresponding display
                            names
  -o, --output=<output>   Output file
  -p, --password=<password>
                          The password used to connect to the database
      --port=<databasePort>
                          Database port
  -q, --enum-lookup-table=<enumLookupTable>
                          Enumeration display value lookup table details to
                            look up display values for a column value via a
                            lookup table. In the form of a comma list:
                            <display>,<value>,<schema|table|view>. e.g. display,
                            code_value,reference.code_value
  -r, --excluded-schemas=<excludeSchemaNames>
                          Schema name(s) to exclude
  -s, --schema=<schemaNames>
                          Schema name(s) to import
  -u, --username=<username>
                          The username used to connect to the database
  -V, --version           Print version information and exit.
```

Example:

```bash
./gradlew run --args='-d metadata_simple -u sa -p "YourStrong!Passw0rd" -o sqlserver.json'
```
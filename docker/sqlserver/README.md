# Test SQL Server docker container
Run these commands in order. Press the play icon if you are using IntelliJ
# Building
```bash
   docker build -t azure-sql-edge-with-sqlcmd .
```
# Running
```bash
   docker run --rm -it -p 1433:1433 azure-sql-edge-with-sqlcmd
```
If all has gone well, you should see the T-SQL prompt in the terminal.

```text
Data loaded in
Starting T-SQL prompt
1> 
```

Test it has worked with:

```SQL
> USE metadata_simple
> GO

Changed database context to 'metadata_simple'.
> SELECT * FROM organisation
> GO
.....
```

# To run from the command line

Command line / run configurations

```bash
    java \
      -cp mauro-plugin-sql-database.main \
      org.maurodata.plugin.sql.database.sqlserver.SQLServerCommand \
      -d metadata_simple -u sa -p "YourStrong!Passw0rd" -o sqlserver.json
```

# Test Oracle docker container
Run these commands in order. Press the play icon if you are using IntelliJ
# Building
```bash
   docker build -t oracle-free-with-sqlplus .
```
# Running
```bash
   docker run --rm -it -p 1521:1521 -e ORACLE_PWD='BOpVnzFi9Ew=1' oracle-free-with-sqlplus
```
If all has gone well, you should see the sqlplus prompt in the terminal.
You may have to press enter to see it

```text
SQL> 
```

Test it has worked with: TODO:

```SQL
SQL> ALTER SESSION SET CONTAINER = FREEPDB1;

Session altered.
    
SQL> ALTER SESSION SET CURRENT_SCHEMA = METADATA_SIMPLE;

Session altered.

SQL> SELECT * FROM organisation;
...
```

# To run from the command line

Command line / run configurations

```bash
    java \
      -cp mauro-plugin-sql-database.main \
      org.maurodata.plugin.sql.database.oracle.OracleCommand \
      -d FREEPDB1 -u metadata_simple -p "BOpVnzFi9Ew=1" -o oracle.json
```

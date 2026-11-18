# Test Oracle docker container
Run these commands in order. Press the play icon if you are using IntelliJ
# Building
```bash
   docker build -t oracle-free-with-sqlplus .
```
# Running
```bash
   docker run --rm -d --name oracle-free-with-sqlplus -p 1521:1521 -e ORACLE_PWD='BOpVnzFi9Ew=1' oracle-free-with-sqlplus
```
The image starts Oracle using its built-in entrypoint. Follow the logs until
the database reports `DATABASE IS READY TO USE!`:

```bash
   docker logs -f oracle-free-with-sqlplus
```

The `create_metadata_simple.sql` startup script is run by the image when the
database starts. Open a SQL*Plus prompt in another terminal with:

```bash
   docker exec -it oracle-free-with-sqlplus sqlplus / as sysdba
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

# To run the importer from Gradle

```bash
./gradlew :cli:runOracle --args='-d FREEPDB1 -u metadata_simple -p "BOpVnzFi9Ew=1" -o oracle.json'
```

Stop and remove the container when finished:

```bash
docker stop oracle-free-with-sqlplus
```

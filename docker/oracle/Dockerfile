FROM container-registry.oracle.com/database/free:latest-lite

COPY docker-startup.sh /opt/oracle/
COPY create_metadata_simple.sql /opt/oracle/scripts/startup/

USER root

RUN chown oracle:oinstall /opt/oracle/docker-startup.sh && chmod u+rwx,g+xr,o-rwx /opt/oracle/docker-startup.sh

USER oracle

CMD [ "/bin/bash", "-c", "echo" ]
ENTRYPOINT ["/opt/oracle/docker-startup.sh"]

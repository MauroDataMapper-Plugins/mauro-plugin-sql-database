FROM mcr.microsoft.com/azure-sql-edge:latest

USER root

# Install required tools and the Microsoft keyring properly
RUN apt-get update && \
    apt-get install -y curl apt-transport-https gnupg ca-certificates && \
    mkdir -p /usr/share/keyrings && \
    curl -fsSL https://packages.microsoft.com/keys/microsoft.asc \
      | gpg --dearmor -o /usr/share/keyrings/microsoft-prod.gpg && \
    echo "deb [arch=amd64,arm64 signed-by=/usr/share/keyrings/microsoft-prod.gpg] https://packages.microsoft.com/debian/12/prod bookworm main" \
      > /etc/apt/sources.list.d/microsoft-prod.list && \
    apt-get update && \
    ACCEPT_EULA=Y apt-get install -y mssql-tools18 unixodbc-dev && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Add sqlcmd to PATH
ENV PATH="${PATH}:/opt/mssql-tools18/bin"

RUN mkdir -p /data && chmod a+rwx /data
COPY --link *.sql /data/
RUN chmod a+r /data/*.sql

COPY --link *.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/*

USER mssql

ENV ACCEPT_EULA=1
ENV MSSQL_SA_PASSWORD="YourStrong!Passw0rd"
ENV MSSQL_PID=Developer

CMD ["/bin/bash"]

EXPOSE 1433
ENTRYPOINT ["docker-startup.sh"]

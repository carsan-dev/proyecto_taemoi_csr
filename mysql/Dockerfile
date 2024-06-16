FROM mysql:8.0

COPY ./init-db.sh /docker-entrypoint-initdb.d/

RUN chmod +x /docker-entrypoint-initdb.d/init-db.sh

EXPOSE 3306
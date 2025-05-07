FROM ubuntu:latest
LABEL authors="amysu"

ENTRYPOINT ["top", "-b"]
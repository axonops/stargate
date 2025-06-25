# Stargate with Cassandra 5.0

This directory provides a Docker Compose configuration for running Stargate with Apache Cassandra 5.0.

## Prerequisites

- Docker Engine 1.10.0+
- Docker Compose 1.6.0+
- 6GB of available memory for Docker

## Quick Start

To start Stargate with Cassandra 5.0 backend:

```bash
./start_cass_50.sh
```

This will pull the necessary Docker images and start:
- 1 Cassandra 5.0 node
- 1 Stargate coordinator
- REST API service
- GraphQL API service
- Document API service

## Configuration Options

The startup script accepts the following options:

- `-t <tag>` or `-r <tag>`: Set the Stargate image tag/version (defaults to latest released version)
- `-l`: Enable DEBUG logging
- `-q`: Enable request logging

Example:
```bash
./start_cass_50.sh -t v2.1.0 -l
```

## Accessing Services

Once started, you can access:
- CQL: `localhost:9042`
- REST API: `http://localhost:8082`
- GraphQL API: `http://localhost:8080`
- Document API: `http://localhost:8180`
- Health Check: `http://localhost:8084/checker/readiness`

## Default Credentials

Authentication is enabled by default:
- Username: `cassandra`
- Password: `cassandra`

## Stopping Services

To stop all services:
```bash
docker-compose down
```

To stop and remove all data:
```bash
docker-compose down -v
```

## Environment Variables

You can customize the deployment by setting environment variables:
- `SGTAG`: Stargate version tag
- `LOGLEVEL`: Log level (INFO, DEBUG, etc.)
- `REQUESTLOG`: Enable request logging (true/false)
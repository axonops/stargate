#!/bin/sh

# Default to INFO as root log level
LOGLEVEL=INFO

# Default to latest released version
SGTAG=v2.1.0

while getopts "lqr:t:" opt; do
  case $opt in
    l)
      LOGLEVEL=DEBUG
      ;;
    q)
      REQUESTLOG=true
      ;;
    r)
      SGTAG=$OPTARG
      ;;
    t)
      SGTAG=$OPTARG
      ;;
    \?)
      echo "Valid options:"
      echo "  -t <tag> - Set image tag (version) for Stargate images - defaults to latest released version"
      echo "  -r <tag> - Same as -t"
      echo "  -l - Enable DEBUG logging"
      echo "  -q - Enable request logging"
      exit 1
      ;;
  esac
done

export LOGLEVEL
export REQUESTLOG
export SGTAG

echo "Running Stargate version $SGTAG with Cassandra 5.0"

docker-compose up -d
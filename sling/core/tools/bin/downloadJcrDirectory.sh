#!/bin/bash
# External tool for IntelliJ to download the contents of a directory via the Composum source servlet as a zip and unpack it,
# overriding the files of the selected directory.
# Arguments in "External Tool" entry: $FilePathRelativeToSourcepath$ , working dir: $FileDir$
# Optional arguments: host:port user:password protocol

set -e

if [ -n "$2" ]; then
  HOSTPORT=(${2//:/ })
  CPM_HOST=${HOSTPORT[0]}
  CPM_PORT=${HOSTPORT[1]}
fi

if [ -n "$3" ]; then
  USERPASS=(${3//:/ })
  CPM_ADMINUSER=${USERPASS[0]}
  CPM_ADMINPASSWD=${USERPASS[1]}
fi

if [ -n "$4" ]; then
  CPM_PROTOCOL=($4)
fi

if [ -z "$CPM_HOST" ]; then
  CPM_HOST="localhost"
fi
if [ -z "$CPM_PORT" ]; then
  CPM_PORT="9090"
fi

if [ -z "$CPM_PROTOCOL" ]; then
   CPM_PROTOCOL=http
fi

if [ -z "$CPM_ADMINUSER" ]; then
   CPM_ADMINUSER=admin
fi

if [ -z "$CPM_ADMINPASSWD" ]; then
   CPM_ADMINPASSWD=admin
fi

echo Arguments "$*"
echo Dir: $(pwd)
echo URL: $CPM_PROTOCOL://$CPM_HOST:$CPM_PORT/bin/cpm/nodes/source.zip/$1

if [ -z $1 ]; then
    echo NO SOURCE DIR GIVEN
    exit 1
fi

TMPFIL=`mktemp -u`.zip
trap "{ rm -f $TMPFIL; }" EXIT
# echo temporary file: $TMPFIL

curl -s -S -o $TMPFIL -u $CPM_ADMINUSER:$CPM_ADMINPASSWD $CPM_PROTOCOL://$CPM_HOST:$CPM_PORT/bin/cpm/nodes/source.zip/$1

unzip -o -u $TMPFIL
unzip -l $TMPFIL

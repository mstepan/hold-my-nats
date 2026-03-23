#!/usr/bin/env bash

JVM_THREADS="-Djdk.virtualThreadScheduler.parallelism=2 -Djdk.traceVirtualThreadLocals -Djdk.tracePinnedThreads=full"
JVM_HEAP="-Xms1G -Xmx1G"
JVM_JMX="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.local.only=true -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1"

JVM_OPTS="$JVM_THREADS $JVM_HEAP $JVM_JMX"

java "$JVM_OPTS" --enable-preview -jar target/hold-my-nats-0.0.1-SNAPSHOT.jar
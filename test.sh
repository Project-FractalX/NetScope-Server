#!/usr/bin/env bash
# Run Spock tests using Java 21.
# Maven's default JVM (Homebrew Java 24) breaks Groovy's ASM bytecode reader.
set -e
JAVA_HOME=/Users/sathnindu/Library/Java/JavaVirtualMachines/openjdk-21.0.1/Contents/Home \
  mvn test "$@"

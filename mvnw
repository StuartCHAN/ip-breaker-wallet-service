#!/bin/sh
set -eu
exec "${JAVA_HOME:-}/bin/java" \
  -Dmaven.multiModuleProjectDirectory="$(pwd)" \
  -classpath ".mvn/wrapper/maven-wrapper.jar" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"

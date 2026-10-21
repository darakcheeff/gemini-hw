#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express fears or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

# Attempt to set APP_BASE_NAME to the basename of this script
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD to -1 to use the system default.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (both adapted from Apache Ant interfaces)
darwin=false
case "`uname`" in
  Darwin* )
    darwin=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
fi

APP_HOME="`pwd -P`"
if [ ! -e "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    # Try downloading wrapper jar if missing
    mkdir -p "$APP_HOME/gradle/wrapper"
    curl --retry 3 -fsSL "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar" -o "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" || true
fi

exec "$JAVACMD" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"

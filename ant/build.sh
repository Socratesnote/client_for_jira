#!/bin/sh

# Launches the Ant build. Run from the /ant/ directory.
#
# This script defines no paths of its own to reduce environment-dependent pathing errors.
# See ../README.md for per-shell instructions.
#
# Required arguments (example path for Windows):
#	ANT_HOME="C:/Program Files/Apache Ant 1.10.7"
#   JDK8_HOME="C:/Program Files/Java/jdk1.8.0_192"
#
# Optional arguments:
#   BUILD_NUMBER   Build number shown on the About screen and used in the ZIP
#                  file name. Defaults to 0.
#   ANT_FILE       Build file to run. Defaults to ./build.xml.
#
# Any arguments given to this script are passed through as Ant targets.
# Without arguments the full distribution is built.

set -e

if [ -z "$ANT_HOME" ]; then
    echo "build.sh: ANT_HOME is not set." >&2
    echo "Set it to the Apache Ant install directory (the one holding bin/ and lib/) and restart the shell." >&2
    exit 1
fi

if [ -z "$JDK8_HOME" ]; then
    echo "build.sh: JDK8_HOME is not set." >&2
    echo "Set it to the Oracle JDK 8 home directory (the one holding bin/ and lib/) and restart the shell." >&2
    exit 1
fi

# Expand explanations if the JDK path cannot be resolved.
if [ ! -x "$JDK8_HOME/bin/java" ] && [ ! -x "$JDK8_HOME/bin/java.exe" ]; then
    echo "build.sh: no java executable under \$JDK8_HOME/bin." >&2
    echo "  JDK8_HOME=$JDK8_HOME" >&2
    echo "This path is not readable from the current shell. If you are in WSL, a" >&2
    echo "Windows path like C:/... will not resolve; if you are in Git Bash, a" >&2
    echo "/mnt/c/... path will not resolve. See ../README.md." >&2
    exit 1
fi

if [ ! -f "$ANT_HOME/lib/ant-launcher.jar" ]; then
    echo "build.sh: ant-launcher.jar not found under \$ANT_HOME/lib." >&2
    echo "  ANT_HOME=$ANT_HOME" >&2
    exit 1
fi

# Build the distribution unless specific targets were requested.
if [ $# -eq 0 ]; then
    set -- prepareDistribution
fi

"$JDK8_HOME/bin/java" \
    -cp "$ANT_HOME/lib/ant-launcher.jar" \
    org.apache.tools.ant.launch.Launcher \
    -f "${ANT_FILE:-./build.xml}" \
    "$@" \
    -Djdk="$JDK8_HOME" \
    -Dbuild.number="${BUILD_NUMBER:-0}"

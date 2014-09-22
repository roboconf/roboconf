#!/bin/bash

# Get the Java version.
# Java 1.5 will give 15.
# Java 1.6 will give 16.
# Java 1.7 will give 17.
VER=`java -version 2>&1 | sed 's/java version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q'`

# We build for several JDKs on Travis.
# Some actions, like analyzing the code (Coveralls) and uploading
# artifacts on a Maven repository, should only be made for one version.

# If the version is 1.6, then perform the following actions.
# 1. Copy Maven settings on the VM.
# 2. Notify Coveralls.
# 3. Upload artifacts to Sonatype.
# 4. Use -q option to only display Maven errors and warnings.
# 5. Use --settings to force the usage of our "settings.xml" file.

if [ $VER == "16" ]; then
	curl -o settings.xml http://roboconf.net/resources/build/settings.xml
	mvn clean deploy cobertura:cobertura org.eluder.coveralls:coveralls-maven-plugin:cobertura -q --settings settings.xml
fi

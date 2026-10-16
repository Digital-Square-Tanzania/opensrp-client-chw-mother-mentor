
# opensrp-client-chw-mothermentor
OpenSRP client Mother Mentor module library

Build with JDK 17 (the version used by CI), Android SDK 33, and the checked-in Gradle wrapper.
On macOS, select JDK 17 before running Gradle:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew clean
./gradlew assemble
./gradlew :opensrp-chw-mother-mentor:publishToMavenLocal
```

The local library coordinate is `org.smartregister:opensrp-chw-mother-mentor:0.1.1-SNAPSHOT`.
The publication task also builds the release AAR when invoked on its own.


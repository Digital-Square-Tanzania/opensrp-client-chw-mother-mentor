
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

The local library coordinate is `org.smartregister:opensrp-chw-mother-mentor:0.1.7-SNAPSHOT`.
The publication task also builds the release AAR when invoked on its own.

## Visit action helpers

Each action has a separate, top-level class in
`opensrp-chw-mother-mentor/src/main/java/org/smartregister/chw/mothermentor/actionhelper`.
Service, household, mother IIT, partner, and Child EID actions each own their
completion rules, summaries, and form preprocessing. The retained contact workflow
uses its own Mother Mentor investigation helper.

`BaseMotherMentorActionHelper` shares only the form lifecycle, JSON access,
translation, and field-clearing utilities. Keep action-specific field identifiers
in `DBConstants.KEY` and completion rules in the concrete helper. Child EID test
recommendations load enrollment data through `MotherMentorDao`.

The library visit interactors assemble actions and decide their order, visibility,
and whether they are optional. Each service, household, mother IIT, partner, and
Child EID workflow has its own interactor in `mothermentor/interactor`. The CHW
activities select these library interactors and retain app navigation and form
launching. The household activity passes its configured family table to the
interactor; `MotherMentorDao` supplies the shared household profile lookup.
`MotherMentorFollowUpType` keeps follow-up routing and event identities in the
library. Follow-up status helpers notify the interactor through
a callback. When hiding a dependent action, use `setJsonPayload(null)` to reset
the helper's completion state along with its saved answer.

The reason section is specific to Mother IIT follow-up. Partner follow-up uses
status, facility attendance, and comments; Child EID follow-up also includes the
test stage. Existing non-attendance form assets and fields remain available for
historical records.

Run the focused library regressions before publishing locally:

```sh
./gradlew :opensrp-chw-mother-mentor:testDebugUnitTest --tests 'org.smartregister.chw.mothermentor.*'
```

After local publication, `MotherMentorVisitActionWiringTest` in the CHW app checks
that all workflows use library interactors and distinct library helpers and that hidden follow-up
answers and completion states are cleared together.


# Changelog

Notable changes to the Mother Mentor Android library are recorded here.
Versions below are Maven snapshot coordinates; `VERSION_CODE` is the library's
Android version code.

## Unreleased

### Added

- Automatically populate service-visit attendance as New or Repeat using the
  client's saved Mother Mentor service history, including visits awaiting local
  processing. Preserve attendance answers when reopening or editing a visit.
- Ask about new PMTCT ART initiation only when initiation was not already recorded
  at enrollment or a previous service visit. Keep the prior-initiation snapshot
  when editing and align English/Swahili form relevance and visit completion.

## 0.1.3-SNAPSHOT — 2026-10-19

### Changed

- Increment `VERSION_CODE` from 3 to 4 for the coordinated Mother Mentor, CHW Core,
  and CHW app release.
- Document the library changes included in the reporting workflows PR and update
  the README dependency coordinate.

### Removed

- Remove the unused `MotherMentorTbInvestigationActionHelper`,
  `MotherMentorPhysicalExamActionHelper`, `MotherMentorHtsActionHelper`, and
  `MotherMentorMedicalHistoryActionHelper`.
- Remove the TB-dependent sample-collection helper and its legacy action wiring.
- Remove the English and Swahili TB-investigation and sample-collection forms,
  their rules, and the orphaned physical-exam, HTS, and medical-history rules.
  Remove the associated form constants and action labels. Active Mother Mentor
  service, household, partner, Child EID, and IIT actions are unchanged.

### Integration

- CHW Core `2.2.0-MOTHER-MENTOR-ALPHA-3-MOH-SNAPSHOT` consumes this version.
- The companion CHW app uses the Swahili Child EID label
  “Mtoto wa ufwatitiaji wa ukusanyaji wa DBS”. These display strings live in the
  app; the library's EID field keys, event types, and clinical test stages remain
  unchanged.

## 0.1.2-SNAPSHOT — 2026-10-19

### Added

- Separate action helper classes for service visits, household visits, Mother IIT,
  partner follow-up, and Child EID follow-up, with focused regression coverage.
- Library visit interactors for each workflow, a shared follow-up type, household
  profile lookup, and Child EID enrollment lookup for test-stage recommendations.

### Changed

- Move visit action assembly and completion rules from the CHW app into the
  library. Keep action-specific behavior in individual helpers and share form
  lifecycle utilities through `BaseMotherMentorActionHelper`.
- Centralize database field names in `DBConstants` and provide additive reporting
  schema upgrades for the consuming application's versioned migration.
- Correct English and Swahili form text and capitalization, restore enrollment
  form loading, and normalize stored answers.
- Clear hidden follow-up answers and helper completion state together.

### Removed

- Remove the unused Mother Mentor contact registration button.
- Remove the reasons-for-not-attending action from partner and Child EID
  follow-up. Retain the historical form assets and fields; Mother IIT still uses
  its interruption-reason action.

## 0.1.1-SNAPSHOT

### Added

- Reporting fields for ART initiation, IIT tracing and return to care, partner and
  Child EID facility attendance, education, counselling, mobilization, and referrals.
- Partner and Child EID identity details and explicit mother linkage in the
  enrollment forms, preserving clinical questions and event identifiers.
- Completion rules for applicable visit sections, with compatibility for older
  saved visits, and additive mobilization reporting columns.

### Changed

- Centralize Mother Mentor English and Swahili forms and their rules in the library.
- Align the build with JDK 17, update the Android build tooling, retain the R8
  compatibility override, and repair release AAR and Maven Local publication.

### Integration

- The CHW app owns the monthly report, app navigation, and client eligibility checks
  (male partners aged 15 years or older and children younger than 18 months).
- The app must consume the updated library through CHW Core before removing its
  duplicate form assets. Existing records do not receive fabricated values for
  newly captured fields.

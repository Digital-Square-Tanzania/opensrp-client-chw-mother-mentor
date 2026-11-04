# Changelog

Notable changes to the Mother Mentor Android library are recorded here.
Versions below are Maven snapshot coordinates; `VERSION_CODE` is the library's
Android version code.

## Unreleased

### Changed

- Require an actively enrolled, eligible Mama Kinara mother before household enrollment, using the same PMTCT window as mother enrollment (CHW #1129).
- Increment the library to `0.1.15-SNAPSHOT` / Android code 16 for the shared eligibility API.

## 0.1.14-SNAPSHOT — 2026-11-04

### Added

- Add the mother visit pregnancy outcome branches in English and Swahili, including
  strict delivery/loss dates, immutable enrollment context, and branch clearing.
- Rebuild current delivery date, child DOB and pregnancy status from finalized
  visit evidence through an explicit database-63 migration API; retain raw history.
- Refresh schema-2 mother evidence on source replay and rebuild delivery projections
  after finalized insert, completion, correction, deletion and enrollment processing.
- Add the mother's child-status, breastfeeding and serial HEI-test questions to
  the existing routine-visit action in English and Swahili (#1142).
- Calculate calendar-based due stages, retain pending/unconfirmed results, and
  rebuild the current enrollment's child state from finalized visit history.
- Read dated results from an unambiguously linked existing Child EID client;
  automatic enrollment and grace-period classification remain outside this change.
- Expose explicit database-64/fresh-schema migration and native wizard adapters
  for the host app. This change depends on Mother visit Parts 1 and 2.
- Close household follow-up after confirmed relocation or consecutive unsuccessful
  visits, rebuilding the current enrollment's counters from finalized visit evidence.
- Provide localized closure confirmation, reasons, dates and active/closed register
  queries while retaining earlier visit history and resetting a new enrollment.
- Retain explicit Mother/child death closures projected by the Mother workflow
  without manufacturing household visits or overwriting its own visit counters.
- Close partner follow-up on success, death, relocation or the agreed consecutive-visit
  thresholds, preserving history and storing the closure reason, date and counters.
- Confirm death and relocation closures before submission in English and Swahili
  (CHW #1127).
- Prepare library `0.1.14-SNAPSHOT` / Android code 15 for the new closure contract.
- Close the current mother enrollment after a validated exit, rebuilding its reason,
  service date and consecutive Not found/Other counter from finalized visit history.
- Provide English and Swahili confirmation and closed-card messages, a Closed
  register filter and history-safe identity lookup, retaining earlier visits.
- Show child age in the mother register metadata when a valid child date of birth
  is available, and normalize legacy pregnancy-status display aliases.

### Fixed

- Allow a partner who did not attend the facility to complete follow-up with only
  the attendance answer and optional comments. Ask education after confirmed
  attendance, and require a valid date before advancing to tests. Preserve original
  education observations when editing a historical non-attendance visit that
  remains non-attendance (CHW #1126).

### Changed

- Capture household follow-up status and missing-household reasons; show service actions only for new or found visits and keep comments optional. (CHW #1130).
- Place optional partner tracing comments last in the English and Swahili
  follow-up status forms, after the selected agreement or missing-person reason.
  Keep the separate comments action for facility-attendance visits (CHW #1125).
- Reorganize mother service visit entry in English and Swahili: show attendance
  and optional comments first, then show services for a new or found client.
- Capture the four nested reasons when a returning mother is not found, clear
  inapplicable answers, and retain the original assets for legacy visit edits.
- Ask new-client ART initiation in group linkage while preserving the original
  field codes, previous-initiation rule, and read-only returning attendance.
- Provide enrollment-scoped, deduplicated mother visit context and clinical-day
  history for subsequent mother workflow sections.

## 0.1.13-SNAPSHOT — 2026-10-30

### Changed

- Increment the library version name from `0.1.12-SNAPSHOT` to `0.1.13-SNAPSHOT`
  and Android version code from 13 to 14.
- Own the Mother Mentor register, follow-up and linkage layouts, icons, colors,
  menu identifiers, and English/Swahili workflow strings in the library.
- Use the existing library household enrollment forms and consent relevance rule
  without app-level duplicates. Form fields and effective resource values are
  unchanged.

## 0.1.12-SNAPSHOT — 2026-10-29

### Changed

- Increment the library version name from `0.1.11-SNAPSHOT` to `0.1.12-SNAPSHOT`
  and Android version code from 12 to 13.

### Fixed

- Ask whether Child EID enrollment test results have returned before collecting
  positive or negative results in English and Swahili (CHW issue #1116).

## 0.1.11-SNAPSHOT — 2026-10-28

### Changed

- Increment the library version name from `0.1.10-SNAPSHOT` to `0.1.11-SNAPSHOT`
  and Android version code from 11 to 12.

### Fixed

- Restrict Child EID test choices to eligible ages and recorded weaning dates,
  retain catch-up tests, offer Not applicable before any test is due, and prevent
  saved drafts from restoring future stages (CHW issue #1111).

- Recognize finalized deceased outcomes for mothers, IIT clients, partners and
  Child EID clients, retain death status across later imports, and close only the
  deceased client’s enrollments through the normal death-event workflow.
- Separate partner tracing from the later facility-attendance visit, capture HIV,
  syphilis and hepatitis B testing outcomes or non-testing reasons, and retain
  optional comments in English and Swahili (CHW issue #1107).
- End completed partner follow-up after the local service day while retaining
  history and allowing later linkage; permanently exclude deceased partners.
- Capture identified partner education and final 18-month Child EID HIV results
  for the monthly report's previously unavailable data sources.

- Display Mother Mentor session details in the selected app language, including
  Swahili titles, field labels and coded options (CHW issue #1114).

- Capture Child EID birth order from 1 to 20 and generate HEI identifiers in the
  format `RR-CC-FFFF-XXXXXX-C01`, using the linked mother's CTC ID and a two-digit
  birth-order suffix (CHW issue #1109).
- Correct the Swahili partner HIV-disclosure question to use "anajua" instead of
  "amejua" (CHW issue #1108).
- Prevent mobilization referrals from exceeding the total number of people
  reached and educated in English and Swahili forms (CHW issue #1115).

## 0.1.10-SNAPSHOT — 2026-10-26

### Changed

- Increment the library version name from `0.1.9-SNAPSHOT` to `0.1.10-SNAPSHOT`
  and Android version code from 10 to 11.

### Fixed

- Show infant ages in completed months and newborn ages in days in English and
  Swahili. Omit the profile age when the date of birth is missing, invalid, or in
  the future (CHW issue #1095).
- Prevent duplicate completed Child EID follow-ups on the same local calendar
  day, using the recorded service date. Exclude drafts and incomplete visits,
  and allow another follow-up on the next day (CHW issue #1096).
- Detect completed household service and partner follow-up visits by the local
  service day, excluding pending or incomplete records (CHW issues #1089, #1090).
- Allow optional comments in Mother Mentor and household service visits
  (CHW issues #1091, #1092).
- Generate Child EID follow-up numbers from the linked mother’s CTC identifier
  and preserve that maternal context when selecting a child (CHW issue #1094).

## 0.1.9-SNAPSHOT — 2026-10-24

### Changed

- Increment the library version name from `0.1.8-SNAPSHOT` to `0.1.9-SNAPSHOT`
  and Android version code from 9 to 10.

### Fixed

- Keep saved or cancelled enrollment forms closed when switching between English
  and Swahili or restoring the register activity. Preserve enrollment details and
  allow new enrollment requests to open normally.

## 0.1.8-SNAPSHOT — 2026-10-24

### Changed

- Increment the library version name from `0.1.7-SNAPSHOT` to `0.1.8-SNAPSHOT`
  and Android version code from 8 to 9 for the merged group-history and Repeat
  attendance fixes.

### Added

- Prepopulate each group-linkage answer as already in the group when an earlier
  Mother Mentor service visit records psychosocial or IGA group linkage. Preserve
  saved visit answers and exclude the current or later visits when editing.

### Fixed

- Make repeat attendance read-only in subsequent Mother Mentor service visits,
  including when reopening a saved repeat visit, in English and Swahili.

## 0.1.7-SNAPSHOT — 2026-10-23

### Changed

- Increment the library version code from 7 to 8 and version name from
  `0.1.6-SNAPSHOT` to `0.1.7-SNAPSHOT` for the merged Mother Mentor fixes.

### Fixed

- Keep the profile pencil available for referrals even without a phone number.
  Open the call/referral menu, disable calling when no number is recorded, and
  reuse the menu with current member details across refreshes (CHW issue #1052).
- Translate the six Mother Mentor service-visit action headings into Swahili
  while retaining the English labels. Keep attendance-based action visibility
  working with localized headings (CHW issue #1048).

## 0.1.6-SNAPSHOT — 2026-10-23

### Added

- Add a primary-client register DAO for current pregnancy/breastfeeding status,
  completed Mother Mentor service-visit totals, name/CTC search, category filters,
  and deterministic pagination. Latest valid clinical service status falls back
  to enrollment when unavailable; drafts and other service types are excluded.
- Add 22dp Mama Kinara bottom-navigation icons matching the supplied mock for clients,
  households, follow-up, and community activities.

### Changed

- Increment the library version code to 7 and version name to `0.1.6-SNAPSHOT`.

### Fixed
- Keep the linked mother's name and CTC number read-only in partner details and
  the mother's name read-only in Child EID details in English and Swahili. These
  display-only fields no longer require manual entry when legacy data is missing.

- Display the recorded enrollment CTC ID in the client profile in English and
  Swahili, and hide the CTC row when no number is recorded.

- Remove “Lost to follow-up” / “Amepotea” from the attendance form's follow-up
  status choices in English and Swahili.

- Separate pregnancy and breastfeeding questions during Mother Mentor enrollment
  in English and Swahili. Ask breastfeeding status only for mothers who are not
  pregnant, while retaining the existing stored status codes for service visits.

- Allow partner enrollment to continue without a CTC number when the partner is
  enrolled in CTC services, while retaining format validation for entered numbers
  in English and Swahili.

- Rename the community activity form to “Community activities” in English and
  “Shughuli za Kijamii” in Swahili.

- Remove the redundant relationship-to-mother questions from partner enrollment
  in English and Swahili, retaining mother linkage and the HIV questions.
- Show the last HIV result only when prior testing is reported in both languages.


## 0.1.5-SNAPSHOT — 2026-10-20

### Fixed

- Use “mwenza” in Swahili partner enrollment, facility attendance, expected visit
  date, and household counselling labels.

### Changed

- Increment `VERSION_NAME` from `0.1.4-SNAPSHOT` to `0.1.5-SNAPSHOT` and
  `VERSION_CODE` from 5 to 6, and update the README dependency coordinate.

## 0.1.4-SNAPSHOT — 2026-10-20

### Added

- Automatically populate service-visit attendance as New or Repeat using the
  client's saved Mother Mentor service history, including visits awaiting local
  processing. Preserve attendance answers when reopening or editing a visit.
- Ask about new PMTCT ART initiation only when initiation was not already recorded
  at enrollment or a previous service visit. Keep the prior-initiation snapshot
  when editing and align English/Swahili form relevance and visit completion.

### Fixed

- Replace incorrect TB/leprosy references in Swahili Mother Mentor labels with
  Mama Kinara terminology, while retaining the tuberculosis education topic.
- Align Swahili visit, service history, caregiver, contact, and session labels
  with their English counterparts and add the five missing service translations.
- Correct English follow-up, navigation, contraindication, positive-result, and
  household counselling labels, and avoid repeating the call action in Swahili.

### Changed

- Increment `VERSION_NAME` from `0.1.3-SNAPSHOT` to `0.1.4-SNAPSHOT` and
  `VERSION_CODE` from 4 to 5.
- Update the README dependency coordinate to the new library version.

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

# Test data

Organisations (legekontor), mock patients and fastlegelister are seeded into the database by the
repeatable Flyway migration `src/main/resources/db/testdata/R__testdata.sql`. The file always shows
the current state. Flyway re-applies it whenever its content changes.

The seed is only applied when `database.seedTestdata` is `true`. It is `true` in
`application-local.yaml` and `application.yaml` (dev), and `false` when the key is absent. Tests
leave it off unless they opt in (see `TestdataSeedTest`). Turning it off on a database that was
already seeded is safe: the seed is ignored and the seeded rows stay.

What the seed does:

- Upserts `legekontor` by `orgnummer` and `pasient` by `personident`. The seed wins on name and
  demographics.
- Inserts the seeded `fastlegeliste` rows if missing and upserts the seeded memberships, so a
  patient the seed places on a list moves there. Patients created in the app keep their list.
  Removing a patient line from the seed does not remove their membership: delete the row from
  `fastlegeliste_pasient` by hand.
- Never deletes patients or clinical data. `R__testdata.sql` does not touch clinical tables
  (`konsultasjon`, `maaling`, ...).

## Hackathon data

`src/main/resources/db/testdata/R__hackathon_testdata.sql` seeds four event organisations, each
with one list owned by a placeholder HPR (900000101 to 900000104) and eight patients (32 in total,
FNR and DNR in every org, two households split across orgs). Attendees log in with SyntPop
clinicians whose HPRs are not on any list, so they work as vikar and pick an org after login. The
patients and what happens to them are listed in [hackathon-roster.md](hackathon-roster.md). The
data is kept permanently as demo data and follows the same rules as `R__testdata.sql`. To stop
seeding it, set `database.seedTestdata` to `false` (this also stops `R__testdata.sql`).

The same file seeds konsultasjoner (active, completed, planned and cancelled), ICPC-2 and ICD-10
diagnoses, journal notes and LOINC/UCUM measurements at the patient's list org, including trend
series for blood pressure, HbA1c and weight. Rows get fixed ids derived from a key with `md5`, are
inserted with `ON CONFLICT DO NOTHING`, and are never updated or deleted, so re-running the seed
duplicates nothing and keeps edits made in the app.

Rows are referenced by natural keys (orgnummer, personident, HPR) through subselects, so the seed
never hard-codes generated ids.

The mock personidents are Skatteetaten synthetic numbers (month + 80, D-number day + 40) and do not
exist in PDL. Apps that look patients up in PDL will not find them.

## Add an organisation

Add a row to the `legekontor` insert. `orgnummer` must be unique and pass the mod11 check (weights
3, 2, 7, 6, 5, 4, 3, 2 over the first eight digits). Use a number that is clearly fictional.

## Add a patient

Add a row to the `pasient` values list with a synthetic personident that matches the birth date,
`personident_type` (`FNR` or `DNR`) and gender (odd individual number for male, even for female).
`PersonidentValidator.validate` and `PersonidentValidator.isSynthetic` must accept it;
`TestdataSeedTest` checks this. Then add the patient to a list (below), otherwise nobody sees them.

## Add a doctor's list

Add a `(hpr, orgnummer)` row to the `fastlegeliste` insert, then one `(hpr, orgnummer, personident)`
row per member to the `fastlegeliste_pasient` insert. A patient can be on at most one list.

## Add a column

1. Add a versioned migration in `src/main/resources/db/migration` with the next `V` number. Never
   edit an applied versioned migration.
2. Add the column to the Exposed table in `core/db/Tables.kt` and to the Kotlin model.
3. Edit `R__testdata.sql` in place to fill the column. Do not add a second seed file.

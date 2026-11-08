# Test data

Organisations (legekontor), mock patients and fastlegelister are seeded into the database by the
repeatable Flyway migration `src/main/resources/db/testdata/R__testdata.sql`. The file always shows
the current state. Flyway re-applies it whenever its content changes. The tables it fills are
`clinic`, `practitioner`, `patient`, `fastlegeliste` and `fastlegeliste_patient`, created by the
versioned migrations `V1` to `V7`.

The seed is only applied when `database.seedTestdata` is `true`. It is `true` in
`application-local.yaml` and `application.yaml` (dev), and `false` when the key is absent. Tests
leave it off unless they opt in (see `TestdataSeedTest`). Turning it off on a database that was
already seeded is safe: the seed is ignored and the seeded rows stay.

What the seed does:

- Upserts `clinic` by `org_number` and `patient` by `personident`. The seed wins on name and
  demographics. It also creates the original mock clinic (Utstrakt Bark, no patients) and the
  practitioner GRØNN VITS (HPR 565501872), which used to come from a versioned migration.
- Inserts the seeded `fastlegeliste` rows if missing and upserts the seeded memberships, so a
  patient the seed places on a list moves there. Patients created in the app keep their list.
  Removing a patient line from the seed does not remove their membership: delete the row from
  `fastlegeliste_patient` by hand.
- Never deletes patients or user-created data. Clinical rows are only inserted, never updated or
  deleted, with one exception: the diagnosis rows of seeded consultations are normalised (see
  [Diagnoses](#diagnoses)).

The eight patients on the Fjordvik and Lyngdal lists also get clinical histories from the same
file: 73 consultations (40 at Fjordvik, 33 at Lyngdal), 79 diagnoses, 406 measurements and 68
journal notes at the patient's list office. Every patient has one active consultation (`IN_PROGRESS`, 2026-10-20)
so SMART launches work, and at least one measurement. Six patients have six or more readings of one
measurement (blood pressure, HbA1c, TSH, weight, FEV1, PHQ-9). Rows have fixed ids derived from a
key with `md5` and are inserted with `ON CONFLICT DO NOTHING`. Diagnoses, measurements and notes
are only inserted for consultations created in the same run, so a re-run never restores rows a
user deleted. The local login stub doctor (HPR 565501872) sees the Fjordvik data.

## Hackathon data

`src/main/resources/db/testdata/R__hackathon_testdata.sql` seeds four event organisations, each
with one list owned by a placeholder HPR (900000101 to 900000104) and eight patients (32 in total,
FNR and DNR in every org, two households split across orgs). Attendees log in with SyntPop
clinicians whose HPRs are not on any list, so they work as vikar and pick an org after login. The
patients and what happens to them are listed in [hackathon-roster.md](hackathon-roster.md). The
data is kept permanently as demo data and follows the same rules as `R__testdata.sql`. To stop
seeding it, set `database.seedTestdata` to `false` (this also stops `R__testdata.sql`).

The same file seeds 231 consultations (`IN_PROGRESS`, `FINISHED`, `PLANNED` and `CANCELLED`), 267 ICPC-2
diagnoses, 175 journal notes and 1034 LOINC/UCUM measurements at the patient's list org.
Every event patient has 3 to 10 consultations over two to four years, and 26 patients have six or
more readings of one measurement (blood pressure, HbA1c, weight, eGFR, TSH, PHQ-9, FEV1 and more).
Rows get fixed ids derived from a key with `md5`, are
inserted with `ON CONFLICT DO NOTHING`, and are never updated or deleted, so re-running the seed
duplicates nothing and keeps edits made in the app. Consultation dates are generated from fixed day
offsets and measurement values from a linear trend plus a deterministic wobble, so the output is the
same on every run.

Rows are referenced by natural keys (org number, personident, HPR) through subselects, so the seed
never hard-codes generated ids.

### Diagnoses

A consultation's diagnoses are ordered by `consultation_diagnosis_code.rank`: rank 1 is the
hoveddiagnose, higher ranks are bidiagnoser. Both seed files give every diagnosed consultation one
ICPC-2 hoveddiagnose with an explicit rank, plus a bidiagnose only where it is clinically distinct
(for example T90 with K86 for RL in `R__testdata.sql`, K86 with U99 for SM in the hackathon
cohort). The seeds contain no ICD-10 diagnoses, but the API still accepts them.

Earlier seed versions stored most diagnoses twice, as an ICPC-2 code and an ICD-10 twin. On every
run each seed script removes those twins from the seeded consultations (found by their
deterministic ids) and renumbers the remaining ranks. User-created consultations and ICD-10
diagnoses that are not seed twins are left alone. The cleanup is idempotent, and rows a user
deleted from a seeded consultation stay deleted.

Rows that existed before V8 got their rank from a deterministic backfill: ICPC-2 first, then by
system and code. The historical hoveddiagnose cannot be recovered, so the backfilled order is not
clinical truth. Only the diagnoses of seeded consultations are corrected, by the seed.

The mock personidents are Skatteetaten synthetic numbers (month + 80, D-number day + 40) and do not
exist in PDL. Apps that look patients up in PDL will not find them.

## Add an organisation

Add a row to the `clinic` insert. `org_number` must be unique and pass the mod11 check (weights
3, 2, 7, 6, 5, 4, 3, 2 over the first eight digits). Use a number that is clearly fictional.

## Add a patient

Add a row to the `patient` values list with a synthetic personident that matches the birth date,
`personident_type` (`FNR` or `DNR`) and gender (odd individual number for male, even for female).
`PersonidentValidator.validate` and `PersonidentValidator.isSynthetic` must accept it;
`TestdataSeedTest` checks this. Then add the patient to a list (below), otherwise nobody sees them.

## Add a doctor's list

Add a `(hpr, org_number)` row to the `fastlegeliste` insert, then one `(hpr, org_number, personident)`
row per member to the `fastlegeliste_patient` insert. A patient can be on at most one list.

## Add a column

1. Add a versioned migration in `src/main/resources/db/migration` with the next `V` number. Never
   edit an applied versioned migration.
2. Add the column to the Exposed table in `core/db/Tables.kt` and to the Kotlin model.
3. Edit `R__testdata.sql` in place to fill the column. Do not add a second seed file.

# Test data

Organisations (legekontor), mock patients and fastlegelister are seeded into the database by the
repeatable Flyway migration `src/main/resources/db/testdata/R__testdata.sql`. The file always shows
the current state. Flyway re-applies it whenever its content changes.

The seed is only applied when `database.seedTestdata` is `true`. It is `true` in
`application-local.yaml` and `application.yaml` (dev), and `false` when the key is absent. Tests
leave it off unless they opt in (see `TestdataSeedTest`).

What the seed does:

- Upserts `legekontor` by `orgnummer` and `pasient` by `personident`. The seed wins on name and
  demographics.
- Deletes and re-inserts `fastlegeliste` and `fastlegeliste_pasient`. Removing a patient from a
  list in the seed removes only the membership.
- Never deletes patients and never touches clinical tables (`konsultasjon`, `maaling`, ...).

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

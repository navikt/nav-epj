# Hackathon roster

Seeded by `src/main/resources/db/testdata/R__hackathon_testdata.sql`. All personidents are
Skatteetaten synthetic numbers and do not exist in PDL. Attendees work as vikar and choose one of
these organisations after login. Everyone at an organisation sees all of its patients and the
clinical data recorded there. Seeded clinical data is attributed to the list HPR in the heading.
Dates are 2026 unless stated.

Households split across organisations: BERGE (Skogtun and Bjerkely) and STRAND (Havnegata and
Solbakken).

## Skogtun Legekontor, orgnr 312000016, list HPR 900000101

| Name | Personident | Type | Scenario |
| --- | --- | --- | --- |
| BJARNE BERGE | 20856210112 | FNR | Hypertension (ICPC-2 K86, ICD-10 I10). Blood pressure and weight trend over three completed konsultasjoner, one active konsultasjon |
| INGRID BERGE | 03916410017 | FNR | Type 2 diabetes (T90, E11.9). HbA1c trend over four konsultasjoner, weight at two |
| TORE LUND | 28815510331 | FNR | Asthma (R96, J45.9). One completed and one cancelled konsultasjon |
| MARIT ENGEN | 55878910264 | DNR | No clinical history |
| SIGRUN MOEN | 09894810030 | FNR | No clinical history |
| OLAV HAUG | 70837710381 | DNR | No clinical history |
| NORA ASPLUND | 12901550261 | FNR | No clinical history (child) |
| KARL DAHL | 22927110176 | FNR | No clinical history |

## Havnegata Legesenter, orgnr 312000024, list HPR 900000102

| Name | Personident | Type | Scenario |
| --- | --- | --- | --- |
| ELSE STRAND | 04845310039 | FNR | Type 2 diabetes (T90, E11.9). Two HbA1c values, one active konsultasjon |
| ANDERS VIK | 18888410364 | FNR | No clinical history |
| HILDE NES | 65866910027 | DNR | Depression (P76, F32.1). Two completed konsultasjoner, one with journal note |
| JON STRAND | 14828010132 | FNR | Back pain (L84, M54.5). One completed konsultasjon with journal note |
| LIV ROSS | 01909410033 | FNR | No clinical history |
| SVEN BAKKE | 47876010166 | DNR | Hypertension (K86, I10). Blood pressure and heart rate trend over three konsultasjoner |
| AMINA HASSAN | 45859810059 | DNR | No clinical history |
| PER TVEIT | 12924510194 | FNR | No clinical history |

## Bjerkely Legepraksis, orgnr 312000032, list HPR 900000103

| Name | Personident | Type | Scenario |
| --- | --- | --- | --- |
| ASTRID BERGE | 17819010269 | FNR | Upper respiratory infection (R74, J06.9). One completed konsultasjon with journal note |
| GUNNAR FOSS | 23895710156 | FNR | Hypertension and type 2 diabetes (K86, I10, T90, E11.9). Blood pressure, HbA1c and weight trend over four konsultasjoner, one planned |
| RANDI SOLHEIM | 08837310041 | FNR | No clinical history |
| EMIL KROG | 30860850180 | FNR | Health check (A98, Z00.0). Height and weight over three years (2024 to 2026) |
| TOMAS NOVAK | 59918610175 | DNR | Cough (R05). One active konsultasjon |
| KARIN LIE | 27846610079 | FNR | No clinical history |
| YNGVE ROSTED | 42885110163 | DNR | No clinical history |
| LINN HOLM | 66820350082 | DNR | No clinical history |

## Solbakken Familielegekontor, orgnr 312000040, list HPR 900000104

| Name | Personident | Type | Scenario |
| --- | --- | --- | --- |
| MARIANNE STRAND | 05928210261 | FNR | Obesity (T82, E66.9). Weight and BMI trend over four konsultasjoner |
| HELGE BRATT | 16834910156 | FNR | Hypertension (K86, I10). Blood pressure trend over four konsultasjoner, the last one active |
| TUVA MYRE | 14899610025 | FNR | No clinical history |
| ROLF SAND | 69906310147 | DNR | Type 2 diabetes (T90, E11.9). HbA1c trend over three konsultasjoner |
| EVA LUNDE | 31857810085 | FNR | No clinical history |
| SAMIR ALI | 49819110351 | DNR | No clinical history |
| BERIT ODEGAARD | 21865810048 | FNR | No clinical history |
| LARS KVAM | 13841050147 | FNR | Asthma (R96, J45.9). One completed konsultasjon |

## Measurement codes

| Code (LOINC) | Name | Unit (UCUM) |
| --- | --- | --- |
| 8480-6 | Systolic blood pressure | mm[Hg] |
| 8462-4 | Diastolic blood pressure | mm[Hg] |
| 4548-4 | Hemoglobin A1c/Hemoglobin.total in Blood | % |
| 29463-7 | Body weight | kg |
| 8302-2 | Body height | cm |
| 39156-5 | Body mass index (BMI) [Ratio] | kg/m2 |
| 8867-4 | Heart rate | /min |

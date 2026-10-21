# Hackathon roster

Seeded by `src/main/resources/db/testdata/R__hackathon_testdata.sql`. All personidents are
Skatteetaten synthetic numbers and do not exist in PDL. Attendees work as vikar and choose one of
these organisations after login. Everyone at an organisation sees all of its patients and the
clinical data recorded there. Seeded clinical data is attributed to the list HPR in the heading.
Every patient has 3 to 10 konsultasjoner over at least two years (2023 to 2026, none dated after
2026-10-20 except planned ones), a diagnosis on every completed konsultasjon and a journal note on
most. 231 konsultasjoner, 534 diagnoses, 175 journal notes and 1034 measurements in total. Series
below are listed oldest to newest. Konsultasjoner are active, planned or cancelled only where
stated.

Households split across organisations: BERGE (Skogtun and Bjerkely) and STRAND (Havnegata and
Solbakken).

## Skogtun Legekontor, orgnr 312000016, list HPR 900000101

| Name | Personident | Type | Scenario |
| --- | --- | --- | --- |
| BJARNE BERGE | 20856210112 | FNR | Hypertension (ICPC-2 K86, ICD-10 I10). 8 konsultasjoner 2024 to 2026, the last one active. Systolic pressure 165 to 138 mm Hg and weight 97.6 to 91.8 kg over 7 readings |
| INGRID BERGE | 03916410017 | FNR | Type 2 diabetes (T90, E11.9). 8 konsultasjoner 2024 to 2026. HbA1c 7.5, peaking at 8.6 in 2025, then 7.1 % over 8 readings; weight 85.6 to 79.6 kg |
| TORE LUND | 28815510331 | FNR | Asthma (R96, J45.9). 8 konsultasjoner 2023 to 2026, one cancelled. FEV1 3.07 to 2.67 L over 6 readings |
| MARIT ENGEN | 55878910264 | DNR | Hypothyroidism (T86, E03.9). 7 konsultasjoner 2023 to 2026. TSH 10.0 to 3.0 mIU/L on levothyroxine over 7 readings |
| SIGRUN MOEN | 09894810030 | FNR | Hypertension and chronic kidney disease (K86, I10, U99, N18.3). 9 konsultasjoner 2023 to 2026, one cancelled. eGFR falls 57 to 47 mL/min/1.73 m2 and systolic pressure rises 135 to 157 mm Hg over 8 readings |
| OLAV HAUG | 70837710381 | DNR | Anxiety (P74, F41.1). 6 konsultasjoner 2023 to 2026. GAD-7 14 to 7 over 6 readings |
| NORA ASPLUND | 12901550261 | FNR | Child health checks (A98, Z00.1). 6 konsultasjoner 2023 to 2026. Height 125.5 to 142.5 cm, weight 25.0 to 35.5 kg and BMI over 6 readings |
| KARL DAHL | 22927110176 | FNR | Dyslipidaemia (T93, E78.5). 6 konsultasjoner 2023 to 2026. Total cholesterol 7.2 to 5.0 mmol/L and LDL falling over 6 readings, blood pressure normal |

## Havnegata Legesenter, orgnr 312000024, list HPR 900000102

| Name | Personident | Type | Scenario |
| --- | --- | --- | --- |
| ELSE STRAND | 04845310039 | FNR | Type 2 diabetes (T90, E11.9). 8 konsultasjoner 2024 to 2026, the last one active. HbA1c 7.3 rising to 8.9, then 8.1 % over 7 readings; eGFR 75 to 67 |
| ANDERS VIK | 18888410364 | FNR | Acute infections (R74 with J06.9, R75 with J01.9, R72 with J02.0) and an ankle sprain (L77, S93.4). 4 konsultasjoner 2023 to 2026, CRP and temperature at the infections |
| HILDE NES | 65866910027 | DNR | Depression (P76, F32.1). 8 konsultasjoner 2024 to 2026. PHQ-9 21 to 11 over 6 readings in 2024 and 2025, none in the 2026 konsultasjoner |
| JON STRAND | 14828010132 | FNR | Back pain (L84, M54.5). 6 konsultasjoner 2024 to 2026. Pain score 6 to 4 over 5 readings |
| LIV ROSS | 01909410033 | FNR | Pregnancy (W78, Z34.8), 7 check-ups from February to August 2026. Earlier contraception (W11, Z30.0), cystitis (U71, N30.0) and a pre-pregnancy health check (A98, Z00.0). 10 konsultasjoner 2024 to 2026. Weight 59.8 to 70.6 kg, hemoglobin 13.2 to 11.4 g/dL |
| SVEN BAKKE | 47876010166 | DNR | Elevated blood pressure (K85, R03.0), then hypertension (K86, I10). 7 konsultasjoner 2024 to 2026. Systolic pressure rises 149 to 168 mm Hg untreated, then falls to 144; heart rate recorded |
| AMINA HASSAN | 45859810059 | DNR | Iron deficiency anaemia (B80, D50.9). 6 konsultasjoner 2024 to 2026. Hemoglobin 10.4 to 12.9 g/dL and ferritin 9 to 44 µg/L over 6 readings |
| PER TVEIT | 12924510194 | FNR | Type 2 diabetes, hypertension and chronic kidney disease (T90, E11.9, K86, I10, U99, N18.3). 8 konsultasjoner 2023 to 2026. eGFR falls 63 to 47 with rising creatinine, HbA1c 7.9 to 8.4 %, systolic pressure 141 to 153 mm Hg |

## Bjerkely Legepraksis, orgnr 312000032, list HPR 900000103

| Name | Personident | Type | Scenario |
| --- | --- | --- | --- |
| ASTRID BERGE | 17819010269 | FNR | Cystitis twice (U71, N30.0), sinusitis (R75, J01.9), strep throat (R72, J02.0) and an upper respiratory infection (R74, J06.9). 5 konsultasjoner 2024 to 2026, CRP at two |
| GUNNAR FOSS | 23895710156 | FNR | Hypertension and type 2 diabetes (K86, I10, T90, E11.9). 10 konsultasjoner 2024 to 2026, one planned. Systolic pressure 169 to 136 mm Hg, HbA1c 8.7 to 6.9 % and weight 106.6 to 97.5 kg over 9 readings |
| RANDI SOLHEIM | 08837310041 | FNR | Hypothyroidism with weight gain (T86, E03.9, T07, R63.5). 8 konsultasjoner 2023 to 2025. TSH rises 4.5 to 9.5 mIU/L and weight 74 to 89 kg over 8 readings |
| EMIL KROG | 30860850180 | FNR | Health checks (A98, Z00.0), ankle sprain (L77, S93.4) and acne (S96, L70.0). 6 konsultasjoner 2023 to 2026. Height 172 to 181 cm and weight 58.4 to 68.2 kg over 4 health checks |
| TOMAS NOVAK | 59918610175 | DNR | Smoking cessation twice (P17, F17.2), bronchitis twice (R78, J20.9) and cough (R05). 5 konsultasjoner 2023 to 2026, the last one active |
| KARIN LIE | 27846610079 | FNR | Menopausal complaints (X11, N95.1), then elevated blood pressure (K85, R03.0). 7 konsultasjoner 2023 to 2026. Systolic pressure rises 126 to 152 mm Hg over 7 readings |
| YNGVE ROSTED | 42885110163 | DNR | COPD (R95, J44.9). 9 konsultasjoner 2023 to 2026, one cancelled. FEV1 falls 2.41 to 1.80 L and SpO2 94 to 92 % over 8 readings |
| LINN HOLM | 66820350082 | DNR | Depression (P76, F32.0). 6 konsultasjoner 2024 to 2026. PHQ-9 rises 7 to 17 over 6 readings |

## Solbakken Familielegekontor, orgnr 312000040, list HPR 900000104

| Name | Personident | Type | Scenario |
| --- | --- | --- | --- |
| MARIANNE STRAND | 05928210261 | FNR | Obesity (T82, E66.9). 8 konsultasjoner 2024 to 2026. Weight rises 82.9 to 88.4 kg, then falls to 83.9 kg; BMI over 8 readings |
| HELGE BRATT | 16834910156 | FNR | Hypertension (K86, I10). 8 konsultasjoner 2024 to 2026, the last one active. Systolic pressure rises 149 to 164 mm Hg, then falls to 142 over 8 readings |
| TUVA MYRE | 14899610025 | FNR | Pregnancy (W78, Z34.8), 6 check-ups from May to October 2026. Earlier health check (A98, Z00.0), migraine (N89, G43.9) and cystitis (U71, N30.0). 9 konsultasjoner 2024 to 2026. Hemoglobin 13.0 to 11.5 g/dL, weight 57.4 to 62.5 kg |
| ROLF SAND | 69906310147 | DNR | Type 2 diabetes (T90, E11.9). 7 konsultasjoner 2023 to 2026. HbA1c falls 10.0 to 7.5 % over 7 readings |
| EVA LUNDE | 31857810085 | FNR | Type 2 diabetes (T90, E11.9). 8 konsultasjoner 2023 to 2026, one cancelled. HbA1c rises 7.1 to 8.8 % and weight 84 to 89.8 kg over 7 readings |
| SAMIR ALI | 49819110351 | DNR | Back pain (L84, M54.5), gastroenteritis (D73, A09.9) and a cold (R74, J06.9). 6 konsultasjoner 2023 to 2025. Pain score 6 to 3 over 4 readings, blood pressure normal |
| BERIT ODEGAARD | 21865810048 | FNR | Hypothyroidism (T86, E03.9) and knee osteoarthritis (L90, M17.9). 7 konsultasjoner 2023 to 2026. TSH stable between 1.9 and 3.3 mIU/L over 6 readings |
| LARS KVAM | 13841050147 | FNR | Asthma (R96, J45.9). 7 konsultasjoner 2023 to 2026. FEV1 2.26 to 3.26 L, height 150.6 to 167.6 cm, weight 38.6 to 57.6 kg and BMI over 6 readings |

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
| 62238-1 | eGFR, creatinine-based (CKD-EPI) | mL/min/{1.73_m2} |
| 14682-9 | Creatinine in serum or plasma | umol/L |
| 14647-2 | Cholesterol in serum or plasma | mmol/L |
| 22748-8 | LDL cholesterol in serum or plasma | mmol/L |
| 3016-3 | Thyrotropin (TSH) | m[IU]/L |
| 718-7 | Hemoglobin in blood | g/dL |
| 2276-4 | Ferritin in serum or plasma | ug/L |
| 44261-6 | PHQ-9 total score | {score} |
| 69737-5 | GAD-7 total score | {score} |
| 72514-3 | Pain severity, 0 to 10 | {score} |
| 20150-9 | FEV1 | L |
| 59408-5 | Oxygen saturation by pulse oximetry | % |
| 1988-5 | C-reactive protein | mg/L |
| 8310-5 | Body temperature | Cel |

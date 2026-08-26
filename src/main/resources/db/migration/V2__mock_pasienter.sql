
INSERT INTO legekontor (id, navn, tlf, orgnummer)
VALUES ('aed5c75c-3b12-4652-83d7-223bdd69062d', 'Legekontor Legesenter', 'tulletlf', '123');


INSERT INTO pasient (id, legekontor_id, fornavn, etternavn, fnr)
VALUES ('376f0dba-8226-41a9-ab3a-3f31fa1dab12', 'aed5c75c-3b12-4652-83d7-223bdd69062d', 'FESTLIG', 'KAKTUS', '56876301577');

INSERT INTO pasient_helsepersonell (pasient_id, hpr)
VALUES ('376f0dba-8226-41a9-ab3a-3f31fa1dab12', '111222333');

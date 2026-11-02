INSERT INTO clinic (id, name, phone, org_number)
VALUES ('aed5c75c-3b12-4652-83d7-223bdd69062d', 'Utstrakt Bark', '12345678', '864425208')
ON CONFLICT (org_number) DO NOTHING;

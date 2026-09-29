import { Alert, Button, Heading, Select, TextField } from "@navikt/ds-react"
import { OpprettPasientSchema, type OpprettPasientRequest, type Pasient } from "@utils/mapping/epj";
import { useState } from "react";

async function opprettPasient(request: OpprettPasientRequest): Promise<Pasient> {
    const res = await fetch("/api/patient", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
    });
    if (!res.ok) {
        throw new Error("Klarte ikke å opprette pasient");
    }
    return res.json();
}


export const OpprettPasient = ({lastPasienter}: { lastPasienter: () => void}) => {
    const [fornavn, setFornavn] = useState("");
    const [etternavn, setEtternavn] = useState("");
    const [personident, setPersonident] = useState("");
    const [personidentType, setPersonidentType] = useState("");
    const [birthDate, setBirthDate] = useState("");
    const [gender, setGender] = useState("");
    const [feilmelding, setFeilmelding] = useState<string | null>(null);
    const [lagrer, setLagrer] = useState(false);

    async function handleOpprettPasient(e: React.FormEvent) {
        e.preventDefault();
        setFeilmelding(null);

        const parsed = OpprettPasientSchema.safeParse({
            fornavn,
            etternavn,
            personident,
            personidentType,
            birthDate,
            gender,
        });
        if (!parsed.success) {
            setFeilmelding(parsed.error.issues[0].message);
            return;
        }

        setLagrer(true);
        try {
            await opprettPasient(parsed.data);
            setFornavn("");
            setEtternavn("");
            setPersonident("");
            setPersonidentType("");
            setBirthDate("");
            setGender("");
            lastPasienter();
        } catch {
            setFeilmelding("Kunne ikke opprette pasient. Sjekk at fødselsnummeret eller D-nummeret ikke allerede finnes.");
        } finally {
            setLagrer(false);
        }
    }

    return (
        <div className="flex flex-col">
            <Heading level="2" size="medium" spacing>
                Opprett ny pasient
            </Heading>
            <form onSubmit={handleOpprettPasient} className="flex flex-col gap-4 items-start">
                {feilmelding && <Alert variant="error">{feilmelding}</Alert>}
                <div className="flex flex-row gap-4">
                    <TextField
                        label="Fornavn"
                        value={fornavn}
                        onChange={(e) => setFornavn(e.target.value)}
                    />
                    <TextField
                        label="Etternavn"
                        value={etternavn}
                        onChange={(e) => setEtternavn(e.target.value)}
                    />
                </div>
                <Select
                    label="Identitetstype"
                    value={personidentType}
                    onChange={(e) => setPersonidentType(e.target.value)}
                >
                    <option value="">Velg identitetstype</option>
                    <option value="FNR">Fødselsnummer</option>
                    <option value="DNR">D-nummer</option>
                </Select>
                <TextField
                    label="Fødselsnummer eller D-nummer"
                    value={personident}
                    onChange={(e) => setPersonident(e.target.value)}
                />
                <TextField
                    label="Fødselsdato"
                    type="date"
                    value={birthDate}
                    onChange={(e) => setBirthDate(e.target.value)}
                />
                <Select
                    label="Administrativt kjønn"
                    value={gender}
                    onChange={(e) => setGender(e.target.value)}
                >
                    <option value="">Velg administrativt kjønn</option>
                    <option value="FEMALE">Kvinne</option>
                    <option value="MALE">Mann</option>
                    <option value="OTHER">Annet</option>
                    <option value="UNKNOWN">Ukjent</option>
                </Select>
                <Button type="submit" loading={lagrer}>
                    Opprett pasient
                </Button>
            </form>
        </div>)
}
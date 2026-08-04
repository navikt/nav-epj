import { Alert, Button, Heading, TextField } from "@navikt/ds-react"
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
    const [navn, setNavn] = useState("");
    const [fnr, setFnr] = useState("");
    const [feilmelding, setFeilmelding] = useState<string | null>(null);
    const [lagrer, setLagrer] = useState(false);

    async function handleOpprettPasient(e: React.FormEvent) {
        e.preventDefault();
        setFeilmelding(null);

        const parsed = OpprettPasientSchema.safeParse({ navn, fnr });
        if (!parsed.success) {
            setFeilmelding(parsed.error.issues[0].message);
            return;
        }

        setLagrer(true);
        try {
            await opprettPasient(parsed.data);
            setNavn("");
            setFnr("");
            lastPasienter();
        } catch {
            setFeilmelding("Kunne ikke opprette pasient. Sjekk at fødselsnummeret ikke allerede finnes.");
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
                <TextField
                    label="Navn"
                    value={navn}
                    onChange={(e) => setNavn(e.target.value)}
                />
                <TextField
                    label="Fødselsnummer"
                    value={fnr}
                    onChange={(e) => setFnr(e.target.value)}
                />
                <Button type="submit" loading={lagrer}>
                    Opprett pasient
                </Button>
            </form>
        </div>)
}
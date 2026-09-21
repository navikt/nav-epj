import { Button, Heading, Link, Table, Textarea, UNSAFE_Combobox } from '@navikt/ds-react'
import {createFileRoute, useNavigate} from '@tanstack/react-router'
import { useEffect, useState, type MouseEvent } from 'react'
import { epjDiagnoser } from '@data/diagnoses'
import {getSykInnUrl} from "@utils/env.ts";
import { fetchKonsultasjon } from '@utils/fetch';
import { KonsultasjonSchema } from '@utils/mapping/epj';

export const Route = createFileRoute(
    '/patients/$patientId/konsultasjon/$konsultasjonId/',
)({
      loader: async ({ params }) => {
        const konsultasjon = await fetchKonsultasjon(params.konsultasjonId);
        return { konsultasjon };
      },
    component: RouteComponent,
})

type PostKonsultasjonBody = {
    konsultasjonId: string
    diagnoser: { kode: string, system: string, beskrivelse: string }[];
    journalNotat: string | null;
    ferdigstill: boolean;
}

function RouteComponent() {
    const navigate = useNavigate()
    const { patientId, konsultasjonId } = Route.useParams();
    const data = Route.useLoaderData();
    const konsultasjon = KonsultasjonSchema.safeParse(
        data.konsultasjon,
      );
    const [diagnoser, setDiagnoser] = useState<{ kode: string, system: string, beskrivelse: string }[]>([])
    const [journalnotat, setJournalnotat] = useState<string>('')
    const [saveError, setSaveError] = useState<string | null>(null)

    const [diagnoseOptions, setDiagnoseOptions] = useState<{ label: string, system: string, value: string }[]>([])

    useEffect(() => {
        const mappedDiagnoser = epjDiagnoser.map((diagnose) => {
            return {
                label: diagnose.beskrivelse,
                value: diagnose.kode,
                system: diagnose.diagnosesystem
            }
        })
        setDiagnoseOptions(mappedDiagnoser)
    }, [])

    function handleToggleSelect(option: string, isSelected: boolean) {
        if (isSelected) {
            const newOption = diagnoseOptions.find((diagnose) => diagnose.value === option)
            if (!newOption) {
                return
            }
            setDiagnoser([...diagnoser, { kode: newOption.value, system: newOption.system, beskrivelse: newOption.label }])
        } else {
            const newDiagnoser = diagnoser.filter((diagnose) => diagnose.kode != option)
            setDiagnoser(newDiagnoser)
        }
    }

    async function handleSubmit(e: MouseEvent, ferdigstill: boolean) {
        e.preventDefault()
        setSaveError(null)
        const requestBody: PostKonsultasjonBody = {
            diagnoser: diagnoser,
            journalNotat: journalnotat,
            ferdigstill: ferdigstill,
            konsultasjonId: konsultasjonId
        }
        const res = await fetch(`/api/patients/${patientId}/konsultasjoner`, { method: 'PATCH', body: JSON.stringify(requestBody), headers: { "Content-Type": "application/json" } }).then((res) => res.ok)
        if (!res) {
            setSaveError('Kunne ikke lagre konsultasjon')
        }
        if (ferdigstill && res) {
            navigate({ to: `/patients/$patientId`, params: { patientId } })
        }
    }

    return (
        <div className="flex flex-col gap-4 items-start">
            {saveError && <div className="text-red-600">{saveError}</div>}
            {konsultasjon.data?.status === 'PÅGÅENDE' && (
                <div>
                    <form className="flex flex-col gap-4 items-start max-w-sm">
                        <UNSAFE_Combobox
                            label="Hvilke diagnoser har pasienten"
                            options={diagnoseOptions}
                            isMultiSelect
                            onToggleSelected={(option, isSelected) => handleToggleSelect(option, isSelected)}

                        />
                        <Textarea label="Journalnotat" onChange={(e) => setJournalnotat(e.target.value)} value={journalnotat} />
                        <div className="flex flex-row gap-4">
                            <Button onClick={(e) => handleSubmit(e, false)}>Lagre konsultasjon</Button>
                            <Button variant={'secondary'} onClick={(e) => handleSubmit(e, true)}>Fullfør konsultasjon</Button>
                        </div>
                    </form>

                    <div className="flex flex-row gap-4">
                        <Button onClick={() => { navigate({ to: `/patients/$patientId/konsultasjon/$konsultasjonId/sykmelding`, params: { patientId, konsultasjonId } }) }}>Start sykmelding (not implemented)</Button>
                        <Link href={`/fhir/launch?url=${getSykInnUrl()}`} target="_blank" > åpne sykmelding i ny fane </Link>
                        <Button onClick={() => { navigate({ to: `/patients/$patientId/konsultasjon/$konsultasjonId/validator`, params: { patientId, konsultasjonId } }) }}>Start valideringsapp</Button>
                    </div>
                </div>
            )}
            {konsultasjon.data?.status === 'FULLFØRT' && (
                <div>
                    <p>Starttidspunkt: {konsultasjon.data.startetTidspunkt}</p>
                    <p>Sluttidspunkt: {konsultasjon.data.avsluttetTidspunkt}</p>
                    <p>Diagnoser: {konsultasjon.data.diagnoser.map(d => d.beskrivelse).join(', ')}</p>
                </div>
            )}
            <Heading size="medium" level="2">Journalnotater</Heading>
            {konsultasjon.success && (
                <Table>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell>Notat</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>
                    <Table.Body>
                        {konsultasjon.data.journalnotat.map((journalnotat, index) => (
                            <Table.Row key={index}>
                                <Table.DataCell>{journalnotat.journalnotat}</Table.DataCell>
                            </Table.Row>
                        ))}
                   
                    </Table.Body>
                </Table>
            )}
        </div>)
}

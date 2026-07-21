import { Button, Textarea, UNSAFE_Combobox } from '@navikt/ds-react'
import { createFileRoute, useNavigate, useRouter } from '@tanstack/react-router'
import { useEffect, useState, type MouseEvent } from 'react'
import { epjDiagnoser } from '@data/diagnoses'

export const Route = createFileRoute(
  '/patients/$patientId/konsultasjon/$konsultasjonId/',
)({
  component: RouteComponent,
})

type PostKonsultasjonBody = {
    konsultasjonId: string
    diagnoser: {kode: string, system: string, beskrivelse: string}[];
    journalNotat: string | null;
    ferdigstill: boolean;
}

function RouteComponent() {
    const navigate = useNavigate()
    const { patientId, konsultasjonId } = Route.useParams();
    const [diagnoser, setDiagnoser] = useState<{kode: string, system: string, beskrivelse: string}[]>([])
    const [journalnotat, setJournalnotat] = useState<string>('')

    const [diagnoseOptions, setDiagnoseOptions] = useState<{label: string, system: string, value: string}[]>([])

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
            setDiagnoser([...diagnoser, {kode: newOption.value, system: newOption.system, beskrivelse: newOption.label}])
        } else {
            const newDiagnoser = diagnoser.filter((diagnose) => diagnose.kode != option)
            setDiagnoser(newDiagnoser)
        }
    }

    async function handleSubmit(e: MouseEvent, ferdigstill: boolean) {
        e.preventDefault()
        const requestBody: PostKonsultasjonBody = {
           diagnoser: diagnoser,
           journalNotat: journalnotat,
           ferdigstill: ferdigstill,
           konsultasjonId: konsultasjonId
        }
        const res = await fetch(`/api/patient/${patientId}/konsultasjon`, { method: 'PATCH', body: JSON.stringify(requestBody), headers: { "Content-Type": "application/json" }}).then((res) => res.ok)
        if (!res) {
            console.error('Kunne ikke lagre')
        }
    }

  return <div>
    <form>
        <UNSAFE_Combobox
  label="Hvilke diagnoser har pasienten"
  options={diagnoseOptions}
  isMultiSelect
  onToggleSelected={(option, isSelected) => handleToggleSelect(option, isSelected)}
 
/>
    <Textarea label="Journalnotat" onChange={(e) => setJournalnotat(e.target.value)} value={journalnotat} />
    <Button onClick={(e) => handleSubmit(e, false)}>Lagre konsultasjon</Button><Button variant={'secondary'} onClick={(e) => handleSubmit(e, true)}>Fullfør konsultasjon</Button>
    </form>

    <div>
        <Button onClick={() => {navigate({ to: `/patients/$patientId/konsultasjon/$konsultasjonId/sykmelding`, params: {patientId, konsultasjonId}})}}>Start sykmelding (not implemented)</Button>
        <Button onClick={() => {navigate({ to: `/patients/$patientId/konsultasjon/$konsultasjonId/validator`, params: {patientId, konsultasjonId}})}}>Start valideringsapp</Button>
    </div>
  </div>
}

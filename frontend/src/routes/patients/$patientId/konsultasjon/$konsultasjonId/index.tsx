import { Button, Textarea, UNSAFE_Combobox } from '@navikt/ds-react'
import { createFileRoute, useNavigate, useRouter } from '@tanstack/react-router'
import { useState, type MouseEvent } from 'react'

export const Route = createFileRoute(
  '/patients/$patientId/konsultasjon/$konsultasjonId/',
)({
  component: RouteComponent,
})

type PostKonsultasjonBody = {
    konsultasjonId: string
    diagnoser: {kode: string, system: string}[];
    journalNotat: string | null;
    ferdigstill: boolean;
}

const diagnoseOptions: {label: string, value: string, system: 'ICD10' | 'ICPC2'}[] = [{label: 'Testdiagnose 1', value: 'P01', system: 'ICD10'}, {label: 'Testdiagnose 2', value: 'P02', system: 'ICPC2'}]

function RouteComponent() {
    const navigate = useNavigate()
    const { patientId, konsultasjonId } = Route.useParams();
    const [diagnoser, setDiagnoser] = useState<{kode: string, system: string}[]>([])
    const [journalnotat, setJournalnotat] = useState<string>('')

    function handleToggleSelect(option: string, isSelected: boolean) {
        if (isSelected) {
            const newOption = diagnoseOptions.find((diagnose) => diagnose.value === option)
            if (!newOption) {
                return
            }
            setDiagnoser([...diagnoser, {kode: newOption.value, system: newOption.system}])
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

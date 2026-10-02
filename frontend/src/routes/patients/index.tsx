import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { NewPatientDialog } from "../../xp/NewPatientDialog";
import { PatientsPage } from "../../xp/PatientsPage";
import { canCreatePatients } from "../../xp/canCreatePatients";
import { useOpenJournal } from "../../xp/useOpenJournal";

export const Route = createFileRoute("/patients/")({
  component: RouteComponent,
});

function RouteComponent() {
  const openJournal = useOpenJournal();
  const [creating, setCreating] = useState(false);
  return (
    <>
      <PatientsPage
        canCreate={canCreatePatients()}
        onNewPatient={() => setCreating(true)}
        onOpenJournal={openJournal}
      />
      {creating && <NewPatientDialog onClose={() => setCreating(false)} />}
    </>
  );
}

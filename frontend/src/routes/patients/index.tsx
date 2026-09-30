import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { NewPatientDialog } from "../../xp/NewPatientDialog";
import { PatientsPage } from "../../xp/PatientsPage";
import { canCreatePatients } from "../../xp/canCreatePatients";

export const Route = createFileRoute("/patients/")({
  component: RouteComponent,
});

function RouteComponent() {
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);
  return (
    <>
      <PatientsPage
        canCreate={canCreatePatients()}
        onNewPatient={() => setCreating(true)}
        onOpenJournal={(pasient) =>
          void navigate({
            to: "/patients/$patientId",
            params: { patientId: pasient.id },
          })
        }
      />
      {creating && <NewPatientDialog onClose={() => setCreating(false)} />}
    </>
  );
}

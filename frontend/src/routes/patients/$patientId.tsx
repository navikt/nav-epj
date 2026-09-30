import { createFileRoute, Outlet } from "@tanstack/react-router";
import { JournalView } from "../../xp/JournalView";

export const Route = createFileRoute("/patients/$patientId")({
  component: PatientLayout,
});

function PatientLayout() {
  return (
    <>
      <JournalView />
      <Outlet />
    </>
  );
}

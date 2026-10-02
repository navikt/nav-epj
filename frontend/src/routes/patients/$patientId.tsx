import { createFileRoute, Outlet } from "@tanstack/react-router";
import { z } from "zod";
import { JournalView } from "../../xp/JournalView";
import { JOURNAL_SUB_TABS } from "../../xp/journalStore";

const searchSchema = z.object({
  tab: z.enum(JOURNAL_SUB_TABS).optional().catch(undefined),
});

export const Route = createFileRoute("/patients/$patientId")({
  validateSearch: (search) => searchSchema.parse(search),
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

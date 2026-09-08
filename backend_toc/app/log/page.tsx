import { Suspense } from "react";
import EventLogPage from "@/components/event-log-page";

export const dynamic = "force-dynamic";

export default function LogRoutePage() {
  return (
    <Suspense fallback={<div style={{ padding: 16, color: "#f5f5f5" }}>Caricamento LOG…</div>}>
      <EventLogPage />
    </Suspense>
  );
}

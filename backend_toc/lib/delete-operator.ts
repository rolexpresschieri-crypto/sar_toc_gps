import type { SupabaseClient } from "@supabase/supabase-js";

function isMissingTable(message: string): boolean {
  const m = message.toLowerCase();
  return m.includes("schema cache") || m.includes("does not exist") || m.includes("could not find the table");
}

async function deleteBySquadId(
  supabase: SupabaseClient,
  table: string,
  squadId: string,
): Promise<void> {
  const { error } = await supabase.from(table).delete().eq("squad_id", squadId);
  if (error && !isMissingTable(error.message)) {
    throw error;
  }
}

/** Cancella operatore + storico sul DB TOC SAR (non tocca gestSQUADRE). */
export async function deleteOperatorForOrganization(
  supabase: SupabaseClient,
  squadId: string,
  organizationId: string,
): Promise<void> {
  await deleteBySquadId(supabase, "squad_track_logs", squadId);
  await deleteBySquadId(supabase, "squad_alarms", squadId);
  await deleteBySquadId(supabase, "squad_sessions", squadId);
  await deleteBySquadId(supabase, "toc_push_logs", squadId);
  await deleteBySquadId(supabase, "toc_mission_logs", squadId);

  const { error } = await supabase
    .from("squads")
    .delete()
    .eq("id", squadId)
    .eq("organization_id", organizationId);
  if (error) {
    throw error;
  }
}

import type { SupabaseClient } from "@supabase/supabase-js";

export type OnlineOperatorRow = {
  sessionId: string;
  eventId: string;
  squadId: string;
  squadCode: string;
  squadName: string;
  loginAt: string | null;
  lastLatitude: number | null;
  lastLongitude: number | null;
  peerVisible: boolean;
  mapColor: string;
};

type SquadEmbed = {
  squad_code: string;
  squad_name: string;
  map_color: string | null;
};

function asSquad(raw: SquadEmbed | SquadEmbed[] | null | undefined): SquadEmbed | null {
  if (!raw) {
    return null;
  }
  return Array.isArray(raw) ? (raw[0] ?? null) : raw;
}

export function hasGpsFix(row: OnlineOperatorRow): boolean {
  return (
    typeof row.lastLatitude === "number" &&
    typeof row.lastLongitude === "number" &&
    Number.isFinite(row.lastLatitude) &&
    Number.isFinite(row.lastLongitude)
  );
}

export async function loadOnlineOperators(
  supabase: SupabaseClient,
  organizationId: string,
): Promise<{ rows: OnlineOperatorRow[]; error: string | null }> {
  const selectWithFlag =
    "id, event_id, squad_id, login_at, last_latitude, last_longitude, peer_visible, squads(squad_code, squad_name, map_color)";
  const selectNoFlag =
    "id, event_id, squad_id, login_at, last_latitude, last_longitude, squads(squad_code, squad_name, map_color)";

  let data: unknown[] | null = null;
  const first = await supabase
    .from("squad_sessions")
    .select(selectWithFlag)
    .eq("is_online", true)
    .eq("organization_id", organizationId)
    .order("login_at", { ascending: false });

  if (first.error) {
    const retry = await supabase
      .from("squad_sessions")
      .select(selectNoFlag)
      .eq("is_online", true)
      .eq("organization_id", organizationId)
      .order("login_at", { ascending: false });
    if (retry.error) {
      return { rows: [], error: first.error.message };
    }
    data = (retry.data ?? []) as unknown[];
  } else {
    data = (first.data ?? []) as unknown[];
  }

  const rows: OnlineOperatorRow[] = [];
  for (const raw of (data ?? []) as Record<string, unknown>[]) {
    const squad = asSquad(raw.squads as SquadEmbed | SquadEmbed[] | null);
    if (!squad) {
      continue;
    }
    rows.push({
      sessionId: String(raw.id),
      eventId: String(raw.event_id ?? ""),
      squadId: String(raw.squad_id ?? ""),
      squadCode: String(squad.squad_code ?? ""),
      squadName: String(squad.squad_name ?? ""),
      loginAt: raw.login_at == null ? null : String(raw.login_at),
      lastLatitude: raw.last_latitude == null ? null : Number(raw.last_latitude),
      lastLongitude: raw.last_longitude == null ? null : Number(raw.last_longitude),
      peerVisible: Boolean(raw.peer_visible),
      mapColor: /^#[0-9A-Fa-f]{6}$/.test((squad.map_color ?? "").trim())
        ? (squad.map_color as string).trim()
        : "#079B42",
    });
  }
  rows.sort((a, b) => a.squadCode.localeCompare(b.squadCode, "it"));
  return { rows, error: null };
}

export async function setPeerVisible(
  supabase: SupabaseClient,
  sessionId: string,
  organizationId: string,
  visible: boolean,
): Promise<string | null> {
  const { error } = await supabase
    .from("squad_sessions")
    .update({ peer_visible: visible })
    .eq("id", sessionId)
    .eq("organization_id", organizationId);
  return error?.message ?? null;
}

export async function forceLogoutOperator(
  supabase: SupabaseClient,
  row: OnlineOperatorRow,
  organizationId: string,
): Promise<string | null> {
  const now = new Date().toISOString();
  const { error } = await supabase
    .from("squad_sessions")
    .update({ is_online: false, logout_at: now })
    .eq("id", row.sessionId)
    .eq("organization_id", organizationId)
    .eq("is_online", true);
  if (error) {
    return error.message;
  }

  const log = await supabase.from("squad_session_auth_logs").insert({
    event_id: row.eventId,
    session_id: row.sessionId,
    squad_id: row.squadId,
    squad_code: row.squadCode,
    squad_name: row.squadName,
    action: "logout",
    organization_id: organizationId,
  });
  if (log.error) {
    const retry = await supabase.from("squad_session_auth_logs").insert({
      event_id: row.eventId,
      session_id: row.sessionId,
      squad_id: row.squadId,
      squad_code: row.squadCode,
      squad_name: row.squadName,
      action: "logout",
    });
    if (retry.error) {
      return null;
    }
  }
  return null;
}

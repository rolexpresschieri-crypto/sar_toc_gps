import { normalizeSquadIconKey } from "@/lib/squad-icons";

export type LiveSquad = {
  sessionId: string;
  eventId: string;
  squadId: string;
  squadCode: string;
  squadName: string;
  isOnline: boolean;
  lastLatitude: number | null;
  lastLongitude: number | null;
  lastAccuracy: number | null;
  lastFixAt: string | null;
  mapColor: string;
  mapIconKey: string;
};

export function hasCoordinates(squad: LiveSquad): boolean {
  return (
    typeof squad.lastLatitude === "number" &&
    typeof squad.lastLongitude === "number" &&
    !Number.isNaN(squad.lastLatitude) &&
    !Number.isNaN(squad.lastLongitude)
  );
}

export function normalizeMapColor(raw: string | null | undefined): string {
  const v = (raw ?? "").trim();
  if (/^#[0-9A-Fa-f]{6}$/.test(v)) {
    return v;
  }
  return "#079B42";
}

type SquadEmbed = {
  squad_code: string;
  squad_name: string;
  map_color: string | null;
  map_icon_key: string | null;
};

function asSquad(raw: SquadEmbed | SquadEmbed[] | null | undefined): SquadEmbed | null {
  if (!raw) {
    return null;
  }
  return Array.isArray(raw) ? (raw[0] ?? null) : raw;
}

export function liveSquadsFromSessionRows(rows: Record<string, unknown>[]): LiveSquad[] {
  return rows.flatMap((row) => {
    const squad = asSquad(row.squads as SquadEmbed | SquadEmbed[] | null);
    if (!squad) {
      return [];
    }
    return [
      {
        sessionId: row.id as string,
        eventId: row.event_id as string,
        squadId: row.squad_id as string,
        squadCode: squad.squad_code,
        squadName: squad.squad_name,
        isOnline: Boolean(row.is_online),
        lastLatitude: (row.last_latitude as number | null) ?? null,
        lastLongitude: (row.last_longitude as number | null) ?? null,
        lastAccuracy: (row.last_accuracy as number | null) ?? null,
        lastFixAt: (row.last_fix_at as string | null) ?? null,
        mapColor: normalizeMapColor(squad.map_color),
        mapIconKey: normalizeSquadIconKey(squad.map_icon_key),
      },
    ];
  });
}

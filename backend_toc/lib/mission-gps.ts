import type { SupabaseClient } from "@supabase/supabase-js";
import { orgStorageFolder } from "@/lib/comune-boundaries";

export const MISSION_GPS_BUCKET = "mission-gps";
const PLACEHOLDER = ".emptyFolderPlaceholder";

export type MissionFolder = {
  name: string;
};

export type MissionFile = {
  name: string;
  path: string;
};

export function sanitizeMissionFolder(raw: string): string {
  const name = raw
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, "_")
    .replace(/[^A-Z0-9_]/g, "")
    .replace(/_+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 40);
  if (!name) {
    throw new Error("Nome cartella non valido. Usa lettere, numeri e _.");
  }
  return name;
}

function fileKind(name: string): "trk" | "wpt" | null {
  const lower = name.toLowerCase();
  if (lower.endsWith(".trk")) {
    return "trk";
  }
  if (lower.endsWith(".wpt")) {
    return "wpt";
  }
  return null;
}

export function safeMissionFileName(raw: string): string {
  const base = raw.trim().split(/[/\\]/).pop()?.trim() ?? "";
  const kind = fileKind(base);
  if (!base || !kind) {
    throw new Error("Sono ammessi solo file .trk e .wpt.");
  }
  const cleaned = base.replace(/[^\w.\- ()]/g, "_").replace(/_+/g, "_");
  if (!fileKind(cleaned)) {
    throw new Error("Sono ammessi solo file .trk e .wpt.");
  }
  return cleaned;
}

function folderPath(orgCode: string, folder: string): string {
  const org = orgStorageFolder(orgCode);
  return folder ? `${org}/${folder}` : org;
}

export async function listMissionFolders(
  supabase: SupabaseClient,
  orgCode: string,
): Promise<MissionFolder[]> {
  const org = orgStorageFolder(orgCode);
  const { data, error } = await supabase.storage.from(MISSION_GPS_BUCKET).list(org, {
    limit: 200,
    sortBy: { column: "name", order: "asc" },
  });
  if (error) {
    throw error;
  }
  return (data ?? [])
    .filter((item) => !item.id && item.name && !item.name.startsWith("."))
    .map((item) => ({ name: item.name }))
    .sort((a, b) => a.name.localeCompare(b.name, "it"));
}

export async function listMissionFiles(
  supabase: SupabaseClient,
  orgCode: string,
  folder: string,
): Promise<MissionFile[]> {
  const prefix = folderPath(orgCode, folder);
  const { data, error } = await supabase.storage.from(MISSION_GPS_BUCKET).list(prefix, {
    limit: 500,
    sortBy: { column: "name", order: "asc" },
  });
  if (error) {
    throw error;
  }
  return (data ?? [])
    .filter((item) => item.id && fileKind(item.name))
    .map((item) => ({ name: item.name, path: `${prefix}/${item.name}` }));
}

export async function createMissionFolder(
  supabase: SupabaseClient,
  orgCode: string,
  rawName: string,
): Promise<string> {
  const name = sanitizeMissionFolder(rawName);
  const path = `${folderPath(orgCode, name)}/${PLACEHOLDER}`;
  const { error } = await supabase.storage.from(MISSION_GPS_BUCKET).upload(path, new Blob([""]), {
    upsert: true,
    contentType: "text/plain",
  });
  if (error && !error.message.toLowerCase().includes("already exists")) {
    throw error;
  }
  return name;
}

export async function uploadMissionFiles(
  supabase: SupabaseClient,
  orgCode: string,
  folder: string,
  files: File[],
): Promise<number> {
  if (!folder) {
    throw new Error("Scegli o crea prima la cartella.");
  }
  const prefix = folderPath(orgCode, sanitizeMissionFolder(folder));
  let count = 0;
  for (const file of files) {
    const name = safeMissionFileName(file.name);
    const { error } = await supabase.storage
      .from(MISSION_GPS_BUCKET)
      .upload(`${prefix}/${name}`, file, { upsert: true, contentType: "text/plain" });
    if (error) {
      throw error;
    }
    count += 1;
  }
  return count;
}

export async function deleteMissionFile(supabase: SupabaseClient, path: string): Promise<void> {
  const { error } = await supabase.storage.from(MISSION_GPS_BUCKET).remove([path]);
  if (error) {
    throw error;
  }
}

export async function deleteMissionFolder(
  supabase: SupabaseClient,
  orgCode: string,
  folder: string,
): Promise<void> {
  const prefix = folderPath(orgCode, sanitizeMissionFolder(folder));
  const { data, error } = await supabase.storage.from(MISSION_GPS_BUCKET).list(prefix, { limit: 500 });
  if (error) {
    throw error;
  }
  const paths = (data ?? []).filter((item) => item.name).map((item) => `${prefix}/${item.name}`);
  if (paths.length === 0) {
    return;
  }
  const { error: removeError } = await supabase.storage.from(MISSION_GPS_BUCKET).remove(paths);
  if (removeError) {
    throw removeError;
  }
}

export type MissionCatalogFile = MissionFile & { kind: "trk" | "wpt" };

export type MissionCatalogFolder = {
  name: string;
  files: MissionCatalogFile[];
};

export type MissionWaypoint = { name: string; lat: number; lon: number };

function parseNum(raw: string): number | null {
  const n = Number(raw.trim().replace(",", "."));
  return Number.isFinite(n) ? n : null;
}

export function parseMissionWpt(raw: string): MissionWaypoint[] {
  const labeled = parseLabeledWpt(raw);
  if (labeled.length > 0) {
    return labeled;
  }
  const out: MissionWaypoint[] = [];
  for (const line of raw.split(/\r?\n/)) {
    const t = line.trim();
    if (!t) {
      continue;
    }
    const low = t.toLowerCase();
    if (low.startsWith("name") || low.startsWith("lat")) {
      continue;
    }
    const parts = t.split(/[,;]+/).map((p) => p.trim());
    if (parts.length < 3) {
      continue;
    }
    const lat = parseNum(parts[1] ?? "");
    const lon = parseNum(parts[2] ?? "");
    if (!parts[0] || lat == null || lon == null) {
      continue;
    }
    out.push({ name: parts[0], lat, lon });
  }
  return out;
}

function parseLabeledWpt(raw: string): MissionWaypoint[] {
  const out: MissionWaypoint[] = [];
  for (const block of raw.split(/\r?\n\s*\r?\n/)) {
    let name = "";
    let lat: number | null = null;
    let lon: number | null = null;
    let labeled = false;
    for (const line of block.split(/\r?\n/).map((l) => l.trim()).filter(Boolean)) {
      const eq = /^(lat|lon|long|longitude|log|alt|altitude)\s*=\s*(.+)$/i.exec(line);
      if (eq) {
        labeled = true;
        const key = eq[1]!.toLowerCase();
        const value = parseNum(eq[2] ?? "");
        if (key === "lat") {
          lat = value;
        } else if (key === "lon" || key === "long" || key === "longitude" || key === "log") {
          lon = value;
        }
      } else if (!name && !line.includes("=") && !line.includes(",")) {
        name = line;
      }
    }
    if (labeled && name && lat != null && lon != null) {
      out.push({ name, lat, lon });
    }
  }
  return out;
}

export function parseMissionTrk(raw: string): Array<Array<[number, number]>> {
  const segments: Array<Array<[number, number]>> = [];
  let current: Array<[number, number]> = [];
  const pushPoint = (lat: number, lon: number, gap: boolean) => {
    if (gap && current.length > 0) {
      segments.push(current);
      current = [];
    }
    current.push([lat, lon]);
  };
  let gap = false;
  for (const line of raw.split(/\r?\n/)) {
    const t = line.trim();
    if (!t) {
      continue;
    }
    if (t.toLowerCase() === "#gap" || t === "---") {
      gap = true;
      continue;
    }
    const garmin = /^\s*T\s+A\s+([\d.,]+)\s*[°º]?\s*([NnSs])\s+([\d.,]+)\s*[°º]?\s*([EeWw])/i.exec(t);
    if (garmin) {
      let lat = parseNum(garmin[1] ?? "");
      let lon = parseNum(garmin[3] ?? "");
      if (lat == null || lon == null) {
        continue;
      }
      if ((garmin[2] ?? "").toUpperCase() === "S") {
        lat = -lat;
      }
      if ((garmin[4] ?? "").toUpperCase() === "W") {
        lon = -lon;
      }
      pushPoint(lat, lon, gap);
      gap = false;
      continue;
    }
    const csv = t.split(/[,;]+/).map((p) => p.trim());
    if (csv.length >= 2 && !csv[0]!.toLowerCase().startsWith("lat")) {
      const lat = parseNum(csv[0] ?? "");
      const lon = parseNum(csv[1] ?? "");
      if (lat == null || lon == null) {
        continue;
      }
      pushPoint(lat, lon, gap);
      gap = false;
    }
  }
  if (current.length > 0) {
    segments.push(current);
  }
  return segments.filter((seg) => seg.length >= 2);
}

export async function listMissionCatalog(
  supabase: SupabaseClient,
  orgCode: string,
): Promise<MissionCatalogFolder[]> {
  const folders = await listMissionFolders(supabase, orgCode);
  const groups: MissionCatalogFolder[] = [];
  for (const folder of folders) {
    const files = await listMissionFiles(supabase, orgCode, folder.name);
    groups.push({
      name: folder.name,
      files: files.map((file) => ({
        ...file,
        kind: file.name.toLowerCase().endsWith(".trk") ? "trk" : "wpt",
      })),
    });
  }
  return groups.filter((group) => group.files.length > 0);
}

export async function downloadMissionText(supabase: SupabaseClient, path: string): Promise<string> {
  const { data, error } = await supabase.storage.from(MISSION_GPS_BUCKET).download(path);
  if (error || !data) {
    throw error ?? new Error("Download non riuscito.");
  }
  return data.text();
}


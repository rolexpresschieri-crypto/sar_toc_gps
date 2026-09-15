import type { SupabaseClient } from "@supabase/supabase-js";

export const CONFINI_BUCKET = "comune-boundaries";

export const VAL_SUSA_CONFINI_SEED: Array<{ id: string; name: string; file: string }> = [
  { id: "claviere", name: "Claviere", file: "/map/confini/claviere.geojson" },
  { id: "cesana-torinese", name: "Cesana Torinese", file: "/map/confini/cesana-torinese.geojson" },
  { id: "sauze-di-cesana", name: "Sauze di Cesana", file: "/map/confini/sauze-di-cesana.geojson" },
  { id: "sestriere", name: "Sestriere", file: "/map/confini/sestriere.geojson" },
  { id: "sauze-d-oulx", name: "Sauze d'Oulx", file: "/map/confini/sauze-d-oulx.geojson" },
  { id: "oulx", name: "Oulx", file: "/map/confini/oulx.geojson" },
  { id: "pragelato", name: "Pragelato", file: "/map/confini/pragelato.geojson" },
];

export type ComuneBoundary = {
  id: string;
  name: string;
  path: string;
};

export function slugifyComune(raw: string): string {
  const slug = raw
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/['’]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 60);
  return slug || "comune";
}

export function nameFromGeoJson(geo: unknown, fallback: string): string {
  const root = geo as Record<string, unknown> | null;
  if (!root || typeof root !== "object") {
    return fallback;
  }
  const fromProps = (props: unknown): string => {
    if (!props || typeof props !== "object") {
      return "";
    }
    const name = (props as Record<string, unknown>).name;
    return typeof name === "string" ? name.trim() : "";
  };
  const direct = fromProps(root.properties);
  if (direct) {
    return direct;
  }
  const features = root.features;
  if (Array.isArray(features) && features[0] && typeof features[0] === "object") {
    const named = fromProps((features[0] as Record<string, unknown>).properties);
    if (named) {
      return named;
    }
  }
  return fallback;
}

function withComuneName(geo: Record<string, unknown>, name: string): Record<string, unknown> {
  const type = String(geo.type ?? "");
  if (type === "FeatureCollection" && Array.isArray(geo.features) && geo.features[0]) {
    const first = { ...(geo.features[0] as Record<string, unknown>) };
    first.properties = { ...((first.properties as Record<string, unknown>) ?? {}), name };
    return { ...geo, features: [first, ...geo.features.slice(1)] };
  }
  if (type === "Feature") {
    return { ...geo, properties: { ...((geo.properties as Record<string, unknown>) ?? {}), name } };
  }
  return {
    type: "FeatureCollection",
    features: [{ type: "Feature", properties: { name }, geometry: geo }],
  };
}

function assertBoundaryGeometry(geo: unknown) {
  const walk = (geom: Record<string, unknown> | undefined) => {
    const t = String(geom?.type ?? "");
    if (t !== "Polygon" && t !== "MultiPolygon") {
      throw new Error("Il file deve contenere un Polygon o MultiPolygon (confini comunali).");
    }
  };
  const root = geo as Record<string, unknown>;
  const type = String(root?.type ?? "");
  if (type === "FeatureCollection") {
    const features = root.features;
    if (!Array.isArray(features) || features.length === 0) {
      throw new Error("GeoJSON senza geometria.");
    }
    walk((features[0] as Record<string, unknown>).geometry as Record<string, unknown>);
    return;
  }
  if (type === "Feature") {
    walk(root.geometry as Record<string, unknown>);
    return;
  }
  walk(root);
}

export async function listComuneBoundaries(supabase: SupabaseClient): Promise<ComuneBoundary[]> {
  const { data, error } = await supabase.storage.from(CONFINI_BUCKET).list("", { limit: 200 });
  if (error) {
    throw error;
  }
  return (data ?? [])
    .filter((item) => item.name.toLowerCase().endsWith(".geojson") && item.id)
    .map((item) => {
      const id = item.name.replace(/\.geojson$/i, "");
      return { id, name: id, path: item.name };
    })
    .sort((a, b) => a.name.localeCompare(b.name, "it"));
}

export async function downloadComuneGeoJson(
  supabase: SupabaseClient,
  path: string,
): Promise<GeoJSON.GeoJsonObject> {
  const { data, error } = await supabase.storage.from(CONFINI_BUCKET).download(path);
  if (error || !data) {
    throw error ?? new Error("Download confini fallito.");
  }
  return JSON.parse(await data.text()) as GeoJSON.GeoJsonObject;
}

async function loadPublicSeedConfini(): Promise<Array<ComuneBoundary & { geo: GeoJSON.GeoJsonObject }>> {
  const rows: Array<ComuneBoundary & { geo: GeoJSON.GeoJsonObject }> = [];
  for (const seed of VAL_SUSA_CONFINI_SEED) {
    const res = await fetch(seed.file);
    if (!res.ok) {
      continue;
    }
    const geo = (await res.json()) as GeoJSON.GeoJsonObject;
    rows.push({ id: seed.id, name: seed.name, path: seed.file, geo });
  }
  return rows.sort((a, b) => a.name.localeCompare(b.name, "it"));
}

export async function loadComuneBoundariesWithGeo(
  supabase: SupabaseClient,
  options: { fallbackPublic?: boolean } = {},
): Promise<Array<ComuneBoundary & { geo: GeoJSON.GeoJsonObject }>> {
  try {
    const listed = await listComuneBoundaries(supabase);
    const rows: Array<ComuneBoundary & { geo: GeoJSON.GeoJsonObject }> = [];
    for (const item of listed) {
      const geo = await downloadComuneGeoJson(supabase, item.path);
      rows.push({ ...item, name: nameFromGeoJson(geo, item.id), geo });
    }
    if (rows.length > 0 || !options.fallbackPublic) {
      rows.sort((a, b) => a.name.localeCompare(b.name, "it"));
      return rows;
    }
  } catch {
    if (!options.fallbackPublic) {
      throw new Error("Bucket comune-boundaries assente. Esegui sql/comune_boundaries.sql.");
    }
  }
  return loadPublicSeedConfini();
}

export async function uploadComuneBoundary(
  supabase: SupabaseClient,
  name: string,
  geo: unknown,
  slugHint?: string,
): Promise<string> {
  assertBoundaryGeometry(geo);
  const named = name.trim() || nameFromGeoJson(geo, "Comune");
  const slug = slugifyComune(slugHint || named);
  const path = `${slug}.geojson`;
  const body = JSON.stringify(withComuneName(geo as Record<string, unknown>, named));
  const blob = new Blob([body], { type: "application/geo+json" });
  const { error } = await supabase.storage.from(CONFINI_BUCKET).upload(path, blob, {
    contentType: "application/geo+json",
    upsert: true,
  });
  if (error) {
    throw error;
  }
  return path;
}

export async function deleteComuneBoundary(supabase: SupabaseClient, path: string): Promise<void> {
  const { error } = await supabase.storage.from(CONFINI_BUCKET).remove([path]);
  if (error) {
    throw error;
  }
}

export async function seedValSusaConfini(supabase: SupabaseClient): Promise<number> {
  let n = 0;
  for (const seed of VAL_SUSA_CONFINI_SEED) {
    const res = await fetch(seed.file);
    if (!res.ok) {
      throw new Error(`File seed non trovato: ${seed.name}`);
    }
    const geo = (await res.json()) as unknown;
    await uploadComuneBoundary(supabase, seed.name, geo, seed.id);
    n += 1;
  }
  return n;
}

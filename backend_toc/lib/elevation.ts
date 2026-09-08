/** Quota del terreno (m s.l.m.) da modello digitale, non GPS barometrico. */
export async function fetchTerrainElevationM(lat: number, lon: number): Promise<number | null> {
  const url = `https://api.open-meteo.com/v1/elevation?latitude=${encodeURIComponent(String(lat))}&longitude=${encodeURIComponent(String(lon))}`;
  try {
    const res = await fetch(url);
    if (!res.ok) {
      return null;
    }
    const data = (await res.json()) as { elevation?: unknown };
    const raw = Array.isArray(data.elevation) ? data.elevation[0] : data.elevation;
    const m = typeof raw === "number" ? raw : Number(raw);
    if (!Number.isFinite(m)) {
      return null;
    }
    return Math.round(m);
  } catch {
    return null;
  }
}

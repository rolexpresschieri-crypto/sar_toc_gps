/** Parsing TRK + metriche cane/conduttore (distanza, durata, velocità). */

export type TrkPoint = {
  lat: number;
  lon: number;
  alt: number | null;
  timeMs: number | null;
  gapBefore: boolean;
};

export type TrkStats = {
  name: string;
  points: number;
  distanceM: number;
  durationMs: number | null;
  avgSpeedKmh: number | null;
  maxSpeedKmh: number | null;
  hasTime: boolean;
};

function parseNum(raw: string): number | null {
  const n = Number(raw.trim().replace(",", "."));
  return Number.isFinite(n) ? n : null;
}

function parseTimeMs(raw: string): number | null {
  const t = raw.trim();
  if (!t) return null;
  const n = Number(t.replace(",", "."));
  if (!Number.isFinite(n) || n <= 0) return null;
  if (n >= 1_000_000_000_000) return Math.floor(n);
  if (n >= 1_000_000_000) return Math.floor(n * 1000);
  return null;
}

export function haversineM(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const r = 6_371_000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function attrLatLon(attrs: string): { lat: number; lon: number } | null {
  const latM = /\blat\s*=\s*["']([^"']+)["']/i.exec(attrs);
  const lonM = /\blon\s*=\s*["']([^"']+)["']/i.exec(attrs);
  const lat = latM ? parseNum(latM[1] ?? "") : null;
  const lon = lonM ? parseNum(lonM[1] ?? "") : null;
  if (lat == null || lon == null) return null;
  return { lat, lon };
}

function parseGpxPointInner(inner: string): { alt: number | null; timeMs: number | null } {
  const eleM = /<ele\b[^>]*>([\s\S]*?)<\/ele>/i.exec(inner);
  const alt = eleM ? parseNum(eleM[1] ?? "") : null;
      const timeM = /<time\b[^>]*>([\s\S]*?)<\/time>/i.exec(inner);
  let timeMs: number | null = null;
  if (timeM?.[1]) {
    timeMs = parseGpxTimeMs(timeM[1].trim());
  }
  return { alt, timeMs };
}

/** GPX senza Z → tratta come UTC (evita sfasamenti fuso orario tra dispositivi). */
function parseGpxTimeMs(raw: string): number | null {
  const s = raw.trim();
  if (!s) return null;
  const normalized =
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(s) && !/[zZ]|[+-]\d{2}:?\d{2}$/.test(s)
      ? `${s}Z`
      : s;
  const t = Date.parse(normalized);
  return Number.isFinite(t) ? t : null;
}

/** Estrae punti da tag trkpt / rtept / wpt (anche self-closing e quote singole). */
function collectGpxTaggedPoints(raw: string, tag: string): TrkPoint[] {
  const out: TrkPoint[] = [];
  const re = new RegExp(`<(?:[\\w.-]+:)?${tag}\\b([^>/]*)(?:/>|>([\\s\\S]*?)</(?:[\\w.-]+:)?${tag}\\s*>)`, "gi");
  let m: RegExpExecArray | null;
  while ((m = re.exec(raw)) != null) {
    const ll = attrLatLon(m[1] ?? "");
    if (!ll) continue;
    const { alt, timeMs } = parseGpxPointInner(m[2] ?? "");
    out.push({ lat: ll.lat, lon: ll.lon, alt, timeMs, gapBefore: false });
  }
  return out;
}

function parseGpxTrackPoints(raw: string): TrkPoint[] {
  const out: TrkPoint[] = [];
  const segRe = /<(?:[\w.-]+:)?trkseg\b[^>]*>([\s\S]*?)<\/(?:[\w.-]+:)?trkseg\s*>/gi;
  const segments: string[] = [];
  let sm: RegExpExecArray | null;
  while ((sm = segRe.exec(raw)) != null) {
    segments.push(sm[1] ?? "");
  }
  const bodies = segments.length > 0 ? segments : [raw];
  let gapNext = false;
  for (let segIdx = 0; segIdx < bodies.length; segIdx++) {
    const pts = collectGpxTaggedPoints(bodies[segIdx] ?? "", "trkpt");
    let first = true;
    for (const p of pts) {
      out.push({
        ...p,
        gapBefore: gapNext || (segIdx > 0 && first),
      });
      gapNext = false;
      first = false;
    }
    if (pts.length > 0) gapNext = true;
  }
  return out;
}

function parseGpxAnyPoints(raw: string): TrkPoint[] {
  const fromTrk = parseGpxTrackPoints(raw);
  if (fromTrk.length >= 2) return fromTrk;
  const fromRte = collectGpxTaggedPoints(raw, "rtept");
  if (fromRte.length >= 2) return fromRte;
  // Poligoni / aree spesso salvati come sola sequenza di waypoint
  return collectGpxTaggedPoints(raw, "wpt");
}

export function parseTrkPoints(raw: string): TrkPoint[] {
  if (/<(?:[\w.-]+:)?(?:trkpt|rtept|wpt)\b/i.test(raw) || /<gpx\b/i.test(raw)) {
    return parseGpxAnyPoints(raw);
  }
  const out: TrkPoint[] = [];
  let nextGap = false;
  for (const line of raw.split(/\r?\n/)) {
    const t = line.trim();
    if (!t) continue;
    if (t.toLowerCase() === "#gap" || t === "---") {
      nextGap = true;
      continue;
    }
    const low = t.toLowerCase();
    if (low.startsWith("lat")) continue;

    const garmin =
      /^\s*T\s+A\s+([\d.,]+)\s*[°º]?\s*([NnSs])\s+([\d.,]+)\s*[°º]?\s*([EeWw])/i.exec(t);
    if (garmin) {
      let lat = parseNum(garmin[1] ?? "");
      let lon = parseNum(garmin[3] ?? "");
      if (lat == null || lon == null) continue;
      if ((garmin[2] ?? "").toUpperCase() === "S") lat = -lat;
      if ((garmin[4] ?? "").toUpperCase() === "W") lon = -lon;
      const altMatch = /\bs\s+([\d.,]+)/i.exec(t);
      const alt = altMatch ? parseNum(altMatch[1] ?? "") : null;
      out.push({ lat, lon, alt, timeMs: null, gapBefore: nextGap });
      nextGap = false;
      continue;
    }

    const csv = t.split(/[,;]+/).map((p) => p.trim());
    if (csv.length < 2) continue;
    const lat = parseNum(csv[0] ?? "");
    const lon = parseNum(csv[1] ?? "");
    if (lat == null || lon == null) continue;
    const alt = csv.length > 2 ? parseNum(csv[2] ?? "") : null;
    const timeMs = csv.length > 3 ? parseTimeMs(csv[3] ?? "") : null;
    out.push({ lat, lon, alt, timeMs, gapBefore: nextGap });
    nextGap = false;
  }
  return out;
}

/** Sopra questa soglia un Δt tra due fix è una pausa / spegnimento, non tempo di lavoro. */
const MAX_ACTIVE_SEG_SEC = 120;
/** Sotto questa velocità = fermo / jitter collare (non «in moto»). */
const MIN_MOVE_KMH = 0.8;
/**
 * Oltre ≈ corsa sostenuta in bosco: tipico salto collare GPS.
 * 18 km/h ≈ 5 m/s.
 */
const MAX_PLAUSIBLE_KMH = 18;
/** Anche con orario: un fix non può spostarsi di troppi metri. */
const MAX_SEG_M = 30;
/** Senza orario: salto > N metri tra due fix consecutivi = outlier. */
const MAX_JUMP_M_NO_TIME = 40;

function segmentSpeedKmh(dM: number, dtSec: number): number {
  if (dtSec <= 0) return Number.POSITIVE_INFINITY;
  return (dM / dtSec) * 3.6;
}

/** true = segmento utilizzabile per metriche (non pausa lunga, non teleporto GPS). */
function isPlausibleSegment(
  dM: number,
  a: TrkPoint,
  b: TrkPoint,
): { ok: boolean; kmh: number | null; dtSec: number | null } {
  if (b.gapBefore) return { ok: false, kmh: null, dtSec: null };
  if (a.timeMs != null && b.timeMs != null && b.timeMs > a.timeMs) {
    const dtSec = (b.timeMs - a.timeMs) / 1000;
    if (dtSec <= 0 || dtSec >= MAX_ACTIVE_SEG_SEC) {
      return { ok: false, kmh: null, dtSec };
    }
    if (dM > MAX_SEG_M) return { ok: false, kmh: segmentSpeedKmh(dM, dtSec), dtSec };
    const kmh = segmentSpeedKmh(dM, dtSec);
    if (kmh > MAX_PLAUSIBLE_KMH) return { ok: false, kmh, dtSec };
    return { ok: true, kmh, dtSec };
  }
  if (dM > MAX_JUMP_M_NO_TIME) return { ok: false, kmh: null, dtSec: null };
  return { ok: true, kmh: null, dtSec: null };
}

type TimedSeg = { t0: number; t1: number; dM: number; kmh: number };

export function computeTrkStats(name: string, points: TrkPoint[]): TrkStats {
  const timedSegs: TimedSeg[] = [];
  let untimedDistanceM = 0;
  let anyTime = false;

  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1]!;
    const b = points[i]!;
    const d = haversineM(a.lat, a.lon, b.lat, b.lon);
    const seg = isPlausibleSegment(d, a, b);
    if (!seg.ok) continue;
    if (seg.kmh != null && a.timeMs != null && b.timeMs != null) {
      anyTime = true;
      if (seg.kmh >= MIN_MOVE_KMH) {
        timedSegs.push({ t0: a.timeMs, t1: b.timeMs, dM: d, kmh: seg.kmh });
      }
    } else {
      untimedDistanceM += d;
    }
  }

  // Solo fase di lavoro principale (come il grafico velocità)
  const win =
    timedSegs.length >= 2
      ? movementTimeWindow(
          [timedSegs.map((s) => ({ tMs: s.t1, kmh: s.kmh }))],
          MIN_MOVE_KMH,
        )
      : null;
  const used =
    win != null
      ? timedSegs.filter((s) => s.t1 >= win.t0 && s.t0 <= win.t1)
      : timedSegs;

  let distanceM = untimedDistanceM;
  let activeDurationMs = 0;
  const speedsKmh: number[] = [];
  for (const s of used) {
    distanceM += s.dM;
    activeDurationMs += s.t1 - s.t0;
    speedsKmh.push(s.kmh);
  }

  const hasTime = anyTime || points.some((p) => p.timeMs != null);
  const durationMs = used.length > 0 && activeDurationMs > 0 ? activeDurationMs : null;

  let avgSpeedKmh: number | null = null;
  let maxSpeedKmh: number | null = null;
  if (speedsKmh.length > 0) {
    avgSpeedKmh = speedsKmh.reduce((s, v) => s + v, 0) / speedsKmh.length;
    const sorted = [...speedsKmh].sort((a, b) => a - b);
    const idx = Math.min(
      sorted.length - 1,
      Math.max(0, Math.ceil(sorted.length * 0.95) - 1),
    );
    maxSpeedKmh = sorted[idx] ?? sorted[sorted.length - 1]!;
  } else if (durationMs != null && durationMs > 0 && distanceM > 0) {
    avgSpeedKmh = (distanceM / (durationMs / 1000)) * 3.6;
  }

  return {
    name,
    points: points.length,
    distanceM,
    durationMs,
    avgSpeedKmh,
    maxSpeedKmh,
    hasTime,
  };
}

export function formatDistance(m: number): string {
  if (m >= 1000) return `${(m / 1000).toFixed(2)} km`;
  return `${m.toFixed(0)} m`;
}

export function formatDuration(ms: number | null): string {
  if (ms == null || ms < 0) return "n/d";
  const sec = Math.floor(ms / 1000);
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  if (m >= 60) {
    const h = Math.floor(m / 60);
    return `${h}h ${m % 60}m`;
  }
  return `${m} min ${s.toString().padStart(2, "0")} s`;
}

export function formatSpeed(kmh: number | null): string {
  if (kmh == null || !Number.isFinite(kmh)) return "n/d";
  return `${kmh.toFixed(1)} km/h`;
}

export function trackLatLngs(points: TrkPoint[]): Array<[number, number]> {
  return points.map((p) => [p.lat, p.lon]);
}

/** Distanza cumulata (m) per indice punto — non richiede timestamp. */
export function cumulativeDistanceSeries(points: TrkPoint[]): number[] {
  const out: number[] = [];
  let sum = 0;
  for (let i = 0; i < points.length; i++) {
    if (i > 0 && !points[i]!.gapBefore) {
      const a = points[i - 1]!;
      const b = points[i]!;
      sum += haversineM(a.lat, a.lon, b.lat, b.lon);
    }
    out.push(sum);
  }
  return out;
}

/** Velocità istantanea (km/h) tra punti consecutivi — richiede timestamp; scarta salti GPS. */
export function speedSeriesKmh(points: TrkPoint[]): Array<{ tMs: number; kmh: number }> {
  const out: Array<{ tMs: number; kmh: number }> = [];
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1]!;
    const b = points[i]!;
    if (a.timeMs == null || b.timeMs == null || b.timeMs <= a.timeMs) continue;
    const d = haversineM(a.lat, a.lon, b.lat, b.lon);
    const seg = isPlausibleSegment(d, a, b);
    if (!seg.ok || seg.kmh == null) continue;
    out.push({ tMs: b.timeMs, kmh: seg.kmh });
  }
  return out;
}

/**
 * Finestra del blocco di moto principale (non il primo micro-movimento in attesa).
 * I punti «in moto» vengono raggruppati; si sceglie il cluster con più campioni.
 */
export function movementTimeWindow(
  series: Array<Array<{ tMs: number; kmh: number }>>,
  minKmh = 1.0,
): { t0: number; t1: number } | null {
  const moving: number[] = [];
  for (const s of series) {
    for (const p of s) {
      if (p.kmh >= minKmh) moving.push(p.tMs);
    }
  }
  if (moving.length < 2) return null;
  moving.sort((a, b) => a - b);

  // Gap > 90s tra due fix «in moto» = cluster diverso (attesa / ripresa)
  const CLUSTER_GAP_MS = 90_000;
  type Cluster = { t0: number; t1: number; n: number };
  const clusters: Cluster[] = [];
  let cur: Cluster = { t0: moving[0]!, t1: moving[0]!, n: 1 };
  for (let i = 1; i < moving.length; i++) {
    const t = moving[i]!;
    if (t - cur.t1 > CLUSTER_GAP_MS) {
      clusters.push(cur);
      cur = { t0: t, t1: t, n: 1 };
    } else {
      cur.t1 = t;
      cur.n += 1;
    }
  }
  clusters.push(cur);

  let best = clusters[0]!;
  for (const c of clusters) {
    if (c.n > best.n || (c.n === best.n && c.t1 - c.t0 > best.t1 - best.t0)) {
      best = c;
    }
  }
  if (best.t1 <= best.t0 && best.n < 2) return null;
  // piccolo margine per non tagliare il primo/ultimo secondo di moto
  return { t0: best.t0, t1: Math.max(best.t1, best.t0 + 1000) };
}

export type SeparationSample = {
  /** Secondi dall’inizio comune (o indice normalizzato × durata stimata). */
  tSec: number;
  distM: number;
};

export type SeparationStats = {
  /** true = sincronizzazione reale via timestamp. */
  hasTime: boolean;
  samples: SeparationSample[];
  minM: number | null;
  maxM: number | null;
  avgM: number | null;
};

function timedPoints(points: TrkPoint[]): Array<TrkPoint & { timeMs: number }> {
  return points
    .filter((p): p is TrkPoint & { timeMs: number } => p.timeMs != null && Number.isFinite(p.timeMs))
    .slice()
    .sort((a, b) => a.timeMs - b.timeMs);
}

function interpolateAt(
  timed: Array<TrkPoint & { timeMs: number }>,
  tMs: number,
): { lat: number; lon: number } | null {
  if (timed.length < 1) return null;
  if (tMs <= timed[0]!.timeMs) return { lat: timed[0]!.lat, lon: timed[0]!.lon };
  const last = timed[timed.length - 1]!;
  if (tMs >= last.timeMs) return { lat: last.lat, lon: last.lon };
  for (let i = 1; i < timed.length; i++) {
    const a = timed[i - 1]!;
    const b = timed[i]!;
    if (tMs < a.timeMs || tMs > b.timeMs) continue;
    const span = b.timeMs - a.timeMs;
    const u = span > 0 ? (tMs - a.timeMs) / span : 0;
    return {
      lat: a.lat + (b.lat - a.lat) * u,
      lon: a.lon + (b.lon - a.lon) * u,
    };
  }
  return null;
}

/**
 * Distanza cane–conduttore nel tempo (metrica chiave tipo analisi ENCI).
 * Con timestamp: a ogni fix del cane interpola la posizione del conduttore allo stesso istante.
 * Senza timestamp: stima lungo il progresso (frazione di traccia) — meno affidabile.
 */
export function caneConduttoreSeparation(
  cane: TrkPoint[],
  conduttore: TrkPoint[],
): SeparationStats {
  const empty: SeparationStats = {
    hasTime: false,
    samples: [],
    minM: null,
    maxM: null,
    avgM: null,
  };
  if (cane.length < 2 || conduttore.length < 2) return empty;

  const caneT = timedPoints(cane);
  const condT = timedPoints(conduttore);
  const samples: SeparationSample[] = [];

  if (caneT.length >= 2 && condT.length >= 2) {
    const overlapStart = Math.max(caneT[0]!.timeMs, condT[0]!.timeMs);
    const overlapEnd = Math.min(caneT[caneT.length - 1]!.timeMs, condT[condT.length - 1]!.timeMs);
    if (overlapEnd > overlapStart) {
      // Griglia temporale uniforme: interpola ENTRAMBI (non solo i fix del cane).
      // Così densità diverse (es. 167 vs 845 pt) non alterano min/media/max.
      const durationMs = overlapEnd - overlapStart;
      const targetSamples = 240;
      const stepMs = Math.max(1000, Math.floor(durationMs / targetSamples));
      for (let t = overlapStart; t <= overlapEnd; t += stepMs) {
        const c = interpolateAt(caneT, t);
        const h = interpolateAt(condT, t);
        if (!c || !h) continue;
        samples.push({
          tSec: (t - overlapStart) / 1000,
          distM: haversineM(c.lat, c.lon, h.lat, h.lon),
        });
      }
      // Assicura anche l’ultimo istante
      const cEnd = interpolateAt(caneT, overlapEnd);
      const hEnd = interpolateAt(condT, overlapEnd);
      if (cEnd && hEnd) {
        const tSec = (overlapEnd - overlapStart) / 1000;
        const last = samples[samples.length - 1];
        if (!last || Math.abs(last.tSec - tSec) > 0.2) {
          samples.push({
            tSec,
            distM: haversineM(cEnd.lat, cEnd.lon, hEnd.lat, hEnd.lon),
          });
        }
      }
      return finalizeSeparation(samples, true);
    }
  }

  // Fallback geometrico: stessa frazione di avanzamento sulle due tracce
  const n = Math.min(120, Math.max(cane.length, conduttore.length));
  for (let i = 0; i < n; i++) {
    const u = n <= 1 ? 0 : i / (n - 1);
    const ci = Math.min(cane.length - 1, Math.round(u * (cane.length - 1)));
    const hi = Math.min(conduttore.length - 1, Math.round(u * (conduttore.length - 1)));
    const c = cane[ci]!;
    const h = conduttore[hi]!;
    samples.push({
      tSec: u * 100, // asse relativo 0..100 (progresso %)
      distM: haversineM(c.lat, c.lon, h.lat, h.lon),
    });
  }
  return finalizeSeparation(samples, false);
}

function finalizeSeparation(samples: SeparationSample[], hasTime: boolean): SeparationStats {
  if (samples.length === 0) {
    return { hasTime, samples: [], minM: null, maxM: null, avgM: null };
  }
  let minM = samples[0]!.distM;
  let maxM = samples[0]!.distM;
  let sum = 0;
  for (const s of samples) {
    minM = Math.min(minM, s.distM);
    maxM = Math.max(maxM, s.distM);
    sum += s.distM;
  }
  return {
    hasTime,
    samples,
    minM,
    maxM,
    avgM: sum / samples.length,
  };
}

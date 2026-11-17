"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ADMIN_SESSION_STORAGE_KEY,
  type AdminSessionData,
} from "@/lib/admin-auth";
import { downloadAnalisiTrkPdf } from "@/lib/analisi-trk-pdf";
import type { AnalisiMapHandle } from "@/components/analisi-trk-map";
import {
  downloadMissionText,
  listMissionCatalog,
  type MissionCatalogFolder,
} from "@/lib/mission-gps";
import { getSupabaseBrowserClient, restoreAdminSessionFromStorage } from "@/lib/supabase-browser";
import {
  caneConduttoreSeparation,
  computeTrkStats,
  formatDistance,
  formatDuration,
  formatSpeed,
  parseTrkPoints,
  type SeparationStats,
  type TrkPoint,
  type TrkStats,
} from "@/lib/trk-analysis";
import styles from "./analisi-trk-page.module.css";

const AnalisiTrkMap = dynamic(() => import("@/components/analisi-trk-map"), { ssr: false });

type LoadedTrk = {
  id: string;
  name: string;
  points: TrkPoint[];
};

type RoleKey = "area" | "conduttore" | "cane";

function StatBlock({
  title,
  stats,
  kind,
}: {
  title: string;
  stats: TrkStats | null;
  kind: "area" | "cane" | "conduttore";
}) {
  if (!stats) {
    return (
      <div className={`${styles.statCard} ${styles[kind]}`}>
        <h3>{title}</h3>
        <p className={styles.hint}>Seleziona una traccia.</p>
      </div>
    );
  }
  return (
    <div className={`${styles.statCard} ${styles[kind]}`}>
      <h3>
        {title}: {stats.name}
      </h3>
      <dl>
        <dt>Distanza</dt>
        <dd>{formatDistance(stats.distanceM)}</dd>
        <dt>Durata (in moto)</dt>
        <dd>{stats.hasTime ? formatDuration(stats.durationMs) : "n/d (senza orario)"}</dd>
        <dt>V media</dt>
        <dd>{stats.hasTime ? formatSpeed(stats.avgSpeedKmh) : "n/d (senza orario)"}</dd>
        <dt>V max</dt>
        <dd>{stats.hasTime ? formatSpeed(stats.maxSpeedKmh) : "n/d (senza orario)"}</dd>
      </dl>
    </div>
  );
}

function SeparationSparkline({ sep }: { sep: SeparationStats }) {
  if (sep.samples.length < 2) return null;
  const w = 280;
  const h = 72;
  const padL = 28;
  const padR = 4;
  const padT = 4;
  const padB = 4;
  const xs = sep.samples.map((s) => s.tSec);
  const ys = sep.samples.map((s) => s.distM);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = 0;
  const maxY = Math.max(...ys, 1);
  const dx = Math.max(maxX - minX, 1e-6);
  const dy = Math.max(maxY - minY, 1e-6);
  const plotW = w - padL - padR;
  const plotH = h - padT - padB;
  const pts = sep.samples
    .map((s) => {
      const x = padL + ((s.tSec - minX) / dx) * plotW;
      const y = padT + plotH - ((s.distM - minY) / dy) * plotH;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");
  const grid = [0, 0.25, 0.5, 0.75, 1].map((frac) => {
    const y = padT + plotH - frac * plotH;
    const val = minY + frac * (maxY - minY);
    return { y, label: val >= 1000 ? `${(val / 1000).toFixed(1)}k` : `${Math.round(val)}` };
  });
  return (
    <svg className={styles.spark} viewBox={`0 0 ${w} ${h}`} role="img" aria-label="Distanza cane-conduttore">
      {grid.map((g) => (
        <g key={g.y}>
          <line x1={padL} y1={g.y} x2={w - padR} y2={g.y} stroke="#5a6a88" strokeWidth="0.6" />
          <text x={padL - 3} y={g.y + 3} textAnchor="end" fill="#aab6d4" fontSize="8">
            {g.label}
          </text>
        </g>
      ))}
      <polyline fill="none" stroke="#9c6bce" strokeWidth="2" points={pts} />
    </svg>
  );
}

function SeparationBlock({
  sep,
  canePts,
  condPts,
}: {
  sep: SeparationStats | null;
  canePts: number;
  condPts: number;
}) {
  if (!sep || sep.samples.length === 0) {
    return (
      <div className={`${styles.statCard} ${styles.pair}`}>
        <h3>Distanza cane – conduttore</h3>
        <p className={styles.hint}>
          Assegna cane e conduttore. Con orario: distanza allo stesso istante (griglia temporale).
          Senza orario: stima per progresso (meno affidabile).
        </p>
      </div>
    );
  }
  return (
    <div className={`${styles.statCard} ${styles.pair}`}>
      <h3>Distanza cane – conduttore</h3>
      <dl>
        <dt>Sync</dt>
        <dd>{sep.hasTime ? "con orario (griglia 1s)" : "stimata (senza orario)"}</dd>
        <dt>Campioni</dt>
        <dd>{sep.samples.length}</dd>
        <dt>Min</dt>
        <dd>{sep.minM != null ? formatDistance(sep.minM) : "n/d"}</dd>
        <dt>Media</dt>
        <dd>{sep.avgM != null ? formatDistance(sep.avgM) : "n/d"}</dd>
        <dt>Max</dt>
        <dd>{sep.maxM != null ? formatDistance(sep.maxM) : "n/d"}</dd>
      </dl>
      {canePts > 0 && condPts > 0 && Math.max(canePts, condPts) / Math.min(canePts, condPts) >= 2 ? (
        <p className={styles.hint}>
          Densità diversa: cane {canePts} pt, conduttore {condPts} pt. Con GPX+orario la distanza è
          allo stesso secondo (non per «indice punto» come spesso sui .trk senza tempo) — i numeri
          possono differire, ed è corretto.
        </p>
      ) : null}
      <SeparationSparkline sep={sep} />
    </div>
  );
}

export default function AnalisiTrkPage() {
  const [supabase, setSupabase] = useState<ReturnType<typeof getSupabaseBrowserClient>>(null);
  const [session, setSession] = useState<AdminSessionData | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [catalog, setCatalog] = useState<MissionCatalogFolder[]>([]);
  const [loaded, setLoaded] = useState<LoadedTrk[]>([]);
  const [areaId, setAreaId] = useState("");
  const [caneId, setCaneId] = useState("");
  const [condId, setCondId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [uploadKey, setUploadKey] = useState(0);
  const [pdfBusy, setPdfBusy] = useState(false);
  const [info, setInfo] = useState<string | null>(null);
  const mapSectionRef = useRef<HTMLElement | null>(null);
  const mapHandleRef = useRef<AnalisiMapHandle | null>(null);
  const rolesRef = useRef({ areaId: "", caneId: "", condId: "" });
  const onMapReady = useCallback((h: AnalisiMapHandle) => {
    mapHandleRef.current = h;
  }, []);

  useEffect(() => {
    rolesRef.current = { areaId, caneId, condId };
  }, [areaId, caneId, condId]);

  useEffect(() => {
    setSupabase(getSupabaseBrowserClient());
    const raw = window.localStorage.getItem(ADMIN_SESSION_STORAGE_KEY);
    if (raw) {
      const restored = restoreAdminSessionFromStorage(raw);
      if (restored) {
        setSession(restored);
      } else {
        window.localStorage.removeItem(ADMIN_SESSION_STORAGE_KEY);
      }
    }
    setAuthChecked(true);
  }, []);

  useEffect(() => {
    if (!authChecked) return;
    if (!session) {
      window.location.replace("/");
    }
  }, [authChecked, session]);

  const refreshCatalog = useCallback(async () => {
    if (!supabase || !session?.organizationCode) {
      setCatalog([]);
      return;
    }
    try {
      const folders = await listMissionCatalog(supabase, session.organizationCode);
      setCatalog(folders);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Errore catalogo missione");
    }
  }, [supabase, session?.organizationCode]);

  useEffect(() => {
    void refreshCatalog();
  }, [refreshCatalog]);

  const missionTrkOptions = useMemo(() => {
    const opts: Array<{ path: string; label: string }> = [];
    for (const folder of catalog) {
      for (const file of folder.files) {
        if (file.kind !== "trk") continue;
        opts.push({ path: file.path, label: `${folder.name} / ${file.name}` });
      }
    }
    return opts;
  }, [catalog]);

  const assignRole = (id: string, name: string, force?: RoleKey) => {
    const r = rolesRef.current;
    // Il nome file non conta: solo il selettore sotto il ruolo (force) o il primo slot libero.
    let assigned: RoleKey | null = force ?? null;
    if (!assigned) {
      if (!r.areaId) assigned = "area";
      else if (!r.condId) assigned = "conduttore";
      else if (!r.caneId) assigned = "cane";
    }
    if (assigned === "area") {
      setAreaId(id);
      r.areaId = id;
    } else if (assigned === "cane") {
      setCaneId(id);
      r.caneId = id;
    } else if (assigned === "conduttore") {
      setCondId(id);
      r.condId = id;
    } else {
      setInfo(
        `«${name}» caricato. Sceglilo nei menu Area / Conduttore / Cane (il nome del file non importa).`,
      );
      return;
    }
    const label =
      assigned === "area" ? "Area" : assigned === "cane" ? "Cane" : "Conduttore";
    setInfo(`«${name}» assegnato a ${label}. Puoi cambiarlo dal menu.`);
  };

  const addLoaded = (name: string, text: string, forceRole?: RoleKey): boolean => {
    const points = parseTrkPoints(text);
    if (points.length < 2) {
      setError(
        `Traccia «${name}» non importata (${points.length} punti). Serve un GPX con traccia, route o almeno 2 waypoint.`,
      );
      return false;
    }
    const id = `${name}::${Date.now()}::${Math.random().toString(36).slice(2, 7)}`;
    setLoaded((prev) => [...prev, { id, name, points }]);
    setError(null);
    assignRole(id, name, forceRole);
    return true;
  };

  const loadMissionPath = async (path: string) => {
    if (!supabase || !path) return;
    setBusy(true);
    try {
      const text = await downloadMissionText(supabase, path);
      const name = path.split("/").pop() ?? path;
      addLoaded(name, text);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Download fallito");
    } finally {
      setBusy(false);
      setUploadKey((k) => k + 1);
    }
  };

  const onUpload = async (files: FileList | null, forceRole?: RoleKey) => {
    if (!files?.length) return;
    setBusy(true);
    try {
      for (const file of Array.from(files)) {
        const text = await file.text();
        addLoaded(file.name, text, forceRole);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Lettura file fallita");
    } finally {
      setBusy(false);
      setUploadKey((k) => k + 1);
    }
  };

  const area = loaded.find((t) => t.id === areaId) ?? null;
  const cane = loaded.find((t) => t.id === caneId) ?? null;
  const conduttore = loaded.find((t) => t.id === condId) ?? null;
  const areaStats = area ? computeTrkStats(area.name, area.points) : null;
  const caneStats = cane ? computeTrkStats(cane.name, cane.points) : null;
  const condStats = conduttore ? computeTrkStats(conduttore.name, conduttore.points) : null;
  const sepStats =
    cane && conduttore ? caneConduttoreSeparation(cane.points, conduttore.points) : null;
  const canExport = Boolean(areaStats || caneStats || condStats);
  const hasAnyRole = Boolean(areaId || caneId || condId || loaded.length);

  const handleReset = () => {
    setLoaded([]);
    setAreaId("");
    setCaneId("");
    setCondId("");
    rolesRef.current = { areaId: "", caneId: "", condId: "" };
    setError(null);
    setInfo(null);
    setBusy(false);
    setUploadKey((k) => k + 1);
  };

  const handlePdfStampa = async () => {
    if (!session || !canExport) return;
    setPdfBusy(true);
    setError(null);
    try {
      let mapDataUrl: string | null = null;
      const mapEl = mapSectionRef.current?.querySelector(
        ".leaflet-container",
      ) as HTMLElement | null;
      if (mapEl) {
        try {
          // Centra sulle tracce prima dello screenshot
          await mapHandleRef.current?.prepareForCapture();
          const html2canvas = (await import("html2canvas")).default;
          const canvas = await html2canvas(mapEl, {
            useCORS: true,
            allowTaint: false,
            backgroundColor: "#ffffff",
            logging: false,
            scale: 2,
          });
          mapDataUrl = canvas.toDataURL("image/png");
        } catch {
          mapDataUrl = null;
        }
      }

      await downloadAnalisiTrkPdf({
        orgCode: session.organizationCode,
        orgName: session.organizationName,
        area: areaStats,
        cane: caneStats,
        conduttore: condStats,
        areaPoints: area?.points ?? [],
        canePoints: cane?.points ?? [],
        conduttorePoints: conduttore?.points ?? [],
        mapDataUrl,
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Errore PDF / stampa");
    } finally {
      setPdfBusy(false);
    }
  };

  if (!authChecked || !session) {
    return <div className={styles.root}>Caricamento…</div>;
  }

  return (
    <div className={styles.root}>
      <header className={styles.topBar}>
        <div>
          <h1>Analisi TRK — area / conduttore / cane</h1>
          <p className={styles.sub}>
            Carica o scegli le tre tracce e assegna i ruoli. La distanza cane–conduttore nel tempo
            richiede l’orario sui punti (app aggiornata).
          </p>
        </div>
        <div className={styles.nav}>
          <button
            type="button"
            className={styles.actionBtn}
            disabled={pdfBusy || !hasAnyRole}
            onClick={handleReset}
          >
            RESET
          </button>
          <button
            type="button"
            className={styles.actionBtnPrimary}
            disabled={!canExport || pdfBusy}
            onClick={() => void handlePdfStampa()}
          >
            {pdfBusy ? "PDF…" : "PDF / Stampa"}
          </button>
          <Link className={styles.backLink} href="/anagrafica">
            Anagrafica
          </Link>
          <Link className={styles.backLink} href="/">
            ← Sala operativa
          </Link>
        </div>
      </header>

      <div className={styles.grid}>
        <aside className={styles.panel}>
          <h2>Tracce</h2>
          <p className={styles.hint}>
            Il nome del file non conta. Per l’area (e gli altri ruoli) usa il selettore file sotto
            «TRK area da bonificare» / Conduttore / Cane, oppure carica in alto e scegli dal menu.
          </p>

          <div className={styles.actionsRow}>
            <button
              type="button"
              className={styles.actionBtn}
              disabled={pdfBusy || !hasAnyRole}
              onClick={handleReset}
            >
              RESET
            </button>
            <button
              type="button"
              className={styles.actionBtnPrimary}
              disabled={!canExport || pdfBusy}
              onClick={() => void handlePdfStampa()}
            >
              {pdfBusy ? "PDF…" : "PDF / Stampa"}
            </button>
          </div>

          <div className={styles.field}>
            <label htmlFor="upload">Carica .gpx / .trk</label>
            <input
              key={uploadKey}
              id="upload"
              type="file"
              accept=".gpx,.trk,.txt,.bin,text/plain,*/*"
              multiple
              disabled={busy}
              onChange={(e) => void onUpload(e.target.files)}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="mission">Da missione TOC</label>
            <select
              id="mission"
              defaultValue=""
              disabled={busy || missionTrkOptions.length === 0}
              onChange={(e) => {
                const path = e.target.value;
                e.target.value = "";
                if (path) void loadMissionPath(path);
              }}
            >
              <option value="">
                {missionTrkOptions.length === 0 ? "Nessuna TRK in missione" : "— scegli —"}
              </option>
              {missionTrkOptions.map((o) => (
                <option key={o.path} value={o.path}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>

          <div className={styles.field}>
            <label htmlFor="area">TRK area da bonificare</label>
            <select id="area" value={areaId} onChange={(e) => setAreaId(e.target.value)}>
              <option value="">—</option>
              {loaded.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.points.length} pt)
                </option>
              ))}
            </select>
            <input
              key={`area-${uploadKey}`}
              type="file"
              accept=".gpx,.trk,.txt,.bin,text/plain,*/*"
              disabled={busy}
              aria-label="Carica file area"
              onChange={(e) => void onUpload(e.target.files, "area")}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="cond">TRK conduttore</label>
            <select id="cond" value={condId} onChange={(e) => setCondId(e.target.value)}>
              <option value="">—</option>
              {loaded.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.points.length} pt)
                </option>
              ))}
            </select>
            <input
              key={`cond-${uploadKey}`}
              type="file"
              accept=".gpx,.trk,.txt,.bin,text/plain,*/*"
              disabled={busy}
              aria-label="Carica file conduttore"
              onChange={(e) => void onUpload(e.target.files, "conduttore")}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="cane">TRK cane</label>
            <select id="cane" value={caneId} onChange={(e) => setCaneId(e.target.value)}>
              <option value="">—</option>
              {loaded.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.points.length} pt)
                </option>
              ))}
            </select>
            <input
              key={`cane-${uploadKey}`}
              type="file"
              accept=".gpx,.trk,.txt,.bin,text/plain,*/*"
              disabled={busy}
              aria-label="Carica file cane"
              onChange={(e) => void onUpload(e.target.files, "cane")}
            />
          </div>

          {error ? <p className={styles.error}>{error}</p> : null}
          {info ? <p className={styles.hint}>{info}</p> : null}
          {busy ? <p className={styles.hint}>Caricamento…</p> : null}

          <div className={styles.statsRow3}>
            <StatBlock title="Area" stats={areaStats} kind="area" />
            <StatBlock title="Conduttore" stats={condStats} kind="conduttore" />
            <StatBlock title="Cane" stats={caneStats} kind="cane" />
          </div>
          <div className={styles.pairWrap}>
            <SeparationBlock
              sep={sepStats}
              canePts={cane?.points.length ?? 0}
              condPts={conduttore?.points.length ?? 0}
            />
          </div>
        </aside>

        <section className={styles.panel} ref={mapSectionRef}>
          <h2>Mappa</h2>
          <div className={styles.mapWrap}>
            {area || cane || conduttore ? (
              <AnalisiTrkMap
                key={`${areaId}|${condId}|${caneId}|${loaded.length}`}
                areaPoints={area?.points ?? []}
                canePoints={cane?.points ?? []}
                conduttorePoints={conduttore?.points ?? []}
                onMapReady={onMapReady}
              />
            ) : (
              <p className={styles.hint} style={{ padding: 16 }}>
                Carica almeno una traccia per vedere la mappa.
              </p>
            )}
          </div>
          <div className={styles.legend}>
            <span>
              <i className={`${styles.swatch} ${styles.swatchArea}`} /> Area da bonificare
            </span>
            <span>
              <i className={`${styles.swatch} ${styles.swatchCond}`} /> Conduttore
            </span>
            <span>
              <i className={`${styles.swatch} ${styles.swatchCane}`} /> Cane
            </span>
          </div>
        </section>
      </div>
    </div>
  );
}

"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ADMIN_SESSION_STORAGE_KEY, type AdminSessionData } from "@/lib/admin-auth";
import { getSupabaseBrowserClient, restoreAdminSessionFromStorage } from "@/lib/supabase-browser";
import styles from "./event-log-page.module.css";

const PHOTO_BUCKET = "squad-photos";

type LogKind = "notifica" | "foto" | "login" | "logout";

type LogRow = {
  id: string;
  kind: LogKind;
  createdAt: string;
  squadCode: string;
  squadName: string;
  detail: string;
  storagePath: string | null;
};

function displaySurnameFirst(raw: string): string {
  const name = raw.trim().replace(/\s+/g, " ");
  const parts = name.split(" ");
  if (parts.length <= 1) {
    return name;
  }
  return `${parts[parts.length - 1]} ${parts.slice(0, -1).join(" ")}`;
}

function kindLabel(kind: LogKind): string {
  if (kind === "foto") {
    return "Foto";
  }
  if (kind === "login") {
    return "Login";
  }
  if (kind === "logout") {
    return "Logout";
  }
  return "Notifica";
}

function CameraIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path d="M9 3 7.2 5H4a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-3.2L15 3H9zm3 15a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-2.2A2.8 2.8 0 1 0 12 8.2a2.8 2.8 0 0 0 0 5.6z" />
    </svg>
  );
}

function DownloadIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path d="M12 3v10.2l3.4-3.4 1.4 1.4L12 17.4 7.2 11.2l1.4-1.4L11 13.2V3h1zM4 19h16v2H4v-2z" />
    </svg>
  );
}

export default function EventLogPage() {
  const searchParams = useSearchParams();
  const highlightPhotoId = searchParams.get("photoId")?.trim() ?? "";
  const [supabase, setSupabase] = useState<ReturnType<typeof getSupabaseBrowserClient>>(null);
  const [session, setSession] = useState<AdminSessionData | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [rows, setRows] = useState<LogRow[]>([]);
  const [status, setStatus] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [openUrl, setOpenUrl] = useState<string | null>(null);
  const [openCaption, setOpenCaption] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);

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

  const orgId = session?.organizationId ?? null;

  const refresh = useCallback(async () => {
    if (!supabase || !orgId) {
      setRows([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    const [alarmsRes, photosRes, authRes] = await Promise.all([
      supabase
        .from("squad_alarms")
        .select("id, squad_code, squad_name, message, created_at")
        .eq("organization_id", orgId)
        .order("created_at", { ascending: false })
        .limit(200),
      supabase
        .from("squad_field_photo_logs")
        .select("id, squad_code, squad_name, note, storage_path, status, created_at, latitude, longitude")
        .eq("organization_id", orgId)
        .eq("status", "inviato")
        .order("created_at", { ascending: false })
        .limit(200),
      supabase
        .from("squad_session_auth_logs")
        .select("id, squad_code, squad_name, action, created_at")
        .eq("organization_id", orgId)
        .order("created_at", { ascending: false })
        .limit(200),
    ]);

    const next: LogRow[] = [];
    if (!alarmsRes.error) {
      for (const a of (alarmsRes.data ?? []) as Record<string, unknown>[]) {
        next.push({
          id: String(a.id),
          kind: "notifica",
          createdAt: String(a.created_at),
          squadCode: String(a.squad_code ?? ""),
          squadName: String(a.squad_name ?? ""),
          detail: String(a.message ?? ""),
          storagePath: null,
        });
      }
    }
    const photosData = photosRes.error
      ? (
          await supabase
            .from("squad_field_photo_logs")
            .select("id, squad_code, squad_name, note, storage_path, status, created_at, latitude, longitude")
            .eq("organization_id", orgId)
            .eq("status", "inviato")
            .order("created_at", { ascending: false })
            .limit(200)
        ).data
      : photosRes.data;
    for (const p of (photosData ?? []) as Record<string, unknown>[]) {
      const lat = Number(p.latitude);
      const lon = Number(p.longitude);
      const note = typeof p.note === "string" ? p.note : "";
      const gps = Number.isFinite(lat) ? `${lat.toFixed(5)}, ${lon.toFixed(5)}` : "";
      next.push({
        id: String(p.id),
        kind: "foto",
        createdAt: String(p.created_at),
        squadCode: String(p.squad_code ?? ""),
        squadName: String(p.squad_name ?? ""),
        detail: [note, gps].filter(Boolean).join(" · "),
        storagePath: typeof p.storage_path === "string" ? p.storage_path : null,
      });
    }
    if (!authRes.error) {
      for (const s of (authRes.data ?? []) as Record<string, unknown>[]) {
        const action = String(s.action ?? "");
        next.push({
          id: String(s.id),
          kind: action === "logout" ? "logout" : "login",
          createdAt: String(s.created_at),
          squadCode: String(s.squad_code ?? ""),
          squadName: String(s.squad_name ?? ""),
          detail: action === "logout" ? "Logout operatore" : "Login operatore",
          storagePath: null,
        });
      }
    } else if (alarmsRes.error && photosRes.error) {
      setStatus(alarmsRes.error.message);
    }
    next.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    setRows(next);
    setLoading(false);
  }, [supabase, orgId]);

  useEffect(() => {
    if (!authChecked) {
      return;
    }
    if (!session) {
      window.location.replace("/");
      return;
    }
    void refresh();
  }, [authChecked, session, refresh]);

  useEffect(() => {
    if (!highlightPhotoId || rows.length === 0) {
      return;
    }
    document.getElementById(`log-row-${highlightPhotoId}`)?.scrollIntoView({
      block: "center",
      behavior: "smooth",
    });
  }, [highlightPhotoId, rows]);

  const signedCache = useMemo(() => new Map<string, string>(), []);

  async function signedUrlFor(path: string, id: string): Promise<string | null> {
    if (!supabase) {
      return null;
    }
    const cached = signedCache.get(id);
    if (cached) {
      return cached;
    }
    const { data, error } = await supabase.storage.from(PHOTO_BUCKET).createSignedUrl(path, 60 * 60);
    if (error || !data?.signedUrl) {
      setStatus(error?.message ?? "Impossibile aprire la foto.");
      return null;
    }
    signedCache.set(id, data.signedUrl);
    return data.signedUrl;
  }

  async function openPhoto(row: LogRow) {
    if (!row.storagePath) {
      return;
    }
    setBusyId(row.id);
    try {
      const url = await signedUrlFor(row.storagePath, row.id);
      if (!url) {
        return;
      }
      setOpenUrl(url);
      setOpenCaption(`${row.squadCode} — ${displaySurnameFirst(row.squadName)}${row.detail ? ` · ${row.detail}` : ""}`);
    } finally {
      setBusyId(null);
    }
  }

  async function savePhoto(row: LogRow) {
    if (!row.storagePath) {
      return;
    }
    setBusyId(row.id);
    try {
      const url = await signedUrlFor(row.storagePath, row.id);
      if (!url) {
        return;
      }
      const res = await fetch(url);
      const blob = await res.blob();
      const href = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = href;
      a.download = `TOC_SAR_${row.squadCode}_${row.id.slice(0, 8)}.jpg`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(href);
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Download foto fallito.");
    } finally {
      setBusyId(null);
    }
  }

  if (!authChecked || !session) {
    return <div className={styles.root}>Caricamento…</div>;
  }

  return (
    <div className={styles.root}>
      <header className={styles.topBar}>
        <div>
          <h1>LOG — {session.organizationCode}</h1>
          <p className={styles.sub}>Notifiche, foto e login operatori</p>
        </div>
        <Link className={styles.backLink} href="/">
          ← Sala operativa
        </Link>
      </header>
      <section className={styles.panel}>
        <p className={styles.hint}>
          {loading ? "Caricamento…" : `${rows.length} eventi.`} Icona foto: apri o salva JPEG.
        </p>
        {status ? <p className={styles.error}>{status}</p> : null}
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Data</th>
                <th>Tipo</th>
                <th>Operatore</th>
                <th>Dettaglio</th>
                <th>Foto</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr
                  key={`${r.kind}-${r.id}`}
                  id={`log-row-${r.id}`}
                  className={highlightPhotoId && r.id === highlightPhotoId ? styles.highlight : undefined}
                >
                  <td>{new Date(r.createdAt).toLocaleString("it-IT")}</td>
                  <td>{kindLabel(r.kind)}</td>
                  <td>
                    {r.squadCode} — {displaySurnameFirst(r.squadName)}
                  </td>
                  <td>{r.detail || "—"}</td>
                  <td>
                    {r.kind === "foto" && r.storagePath ? (
                      <div className={styles.photoCell}>
                        <button
                          type="button"
                          className={styles.iconBtn}
                          title="Apri foto"
                          disabled={busyId === r.id}
                          onClick={() => void openPhoto(r)}
                        >
                          <CameraIcon />
                        </button>
                        <button
                          type="button"
                          className={styles.iconBtn}
                          title="Salva JPEG"
                          disabled={busyId === r.id}
                          onClick={() => void savePhoto(r)}
                        >
                          <DownloadIcon />
                        </button>
                      </div>
                    ) : (
                      "—"
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      {openUrl ? (
        <div className={styles.lightbox} role="dialog" aria-modal="true" onClick={() => setOpenUrl(null)}>
          <div className={styles.lightboxInner} onClick={(e) => e.stopPropagation()}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className={styles.lightboxImg} src={openUrl} alt="Foto dal campo" />
            <p className={styles.lightboxCaption}>{openCaption}</p>
            <div className={styles.lightboxActions}>
              <button type="button" className={styles.lightboxBtn} onClick={() => setOpenUrl(null)}>
                Chiudi
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

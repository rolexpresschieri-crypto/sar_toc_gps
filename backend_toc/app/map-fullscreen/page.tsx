"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { ADMIN_SESSION_STORAGE_KEY, type AdminSessionData } from "@/lib/admin-auth";
import { restoreAdminSessionFromStorage, getSupabaseBrowserClient } from "@/lib/supabase-browser";
import { readStoredLayerMode, writeStoredLayerMode } from "@/lib/map-layer-storage";
import { layerOptions, parseLayerMode, type LayerMode } from "@/lib/map-layers";
import { useLiveFeed } from "@/lib/use-live-feed";
import type { LiveSquad } from "@/lib/live-squads";
import styles from "@/components/map-fullscreen.module.css";

const SarLiveMap = dynamic(() => import("@/components/sar-live-map"), { ssr: false });

function readAdminSession(): AdminSessionData | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(ADMIN_SESSION_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  return restoreAdminSessionFromStorage(raw);
}

function MapFullscreenContent() {
  const searchParams = useSearchParams();
  const displayMode = searchParams.get("display") === "1";
  const [supabase, setSupabase] = useState<ReturnType<typeof getSupabaseBrowserClient>>(null);
  const [session, setSession] = useState<AdminSessionData | null>(null);
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [recenterNonce, setRecenterNonce] = useState(0);
  const [layerMode, setLayerMode] = useState<LayerMode>("standard");
  const mapRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setSupabase(getSupabaseBrowserClient());
    setSession(readAdminSession());
    const fromUrl = parseLayerMode(searchParams.get("layer"));
    const next = fromUrl ?? readStoredLayerMode();
    setLayerMode(next);
    writeStoredLayerMode(next);
  }, [searchParams]);

  const orgId = session?.organizationId ?? null;
  const { squads, alarms, loadLive } = useLiveFeed(supabase, orgId, Boolean(session && supabase));

  const alarmingIds = useMemo(
    () => new Set(alarms.map((a) => a.session_id)),
    [alarms],
  );

  function selectSquad(squad: LiveSquad) {
    setSelectedSessionId(squad.sessionId);
    setRecenterNonce((n) => n + 1);
  }

  function toggleBrowserFullscreen() {
    const el = mapRef.current;
    if (!el) {
      return;
    }
    if (!document.fullscreenElement) {
      void el.requestFullscreen?.();
    } else {
      void document.exitFullscreen?.();
    }
  }

  if (!session) {
    return (
      <main className={styles.login}>
        <h1>Accesso richiesto</h1>
        <p>
          {displayMode ? (
            <>Sessione TOC chiusa nella sala operativa. Puoi chiudere questa finestra.</>
          ) : (
            <>
              <Link href="/">Effettua il login TOC</Link> nella finestra principale, poi riapri
              questa mappa.
            </>
          )}
        </p>
      </main>
    );
  }

  return (
    <main className={styles.screen}>
      <header className={styles.header}>
        {displayMode ? (
          <span className={styles.brand}>TOC SAR — mappa display</span>
        ) : (
          <Link className={styles.back} href="/">
            ← Sala operativa
          </Link>
        )}
        <button className={styles.btn} type="button" onClick={() => void loadLive()}>
          Aggiorna
        </button>
        <button className={styles.btn} type="button" onClick={() => setRecenterNonce((n) => n + 1)}>
          Ricentra mappa
        </button>
        <label className={styles.layerLabel}>
          Layer
          <select
            className={styles.layerSelect}
            value={layerMode}
            onChange={(e) => {
              const mode = e.target.value as LayerMode;
              setLayerMode(mode);
              writeStoredLayerMode(mode);
            }}
          >
            {layerOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </label>
        <button className={styles.btn} type="button" onClick={toggleBrowserFullscreen}>
          Schermo intero
        </button>
        {displayMode ? (
          <span className={styles.hint}>
            Trascina questa finestra sul secondo monitor. Poi F11 o «Schermo intero» se vuoi
            massimizzare.
          </span>
        ) : null}
      </header>
      <div ref={mapRef} className={styles.mapWrap}>
        <SarLiveMap
          squads={squads}
          selectedSessionId={selectedSessionId}
          alarmingSessionIds={alarmingIds}
          recenterNonce={recenterNonce}
          onSelect={selectSquad}
          layerMode={layerMode}
        />
      </div>
      <div className={styles.chips}>
        {squads.map((s) => {
          const alarming = alarmingIds.has(s.sessionId);
          return (
            <button
              key={s.sessionId}
              type="button"
              className={styles.chip}
              style={{
                background: alarming ? "#c62828" : s.mapColor,
                boxShadow: s.sessionId === selectedSessionId ? "0 0 0 2px #e0be3a" : undefined,
              }}
              onClick={() => selectSquad(s)}
            >
              {alarming ? "⚠ " : ""}
              {s.squadCode}
            </button>
          );
        })}
      </div>
    </main>
  );
}

export default function MapFullscreenPage() {
  return (
    <Suspense fallback={<main className={styles.login}>Caricamento mappa…</main>}>
      <MapFullscreenContent />
    </Suspense>
  );
}

"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { hasCoordinates, type LiveSquad } from "@/lib/live-squads";
import { readStoredLayerMode, writeStoredLayerMode } from "@/lib/map-layer-storage";
import { layerOptions, type LayerMode } from "@/lib/map-layers";
import { openExternalMapWindow } from "@/lib/open-external-map";
import {
  markPhotoAckedLocally,
  useLiveFeed,
  type FieldPhoto,
  type OutboundPush,
} from "@/lib/use-live-feed";
import { TocLoginForm, useTocSession } from "@/lib/use-toc-session";
import { canManageBackendUsers } from "@/lib/admin-auth";
import { enteLogoUrl } from "@/lib/ente-logo";
import styles from "./toc-home.module.css";

function HeaderEnteLogo({ orgCode, className }: { orgCode: string; className?: string }) {
  const src = enteLogoUrl(orgCode);
  const [broken, setBroken] = useState(false);
  useEffect(() => {
    setBroken(false);
  }, [src]);
  if (!src || broken) {
    return null;
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      className={className}
      src={src}
      alt={orgCode}
      onError={() => setBroken(true)}
    />
  );
}

function LogoutDoorIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="currentColor"
        d="M10 3a1 1 0 0 0-1 1v4h2V5h7v14h-7v-3H9v4a1 1 0 0 0 1 1h9a1 1 0 0 0 1-1V4a1 1 0 0 0-1-1h-9z"
      />
      <path
        fill="currentColor"
        d="M4.3 11.3a1 1 0 0 0 0 1.4l3 3a1 1 0 0 0 1.4-1.4L7.42 13H15a1 1 0 1 0 0-2H7.42l1.28-1.3a1 1 0 0 0-1.4-1.4l-3 3z"
      />
    </svg>
  );
}

const SarLiveMap = dynamic(() => import("@/components/sar-live-map"), {
  ssr: false,
  loading: () => <p className={styles.opsEmpty}>Caricamento mappa…</p>,
});

function displaySurnameFirst(raw: string): string {
  const name = raw.trim().replace(/\s+/g, " ");
  const parts = name.split(" ");
  if (parts.length <= 1) {
    return name;
  }
  return `${parts[parts.length - 1]} ${parts.slice(0, -1).join(" ")}`;
}

export default function TocHome() {
  const toc = useTocSession();
  const { supabase, session } = toc;
  const orgId = session?.organizationId ?? null;
  const {
    squads,
    alarms,
    setAlarms,
    photos,
    setPhotos,
    outbound,
    setOutbound,
    statusMessage,
    setStatusMessage,
    loadLive,
  } = useLiveFeed(supabase, orgId, Boolean(session && supabase));
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [recenterNonce, setRecenterNonce] = useState(0);
  const [ackBusy, setAckBusy] = useState<string | null>(null);
  const [layerMode, setLayerMode] = useState<LayerMode>("standard");
  const [focusPoint, setFocusPoint] = useState<{ lat: number; lng: number } | null>(null);
  const [focusNonce, setFocusNonce] = useState(0);
  const [pushText, setPushText] = useState("");
  const [pushBusy, setPushBusy] = useState(false);
  const [pushSelectAll, setPushSelectAll] = useState(true);
  const [pushTargets, setPushTargets] = useState<Set<string>>(new Set());
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setLayerMode(readStoredLayerMode());
  }, []);

  useEffect(() => {
    if (!menuOpen) {
      return;
    }
    function onDoc(e: MouseEvent) {
      if (!menuRef.current?.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        setMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [menuOpen]);

  useEffect(() => {
    const ids = squads.map((s) => s.sessionId);
    setPushTargets((prev) => {
      if (pushSelectAll) {
        return new Set(ids);
      }
      return new Set([...prev].filter((id) => ids.includes(id)));
    });
  }, [squads, pushSelectAll]);

  const notifyItems = useMemo(() => {
    const items: Array<
      | { kind: "text"; at: string; id: string }
      | { kind: "photo"; at: string; id: string }
      | { kind: "toc"; at: string; id: string }
    > = [
      ...alarms.map((a) => ({ kind: "text" as const, at: a.created_at, id: a.id })),
      ...photos.map((p) => ({ kind: "photo" as const, at: p.created_at, id: p.id })),
      ...outbound.map((p) => ({ kind: "toc" as const, at: p.created_at, id: p.id })),
    ];
    items.sort((a, b) => b.at.localeCompare(a.at));
    return items;
  }, [alarms, photos, outbound]);

  const alarmingIds = useMemo(
    () => new Set(alarms.map((a) => a.session_id)),
    [alarms],
  );

  function selectSquad(squad: LiveSquad) {
    setSelectedSessionId(squad.sessionId);
    setRecenterNonce((n) => n + 1);
  }

  function focusPhoto(photo: FieldPhoto) {
    const squad = squads.find((s) => s.sessionId === photo.session_id);
    if (squad) {
      selectSquad(squad);
    } else {
      setSelectedSessionId(photo.session_id);
    }
    if (Number.isFinite(photo.latitude) && Number.isFinite(photo.longitude)) {
      setFocusPoint({ lat: photo.latitude, lng: photo.longitude });
      setFocusNonce((n) => n + 1);
    }
  }

  async function ackAlarm(id: string) {
    if (!supabase || !session) {
      return;
    }
    setAckBusy(id);
    try {
      const { error } = await supabase
        .from("squad_alarms")
        .update({
          acknowledged_at: new Date().toISOString(),
          acknowledged_by: session.code,
        })
        .eq("id", id);
      if (error) {
        setStatusMessage(error.message);
        return;
      }
      setAlarms((prev) => prev.filter((a) => a.id !== id));
    } finally {
      setAckBusy(null);
    }
  }

  async function ackPhoto(id: string) {
    if (!supabase || !session) {
      return;
    }
    setAckBusy(`photo-${id}`);
    try {
      const { error } = await supabase
        .from("squad_field_photo_logs")
        .update({
          acknowledged_at: new Date().toISOString(),
          acknowledged_by: session.code,
        })
        .eq("id", id);
      if (error) {
        markPhotoAckedLocally(id);
      }
      setPhotos((prev) => prev.filter((p) => p.id !== id));
    } finally {
      setAckBusy(null);
    }
  }

  /** Chiude una notifica TOC→campo anche se l'operatore è offline / non ha fatto Reset. */
  async function resetOutboundFromToc(push: OutboundPush) {
    if (!supabase || !session) {
      return;
    }
    setAckBusy(`toc-${push.id}`);
    try {
      const now = new Date().toISOString();
      const { error } = await supabase
        .from("toc_push_logs")
        .update({
          mobile_dismissed_at: now,
          closed_at: now,
          closed_by: session.code,
        })
        .eq("id", push.id)
        .is("mobile_dismissed_at", null);
      if (error) {
        setStatusMessage(error.message);
        return;
      }
      if (push.event_id) {
        await supabase.from("toc_mission_force_dismiss_logs").insert({
          event_id: push.event_id,
          mission_kind: "toc_push",
          squad_code: push.squad_code,
          squad_name: push.squad_name,
          admin_code: session.code,
          source_ref: push.id,
          detail: [push.title, push.body].filter(Boolean).join(" — ").slice(0, 500) || "Reset da TOC",
        });
      }
      setOutbound((prev) => prev.filter((p) => p.id !== push.id));
      setStatusMessage(`Notifica a ${push.squad_code} resettata da TOC.`);
    } finally {
      setAckBusy(null);
    }
  }

  function togglePushTarget(sessionId: string) {
    setPushSelectAll(false);
    setPushTargets((prev) => {
      const next = new Set(prev);
      if (next.has(sessionId)) {
        next.delete(sessionId);
      } else {
        next.add(sessionId);
      }
      return next;
    });
  }

  async function sendTocPush() {
    if (!supabase || !session || !orgId) {
      return;
    }
    const body = pushText.trim();
    if (!body) {
      setStatusMessage("Scrivi il testo della notifica.");
      return;
    }
    const targets = squads.filter((s) => pushTargets.has(s.sessionId));
    if (targets.length === 0) {
      setStatusMessage("Seleziona almeno un operatore online.");
      return;
    }
    setPushBusy(true);
    try {
      let eventId = targets[0]?.eventId ?? "";
      if (!eventId) {
        const { data: ev, error: evErr } = await supabase
          .from("events")
          .select("id")
          .eq("organization_id", orgId)
          .eq("is_active", true)
          .maybeSingle();
        if (evErr || !ev?.id) {
          setStatusMessage("Nessuna operazione attiva: impossibile inviare.");
          return;
        }
        eventId = String(ev.id);
      }
      const rows = targets.map((s) => ({
        event_id: s.eventId || eventId,
        session_id: s.sessionId,
        squad_id: s.squadId,
        squad_code: s.squadCode,
        squad_name: s.squadName,
        admin_code: session.code,
        title: "TOC SAR",
        body: body.slice(0, 500),
        is_alarm: true,
        status: "sent",
      }));
      const { error } = await supabase.from("toc_push_logs").insert(rows);
      if (error) {
        setStatusMessage(error.message);
        return;
      }
      setPushText("");
      setStatusMessage(`Notifica inviata a ${rows.length} operator${rows.length === 1 ? "e" : "i"}.`);
      await loadLive();
      setStatusMessage(`Notifica inviata a ${rows.length} operator${rows.length === 1 ? "e" : "i"}.`);
    } finally {
      setPushBusy(false);
    }
  }

  if (!toc.authChecked) {
    return <div className={styles.screen}>Caricamento…</div>;
  }

  if (!session) {
    return (
      <TocLoginForm
        loginEnte={toc.loginEnte}
        setLoginEnte={toc.setLoginEnte}
        loginCode={toc.loginCode}
        setLoginCode={toc.setLoginCode}
        loginPassword={toc.loginPassword}
        setLoginPassword={toc.setLoginPassword}
        enteLocked={toc.enteLocked}
        loginBusy={toc.loginBusy}
        statusMessage={toc.statusMessage}
        onLogin={(e) => void toc.handleLogin(e)}
        onChangeEnte={toc.handleChangeEnte}
      />
    );
  }

  return (
    <main className={styles.screen}>
      <header className={styles.header}>
        <div className={styles.headerTop}>
          <div className={styles.headerLogoWrap}>
            <HeaderEnteLogo orgCode={session.organizationCode} className={styles.headerLogo} />
          </div>
          <div className={styles.headerCenterCluster}>
            <h1>
              TOC SAR <span className={styles.orgTag}>· {session.organizationCode}</span>
            </h1>
            <p className={styles.sub}>
              {session.organizationName} · {session.code}
            </p>
            {statusMessage ? <p className={styles.message}>{statusMessage}</p> : null}
          </div>
          <div className={styles.headerMenuSlot} ref={menuRef}>
            <button
              type="button"
              className={styles.burgerBtn}
              aria-label={menuOpen ? "Chiudi menu" : "Apri menu"}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((v) => !v)}
            >
              <span className={styles.burgerBar} />
              <span className={styles.burgerBar} />
              <span className={styles.burgerBar} />
            </button>
            {menuOpen ? (
              <nav className={styles.burgerMenu} aria-label="Menu TOC">
                <Link className={styles.burgerItem} href="/anagrafica" onClick={() => setMenuOpen(false)}>
                  Anagrafica
                </Link>
                <Link className={styles.burgerItem} href="/log" onClick={() => setMenuOpen(false)}>
                  LOG
                </Link>
                <button
                  type="button"
                  className={styles.burgerItem}
                  onClick={() => {
                    setMenuOpen(false);
                    openExternalMapWindow(layerMode);
                  }}
                >
                  Mappa su schermo grande
                </button>
                <Link className={styles.burgerItem} href="/config" onClick={() => setMenuOpen(false)}>
                  Operatori online
                </Link>
                {canManageBackendUsers(session.code) ? (
                  <Link className={styles.burgerItem} href="/utenti" onClick={() => setMenuOpen(false)}>
                    Utenti backend
                  </Link>
                ) : null}
                <button
                  type="button"
                  className={`${styles.burgerItem} ${styles.burgerLogout}`}
                  onClick={() => {
                    setMenuOpen(false);
                    toc.handleLogout();
                  }}
                >
                  <LogoutDoorIcon className={styles.burgerLogoutIcon} />
                  Logout TOC
                </button>
              </nav>
            ) : null}
          </div>
        </div>
        <div className={styles.actions}>
          <button className={`${styles.btn} ${styles.btnPrimary}`} type="button" onClick={() => void loadLive()}>
            Aggiorna
          </button>
        </div>
      </header>

      <div className={styles.workspace}>
        <div className={styles.opsGrid}>
          <section className={styles.opsColumn}>
            <h2 className={styles.opsColumnTitle}>Operatori online ({squads.length})</h2>
            <p className={styles.opsColumnHint}>Clicca un operatore per centrarlo sulla mappa.</p>
            <div className={styles.opsColumnBody}>
              <ul className={styles.squadList}>
                {squads.length === 0 ? (
                  <li className={styles.opsEmpty}>Nessun operatore online.</li>
                ) : (
                  squads.map((s) => {
                    const alarming = alarmingIds.has(s.sessionId);
                    const selected = selectedSessionId === s.sessionId;
                    return (
                      <li
                        key={s.sessionId}
                        className={
                          selected ? `${styles.squadRow} ${styles.squadRowSelected}` : styles.squadRow
                        }
                        onClick={() => selectSquad(s)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault();
                            selectSquad(s);
                          }
                        }}
                        role="button"
                        tabIndex={0}
                      >
                        <span
                          className={
                            alarming ? `${styles.squadBadge} ${styles.squadBadgeAlarm}` : styles.squadBadge
                          }
                          style={alarming ? undefined : { background: s.mapColor }}
                        />
                        <span className={alarming ? styles.squadLabelAlarm : styles.squadLabel}>
                          {alarming ? "ALLARME — " : ""}
                          {s.squadCode} — {displaySurnameFirst(s.squadName)}
                        </span>
                        {!hasCoordinates(s) ? <span className={styles.noGps}>no GPS</span> : null}
                      </li>
                    );
                  })
                )}
              </ul>
            </div>
          </section>

          <section className={styles.opsColumn}>
            <h2 className={styles.opsColumnTitle}>Notifiche ({notifyItems.length})</h2>
            <p className={styles.opsColumnHint}>
              Resta in elenco finché l&apos;operatore fa Reset notifica, oppure usa Reset da TOC (anche se è offline).
            </p>
            <form
              className={styles.pushForm}
              onSubmit={(e) => {
                e.preventDefault();
                void sendTocPush();
              }}
            >
              <textarea
                className={styles.pushText}
                value={pushText}
                onChange={(e) => setPushText(e.target.value)}
                placeholder="Testo notifica TOC → operatori"
                rows={3}
                maxLength={500}
                disabled={pushBusy}
              />
              <label className={styles.pushAll}>
                <input
                  type="checkbox"
                  checked={pushSelectAll && squads.length > 0 && pushTargets.size === squads.length}
                  onChange={(e) => setPushSelectAll(e.target.checked)}
                  disabled={pushBusy || squads.length === 0}
                />
                Tutti gli online ({squads.length})
              </label>
              {squads.length > 0 ? (
                <ul className={styles.pushList}>
                  {squads.map((s) => (
                    <li key={s.sessionId}>
                      <label>
                        <input
                          type="checkbox"
                          checked={pushTargets.has(s.sessionId)}
                          onChange={() => togglePushTarget(s.sessionId)}
                          disabled={pushBusy}
                        />
                        {s.squadCode} — {displaySurnameFirst(s.squadName)}
                      </label>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className={styles.opsEmpty}>Nessun operatore online a cui inviare.</p>
              )}
              <button
                type="submit"
                className={`${styles.btn} ${styles.btnPrimary} ${styles.btnSmall}`}
                disabled={pushBusy || squads.length === 0}
              >
                {pushBusy ? "Invio…" : `Invia al campo (${pushTargets.size})`}
              </button>
            </form>
            <div className={styles.opsColumnBody}>
              {notifyItems.length === 0 ? (
                <p className={styles.opsEmpty}>Nessuna notifica in attesa.</p>
              ) : (
                notifyItems.map((item) => {
                  if (item.kind === "toc") {
                    const p = outbound.find((x) => x.id === item.id);
                    if (!p) {
                      return null;
                    }
                    return (
                      <div
                        key={`toc-${p.id}`}
                        className={`${styles.alarmItem} ${styles.tocItem}`}
                        onClick={() => {
                          if (!p.session_id) {
                            return;
                          }
                          const squad = squads.find((s) => s.sessionId === p.session_id);
                          if (squad) {
                            selectSquad(squad);
                          } else {
                            setSelectedSessionId(p.session_id);
                          }
                        }}
                        role="button"
                        tabIndex={0}
                      >
                        <div className={styles.alarmDot} aria-hidden>
                          →
                        </div>
                        <div className={styles.alarmBody}>
                          <p className={styles.alarmTitle}>
                            TOC → {p.squad_code} — {displaySurnameFirst(p.squad_name)}
                          </p>
                          {p.body ? <p className={styles.alarmMessage}>{p.body}</p> : null}
                          <p className={styles.alarmMeta}>
                            Inviata {new Date(p.created_at).toLocaleString("it-IT")}
                            {p.admin_code ? ` · da ${p.admin_code}` : ""}
                            {" · in attesa di Reset notifica"}
                          </p>
                          <div className={styles.notifyActions}>
                            <button
                              type="button"
                              className={`${styles.btn} ${styles.btnSmall}`}
                              disabled={ackBusy === `toc-${p.id}`}
                              onClick={(e) => {
                                e.stopPropagation();
                                void resetOutboundFromToc(p);
                              }}
                            >
                              Reset da TOC
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  }
                  if (item.kind === "photo") {
                    const p = photos.find((x) => x.id === item.id);
                    if (!p) {
                      return null;
                    }
                    return (
                      <div
                        key={`photo-${p.id}`}
                        className={`${styles.alarmItem} ${styles.photoItem}`}
                        onClick={() => focusPhoto(p)}
                        role="button"
                        tabIndex={0}
                      >
                        <div className={styles.alarmDot} aria-hidden>
                          ●
                        </div>
                        <div className={styles.alarmBody}>
                          <p className={styles.alarmTitle}>
                            Foto inviata da {p.squad_code} — {displaySurnameFirst(p.squad_name)}
                          </p>
                          <p className={styles.alarmMeta}>
                            {new Date(p.created_at).toLocaleString("it-IT")}
                          </p>
                          <div className={styles.notifyActions}>
                            <Link
                              className={styles.logLink}
                              href={`/log?photoId=${encodeURIComponent(p.id)}`}
                              onClick={(e) => e.stopPropagation()}
                            >
                              Vedi in LOG
                            </Link>
                            <button
                              type="button"
                              className={`${styles.btn} ${styles.btnSmall}`}
                              disabled={ackBusy === `photo-${p.id}`}
                              onClick={(e) => {
                                e.stopPropagation();
                                void ackPhoto(p.id);
                              }}
                            >
                              Spegni
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  }
                  const a = alarms.find((x) => x.id === item.id);
                  if (!a) {
                    return null;
                  }
                  return (
                    <div
                      key={`text-${a.id}`}
                      className={styles.alarmItem}
                      onClick={() => {
                        const squad = squads.find((s) => s.sessionId === a.session_id);
                        if (squad) {
                          selectSquad(squad);
                        } else {
                          setSelectedSessionId(a.session_id);
                        }
                      }}
                      role="button"
                      tabIndex={0}
                    >
                      <div className={styles.alarmDot} aria-hidden>
                        !
                      </div>
                      <div className={styles.alarmBody}>
                        <p className={styles.alarmTitle}>
                          {a.squad_code} — {displaySurnameFirst(a.squad_name)}
                        </p>
                        {a.message ? <p className={styles.alarmMessage}>{a.message}</p> : null}
                        <p className={styles.alarmMeta}>
                          {new Date(a.created_at).toLocaleString("it-IT")}
                        </p>
                        <div className={styles.notifyActions}>
                          <button
                            type="button"
                            className={`${styles.btn} ${styles.btnSmall}`}
                            disabled={ackBusy === a.id}
                            onClick={(e) => {
                              e.stopPropagation();
                              void ackAlarm(a.id);
                            }}
                          >
                            Preso in carico
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </section>

          <section className={styles.opsColumn}>
            <h2 className={styles.opsColumnTitle}>Mappa</h2>
            <div className={styles.mapToolbar}>
              <p className={styles.opsColumnHint}>Posizioni GPS degli operatori online.</p>
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
            </div>
            <div className={styles.mapPane}>
              <SarLiveMap
                squads={squads}
                selectedSessionId={selectedSessionId}
                alarmingSessionIds={alarmingIds}
                recenterNonce={recenterNonce}
                onSelect={selectSquad}
                layerMode={layerMode}
                focusPoint={focusPoint}
                focusNonce={focusNonce}
                organizationCode={session.organizationCode}
              />
            </div>
          </section>
        </div>
      </div>
    </main>
  );
}

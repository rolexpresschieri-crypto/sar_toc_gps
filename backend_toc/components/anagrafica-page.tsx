"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import {
  ADMIN_SESSION_STORAGE_KEY,
  canManageAnagrafica,
  type AdminSessionData,
} from "@/lib/admin-auth";
import {
  DEFAULT_SQUAD_ICON_KEY,
  SQUAD_ICON_OPTIONS,
  normalizeSquadIconKey,
  squadIconMapUrl,
  type SquadIconKey,
} from "@/lib/squad-icons";
import {
  getSupabaseBrowserClient,
  restoreAdminSessionFromStorage,
} from "@/lib/supabase-browser";
import { deleteOperatorForOrganization } from "@/lib/delete-operator";
import { normalizeMapColor } from "@/lib/live-squads";
import styles from "./anagrafica.module.css";

type SquadRow = {
  id: string;
  squad_code: string;
  squad_name: string;
  password_hash: string;
  map_color: string | null;
  map_icon_key: string | null;
  is_enabled: boolean;
};

type OperationRow = {
  id: string;
  title: string;
  description: string | null;
  is_active: boolean;
  created_at: string;
};

const DEFAULT_COLORS = ["#079B42", "#1E88E5", "#E0BE3A", "#C62828", "#6A1B9A"];

/** Anagrafica salvata come «Nome Cognome»: in elenco si mostra Cognome Nome. */
function displaySurnameFirst(raw: string): string {
  const name = raw.trim().replace(/\s+/g, " ");
  if (!name) {
    return "";
  }
  const parts = name.split(" ");
  if (parts.length === 1) {
    return parts[0];
  }
  const family = parts[parts.length - 1];
  const given = parts.slice(0, -1).join(" ");
  return `${family} ${given}`;
}

function surnameSortKey(raw: string): string {
  const name = raw.trim().replace(/\s+/g, " ");
  const parts = name.split(" ").filter(Boolean);
  if (parts.length === 0) {
    return "";
  }
  if (parts.length === 1) {
    return parts[0].toLocaleLowerCase("it");
  }
  const family = parts[parts.length - 1];
  const given = parts.slice(0, -1).join(" ");
  return `${family.toLocaleLowerCase("it")}\u0000${given.toLocaleLowerCase("it")}`;
}

function friendlyError(err: unknown, fallback: string): string {
  const msg = err instanceof Error ? err.message : fallback;
  const lower = msg.toLowerCase();
  if (lower.includes("duplicate") || lower.includes("unique")) {
    return "Codice già presente in questo ente.";
  }
  if (lower.includes("foreign key") || lower.includes("violates")) {
    return "Impossibile eliminare: ci sono dati collegati. Disabilita il login invece.";
  }
  return msg || fallback;
}

export default function AnagraficaPage() {
  const router = useRouter();
  const [supabase, setSupabase] = useState<ReturnType<typeof getSupabaseBrowserClient>>(null);
  const [session, setSession] = useState<AdminSessionData | null>(null);
  const [authChecked, setAuthChecked] = useState(false);

  const [squads, setSquads] = useState<SquadRow[]>([]);
  const [operations, setOperations] = useState<OperationRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const [squadEditingId, setSquadEditingId] = useState<string | null>(null);
  const [squadCode, setSquadCode] = useState("");
  const [squadName, setSquadName] = useState("");
  const [squadPassword, setSquadPassword] = useState("");
  const [mapColor, setMapColor] = useState(DEFAULT_COLORS[0]);
  const [mapIconKey, setMapIconKey] = useState<SquadIconKey>(DEFAULT_SQUAD_ICON_KEY);
  const [squadEnabled, setSquadEnabled] = useState(true);

  const [operationTitle, setOperationTitle] = useState("");
  const [operationDescription, setOperationDescription] = useState("");
  const [operationActivateNow, setOperationActivateNow] = useState(true);

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
    if (authChecked && !session) {
      router.replace("/");
    }
  }, [authChecked, session, router]);

  const orgId = session?.organizationId ?? null;

  const squadsBySurname = useMemo(
    () =>
      [...squads].sort((a, b) =>
        surnameSortKey(a.squad_name).localeCompare(surnameSortKey(b.squad_name), "it"),
      ),
    [squads],
  );

  const refreshSquads = useCallback(async () => {
    if (!supabase || !orgId) {
      setSquads([]);
      return;
    }
    const { data, error } = await supabase
      .from("squads")
      .select("id, squad_code, squad_name, password_hash, map_color, map_icon_key, is_enabled")
      .eq("organization_id", orgId)
      .order("squad_code", { ascending: true });
    if (error) {
      setFormError(error.message);
      return;
    }
    setSquads((data ?? []) as SquadRow[]);
  }, [supabase, orgId]);

  const refreshOperations = useCallback(async () => {
    if (!supabase || !orgId) {
      setOperations([]);
      return;
    }
    const { data, error } = await supabase
      .from("events")
      .select("id, title, description, is_active, created_at")
      .eq("organization_id", orgId)
      .order("created_at", { ascending: false });
    if (error) {
      setFormError(error.message);
      return;
    }
    setOperations((data ?? []) as OperationRow[]);
  }, [supabase, orgId]);

  useEffect(() => {
    if (!session || !supabase || !orgId) {
      setSquads([]);
      setOperations([]);
      return;
    }
    void (async () => {
      setLoading(true);
      setFormError(null);
      await Promise.all([refreshSquads(), refreshOperations()]);
      setLoading(false);
    })();
  }, [session, supabase, orgId, refreshSquads, refreshOperations]);

  function resetSquadForm() {
    setSquadEditingId(null);
    setSquadCode("");
    setSquadName("");
    setSquadPassword("");
    setMapColor(DEFAULT_COLORS[squads.length % DEFAULT_COLORS.length]);
    setMapIconKey(DEFAULT_SQUAD_ICON_KEY);
    setSquadEnabled(true);
    setFormError(null);
  }

  function beginEditSquad(row: SquadRow) {
    setSquadEditingId(row.id);
    setSquadCode(row.squad_code);
    setSquadName(row.squad_name);
    setSquadPassword(row.password_hash);
    setMapColor(normalizeMapColor(row.map_color));
    setMapIconKey(normalizeSquadIconKey(row.map_icon_key));
    setSquadEnabled(row.is_enabled);
    setFormError(null);
  }

  async function handleSquadSubmit(e: FormEvent) {
    e.preventDefault();
    if (!supabase || !orgId || !canManageAnagrafica(session)) {
      return;
    }
    const code = squadCode.trim().toUpperCase();
    const name = squadName.trim();
    const pwd = squadPassword.trim();
    if (!code || !name || !pwd) {
      setFormError("Compila codice, nome e password operatore.");
      return;
    }

    setBusy(true);
    setFormError(null);
    try {
      if (squadEditingId) {
        const { error } = await supabase
          .from("squads")
          .update({
            squad_code: code,
            squad_name: name,
            password_hash: pwd,
            map_color: normalizeMapColor(mapColor),
            map_icon_key: mapIconKey,
            is_enabled: squadEnabled,
          })
          .eq("id", squadEditingId)
          .eq("organization_id", orgId);
        if (error) {
          throw error;
        }
        setToast("Operatore aggiornato.");
      } else {
        const { error } = await supabase.from("squads").insert({
          squad_code: code,
          squad_name: name,
          password_hash: pwd,
          map_color: normalizeMapColor(mapColor),
          map_icon_key: mapIconKey,
          is_enabled: squadEnabled,
          organization_id: orgId,
        });
        if (error) {
          throw error;
        }
        setToast("Operatore creato.");
      }
      resetSquadForm();
      await refreshSquads();
    } catch (err) {
      setFormError(friendlyError(err, "Errore salvataggio operatore."));
    } finally {
      setBusy(false);
    }
  }

  async function handleToggleSquadEnabled(row: SquadRow) {
    if (!supabase || !orgId) {
      return;
    }
    const nextEnabled = !row.is_enabled;
    if (
      !window.confirm(
        `${nextEnabled ? "Attivare" : "Disabilitare"} ${row.squad_code} — ${row.squad_name}?\n` +
          (nextEnabled
            ? "Potrà di nuovo fare login dall'app."
            : "Non potrà più fare login (sessioni già aperte restano fino al logout)."),
      )
    ) {
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      const { error } = await supabase
        .from("squads")
        .update({ is_enabled: nextEnabled })
        .eq("id", row.id)
        .eq("organization_id", orgId);
      if (error) {
        throw error;
      }
      if (squadEditingId === row.id) {
        setSquadEnabled(nextEnabled);
      }
      setToast(nextEnabled ? "Operatore attivato." : "Operatore disabilitato.");
      await refreshSquads();
    } catch (err) {
      setFormError(friendlyError(err, "Errore aggiornamento operatore."));
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteSquad(row: SquadRow) {
    if (!supabase || !orgId) {
      return;
    }
    if (
      !window.confirm(
        `Eliminare ${row.squad_code} — ${row.squad_name}?\n\n` +
          "Si cancella anche lo storico di questo operatore su TOC SAR (login di prova, sessioni, allarmi).\n" +
          "gestSQUADRE non viene toccato.",
      )
    ) {
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      await deleteOperatorForOrganization(supabase, row.id, orgId);
      if (squadEditingId === row.id) {
        resetSquadForm();
      }
      setToast("Operatore eliminato.");
      await refreshSquads();
    } catch (err) {
      setFormError(friendlyError(err, "Errore eliminazione operatore."));
    } finally {
      setBusy(false);
    }
  }

  async function deactivateOtherOperations(organizationId: string) {
    if (!supabase) {
      return;
    }
    const { error } = await supabase
      .from("events")
      .update({ is_active: false })
      .eq("organization_id", organizationId)
      .eq("is_active", true);
    if (error) {
      throw error;
    }
  }

  async function handleOperationSubmit(e: FormEvent) {
    e.preventDefault();
    if (!supabase || !orgId || !canManageAnagrafica(session)) {
      return;
    }
    const title = operationTitle.trim();
    if (!title) {
      setFormError("Inserisci il titolo dell'operazione.");
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      if (operationActivateNow) {
        await deactivateOtherOperations(orgId);
      }
      const { error } = await supabase.from("events").insert({
        title,
        description: operationDescription.trim() || null,
        is_active: operationActivateNow,
        organization_id: orgId,
      });
      if (error) {
        throw error;
      }
      setOperationTitle("");
      setOperationDescription("");
      setToast(operationActivateNow ? "Operazione creata e attivata." : "Operazione creata.");
      await refreshOperations();
    } catch (err) {
      setFormError(friendlyError(err, "Errore creazione operazione."));
    } finally {
      setBusy(false);
    }
  }

  async function handleActivateOperation(row: OperationRow) {
    if (!supabase || !orgId) {
      return;
    }
    if (row.is_active) {
      return;
    }
    if (
      !window.confirm(
        `Attivare «${row.title}»?\n` +
          "Le altre operazioni di questo ente verranno disattivate. L'app userà questa al login.",
      )
    ) {
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      await deactivateOtherOperations(orgId);
      const { error } = await supabase
        .from("events")
        .update({ is_active: true })
        .eq("id", row.id)
        .eq("organization_id", orgId);
      if (error) {
        throw error;
      }
      setToast("Operazione attiva aggiornata.");
      await refreshOperations();
    } catch (err) {
      setFormError(friendlyError(err, "Errore attivazione operazione."));
    } finally {
      setBusy(false);
    }
  }

  if (!authChecked || !session) {
    return <div className={styles.root}>Caricamento…</div>;
  }

  if (!canManageAnagrafica(session)) {
    return (
      <div className={styles.root}>
        <div className={styles.panel}>
          <p>Accesso in sola lettura: l&apos;anagrafica è riservata al ruolo admin.</p>
          <Link className={styles.backLink} href="/">
            ← Sala operativa
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.root}>
      <header className={styles.topBar}>
        <div>
          <h1>TOC SAR — {session.organizationCode}</h1>
          <p className={styles.sub}>
            {session.organizationName} · {session.code} ({session.name})
          </p>
        </div>
        <div className={styles.topBarActions}>
          <Link className={styles.backLink} href="/">
            ← Sala operativa
          </Link>
        </div>
      </header>

      <div className={styles.scroll}>
        <div className={styles.maxWidth}>
          {toast ? (
            <div className={styles.toast} role="status">
              {toast}
              <button type="button" onClick={() => setToast(null)}>
                Chiudi
              </button>
            </div>
          ) : null}
          {formError ? <p className={styles.error}>{formError}</p> : null}

          <section className={styles.panel}>
            <div className={styles.panelHeader}>
              <div className={styles.panelHeaderText}>
                <h2>Operatori</h2>
                <p>
                  Codice unico in questo ente. Tabella DB: <code>squads</code>.
                </p>
              </div>
            </div>
            <form className={styles.form} onSubmit={(e) => void handleSquadSubmit(e)}>
              <div className={styles.fieldRow}>
                <label>
                  Codice
                  <input
                    value={squadCode}
                    onChange={(e) => setSquadCode(e.target.value.toUpperCase())}
                    disabled={busy}
                    placeholder="Es. LUPO"
                    required
                  />
                </label>
                <label>
                  Nome e cognome
                  <input
                    value={squadName}
                    onChange={(e) => setSquadName(e.target.value)}
                    disabled={busy}
                    placeholder="Es. Roberto Ronco"
                    required
                  />
                </label>
              </div>
              <div className={styles.fieldRow}>
                <label>
                  Password app
                  <input
                    type="text"
                    value={squadPassword}
                    onChange={(e) => setSquadPassword(e.target.value)}
                    disabled={busy}
                    required
                  />
                </label>
              </div>
              <div className={styles.fieldGroup}>
                <span className={styles.iconPickerLabel}>Colore del cerchio in mappa</span>
                <div className={styles.colorRow}>
                  {DEFAULT_COLORS.map((c) => (
                    <button
                      key={c}
                      type="button"
                      className={
                        mapColor.toLowerCase() === c.toLowerCase()
                          ? `${styles.colorSwatchBtn} ${styles.colorSwatchBtnActive}`
                          : styles.colorSwatchBtn
                      }
                      style={{ background: c }}
                      onClick={() => setMapColor(c)}
                      disabled={busy}
                      aria-label={`Colore ${c}`}
                      title={c}
                    />
                  ))}
                  <input
                    className={styles.colorInput}
                    type="color"
                    value={normalizeMapColor(mapColor)}
                    onChange={(e) => setMapColor(e.target.value)}
                    disabled={busy}
                    title="Scegli un colore qualsiasi"
                  />
                </div>
              </div>
              <div className={styles.fieldGroup}>
                <span className={styles.iconPickerLabel}>Icona</span>
                <div className={styles.iconPicker} role="radiogroup" aria-label="Icona operatore">
                  {SQUAD_ICON_OPTIONS.map((opt) => (
                    <label
                      key={opt.key}
                      className={
                        mapIconKey === opt.key
                          ? `${styles.iconOption} ${styles.iconOptionActive}`
                          : styles.iconOption
                      }
                    >
                      <input
                        type="radio"
                        name="squad-icon"
                        value={opt.key}
                        checked={mapIconKey === opt.key}
                        onChange={() => setMapIconKey(opt.key)}
                        disabled={busy}
                      />
                      {opt.mapUrl ? (
                        <>
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img src={opt.mapUrl} alt="" width={40} height={40} />
                        </>
                      ) : (
                        <span
                          className={styles.circleSwatch}
                          style={{ background: mapColor }}
                          aria-hidden
                        />
                      )}
                      <span>{opt.label}</span>
                    </label>
                  ))}
                </div>
              </div>
              <label className={styles.checkLabel}>
                <input
                  type="checkbox"
                  checked={squadEnabled}
                  onChange={(e) => setSquadEnabled(e.target.checked)}
                  disabled={busy}
                />
                Abilitato al login
              </label>
              <div className={styles.formActions}>
                <button type="submit" className={styles.btnPrimary} disabled={busy}>
                  {squadEditingId ? "Salva operatore" : "Aggiungi operatore"}
                </button>
                {squadEditingId ? (
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={resetSquadForm}
                    disabled={busy}
                  >
                    Annulla
                  </button>
                ) : null}
              </div>
            </form>
            {loading ? (
              <p className={styles.empty}>Caricamento…</p>
            ) : squads.length === 0 ? (
              <p className={styles.empty}>Nessun operatore per questo ente.</p>
            ) : (
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Codice</th>
                    <th>Cognome e nome</th>
                    <th>Password</th>
                    <th>Login</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {squadsBySurname.map((row) => (
                    <tr key={row.id}>
                      <td>
                        <span className={styles.squadIconPreview}>
                          {squadIconMapUrl(row.map_icon_key) ? (
                            <>
                              {/* eslint-disable-next-line @next/next/no-img-element */}
                              <img
                                src={squadIconMapUrl(row.map_icon_key)}
                                alt=""
                                width={22}
                                height={22}
                              />
                            </>
                          ) : (
                            <span
                              className={styles.circleSwatchSm}
                              style={{
                                background: normalizeMapColor(row.map_color),
                              }}
                              aria-hidden
                            />
                          )}
                        </span>
                        {row.squad_code}
                      </td>
                      <td>{displaySurnameFirst(row.squad_name)}</td>
                      <td>{row.password_hash}</td>
                      <td>
                        <button
                          type="button"
                          className={
                            row.is_enabled ? styles.statusActive : styles.statusDisabled
                          }
                          onClick={() => void handleToggleSquadEnabled(row)}
                          disabled={busy}
                        >
                          {row.is_enabled ? "Attivo" : "Disabilitato"}
                        </button>
                      </td>
                      <td className={styles.rowActions}>
                        <button type="button" onClick={() => beginEditSquad(row)} disabled={busy}>
                          Modifica
                        </button>
                        <button
                          type="button"
                          className={styles.btnDanger}
                          onClick={() => void handleDeleteSquad(row)}
                          disabled={busy}
                        >
                          Elimina
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          <section className={styles.panel}>
            <div className={styles.panelHeader}>
              <div className={styles.panelHeaderText}>
                <h2>Operazioni</h2>
                <p>
                  Attività di questo ente (ricerca, esercitazione, turno). Una sola{" "}
                  <strong>attiva</strong>: l&apos;app si collega a quella al login.
                </p>
              </div>
            </div>
            <form className={styles.form} onSubmit={(e) => void handleOperationSubmit(e)}>
              <div className={styles.fieldRow}>
                <label>
                  Titolo
                  <input
                    value={operationTitle}
                    onChange={(e) => setOperationTitle(e.target.value)}
                    disabled={busy}
                    placeholder="Es. Ricerca Val Susa"
                    required
                  />
                </label>
                <label>
                  Note
                  <input
                    value={operationDescription}
                    onChange={(e) => setOperationDescription(e.target.value)}
                    disabled={busy}
                    placeholder="Opzionale"
                  />
                </label>
              </div>
              <label className={styles.checkLabel}>
                <input
                  type="checkbox"
                  checked={operationActivateNow}
                  onChange={(e) => setOperationActivateNow(e.target.checked)}
                  disabled={busy}
                />
                Attivala subito (disattiva le altre di questo ente)
              </label>
              <div className={styles.formActions}>
                <button type="submit" className={styles.btnPrimary} disabled={busy}>
                  Crea operazione
                </button>
              </div>
            </form>
            {operations.length === 0 ? (
              <p className={styles.empty}>
                Nessuna operazione. Senza un&apos;operazione attiva l&apos;app rifiuta il login.
              </p>
            ) : (
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Titolo</th>
                    <th>Note</th>
                    <th>Stato</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {operations.map((row) => (
                    <tr key={row.id}>
                      <td>{row.title}</td>
                      <td>{row.description || "—"}</td>
                      <td>
                        <span
                          className={row.is_active ? styles.statusActive : styles.statusDisabled}
                        >
                          {row.is_active ? "Attiva" : "Chiusa"}
                        </span>
                      </td>
                      <td className={styles.rowActions}>
                        {row.is_active ? null : (
                          <button
                            type="button"
                            className={styles.btnPrimary}
                            onClick={() => void handleActivateOperation(row)}
                            disabled={busy}
                          >
                            Attiva
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

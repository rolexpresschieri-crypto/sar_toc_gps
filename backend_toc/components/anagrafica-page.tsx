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
import {
  countOnlineForOperation,
  deleteOperationLogs,
  downloadOperationXlsx,
  loadOperationReport,
  downloadOperationPdf,
  type OperationMeta,
} from "@/lib/operation-report";
import styles from "./anagrafica.module.css";

type FolderRow = {
  id: string;
  folder_name: string;
  map_color: string;
};

type SquadRow = {
  id: string;
  squad_code: string;
  squad_name: string;
  password_hash: string;
  map_color: string | null;
  map_icon_key: string | null;
  is_enabled: boolean;
  folder_id: string | null;
};

type OperationRow = {
  id: string;
  title: string;
  description: string | null;
  is_active: boolean;
  created_at: string;
};

const DEFAULT_COLORS = ["#079B42", "#1E88E5", "#E0BE3A", "#C62828", "#6A1B9A"];
const FOLDER_NEW = "__new__";
const FOLDER_ALL = "__all__";
const DEFAULT_FOLDER_NAME = "NV_ANSMI";
const FOLDER_HAS_OPS_WARN = "Attenzione: la cartella contiene operatori.";

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
    return "Nome o codice già presente.";
  }
  if (lower.includes("foreign key") || lower.includes("violates")) {
    return "Impossibile eliminare: ci sono dati collegati. Disabilita il login invece.";
  }
  if (lower.includes("folder_id") || lower.includes("operator_folders")) {
    return "Esegui sql/operator_folders.sql in Supabase, poi ricarica.";
  }
  return msg || fallback;
}

export default function AnagraficaPage() {
  const router = useRouter();
  const [supabase, setSupabase] = useState<ReturnType<typeof getSupabaseBrowserClient>>(null);
  const [session, setSession] = useState<AdminSessionData | null>(null);
  const [authChecked, setAuthChecked] = useState(false);

  const [folders, setFolders] = useState<FolderRow[]>([]);
  const [folderChoice, setFolderChoice] = useState(FOLDER_NEW);
  const [folderName, setFolderName] = useState("");
  const [folderColor, setFolderColor] = useState(DEFAULT_COLORS[0]);

  const [squads, setSquads] = useState<SquadRow[]>([]);
  const [operations, setOperations] = useState<OperationRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const [squadEditingId, setSquadEditingId] = useState<string | null>(null);
  const [squadEditingFolderId, setSquadEditingFolderId] = useState<string | null>(null);
  const [squadCode, setSquadCode] = useState("");
  const [squadName, setSquadName] = useState("");
  const [squadPassword, setSquadPassword] = useState("");
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

  const foldersSorted = useMemo(
    () =>
      [...folders].sort((a, b) =>
        a.folder_name.localeCompare(b.folder_name, "it", { sensitivity: "base" }),
      ),
    [folders],
  );

  const selectedFolder = foldersSorted.find((f) => f.id === folderChoice) ?? null;
  const viewingAll = folderChoice === FOLDER_ALL;
  const folderSquads = viewingAll
    ? squads
    : selectedFolder
      ? squads.filter((s) => s.folder_id === selectedFolder.id)
      : [];

  function enteName(row: SquadRow): string {
    return folders.find((f) => f.id === row.folder_id)?.folder_name.trim() || "—";
  }

  function activeCount(folderId: string): number {
    return squads.filter((s) => s.folder_id === folderId && s.is_enabled).length;
  }

  function operatorsInFolder(folderId: string): SquadRow[] {
    return squads.filter((s) => s.folder_id === folderId);
  }

  const activeTotal = squads.filter((s) => s.is_enabled).length;

  const listedSquads = useMemo(() => {
    return [...folderSquads].sort((a, b) => {
      const ente = enteName(a).localeCompare(enteName(b), "it", { sensitivity: "base" });
      if (ente !== 0) {
        return ente;
      }
      return surnameSortKey(a.squad_name).localeCompare(surnameSortKey(b.squad_name), "it");
    });
  }, [folderSquads, folders]);

  const refreshFolders = useCallback(async (): Promise<FolderRow[]> => {
    if (!supabase || !orgId) {
      setFolders([]);
      return [];
    }
    const { data, error } = await supabase
      .from("operator_folders")
      .select("id, folder_name, map_color")
      .eq("organization_id", orgId);
    if (error) {
      setFormError(friendlyError(error, error.message));
      setFolders([]);
      return [];
    }
    const rows = (data ?? []) as FolderRow[];
    setFolders(rows);
    return rows;
  }, [supabase, orgId]);

  const refreshSquads = useCallback(async (): Promise<SquadRow[]> => {
    if (!supabase || !orgId) {
      setSquads([]);
      return [];
    }
    const { data, error } = await supabase
      .from("squads")
      .select("id, squad_code, squad_name, password_hash, map_color, map_icon_key, is_enabled, folder_id")
      .eq("organization_id", orgId)
      .order("squad_code", { ascending: true });
    if (error) {
      setFormError(friendlyError(error, error.message));
      return [];
    }
    const rows = (data ?? []) as SquadRow[];
    setSquads(rows);
    return rows;
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
      setFolders([]);
      return;
    }
    void (async () => {
      setLoading(true);
      setFormError(null);
      let loaded = await refreshFolders();
      let squadRows = await refreshSquads();
      await refreshOperations();

      const homeName = DEFAULT_FOLDER_NAME.toLowerCase();
      let home: FolderRow | null =
        loaded.find((f) => f.folder_name.trim().toLowerCase() === homeName) ?? null;
      const orphans = squadRows.filter((s) => !s.folder_id);
      if (!home) {
        const color = normalizeMapColor(orphans[0]?.map_color);
        const created = await supabase
          .from("operator_folders")
          .insert({
            organization_id: orgId,
            folder_name: DEFAULT_FOLDER_NAME,
            map_color: color,
          })
          .select("id, folder_name, map_color")
          .single();
        if (!created.error && created.data) {
          home = created.data as FolderRow;
        }
        loaded = await refreshFolders();
        home =
          loaded.find((f) => f.folder_name.trim().toLowerCase() === homeName) ?? home;
      }
      if (home && orphans.length > 0) {
        await supabase
          .from("squads")
          .update({ folder_id: home.id })
          .eq("organization_id", orgId)
          .is("folder_id", null);
        await refreshSquads();
      }

      setFolderChoice((prev) => {
        const keep =
          prev === FOLDER_ALL ||
          (prev !== FOLDER_NEW && loaded.some((f) => f.id === prev));
        const sorted = [...loaded].sort((a, b) =>
          a.folder_name.localeCompare(b.folder_name, "it", { sensitivity: "base" }),
        );
        const nv = loaded.find((f) => f.folder_name.trim().toLowerCase() === homeName);
        const next = keep ? prev : (nv?.id ?? sorted[0]?.id ?? FOLDER_NEW);
        const row = loaded.find((f) => f.id === next);
        if (row) {
          setFolderName(row.folder_name);
          setFolderColor(normalizeMapColor(row.map_color));
        } else if (next === FOLDER_NEW) {
          setFolderName("");
          setFolderColor(DEFAULT_COLORS[0]);
        }
        return next;
      });
      setLoading(false);
    })();
  }, [session, supabase, orgId, refreshFolders, refreshSquads, refreshOperations]);

  function resetSquadForm() {
    setSquadEditingId(null);
    setSquadEditingFolderId(null);
    setSquadCode("");
    setSquadName("");
    setSquadPassword("");
    setMapIconKey(DEFAULT_SQUAD_ICON_KEY);
    setSquadEnabled(true);
    setFormError(null);
  }

  function onPickFolder(value: string) {
    setFolderChoice(value);
    resetSquadForm();
    if (value === FOLDER_NEW) {
      setFolderName("");
      setFolderColor(DEFAULT_COLORS[folders.length % DEFAULT_COLORS.length]);
      return;
    }
    if (value === FOLDER_ALL) {
      return;
    }
    const row = folders.find((f) => f.id === value);
    if (row) {
      setFolderName(row.folder_name);
      setFolderColor(normalizeMapColor(row.map_color));
    }
  }

  function beginEditSquad(row: SquadRow) {
    setSquadEditingId(row.id);
    setSquadEditingFolderId(row.folder_id);
    setSquadCode(row.squad_code);
    setSquadName(row.squad_name);
    setSquadPassword(row.password_hash);
    setMapIconKey(normalizeSquadIconKey(row.map_icon_key));
    setSquadEnabled(row.is_enabled);
    setFormError(null);
  }

  async function handleCreateFolder(e: FormEvent) {
    e.preventDefault();
    if (!supabase || !orgId || !canManageAnagrafica(session)) {
      return;
    }
    const name = folderName.trim();
    if (!name) {
      setFormError("Inserisci il nome della cartella (associazione).");
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      const { data, error } = await supabase
        .from("operator_folders")
        .insert({
          organization_id: orgId,
          folder_name: name,
          map_color: normalizeMapColor(folderColor),
        })
        .select("id, folder_name, map_color")
        .single();
      if (error) {
        throw error;
      }
      await refreshFolders();
      if (data) {
        setFolderChoice(data.id);
        setFolderName(data.folder_name);
        setFolderColor(normalizeMapColor(data.map_color));
      }
      setToast("Cartella creata.");
    } catch (err) {
      setFormError(friendlyError(err, "Errore creazione cartella."));
    } finally {
      setBusy(false);
    }
  }

  async function handleSaveFolder(e: FormEvent) {
    e.preventDefault();
    if (!supabase || !orgId || !selectedFolder || !canManageAnagrafica(session)) {
      return;
    }
    const name = folderName.trim();
    if (!name) {
      setFormError("Inserisci il nome della cartella.");
      return;
    }
    const members = operatorsInFolder(selectedFolder.id);
    if (members.length > 0) {
      if (
        !window.confirm(
          `${FOLDER_HAS_OPS_WARN}\n\n` +
            `Salvare nome e colore per tutti gli operatori di «${selectedFolder.folder_name}» ` +
            `(${members.length}, attivi e disabilitati)?`,
        )
      ) {
        return;
      }
    }
    const color = normalizeMapColor(folderColor);
    setBusy(true);
    setFormError(null);
    try {
      const { error } = await supabase
        .from("operator_folders")
        .update({ folder_name: name, map_color: color })
        .eq("id", selectedFolder.id)
        .eq("organization_id", orgId);
      if (error) {
        throw error;
      }
      const { error: squadErr } = await supabase
        .from("squads")
        .update({ map_color: color })
        .eq("folder_id", selectedFolder.id)
        .eq("organization_id", orgId);
      if (squadErr) {
        throw squadErr;
      }
      await Promise.all([refreshFolders(), refreshSquads()]);
      setToast("Cartella aggiornata. Colore applicato agli operatori.");
    } catch (err) {
      setFormError(friendlyError(err, "Errore salvataggio cartella."));
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteFolder() {
    if (!supabase || !orgId || !selectedFolder) {
      return;
    }
    const members = operatorsInFolder(selectedFolder.id);
    const ok = members.length > 0
      ? window.confirm(
          `${FOLDER_HAS_OPS_WARN}\n\n` +
            `Eliminare la cartella «${selectedFolder.folder_name}» e tutti i suoi operatori ` +
            `(${members.length}, attivi e disabilitati), compreso lo storico TOC SAR di ciascuno?`,
        )
      : window.confirm(`Eliminare la cartella «${selectedFolder.folder_name}»?`);
    if (!ok) {
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      for (const row of members) {
        await deleteOperatorForOrganization(supabase, row.id, orgId);
      }
      const { error } = await supabase
        .from("operator_folders")
        .delete()
        .eq("id", selectedFolder.id)
        .eq("organization_id", orgId);
      if (error) {
        throw error;
      }
      const loaded = await refreshFolders();
      await refreshSquads();
      resetSquadForm();
      const sorted = [...loaded].sort((a, b) =>
        a.folder_name.localeCompare(b.folder_name, "it", { sensitivity: "base" }),
      );
      setFolderChoice(sorted[0]?.id ?? FOLDER_NEW);
      setFolderName(sorted[0]?.folder_name ?? "");
      setFolderColor(sorted[0] ? normalizeMapColor(sorted[0].map_color) : DEFAULT_COLORS[0]);
      setToast(
        members.length > 0
          ? "Cartella ed operatori eliminati."
          : "Cartella eliminata.",
      );
    } catch (err) {
      setFormError(friendlyError(err, "Errore eliminazione cartella."));
      await Promise.all([refreshFolders(), refreshSquads()]);
    } finally {
      setBusy(false);
    }
  }

  async function handleSquadSubmit(e: FormEvent) {
    e.preventDefault();
    const targetFolderId = selectedFolder?.id ?? squadEditingFolderId;
    const targetFolder = folders.find((f) => f.id === targetFolderId) ?? selectedFolder;
    if (!supabase || !orgId || !canManageAnagrafica(session) || !targetFolder) {
      setFormError("Seleziona o crea una cartella prima di inserire un operatore.");
      return;
    }
    const code = squadCode.trim().toUpperCase();
    const name = squadName.trim();
    const pwd = squadPassword.trim();
    if (!code || !name || !pwd) {
      setFormError("Compila codice, nome e password operatore.");
      return;
    }
    const color = normalizeMapColor(targetFolder.map_color);

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
            map_color: color,
            map_icon_key: mapIconKey,
            is_enabled: squadEnabled,
            folder_id: targetFolder.id,
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
          map_color: color,
          map_icon_key: mapIconKey,
          is_enabled: squadEnabled,
          organization_id: orgId,
          folder_id: targetFolder.id,
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

  async function handleCloseOperation(row: OperationRow) {
    if (!supabase || !orgId) {
      return;
    }
    if (!row.is_active) {
      return;
    }
    const online = await countOnlineForOperation(supabase, row.id);
    const othersOpen = operations.some((o) => o.id !== row.id && o.is_active);
    const parts = [
      `Chiudere l'operazione «${row.title}»?`,
      "Gli operatori non potranno più fare login su questa operazione.",
    ];
    if (online > 0) {
      parts.push(
        `Ci sono ${online} operatori ancora online: restano in sessione finché non fanno log-out (o force log-out in Config).`,
      );
    }
    if (!othersOpen) {
      parts.push(
        "Attenzione: è l'unica operazione attiva. Dopo la chiusura nessun login dall'app finché non ne attivi un'altra.",
      );
    }
    if (!window.confirm(parts.join("\n\n"))) {
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      const { error } = await supabase
        .from("events")
        .update({ is_active: false })
        .eq("id", row.id)
        .eq("organization_id", orgId);
      if (error) {
        throw error;
      }
      setToast("Operazione chiusa.");
      await refreshOperations();
    } catch (err) {
      setFormError(friendlyError(err, "Errore chiusura operazione."));
    } finally {
      setBusy(false);
    }
  }

  async function handleExportOperation(row: OperationRow, kind: "xlsx" | "pdf") {
    if (!supabase || !orgId || !session) {
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      const report = await loadOperationReport(
        supabase,
        orgId,
        session.organizationCode,
        session.organizationName,
        row as OperationMeta,
      );
      if (kind === "xlsx") {
        const kindOut = await downloadOperationXlsx(report, supabase);
        setToast(
          kindOut === "zip"
            ? "Excel e foto JPEG scaricati (ZIP: apri la cartella Foto)."
            : "Export Excel scaricato.",
        );
      } else {
        const kindOut = await downloadOperationPdf(report, supabase);
        setToast(
          kindOut === "pdf-photos" ? "PDF con foto scaricato." : "Export PDF scaricato.",
        );
      }
    } catch (err) {
      setFormError(friendlyError(err, "Errore export operazione."));
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteOperation(row: OperationRow) {
    if (!supabase || !orgId || !session) {
      return;
    }
    const online = await countOnlineForOperation(supabase, row.id);
    const warn = [
      `ELIMINARE l'operazione «${row.title}»?`,
      "Si cancella SOLO il log di questa operazione: sessioni, accessi, notifiche campo, foto (file e log), notifiche TOC, tracce.",
      "NON si tocca l'anagrafica: enti, cartelle, operatori e password restano.",
    ];
    if (row.is_active) {
      warn.push("L'operazione è ancora APERTA: verrà chiusa e poi cancellata.");
    }
    if (online > 0) {
      warn.push(
        `Ci sono ${online} operatori online su questa operazione: verranno disconnessi (la sessione viene cancellata).`,
      );
    }
    warn.push("Questa azione non si può annullare. Esporta prima PDF o Excel se ti serve lo storico.");
    if (!window.confirm(warn.join("\n\n"))) {
      return;
    }
    if (
      !window.confirm(
        `Confermi di cancellare il log di «${row.title}»?\nAnagrafica enti/operatori: NON viene cancellata.`,
      )
    ) {
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      const report = await loadOperationReport(
        supabase,
        orgId,
        session.organizationCode,
        session.organizationName,
        row as OperationMeta,
      );
      await deleteOperationLogs(supabase, orgId, row.id, report.photoPaths);
      setToast("Operazione e relativo log eliminati. Anagrafica invariata.");
      await refreshOperations();
    } catch (err) {
      setFormError(friendlyError(err, "Errore eliminazione operazione."));
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

  const colorSourceFolder =
    selectedFolder ?? folders.find((f) => f.id === squadEditingFolderId) ?? null;
  const folderColorPreview = colorSourceFolder
    ? normalizeMapColor(colorSourceFolder.map_color)
    : normalizeMapColor(folderColor);
  const showOperatorList = viewingAll || Boolean(selectedFolder);
  const showOperatorForm = Boolean(selectedFolder) || (viewingAll && Boolean(squadEditingId));

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
                <h2>Cartelle anagrafica</h2>
                <p>
                  Associazioni (es. ANSMI, CRI). Non è l&apos;ente di login. Un operatore sta in una
                  sola cartella; il colore del pin è quello della cartella.
                </p>
              </div>
            </div>
            <div className={styles.form}>
              <div className={styles.fieldRow}>
                <label>
                  Cartella
                  <select
                    value={folderChoice}
                    onChange={(e) => onPickFolder(e.target.value)}
                    disabled={busy}
                  >
                    <option value={FOLDER_ALL}>
                      Tutte le cartelle ({activeTotal})
                    </option>
                    <option value={FOLDER_NEW}>— Nuova cartella —</option>
                    {foldersSorted.map((f) => (
                      <option key={f.id} value={f.id}>
                        {f.folder_name} ({activeCount(f.id)})
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              {folderChoice === FOLDER_NEW ? (
                <form onSubmit={(e) => void handleCreateFolder(e)}>
                  <p className={styles.folderHint}>Nome associazione e colore degli operatori.</p>
                  <div className={styles.fieldRow}>
                    <label>
                      Nome cartella
                      <input
                        value={folderName}
                        onChange={(e) => setFolderName(e.target.value)}
                        disabled={busy}
                        placeholder="Es. ANSMI"
                        required
                      />
                    </label>
                  </div>
                  <FolderColorPicker
                    color={folderColor}
                    onChange={setFolderColor}
                    busy={busy}
                  />
                  <div className={styles.formActions}>
                    <button type="submit" className={styles.btnPrimary} disabled={busy}>
                      Crea cartella
                    </button>
                  </div>
                </form>
              ) : null}

              {selectedFolder ? (
                <form onSubmit={(e) => void handleSaveFolder(e)}>
                  <div className={styles.fieldRow}>
                    <label>
                      Nome cartella
                      <input
                        value={folderName}
                        onChange={(e) => setFolderName(e.target.value)}
                        disabled={busy}
                        required
                      />
                    </label>
                  </div>
                  <FolderColorPicker
                    color={folderColor}
                    onChange={setFolderColor}
                    busy={busy}
                  />
                  {operatorsInFolder(selectedFolder.id).length > 0 ? (
                    <p className={styles.folderWarn} role="status">
                      {FOLDER_HAS_OPS_WARN}
                    </p>
                  ) : null}
                  <div className={styles.formActions}>
                    <button type="submit" className={styles.btnPrimary} disabled={busy}>
                      Salva cartella
                    </button>
                    <button
                      type="button"
                      className={styles.btnDanger}
                      onClick={() => void handleDeleteFolder()}
                      disabled={busy}
                    >
                      Elimina cartella
                    </button>
                  </div>
                </form>
              ) : null}
            </div>

            {showOperatorList ? (
              <>
                <div className={styles.panelHeader}>
                  <div className={styles.panelHeaderText}>
                    <h2>
                      {viewingAll
                        ? "Operatori — Tutte le cartelle"
                        : `Operatori — ${selectedFolder?.folder_name ?? ""}`}
                    </h2>
                    <p>
                      {viewingAll
                        ? "Elenco per ente, cognome e nome. Per aggiungere un operatore seleziona una cartella."
                        : "Codice unico in questo ente TOC. Icona per operatore; colore dalla cartella."}
                    </p>
                  </div>
                </div>
                {showOperatorForm ? (
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
                              style={{ background: folderColorPreview }}
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
                ) : null}
              </>
            ) : null}

            {loading ? (
              <p className={styles.empty}>Caricamento…</p>
            ) : folderChoice === FOLDER_NEW ? (
              <p className={styles.empty}>Crea una cartella per gestire gli operatori.</p>
            ) : listedSquads.length === 0 ? (
              <p className={styles.empty}>
                {viewingAll ? "Nessun operatore." : "Nessun operatore in questa cartella."}
              </p>
            ) : (
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Codice</th>
                    <th>Cognome e nome</th>
                    <th>Password</th>
                    <th>Ente</th>
                    <th>Login</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {listedSquads.map((row) => (
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
                      <td>{enteName(row)}</td>
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
                  <strong>attiva</strong>: l&apos;app si collega a quella al login. Puoi chiudere,
                  esportare (anche se aperta) o eliminare solo il log: l&apos;anagrafica non si tocca.
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
                        {row.is_active ? (
                          <button
                            type="button"
                            onClick={() => void handleCloseOperation(row)}
                            disabled={busy}
                          >
                            Chiudi
                          </button>
                        ) : (
                          <button
                            type="button"
                            className={styles.btnPrimary}
                            onClick={() => void handleActivateOperation(row)}
                            disabled={busy}
                          >
                            Attiva
                          </button>
                        )}
                        <button
                          type="button"
                          title="Excel e JPEG delle foto (ZIP)"
                          onClick={() => void handleExportOperation(row, "xlsx")}
                          disabled={busy}
                        >
                          Excel
                        </button>
                        <button
                          type="button"
                          title="PDF con tabelle e foto"
                          onClick={() => void handleExportOperation(row, "pdf")}
                          disabled={busy}
                        >
                          PDF
                        </button>
                        <button
                          type="button"
                          className={styles.btnDanger}
                          onClick={() => void handleDeleteOperation(row)}
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
        </div>
      </div>
    </div>
  );
}

function FolderColorPicker({
  color,
  onChange,
  busy,
}: {
  color: string;
  onChange: (c: string) => void;
  busy: boolean;
}) {
  const value = normalizeMapColor(color);
  return (
    <div className={styles.fieldGroup}>
      <span className={styles.iconPickerLabel}>Colore operatori in mappa</span>
      <div className={styles.colorRow}>
        {DEFAULT_COLORS.map((c) => (
          <button
            key={c}
            type="button"
            className={
              value.toLowerCase() === c.toLowerCase()
                ? `${styles.colorSwatchBtn} ${styles.colorSwatchBtnActive}`
                : styles.colorSwatchBtn
            }
            style={{ background: c }}
            onClick={() => onChange(c)}
            disabled={busy}
            aria-label={`Colore ${c}`}
            title={c}
          />
        ))}
        <input
          className={styles.colorInput}
          type="color"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={busy}
          title="Scegli un colore qualsiasi"
        />
      </div>
    </div>
  );
}

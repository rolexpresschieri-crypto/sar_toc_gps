"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import type { SupabaseClient } from "@supabase/supabase-js";
import {
  createMissionFolder,
  deleteMissionFile,
  deleteMissionFolder,
  listMissionFiles,
  listMissionFolders,
  uploadMissionFiles,
  type MissionFile,
  type MissionFolder,
} from "@/lib/mission-gps";
import styles from "./anagrafica.module.css";

function storageHint(err: unknown): string {
  const msg = err instanceof Error ? err.message : "Operazione non riuscita.";
  const lower = msg.toLowerCase();
  if (lower.includes("row-level") || lower.includes("policy") || lower.includes("403") || lower.includes("unauthorized")) {
    return "Manca il permesso di scrittura. In Supabase SQL Editor esegui sql/mission_gps_storage_write.sql, poi riprova.";
  }
  return msg;
}

export default function MissionGpsPanel({
  supabase,
  orgCode,
}: {
  supabase: SupabaseClient;
  orgCode: string;
}) {
  const [folders, setFolders] = useState<MissionFolder[]>([]);
  const [folder, setFolder] = useState("");
  const [newFolder, setNewFolder] = useState("");
  const [files, setFiles] = useState<MissionFile[]>([]);
  const [picked, setPicked] = useState<FileList | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const refreshFolders = useCallback(async () => {
    const rows = await listMissionFolders(supabase, orgCode);
    setFolders(rows);
    setFolder((current) => current || rows[0]?.name || "");
    return rows;
  }, [supabase, orgCode]);

  const refreshFiles = useCallback(async () => {
    if (!folder) {
      setFiles([]);
      return;
    }
    setFiles(await listMissionFiles(supabase, orgCode, folder));
  }, [supabase, orgCode, folder]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const rows = await listMissionFolders(supabase, orgCode);
        if (cancelled) {
          return;
        }
        setFolders(rows);
        setFolder((current) => current || rows[0]?.name || "");
        setError(null);
      } catch (err) {
        if (!cancelled) {
          setError(storageHint(err));
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [supabase, orgCode]);

  useEffect(() => {
    if (!folder) {
      setFiles([]);
      return;
    }
    let cancelled = false;
    void listMissionFiles(supabase, orgCode, folder)
      .then((rows) => {
        if (!cancelled) {
          setFiles(rows);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(storageHint(err));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [supabase, orgCode, folder]);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const t = window.setTimeout(() => setToast(null), 3500);
    return () => window.clearTimeout(t);
  }, [toast]);

  async function handleCreateFolder(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const name = await createMissionFolder(supabase, orgCode, newFolder);
      setNewFolder("");
      await refreshFolders();
      setFolder(name);
      setToast(`Cartella ${name} creata.`);
    } catch (err) {
      setError(storageHint(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleUpload(e: FormEvent) {
    e.preventDefault();
    const list = picked ? Array.from(picked) : [];
    if (list.length === 0) {
      setError("Scegli almeno un file .trk o .wpt.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const n = await uploadMissionFiles(supabase, orgCode, folder, list);
      setPicked(null);
      await refreshFiles();
      setToast(n === 1 ? "1 file caricato." : `${n} file caricati in ${folder}.`);
    } catch (err) {
      setError(storageHint(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteFile(file: MissionFile) {
    if (!window.confirm(`Eliminare ${file.name} da ${folder}?`)) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await deleteMissionFile(supabase, file.path);
      await refreshFiles();
      setToast(`${file.name} eliminato.`);
    } catch (err) {
      setError(storageHint(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteFolder() {
    if (!folder) {
      return;
    }
    if (!window.confirm(`Eliminare la cartella ${folder} e i file che contiene?`)) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await deleteMissionFolder(supabase, orgCode, folder);
      const rows = await refreshFolders();
      setFolder(rows[0]?.name || "");
      setToast(`Cartella ${folder} eliminata.`);
    } catch (err) {
      setError(storageHint(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <div className={styles.panelHeaderText}>
          <h2>Missione GPS — {orgCode}</h2>
          <p>
            Cartelle e file nel bucket mission-gps, sotto {orgCode}. L&apos;app mostra la cartella
            solo se contiene file .trk o .wpt. Una cartella nuova compare qui subito e in app dopo
            il primo file.
          </p>
        </div>
      </div>
      {toast ? <p className={styles.toast}>{toast}</p> : null}
      {error ? <p className={styles.error}>{error}</p> : null}
      <form className={styles.form} onSubmit={(e) => void handleCreateFolder(e)}>
        <div className={styles.fieldRow}>
          <label>
            Nuova cartella
            <input
              value={newFolder}
              onChange={(e) => setNewFolder(e.target.value.toUpperCase())}
              disabled={busy}
              placeholder="Es. TRK_BARDONECCHIA o WP_OULX"
            />
          </label>
          <button type="submit" disabled={busy || !newFolder.trim()}>
            Crea cartella
          </button>
        </div>
      </form>
      <form className={styles.form} onSubmit={(e) => void handleUpload(e)}>
        <div className={styles.fieldRow}>
          <label>
            Cartella
            <select value={folder} onChange={(e) => setFolder(e.target.value)} disabled={busy || folders.length === 0}>
              {folders.length === 0 ? <option value="">Nessuna cartella</option> : null}
              {folders.map((row) => (
                <option key={row.name} value={row.name}>
                  {row.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            File .trk / .wpt
            <input
              type="file"
              accept=".trk,.wpt,text/plain"
              multiple
              disabled={busy || !folder}
              onChange={(e) => setPicked(e.target.files)}
            />
          </label>
          <button type="submit" disabled={busy || !folder}>
            Carica in {folder || "…"}
          </button>
          <button type="button" className={styles.btnDanger} disabled={busy || !folder} onClick={() => void handleDeleteFolder()}>
            Elimina cartella
          </button>
        </div>
      </form>
      {folder && files.length === 0 ? (
        <p className={styles.empty}>Nessun .trk o .wpt in {folder}. L&apos;app non mostra ancora questa cartella.</p>
      ) : files.length > 0 ? (
        <table className={styles.table}>
          <thead>
            <tr>
              <th>File</th>
              <th>Tipo</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {files.map((file) => (
              <tr key={file.path}>
                <td>{file.name}</td>
                <td>{file.name.toLowerCase().endsWith(".trk") ? "TRK" : "WP"}</td>
                <td className={styles.rowActions}>
                  <button type="button" className={styles.btnDanger} disabled={busy} onClick={() => void handleDeleteFile(file)}>
                    Elimina
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  );
}

import type { SupabaseClient } from "@supabase/supabase-js";

export type OperationMeta = {
  id: string;
  title: string;
  description: string | null;
  is_active: boolean;
  created_at: string;
};

type ReportRow = Record<string, string>;

export type OperationPhotoItem = {
  path: string;
  filename: string;
  caption: string;
};

export type OperationReport = {
  orgCode: string;
  orgName: string;
  meta: OperationMeta;
  exportedAt: string;
  onlineCount: number;
  accessi: ReportRow[];
  notificheCampo: ReportRow[];
  foto: ReportRow[];
  tocCampo: ReportRow[];
  tracce: ReportRow[];
  photoItems: OperationPhotoItem[];
  photoPaths: string[];
};

const PHOTO_BUCKET = "squad-photos";

function asRows(data: unknown): Record<string, unknown>[] {
  return (data ?? []) as Record<string, unknown>[];
}

function str(v: unknown): string {
  if (v == null) {
    return "";
  }
  return String(v);
}

function when(iso: unknown): string {
  const s = str(iso);
  if (!s) {
    return "";
  }
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) {
    return s;
  }
  return d.toLocaleString("it-IT");
}

function surnameFirst(raw: string): string {
  const name = raw.trim().replace(/\s+/g, " ");
  const parts = name.split(" ");
  if (parts.length <= 1) {
    return name;
  }
  return `${parts[parts.length - 1]} ${parts.slice(0, -1).join(" ")}`;
}

function fileSafe(raw: string): string {
  return raw
    .trim()
    .replace(/[^\p{L}\p{N}._-]+/gu, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 40) || "operazione";
}

export function operationExportBasename(orgCode: string, title: string): string {
  const day = new Date().toISOString().slice(0, 10);
  return `TOC_SAR_${orgCode}_${fileSafe(title)}_${day}`;
}

export async function countOnlineForOperation(
  supabase: SupabaseClient,
  eventId: string,
): Promise<number> {
  const { count, error } = await supabase
    .from("squad_sessions")
    .select("id", { count: "exact", head: true })
    .eq("event_id", eventId)
    .eq("is_online", true);
  if (error) {
    return 0;
  }
  return count ?? 0;
}

async function fetchRows(
  supabase: SupabaseClient,
  table: string,
  select: string,
  eventId: string,
  orgId: string | null,
  sessionIds: string[],
  orgEventIds: string[],
): Promise<Record<string, unknown>[]> {
  const take = async (
    sel: string,
    filter: { col: string; op: "eq" | "in"; val: string | string[] },
  ): Promise<Record<string, unknown>[] | null> => {
    let q = supabase.from(table).select(sel);
    q = filter.op === "eq" ? q.eq(filter.col, filter.val as string) : q.in(filter.col, filter.val as string[]);
    const { data, error } = await q.order("created_at", { ascending: true }).limit(2000);
    if (error || !data) {
      return null;
    }
    return asRows(data);
  };

  const filters: Array<{ col: string; op: "eq" | "in"; val: string | string[] }> = [
    { col: "event_id", op: "eq", val: eventId },
  ];
  if (sessionIds.length > 0) {
    filters.push({ col: "session_id", op: "in", val: sessionIds });
  }
  if (orgId) {
    filters.push({ col: "organization_id", op: "eq", val: orgId });
  }
  if (orgEventIds.length > 0) {
    filters.push({ col: "event_id", op: "in", val: orgEventIds });
  }

  const selects = [select];
  if (select.includes("mobile_dismissed_at")) {
    selects.push(select.replace(", mobile_dismissed_at", "").replace("mobile_dismissed_at,", ""));
  }

  let fallback: Record<string, unknown>[] = [];
  for (const sel of selects) {
    for (const filter of filters) {
      const rows = await take(sel, filter);
      if (!rows || rows.length === 0) {
        continue;
      }
      const scoped = rows.filter((r) => {
        const eid = str(r.event_id);
        const sid = str(r.session_id);
        return eid === eventId || (sid !== "" && sessionIds.includes(sid));
      });
      if (scoped.length > 0) {
        return scoped;
      }
      fallback = rows;
    }
  }
  return fallback;
}

export async function loadOperationReport(
  supabase: SupabaseClient,
  orgId: string,
  orgCode: string,
  orgName: string,
  meta: OperationMeta,
): Promise<OperationReport> {
  const eventId = meta.id;
  const [sessionsRes, eventsRes] = await Promise.all([
    supabase.from("squad_sessions").select("id").eq("event_id", eventId).limit(2000),
    supabase.from("events").select("id").eq("organization_id", orgId).limit(200),
  ]);
  const sessionIds = asRows(sessionsRes.error ? [] : sessionsRes.data)
    .map((r) => str(r.id))
    .filter(Boolean);
  const orgEventIds = asRows(eventsRes.error ? [] : eventsRes.data)
    .map((r) => str(r.id))
    .filter(Boolean);

  const [auth, alarms, photos, pushes, tracks, online] = await Promise.all([
    fetchRows(
      supabase,
      "squad_session_auth_logs",
      "created_at, squad_code, squad_name, action, event_id, session_id, organization_id",
      eventId,
      orgId,
      sessionIds,
      orgEventIds,
    ),
    fetchRows(
      supabase,
      "squad_alarms",
      "created_at, squad_code, squad_name, message, event_id, session_id, organization_id",
      eventId,
      orgId,
      sessionIds,
      orgEventIds,
    ),
    fetchRows(
      supabase,
      "squad_field_photo_logs",
      "created_at, squad_code, squad_name, note, latitude, longitude, storage_path, status, event_id, session_id, organization_id",
      eventId,
      orgId,
      sessionIds,
      orgEventIds,
    ),
    fetchRows(
      supabase,
      "toc_push_logs",
      "created_at, squad_code, squad_name, title, body, admin_code, mobile_dismissed_at, status, event_id, session_id",
      eventId,
      null,
      sessionIds,
      orgEventIds,
    ),
    fetchRows(
      supabase,
      "squad_track_logs",
      "created_at, squad_code, squad_name, track_name, distance_m, duration_s, n_points, elev_gain_m, elev_loss_m, event_id, session_id, organization_id",
      eventId,
      orgId,
      sessionIds,
      orgEventIds,
    ),
    countOnlineForOperation(supabase, eventId),
  ]);

  const photoItems: OperationPhotoItem[] = photos.map((r, i) => {
    const path = str(r.storage_path).trim();
    const operator = `${str(r.squad_code)} — ${surnameFirst(str(r.squad_name))}`;
    const stamp = fileSafe(when(r.created_at)).slice(0, 20);
    const filename = path
      ? `${String(i + 1).padStart(2, "0")}_${fileSafe(str(r.squad_code) || "foto")}_${stamp || "img"}.jpg`
      : "";
    const caption = [
      when(r.created_at),
      operator,
      str(r.note),
      `${str(r.latitude)} ${str(r.longitude)}`.trim(),
    ]
      .filter(Boolean)
      .join(" | ");
    return { path, filename, caption };
  });
  const photoPaths = photoItems.map((p) => p.path).filter(Boolean);

  return {
    orgCode,
    orgName,
    meta,
    exportedAt: new Date().toISOString(),
    onlineCount: online,
    accessi: auth.map((r) => ({
      Data: when(r.created_at),
      Operatore: `${str(r.squad_code)} — ${surnameFirst(str(r.squad_name))}`,
      Azione: str(r.action) === "logout" ? "Logout" : "Login",
    })),
    notificheCampo: alarms.map((r) => ({
      Data: when(r.created_at),
      Operatore: `${str(r.squad_code)} — ${surnameFirst(str(r.squad_name))}`,
      Messaggio: str(r.message),
    })),
    foto: photos.map((r, i) => ({
      Data: when(r.created_at),
      Operatore: `${str(r.squad_code)} — ${surnameFirst(str(r.squad_name))}`,
      Stato: str(r.status),
      Nota: str(r.note),
      GPS: `${str(r.latitude)} ${str(r.longitude)}`.trim(),
      File: photoItems[i]?.filename ? `Foto/${photoItems[i].filename}` : "",
    })),
    tocCampo: pushes.map((r) => ({
      Data: when(r.created_at),
      Destinatario: `${str(r.squad_code)} — ${surnameFirst(str(r.squad_name))}`,
      Da: str(r.admin_code),
      Testo: [str(r.title), str(r.body)].filter(Boolean).join(" — "),
      Reset: r.mobile_dismissed_at ? when(r.mobile_dismissed_at) : "",
      Esito: str(r.status),
    })),
    tracce: tracks.map((r) => ({
      Data: when(r.created_at),
      Operatore: `${str(r.squad_code)} — ${surnameFirst(str(r.squad_name))}`,
      Traccia: str(r.track_name),
      Distanza_m: str(r.distance_m),
      Durata_s: str(r.duration_s),
      Punti: str(r.n_points),
      Dislivello_piu_m: str(r.elev_gain_m),
      Dislivello_meno_m: str(r.elev_loss_m),
    })),
    photoItems,
    photoPaths,
  };
}

function sheetFromRows(XLSX: typeof import("xlsx"), rows: ReportRow[], headers: string[]) {
  const aoa = [headers, ...rows.map((r) => headers.map((h) => r[h] ?? ""))];
  return XLSX.utils.aoa_to_sheet(aoa);
}

function saveBlob(blob: Blob, filename: string) {
  const href = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = href;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(href);
}

async function fetchStorageBytes(
  supabase: SupabaseClient,
  path: string,
): Promise<Uint8Array | null> {
  const { data, error } = await supabase.storage.from(PHOTO_BUCKET).download(path);
  if (!error && data) {
    return new Uint8Array(await data.arrayBuffer());
  }
  const signed = await supabase.storage.from(PHOTO_BUCKET).createSignedUrl(path, 60 * 10);
  if (signed.error || !signed.data?.signedUrl) {
    return null;
  }
  const res = await fetch(signed.data.signedUrl);
  if (!res.ok) {
    return null;
  }
  return new Uint8Array(await res.arrayBuffer());
}

type LoadedPhoto = OperationPhotoItem & { bytes: Uint8Array };

async function loadPhotoFiles(
  supabase: SupabaseClient,
  items: OperationPhotoItem[],
): Promise<LoadedPhoto[]> {
  const wanted = items.filter((p) => p.path && p.filename);
  if (wanted.length === 0) {
    return [];
  }
  const loaded: LoadedPhoto[] = [];
  let next = 0;
  const workerCount = Math.min(4, wanted.length);
  async function worker() {
    while (next < wanted.length) {
      const i = next;
      next += 1;
      const item = wanted[i];
      const bytes = await fetchStorageBytes(supabase, item.path);
      if (bytes && bytes.byteLength > 0) {
        loaded.push({ ...item, bytes });
      }
    }
  }
  await Promise.all(Array.from({ length: workerCount }, () => worker()));
  loaded.sort((a, b) => a.filename.localeCompare(b.filename, "it"));
  return loaded;
}

function imageSizeMm(pxW: number, pxH: number, maxW: number, maxH: number): { w: number; h: number } {
  const wMm = (pxW * 25.4) / 96;
  const hMm = (pxH * 25.4) / 96;
  const scale = Math.min(maxW / wMm, maxH / hMm, 1);
  return { w: wMm * scale, h: hMm * scale };
}

function bytesToBlob(bytes: Uint8Array): Blob {
  const copy = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(copy).set(bytes);
  return new Blob([copy]);
}

function photoDataUrl(bytes: Uint8Array): Promise<{ dataUrl: string; w: number; h: number } | null> {
  return new Promise((resolve) => {
    const blob = bytesToBlob(bytes);
    const url = URL.createObjectURL(blob);
    const img = new Image();
    img.onload = () => {
      try {
        const max = 1600;
        const scale = Math.min(1, max / Math.max(img.naturalWidth, img.naturalHeight));
        const cw = Math.max(1, Math.round(img.naturalWidth * scale));
        const ch = Math.max(1, Math.round(img.naturalHeight * scale));
        const canvas = document.createElement("canvas");
        canvas.width = cw;
        canvas.height = ch;
        const ctx = canvas.getContext("2d");
        if (!ctx) {
          URL.revokeObjectURL(url);
          resolve(null);
          return;
        }
        ctx.drawImage(img, 0, 0, cw, ch);
        const dataUrl = canvas.toDataURL("image/jpeg", 0.82);
        URL.revokeObjectURL(url);
        resolve({ dataUrl, w: cw, h: ch });
      } catch {
        URL.revokeObjectURL(url);
        resolve(null);
      }
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      resolve(null);
    };
    img.src = url;
  });
}

export async function downloadOperationXlsx(
  report: OperationReport,
  supabase: SupabaseClient,
): Promise<"zip" | "xlsx"> {
  const [XLSX, JSZipMod] = await Promise.all([import("xlsx"), import("jszip")]);
  const JSZip = JSZipMod.default ?? JSZipMod;
  const loaded = await loadPhotoFiles(supabase, report.photoItems);
  const got = new Set(loaded.map((p) => p.filename));
  const fotoRows = report.foto.map((r, i) => {
    const item = report.photoItems[i];
    let file = "";
    if (item?.filename && got.has(item.filename)) {
      file = `Foto/${item.filename}`;
    } else if (item?.path) {
      file = "non scaricata";
    }
    return { ...r, File: file };
  });
  const base = operationExportBasename(report.orgCode, report.meta.title);
  const wb = XLSX.utils.book_new();
  const cover = [
    ["Ente", `${report.orgCode} — ${report.orgName}`],
    ["Operazione", report.meta.title],
    ["Note", report.meta.description ?? ""],
    ["Stato", report.meta.is_active ? "Aperta" : "Chiusa"],
    ["Creata", when(report.meta.created_at)],
    ["Esportata", when(report.exportedAt)],
    ["Operatori online al momento export", String(report.onlineCount)],
    ["Accessi", String(report.accessi.length)],
    ["Notifiche campo", String(report.notificheCampo.length)],
    ["Foto", String(report.foto.length)],
    [
      "Foto JPEG",
      loaded.length > 0
        ? `cartella Foto/ nello ZIP (${loaded.length} file)`
        : report.photoItems.some((p) => p.path)
          ? "nessuna foto scaricata"
          : "nessuna",
    ],
    ["TOC → campo", String(report.tocCampo.length)],
    ["Tracce", String(report.tracce.length)],
  ];
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(cover), "Operazione");
  XLSX.utils.book_append_sheet(
    wb,
    sheetFromRows(XLSX, report.accessi, ["Data", "Operatore", "Azione"]),
    "Accessi",
  );
  XLSX.utils.book_append_sheet(
    wb,
    sheetFromRows(XLSX, report.notificheCampo, ["Data", "Operatore", "Messaggio"]),
    "Notifiche campo",
  );
  XLSX.utils.book_append_sheet(
    wb,
    sheetFromRows(XLSX, fotoRows, ["Data", "Operatore", "Stato", "Nota", "GPS", "File"]),
    "Foto",
  );
  XLSX.utils.book_append_sheet(
    wb,
    sheetFromRows(XLSX, report.tocCampo, ["Data", "Destinatario", "Da", "Testo", "Reset", "Esito"]),
    "TOC campo",
  );
  XLSX.utils.book_append_sheet(
    wb,
    sheetFromRows(XLSX, report.tracce, [
      "Data",
      "Operatore",
      "Traccia",
      "Distanza_m",
      "Durata_s",
      "Punti",
      "Dislivello_piu_m",
      "Dislivello_meno_m",
    ]),
    "Tracce",
  );
  if (loaded.length === 0) {
    XLSX.writeFile(wb, `${base}.xlsx`);
    return "xlsx";
  }
  const xlsxBuf = XLSX.write(wb, { bookType: "xlsx", type: "array" }) as ArrayBuffer;
  const zip = new JSZip();
  zip.file(`${base}.xlsx`, xlsxBuf);
  for (const photo of loaded) {
    zip.file(`Foto/${photo.filename}`, photo.bytes);
  }
  const blob = await zip.generateAsync({ type: "blob" });
  saveBlob(blob, `${base}.zip`);
  return "zip";
}

function pdfText(s: string): string {
  return s
    .replace(/[—–]/g, "-")
    .replace(/[·•]/g, "-")
    .replace(/[“”]/g, '"')
    .replace(/[‘’]/g, "'")
    .replace(/→/g, "->")
    .replace(/\u0000/g, "");
}

export async function downloadOperationPdf(
  report: OperationReport,
  supabase: SupabaseClient,
): Promise<"pdf-photos" | "pdf"> {
  const [{ jsPDF }, autoTableMod] = await Promise.all([import("jspdf"), import("jspdf-autotable")]);
  const autoTable =
    autoTableMod.default ??
    (autoTableMod as { autoTable?: typeof autoTableMod.default }).autoTable;
  if (!autoTable) {
    throw new Error("Libreria PDF non disponibile.");
  }
  const loaded = await loadPhotoFiles(supabase, report.photoItems);
  const doc = new jsPDF({ orientation: "landscape", unit: "mm", format: "a4" });
  const name = `${operationExportBasename(report.orgCode, report.meta.title)}.pdf`;
  const stato = report.meta.is_active ? "Aperta" : "Chiusa";
  doc.setFontSize(16);
  doc.text(pdfText(`TOC SAR - ${report.orgCode} - ${report.meta.title}`), 14, 16);
  doc.setFontSize(10);
  const metaLines = [
    `Ente: ${report.orgName}`,
    `Stato: ${stato}`,
    `Creata: ${when(report.meta.created_at)}`,
    `Esportata: ${when(report.exportedAt)}`,
    `Note: ${report.meta.description || "-"}`,
    `Accessi ${report.accessi.length} | Notifiche campo ${report.notificheCampo.length} | Foto ${report.foto.length} (${loaded.length} immagini) | TOC campo ${report.tocCampo.length} | Tracce ${report.tracce.length}`,
  ].map(pdfText);
  doc.text(doc.splitTextToSize(metaLines.join("\n"), 270), 14, 24);

  const sections: Array<{ title: string; headers: string[]; rows: ReportRow[] }> = [
    { title: "Accessi", headers: ["Data", "Operatore", "Azione"], rows: report.accessi },
    {
      title: "Notifiche campo",
      headers: ["Data", "Operatore", "Messaggio"],
      rows: report.notificheCampo,
    },
    {
      title: "Foto",
      headers: ["Data", "Operatore", "Stato", "Nota", "GPS", "File"],
      rows: report.foto,
    },
    {
      title: "TOC campo",
      headers: ["Data", "Destinatario", "Da", "Testo", "Reset", "Esito"],
      rows: report.tocCampo,
    },
    {
      title: "Tracce",
      headers: ["Data", "Operatore", "Traccia", "Distanza_m", "Durata_s", "Punti"],
      rows: report.tracce,
    },
  ];

  let startY = 62;
  for (const section of sections) {
    doc.setFontSize(11);
    doc.text(pdfText(section.title), 14, startY);
    startY += 5;
    const body =
      section.rows.length === 0
        ? [section.headers.map((_, i) => (i === 0 ? "Nessun dato" : ""))]
        : section.rows.map((r) => section.headers.map((h) => pdfText(r[h] ?? "")));
    autoTable(doc, {
      startY,
      head: [section.headers],
      body,
      theme: "grid",
      styles: { fontSize: 8, cellPadding: 1.4, overflow: "linebreak" },
      headStyles: { fillColor: [20, 41, 93], textColor: 255, fontStyle: "bold" },
      margin: { left: 14, right: 14 },
    });
    const last = (doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable;
    startY = (last?.finalY ?? startY) + 12;
    if (startY > 185) {
      doc.addPage();
      startY = 18;
    }
  }

  for (const photo of loaded) {
    doc.addPage();
    doc.setFontSize(11);
    const cap = doc.splitTextToSize(pdfText(`Foto - ${photo.caption}`), 269);
    doc.text(cap, 14, 16);
    const capH = Array.isArray(cap) ? cap.length * 5 : 5;
    const img = await photoDataUrl(photo.bytes);
    if (!img) {
      doc.setFontSize(10);
      doc.text("Immagine non disponibile.", 14, 16 + capH + 4);
      continue;
    }
    const box = imageSizeMm(img.w, img.h, 269, Math.max(40, 178 - capH));
    doc.addImage(img.dataUrl, "JPEG", 14, 16 + capH + 2, box.w, box.h);
  }

  doc.save(name);
  return loaded.length > 0 ? "pdf-photos" : "pdf";
}

export async function deleteOperationLogs(
  supabase: SupabaseClient,
  orgId: string,
  eventId: string,
  photoPaths: string[],
): Promise<void> {
  const uniquePaths = [...new Set(photoPaths)];
  for (let i = 0; i < uniquePaths.length; i += 40) {
    const chunk = uniquePaths.slice(i, i + 40);
    const { error } = await supabase.storage.from("squad-photos").remove(chunk);
    if (error) {
      throw error;
    }
  }
  const { error: trackErr } = await supabase.from("squad_track_logs").delete().eq("event_id", eventId);
  if (trackErr && !/does not exist|schema cache/i.test(trackErr.message)) {
    throw trackErr;
  }
  const { error } = await supabase.from("events").delete().eq("id", eventId).eq("organization_id", orgId);
  if (error) {
    throw error;
  }
}

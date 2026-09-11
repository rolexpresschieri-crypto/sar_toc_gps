"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { addAckedPhotoId, readAckedPhotoIds } from "@/lib/acked-photos";
import { liveSquadsFromSessionRows, type LiveSquad } from "@/lib/live-squads";
import { getSupabaseBrowserClient } from "@/lib/supabase-browser";

const POLL_MS = 4000;

export type PendingAlarm = {
  id: string;
  squad_code: string;
  squad_name: string;
  message: string | null;
  created_at: string;
  session_id: string;
};

export type FieldPhoto = {
  id: string;
  squad_code: string;
  squad_name: string;
  latitude: number;
  longitude: number;
  accuracy_m: number | null;
  note: string | null;
  storage_path: string | null;
  created_at: string;
  session_id: string;
};

export type OutboundPush = {
  id: string;
  squad_code: string;
  squad_name: string;
  title: string;
  body: string;
  admin_code: string;
  created_at: string;
  session_id: string | null;
};

export function useLiveFeed(
  supabase: ReturnType<typeof getSupabaseBrowserClient>,
  orgId: string | null,
  enabled: boolean,
) {
  const [squads, setSquads] = useState<LiveSquad[]>([]);
  const [alarms, setAlarms] = useState<PendingAlarm[]>([]);
  const [photos, setPhotos] = useState<FieldPhoto[]>([]);
  const [outbound, setOutbound] = useState<OutboundPush[]>([]);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const localAcked = useRef(readAckedPhotoIds());

  const loadLive = useCallback(async () => {
    if (!supabase || !orgId) {
      setSquads([]);
      setAlarms([]);
      setPhotos([]);
      setOutbound([]);
      return;
    }
    const photoSelect =
      "id, squad_code, squad_name, latitude, longitude, accuracy_m, note, storage_path, created_at, session_id";
    const [sessionsRes, alarmsRes, photosRes, eventsRes] = await Promise.all([
      supabase
        .from("squad_sessions")
        .select(
          "id, event_id, squad_id, is_online, login_at, last_latitude, last_longitude, last_accuracy, last_fix_at, organization_id, squads(squad_code, squad_name, map_color, map_icon_key)",
        )
        .eq("is_online", true)
        .eq("organization_id", orgId),
      supabase
        .from("squad_alarms")
        .select("id, squad_code, squad_name, message, created_at, session_id")
        .eq("organization_id", orgId)
        .is("acknowledged_at", null)
        .order("created_at", { ascending: false })
        .limit(40),
      supabase
        .from("squad_field_photo_logs")
        .select(photoSelect)
        .eq("organization_id", orgId)
        .eq("status", "inviato")
        .is("acknowledged_at", null)
        .order("created_at", { ascending: false })
        .limit(40),
      supabase.from("events").select("id").eq("organization_id", orgId),
    ]);

    if (sessionsRes.error) {
      setStatusMessage(sessionsRes.error.message);
    } else {
      setSquads(liveSquadsFromSessionRows((sessionsRes.data ?? []) as Record<string, unknown>[]));
      setStatusMessage(null);
    }
    if (alarmsRes.error) {
      setStatusMessage(alarmsRes.error.message);
    } else {
      setAlarms((alarmsRes.data ?? []) as PendingAlarm[]);
    }

    let photoRows = photosRes.data;
    if (photosRes.error) {
      const retry = await supabase
        .from("squad_field_photo_logs")
        .select(photoSelect)
        .eq("organization_id", orgId)
        .eq("status", "inviato")
        .order("created_at", { ascending: false })
        .limit(40);
      photoRows = retry.error ? [] : retry.data;
    }

    localAcked.current = readAckedPhotoIds();
    const nextPhotos: FieldPhoto[] = [];
    for (const raw of (photoRows ?? []) as Record<string, unknown>[]) {
      const id = String(raw.id);
      if (localAcked.current.has(id)) {
        continue;
      }
      nextPhotos.push({
        id,
        squad_code: String(raw.squad_code ?? ""),
        squad_name: String(raw.squad_name ?? ""),
        latitude: Number(raw.latitude),
        longitude: Number(raw.longitude),
        accuracy_m: raw.accuracy_m == null ? null : Number(raw.accuracy_m),
        note: typeof raw.note === "string" ? raw.note : null,
        storage_path: typeof raw.storage_path === "string" ? raw.storage_path : null,
        created_at: String(raw.created_at),
        session_id: String(raw.session_id),
      });
    }
    setPhotos(nextPhotos);

    const eventIds = ((eventsRes.data ?? []) as Record<string, unknown>[])
      .map((e) => String(e.id))
      .filter(Boolean);
    if (eventIds.length === 0) {
      setOutbound([]);
    } else {
      const pushRes = await supabase
        .from("toc_push_logs")
        .select("id, squad_code, squad_name, title, body, admin_code, created_at, session_id")
        .in("event_id", eventIds)
        .is("mobile_dismissed_at", null)
        .order("created_at", { ascending: false })
        .limit(80);
      if (pushRes.error) {
        setOutbound([]);
      } else {
        setOutbound(
          ((pushRes.data ?? []) as Record<string, unknown>[]).map((raw) => ({
            id: String(raw.id),
            squad_code: String(raw.squad_code ?? ""),
            squad_name: String(raw.squad_name ?? ""),
            title: String(raw.title ?? ""),
            body: String(raw.body ?? ""),
            admin_code: String(raw.admin_code ?? ""),
            created_at: String(raw.created_at),
            session_id: raw.session_id == null ? null : String(raw.session_id),
          })),
        );
      }
    }
  }, [supabase, orgId]);

  useEffect(() => {
    if (!enabled || !supabase || !orgId) {
      return;
    }
    void loadLive();
    const t = window.setInterval(() => void loadLive(), POLL_MS);
    return () => window.clearInterval(t);
  }, [enabled, supabase, orgId, loadLive]);

  return {
    squads,
    alarms,
    setAlarms,
    photos,
    setPhotos,
    outbound,
    statusMessage,
    setStatusMessage,
    loadLive,
  };
}

export function markPhotoAckedLocally(id: string) {
  addAckedPhotoId(id);
}

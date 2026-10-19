const CHANNEL = "toc-sar-mission-selection";

function selectionKey(orgCode: string): string {
  return `toc_sar_mission_selection:${orgCode.trim()}`;
}

function parsePaths(raw: string | null): string[] {
  if (!raw) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((item): item is string => typeof item === "string");
  } catch {
    return [];
  }
}

export function readMissionSelection(orgCode: string): Set<string> {
  if (typeof window === "undefined" || !orgCode.trim()) {
    return new Set();
  }
  return new Set(parsePaths(window.localStorage.getItem(selectionKey(orgCode))));
}

export function writeMissionSelection(orgCode: string, paths: Iterable<string>): void {
  if (typeof window === "undefined" || !orgCode.trim()) {
    return;
  }
  const list = [...paths];
  const org = orgCode.trim();
  window.localStorage.setItem(selectionKey(org), JSON.stringify(list));
  try {
    const channel = new BroadcastChannel(CHANNEL);
    channel.postMessage({ org, paths: list });
    channel.close();
  } catch {
    // La finestra gemella riceve comunque l'evento storage.
  }
}

export function subscribeMissionSelection(
  orgCode: string,
  onChange: (paths: string[]) => void,
): () => void {
  const org = orgCode.trim();
  if (typeof window === "undefined" || !org) {
    return () => {};
  }
  const key = selectionKey(org);
  const onStorage = (event: StorageEvent) => {
    if (event.key !== key) {
      return;
    }
    onChange(parsePaths(event.newValue));
  };
  window.addEventListener("storage", onStorage);
  let channel: BroadcastChannel | null = null;
  try {
    channel = new BroadcastChannel(CHANNEL);
    channel.onmessage = (event: MessageEvent<{ org?: string; paths?: unknown }>) => {
      const data = event.data;
      if (!data || data.org !== org || !Array.isArray(data.paths)) {
        return;
      }
      onChange(data.paths.filter((item): item is string => typeof item === "string"));
    };
  } catch {
    channel = null;
  }
  return () => {
    window.removeEventListener("storage", onStorage);
    channel?.close();
  };
}

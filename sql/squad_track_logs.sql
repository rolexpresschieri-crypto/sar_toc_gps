-- Log riepilogo TRK registrata in app (quando l'operatore salva la traccia).
-- Eseguire dopo sql/organizations.sql.

create table if not exists squad_track_logs (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references organizations(id) on delete restrict,
  event_id uuid references events(id) on delete set null,
  session_id uuid references squad_sessions(id) on delete set null,
  squad_id uuid not null references squads(id) on delete restrict,
  squad_code text not null,
  squad_name text not null,
  track_name text not null,
  distance_m double precision not null,
  duration_s double precision not null,
  avg_speed_kmh double precision,
  elev_gain_m double precision not null default 0,
  elev_loss_m double precision not null default 0,
  n_points int not null,
  created_at timestamptz not null default now()
);

create index if not exists squad_track_logs_org_created_idx
  on squad_track_logs (organization_id, created_at desc);

alter table squad_track_logs enable row level security;

drop policy if exists "gest anon insert squad_track_logs" on squad_track_logs;
create policy "gest anon insert squad_track_logs"
  on squad_track_logs for insert to anon with check (true);

drop policy if exists "gest anon read squad_track_logs" on squad_track_logs;
create policy "gest anon read squad_track_logs"
  on squad_track_logs for select to anon using (true);

comment on table squad_track_logs is
  'Riepilogo TRK salvata da app: distanza, durata, velocità media, dislivello +/-.';

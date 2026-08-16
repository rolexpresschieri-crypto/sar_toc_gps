-- Visibilità pin in mappa per gli altri operatori.
-- LUPO (admin) vede tutti; gli altri vedono solo peer_visible = true (anche LUPO, se il flag è acceso).
-- Eseguire in Supabase → SQL Editor prima di usare APK 1.0.40+.

alter table squad_sessions
  add column if not exists peer_visible boolean not null default false;

create or replace view active_squad_summaries as
select
  ss.id as session_id,
  ss.event_id,
  ss.squad_id,
  s.squad_code,
  s.squad_name,
  s.map_color,
  ss.is_online,
  ss.login_at,
  ss.last_latitude,
  ss.last_longitude,
  ss.last_accuracy,
  ss.last_fix_at,
  s.map_icon_key,
  ss.peer_visible
from squad_sessions ss
join squads s on s.id = ss.squad_id
where ss.is_online = true;

grant select on active_squad_summaries to anon, authenticated, service_role;

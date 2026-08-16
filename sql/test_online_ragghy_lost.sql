-- Test: metti online RAGGHY e LOST con coordinate fisse.
-- Eseguire tutto in Supabase → SQL Editor (puoi lanciare tutto il file).

-- 1) Aggiorna sessioni già online
with evt as (
  select id as event_id
  from events
  where is_active = true
  order by created_at desc nulls last
  limit 1
),
targets (squad_code, lat, lon) as (
  values
    ('RAGGHY', 44.944165::double precision, 6.807381::double precision),
    ('LOST',   44.951343::double precision, 6.806179::double precision)
),
ops as (
  select s.id as squad_id, t.lat, t.lon, e.event_id
  from targets t
  join squads s on upper(s.squad_code) = upper(t.squad_code)
  cross join evt e
)
update squad_sessions ss
set
  is_online = true,
  logout_at = null,
  last_latitude = ops.lat,
  last_longitude = ops.lon,
  last_accuracy = 10,
  last_fix_at = now(),
  peer_visible = true
from ops
where ss.event_id = ops.event_id
  and ss.squad_id = ops.squad_id
  and ss.is_online = true;

-- 2) Inserisci se manca sessione online
with evt as (
  select id as event_id
  from events
  where is_active = true
  order by created_at desc nulls last
  limit 1
),
targets (squad_code, lat, lon) as (
  values
    ('RAGGHY', 44.944165::double precision, 6.807381::double precision),
    ('LOST',   44.951343::double precision, 6.806179::double precision)
),
ops as (
  select s.id as squad_id, t.lat, t.lon, e.event_id
  from targets t
  join squads s on upper(s.squad_code) = upper(t.squad_code)
  cross join evt e
)
insert into squad_sessions (
  event_id, squad_id, is_online, login_at, logout_at,
  last_latitude, last_longitude, last_accuracy, last_fix_at, peer_visible
)
select
  ops.event_id,
  ops.squad_id,
  true,
  now(),
  null,
  ops.lat,
  ops.lon,
  10,
  now(),
  true
from ops
where not exists (
  select 1
  from squad_sessions ss
  where ss.event_id = ops.event_id
    and ss.squad_id = ops.squad_id
    and ss.is_online = true
);

-- 3) Verifica
select
  s.squad_code,
  ss.is_online,
  ss.peer_visible,
  ss.last_latitude,
  ss.last_longitude,
  ss.last_fix_at
from squad_sessions ss
join squads s on s.id = ss.squad_id
where upper(s.squad_code) in ('RAGGHY', 'LOST')
  and ss.is_online = true
order by s.squad_code;

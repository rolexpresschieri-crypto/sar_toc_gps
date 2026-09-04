-- Seed operatori demo SAR (tabella DB: squads = anagrafica operatori)
-- Eseguire dopo organizations.sql e organization_login.sql (unique per ente).

insert into squads (
  squad_code, squad_name, password_hash, map_color, map_icon_key, is_enabled, organization_id
)
select
  v.squad_code,
  v.squad_name,
  v.password_hash,
  v.map_color,
  v.map_icon_key,
  true,
  o.id
from (
  values
    ('LUPO', 'Roberto Ronco', '1234', '#079B42', 'squadre_a_piedi'),
    ('OP001', 'Operatore Alpha', '1234', '#079B42', 'squadre_a_piedi'),
    ('OP002', 'Operatore Bravo', '1234', '#E53935', 'ambulanza')
) as v(squad_code, squad_name, password_hash, map_color, map_icon_key)
join organizations o on o.org_code = 'NVANSMI'
on conflict (organization_id, squad_code) do update set
  squad_name = excluded.squad_name,
  password_hash = excluded.password_hash,
  map_color = excluded.map_color,
  map_icon_key = excluded.map_icon_key,
  is_enabled = excluded.is_enabled;

-- Assicura un evento attivo per NVANSMI
insert into events (title, description, is_active, organization_id)
select 'Evento operativo TOC SAR', 'Evento demo', true, o.id
from organizations o
where o.org_code = 'NVANSMI'
  and not exists (
    select 1 from events e
    where e.is_active = true and e.organization_id = o.id
  );

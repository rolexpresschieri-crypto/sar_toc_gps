-- Forza log-out operatore RAGGHY (sessioni ancora is_online = true).
-- Eseguire in Supabase → SQL Editor.

update squad_sessions ss
set
  is_online = false,
  logout_at = coalesce(logout_at, now())
from squads s
where ss.squad_id = s.id
  and upper(s.squad_code) = 'RAGGHY'
  and ss.is_online = true;

-- Verifica
select ss.id, s.squad_code, ss.is_online, ss.login_at, ss.logout_at, ss.last_fix_at
from squad_sessions ss
join squads s on s.id = ss.squad_id
where upper(s.squad_code) = 'RAGGHY'
order by ss.login_at desc
limit 10;

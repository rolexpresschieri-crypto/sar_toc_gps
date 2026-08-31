-- Cancella anagrafica + tutto lo storico di un operatore (squad_code).
-- Eseguire in Supabase → SQL Editor (tutto lo script in un colpo).
--
-- INPUT: modifica SOLO queste due righe, poi Run.
select set_config('toc.delete_squad_code', 'INSERISCI_SQUAD_CODE', false);  -- es. 'RAGGHY'
select set_config('toc.delete_allow_lupo', 'false', false);                 -- 'true' solo per LUPO

do $$
declare
  v_code text := upper(trim(current_setting('toc.delete_squad_code')));
  v_allow_lupo boolean := lower(current_setting('toc.delete_allow_lupo')) in ('true', 't', '1');
  v_id uuid;
  v_name text;
  n int;
begin
  if v_code is null or v_code = '' or v_code = 'INSERISCI_SQUAD_CODE' then
    raise exception 'Imposta toc.delete_squad_code in cima (es. ''RAGGHY'').';
  end if;
  if v_code = 'LUPO' and not v_allow_lupo then
    raise exception 'Cancellazione di LUPO bloccata. Imposta toc.delete_allow_lupo a true se sei sicuro.';
  end if;

  select s.id, s.squad_name
    into v_id, v_name
  from squads s
  where upper(s.squad_code) = v_code;

  if v_id is null then
    raise notice 'Nessuna riga in squads per %. Pulisco comunque i log orfani con quel codice.', v_code;
  else
    raise notice 'Cancello % (% / id %)', v_code, coalesce(v_name, '?'), v_id;
  end if;

  -- Log allarmi automatici (testo, o FK verso allarmi/sessioni)
  if to_regclass('public.alarm_auto_notify_logs') is not null then
    delete from alarm_auto_notify_logs
    where upper(squad_code) = v_code
       or upper(recipient_squad_code) = v_code;
    get diagnostics n = row_count;
    raise notice 'alarm_auto_notify_logs: %', n;
  end if;

  -- Allarmi (ON DELETE RESTRICT su squads)
  if v_id is not null then
    delete from squad_alarms where squad_id = v_id;
    get diagnostics n = row_count;
    raise notice 'squad_alarms: %', n;
  end if;

  -- Sessioni (RESTRICT su squads). Cascade: FCM, route, dismiss mobile
  if v_id is not null then
    delete from squad_sessions where squad_id = v_id;
    get diagnostics n = row_count;
    raise notice 'squad_sessions: %', n;
  end if;

  -- Log / routing che tengono solo il testo squad_code
  if to_regclass('public.alarm_notify_routing') is not null then
    delete from alarm_notify_routing where upper(recipient_squad_code) = v_code;
    get diagnostics n = row_count;
    raise notice 'alarm_notify_routing: %', n;
  end if;

  delete from toc_push_logs
  where upper(coalesce(squad_code, '')) = v_code
     or (v_id is not null and squad_id = v_id);
  get diagnostics n = row_count;
  raise notice 'toc_push_logs: %', n;

  if to_regclass('public.toc_mission_close_logs') is not null then
    delete from toc_mission_close_logs
    where upper(coalesce(squad_code, '')) = v_code
       or (v_id is not null and squad_id = v_id);
    get diagnostics n = row_count;
    raise notice 'toc_mission_close_logs: %', n;
  end if;

  if to_regclass('public.toc_mission_force_dismiss_logs') is not null then
    delete from toc_mission_force_dismiss_logs where upper(squad_code) = v_code;
    get diagnostics n = row_count;
    raise notice 'toc_mission_force_dismiss_logs: %', n;
  end if;

  -- Anagrafica: cascade su foto, auth logs, dismiss rimanenti
  if v_id is not null then
    delete from squads where id = v_id;
    get diagnostics n = row_count;
    raise notice 'squads: %', n;
  end if;

  raise notice 'Fine. Codice % rimosso.', v_code;
end $$;

-- Verifica: deve tornare 0 su squads
select 'squads' as tab, count(*)::int as n
from squads
where upper(squad_code) = upper(current_setting('toc.delete_squad_code'));

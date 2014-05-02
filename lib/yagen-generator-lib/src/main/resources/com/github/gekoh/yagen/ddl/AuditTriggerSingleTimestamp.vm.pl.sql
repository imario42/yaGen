create or replace
trigger ${triggerName}
before insert or update on ${liveTableName}
for each row
begin
  :new.${last_modified_at} := current_timestamp;
end;
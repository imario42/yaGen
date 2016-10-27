create or replace
trigger ${triggerName}
before insert or update on ${liveTableName}
for each row
#if( $last_modified_by )
declare
  user_name ${liveTableName}.${last_modified_by}%type;
begin
#*
-- re-/set the modifier / modifierdate values ONLY if no different modifier since last update
-- has been set in incoming value - this enables entity listeners to work properly as their values will be kept
-- without the need of connection transforming
-- 2013-07-05: changed to only use the modifier from entity listeners as the timestamp has to be provided by DB -> PTA timeshift issues *#
  ##    -- detect change made in application based on the modified timestamp, in this case the app will provide the modifier name within the update statement
  user_name := case when :new.${last_modified_at} is not null and (:old.${last_modified_at} is null or :new.${last_modified_at} <> :old.${last_modified_at} )
                      then :new.${last_modified_by} end;

  if user_name is null or lower(user_name)='unknown'
  then
    user_name:=substr(coalesce(sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER')), 1, 20);
  end if;

  user_name:= substr(user || case when user_name is not null and lower(user) <> lower(user_name) then ' ('||user_name||')' end, 1, 35);

  :new.${last_modified_by} := user_name;
#else
begin
#end
  :new.${last_modified_at} := current_timestamp;
end;
create trigger ${triggerName}
before #if(${operation} == 'I') insert #elseif (${operation} == 'U') update #else delete #end on ${liveTableName}
referencing #if( ${operation} != 'D' ) new as new #end #if( ${operation} != 'I' ) old as old #end
for each row
begin atomic
#if( $last_modified_by )
  declare user_name varchar(35);

#*
-- re-/set the modifier / modifierdate values ONLY if no different modifier since last update
-- has been set in incoming value - this enables entity listeners to work properly as their values will be kept
-- without the need of connection transforming
-- 2013-07-05: changed to only use the modifier from entity listeners as the timestamp has to be provided by DB -> PTA timeshift issues *#
##    -- detect change made in application based on the modified timestamp, in this case the app will provide the modifier name within the update statement
  set user_name = case when new.${last_modified_at} is not null and (old.${last_modified_at} is null or new.${last_modified_at} <> old.${last_modified_at} )
                       then new.${last_modified_by} end;

  if user_name is null or lower(user_name)='unknown'
  then
    set user_name = substr(coalesce(sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER')), 1, 20);
  end if;

  set user_name = substr(user || case when user_name is not null and lower(user) <> lower(user_name) then ' ('||user_name||')' end, 1, 35);

  set new.${last_modified_by} = user_name;
#end
  set new.${last_modified_at} = current_timestamp_9();
end;
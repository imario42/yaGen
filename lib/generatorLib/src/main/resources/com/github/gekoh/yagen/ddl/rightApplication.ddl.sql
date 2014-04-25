#set( $is_postgres = ${dialect.getClass().getSimpleName().toLowerCase().contains('postgres')} )
#set( $is_oracle = ${dialect.getClass().getSimpleName().toLowerCase().contains('oracle')} )
#set( $is_oracleXE = ${dialect.getClass().getSimpleName().toLowerCase().contains('oraclexe')} )
#set( $is_hsql = ${dialect.getClass().getSimpleName().toLowerCase().contains('hsql')} )

#if( $is_oracle )
------- CreateDDL statement separator -------
/**
 * creation of IAM_RIGHT_APPLICATION as we are using the view RIGHT_APPLICATION_V for the relation Right.applications
 */
create table IAM_RIGHT_APPLICATION (APPLICATION_UUID varchar2(32 char), RIGHT_UUID varchar2(32 char) constraint ira_right_uuid_NN not null, created_at timestamp, created_by varchar2(35 char), last_modified_at timestamp, last_modified_by varchar2(35 char), constraint ira_RIGHTUUID_PK primary key (RIGHT_UUID));

------- CreateDDL statement separator -------
-- creating audit trigger
create or replace
trigger iam_right_application_ATR
before insert or update on iam_right_application
for each row
begin
  if INSERTING then
    :new.created_at := systimestamp;
    :new.created_by := coalesce(
        :new.created_by,
        sys_context('USERENV','CLIENT_IDENTIFIER'),
        sys_context('USERENV','OS_USER'),
        user
    );
    :new.last_modified_at := null;
    :new.last_modified_by := null;
  elsif UPDATING then
    :new.created_at := :old.created_at;
    :new.created_by := :old.created_by;
    if not(
        :new.last_modified_at is not null and (:old.last_modified_at is null or :new.last_modified_at <> :old.last_modified_at )
    ) then
      :new.last_modified_by := coalesce(
        sys_context('USERENV','CLIENT_IDENTIFIER'),
        sys_context('USERENV','OS_USER'),
        user
    );
    end if;
    :new.last_modified_at := systimestamp;
  end if;
end;
/

------- CreateDDL statement separator -------
-- adding history table due to annotation com.github.gekoh.yagen.api.TemporalEntity on entity of table IAM_RIGHT_APPLICATION
create table IAM_RIGHT_APPLICATION_HST (hst_uuid varchar2(32 char), operation char(1) constraint iraH_operation_NN not null, APPLICATION_UUID varchar2(32 char), RIGHT_UUID varchar2(32 char), created_at timestamp, created_by varchar2(35 char), last_modified_at timestamp, last_modified_by varchar2(35 char), transaction_timestamp timestamp constraint iraH_transaction_timestamp_NN not null, invalidated_at timestamp, constraint iraH_hstuuid_PK primary key (hst_uuid), constraint iraH_RIGHT_trans_UI unique (RIGHT_UUID, transaction_timestamp));

------- CreateDDL statement separator -------
-- creating trigger for inserting history rows from table IAM_RIGHT_APPLICATION
create or replace
trigger iam_right_application_htr
after insert or update or delete on iam_right_application
for each row
declare
  transaction_timestamp_found timestamp;
  hst_operation HST_MODIFIED_ROW.operation%TYPE:=case when inserting then 'I'
                                                      when updating then 'U'
                                                      when deleting then 'D' end;
  live_rowid rowid:=nvl(
        :new.rowid,
        :old.rowid
    );
  hst_uuid_used IAM_RIGHT_APPLICATION_HST.hst_uuid%TYPE:=sys_guid(

    );
  hst_modified_by varchar2(
        35
    ):=coalesce(
        sys_context('USERENV','CLIENT_IDENTIFIER'),
        sys_context('USERENV','OS_USER'),
        user
    );
  hst_table_name varchar2(
        30
    ):=upper(
        'iam_right_application'
    );
begin

  if inserting or deleting
  or (
        (:NEW.application_uuid is null and :OLD.application_uuid is not null) or
      (:NEW.application_uuid is not null and :OLD.application_uuid is null) or
      :NEW.application_uuid!=:OLD.application_uuid
    )
  or (
        (:NEW.right_uuid is null and :OLD.right_uuid is not null) or
      (:NEW.right_uuid is not null and :OLD.right_uuid is null) or
      :NEW.right_uuid!=:OLD.right_uuid
    )
  then

    begin
      select transaction_timestamp into transaction_timestamp_found
      from HST_CURRENT_TRANSACTION
      where transaction_id=DBMS_TRANSACTION.LOCAL_TRANSACTION_ID;
    exception when no_data_found then
      transaction_timestamp_found:=current_timestamp;
      insert into HST_CURRENT_TRANSACTION (
        transaction_id,
        transaction_timestamp
    )
        values (
        DBMS_TRANSACTION.LOCAL_TRANSACTION_ID,
        transaction_timestamp_found
    );
    end;

    if :new.rowid<>:old.rowid then
      update hst_modified_row set row_id=:new.rowid
        where table_name=hst_table_name
          and row_id=:old.rowid;
    end if;

    begin
      insert into hst_modified_row values (
        hst_table_name,
        live_rowid,
        hst_operation,
        hst_uuid_used
    );

      -- invalidate latest entry in history table
      update IAM_RIGHT_APPLICATION_HST h set invalidated_at=transaction_timestamp_found
        where
          right_uuid=coalesce(
        :new.right_uuid,
        :old.right_uuid
    ) and
          invalidated_at is null;
    exception when dup_val_on_index then
      declare
        prev_operation IAM_RIGHT_APPLICATION_HST.operation%TYPE;
      begin
        select operation, hst_uuid into prev_operation, hst_uuid_used
          from hst_modified_row
         where table_name=hst_table_name
           and row_id=live_rowid;

        if prev_operation='I' and hst_operation='U' then
          hst_operation:='I';
        elsif prev_operation='I' and hst_operation='D' then
          hst_operation:=null;
        end if;
      end;

      delete IAM_RIGHT_APPLICATION_HST where hst_uuid=hst_uuid_used;

    end;

    if hst_operation is not null then
      insert into IAM_RIGHT_APPLICATION_HST (
         right_uuid,
          created_by,
          last_modified_by,
          last_modified_at,
          application_uuid,
          created_at,
         hst_uuid,
        operation,
        transaction_timestamp
    )
      values (
         coalesce(:new.right_uuid, :old.right_uuid),
           :new.created_by,
           coalesce(:new.last_modified_by, hst_modified_by),
           :new.last_modified_at,
            :new.application_uuid,
            :new.created_at,
          hst_uuid_used,
        hst_operation,
        transaction_timestamp_found
    );
    end if;

  end if;

end;
/

------- CreateDDL statement separator -------
-- only add FK if there is an application table (additional RIM schema has a view in place)
begin
  for data in (
    select
    'alter table IAM_RIGHT_APPLICATION
        add constraint ira_applicationuuid_FK
        foreign key (APPLICATION_UUID)
        references '||table_name||'
        on delete cascade' stmt
    from tabs
    where lower(table_name)='application'
  ) loop
    execute immediate data.stmt;
  end loop;
end;
/

------- CreateDDL statement separator -------
create index ira_applicationuuid_IX on iam_right_application (application_uuid);

------- CreateDDL statement separator -------
alter table IAM_RIGHT_APPLICATION add constraint ira_rightuuid_FK foreign key (RIGHT_UUID) references iam_right on delete cascade;


------- CreateDDL statement separator -------
/**
 * created by Marta 2013-02-14, used by REM
 * modified by Georg 2013-03-28
 */
create or replace view RIGHT_APPLICATION_V
        (right_uuid
        ,right_name
        ,right_key
        ,right_type
        ,right_valid_from
        ,right_valid_to
        ,is_delegable
        ,is_active
        ,right_assignability
        ,max_delegations
        ,right_access_type
        ,sensitivity_cd
        ,description
        ,application_uuid
        ,ictonumber
        ,application_name )
as
select   uuid         right_uuid
        ,right_name
        ,right_key
        ,right_type
        ,valid_from   right_valid_from
        ,valid_to     right_valid_to
        ,is_delegable
        ,is_active
        ,right_assignability
        ,max_delegations
        ,right_access_type
        ,sensitivity_cd
        ,description
        ,application_uuid
        ,ictonumber
        ,application_name
from
( with right_app AS ( select RIGHT_UUID, APPLICATION_UUID
                      from (
                        select APPLICATION_UUID, RIGHT_UUID, row_number() over (partition by RIGHT_UUID order by priority) rnk
                        from (
                            select a.UUID APPLICATION_UUID, r.UUID RIGHT_UUID, 1 PRIORITY
                            from
                              IAM_RIGHT r
                              join SECURITY_LOGIC s on r.SECURITY_LOGIC_UUID=s.UUID
                              join APPLICATION_GROUP g on s.APPLICATION_GROUP_UUID=g.UUID
                              join APPLICATION a on a.APPLICATION_GROUP_UUID=g.UUID
                            union all
                            select APPLICATION_UUID, RIGHT_UUID, 2 PRIORITY
                            from
                              IAM_RIGHT_APPLICATION
                            union all
                            select a.UUID APPLICATION_UUID, r.UUID RIGHT_UUID, 3 PRIORITY
                            from
                              IAM_RIGHT r,
                              APPLICATION a
                            where
                              a.ICTONUMBER='ICTO-tbd'
                          )
                        )
                      where rnk=1 ),
       rg AS (SELECT r.uuid
                    ,r.right_name
                    ,r.right_key
                    ,r.right_type
                    ,r.valid_from
                    ,r.valid_to
                    ,r.is_delegable
                    ,r.is_active
                    ,r.right_assignability
                    ,r.max_delegations
                    ,r.right_access_type
                    ,r.sensitivity_cd
                    ,r.security_logic_uuid
                    ,d.description
                from iam_right r
                left outer join iam_right_i18n d on r.uuid=d.right_uuid and d.language_cd = 'EN' )
        select       rg.uuid
                    ,rg.right_name
                    ,rg.right_key
                    ,rg.right_type
                    ,rg.valid_from
                    ,rg.valid_to
                    ,rg.is_delegable
                    ,rg.is_active
                    ,rg.right_assignability
                    ,rg.max_delegations
                    ,rg.right_access_type
                    ,rg.sensitivity_cd
                    ,rg.description
                    ,a.uuid    application_uuid
                    ,a.ictonumber
                    ,a.application_name
            from rg, right_app ra , application a
            where rg.uuid = ra.right_uuid
            and   a.uuid = ra.application_uuid
);

------- CreateDDL statement separator -------
-- create instead of trigger which does nothing avoid errors when hibernate tries to delete a right
create or replace trigger RIGHT_APPLICATION_V_TRG
 instead of insert or update or delete on RIGHT_APPLICATION_V
 begin
  null;
 end;
/
#end

#if( $is_hsql )
------- CreateDDL statement separator -------
create table IAM_RIGHT_APPLICATION (APPLICATION_UUID varchar(32), RIGHT_UUID varchar(32) constraint ira_right_uuid_NN not null, created_at timestamp, created_by varchar(35), last_modified_at timestamp, last_modified_by varchar(35), constraint ira_RIGHTUUID_PK primary key (RIGHT_UUID));

------- CreateDDL statement separator -------
create trigger iam_right_application_atI
before insert on iam_right_application
referencing new as new
for each row
  begin atomic
  set new.created_at = current_timestamp;
    set new.created_by = coalesce(new.created_by, sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER'), user);
    set new.last_modified_at = null;
    set new.last_modified_by = null;
  end;
/

------- CreateDDL statement separator -------
create trigger iam_right_application_atU
before update on iam_right_application
referencing new as new old as old
for each row
  begin atomic
  set new.created_at = old.created_at;
    set new.created_by = old.created_by;
    if not(new.last_modified_at is not null and (old.last_modified_at is null or new.last_modified_at <> old.last_modified_at )) then
      set new.last_modified_by = coalesce(sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER'), user);
    end if;
    set new.last_modified_at = current_timestamp;
  end;
/
#end

#if( $is_postgres )
------- CreateDDL statement separator -------
create table IAM_RIGHT_APPLICATION (APPLICATION_UUID varchar(32), RIGHT_UUID varchar(32) constraint ira_right_uuid_NN not null, created_at timestamp, created_by varchar(35), last_modified_at timestamp, last_modified_by varchar(35), constraint ira_RIGHTUUID_PK primary key (RIGHT_UUID));

------- CreateDDL statement separator -------
create trigger iam_right_application_atr BEFORE INSERT OR UPDATE ON iam_right_application FOR EACH ROW EXECUTE PROCEDURE audit_trigger_function();
#end

#if( !$is_oracle )
------- CreateDDL statement separator -------
create or replace view RIGHT_APPLICATION_V
(right_uuid
  ,right_name
  ,right_key
  ,right_type
  ,right_valid_from
  ,right_valid_to
  ,is_delegable
  ,is_active
  ,right_assignability
  ,max_delegations
  ,right_access_type
  ,sensitivity_cd
  ,description
  ,application_uuid
  ,ictonumber
  ,application_name )
as
  select   uuid         right_uuid
    ,right_name
    ,right_key
    ,right_type
    ,valid_from   right_valid_from
    ,valid_to     right_valid_to
    ,is_delegable
    ,is_active
    ,right_assignability
    ,max_delegations
    ,right_access_type
    ,sensitivity_cd
    ,description
    ,application_uuid
    ,ictonumber
    ,application_name
  from
    ( with right_app AS ( select min(a.UUID) APPLICATION_UUID, r.UUID RIGHT_UUID
                          from
                            IAM_RIGHT r
                            join SECURITY_LOGIC s on r.SECURITY_LOGIC_UUID=s.UUID
                            join APPLICATION_GROUP g on s.APPLICATION_GROUP_UUID=g.UUID
                            join APPLICATION a on a.APPLICATION_GROUP_UUID=g.UUID
                          group by r.UUID
                        ),
          rg AS (SELECT r.uuid
                   ,r.right_name
                   ,r.right_key
                   ,r.right_type
                   ,r.valid_from
                   ,r.valid_to
                   ,r.is_delegable
                   ,r.is_active
                   ,r.right_assignability
                   ,r.max_delegations
                   ,r.right_access_type
                   ,r.sensitivity_cd
                   ,r.security_logic_uuid
                   ,d.description
                 from iam_right r
                   left outer join iam_right_i18n d on r.uuid=d.right_uuid and d.language_cd = 'EN' )
      select       rg.uuid
        ,rg.right_name
        ,rg.right_key
        ,rg.right_type
        ,rg.valid_from
        ,rg.valid_to
        ,rg.is_delegable
        ,rg.is_active
        ,rg.right_assignability
        ,rg.max_delegations
        ,rg.right_access_type
        ,rg.sensitivity_cd
        ,rg.description
        ,a.uuid    application_uuid
        ,a.ictonumber
        ,a.application_name
      from rg, right_app ra , application a
      where rg.uuid = ra.right_uuid
            and   a.uuid = ra.application_uuid
    ) t;
#end

#if( $is_postgres )
------- CreateDDL statement separator -------
create or replace function RIGHT_APPLICATION_V_TRG_FUNC()
  returns trigger AS $$
begin
  return new;
end;
$$ LANGUAGE 'plpgsql';
/

------- CreateDDL statement separator -------
create trigger RIGHT_APPLICATION_V_TRG
instead of insert or update or delete on RIGHT_APPLICATION_V
for each row
execute procedure RIGHT_APPLICATION_V_TRG_FUNC();
#end
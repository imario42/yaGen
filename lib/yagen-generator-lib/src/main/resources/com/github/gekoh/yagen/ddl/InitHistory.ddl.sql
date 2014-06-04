#set( $is_postgres = ${dialect.getClass().getSimpleName().toLowerCase().contains('postgres')} )
#set( $is_oracle = ${dialect.getClass().getSimpleName().toLowerCase().contains('oracle')} )
#set( $is_oracleXE = ${dialect.getClass().getSimpleName().toLowerCase().contains('oraclexe')} )
#set( $is_hsql = ${dialect.getClass().getSimpleName().toLowerCase().contains('hsql')} )

#if( $is_oracle )
------- CreateDDL statement separator -------
create global temporary table HST_CURRENT_TRANSACTION (
  transaction_id varchar2(4000 char),
  transaction_timestamp timestamp,
  constraint hsttr_transaction_id_PK primary key (transaction_id)
);

------- CreateDDL statement separator -------
create global temporary table HST_MODIFIED_ROW (
  table_name varchar2(30 char),
  row_id rowid,
  operation varchar2(1 char),
  hst_uuid varchar2(32 char),
  constraint hstmod_rowid_tablename_PK primary key (row_id, table_name)
);

------- CreateDDL statement separator -------
create procedure set_transaction_timestamp(timestamp_in in timestamp) is
begin
  insert into HST_CURRENT_TRANSACTION (TRANSACTION_ID, TRANSACTION_TIMESTAMP)
    values (DBMS_TRANSACTION.LOCAL_TRANSACTION_ID, timestamp_in);
end;
/
#end

#if( $is_hsql )
------- CreateDDL statement separator -------
DROP SCHEMA PUBLIC CASCADE;

------- CreateDDL statement separator -------
CREATE FUNCTION sys_guid() RETURNS char(32)
LANGUAGE JAVA DETERMINISTIC NO SQL
EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.createUUID'
;

------- CreateDDL statement separator -------
CREATE FUNCTION sys_context(namespace varchar(255), param varchar(255)) RETURNS varchar(255)
LANGUAGE JAVA DETERMINISTIC NO SQL
EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.getSysContext'
;

------- CreateDDL statement separator -------
create global temporary table HST_CURRENT_TRANSACTION (
  transaction_id bigint,
  transaction_timestamp timestamp,
  constraint hsttr_transaction_id_PK primary key (transaction_id)
);

------- CreateDDL statement separator -------
create global temporary table HST_MODIFIED_ROW (
  table_name varchar(30),
  row_id varchar(64),
  operation char(1),
  hst_uuid varchar(32),
  constraint hstmod_rowid_tablename_PK primary key (row_id, table_name)
);

------- CreateDDL statement separator -------
create procedure set_transaction_timestamp(in timestamp_in timestamp)
begin atomic
  insert into HST_CURRENT_TRANSACTION (TRANSACTION_ID, TRANSACTION_TIMESTAMP)
    values (TRANSACTION_ID(), timestamp_in);
end;
/
#end

#if( $is_postgres )
------- CreateDDL statement separator -------
/*
  java execution requires a certain postgres setup
  1) java initialized
    - installed package (e.g. postgresql-9.1-pljava-gcj)
    - set trusted language (UPDATE pg_language SET lanpltrusted = true WHERE lanname LIKE 'java';)
  2) DBHelper jar to be loaded
    - e.g.: select sqlj.install_jar('file:///<path-to>.jar', 'dbhelper', true);
  3) classpath set
    - e.g.: select sqlj.set_classpath('public', 'dbhelper');
 */
CREATE FUNCTION sys_guid() RETURNS VARCHAR
AS 'com.github.gekoh.yagen.util.DBHelper.createUUID'
LANGUAGE java;

------- CreateDDL statement separator -------
CREATE FUNCTION sys_context(namespace varchar,parameter varchar) RETURNS VARCHAR AS $$
begin
  if 'USERENV' = namespace then
    if 'DB_NAME' = parameter then
      return 'PostgreSQL';
    elsif 'OS_USER' = parameter then
      return null;
    elsif 'CLIENT_IDENTIFIER' = parameter then
      return session_user;
    end if;
  end if;
end;
$$ LANGUAGE PLPGSQL;

------- CreateDDL statement separator -------
CREATE VIEW dual AS
  select cast('X' as varchar) DUMMY
;

------- CreateDDL statement separator -------
CREATE FUNCTION audit_trigger_function()
  RETURNS trigger AS $$
BEGIN
  if TG_OP = 'INSERT' then
    new.created_at := current_timestamp;
    new.created_by := coalesce(new.created_by, sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER'), user);
    new.last_modified_at := null;
    new.last_modified_by := null;
  elsif TG_OP = 'UPDATE' then
    new.created_at := old.created_at;
    new.created_by := old.created_by;
    if not(new.last_modified_at is not null and (old.last_modified_at is null or new.last_modified_at <> old.last_modified_at )) then
      new.last_modified_by := coalesce(sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER'), user);
    end if;
    new.last_modified_at := current_timestamp;
  end if;
  return new;
END;
$$ LANGUAGE 'plpgsql';
/

------- CreateDDL statement separator -------
create table HST_CURRENT_TRANSACTION (transaction_id bigint, transaction_timestamp timestamp, constraint hsttr_transaction_id_PK primary key (transaction_id));

------- CreateDDL statement separator -------
create table HST_MODIFIED_ROW (transaction_id bigint, table_name varchar(30), row_id varchar(64), operation char(1), hst_uuid varchar(32), constraint hstmod_rowid_tablename_PK primary key (transaction_id, row_id, table_name));

------- CreateDDL statement separator -------
create index hstmod_rowid_tablename_IX on HST_MODIFIED_ROW (row_id, table_name);

------- CreateDDL statement separator -------
CREATE or REPLACE FUNCTION HST_CURRENT_TRANSACTION_TRG_FCT()
  RETURNS trigger AS $$
begin
  delete from HST_CURRENT_TRANSACTION where transaction_id=new.transaction_id;
  delete from HST_MODIFIED_ROW where transaction_id=new.transaction_id;
  return old;
end;
$$ LANGUAGE 'plpgsql';
/

------- CreateDDL statement separator -------
/*
  This simulates the behaviour of global temporary tables since there is only
  temporary tables available in postgresql.
  So on commit we remove the inserted rows via trigger function HST_CURRENT_TRANSACTION_TRG_FCT.
 */
create constraint trigger HST_CURRENT_TRANSACTION_TRG after insert
on HST_CURRENT_TRANSACTION initially deferred for each row
execute procedure HST_CURRENT_TRANSACTION_TRG_FCT();

#end

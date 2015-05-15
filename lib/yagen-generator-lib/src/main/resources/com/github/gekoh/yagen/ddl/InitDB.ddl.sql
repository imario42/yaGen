#set( $is_postgres = ${dialect.getClass().getSimpleName().toLowerCase().contains('postgres')} )
#set( $is_oracle = ${dialect.getClass().getSimpleName().toLowerCase().contains('oracle')} )
#set( $is_oracleXE = ${dialect.getClass().getSimpleName().toLowerCase().contains('oraclexe')} )
#set( $is_hsql = ${dialect.getClass().getSimpleName().toLowerCase().contains('hsql')} )

#if( $is_hsql )
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

#end

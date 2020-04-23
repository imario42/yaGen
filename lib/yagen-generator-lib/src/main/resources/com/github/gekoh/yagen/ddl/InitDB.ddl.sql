#if( $is_oracle )
------- CreateDDL statement separator -------
create or replace function get_audit_user(client_user_in in varchar2) return varchar2 is
  user_name varchar2(50):=substr(client_user_in, 1, 50);
begin
  if lower(user_name)='unknown' then
    user_name:=null;
  end if;
  user_name:=substr(regexp_replace(regexp_replace(coalesce(user_name, sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER')),
             '^(.*)@.*$', '\1'),
             '^.*CN=([^, ]*).*$', '\1'),
    1, 20);
  return user || case when user_name is not null and lower(user) <> lower(user_name) then ' ('||user_name||')' end;
end;
/
#end

#if( $is_hsql )
------- CreateDDL statement separator -------
CREATE FUNCTION sys_guid() RETURNS char(32)
LANGUAGE JAVA DETERMINISTIC NO SQL
EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.createUUID'
;

------- CreateDDL statement separator -------
CREATE FUNCTION sys_context_internal(namespace varchar(255), param varchar(255)) RETURNS varchar(255)
LANGUAGE JAVA DETERMINISTIC NO SQL
EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.getSysContext'
;

------- CreateDDL statement separator -------
create global temporary table SESSION_VARIABLES (
    NAME VARCHAR(50),
    VALUE VARCHAR(50),
    constraint SESS_VAR_PK primary key (NAME)
);

------- CreateDDL statement separator -------
CREATE FUNCTION sys_context(namespace varchar(255), param varchar(255)) RETURNS varchar(255)
begin atomic
  declare var_found VARCHAR(50);
  declare exit handler for SQLEXCEPTION
      return sys_context_internal(namespace, param);
  if namespace='USERENV' and param='CLIENT_IDENTIFIER' then
    select value into var_found from SESSION_VARIABLES where NAME='CLIENT_IDENTIFIER';
    return var_found;
  end if;
  return sys_context_internal(namespace, param);
end;

------- CreateDDL statement separator -------
CREATE FUNCTION systimestamp_9() RETURNS timestamp(9)
  LANGUAGE JAVA DETERMINISTIC NO SQL
  EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.getCurrentTimestamp'
;

------- CreateDDL statement separator -------
CREATE FUNCTION regexp_like(s VARCHAR(4000), regexp VARCHAR(500))
  RETURNS BOOLEAN
  LANGUAGE JAVA DETERMINISTIC NO SQL
  EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.regexpLike'
;

------- CreateDDL statement separator -------
CREATE FUNCTION regexp_like(s VARCHAR(4000), regexp VARCHAR(500), flags VARCHAR(10))
  RETURNS BOOLEAN
  LANGUAGE JAVA DETERMINISTIC NO SQL
  EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.regexpLikeFlags'
;

------- CreateDDL statement separator -------
CREATE FUNCTION is_bypassed(object_name VARCHAR(100))
  RETURNS BOOLEAN
  LANGUAGE JAVA DETERMINISTIC NO SQL
  EXTERNAL NAME 'CLASSPATH:com.github.gekoh.yagen.util.DBHelper.isBypassed'
;

------- CreateDDL statement separator -------
CREATE FUNCTION get_audit_user(client_user_in VARCHAR(50)) RETURNS varchar(50)
begin atomic
  declare user_name VARCHAR(50);
  set user_name = client_user_in;

  if lower(user_name)='unknown' then
    set user_name = null;
  end if;

  set user_name = substr(regexp_replace(regexp_replace(coalesce(user_name, sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER')),
    '^(.*)@.*$', '\1'),
    '^.*CN=([^, ]*).*$', '\1'),
    1, 20);
  return user || case when user_name is not null and lower(user) <> lower(user_name) then ' ('||user_name||')' end;
end;
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
CREATE FUNCTION get_audit_user() RETURNS VARCHAR AS $$
begin
  return regexp_replace(regexp_replace(coalesce(sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER')),
             '^(.*)@.*$', '\1'),
             '^.*CN=([^, ]*).*$', '\1');
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
    new.created_at := systimestamp;
    new.created_by := coalesce(new.created_by, sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER'), user);
    new.last_modified_at := null;
    new.last_modified_by := null;
  elsif TG_OP = 'UPDATE' then
    new.created_at := old.created_at;
    new.created_by := old.created_by;
    if not(new.last_modified_at is not null and (old.last_modified_at is null or new.last_modified_at <> old.last_modified_at )) then
      new.last_modified_by := coalesce(sys_context('USERENV','CLIENT_IDENTIFIER'), sys_context('USERENV','OS_USER'), user);
    end if;
    new.last_modified_at := systimestamp;
  end if;
  return new;
END;
$$ LANGUAGE 'plpgsql';
/

#end

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

CREATE OR REPLACE FUNCTION ${objectName}()
  RETURNS trigger
AS $$
declare
  violation_cnt integer;
begin
  select count(1) into violation_cnt
  from ${tableName} t
    join hst_modified_row m on m.row_id=''#foreach( $pkColumn in $pkColumns )||t.${pkColumn}#{end}

  where m.table_name=upper('${tableName}')
        and m.transaction_id=txid_current()
        and not(${declaration});
  if violation_cnt>0 then
    RAISE EXCEPTION 'check constraint ${constraintName} violated!';
  end if;
  return new;
end;
$$ LANGUAGE 'plpgsql'
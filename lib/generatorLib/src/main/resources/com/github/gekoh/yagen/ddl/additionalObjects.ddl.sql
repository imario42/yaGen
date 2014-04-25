#set( $is_postgres = ${dialect.getClass().getSimpleName().toLowerCase().contains('postgres')} )
#set( $is_oracle = ${dialect.getClass().getSimpleName().toLowerCase().contains('oracle')} )
#set( $is_oracleXE = ${dialect.getClass().getSimpleName().toLowerCase().contains('oraclexe')} )
#set( $is_hsql = ${dialect.getClass().getSimpleName().toLowerCase().contains('hsql')} )

#if( $is_oracle )
------- CreateDDL statement separator -------
-- set unique function based index to initially deferred, only possible with virtual column
alter table ENGAGEMENT add (ACTIVE_OR_ORGUNITUUID varchar2(32 char) GENERATED ALWAYS AS (decode(IS_ACTIVE, 1, 'ACTIVE', ORG_UNIT_UUID)) VIRTUAL, constraint EGMT_USER_ACTIVE_OU_UK unique (INTERNAL_USER_UUID, ACTIVE_OR_ORGUNITUUID) deferrable initially deferred);

------- CreateDDL statement separator -------
create or replace
trigger disevqi_created_at_trg
before insert on dis_event_queue_item
for each row
  begin
    :new.CREATED_AT := systimestamp;
  end;
/

create or replace view IND_SCOPE_VALUE_SET_SEP_HST_V as
with
last_value_set as (
  select
    HST_UUID,
    OPERATION,
    UUID,
    CREATED_AT,
    CREATED_BY,
    LAST_MODIFIED_AT,
    LAST_MODIFIED_BY,
    VERSION,
    SCOPE_VALUE_SET_KEY,
    SCOPE_TEMPLATE_UUID,
    TRANSACTION_TIMESTAMP,
    INVALIDATED_AT
  from (
    select
      HST_UUID,
      OPERATION,
      UUID,
      CREATED_AT,
      CREATED_BY,
      LAST_MODIFIED_AT,
      LAST_MODIFIED_BY,
      VERSION,
      SCOPE_VALUE_SET_KEY,
      SCOPE_TEMPLATE_UUID,
      TRANSACTION_TIMESTAMP,
      INVALIDATED_AT,
      rank() over (partition by UUID order by TRANSACTION_TIMESTAMP desc) rnk
    from
      SCOPE_TEMPLATE_VALUE_SET_HST
  )
  where rnk=1
),
hst_ref_ind_value_set as (
  select
    UUID,
    CREATED_AT,
    CREATED_BY,
    LAST_MODIFIED_AT,
    LAST_MODIFIED_BY,
    VERSION,
    IS_ACTIVE,
    VALID_FROM,
    VALID_TO,
    VALUE_SET_KEY,
    MASTER_SYSTEM,
    ASSIGNMENT_UUID,
    SCOPE_TEMPLATE_VALUE_SET_UUID
  from (
    select
      UUID,
      CREATED_AT,
      CREATED_BY,
      LAST_MODIFIED_AT,
      LAST_MODIFIED_BY,
      VERSION,
      IS_ACTIVE,
      VALID_FROM,
      VALID_TO,
      VALUE_SET_KEY,
      MASTER_SYSTEM,
      ASSIGNMENT_UUID,
      nth_value(SCOPE_TEMPLATE_VALUE_SET_UUID, 1) ignore nulls over (partition by UUID order by TRANSACTION_TIMESTAMP desc rows between current row and unbounded following) SCOPE_TEMPLATE_VALUE_SET_UUID,
      rank() over (partition by UUID order by TRANSACTION_TIMESTAMP desc) rnk
    from
      IND_SCOPE_VALUE_SET_HST svs
  )
  where rnk=1
)
select
  isvs.UUID,
  isvs.CREATED_AT,
  isvs.CREATED_BY,
  isvs.LAST_MODIFIED_AT,
  isvs.LAST_MODIFIED_BY,
  isvs.VERSION,
  isvs.IS_ACTIVE,
  isvs.VALID_FROM,
  isvs.VALID_TO,
  isvs.VALUE_SET_KEY,
  isvs.MASTER_SYSTEM,
  isvs.ASSIGNMENT_UUID,
  isvs.SCOPE_TEMPLATE_VALUE_SET_UUID,
  stvs.HST_UUID SCOPE_TEMPL_VALUE_SET_HST_UUID
from
  hst_ref_ind_value_set isvs
  left join last_value_set stvs on isvs.SCOPE_TEMPLATE_VALUE_SET_UUID=stvs.UUID
;
#end

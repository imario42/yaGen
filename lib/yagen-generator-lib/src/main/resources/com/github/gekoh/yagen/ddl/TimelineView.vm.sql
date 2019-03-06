#if( $is_oracle )
create or replace view ${viewName} as
with
changelog as (
${changelogQueryString}
),
mut as (
#foreach( $column in $columns )
#if( $foreach.count > 1 )
	union all
#end
	select
    l.uuid CHANGELOG_UUID,
	  l.created_at CHANGE_TIMESTAMP,
	  l.mutation_type,
	  l.entity_uuid UUID,
    l.CREATED_AT,
    l.CREATED_BY,
    l.LAST_MODIFIED_AT,
    l.LAST_MODIFIED_BY
#foreach( $selColumn in $columns )
#if( $selColumn == $column )
#if( $numericColumns.contains($selColumn) )
  , cast(l.new_value as $numericColumnDefinitions[$selColumn]) ${selColumn}
#elseif( $clobColumns.contains($selColumn) )
  , nvl(l.new_long_value, empty_clob()) ${selColumn}
#else
  , nvl(l.new_value, chr(0)) ${selColumn}
#end
#else
  , null ${selColumn}
#end
#end
	from
	  changelog l
	where
	  l.TABLE_NAME='${tableName.toUpperCase()}'
	  and l.COLUMN_NAME='${column.toUpperCase()}'
#end
)
select
  TML_UUID,
	CREATED_AT,
	CREATED_BY,
	LAST_MODIFIED_AT,
	LAST_MODIFIED_BY,
  TML_PHASE,
  UUID,
#foreach( $column in $columns )
#if( $numericColumns.contains($column) )
  ${column},
#elseif( $clobColumns.contains($column) )
  case when dbms_lob.compare(${column}, empty_clob()) = 0 then null else ${column} end ${column},
#else
  decode(${column}, chr(0), null, ${column}) ${column},
#end
#end
  EFFECTIVE_TIMESTAMP_FROM,
  EFFECTIVE_TIMESTAMP_TO
from (
  select
    TML_UUID,
    TML_PHASE,
    UUID,
		first_value(CREATED_AT) ignore nulls over (partition by UUID order by EFFECTIVE_TIMESTAMP_FROM nulls first) CREATED_AT,
		first_value(CREATED_BY) ignore nulls over (partition by UUID order by EFFECTIVE_TIMESTAMP_FROM nulls first) CREATED_BY,
		last_value(LAST_MODIFIED_AT) ignore nulls over (partition by UUID order by EFFECTIVE_TIMESTAMP_FROM nulls first) LAST_MODIFIED_AT,
		last_value(LAST_MODIFIED_BY) ignore nulls over (partition by UUID order by EFFECTIVE_TIMESTAMP_FROM nulls first) LAST_MODIFIED_BY,
#foreach( $column in $columns )
#if( $clobColumns.contains($column) )
  -- to work around an obvious serious bug in Oracle we need to define the following coalesce expression exactly this way
  -- any use of the with object 'mut' will result in a completely weird force to null behaviour of selected Clob expressions like FILTER_EXPRESSION
    coalesce(${column},
      (
        select coalesce(NEW_LONG_VALUE, empty_clob()) ${column}
        from changelog mi
        where ENTITY_UUID=o_tml.UUID
        and CREATED_AT=(select max(CREATED_AT) from changelog where ENTITY_UUID=mi.ENTITY_UUID and CREATED_AT<o_tml.EFFECTIVE_TIMESTAMP_FROM and COLUMN_NAME=mi.COLUMN_NAME)
        and COLUMN_NAME='${column.toUpperCase()}'
      ),
      (
        select ${column}
        from ${tableName}
        where UUID=o_tml.UUID
      )
    ) ${column},
#else
		nth_value(${column}, 1) ignore nulls over (partition by UUID order by EFFECTIVE_TIMESTAMP_FROM desc nulls last rows between current row and unbounded following) ${column},
#end
#end
    EFFECTIVE_TIMESTAMP_FROM,
    EFFECTIVE_TIMESTAMP_TO
  from (
    select
      #foreach( $pkColumn in $pkColumns )e.${pkColumn}||'-'||#{end}coalesce(e.LAST_MODIFIED_AT,e.CREATED_AT) TML_UUID,
      'L' TML_PHASE,
      #foreach( $pkColumn in $pkColumns )#if($foreach.count>1)||'-'||#{end}e.${pkColumn}#{end} UUID,
      e.CREATED_AT,
      e.CREATED_BY,
      e.LAST_MODIFIED_AT,
      e.LAST_MODIFIED_BY,
#foreach( $column in $columns )
      e.${column},
#end
      null EFFECTIVE_TIMESTAMP_FROM,
      (select min(CREATED_AT) from changelog where uuid=#foreach($pkColumn in $pkColumns)#if($foreach.count>1)||'-'||#{end}e.${pkColumn}#{end} and TABLE_NAME='${tableName.toUpperCase()}') EFFECTIVE_TIMESTAMP_TO
      -- following was in place before, but due to a serious oracle bug this was rewritten
      -- with the following line in place oracle seems to silently drop any existing CLOB column value under certain circumstances
      --(select min(CHANGE_TIMESTAMP) from mut where uuid=#foreach($pkColumn in $pkColumns)#if($foreach.count>1)||'-'||#{end}e.${pkColumn}#{end}) EFFECTIVE_TIMESTAMP_TO
    from
      ${tableName} e
    union all
    select
      CHANGELOG_UUID TML_UUID,
      'M' TML_PHASE,
      UUID,
      CREATED_AT,
      CREATED_BY,
      LAST_MODIFIED_AT,
      LAST_MODIFIED_BY,
#foreach( $column in $columns )
      ${column},
#end
      EFFECTIVE_TIMESTAMP_FROM,
      EFFECTIVE_TIMESTAMP_TO
    from (
        select
          m.CHANGELOG_UUID,
          m.MUTATION_TYPE,
          m.UUID,
          m.CREATED_AT,
          m.CREATED_BY,
          m.LAST_MODIFIED_AT,
          m.LAST_MODIFIED_BY,
#foreach( $column in $columns )
          m.${column},
#end
          m.CHANGE_TIMESTAMP EFFECTIVE_TIMESTAMP_FROM,
          lead(m.CHANGE_TIMESTAMP) over (partition by m.UUID order by decode(m.MUTATION_TYPE,'NEW',1,'TERMINATE',3,2), m.CHANGE_TIMESTAMP) EFFECTIVE_TIMESTAMP_TO
        from
          mut m
      )
    where
      -- terminations will only give EFFECTIVE_TIMESTAMP_TO value in previous row
      MUTATION_TYPE<>'TERMINATE'
  ) o_tml
)
;
#else
------- CreateDDL statement separator -------
drop view CHANGELOG_ROW_V if exists;

------- CreateDDL statement separator -------
create view CHANGELOG_ROW_V as
	select l.*, rownum() row_nr
	from
		dsm_changelog l
	join dsm_changeset s on l.CHANGESET_UUID=s.uuid
	where
		s.CHANGESET_STATUS='READY_TO_DEPLOY'
;

------- CreateDDL statement separator -------
create or replace view ${viewName} as
with mut as (
#foreach( $column in $columns )
#if( $foreach.count > 1 )
	union all
#end
	select
	  l.row_nr,
		l.uuid CHANGELOG_UUID,
	  l.created_at CHANGE_TIMESTAMP,
	  l.mutation_type,
	  l.entity_uuid UUID,
    l.CREATED_AT,
    l.CREATED_BY,
    l.LAST_MODIFIED_AT,
    l.LAST_MODIFIED_BY
#foreach( $selColumn in $columns )
#if( $selColumn == $column )
#if( $numericColumns.contains($selColumn) )
  , cast(l.new_value as $numericColumnDefinitions[$selColumn]) ${selColumn}
#elseif( $clobColumns.contains($selColumn) )
	, coalesce(l.new_long_value,'') ${selColumn}
#else
	, coalesce(l.new_value,'') ${selColumn}
#end
#else
  , null ${selColumn}
#end
#end
	from
	  CHANGELOG_ROW_V l
	where
	  l.TABLE_NAME='${tableName.toUpperCase()}'
	  and l.COLUMN_NAME='${column.toUpperCase()}'
#end
),
tml as (
  select
    #foreach( $pkColumn in $pkColumns )e.${pkColumn}||'-'||#{end}coalesce(e.LAST_MODIFIED_AT,e.CREATED_AT) TML_UUID,
    'L' TML_PHASE,
    #foreach( $pkColumn in $pkColumns )#if($foreach.count>1)||'-'||#{end}e.${pkColumn}#{end} UUID,
    e.CREATED_AT,
    e.CREATED_BY,
    e.LAST_MODIFIED_AT,
    e.LAST_MODIFIED_BY,
#foreach( $column in $columns )
    e.${column},
#end
    null EFFECTIVE_TIMESTAMP_FROM,
    (select min(CHANGE_TIMESTAMP) from mut where uuid=#foreach($pkColumn in $pkColumns)#if($foreach.count>1)||'-'||#{end}e.${pkColumn}#{end}) EFFECTIVE_TIMESTAMP_TO
  from
    ${tableName} e
  union all
  select
    m.CHANGELOG_UUID TML_UUID,
    'M' TML_PHASE,
    m.UUID,
    m.CREATED_AT,
    m.CREATED_BY,
    m.LAST_MODIFIED_AT,
    m.LAST_MODIFIED_BY,
#foreach( $column in $columns )
    m.${column},
#end
    m.CHANGE_TIMESTAMP EFFECTIVE_TIMESTAMP_FROM,
    (select min(CHANGE_TIMESTAMP) from mut mi where UUID=m.UUID and (CHANGE_TIMESTAMP>m.CHANGE_TIMESTAMP or (CHANGE_TIMESTAMP=m.CHANGE_TIMESTAMP and ROW_NR>m.ROW_NR))) EFFECTIVE_TIMESTAMP_TO
  from
    mut m
  where
    -- terminations will only give EFFECTIVE_TIMESTAMP_TO value in previous row
    m.MUTATION_TYPE<>'TERMINATE'
)
select
  TML_UUID,
  TML_PHASE,
  UUID,
  CREATED_AT,
  CREATED_BY,
  LAST_MODIFIED_AT,
  LAST_MODIFIED_BY,
#foreach( $column in $columns )
#if( $numericColumns.contains($column) )
  ${column},
#else
	case when ${column}<>'' then ${column} end ${column},
#end
#end
  EFFECTIVE_TIMESTAMP_FROM,
  EFFECTIVE_TIMESTAMP_TO
from (
	select distinct
		TML_UUID,
		TML_PHASE,
		UUID,
		coalesce(CREATED_AT, (select CREATED_AT from tml where UUID=o_tml.UUID and (EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_FROM<=o_tml.EFFECTIVE_TIMESTAMP_FROM) and CREATED_AT is not null order by EFFECTIVE_TIMESTAMP_FROM asc nulls last limit 1)) CREATED_AT,
		coalesce(CREATED_BY, (select CREATED_BY from tml where UUID=o_tml.UUID and (EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_FROM<=o_tml.EFFECTIVE_TIMESTAMP_FROM) and CREATED_BY is not null order by EFFECTIVE_TIMESTAMP_FROM asc nulls last limit 1)) CREATED_BY,
		coalesce(LAST_MODIFIED_AT, (select LAST_MODIFIED_AT from tml where UUID=o_tml.UUID and (EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_FROM<=o_tml.EFFECTIVE_TIMESTAMP_FROM) and LAST_MODIFIED_AT is not null order by EFFECTIVE_TIMESTAMP_FROM desc nulls last limit 1)) LAST_MODIFIED_AT,
		coalesce(LAST_MODIFIED_BY, (select LAST_MODIFIED_BY from tml where UUID=o_tml.UUID and (EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_FROM<=o_tml.EFFECTIVE_TIMESTAMP_FROM) and LAST_MODIFIED_BY is not null order by EFFECTIVE_TIMESTAMP_FROM desc nulls last limit 1)) LAST_MODIFIED_BY,
#foreach( $column in $columns )
		coalesce(${column}, (select ${column} from tml where UUID=o_tml.UUID and (EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_FROM<=o_tml.EFFECTIVE_TIMESTAMP_FROM) and ${column} is not null order by EFFECTIVE_TIMESTAMP_FROM desc nulls last limit 1)) ${column},
#end
		EFFECTIVE_TIMESTAMP_FROM,
		EFFECTIVE_TIMESTAMP_TO
	from
		tml o_tml
)
where EFFECTIVE_TIMESTAMP_FROM<>EFFECTIVE_TIMESTAMP_TO or EFFECTIVE_TIMESTAMP_FROM is null or EFFECTIVE_TIMESTAMP_TO is null
;
#end
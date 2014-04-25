------- CreateDDL statement separator -------
insert into module_config (uuid, aura_module, label, tooltip, base_url, start_page_path, menu_ordering, version)
select sys_guid(), 'home', 'Home', 'Home of AURA Portal',
  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'https://onejap.csintra.net:31876'
                              when 'Q' then 'https://onejappta.csintra.net:31876'
                              else 'https://chvj103ld105.csintra.net:31876'
                            end
  , '/aura-base/index.jsp', 0, 0 from dual
union all select sys_guid(), 'access_shop', 'Access Shop', 'Home of Access Shop',
  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'https://onejap.csintra.net:31876'
                              when 'Q' then 'https://onejappta.csintra.net:31876'
                              else 'https://chvj103ld105.csintra.net:31876'
                            end
  , '/aura-as/as/OrderWorklistMainFlowRefreshSessionStart.do', 1, 0 from dual
union all select sys_guid(), 'deputy', 'Deputy', 'Home of Deputy',
  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'https://onejap.csintra.net:31876'
                              when 'Q' then 'https://onejappta.csintra.net:31876'
                              else 'https://chvj103ld105.csintra.net:31876'
                            end
  , '/aura-dy/dy/ViewPendingRequestsMainFlowStart.do', 2, 0 from dual
union all select sys_guid(), 'recertification', 'Recertification', 'Home of Recertification',
  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'https://onejap.csintra.net:31445'
                              when 'Q' then 'https://onejappta.csintra.net:31445'
                              else 'https://chvj100ld118.csintra.net:31445'
                            end
  , '/recertification', 3, 0 from dual
union all select sys_guid(), 'org_changes', 'Org. Changes', 'Home of Organizational Changes',
  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'https://onejap.csintra.net:30062'
                              when 'Q' then 'https://onejappta.csintra.net:30062'
                              else 'https://chvj100ld118.csintra.net:30062'
                            end
  , '/ocm', 4, 0 from dual
union all select sys_guid(), 'right_modeling', 'Right Modeling', 'Home of Right Modeling',
  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'https://onejap.csintra.net:31415'
                              when 'Q' then 'https://onejappta.csintra.net:31415'
                              else 'https://chvj100ld118.csintra.net:31415'
                            end
  , '/rightModeling', 5, 0 from dual
union all select sys_guid(), 'role_modeling', 'Role Modeling', 'Home of Role Modeling',
  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'https://onejap.csintra.net:31458'
                              when 'Q' then 'https://onejappta.csintra.net:31458'
                              else 'https://chvj100ld120.csintra.net:31458'
                            end
  , '/aura-cc', 6, 0 from dual
union all select sys_guid(), 'migration', 'Migration', 'Home of Migration',
  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'https://onejap.csintra.net:31461'
                              when 'Q' then 'https://onejappta.csintra.net:31461'
                              else 'https://chvj100ld120.csintra.net:31461'
                            end
  , '/aura-ff', 7, 0 from dual
union all select sys_guid(), 'administration', 'Administration', 'Home of Administration',
  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'https://onejap.csintra.net:31876'
                              when 'Q' then 'https://onejappta.csintra.net:31876'
                              else 'https://chvj103ld105.csintra.net:31876'
                            end
  , '/aura-base/bw/Administration.jsp', 8, 0 from dual
union all select sys_guid(), 'reporting', 'Reporting', 'Home of Reporting',
  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'https://onejap.csintra.net:31876'
                              when 'Q' then 'https://onejappta.csintra.net:31876'
                              else 'https://chvj103ld105.csintra.net:31876'
                            end
  , '/aura-rc/rc/AccountSearchEmployeeFlowStart.do', 9, 0 from dual
union all select sys_guid(), 'entitlement_admin', 'Entitlement Adm.', 'Home of Entitlement Administration',
  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'https://chl20010312.ch.hedani.net:29930'
                              when 'Q' then 'https://chl20010312.ch.hedani.net:29930'
                              else 'https://chl20010312.ch.hedani.net:29930'
                            end
  , '/adm', 10, 0 from dual;


------- CreateDDL statement separator -------
insert into timeshift(id, timeshift_ms) values (1, 0);
------- CreateDDL statement separator -------
-- ID 2 only used for external systems in transformation
insert into timeshift(id, timeshift_ms) values (2, 0);

------- CreateDDL statement separator -------
insert into system_info (uuid, key, value, description)
select sys_guid(), 'DB.I',  case substr(sys_context('USERENV','DB_NAME'),1,1)
                              when 'P' then 'PROD'
                              when 'Q' then 'PTA'
                              when 'T' then 'IT'
                              when 'D' then 'ET'
                              else 'unknown'
                            end, 'AURA DB Instance' from dual
union all select sys_guid(), 'TR.START', '', 'Core Transformation Start' from dual
union all select sys_guid(), 'TR.END', '', 'Core Transformation End' from dual
union all select sys_guid(), 'SINGLE_NODE_PROCESSING_SERVER_INFO', '', 'Info of node that does single node processing ("<host name> <last alive timestamp>")' from dual
;

------- role-prov.rule config -------

------- CreateDDL statement separator -------
-- BASE ROLE PROVISIONING RULE
insert into role_prov_rule_config_rule(uuid, rule_type, rule_attribute_category, lower_bound_quantity) values(sys_guid(), 'ROLE_PROV_RULE_BASE_ROLE', 'Organization', 1);
------- CreateDDL statement separator -------
-- and
insert into role_prov_rule_config_rule(uuid, rule_type, rule_attribute, rule_attribute_category, lower_bound_quantity, upper_bound_quantity) values(sys_guid(), 'ROLE_PROV_RULE_BASE_ROLE', 'UserId', 'Employee', 1, 1);

------- CreateDDL statement separator -------
-- ENTERPRISE ROLE PROVISIONING RULE
insert into role_prov_rule_config_rule(uuid, rule_type, rule_attribute_category, lower_bound_quantity) values(sys_guid(), 'ROLE_PROV_RULE_ENTERPRISE_ROLE', 'Function', 1);
------- CreateDDL statement separator -------
-- and
insert into role_prov_rule_config_rule(uuid, rule_type, rule_attribute_category, lower_bound_quantity) values(sys_guid(), 'ROLE_PROV_RULE_ENTERPRISE_ROLE', 'Organization', 1);
------- CreateDDL statement separator -------
-- and
insert into role_prov_rule_config_rule(uuid, rule_type, rule_attribute_category, lower_bound_quantity) values(sys_guid(), 'ROLE_PROV_RULE_ENTERPRISE_ROLE', 'Employee', 0);

------- CreateDDL statement separator -------
-- FUNCTION ROLE PROVISIONING RULE
insert into role_prov_rule_config_rule(uuid, rule_type, rule_attribute_category, lower_bound_quantity) values(sys_guid(), 'ROLE_PROV_RULE_FUNCTION_ROLE', 'Function', 1);
------- CreateDDL statement separator -------
-- and
insert into role_prov_rule_config_rule(uuid, rule_type, rule_attribute_category, lower_bound_quantity) values(sys_guid(), 'ROLE_PROV_RULE_FUNCTION_ROLE', 'Organization', 0);
------- CreateDDL statement separator -------
-- and
insert into role_prov_rule_config_rule(uuid, rule_type, rule_attribute_category, lower_bound_quantity) values(sys_guid(), 'ROLE_PROV_RULE_FUNCTION_ROLE', 'Employee', 0);

------- CreateDDL statement separator -------
-- SCOPE PROVISIONING RULEs
insert into role_prov_rule_config_rule(uuid, rule_type, rule_attribute_category, lower_bound_quantity) values(sys_guid(), 'SCOPE_PROV_RULE', 'Function', 0);
------- CreateDDL statement separator -------
-- and
insert into role_prov_rule_config_rule(uuid, rule_type, rule_attribute_category, lower_bound_quantity) values(sys_guid(), 'SCOPE_PROV_RULE', 'Organization', 0);
------- CreateDDL statement separator -------
-- and
insert into role_prov_rule_config_rule(uuid, rule_type, rule_attribute_category, lower_bound_quantity) values(sys_guid(), 'SCOPE_PROV_RULE', 'Employee', 0);

------- CreateDDL statement separator -------
commit;



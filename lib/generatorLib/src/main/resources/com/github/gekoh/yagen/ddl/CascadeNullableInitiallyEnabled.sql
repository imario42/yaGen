------- CreateDDL statement separator -------
insert into SYSTEM_SETTING (UUID, REQUIRED_BY, SETTING_KEY, SETTING_TYPE, BOOLEAN_VALUE)
  values (sys_guid(), 'IMP', 'CASCADE_NULLABLE_CTRL.null_permitted', 'BOOLEAN', 1);

------- CreateDDL statement separator -------
commit;
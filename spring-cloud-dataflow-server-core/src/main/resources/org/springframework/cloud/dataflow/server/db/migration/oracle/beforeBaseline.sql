-- NOTE: this file can be modified as flyway doesn't checksum it
-- we essentially assume that existing db is in state of dataflow 1.7.x and
-- this script brings it up to what V1 would create.

-- prepare V1 tables so that we can copy data over
create table app_registration_tmp (
  id number(19,0) not null,
  object_version number(19,0),
  default_version number(1,0),
  metadata_uri clob,
  name varchar2(255 char),
  type number(10,0),
  uri clob,
  version varchar2(255 char),
  primary key (id)
);
create table audit_records_tmp (
  id number(19,0) not null,
  audit_action number(19,0),
  audit_data long,
  audit_operation number(19,0),
  correlation_id varchar2(255 char),
  created_by varchar2(255 char),
  created_on timestamp,
  primary key (id)
);

-- copy data
insert into
  app_registration_tmp (id, object_version, default_version, metadata_uri, name, type, uri, version) 
  select id, object_Version, default_Version, metadata_Uri, name, type, uri, version
  from APP_REGISTRATION;
insert into
  audit_records_tmp (id, audit_action, audit_data, audit_operation, correlation_id, created_by, created_on)
  select id, audit_Action, audit_data, audit_Operation, correlation_id, created_by, created_On
  from AUDIT_RECORDS;

-- drop old tables
drop table APP_REGISTRATION;
drop table AUDIT_RECORDS;

-- rename back to real tables
alter table app_registration_tmp rename to app_registration;
alter table audit_records_tmp rename to audit_records;

-- bring back indexes
create index audit_records_audit_action_idx on audit_records (audit_action) ;
create index audit_records_audit_operation_idx on audit_records (audit_operation) ;
create index audit_records_correlation_id_idx on audit_records (correlation_id) ;
create index audit_records_created_on_idx on audit_records (created_on) ;

-- expected V1 additions
CREATE TABLE TASK_LOCK (
  LOCK_KEY CHAR(36) NOT NULL,
  REGION VARCHAR(100) NOT NULL,
  CLIENT_ID CHAR(36),
  CREATED_DATE TIMESTAMP NOT NULL,
  constraint LOCK_PK primary key (LOCK_KEY, REGION)
);

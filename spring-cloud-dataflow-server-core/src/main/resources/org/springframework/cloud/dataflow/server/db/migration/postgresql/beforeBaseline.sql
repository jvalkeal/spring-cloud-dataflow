-- NOTE: this file can be modified as flyway doesn't checksum it
-- we essentially assume that existing db is in state of dataflow 1.7.x and
-- this script brings it up to what V1 would create.

-- drop existing indexes
drop index if exists AUDIT_RECORDS_AUDIT_ACTION_IDX;
drop index if exists AUDIT_RECORDS_AUDIT_OPERATION_IDX;
drop index if exists AUDIT_RECORDS_CORRELATION_ID_IDX;
drop index if exists AUDIT_RECORDS_CREATED_ON_IDX;

-- prepare V1 tables so that we can copy data over
create table app_registration_tmp (
  id int8 not null,
  object_version int8,
  default_version boolean,
  metadata_uri text,
  name varchar(255),
  type int4,
  uri text,
  version varchar(255),
  primary key (id)
);
create table audit_records_tmp (
  id int8 not null,
  audit_action int8,
  audit_data text,
  audit_operation int8,
  correlation_id varchar(255),
  created_by varchar(255),
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
create index if not exists audit_records_audit_action_idx on audit_records (audit_action) ;
create index if not exists audit_records_audit_operation_idx on audit_records (audit_operation) ;
create index if not exists audit_records_correlation_id_idx on audit_records (correlation_id) ;
create index if not exists audit_records_created_on_idx on audit_records (created_on) ;

-- expected V1 additions
CREATE TABLE TASK_LOCK (
  LOCK_KEY CHAR(36),
  REGION VARCHAR(100),
  CLIENT_ID CHAR(36),
  CREATED_DATE TIMESTAMP NOT NULL,
  constraint LOCK_PK primary key (LOCK_KEY, REGION)
);

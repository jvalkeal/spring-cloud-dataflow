create table app_registration (
    id bigint not null,
    object_version bigint,
    default_version bit,
    metadata_uri longtext,
    name varchar(255),
    type integer,
    uri longtext,
    version varchar(255),
    primary key (id)
);

insert into app_registration (
    id,
    object_version,
    default_version,
    metadata_uri,
    name,
    type,
    uri,
    version)
select
    id,
    object_version,
    default_version,
    metadata_uri,
    name,
    type,
    uri,
    version
from APP_REGISTRATION;

drop table APP_REGISTRATION;

create table audit_records (
    id bigint not null,
    audit_action bigint,
    audit_data longtext,
    audit_operation bigint,
    correlation_id varchar(255),
    created_by varchar(255),
    created_on datetime,
    primary key (id)
);


insert into audit_records (
    id, audit_action, audit_data, audit_operation, correlation_id, created_by, created_on)
    select id, audit_action, audit_data, audit_operation, correlation_id, created_by, created_on from AUDIT_RECORDS;

drop table AUDIT_RECORDS;
    

    
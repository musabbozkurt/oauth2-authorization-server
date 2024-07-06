create schema if not exists oauth2_authorization_server;

create table if not exists oauth2_authorization_server.authorities
(
    id                bigint not null auto_increment,
    authority         varchar(255),
    default_authority bit default false,
    primary key (id)
);

create table if not exists oauth2_authorization_server.authorization
(
    id                            varchar(255) not null,
    access_token_expires_at       datetime(6),
    access_token_issued_at        datetime(6),
    access_token_metadata         text,
    access_token_scopes           varchar(1000),
    access_token_type             varchar(255),
    access_token_value            text,
    attributes                    text,
    authorization_code_expires_at datetime(6),
    authorization_code_issued_at  datetime(6),
    authorization_code_metadata   varchar(255),
    authorization_code_value      text,
    authorization_grant_type      varchar(255),
    authorized_scopes             varchar(1000),
    oidc_id_token_claims          text,
    oidc_id_token_expires_at      datetime(6),
    oidc_id_token_issued_at       datetime(6),
    oidc_id_token_metadata        text,
    oidc_id_token_value           text,
    principal_name                varchar(255),
    refresh_token_expires_at      datetime(6),
    refresh_token_issued_at       datetime(6),
    refresh_token_metadata        text,
    refresh_token_value           text,
    registered_client_id          varchar(255),
    state                         varchar(500),
    primary key (id)
);

create table if not exists oauth2_authorization_server.client
(
    id                            varchar(255) not null,
    authorization_grant_types     varchar(1000),
    client_authentication_methods varchar(1000),
    client_id                     varchar(255),
    client_id_issued_at           datetime(6),
    client_name                   varchar(255),
    client_secret                 varchar(255),
    client_secret_expires_at      datetime(6),
    client_settings               varchar(2000),
    redirect_uris                 varchar(1000),
    scopes                        varchar(1000),
    token_settings                varchar(2000),
    primary key (id)
);

create table if not exists oauth2_authorization_server.users
(
    id                      bigint              not null auto_increment,
    account_non_expired     bit,
    account_non_locked      bit,
    credentials_non_expired bit,
    enabled                 bit,
    password                varchar(255)        not null,
    username                varchar(255) unique not null,
    first_name              varchar(255)        not null,
    last_name               varchar(255)        not null,
    phone_number            varchar(50) unique  not null,
    email                   varchar(255) unique not null,
    primary key (id)
);

create table if not exists oauth2_authorization_server.users_authorities
(
    users_id       bigint not null,
    authorities_id bigint not null,
    primary key (users_id, authorities_id)
);

alter table oauth2_authorization_server.authorities
    drop index if exists idx__authorities_authority;

alter table oauth2_authorization_server.authorities
    add constraint idx__authorities_authority unique (authority);

alter table oauth2_authorization_server.users
    drop index if exists idx__users_username;

alter table oauth2_authorization_server.users
    add constraint idx__users_username unique (username);

alter table oauth2_authorization_server.users_authorities
    drop constraint if exists users_authorities_authorities_id__authorities_id;

alter table oauth2_authorization_server.users_authorities
    add constraint users_authorities_authorities_id__authorities_id foreign key (authorities_id) references oauth2_authorization_server.authorities (id);

alter table oauth2_authorization_server.users_authorities
    drop constraint if exists users_authorities_users_id__users_id;

alter table oauth2_authorization_server.users_authorities
    add constraint users_authorities_users_id__users_id foreign key (users_id) references oauth2_authorization_server.users (id);

INSERT INTO oauth2_authorization_server.authorities(authority)
VALUES ('ROLE_USER');

INSERT INTO oauth2_authorization_server.authorities(authority)
VALUES ('ROLE_ADMIN');

INSERT INTO oauth2_authorization_server.authorities(authority, default_authority)
VALUES ('ROLE_DEVELOPER', true);

INSERT INTO oauth2_authorization_server.users(first_name, last_name, phone_number, email, username, password,
                                              account_non_expired, account_non_locked, credentials_non_expired, enabled)
VALUES ('Developer', 'Developer', '90123456789', 'developer@developer.com', 'Developer',
        '$2a$10$Vs0szjjJRXdIIdik/Kpg/eE7eXo97vA.vHW0PF9bLaGvEyyO2IuY6', true, true, true, true);

INSERT INTO oauth2_authorization_server.users(first_name, last_name, phone_number, email, username, password,
                                              account_non_expired, account_non_locked, credentials_non_expired, enabled)
VALUES ('Admin', 'Admin', '90123456781', 'admin@admin.com', 'Admin',
        '$2a$10$Vs0szjjJRXdIIdik/Kpg/eE7eXo97vA.vHW0PF9bLaGvEyyO2IuY6', true, true, true, true);

INSERT INTO oauth2_authorization_server.users(first_name, last_name, phone_number, email, username, password,
                                              account_non_expired, account_non_locked, credentials_non_expired, enabled)
VALUES ('User', 'User', '90123456782', 'user@user.com', 'User',
        '$2a$10$Vs0szjjJRXdIIdik/Kpg/eE7eXo97vA.vHW0PF9bLaGvEyyO2IuY6', true, true, true, true);

INSERT INTO oauth2_authorization_server.users_authorities(users_id, authorities_id)
VALUES (1, 1);

INSERT INTO oauth2_authorization_server.users_authorities(users_id, authorities_id)
VALUES (1, 2);

INSERT INTO oauth2_authorization_server.users_authorities(users_id, authorities_id)
VALUES (1, 3);

INSERT INTO oauth2_authorization_server.users_authorities(users_id, authorities_id)
VALUES (2, 1);

INSERT INTO oauth2_authorization_server.users_authorities(users_id, authorities_id)
VALUES (2, 2);

INSERT INTO oauth2_authorization_server.users_authorities(users_id, authorities_id)
VALUES (3, 1);

INSERT INTO oauth2_authorization_server.client(id, authorization_grant_types, client_authentication_methods, client_id,
                                               client_id_issued_at, client_name, client_secret,
                                               client_secret_expires_at,
                                               client_settings, redirect_uris, scopes, token_settings)
VALUES ('abbc70f1-fb59-4b42-b1e4-c52fa0080bea',
        'refresh_token,client_credentials,authorization_code,urn:ietf:params:oauth:grant-type:jwt-bearer',
        'client_secret_basic', 'client', null, 'abbc70f1-fb59-4b42-b1e4-c52fa0080bea',
        '$2a$10$lcGI9Fp6GLfk7wjyOK0VqORQqMtsQRoC3J7i/V023SgQv9JZLZ01K', null,
        '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":true}',
        'https://insomnia,http://127.0.0.1:8080/login/oauth2/code/client', 'read,openid,profile',
        '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,
        "settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],
        "settings.token.access-token-time-to-live":["java.time.Duration",86400.000000000],
        "settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat",
        "value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],
        "settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000]}');
create schema if not exists springbootoauth2server;

create table if not exists springbootoauth2server.authorities
(
    id        integer not null auto_increment,
    authority varchar(255),
    primary key (id)
);


create table if not exists springbootoauth2server.authorization
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


create table if not exists springbootoauth2server.client
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


create table if not exists springbootoauth2server.users
(
    id                      integer not null auto_increment,
    account_non_expired     bit,
    account_non_locked      bit,
    credentials_non_expired bit,
    enabled                 bit,
    password                varchar(255),
    username                varchar(255),
    primary key (id)
);


create table if not exists springbootoauth2server.users_authorities
(
    users_id       integer not null,
    authorities_id integer not null,
    primary key (users_id, authorities_id)
);


alter table springbootoauth2server.authorities
    drop index if exists UK_q0u5f2cdlshec8tlh6818bhbk;


alter table springbootoauth2server.authorities
    add constraint UK_q0u5f2cdlshec8tlh6818bhbk unique (authority);


alter table springbootoauth2server.users
    drop index if exists UK_r43af9ap4edm43mmtq01oddj6;


alter table springbootoauth2server.users
    add constraint UK_r43af9ap4edm43mmtq01oddj6 unique (username);


alter table springbootoauth2server.users_authorities
    add constraint FKmfxncv8ke1jjgna64c8kclry5
        foreign key (authorities_id)
            references authorities (id);


alter table springbootoauth2server.users_authorities
    add constraint FK2cmfwo8tbjcpmltse0rh5ir0t
        foreign key (users_id)
            references users (id);


INSERT INTO springbootoauth2server.authorities(authority)
VALUES ('ROLE_USER');
INSERT INTO springbootoauth2server.authorities(authority)
VALUES ('ROLE_ADMIN');
INSERT INTO springbootoauth2server.authorities(authority)
VALUES ('ROLE_DEVELOPER');

INSERT INTO springbootoauth2server.users(username, password, account_non_expired, account_non_locked,
                                         credentials_non_expired, enabled)
VALUES ('Developer', '$2a$12$2yOChyhSuJm/naTBUjGZb.6d6mu1NsXS8XWRFousQfRTwzy0ZQtWW', true, true, true, true);
INSERT INTO springbootoauth2server.users(username, password, account_non_expired, account_non_locked,
                                         credentials_non_expired, enabled)
VALUES ('Admin', '$2a$12$2yOChyhSuJm/naTBUjGZb.6d6mu1NsXS8XWRFousQfRTwzy0ZQtWW', true, true, true, true);
INSERT INTO springbootoauth2server.users(username, password, account_non_expired, account_non_locked,
                                         credentials_non_expired, enabled)
VALUES ('User', '$2a$12$2yOChyhSuJm/naTBUjGZb.6d6mu1NsXS8XWRFousQfRTwzy0ZQtWW', true, true, true, true);

INSERT INTO springbootoauth2server.users_authorities(users_id, authorities_id)
VALUES (1, 1);
INSERT INTO springbootoauth2server.users_authorities(users_id, authorities_id)
VALUES (1, 2);
INSERT INTO springbootoauth2server.users_authorities(users_id, authorities_id)
VALUES (1, 3);
INSERT INTO springbootoauth2server.users_authorities(users_id, authorities_id)
VALUES (2, 1);
INSERT INTO springbootoauth2server.users_authorities(users_id, authorities_id)
VALUES (2, 2);
INSERT INTO springbootoauth2server.users_authorities(users_id, authorities_id)
VALUES (3, 1);

INSERT INTO springbootoauth2server.client(id, authorization_grant_types, client_authentication_methods, client_id,
                                          client_id_issued_at, client_name, client_secret, client_secret_expires_at,
                                          client_settings, redirect_uris, scopes, token_settings)
VALUES ('abbc70f1-fb59-4b42-b1e4-c52fa0080bea', 'refresh_token,client_credentials,authorization_code',
        'client_secret_basic',
        'client', null, 'abbc70f1-fb59-4b42-b1e4-c52fa0080bea',
        '$2a$10$lcGI9Fp6GLfk7wjyOK0VqORQqMtsQRoC3J7i/V023SgQv9JZLZ01K', null,
        '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":true}',
        'http://insomnia,http://127.0.0.1:8080/login/oauth2/code/client', 'read,openid,profile',
        '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,
        "settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],
        "settings.token.access-token-time-to-live":["java.time.Duration",86400.000000000],
        "settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat",
        "value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],
        "settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000]}');
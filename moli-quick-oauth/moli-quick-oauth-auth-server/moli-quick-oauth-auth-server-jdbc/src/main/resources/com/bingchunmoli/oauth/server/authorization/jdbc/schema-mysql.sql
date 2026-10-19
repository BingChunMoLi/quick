/*
IMPORTANT:
    If using PostgreSQL:
        - update ALL columns defined with 'timestamp' to 'timestamptz', to ensure that time instants are stored accurately.
    If using MySQL:
        - add 'preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' to JDBC connection URL
          to ensure that time instants are stored accurately. See https://dev.mysql.com/doc/connector-j/en/connector-j-time-instants.html
*/
CREATE TABLE oauth2_registered_client (
    id varchar(100) NOT NULL,
    client_id varchar(100) NOT NULL UNIQUE,
    client_id_issued_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret varchar(200) DEFAULT NULL,
    client_secret_expires_at timestamp DEFAULT NULL,
    client_name varchar(200) NOT NULL,
    client_authentication_methods varchar(1000) NOT NULL,
    authorization_grant_types varchar(1000) NOT NULL,
    redirect_uris varchar(1000) DEFAULT NULL,
    post_logout_redirect_uris varchar(1000) DEFAULT NULL,
    scopes varchar(1000) NOT NULL,
    client_settings varchar(2000) NOT NULL,
    token_settings varchar(2000) NOT NULL,
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

/*
IMPORTANT:
    If using PostgreSQL:
        - update ALL columns defined with 'longblob' to 'text', as PostgreSQL does not support the 'longblob' data type.
        - update ALL columns defined with 'timestamp' to 'timestamptz', to ensure that time instants are stored accurately.
    If using MySQL:
        - add 'preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' to JDBC connection URL
          to ensure that time instants are stored accurately. See https://dev.mysql.com/doc/connector-j/en/connector-j-time-instants.html
*/
CREATE TABLE oauth2_authorization (
    id varchar(100) NOT NULL,
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorization_grant_type varchar(100) NOT NULL,
    authorized_scopes varchar(1000) DEFAULT NULL,
    attributes longblob DEFAULT NULL,
    state varchar(500) DEFAULT NULL,
    authorization_code_value longblob DEFAULT NULL,
    authorization_code_issued_at timestamp DEFAULT NULL,
    authorization_code_expires_at timestamp DEFAULT NULL,
    authorization_code_metadata longblob DEFAULT NULL,
    access_token_value longblob DEFAULT NULL,
    access_token_issued_at timestamp DEFAULT NULL,
    access_token_expires_at timestamp DEFAULT NULL,
    access_token_metadata longblob DEFAULT NULL,
    access_token_type varchar(100) DEFAULT NULL,
    access_token_scopes varchar(1000) DEFAULT NULL,
    oidc_id_token_value longblob DEFAULT NULL,
    oidc_id_token_issued_at timestamp DEFAULT NULL,
    oidc_id_token_expires_at timestamp DEFAULT NULL,
    oidc_id_token_metadata longblob DEFAULT NULL,
    refresh_token_value longblob DEFAULT NULL,
    refresh_token_issued_at timestamp DEFAULT NULL,
    refresh_token_expires_at timestamp DEFAULT NULL,
    refresh_token_metadata longblob DEFAULT NULL,
    user_code_value longblob DEFAULT NULL,
    user_code_issued_at timestamp DEFAULT NULL,
    user_code_expires_at timestamp DEFAULT NULL,
    user_code_metadata longblob DEFAULT NULL,
    device_code_value longblob DEFAULT NULL,
    device_code_issued_at timestamp DEFAULT NULL,
    device_code_expires_at timestamp DEFAULT NULL,
    device_code_metadata longblob DEFAULT NULL,
    PRIMARY KEY (id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE oauth2_authorization_consent (
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorities varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Quick expiration metadata; updated atomically with native authorization records.
CREATE TABLE quick_oauth_authorization_expiry (authorization_id varchar(100) NOT NULL PRIMARY KEY, expires_at timestamp DEFAULT NULL, INDEX quick_oauth_expiry_idx (expires_at)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

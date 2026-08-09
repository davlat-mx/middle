create table client (
    id      varchar(64) primary key,
    name    varchar(255) not null,
    country varchar(2)  not null
);

create table transfer (
    id            varchar(64) primary key,
    sender_id     varchar(64)   not null references client (id),
    receiver_id   varchar(64)   not null references client (id),
    amount        numeric(19, 2) not null,
    currency      varchar(3)    not null,
    corridor_from varchar(2)    not null,
    corridor_to   varchar(2)    not null,
    status        varchar(16)   not null,
    version       bigint        not null,
    created_at    timestamp     not null default now(),
    updated_at    timestamp     not null default now()
);

create table transfer_event (
    id          bigserial primary key,
    transfer_id varchar(64) not null references transfer (id),
    from_status varchar(16),
    to_status   varchar(16) not null,
    at          timestamp   not null,
    note        varchar(255)
);

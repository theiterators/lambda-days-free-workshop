
CREATE TABLE users (
    id serial PRIMARY KEY,
    email text NOT NULL,
    nick text,
    about text,
    encrypted_password text NOT NULL,
    banned boolean NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    admin boolean NOT NULL DEFAULT false,
    confirmed boolean NOT NULL DEFAULT false,
    UNIQUE (email),
    CHECK((NOT confirmed) OR (nick IS NOT NULL))
);

CREATE UNIQUE INDEX users_nick_key ON users(LOWER(nick));

CREATE TABLE confirmation_tokens
(
    id bigserial PRIMARY KEY,
    email text NOT NULL REFERENCES users(email) ON UPDATE CASCADE ON DELETE CASCADE,
    token text NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_confirmation_tokens_email_token
  ON public.confirmation_tokens
  USING btree
  (email, token);

CREATE TABLE refresh_tokens
(
    id bigserial PRIMARY KEY,
    email text NOT NULL REFERENCES users(email) ON UPDATE CASCADE ON DELETE CASCADE,
    token text NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_email_token
  ON public.refresh_tokens
  USING btree
  (email, token);

CREATE TABLE threads (
    id bigserial PRIMARY KEY,
    subject text NOT NULL,
    author_id INTEGER NOT NULL REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    closed boolean NOT NULL DEFAULT false
);

CREATE INDEX idx_threads_created_at_paginate
  ON threads
  USING btree
  (created_at, id);


CREATE TABLE posts (
    id bigserial PRIMARY KEY,
    thread_id bigint NOT NULL REFERENCES threads(id) ON UPDATE CASCADE ON DELETE CASCADE,
    content text NOT NULL,
    author_id INTEGER NOT NULL REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_posts_thread
  ON posts
  USING btree
  (thread_id);

CREATE INDEX idx_posts_author
  ON posts
  USING btree
  (author_id);

CREATE VIEW authors AS
    SELECT u.id as id, u.nick as nick, u.created_at as member_since, count(p.id) as number_of_posts
    FROM users u JOIN posts p ON (u.id = p.author_id)
    GROUP BY u.id, u.nick, u.created_at
    ;

CREATE VIEW threads_info AS
    SELECT t.id as thread_id, count(p.id) as number_of_posts, max(p.created_at) as last_post_date
    FROM threads t JOIN posts p ON (t.id = p.thread_id)
    GROUP BY t.id
    ;
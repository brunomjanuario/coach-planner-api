CREATE EXTENSION IF NOT EXISTS citext;

-- ─── auth ────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            uuid PRIMARY KEY,
    email         citext NOT NULL UNIQUE,
    name          text   NOT NULL CHECK (length(trim(name)) > 0),
    password_hash text   NOT NULL,
    version       bigint NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
-- citext gives case-insensitive uniqueness in the database, so
-- "A@b.com" and "a@B.com" cannot both register regardless of app code.

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,          -- SHA-256; never the raw token
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id) WHERE revoked_at IS NULL;

-- ─── squad ───────────────────────────────────────────────────────────────
CREATE TABLE teams (
    id         uuid PRIMARY KEY,
    owner_id   uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       text NOT NULL CHECK (length(trim(name)) > 0),
    club       text,
    season     text,
    version    bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_teams_owner ON teams(owner_id);

CREATE TYPE player_position AS ENUM (
    'GK','RB','CB','LB','RWB','LWB','CDM','RM','CM','LM','CAM','RW','LW','ST','CF','RF','LF'
);

CREATE TABLE players (
    id             uuid PRIMARY KEY,
    team_id        uuid NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    name           text NOT NULL CHECK (length(trim(name)) > 0),
    age            int  CHECK (age BETWEEN 4 AND 99),
    shirt_number   int  CHECK (shirt_number BETWEEN 1 AND 99),
    position       player_position,
    goals          int NOT NULL DEFAULT 0 CHECK (goals >= 0),
    assists        int NOT NULL DEFAULT 0 CHECK (assists >= 0),
    conceded_goals int NOT NULL DEFAULT 0 CHECK (conceded_goals >= 0),
    version        bigint NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_players_team ON players(team_id);
-- Deliberately NO unique index on (team_id, shirt_number): the frontend's own
-- seed data ships three players wearing 3 in one team. Enforcing it here would
-- reject data the app already considers valid.

-- ─── trainings ───────────────────────────────────────────────────────────
CREATE TABLE trainings (
    id               uuid PRIMARY KEY,
    owner_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id          uuid REFERENCES teams(id) ON DELETE SET NULL,   -- AD-107
    day              timestamptz NOT NULL,
    duration_minutes int NOT NULL CHECK (duration_minutes BETWEEN 1 AND 480),
    version          bigint NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_trainings_owner_team_day ON trainings(owner_id, team_id, day);
-- Composite ordered exactly as the numbering read path scans (AD-108).

CREATE TABLE exercises (
    id                uuid PRIMARY KEY,
    training_id       uuid NOT NULL REFERENCES trainings(id) ON DELETE CASCADE,
    order_index       int  NOT NULL CHECK (order_index >= 0),
    description       text NOT NULL CHECK (length(trim(description)) > 0),
    number_of_players int  CHECK (number_of_players BETWEEN 1 AND 40),
    duration_minutes  int  CHECK (duration_minutes BETWEEN 1 AND 480),
    repetitions       int  CHECK (repetitions BETWEEN 1 AND 99),
    diagram           jsonb,                                          -- AD-102, frontend AD-015
    UNIQUE (training_id, order_index) DEFERRABLE INITIALLY DEFERRED
);
-- DEFERRABLE so a wholesale reorder inside one transaction doesn't trip the
-- constraint mid-update. The legacy `image` field is NOT carried over: the
-- frontend's AD-015 left it deliberately unused and dead columns rot.

-- ─── games ───────────────────────────────────────────────────────────────
CREATE TABLE games (
    id          uuid PRIMARY KEY,
    owner_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id     uuid REFERENCES teams(id) ON DELETE SET NULL,          -- AD-107
    opponent    text NOT NULL CHECK (length(trim(opponent)) > 0),      -- AD-104: a name, not an FK
    competition text,                                                  -- AD-104
    date        timestamptz NOT NULL,
    is_home     boolean NOT NULL,
    us_score    int CHECK (us_score BETWEEN 0 AND 99),
    them_score  int CHECK (them_score BETWEEN 0 AND 99),
    version     bigint NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT scores_recorded_together
        CHECK ((us_score IS NULL) = (them_score IS NULL))
);
CREATE INDEX idx_games_owner_team_date ON games(owner_id, team_id, date DESC);
CREATE INDEX idx_games_owner_competition ON games(owner_id, lower(competition));
CREATE INDEX idx_games_owner_opponent    ON games(owner_id, lower(opponent));
-- The two lower() indexes serve the AD-104 rename cascade, which matches
-- case-insensitively on trimmed names.
-- `scores_recorded_together` makes the null-vs-zero trap unrepresentable: a
-- half-recorded result cannot exist, so `hasResult` is a single null check.

-- ─── standings (manually maintained rival rows) ─────────────────────────
CREATE TABLE standings_rivals (
    id            uuid PRIMARY KEY,
    owner_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          text NOT NULL CHECK (length(trim(name)) > 0),
    played        int NOT NULL CHECK (played  >= 0),
    won           int NOT NULL CHECK (won     >= 0),
    drawn         int NOT NULL CHECK (drawn   >= 0),
    lost          int NOT NULL CHECK (lost    >= 0),
    goals_for     int NOT NULL CHECK (goals_for     >= 0),
    goals_against int NOT NULL CHECK (goals_against >= 0),
    version       bigint NOT NULL DEFAULT 0,
    CONSTRAINT results_sum_to_played CHECK (won + drawn + lost = played)
);
CREATE INDEX idx_standings_owner ON standings_rivals(owner_id);
-- No points/goal_difference columns — always derived (AD-108, frontend AD-008).
-- No team_id — see spec.md's logged assumption. This is the known gap.

-- ─── discipline ──────────────────────────────────────────────────────────
CREATE TYPE card_type AS ENUM ('yellow','red');

CREATE TABLE cards (
    id         uuid PRIMARY KEY,
    player_id  uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    game_id    uuid NOT NULL REFERENCES games(id)   ON DELETE CASCADE,
    type       card_type NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_cards_game   ON cards(game_id);
CREATE INDEX idx_cards_player ON cards(player_id);

CREATE TABLE ratings (
    id          uuid PRIMARY KEY,
    player_id   uuid NOT NULL REFERENCES players(id)   ON DELETE CASCADE,
    training_id uuid REFERENCES trainings(id) ON DELETE CASCADE,       -- AD-106
    game_id     uuid REFERENCES games(id)     ON DELETE CASCADE,       -- AD-106
    value       smallint NOT NULL CHECK (value BETWEEN 0 AND 10),
    version     bigint NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT exactly_one_event CHECK ((training_id IS NULL) != (game_id IS NULL))
);
CREATE UNIQUE INDEX uq_ratings_player_training
    ON ratings(player_id, training_id) WHERE training_id IS NOT NULL;
CREATE UNIQUE INDEX uq_ratings_player_game
    ON ratings(player_id, game_id)     WHERE game_id IS NOT NULL;
-- Two partial unique indexes give the (player, eventType, eventId) uniqueness
-- the frontend enforces with a findIndex. This is what makes the upsert safe
-- under concurrent calls (AC RATE-07) — the database rejects the duplicate,
-- the service retries as an update.

-- ─── reference lists (AD-104) ────────────────────────────────────────────
CREATE TABLE competitions (
    id       uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name     text NOT NULL CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX uq_competitions_owner_name ON competitions(owner_id, lower(name));

CREATE TABLE opponents (
    id       uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name     text NOT NULL CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX uq_opponents_owner_name ON opponents(owner_id, lower(name));

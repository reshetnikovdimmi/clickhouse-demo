-- =====================================================
-- Фаза 3: Создание таблиц ClickHouse для покера
-- =====================================================

-- 1. Таблица игр
CREATE TABLE IF NOT EXISTS app_db.poker_games
(
    game_id UInt64,
    game_name String,
    game_state String,
    pot UInt32,
    current_bet UInt32,
    min_bet UInt32,
    max_players UInt8,
    small_blind UInt32,
    big_blind UInt32,
    community_cards String,
    player_bets String,
    current_player_id String,
    deck_count UInt8,
    winner_id String,
    winner_amount UInt32,
    started_at DateTime,
    finished_at DateTime
)
    ENGINE = MergeTree()
    ORDER BY (game_id, started_at)
    PARTITION BY toYYYYMM(started_at);

-- Индексы для poker_games
ALTER TABLE app_db.poker_games ADD INDEX IF NOT EXISTS idx_game_state game_state TYPE bloom_filter GRANULARITY 1;
ALTER TABLE app_db.poker_games ADD INDEX IF NOT EXISTS idx_started_at started_at TYPE minmax GRANULARITY 1;


-- 2. Таблица игроков
CREATE TABLE IF NOT EXISTS app_db.poker_players
(
    player_id String,
    player_name String,
    chips UInt32,
    seat_number UInt8,
    is_active UInt8,
    is_dealer UInt8,
    hands_played UInt32,
    hands_won UInt32,
    win_rate Float64,
    joined_at DateTime,
    last_action_at DateTime
)
    ENGINE = MergeTree()
    ORDER BY (player_id, joined_at)
    PARTITION BY toYYYYMM(joined_at);

-- Индексы для poker_players
ALTER TABLE app_db.poker_players ADD INDEX IF NOT EXISTS idx_player_name player_name TYPE bloom_filter GRANULARITY 1;
ALTER TABLE app_db.poker_players ADD INDEX IF NOT EXISTS idx_is_active is_active TYPE set(2) GRANULARITY 1;


-- 3. Таблица действий игроков
CREATE TABLE IF NOT EXISTS app_db.poker_actions
(
    action_id UInt64,
    game_id UInt64,
    player_id String,
    action_type String,
    bet_amount UInt32,
    stack_after UInt32,
    pot_after UInt32,
    round_number UInt8,
    action_time DateTime
)
    ENGINE = MergeTree()
    ORDER BY (game_id, action_time, action_id)
    PARTITION BY toYYYYMM(action_time);

-- Индексы для poker_actions
ALTER TABLE app_db.poker_actions ADD INDEX IF NOT EXISTS idx_action_type action_type TYPE bloom_filter GRANULARITY 1;
ALTER TABLE app_db.poker_actions ADD INDEX IF NOT EXISTS idx_player_id player_id TYPE bloom_filter GRANULARITY 1;


-- 4. Таблица раздач
CREATE TABLE IF NOT EXISTS app_db.poker_hands
(
    hand_id UInt64,
    game_id UInt64,
    hand_number UInt32,
    hole_cards String,
    community_cards String,
    player_id String,
    hand_rank String,
    hand_description String,
    hand_value UInt16,
    is_winner UInt8,
    winnings UInt32,
    hand_time DateTime
)
    ENGINE = MergeTree()
    ORDER BY (game_id, hand_number, hand_time)
    PARTITION BY toYYYYMM(hand_time);

-- Индексы для poker_hands
ALTER TABLE app_db.poker_hands ADD INDEX IF NOT EXISTS idx_hand_rank hand_rank TYPE bloom_filter GRANULARITY 1;
ALTER TABLE app_db.poker_hands ADD INDEX IF NOT EXISTS idx_hand_player player_id TYPE bloom_filter GRANULARITY 1;
ALTER TABLE app_db.poker_hands ADD INDEX IF NOT EXISTS idx_is_winner is_winner TYPE set(2) GRANULARITY 1;


-- 5. Таблица активных сессий
CREATE TABLE IF NOT EXISTS app_db.poker_sessions
(
    session_id String,
    player_id String,
    game_id UInt64,
    session_status String,
    last_heartbeat DateTime,
    created_at DateTime
)
    ENGINE = MergeTree()
    ORDER BY (player_id, game_id, created_at)
    PARTITION BY toYYYYMM(created_at);

ALTER TABLE app_db.poker_sessions ADD INDEX IF NOT EXISTS idx_session_status session_status TYPE set(5) GRANULARITY 1;


-- 6. Таблица истории ставок
CREATE TABLE IF NOT EXISTS app_db.poker_bets
(
    bet_id UInt64,
    hand_id UInt64,
    game_id UInt64,
    player_id String,
    round_number UInt8,
    bet_type String,
    bet_amount UInt32,
    stack_before UInt32,
    stack_after UInt32,
    pot_before UInt32,
    pot_after UInt32,
    bet_time DateTime
)
    ENGINE = MergeTree()
    ORDER BY (game_id, hand_id, round_number, bet_time)
    PARTITION BY toYYYYMM(bet_time);

ALTER TABLE app_db.poker_bets ADD INDEX IF NOT EXISTS idx_bet_player player_id TYPE bloom_filter GRANULARITY 1;
ALTER TABLE app_db.poker_bets ADD INDEX IF NOT EXISTS idx_bet_type bet_type TYPE set(6) GRANULARITY 1;


-- 7. Таблица сообщений чата
CREATE TABLE IF NOT EXISTS app_db.poker_chat_messages
(
    message_id UInt64,
    game_id UInt64,
    player_id String,
    player_name String,
    message String,
    message_type String,
    recipient_id String,
    created_at DateTime
)
    ENGINE = MergeTree()
    ORDER BY (game_id, created_at)
    PARTITION BY toYYYYMM(created_at);

ALTER TABLE app_db.poker_chat_messages ADD INDEX IF NOT EXISTS idx_chat_player player_id TYPE bloom_filter GRANULARITY 1;


-- 8. Таблица системных событий
CREATE TABLE IF NOT EXISTS app_db.poker_system_events
(
    event_id UInt64,
    event_type String,
    game_id UInt64,
    player_id String,
    severity String,
    message String,
    event_data String,
    created_at DateTime
)
    ENGINE = MergeTree()
    ORDER BY (event_type, created_at)
    PARTITION BY toYYYYMM(created_at);

ALTER TABLE app_db.poker_system_events ADD INDEX IF NOT EXISTS idx_event_type event_type TYPE bloom_filter GRANULARITY 1;


-- 9. Материализованное представление для статистики игроков
CREATE MATERIALIZED VIEW IF NOT EXISTS app_db.player_stats_mv
ENGINE = SummingMergeTree()
ORDER BY (player_id, date)
AS SELECT
              player_id,
              toDate(hand_time) as date,
    count() as total_hands,
    sum(is_winner) as hands_won,
    sum(winnings) as total_winnings,
    avg(hand_value) as avg_hand_value
   FROM app_db.poker_hands
   GROUP BY player_id, date;


-- 10. Материализованное представление для аналитики действий
CREATE MATERIALIZED VIEW IF NOT EXISTS app_db.action_stats_mv
ENGINE = SummingMergeTree()
ORDER BY (action_type, date)
AS SELECT
              action_type,
              toDate(action_time) as date,
    count() as total_actions,
    avg(bet_amount) as avg_bet,
    sum(bet_amount) as total_bet,
    count(DISTINCT player_id) as unique_players
   FROM app_db.poker_actions
   GROUP BY action_type, date;
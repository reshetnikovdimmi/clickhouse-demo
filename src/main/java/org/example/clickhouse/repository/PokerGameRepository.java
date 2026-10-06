package org.example.clickhouse.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.clickhouse.model.PokerAction;
import org.example.clickhouse.model.PokerGame;
import org.example.clickhouse.model.PokerHand;
import org.example.clickhouse.model.PokerPlayer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PokerGameRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * RowMapper для преобразования ResultSet в PokerGame
     */
    private final RowMapper<PokerGame> pokerGameRowMapper = (rs, rowNum) -> PokerGame.builder()
            .gameId(rs.getLong("game_id"))
            .gameName(rs.getString("game_name"))
            .gameState(rs.getString("game_state"))
            .pot(rs.getInt("pot"))
            .currentBet(rs.getInt("current_bet"))
            .minBet(rs.getInt("min_bet"))
            .maxPlayers(rs.getInt("max_players"))
            .smallBlind(rs.getInt("small_blind"))
            .bigBlind(rs.getInt("big_blind"))
            .startedAt(rs.getObject("started_at", LocalDateTime.class))
            .finishedAt(rs.getObject("finished_at", LocalDateTime.class))
            .build();

    /**
     * RowMapper для преобразования ResultSet в PokerAction
     */
    private final RowMapper<PokerAction> pokerActionRowMapper = (rs, rowNum) -> PokerAction.builder()
            .actionId(rs.getLong("action_id"))
            .gameId(rs.getLong("game_id"))
            .playerId(rs.getString("player_id"))
            .actionType(rs.getString("action_type"))
            .betAmount(rs.getInt("bet_amount"))
            .stackAfter(rs.getInt("stack_after"))
            .potAfter(rs.getInt("pot_after"))
            .roundNumber(rs.getInt("round_number"))
            .actionTime(rs.getObject("action_time", LocalDateTime.class))
            .build();

    /**
     * RowMapper для преобразования ResultSet в PokerHand
     */
    private final RowMapper<PokerHand> pokerHandRowMapper = (rs, rowNum) -> PokerHand.builder()
            .handId(rs.getLong("hand_id"))
            .gameId(rs.getLong("game_id"))
            .handNumber(rs.getInt("hand_number"))
            .holeCards(parseJsonArray(rs.getString("hole_cards")))
            .communityCards(parseJsonArray(rs.getString("community_cards")))
            .playerId(rs.getString("player_id"))
            .handRank(rs.getString("hand_rank"))
            .handDescription(rs.getString("hand_description"))
            .handValue(rs.getInt("hand_value"))
            .isWinner(rs.getBoolean("is_winner"))
            .winnings(rs.getInt("winnings"))
            .handTime(rs.getObject("hand_time", LocalDateTime.class))
            .build();

    /**
     * RowMapper для преобразования ResultSet в PokerPlayer
     */
    private final RowMapper<PokerPlayer> pokerPlayerRowMapper = (rs, rowNum) -> PokerPlayer.builder()
            .playerId(rs.getString("player_id"))
            .playerName(rs.getString("player_name"))
            .chips(rs.getInt("chips"))
            .seatNumber(rs.getInt("seat_number"))
            .isActive(rs.getBoolean("is_active"))
            .isDealer(rs.getBoolean("is_dealer"))
            .handsPlayed(rs.getInt("hands_played"))
            .handsWon(rs.getInt("hands_won"))
            .winRate(rs.getDouble("win_rate"))
            .joinedAt(rs.getObject("joined_at", LocalDateTime.class))
            .lastActionAt(rs.getObject("last_action_at", LocalDateTime.class))
            .build();

    // ==================== РАБОТА С ИГРАМИ ====================

    /**
     * Создать новую игру
     */
    public void createGame(PokerGame game) {
        String sql = """
            INSERT INTO app_db.poker_games 
            (game_id, game_name, game_state, pot, current_bet, min_bet, 
             max_players, small_blind, big_blind, started_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql,
                game.getGameId(),
                game.getGameName(),
                game.getGameState(),
                game.getPot(),
                game.getCurrentBet(),
                game.getMinBet(),
                game.getMaxPlayers(),
                game.getSmallBlind(),
                game.getBigBlind(),
                game.getStartedAt()
        );
        log.info("✅ Game created with ID: {}", game.getGameId());
    }

    /**
     * Получить игру по ID
     */
    public PokerGame getGameById(Long gameId) {
        String sql = "SELECT * FROM app_db.poker_games WHERE game_id = ?";
        List<PokerGame> games = jdbcTemplate.query(sql, pokerGameRowMapper, gameId);
        return games.isEmpty() ? null : games.get(0);
    }

    /**
     * Получить все активные игры
     */
    public List<PokerGame> getActiveGames() {
        String sql = """
            SELECT * FROM app_db.poker_games 
            WHERE game_state NOT IN ('FINISHED') 
            ORDER BY started_at DESC 
            LIMIT 10
            """;
        return jdbcTemplate.query(sql, pokerGameRowMapper);
    }

    /**
     * Обновить состояние игры
     */
    public void updateGameState(Long gameId, String state, Integer pot, Integer currentBet, String currentPlayerId) {
        String sql = """
            UPDATE app_db.poker_games 
            SET game_state = ?, pot = ?, current_bet = ?, current_player_id = ? 
            WHERE game_id = ?
            """;

        jdbcTemplate.update(sql, state, pot, currentBet, currentPlayerId, gameId);
        log.info("🔄 Game {} state updated to: {}", gameId, state);
    }

    /**
     * Завершить игру
     */
    public void finishGame(Long gameId, String winnerId, Integer winnerAmount) {
        String sql = """
            UPDATE app_db.poker_games 
            SET game_state = 'FINISHED', winner_id = ?, winner_amount = ?, finished_at = now() 
            WHERE game_id = ?
            """;

        jdbcTemplate.update(sql, winnerId, winnerAmount, gameId);
        log.info("🏆 Game {} finished. Winner: {}, Amount: {}", gameId, winnerId, winnerAmount);
    }

    /**
     * Обновить банк игры
     */
    public void updatePot(Long gameId, Integer pot) {
        String sql = "UPDATE app_db.poker_games SET pot = ? WHERE game_id = ?";
        jdbcTemplate.update(sql, pot, gameId);
    }

    // ==================== РАБОТА С ДЕЙСТВИЯМИ ====================

    /**
     * Сохранить действие игрока
     */
    public void saveAction(PokerAction action) {
        String sql = """
            INSERT INTO app_db.poker_actions 
            (action_id, game_id, player_id, action_type, bet_amount, 
             stack_after, pot_after, round_number, action_time) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql,
                action.getActionId(),
                action.getGameId(),
                action.getPlayerId(),
                action.getActionType(),
                action.getBetAmount(),
                action.getStackAfter(),
                action.getPotAfter(),
                action.getRoundNumber(),
                action.getActionTime()
        );
        log.debug("🎯 Action saved: {} for player {} in game {}",
                action.getActionType(), action.getPlayerId(), action.getGameId());
    }

    /**
     * Получить все действия игры
     */
    public List<PokerAction> getGameActions(Long gameId) {
        String sql = "SELECT * FROM app_db.poker_actions WHERE game_id = ? ORDER BY action_time";
        return jdbcTemplate.query(sql, pokerActionRowMapper, gameId);
    }

    /**
     * Получить действия игрока в игре
     */
    public List<PokerAction> getPlayerActions(Long gameId, String playerId) {
        String sql = """
            SELECT * FROM app_db.poker_actions 
            WHERE game_id = ? AND player_id = ? 
            ORDER BY action_time
            """;
        return jdbcTemplate.query(sql, pokerActionRowMapper, gameId, playerId);
    }

    /**
     * Получить последнее действие в игре
     */
    public PokerAction getLastAction(Long gameId) {
        String sql = """
            SELECT * FROM app_db.poker_actions 
            WHERE game_id = ? 
            ORDER BY action_time DESC 
            LIMIT 1
            """;
        List<PokerAction> actions = jdbcTemplate.query(sql, pokerActionRowMapper, gameId);
        return actions.isEmpty() ? null : actions.get(0);
    }

    // ==================== РАБОТА С РАЗДАЧАМИ ====================

    /**
     * Сохранить раздачу
     */
    public void saveHand(PokerHand hand) {
        String sql = """
            INSERT INTO app_db.poker_hands 
            (hand_id, game_id, hand_number, hole_cards, community_cards, 
             player_id, hand_rank, hand_description, hand_value, is_winner, winnings, hand_time) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql,
                hand.getHandId(),
                hand.getGameId(),
                hand.getHandNumber(),
                convertToJsonArray(hand.getHoleCards()),
                convertToJsonArray(hand.getCommunityCards()),
                hand.getPlayerId(),
                hand.getHandRank(),
                hand.getHandDescription(),
                hand.getHandValue(),
                hand.getIsWinner() ? 1 : 0,
                hand.getWinnings(),
                hand.getHandTime()
        );
        log.info("🃏 Hand saved: {} for player {}", hand.getHandId(), hand.getPlayerId());
    }

    /**
     * Получить все раздачи игры
     */
    public List<PokerHand> getGameHands(Long gameId) {
        String sql = "SELECT * FROM app_db.poker_hands WHERE game_id = ? ORDER BY hand_number";
        return jdbcTemplate.query(sql, pokerHandRowMapper, gameId);
    }

    /**
     * Получить выигрышные раздачи игрока
     */
    public List<PokerHand> getWinningHands(String playerId) {
        String sql = """
            SELECT * FROM app_db.poker_hands 
            WHERE player_id = ? AND is_winner = 1 
            ORDER BY hand_time DESC 
            LIMIT 50
            """;
        return jdbcTemplate.query(sql, pokerHandRowMapper, playerId);
    }

    // ==================== РАБОТА С ИГРОКАМИ ====================

    /**
     * Добавить игрока в игру
     */
    public void addPlayer(PokerPlayer player) {
        String sql = """
            INSERT INTO app_db.poker_players 
            (player_id, player_name, chips, seat_number, is_active, 
             is_dealer, hands_played, hands_won, win_rate, joined_at, last_action_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql,
                player.getPlayerId(),
                player.getPlayerName(),
                player.getChips(),
                player.getSeatNumber(),
                player.getIsActive() ? 1 : 0,
                player.getIsDealer() ? 1 : 0,
                player.getHandsPlayed(),
                player.getHandsWon(),
                player.getWinRate(),
                player.getJoinedAt(),
                player.getLastActionAt()
        );
        log.info("👤 Player {} added to game", player.getPlayerName());
    }

    /**
     * Обновить состояние игрока
     */
    public void updatePlayerState(String playerId, Integer chips, Boolean isActive, String lastAction) {
        String sql = """
            UPDATE app_db.poker_players 
            SET chips = ?, is_active = ?, last_action_at = now() 
            WHERE player_id = ?
            """;

        jdbcTemplate.update(sql, chips, isActive ? 1 : 0, playerId);
    }

    /**
     * Получить игрока по ID
     */
    public PokerPlayer getPlayerById(String playerId) {
        String sql = "SELECT * FROM app_db.poker_players WHERE player_id = ?";
        List<PokerPlayer> players = jdbcTemplate.query(sql, pokerPlayerRowMapper, playerId);
        return players.isEmpty() ? null : players.get(0);
    }

    /**
     * Получить всех игроков в игре
     */
    public List<PokerPlayer> getGamePlayers(Long gameId) {
        // Упрощенная версия - в реальности нужно связывать через отдельную таблицу
        String sql = "SELECT * FROM app_db.poker_players WHERE is_active = 1 ORDER BY seat_number";
        return jdbcTemplate.query(sql, pokerPlayerRowMapper);
    }

    /**
     * Обновить статистику игрока после раздачи
     */
    public void updatePlayerStats(String playerId, boolean won, int winnings) {
        String sql = """
            UPDATE app_db.poker_players 
            SET hands_played = hands_played + 1,
                hands_won = hands_won + ?,
                win_rate = (hands_won + ?) * 100.0 / (hands_played + 1),
                chips = chips + ?
            WHERE player_id = ?
            """;

        jdbcTemplate.update(sql, won ? 1 : 0, won ? 1 : 0, winnings, playerId);
    }

    // ==================== СТАТИСТИКА ====================

    /**
     * Получить общее количество игр
     */
    public Integer getTotalGamesCount() {
        String sql = "SELECT COUNT(*) FROM app_db.poker_games";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    /**
     * Получить общую сумму выигрышей
     */
    public Long getTotalWinnings() {
        String sql = "SELECT SUM(winner_amount) FROM app_db.poker_games WHERE winner_amount IS NOT NULL";
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result != null ? result : 0L;
    }

    /**
     * Получить статистику по типам действий
     */
    public List<Object[]> getActionStats() {
        String sql = """
            SELECT 
                action_type, 
                COUNT(*) as count, 
                AVG(bet_amount) as avg_bet,
                SUM(bet_amount) as total_bet
            FROM app_db.poker_actions 
            GROUP BY action_type 
            ORDER BY count DESC
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
                rs.getString("action_type"),
                rs.getLong("count"),
                rs.getDouble("avg_bet"),
                rs.getLong("total_bet")
        });
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    /**
     * Преобразовать список строк в JSON массив для ClickHouse
     */
    private String convertToJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Распарсить JSON массив из ClickHouse в список строк
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.equals("[]") || json.isEmpty()) {
            return List.of();
        }
        // Упрощенный парсинг - убираем квадратные скобки и кавычки
        String cleaned = json.replace("[", "").replace("]", "").replace("\"", "");
        if (cleaned.isEmpty()) {
            return List.of();
        }
        return List.of(cleaned.split(","));
    }
}
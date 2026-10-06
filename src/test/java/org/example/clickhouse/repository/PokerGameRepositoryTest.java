package org.example.clickhouse.repository;

import org.example.clickhouse.model.PokerAction;
import org.example.clickhouse.model.PokerGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Тесты репозитория покерных данных")
class PokerGameRepositoryTest {

    @Autowired
    private PokerGameRepository pokerGameRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testGameId;

    @BeforeEach
    void setUp() {
        testGameId = System.currentTimeMillis();

        // Очищаем тестовые данные
        jdbcTemplate.execute("TRUNCATE TABLE IF EXISTS app_db.poker_games");
        jdbcTemplate.execute("TRUNCATE TABLE IF EXISTS app_db.poker_actions");
    }

    @Test
    @DisplayName("Создание игры сохраняется в БД")
    void createGame() {
        PokerGame game = PokerGame.builder()
                .gameId(testGameId)
                .gameName("Test Game")
                .gameState("WAITING")
                .pot(0)
                .currentBet(0)
                .minBet(10)
                .maxPlayers(6)
                .smallBlind(5)
                .bigBlind(10)
                .startedAt(LocalDateTime.now())
                .build();

        pokerGameRepository.createGame(game);

        PokerGame savedGame = pokerGameRepository.getGameById(testGameId);

        assertThat(savedGame).isNotNull();
        assertThat(savedGame.getGameName()).isEqualTo("Test Game");
        assertThat(savedGame.getGameState()).isEqualTo("WAITING");
    }

    @Test
    @DisplayName("Сохранение действия игрока")
    void saveAction() {
        // Сначала создаем игру
        PokerGame game = PokerGame.builder()
                .gameId(testGameId)
                .gameName("Test Game")
                .gameState("PRE_FLOP")
                .pot(0)
                .currentBet(0)
                .minBet(10)
                .maxPlayers(6)
                .smallBlind(5)
                .bigBlind(10)
                .startedAt(LocalDateTime.now())
                .build();
        pokerGameRepository.createGame(game);

        // Сохраняем действие
        PokerAction action = PokerAction.builder()
                .actionId(System.currentTimeMillis())
                .gameId(testGameId)
                .playerId("player1")
                .actionType("call")
                .betAmount(10)
                .stackAfter(990)
                .potAfter(20)
                .roundNumber(1)
                .actionTime(LocalDateTime.now())
                .build();

        pokerGameRepository.saveAction(action);

        List<PokerAction> actions = pokerGameRepository.getGameActions(testGameId);

        assertThat(actions).isNotEmpty();
        assertThat(actions.get(0).getActionType()).isEqualTo("call");
        assertThat(actions.get(0).getBetAmount()).isEqualTo(10);
    }

    @Test
    @DisplayName("Обновление состояния игры")
    void updateGameState() {
        // Создаем игру
        PokerGame game = PokerGame.builder()
                .gameId(testGameId)
                .gameName("Test Game")
                .gameState("WAITING")
                .pot(0)
                .currentBet(0)
                .minBet(10)
                .maxPlayers(6)
                .smallBlind(5)
                .bigBlind(10)
                .startedAt(LocalDateTime.now())
                .build();
        pokerGameRepository.createGame(game);

        // Обновляем состояние
        pokerGameRepository.updateGameState(testGameId, "FLOP", 100, 20, "player1");

        PokerGame updatedGame = pokerGameRepository.getGameById(testGameId);

        assertThat(updatedGame.getGameState()).isEqualTo("FLOP");
        assertThat(updatedGame.getPot()).isEqualTo(100);
        assertThat(updatedGame.getCurrentBet()).isEqualTo(20);
    }

    @Test
    @DisplayName("Получение активных игр")
    void getActiveGames() {
        // Создаем несколько игр
        for (int i = 1; i <= 3; i++) {
            PokerGame game = PokerGame.builder()
                    .gameId(testGameId + i)
                    .gameName("Game " + i)
                    .gameState(i == 3 ? "FINISHED" : "WAITING")
                    .pot(0)
                    .currentBet(0)
                    .minBet(10)
                    .maxPlayers(6)
                    .smallBlind(5)
                    .bigBlind(10)
                    .startedAt(LocalDateTime.now())
                    .build();
            pokerGameRepository.createGame(game);
        }

        List<PokerGame> activeGames = pokerGameRepository.getActiveGames();

        assertThat(activeGames).hasSize(2);
        assertThat(activeGames).allMatch(game -> !game.getGameState().equals("FINISHED"));
    }

    @Test
    @DisplayName("Завершение игры")
    void finishGame() {
        // Создаем игру
        PokerGame game = PokerGame.builder()
                .gameId(testGameId)
                .gameName("Test Game")
                .gameState("RIVER")
                .pot(500)
                .currentBet(50)
                .minBet(10)
                .maxPlayers(6)
                .smallBlind(5)
                .bigBlind(10)
                .startedAt(LocalDateTime.now())
                .build();
        pokerGameRepository.createGame(game);

        // Завершаем игру
        pokerGameRepository.finishGame(testGameId, "player1", 500);

        PokerGame finishedGame = pokerGameRepository.getGameById(testGameId);

        assertThat(finishedGame.getGameState()).isEqualTo("FINISHED");
        assertThat(finishedGame.getWinnerId()).isEqualTo("player1");
        assertThat(finishedGame.getWinnerAmount()).isEqualTo(500);
        assertThat(finishedGame.getFinishedAt()).isNotNull();
    }
}
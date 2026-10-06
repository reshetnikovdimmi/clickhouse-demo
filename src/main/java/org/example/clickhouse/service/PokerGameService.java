package org.example.clickhouse.service;

import lombok.extern.slf4j.Slf4j;
import org.example.clickhouse.game.GameState;
import org.example.clickhouse.game.PokerGameEngine;
import org.example.clickhouse.model.PokerAction;
import org.example.clickhouse.model.PokerGame;
import org.example.clickhouse.repository.PokerGameRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PokerGameService {

    private final PokerGameRepository pokerGameRepository;

    // Активные игры в памяти
    private final Map<String, PokerGameEngine> activeGames = new ConcurrentHashMap<>();

    @Autowired
    public PokerGameService(PokerGameRepository pokerGameRepository) {
        this.pokerGameRepository = pokerGameRepository;
    }

    /**
     * Создать новую игру
     */
    public String createGame(String gameName, int minBet, int maxPlayers,
                             int startingChips, int smallBlind, int bigBlind) {
        return createGame(gameName, minBet, maxPlayers, startingChips, smallBlind, bigBlind, null);
    }

    /**
     * Создать новую игру с указанием создателя
     */
    public String createGame(String gameName, int minBet, int maxPlayers,
                             int startingChips, int smallBlind, int bigBlind,
                             String creatorPlayerId) {
        String gameId = generateGameId();

        // Сохраняем в БД
        PokerGame game = PokerGame.builder()
                .gameId(Long.parseLong(gameId))
                .gameName(gameName)
                .gameState("WAITING")
                .pot(0)
                .currentBet(0)
                .minBet(minBet)
                .maxPlayers(maxPlayers)
                .smallBlind(smallBlind)
                .bigBlind(bigBlind)
                .startedAt(LocalDateTime.now())
                .build();

        pokerGameRepository.createGame(game);

        // Создаем игровой движок
        PokerGameEngine engine = new PokerGameEngine(
                gameId, smallBlind, bigBlind, minBet, maxPlayers
        );

        // ✅ Сохраняем создателя игры
        if (creatorPlayerId != null) {
            engine.getGameState().setCreatorPlayerId(creatorPlayerId);
        }
        engine.getGameState().setIsGameRunning(false);

        activeGames.put(gameId, engine);

        log.info("✅ Game created: {} with ID: {}, creator: {}", gameName, gameId, creatorPlayerId);
        return gameId;
    }

    /**
     * Получить игровой движок
     */
    public PokerGameEngine getGameEngine(String gameId) {
        return activeGames.get(gameId);
    }

    /**
     * Добавить игрока в игру
     */
    public boolean addPlayerToGame(String gameId, String playerId, String playerName, int chips) {
        PokerGameEngine engine = activeGames.get(gameId);
        if (engine == null) {
            log.warn("Game {} not found", gameId);
            return false;
        }

        int seatNumber = engine.getGameState().getPlayers().size() + 1;
        return engine.addPlayer(playerId, playerName, chips, seatNumber);
    }

    /**
     * Обработать действие игрока
     */
    public boolean processAction(String gameId, String playerId, String action, int betAmount) {
        PokerGameEngine engine = activeGames.get(gameId);
        if (engine == null) {
            log.warn("Game {} not found", gameId);
            return false;
        }

        boolean result = engine.processAction(playerId, action, betAmount);

        if (result) {
            GameState.PlayerState player = engine.getGameState().getPlayer(playerId);
            if (player != null) {
                PokerAction pokerAction = PokerAction.builder()
                        .actionId(System.currentTimeMillis())
                        .gameId(Long.parseLong(gameId))
                        .playerId(playerId)
                        .actionType(action)
                        .betAmount(betAmount)
                        .stackAfter(player.getChips())
                        .potAfter(engine.getGameState().getPot())
                        .roundNumber(engine.getGameState().getCurrentRound())
                        .actionTime(LocalDateTime.now())
                        .build();
                pokerGameRepository.saveAction(pokerAction);
            }
        }

        return result;
    }

    /**
     * Начать новую раздачу
     */
    public void startNewHand(String gameId) {
        PokerGameEngine engine = activeGames.get(gameId);
        if (engine != null) {
            engine.startNewHand();
            engine.getGameState().setHandEnded(false);
            engine.getGameState().setWinnerName(null);
            engine.getGameState().setWonAmount(0);
        }
    }

    /**
     * Перейти к следующему этапу
     */
    public void nextStage(String gameId) {
        PokerGameEngine engine = activeGames.get(gameId);
        if (engine != null) {
            engine.nextStage();
        }
    }

    /**
     * Получить состояние игры
     */
    public GameState getGameState(String gameId) {
        PokerGameEngine engine = activeGames.get(gameId);
        return engine != null ? engine.getGameState() : null;
    }

    /**
     * Получить все активные игры
     */
    public Map<String, PokerGameEngine> getActiveGames() {
        return activeGames;
    }

    /**
     * Удалить игру
     */
    public void removeGame(String gameId) {
        activeGames.remove(gameId);
        log.info("Game {} removed", gameId);
    }

    private String generateGameId() {
        return String.valueOf(System.currentTimeMillis());
    }

    /**
     * Обработать действие игрока и вернуть результат
     * @return Map с результатами обработки (handEnded, stageChanged и т.д.)
     */
    public PlayerActionResult processPlayerAction(String gameId, String playerId, String action, int betAmount) {
        PokerGameEngine engine = activeGames.get(gameId);
        if (engine == null) {
            log.warn("Game {} not found", gameId);
            return PlayerActionResult.error("Game not found");
        }

        // Сохраняем состояние ДО действия
        GameState beforeState = engine.getGameState().copy();
        int potBefore = beforeState.getPot();
        int playersInHandBefore = beforeState.getPlayersInHandCount();

        // Обрабатываем действие
        boolean success = engine.processAction(playerId, action, betAmount);

        if (!success) {
            return PlayerActionResult.error("Invalid action");
        }

        // Получаем состояние ПОСЛЕ действия
        GameState afterState = engine.getGameState();
        int playersInHandAfter = afterState.getPlayersInHandCount();

        // Проверяем флаг handEnded из GameState
        boolean handEnded = afterState.isHandEnded();
        String winnerName = afterState.getWinnerName();
        int wonAmount = afterState.getWonAmount();

        // Если handEnded не установлен, проверяем по количеству игроков
        if (!handEnded) {
            if (playersInHandBefore == 1) {
                handEnded = true;
                winnerName = beforeState.getPlayersInHand().get(0).getPlayerName();
                wonAmount = potBefore;
            } else if (playersInHandBefore == 2 && playersInHandAfter == 1) {
                handEnded = true;
                var remainingPlayers = afterState.getPlayersInHand();
                if (!remainingPlayers.isEmpty()) {
                    winnerName = remainingPlayers.get(0).getPlayerName();
                    wonAmount = potBefore;
                }
            }
        }

        // Проверяем, завершен ли раунд (для STAGE_CHANGED)
        boolean stageChanged = false;
        String newStage = null;

        if (!handEnded && engine.isRoundComplete()) {
            engine.nextStage();
            afterState = engine.getGameState();
            newStage = afterState.getState();
            stageChanged = true;
        }

        // Логируем результат
        if (handEnded) {
            log.info("=== HAND ENDED in Service ===");
            log.info("Winner: {}, Won amount: {}", winnerName, wonAmount);
        }

        return PlayerActionResult.builder()
                .success(true)
                .handEnded(handEnded)
                .winnerName(winnerName)
                .wonAmount(wonAmount)
                .stageChanged(stageChanged)
                .newStage(newStage)
                .communityCards(afterState.getCommunityCards())
                .players(afterState.getPlayers())
                .pot(afterState.getPot())
                .currentBet(afterState.getCurrentBet())
                .currentPlayerId(afterState.getCurrentPlayerId())
                .build();
    }

    /**
     * Результат обработки действия игрока
     */
    @lombok.Builder
    @lombok.Data
    public static class PlayerActionResult {
        private boolean success;
        private String error;
        private boolean handEnded;
        private String winnerName;
        private int wonAmount;
        private boolean stageChanged;
        private String newStage;
        private java.util.List<org.example.clickhouse.game.Card> communityCards;
        private java.util.Map<String, org.example.clickhouse.game.GameState.PlayerState> players;
        private int pot;
        private int currentBet;
        private String currentPlayerId;

        public static PlayerActionResult error(String error) {
            return PlayerActionResult.builder()
                    .success(false)
                    .error(error)
                    .build();
        }
    }

}
package org.example.clickhouse.grpc;

import com.example.clickhouse.grpc.*;
import com.example.clickhouse.grpc.PokerServiceGrpc;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.clickhouse.game.GameState;
import org.example.clickhouse.model.PokerAction;
import org.example.clickhouse.model.PokerGame;
import org.example.clickhouse.repository.PokerGameRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class PokerGrpcService extends PokerServiceGrpc.PokerServiceImplBase {

    private final PokerGameRepository pokerGameRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Хранилище активных игр в памяти (GameState)
    private final Map<String, GameSession> activeGames = new ConcurrentHashMap<>();

    // Хранилище сессий игроков (playerId -> сессия)
    private final Map<String, PlayerSession> playerSessions = new ConcurrentHashMap<>();

    // =====================================================
    // 1. Создание игры
    // =====================================================

    @Override
    public void createGame(CreateGameRequest request, StreamObserver<CreateGameResponse> responseObserver) {
        try {
            // Генерируем ID игры
            String gameId = generateGameId();

            // Создаем игру в базе данных
            PokerGame game = PokerGame.builder()
                    .gameId(Long.parseLong(gameId))
                    .gameName(request.getGameName())
                    .gameState("WAITING")
                    .pot(0)
                    .currentBet(0)
                    .minBet(request.getMinBet())
                    .maxPlayers(request.getMaxPlayers())
                    .smallBlind(request.getSmallBlind())
                    .bigBlind(request.getBigBlind())
                    .startedAt(LocalDateTime.now())
                    .build();

            pokerGameRepository.createGame(game);

            // Создаем игровую сессию в памяти
            GameSession session = new GameSession();
            session.gameId = gameId;
            session.gameName = request.getGameName();
            session.minBet = request.getMinBet();
            session.maxPlayers = request.getMaxPlayers();
            session.startingChips = request.getStartingChips();
            session.smallBlind = request.getSmallBlind();
            session.bigBlind = request.getBigBlind();
            session.state = "WAITING";
            session.pot = 0;
            session.currentBet = 0;
            session.players = new ConcurrentHashMap<>();

            activeGames.put(gameId, session);

            log.info("✅ Game created: {} with ID: {}", request.getGameName(), gameId);

            CreateGameResponse response = CreateGameResponse.newBuilder()
                    .setSuccess(true)
                    .setGameId(gameId)
                    .setMessage("Game created successfully")
                    .setCreatedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Failed to create game", e);
            CreateGameResponse response = CreateGameResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed: " + e.getMessage())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    // =====================================================
    // 2. Подключение к игре (двунаправленный стриминг)
    // =====================================================

    @Override
    public StreamObserver<PlayerAction> joinGame(StreamObserver<GameEvent> responseObserver) {
        log.info("🎮 New player connecting to poker game");

        return new StreamObserver<PlayerAction>() {
            private String playerId;
            private String playerName;
            private String gameId;
            private GameSession session;

            @Override
            public void onNext(PlayerAction action) {
                try {
                    playerId = action.getPlayerId();
                    gameId = action.getGameId();

                    log.info("Player {} action in game {}: {}", playerId, gameId, action.getAction());

                    // Получаем игровую сессию
                    session = activeGames.get(gameId);
                    if (session == null) {
                        sendError(responseObserver, "Game not found: " + gameId);
                        return;
                    }

                    // Обрабатываем действие в зависимости от типа
                    switch (action.getAction()) {
                        case "join":
                            handleJoin(action, responseObserver, session);
                            break;
                        case "fold":
                        case "check":
                        case "call":
                        case "raise":
                        case "all_in":
                        case "bet":
                            handlePlayerAction(action, responseObserver, session);
                            break;
                        default:
                            log.warn("Unknown action: {}", action.getAction());
                    }

                } catch (Exception e) {
                    log.error("Error processing player action", e);
                    sendError(responseObserver, e.getMessage());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Stream error from player {} in game {}", playerId, gameId, t);
                if (gameId != null && playerId != null) {
                    removePlayerFromGame(gameId, playerId);
                }
            }

            @Override
            public void onCompleted() {
                log.info("Player {} left game {}", playerId, gameId);
                if (gameId != null && playerId != null) {
                    removePlayerFromGame(gameId, playerId);
                }
                responseObserver.onCompleted();
            }
        };
    }

    /**
     * Обработка подключения игрока
     */
    private void handleJoin(PlayerAction action, StreamObserver<GameEvent> responseObserver, GameSession session) {
        String playerId = action.getPlayerId();
        String playerName = action.getPlayerId(); // В реальности имя передается отдельно

        // Проверяем, не заполнен ли стол
        if (session.players.size() >= session.maxPlayers) {
            sendError(responseObserver, "Game is full");
            return;
        }

        // Добавляем игрока
        GameSession.PlayerInfo player = session.new PlayerInfo();
        player.playerId = playerId;
        player.playerName = playerName;
        player.chips = session.startingChips;
        player.seatNumber = session.players.size();
        player.isActive = true;

        session.players.put(playerId, player);

        // Сохраняем сессию для отправки сообщений
        PlayerSession playerSession = new PlayerSession();
        playerSession.responseObserver = responseObserver;
        playerSession.gameId = action.getGameId();
        playerSessions.put(playerId, playerSession);

        // Отправляем событие о подключении
        GameEvent event = GameEvent.newBuilder()
                .setEventType("player_joined")
                .setGameId(action.getGameId())
                .setData(String.format("{\"player_id\":\"%s\",\"player_name\":\"%s\",\"players_count\":%d}",
                        playerId, playerName, session.players.size()))
                .setTimestamp(System.currentTimeMillis() / 1000)
                .build();

        responseObserver.onNext(event);

        // Рассылаем обновление всем игрокам
        broadcastToGame(action.getGameId(), event);

        log.info("👤 Player {} joined game {}, total players: {}",
                playerName, action.getGameId(), session.players.size());
    }

    /**
     * Обработка действия игрока
     */
    private void handlePlayerAction(PlayerAction action, StreamObserver<GameEvent> responseObserver, GameSession session) {
        GameSession.PlayerInfo player = session.players.get(action.getPlayerId());
        if (player == null || !player.isActive) {
            sendError(responseObserver, "Player not active");
            return;
        }

        // Сохраняем действие в БД
        PokerAction pokerAction = PokerAction.builder()
                .actionId(System.currentTimeMillis())
                .gameId(Long.parseLong(action.getGameId()))
                .playerId(action.getPlayerId())
                .actionType(action.getAction())
                .betAmount(action.getBetAmount())
                .stackAfter(player.chips - action.getBetAmount())
                .potAfter(session.pot + action.getBetAmount())
                .actionTime(LocalDateTime.now())
                .build();

        pokerGameRepository.saveAction(pokerAction);

        // Обновляем состояние игры в памяти
        if (action.getBetAmount() > 0) {
            player.chips -= action.getBetAmount();
            session.pot += action.getBetAmount();
            if (action.getBetAmount() > session.currentBet) {
                session.currentBet = action.getBetAmount();
            }
        }

        // Отправляем событие о действии
        GameEvent event = GameEvent.newBuilder()
                .setEventType("player_action")
                .setGameId(action.getGameId())
                .setData(String.format("{\"player_id\":\"%s\",\"action\":\"%s\",\"bet_amount\":%d,\"pot\":%d}",
                        action.getPlayerId(), action.getAction(), action.getBetAmount(), session.pot))
                .setTimestamp(System.currentTimeMillis() / 1000)
                .build();

        responseObserver.onNext(event);
        broadcastToGame(action.getGameId(), event);

        log.info("🎯 Player {} performed {} in game {}", action.getPlayerId(), action.getAction(), action.getGameId());
    }

    /**
     * Удалить игрока из игры
     */
    private void removePlayerFromGame(String gameId, String playerId) {
        GameSession session = activeGames.get(gameId);
        if (session != null) {
            session.players.remove(playerId);
            playerSessions.remove(playerId);

            log.info("👋 Player {} removed from game {}", playerId, gameId);

            // Если игроков не осталось, удаляем игру
            if (session.players.isEmpty()) {
                activeGames.remove(gameId);
                log.info("🗑️ Game {} removed (no players)", gameId);
            }
        }
    }

    /**
     * Отправить ошибку клиенту
     */
    private void sendError(StreamObserver<GameEvent> responseObserver, String message) {
        GameEvent errorEvent = GameEvent.newBuilder()
                .setEventType("error")
                .setData("{\"message\":\"" + message + "\"}")
                .setTimestamp(System.currentTimeMillis() / 1000)
                .build();
        responseObserver.onNext(errorEvent);
    }

    /**
     * Разослать событие всем игрокам в игре
     */
    private void broadcastToGame(String gameId, GameEvent event) {
        activeGames.get(gameId).players.keySet().forEach(playerId -> {
            PlayerSession ps = playerSessions.get(playerId);
            if (ps != null && ps.gameId.equals(gameId)) {
                try {
                    ps.responseObserver.onNext(event);
                } catch (Exception e) {
                    log.error("Failed to send event to player {}", playerId, e);
                }
            }
        });
    }

    // =====================================================
    // 3. Получение списка активных игр
    // =====================================================

    @Override
    public void getActiveGames(Empty request, StreamObserver<GamesList> responseObserver) {
        GamesList.Builder gamesList = GamesList.newBuilder();

        activeGames.forEach((gameId, session) -> {
            GameInfo gameInfo = GameInfo.newBuilder()
                    .setGameId(gameId)
                    .setGameName(session.gameName)
                    .setStatus(session.state)
                    .setPlayersCount(session.players.size())
                    .setPot(session.pot)
                    .build();
            gamesList.addGames(gameInfo);
        });

        responseObserver.onNext(gamesList.build());
        responseObserver.onCompleted();
    }

    // =====================================================
    // 4. Получение статистики игры
    // =====================================================

    @Override
    public void getGameStats(GameStatsRequest request, StreamObserver<GameStatsResponse> responseObserver) {
        try {
            GameSession session = activeGames.get(request.getGameId());

            if (session == null) {
                // Пробуем получить из БД
                PokerGame game = pokerGameRepository.getGameById(Long.parseLong(request.getGameId()));
                if (game == null) {
                    responseObserver.onError(new RuntimeException("Game not found"));
                    return;
                }

                GameStatsResponse response = GameStatsResponse.newBuilder()
                        .setGameId(String.valueOf(game.getGameId()))
                        .setGameName(game.getGameName())
                        .setGameState(game.getGameState())
                        .setPot(game.getPot())
                        .setCurrentBet(game.getCurrentBet())
                        .setPlayersCount6(0)
                        .setMaxPlayers(game.getMaxPlayers())
                        .setSmallBlind(game.getSmallBlind())
                        .setBigBlind(game.getBigBlind())
                        .setStartedAt(game.getStartedAt().atZone(ZoneId.systemDefault()).toEpochSecond())
                        .build();

                responseObserver.onNext(response);
            } else {
                // Из памяти
                GameStatsResponse.Builder builder = GameStatsResponse.newBuilder()
                        .setGameId(session.gameId)
                        .setGameName(session.gameName)
                        .setGameState(session.state)
                        .setPot(session.pot)
                        .setCurrentBet(session.currentBet)
                        .setPlayersCount6(session.players.size())
                        .setMaxPlayers(session.maxPlayers)
                        .setSmallBlind(session.smallBlind)
                        .setBigBlind(session.bigBlind);

                session.players.values().forEach(player -> {
                    builder.addPlayers11(PlayerStat.newBuilder()
                            .setPlayerId(player.playerId)
                            .setPlayerName(player.playerName)
                            .setChips(player.chips)
                            .setCurrentBet(player.currentBet)
                            .setIsActive(player.isActive)
                            .build());
                });

                responseObserver.onNext(builder.build());
            }

            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Failed to get game stats", e);
            responseObserver.onError(e);
        }
    }

    // =====================================================
    // 5. Получение истории игры
    // =====================================================

    @Override
    public void getGameHistory(GameHistoryRequest request, StreamObserver<GameHistoryResponse> responseObserver) {
        try {
            List<PokerAction> actions = pokerGameRepository.getGameActions(Long.parseLong(request.getGameId()));

            GameHistoryResponse.Builder builder = GameHistoryResponse.newBuilder()
                    .setGameId(request.getGameId())
                    .setTotalCount(actions.size());

            int limit = request.getLimit() > 0 ? request.getLimit() : 50;
            int offset = request.getOffset();

            actions.stream()
                    .skip(offset)
                    .limit(limit)
                    .forEach(action -> {
                        builder.addActions(HistoryAction.newBuilder()
                                .setPlayerId(action.getPlayerId())
                                .setAction(action.getActionType())
                                .setBetAmount(action.getBetAmount())
                                .setRoundNumber(action.getRoundNumber())
                                .setActionTime(action.getActionTime().atZone(ZoneId.systemDefault()).toEpochSecond())
                                .build());
                    });

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Failed to get game history", e);
            responseObserver.onError(e);
        }
    }

    // =====================================================
    // Вспомогательные методы
    // =====================================================

    private String generateGameId() {
        return String.valueOf(System.currentTimeMillis());
    }

    // =====================================================
    // Внутренние классы
    // =====================================================

    /**
     * Игровая сессия в памяти
     */
    private static class GameSession {
        String gameId;
        String gameName;
        String state;
        int minBet;
        int maxPlayers;
        int startingChips;
        int smallBlind;
        int bigBlind;
        int pot;
        int currentBet;
        Map<String, PlayerInfo> players = new ConcurrentHashMap<>();

        class PlayerInfo {
            String playerId;
            String playerName;
            int chips;
            int currentBet;
            int seatNumber;
            boolean isActive;
            boolean isFolded;
        }
    }

    /**
     * Сессия игрока для WebSocket/gRPC
     */
    private static class PlayerSession {
        StreamObserver<GameEvent> responseObserver;
        String gameId;
    }
}
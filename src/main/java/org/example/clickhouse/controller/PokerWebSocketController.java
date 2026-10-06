package org.example.clickhouse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.clickhouse.game.GameState;
import org.example.clickhouse.game.PokerGameEngine;
import org.example.clickhouse.service.PokerGameService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PokerWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PokerGameService pokerGameService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Игрок подключается к игре
     */
    @MessageMapping("/poker/{gameId}/join")
    @SendTo("/topic/poker/{gameId}")
    public Map<String, Object> joinGame(@DestinationVariable String gameId, Map<String, Object> payload) {
        String playerId = (String) payload.get("playerId");
        String playerName = (String) payload.get("playerName");
        Integer chips = payload.get("chips") != null ? (Integer) payload.get("chips") : 1000;

        log.info("Player {} joining game {}", playerName, gameId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "PLAYER_JOINED");
        response.put("playerId", playerId);
        response.put("playerName", playerName);
        response.put("timestamp", LocalDateTime.now().toString());

        boolean success = pokerGameService.addPlayerToGame(gameId, playerId, playerName, chips);

        if (success) {
            GameState gameState = pokerGameService.getGameState(gameId);
            if (gameState != null) {
                response.put("players", gameState.getPlayers());
                response.put("gameState", gameState.getState());
                response.put("pot", gameState.getPot());
                response.put("creatorPlayerId", gameState.getCreatorPlayerId());
                response.put("isGameRunning", gameState.getIsGameRunning());
            }
            return response;
        } else {
            log.warn("Player {} failed to join game {} - game is full", playerName, gameId);
            // ✅ Отправляем ошибку через PLAYER_JOINED
            response.put("error", "Game is full. Maximum players reached.");
            return response;  // ← Возвращаем с ошибкой, не null
        }
    }

    /**
     * Игрок совершает действие
     */
    @MessageMapping("/poker/{gameId}/action")
    @SendTo("/topic/poker/{gameId}")
    public Map<String, Object> playerAction(@DestinationVariable String gameId, Map<String, Object> payload) {
        String playerId = (String) payload.get("playerId");
        String action = (String) payload.get("action");
        Integer betAmount = payload.get("betAmount") != null ? (Integer) payload.get("betAmount") : 0;

        log.info("Player {} action: {} in game {}", playerId, action, gameId);

        // ✅ Вызываем сервис для обработки действия
        var result = pokerGameService.processPlayerAction(gameId, playerId, action, betAmount);

        // Формируем ответ
        Map<String, Object> response = new HashMap<>();
        response.put("type", "PLAYER_ACTION");
        response.put("playerId", playerId);
        response.put("action", action);
        response.put("betAmount", betAmount);
        response.put("timestamp", LocalDateTime.now().toString());

        if (!result.isSuccess()) {
            response.put("error", result.getError());
            return response;
        }

        response.put("pot", result.getPot());
        response.put("currentBet", result.getCurrentBet());
        response.put("currentPlayerId", result.getCurrentPlayerId());
        response.put("players", result.getPlayers());

        // ✅ Отправляем HAND_ENDED если раздача завершена
        if (result.isHandEnded()) {
            log.info("=== HAND ENDED ===");
            log.info("Winner: {}, Won amount: {}", result.getWinnerName(), result.getWonAmount());

            Map<String, Object> handEndedEvent = new HashMap<>();
            handEndedEvent.put("type", "HAND_ENDED");
            handEndedEvent.put("winnerName", result.getWinnerName());
            handEndedEvent.put("wonAmount", result.getWonAmount());
            handEndedEvent.put("players", result.getPlayers());
            handEndedEvent.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend("/topic/poker/" + gameId, handEndedEvent);
        }

        // ✅ Отправляем STAGE_CHANGED если раунд завершён
        if (result.isStageChanged()) {
            Map<String, Object> stageEvent = new HashMap<>();
            stageEvent.put("type", "STAGE_CHANGED");
            stageEvent.put("stage", result.getNewStage());
            stageEvent.put("communityCards", result.getCommunityCards());
            stageEvent.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend("/topic/poker/" + gameId, stageEvent);
        }

        return response;
    }

    /**
     * Найти имя победителя
     */
    private String findWinnerName(GameState gameState) {
        if (gameState == null || gameState.getPlayers() == null) {
            return "Unknown";
        }

        // Находим игрока, который не сбросил карты и активен
        return gameState.getPlayers().values().stream()
                .filter(p -> p.getIsActive() && !p.getIsFolded())
                .findFirst()
                .map(p -> p.getPlayerName())
                .orElse("Unknown");
    }

    /**
     * Сообщение в чат
     */
    @MessageMapping("/poker/{gameId}/chat")
    @SendTo("/topic/poker/{gameId}")
    public Map<String, Object> chatMessage(@DestinationVariable String gameId, Map<String, Object> payload) {
        String playerName = (String) payload.get("playerName");
        String message = (String) payload.get("message");

        log.info("Chat message in game {} from {}: {}", gameId, playerName, message);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "CHAT_MESSAGE");
        response.put("playerName", playerName);
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now().toString());

        return response;
    }

    /**
     * Запрос состояния игры
     */
    @MessageMapping("/poker/{gameId}/state")
    @SendTo("/topic/poker/{gameId}")
    public Map<String, Object> getGameState(@DestinationVariable String gameId) {
        GameState gameState = pokerGameService.getGameState(gameId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "GAME_STATE");
        response.put("timestamp", LocalDateTime.now().toString());

        if (gameState != null) {
            response.put("gameId", gameState.getGameId());
            response.put("state", gameState.getState());
            response.put("pot", gameState.getPot());
            response.put("currentBet", gameState.getCurrentBet());
            response.put("currentPlayerId", gameState.getCurrentPlayerId());
            response.put("players", gameState.getPlayers());
            response.put("communityCards", gameState.getCommunityCards());
            response.put("handEnded", gameState.isHandEnded());
            response.put("winnerName", gameState.getWinnerName());
            response.put("wonAmount", gameState.getWonAmount());
            response.put("creatorPlayerId", gameState.getCreatorPlayerId());   // ✅ Добавляем
            response.put("isGameRunning", gameState.getIsGameRunning());       // ✅ Добавляем
        } else {
            response.put("error", "Game not found");
        }

        return response;
    }


    /**
     * Запрос подсказки
     */
    @MessageMapping("/poker/{gameId}/tip")
    public void getTip(@DestinationVariable String gameId, @Payload Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String playerId = payload.get("playerId");

        PokerGameEngine engine = pokerGameService.getGameEngine(gameId);

        if (engine != null && playerId != null) {
            String tip = engine.getTipForPlayer(playerId);

            // Отправляем подсказку только запросившему игроку
            messagingTemplate.convertAndSendToUser(
                    headerAccessor.getSessionId(),
                    "/queue/poker/" + gameId + "/tip",
                    Map.of("type", "TIP", "message", tip)
            );
        }
    }
    /**
     * Запросить новую раздачу (первый шаг - предложение)
     */
    @MessageMapping("/poker/{gameId}/requestNewHand")
    public void requestNewHand(@DestinationVariable String gameId, @Payload Map<String, String> payload) {
        String playerId = payload.get("playerId");
        PokerGameEngine engine = pokerGameService.getGameEngine(gameId);

        if (engine != null && engine.getGameState().isHandEnded()) {
            // Сохраняем, кто запросил новую раздачу
            engine.getGameState().setNewHandRequesterId(playerId);
            engine.getGameState().getReadyForNewHand().clear();
            engine.getGameState().getReadyForNewHand().add(playerId);

            log.info("Player {} requested new hand for game {}", playerId, gameId);

            // Отправляем событие всем игрокам, что кто-то хочет начать новую раздачу
            Map<String, Object> event = new HashMap<>();
            event.put("type", "NEW_HAND_REQUEST");
            event.put("requesterId", playerId);
            event.put("requesterName", engine.getGameState().getPlayer(playerId).getPlayerName());
            event.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend("/topic/poker/" + gameId, event);
        }
    }

    /**
     * Подтвердить начало новой раздачи (второй шаг - согласие)
     */
    @MessageMapping("/poker/{gameId}/confirmNewHand")
    public void confirmNewHand(@DestinationVariable String gameId, @Payload Map<String, String> payload) {
        String playerId = payload.get("playerId");
        PokerGameEngine engine = pokerGameService.getGameEngine(gameId);

        if (engine != null && engine.getGameState().isHandEnded()) {
            Set<String> readyPlayers = engine.getGameState().getReadyForNewHand();
            readyPlayers.add(playerId);

            log.info("Player {} confirmed new hand for game {}. Ready: {}/{}",
                    playerId, gameId, readyPlayers.size(), engine.getGameState().getPlayers().size());

            // Отправляем событие о подтверждении
            Map<String, Object> confirmEvent = new HashMap<>();
            confirmEvent.put("type", "NEW_HAND_CONFIRM");
            confirmEvent.put("playerId", playerId);
            confirmEvent.put("playerName", engine.getGameState().getPlayer(playerId).getPlayerName());
            confirmEvent.put("readyCount", readyPlayers.size());
            confirmEvent.put("totalPlayers", engine.getGameState().getPlayers().size());
            confirmEvent.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend("/topic/poker/" + gameId, confirmEvent);

            // Если все игроки согласны - начинаем новую раздачу
            if (readyPlayers.size() >= engine.getGameState().getPlayers().size()) {
                log.info("All players ready! Starting new hand for game {}", gameId);

                engine.startNewHand();
                engine.getGameState().setHandEnded(false);
                engine.getGameState().setNewHandRequesterId(null);
                engine.getGameState().getReadyForNewHand().clear();

                // ✅ Отправляем GAME_STATE с явным типом
                Map<String, Object> gameStateResponse = new HashMap<>();
                gameStateResponse.put("type", "GAME_STATE");
                gameStateResponse.put("gameId", engine.getGameState().getGameId());
                gameStateResponse.put("state", engine.getGameState().getState());
                gameStateResponse.put("pot", engine.getGameState().getPot());
                gameStateResponse.put("currentBet", engine.getGameState().getCurrentBet());
                gameStateResponse.put("currentPlayerId", engine.getGameState().getCurrentPlayerId());
                gameStateResponse.put("players", engine.getGameState().getPlayers());
                gameStateResponse.put("communityCards", engine.getGameState().getCommunityCards());
                gameStateResponse.put("handEnded", engine.getGameState().isHandEnded());

                messagingTemplate.convertAndSend("/topic/poker/" + gameId, gameStateResponse);

                // Отправляем событие о начале новой раздачи
                Map<String, Object> startEvent = new HashMap<>();
                startEvent.put("type", "NEW_HAND_STARTED");
                startEvent.put("timestamp", LocalDateTime.now().toString());
                messagingTemplate.convertAndSend("/topic/poker/" + gameId, startEvent);
            }
        }
    }
    /**
     * Игрок покидает игру
     */
    @MessageMapping("/poker/{gameId}/leave")
    public void leaveGame(@DestinationVariable String gameId, @Payload Map<String, String> payload) {
        String playerId = payload.get("playerId");
        PokerGameEngine engine = pokerGameService.getGameEngine(gameId);

        if (engine == null) {
            log.warn("Game {} not found for leave", gameId);
            return;
        }

        GameState.PlayerState player = engine.getPlayer(playerId);
        if (player == null) {
            log.warn("Player {} not found in game {}", playerId, gameId);
            return;
        }

        String playerName = player.getPlayerName();
        log.info("Player {} is leaving game {}", playerName, gameId);

        // ✅ ВАЖНО: Если игрок был в раздаче и не сбросил карты - автоматически сбрасываем
        if (!player.getIsFolded() && engine.getGameState().isHandEnded() == false) {
            log.info("Player {} was in hand, auto-folding", playerName);
            engine.processAction(playerId, "fold", 0);
        }

        // Удаляем игрока из игры
        boolean removed = engine.removePlayer(playerId);

        if (removed) {
            int remainingPlayers = engine.getGameState().getPlayers().size();

            // Отправляем событие всем оставшимся игрокам
            Map<String, Object> leaveEvent = new HashMap<>();
            leaveEvent.put("type", "PLAYER_LEFT");
            leaveEvent.put("playerId", playerId);
            leaveEvent.put("playerName", playerName);
            leaveEvent.put("remainingPlayers", remainingPlayers);
            leaveEvent.put("players", engine.getGameState().getPlayers());
            leaveEvent.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend("/topic/poker/" + gameId, leaveEvent);

            // ✅ ИСПРАВЛЕНИЕ: Завершаем игру ТОЛЬКО если осталось МЕНЕЕ 2 игроков
            if (remainingPlayers < 2) {
                log.info("Game {} has less than 2 players, ending game", gameId);

                Map<String, Object> gameEndEvent = new HashMap<>();
                gameEndEvent.put("type", "GAME_ENDED");
                gameEndEvent.put("reason", "Not enough players to continue (minimum 2 required)");
                gameEndEvent.put("remainingPlayer", engine.getGameState().getPlayers().values().stream()
                        .findFirst()
                        .map(GameState.PlayerState::getPlayerName)
                        .orElse(null));
                gameEndEvent.put("timestamp", LocalDateTime.now().toString());

                messagingTemplate.convertAndSend("/topic/poker/" + gameId, gameEndEvent);

                // Останавливаем игру
                engine.getGameState().setIsGameRunning(false);
                engine.getGameState().setHandEnded(true);
            } else {
                // ✅ Если осталось 2+ игроков - игра продолжается
                log.info("Game {} continues with {} players", gameId, remainingPlayers);

                // Если текущий игрок вышел - переключаем ход
                if (engine.getGameState().getCurrentPlayerId().equals(playerId)) {
                    engine.moveToNextPlayer();

                    // Отправляем обновлённое состояние
                    Map<String, Object> gameStateResponse = new HashMap<>();
                    gameStateResponse.put("type", "GAME_STATE");
                    gameStateResponse.put("gameId", engine.getGameState().getGameId());
                    gameStateResponse.put("state", engine.getGameState().getState());
                    gameStateResponse.put("pot", engine.getGameState().getPot());
                    gameStateResponse.put("currentBet", engine.getGameState().getCurrentBet());
                    gameStateResponse.put("currentPlayerId", engine.getGameState().getCurrentPlayerId());
                    gameStateResponse.put("players", engine.getGameState().getPlayers());
                    gameStateResponse.put("communityCards", engine.getGameState().getCommunityCards());
                    gameStateResponse.put("handEnded", engine.getGameState().isHandEnded());

                    messagingTemplate.convertAndSend("/topic/poker/" + gameId, gameStateResponse);
                }
            }
        }
    }
    /**
     * Создатель игры начинает раздачу
     */
    @MessageMapping("/poker/{gameId}/start")
    @SendTo("/topic/poker/{gameId}")
    public Map<String, Object> startGame(@DestinationVariable String gameId, @Payload Map<String, String> payload) {
        String playerId = payload.get("playerId");
        PokerGameEngine engine = pokerGameService.getGameEngine(gameId);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());

        if (engine == null) {
            response.put("type", "ERROR");
            response.put("message", "Game not found");
            return response;
        }

        GameState gameState = engine.getGameState();

        // Проверка: только создатель может начать
        if (!playerId.equals(gameState.getCreatorPlayerId())) {
            response.put("type", "ERROR");
            response.put("message", "Only the game creator can start the game");
            return response;
        }

        // Проверка: достаточно ли игроков
        if (gameState.getPlayers().size() < 2) {
            response.put("type", "ERROR");
            response.put("message", "Need at least 2 players to start");
            return response;
        }

        // Проверка: не начата ли уже игра
        if (gameState.getIsGameRunning()) {
            response.put("type", "ERROR");
            response.put("message", "Game already in progress");
            return response;
        }

        // Запускаем игру
        pokerGameService.startNewHand(gameId);
        gameState.setIsGameRunning(true);

        response.put("type", "GAME_STARTED");
        response.put("gameId", gameId);
        response.put("startedBy", playerId);
        response.put("players", gameState.getPlayers());
        response.put("pot", gameState.getPot());
        response.put("currentBet", gameState.getCurrentBet());
        response.put("currentPlayerId", gameState.getCurrentPlayerId());
        response.put("communityCards", gameState.getCommunityCards());
        response.put("state", gameState.getState());

        log.info("Game {} started by creator {}", gameId, playerId);

        return response;
    }
}
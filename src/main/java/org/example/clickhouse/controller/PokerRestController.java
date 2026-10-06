package org.example.clickhouse.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.clickhouse.game.GameState;
import org.example.clickhouse.service.PokerGameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/poker")
@RequiredArgsConstructor
public class PokerRestController {

    private final PokerGameService pokerGameService;

    /**
     * Создать новую игру
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createGame(@RequestBody CreateGameRequest request) {
        try {
            String gameId = pokerGameService.createGame(
                    request.getGameName(),
                    request.getMinBet(),
                    request.getMaxPlayers(),
                    request.getStartingChips(),
                    request.getSmallBlind(),
                    request.getBigBlind(),
                    request.getCreatorPlayerId()  // ✅ Передаём создателя
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("gameId", gameId);
            response.put("message", "Game created successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create game", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Получить список активных игр
     */
    @GetMapping("/games")
    public ResponseEntity<Map<String, Object>> getActiveGames() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("games", pokerGameService.getActiveGames().keySet());
        response.put("count", pokerGameService.getActiveGames().size());
        return ResponseEntity.ok(response);
    }

    /**
     * Получить состояние игры
     */
    @GetMapping("/game/{gameId}")
    public ResponseEntity<Map<String, Object>> getGameState(@PathVariable String gameId) {
        GameState gameState = pokerGameService.getGameState(gameId);

        Map<String, Object> response = new HashMap<>();

        if (gameState != null) {
            response.put("success", true);
            response.put("gameId", gameState.getGameId());
            response.put("state", gameState.getState());
            response.put("pot", gameState.getPot());
            response.put("currentBet", gameState.getCurrentBet());
            response.put("currentPlayerId", gameState.getCurrentPlayerId());
            response.put("playersCount", gameState.getPlayers().size());
            response.put("players", gameState.getPlayers());
            response.put("creatorPlayerId", gameState.getCreatorPlayerId());  // ✅ Добавляем
            response.put("isGameRunning", gameState.getIsGameRunning());      // ✅ Добавляем
        } else {
            response.put("success", false);
            response.put("error", "Game not found");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Добавить игрока в игру
     */
    @PostMapping("/game/{gameId}/join")
    public ResponseEntity<Map<String, Object>> joinGame(
            @PathVariable String gameId,
            @RequestBody JoinGameRequest request) {

        boolean success = pokerGameService.addPlayerToGame(
                gameId,
                request.getPlayerId(),
                request.getPlayerName(),
                request.getChips()
        );

        Map<String, Object> response = new HashMap<>();

        if (success) {
            response.put("success", true);
            response.put("message", "Player joined successfully");
        } else {
            response.put("success", false);
            response.put("error", "Failed to join game");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Начать новую раздачу (через REST API)
     */
    @PostMapping("/game/{gameId}/start-hand")
    public ResponseEntity<Map<String, Object>> startNewHand(@PathVariable String gameId) {
        try {
            pokerGameService.startNewHand(gameId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "New hand started");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to start hand", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Выполнить действие игрока
     */
    @PostMapping("/game/{gameId}/action")
    public ResponseEntity<Map<String, Object>> playerAction(
            @PathVariable String gameId,
            @RequestBody ActionRequest request) {

        boolean success = pokerGameService.processAction(
                gameId,
                request.getPlayerId(),
                request.getAction(),
                request.getBetAmount()
        );

        Map<String, Object> response = new HashMap<>();

        if (success) {
            response.put("success", true);
            response.put("message", "Action processed");
        } else {
            response.put("success", false);
            response.put("error", "Invalid action");
        }

        return ResponseEntity.ok(response);
    }

    // ==================== ВНУТРЕННИЕ КЛАССЫ ДЛЯ ЗАПРОСОВ ====================

    public static class CreateGameRequest {
        private String gameName;
        private int minBet;
        private int maxPlayers;
        private int startingChips;
        private int smallBlind;
        private int bigBlind;
        private String creatorPlayerId;  // ✅ Добавляем поле создателя

        public String getGameName() { return gameName; }
        public void setGameName(String gameName) { this.gameName = gameName; }
        public int getMinBet() { return minBet; }
        public void setMinBet(int minBet) { this.minBet = minBet; }
        public int getMaxPlayers() { return maxPlayers; }
        public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
        public int getStartingChips() { return startingChips; }
        public void setStartingChips(int startingChips) { this.startingChips = startingChips; }
        public int getSmallBlind() { return smallBlind; }
        public void setSmallBlind(int smallBlind) { this.smallBlind = smallBlind; }
        public int getBigBlind() { return bigBlind; }
        public void setBigBlind(int bigBlind) { this.bigBlind = bigBlind; }
        public String getCreatorPlayerId() { return creatorPlayerId; }
        public void setCreatorPlayerId(String creatorPlayerId) { this.creatorPlayerId = creatorPlayerId; }
    }

    public static class JoinGameRequest {
        private String playerId;
        private String playerName;
        private int chips;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public int getChips() { return chips; }
        public void setChips(int chips) { this.chips = chips; }
    }

    public static class ActionRequest {
        private String playerId;
        private String action;
        private int betAmount;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public int getBetAmount() { return betAmount; }
        public void setBetAmount(int betAmount) { this.betAmount = betAmount; }
    }
}
package org.example.clickhouse.game;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Ядро покерной игры
 * Управляет раздачами, ставками, определением победителя
 * Поддерживает 2-9 игроков
 */
@Slf4j
public class PokerGameEngine {

    private final String gameId;
    private GameState gameState;
    private Deck deck;
    private final int smallBlind;
    private final int bigBlind;
    private final int minBet;
    private final int maxPlayers;

    public PokerGameEngine(String gameId, int smallBlind, int bigBlind, int minBet, int maxPlayers) {
        this.gameId = gameId;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.minBet = minBet;
        this.maxPlayers = maxPlayers;
        this.gameState = GameState.builder()
                .gameId(gameId)
                .state(GameStage.WAITING.getCode())
                .pot(0)
                .currentBet(0)
                .minBet(minBet)
                .smallBlind(smallBlind)
                .bigBlind(bigBlind)
                .currentRound(0)
                .players(new HashMap<>())
                .build();
    }

    /**
     * Добавить игрока в игру
     */
    public boolean addPlayer(String playerId, String playerName, int chips, int seatNumber) {
        if (gameState.getPlayers().size() >= maxPlayers) {
            log.warn("Game {} is full, cannot add player {}", gameId, playerId);
            return false;
        }

        GameState.PlayerState player = GameState.PlayerState.builder()
                .playerId(playerId)
                .playerName(playerName)
                .chips(chips)
                .currentBet(0)
                .isActive(true)
                .isFolded(false)
                .isAllIn(false)
                .seatNumber(seatNumber)
                .holeCards(new ArrayList<>())
                .build();

        gameState.getPlayers().put(playerId, player);
        log.info("Player {} added to game {}. Total players: {}", playerName, gameId, gameState.getPlayers().size());
        return true;
    }

    /**
     * Удалить игрока из игры
     */
    public boolean removePlayer(String playerId) {
        GameState.PlayerState player = gameState.getPlayers().remove(playerId);
        if (player != null) {
            log.info("Player {} removed from game {}. Remaining players: {}",
                    player.getPlayerName(), gameId, gameState.getPlayers().size());
            return true;
        }
        return false;
    }

    /**
     * Начать новую раздачу
     */
    public void startNewHand() {
        log.info("Starting new hand for game {} with {} players", gameId, getActivePlayersCount());

        // Сброс состояния
        gameState.setPot(0);
        gameState.setCurrentBet(0);
        gameState.setCurrentRound(1);
        gameState.setState(GameStage.PRE_FLOP.getCode());
        gameState.setCommunityCards(new ArrayList<>());

        // Сброс ставок игроков
        for (GameState.PlayerState player : gameState.getPlayers().values()) {
            if (player.getIsActive() && player.getChips() > 0) {
                player.setCurrentBet(0);
                player.setIsFolded(false);
                player.setIsAllIn(false);
                player.setHoleCards(new ArrayList<>());
                player.setHandRank(null);
                player.setHandValue(0);
                player.setLastAction(null);
            }
        }

        // Создаем и тасуем колоду
        deck = new Deck();
        deck.shuffle();

        // Определяем дилера (ротация)
        rotateDealer();

        // Раздаем карты
        dealHoleCards();

        // Собираем блайнды
        collectBlinds();

        // Определяем первого игрока для ставок
        determineFirstPlayer();

        log.info("New hand started, pot: {}, current bet: {}, players in hand: {}",
                gameState.getPot(), gameState.getCurrentBet(), getPlayersInHand());
    }

    /**
     * Ротация дилера
     */
    private void rotateDealer() {
        List<GameState.PlayerState> activePlayers = getActivePlayersList();

        if (activePlayers.isEmpty()) {
            log.warn("No active players for dealer rotation");
            return;
        }

        String currentDealer = gameState.getDealerId();

        if (currentDealer == null) {
            // Первая раздача - первый игрок становится дилером
            gameState.setDealerId(activePlayers.get(0).getPlayerId());
            log.info("First dealer: {}", activePlayers.get(0).getPlayerName());
            return;
        }

        // Находим следующего дилера
        int currentIndex = findPlayerIndex(currentDealer, activePlayers);
        int nextIndex = (currentIndex + 1) % activePlayers.size();
        String newDealer = activePlayers.get(nextIndex).getPlayerId();

        gameState.setDealerId(newDealer);

        // Обновляем флаг isDealer у игроков
        for (GameState.PlayerState player : gameState.getPlayers().values()) {
            player.setIsDealer(player.getPlayerId().equals(newDealer));
        }

        log.info("New dealer: {}", activePlayers.get(nextIndex).getPlayerName());
    }

    /**
     * Раздать карты игрокам (по 2 карты каждому)
     */
    private void dealHoleCards() {
        for (GameState.PlayerState player : gameState.getPlayers().values()) {
            if (player.getIsActive() && player.getChips() > 0) {
                List<Card> cards = deck.dealCards(2);
                player.setHoleCards(cards);
                log.debug("Player {} dealt: {}", player.getPlayerName(),
                        cards.stream().map(Card::toString).collect(Collectors.joining(" ")));
            }
        }
    }

    /**
     * Собрать блайнды (малый и большой)
     */
    private void collectBlinds() {
        List<GameState.PlayerState> activePlayers = getActivePlayersList();

        if (activePlayers.size() < 2) {
            log.warn("Not enough players to collect blinds, need at least 2, have {}", activePlayers.size());
            return;
        }

        // Находим позиции относительно дилера
        int dealerIndex = findPlayerIndex(gameState.getDealerId(), activePlayers);

        // Малый блайнд (следующий после дилера)
        int smallBlindIndex = (dealerIndex + 1) % activePlayers.size();
        GameState.PlayerState smallBlindPlayer = activePlayers.get(smallBlindIndex);
        int smallBlindAmount = Math.min(smallBlind, smallBlindPlayer.getChips());
        makeBet(smallBlindPlayer, smallBlindAmount);
        smallBlindPlayer.setIsSmallBlind(true);

        // Большой блайнд (через одного после дилера)
        int bigBlindIndex = (dealerIndex + 2) % activePlayers.size();
        GameState.PlayerState bigBlindPlayer = activePlayers.get(bigBlindIndex);
        int bigBlindAmount = Math.min(bigBlind, bigBlindPlayer.getChips());
        makeBet(bigBlindPlayer, bigBlindAmount);
        bigBlindPlayer.setIsBigBlind(true);

        gameState.setCurrentBet(bigBlindAmount);

        log.info("Blinds collected: SB={} ({}), BB={} ({})",
                smallBlindPlayer.getPlayerName(), smallBlindAmount,
                bigBlindPlayer.getPlayerName(), bigBlindAmount);
    }

    /**
     * Определить первого игрока для ставок
     * В покере первым ходит игрок после большого блайнда
     */
    private void determineFirstPlayer() {
        List<GameState.PlayerState> activePlayers = getActivePlayersList();

        if (activePlayers.size() < 2) {
            log.warn("Not enough players to determine first player");
            return;
        }

        // Первый игрок - через 2 позиции после дилера (после SB и BB)
        int dealerIndex = findPlayerIndex(gameState.getDealerId(), activePlayers);
        int firstPlayerIndex = (dealerIndex + 3) % activePlayers.size();

        String firstPlayerId = activePlayers.get(firstPlayerIndex).getPlayerId();
        gameState.setCurrentPlayerId(firstPlayerId);

        log.info("First player to act: {} (position {})",
                activePlayers.get(firstPlayerIndex).getPlayerName(), firstPlayerIndex);
    }

    /**
     * Обработать действие игрока
     */
    public boolean processAction(String playerId, String action, int betAmount) {
        GameState.PlayerState player = gameState.getPlayers().get(playerId);
        if (player == null || !player.getIsActive() || player.getIsFolded()) {
            log.warn("Invalid action from player {}", playerId);
            return false;
        }

        int callAmount = gameState.getCurrentBet() - player.getCurrentBet();
        boolean actionSuccess = false;

        log.info("=== PROCESS ACTION ===");
        log.info("Player: {}, Action: {}, BetAmount: {}", player.getPlayerName(), action, betAmount);
        log.info("Current bet: {}, Player current bet: {}, Call amount: {}",
                gameState.getCurrentBet(), player.getCurrentBet(), callAmount);
        log.info("Current stage: {}, Current player: {}",
                gameState.getState(), gameState.getCurrentPlayerId());

        switch (action.toLowerCase()) {
            case "fold":
                actionSuccess = fold(player);
                break;
            case "check":
                actionSuccess = check(player);
                break;
            case "call":
                actionSuccess = call(player, callAmount);
                break;
            case "raise":
                actionSuccess = raise(player, betAmount, callAmount);
                break;
            case "all_in":
                actionSuccess = allIn(player);
                break;
            default:
                log.warn("Unknown action: {}", action);
                return false;
        }

        if (!actionSuccess) {
            log.warn("Action failed for player {}", player.getPlayerName());
            return false;
        }

        log.info("After action - Pot: {}, Current bet: {}, Player bet: {}",
                gameState.getPot(), gameState.getCurrentBet(), player.getCurrentBet());

        // ✅ НОВАЯ ПРОВЕРКА: Остался ли только один активный игрок?
        List<GameState.PlayerState> playersInHand = getPlayersInHandList();

        if (playersInHand.size() <= 1) {
            log.info("Only one player left in hand! Ending the hand.");

            String winnerName = null;
            int wonAmount = gameState.getPot();

            // Определяем победителя и обновляем баланс
            if (!playersInHand.isEmpty()) {
                GameState.PlayerState winner = playersInHand.get(0);
                winnerName = winner.getPlayerName();
                winner.setChips(winner.getChips() + wonAmount);

                log.info("🏆 Player {} wins the pot of {} by default", winnerName, wonAmount);
                log.info("   Balance: {} → {} (+{})",
                        winner.getChips() - wonAmount, winner.getChips(), wonAmount);
            }

            // ✅ Устанавливаем флаги завершения раздачи
            gameState.setHandEnded(true);
            gameState.setWinnerName(winnerName);
            gameState.setWonAmount(wonAmount);

            // Обнуляем пот и ставки
            gameState.setPot(0);
            gameState.setCurrentBet(0);

            // Сбрасываем ставки игроков
            resetBetsForNewRound();

            // ❌ НЕ вызываем startNewHand() здесь!
            // Новая раздача начнётся только после запроса от клиента

            log.info("Hand ended. Waiting for players to start new hand.");
            return true;
        }

        // ✅ Проверяем, закончился ли раунд
        boolean roundComplete = isRoundComplete();
        log.info("Round complete: {}", roundComplete);

        if (roundComplete) {
            log.info("Moving to next stage...");
            nextStage();
        } else {
            moveToNextPlayer();
        }

        log.info("New current player: {}", gameState.getCurrentPlayerId());
        log.info("======================");

        return true;
    }
    /**
     * Начать новую раздачу (по запросу игрока)
     */
    public void startNewHandRequest() {
        log.info("Starting new hand for game {} by player request", gameId);
        startNewHand();
        gameState.setHandEnded(false);
        gameState.setWinnerName(null);
        gameState.setWonAmount(0);
    }
    /**
     * Запросить новую раздачу (первый шаг - предложение)
     */
    public void requestNewHand(String playerId) {
        gameState.setNewHandRequesterId(playerId);
        gameState.getReadyForNewHand().clear();
        gameState.getReadyForNewHand().add(playerId);
        log.info("Player {} requested new hand. Waiting for other players...", playerId);
    }

    /**
     * Подтвердить начало новой раздачи (второй шаг - согласие)
     */
    public boolean confirmNewHand(String playerId) {
        gameState.getReadyForNewHand().add(playerId);
        log.info("Player {} confirmed ready for new hand. Ready: {}/{}",
                playerId,
                gameState.getReadyForNewHand().size(),
                gameState.getPlayers().size());

        // Если все игроки готовы - начинаем новую раздачу
        if (gameState.getReadyForNewHand().size() >= gameState.getPlayers().size()) {
            log.info("All players ready! Starting new hand...");
            startNewHandRequest();
            gameState.setHandEnded(false);
            gameState.setWinnerName(null);
            gameState.setWonAmount(0);
            gameState.getReadyForNewHand().clear();
            gameState.setNewHandRequesterId(null);
            return true;
        }
        return false;
    }
    /**
     * Получить игрока по ID
     */
    public GameState.PlayerState getPlayer(String playerId) {
        return gameState.getPlayers().get(playerId);
    }

    /**
     * Переключение на следующего активного игрока
     */
    public void moveToNextPlayer() {
        List<GameState.PlayerState> playersInHand = getPlayersInHandList();

        if (playersInHand.size() <= 1) {
            if (playersInHand.size() == 1) {
                GameState.PlayerState winner = playersInHand.get(0);
                winner.setChips(winner.getChips() + gameState.getPot());
                log.info("🏆 Player {} wins the pot of {} by default", winner.getPlayerName(), gameState.getPot());
                gameState.setPot(0);
                startNewHand();
            }
            return;
        }

        String currentPlayerId = gameState.getCurrentPlayerId();
        int currentIndex = -1;

        for (int i = 0; i < playersInHand.size(); i++) {
            if (playersInHand.get(i).getPlayerId().equals(currentPlayerId)) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            gameState.setCurrentPlayerId(playersInHand.get(0).getPlayerId());
            log.info("Current player not found, setting to first: {}", playersInHand.get(0).getPlayerName());
            return;
        }

        int nextIndex = (currentIndex + 1) % playersInHand.size();
        GameState.PlayerState nextPlayer = playersInHand.get(nextIndex);
        gameState.setCurrentPlayerId(nextPlayer.getPlayerId());

        log.info("Turn moved from {} to {}",
                playersInHand.get(currentIndex).getPlayerName(),
                nextPlayer.getPlayerName());

        // ✅ УБИРАЕМ ВЫЗОВ nextStage() - это уже проверяется в processAction через isRoundComplete()
    }

    /**
     * Сбросить карты
     */
    private boolean fold(GameState.PlayerState player) {
        player.setIsFolded(true);
        player.setLastAction("fold");
        log.info("Player {} folded", player.getPlayerName());
        return true;
    }

    /**
     * Чек (пропустить ход, когда нет ставки)
     */
    private boolean check(GameState.PlayerState player) {
        if (gameState.getCurrentBet() - player.getCurrentBet() > 0) {
            log.warn("Player {} cannot check, must call or fold", player.getPlayerName());
            return false;
        }

        player.setLastAction("check");
        log.info("Player {} checked", player.getPlayerName());
        return true;
    }

    /**
     * Уравнять ставку
     */
    private boolean call(GameState.PlayerState player, int callAmount) {
        if (callAmount <= 0) {
            return check(player);
        }

        if (player.getChips() < callAmount) {
            log.warn("Player {} has insufficient chips to call", player.getPlayerName());
            return false;
        }

        makeBet(player, callAmount);
        player.setLastAction("call");
        log.info("Player {} called {}", player.getPlayerName(), callAmount);
        return true;
    }

    /**
     * Поднять ставку
     */
    private boolean raise(GameState.PlayerState player, int raiseAmount, int callAmount) {
        int totalBet = callAmount + raiseAmount;

        if (raiseAmount < minBet) {
            log.warn("Raise amount {} is less than minimum bet {}", raiseAmount, minBet);
            return false;
        }

        if (player.getChips() < totalBet) {
            log.warn("Player {} has insufficient chips to raise", player.getPlayerName());
            return false;
        }

        makeBet(player, totalBet);
        gameState.setCurrentBet(player.getCurrentBet());
        player.setLastAction("raise");
        log.info("Player {} raised to {}", player.getPlayerName(), player.getCurrentBet());
        return true;
    }

    /**
     * Поставить все фишки
     */
    private boolean allIn(GameState.PlayerState player) {
        int allInAmount = player.getChips();
        makeBet(player, allInAmount);
        player.setIsAllIn(true);

        if (player.getCurrentBet() > gameState.getCurrentBet()) {
            gameState.setCurrentBet(player.getCurrentBet());
        }

        player.setLastAction("all_in");
        log.info("Player {} went all-in with {}", player.getPlayerName(), allInAmount);
        return true;
    }

    /**
     * Сделать ставку
     */
    private void makeBet(GameState.PlayerState player, int amount) {
        player.setChips(player.getChips() - amount);
        player.setCurrentBet(player.getCurrentBet() + amount);
        gameState.setPot(gameState.getPot() + amount);
    }

    /**
     * Перейти к следующему этапу
     */
    public void nextStage() {
        GameStage currentStage = GameStage.fromCode(gameState.getState());
        GameStage nextStage = currentStage.next();

        gameState.setState(nextStage.getCode());

        switch (nextStage) {
            case FLOP:
                dealFlop();
                break;
            case TURN:
                dealTurn();
                break;
            case RIVER:
                dealRiver();
                break;
            case SHOWDOWN:
                determineWinner();
                // После шоудауна начинаем новую раздачу
                startNewHand();
                return;
        }

        // Сброс ставок для нового раунда
        resetBetsForNewRound();

        // Устанавливаем первого игрока для нового раунда
        setFirstPlayerForNewRound();

        log.info("Game {} advanced to stage: {}", gameId, nextStage.getDescription());
    }

    /**
     * Установить первого игрока для нового раунда (флоп, терн, ривер)
     * Первым ходит игрок после дилера (не сбросивший)
     */
    private void setFirstPlayerForNewRound() {
        List<GameState.PlayerState> playersInHand = getPlayersInHandList();

        log.info("=== SET FIRST PLAYER FOR NEW ROUND ===");
        log.info("Stage: {}", gameState.getState());
        log.info("Players in hand: {}", playersInHand.stream()
                .map(GameState.PlayerState::getPlayerName)
                .collect(Collectors.joining(", ")));

        if (playersInHand.isEmpty()) {
            log.warn("No players in hand to set first player");
            return;
        }

        // Находим дилера
        String dealerId = gameState.getDealerId();
        int dealerIndex = findPlayerIndex(dealerId, playersInHand);
        log.info("Dealer: {} at index {}",
                playersInHand.get(dealerIndex).getPlayerName(), dealerIndex);

        // Первым ходит игрок ПОСЛЕ дилера
        int firstIndex = (dealerIndex + 1) % playersInHand.size();
        GameState.PlayerState firstPlayer = playersInHand.get(firstIndex);

        gameState.setCurrentPlayerId(firstPlayer.getPlayerId());

        log.info("First player for {} stage: {} (position {})",
                gameState.getState(),
                firstPlayer.getPlayerName(),
                firstIndex);
        log.info("=====================================");
    }

    /**
     * Раздать флоп (3 карты)
     */
    private void dealFlop() {
        List<Card> flop = deck.dealCards(3);
        gameState.getCommunityCards().addAll(flop);
        log.info("Flop dealt: {}", flop.stream().map(Card::toString).collect(Collectors.joining(" ")));
    }

    /**
     * Раздать терн (4-ю карту)
     */
    private void dealTurn() {
        Card turn = deck.dealCard();
        gameState.getCommunityCards().add(turn);
        log.info("Turn dealt: {}", turn);
    }

    /**
     * Раздать ривер (5-ю карту)
     */
    private void dealRiver() {
        Card river = deck.dealCard();
        gameState.getCommunityCards().add(river);
        log.info("River dealt: {}", river);
    }

    /**
     * Сбросить ставки для нового раунда
     */
    private void resetBetsForNewRound() {
        for (GameState.PlayerState player : gameState.getPlayers().values()) {
            if (!player.getIsFolded() && player.getIsActive()) {
                player.setCurrentBet(0);
                player.setLastAction(null); // ✅ ВАЖНО: сбрасываем последнее действие
            }
        }
        gameState.setCurrentBet(0);
    }

    /**
     * Определить победителя
     */
    private void determineWinner() {
        List<GameState.PlayerState> playersInHand = getPlayersInHandList();

        if (playersInHand.isEmpty()) {
            log.warn("No active players to determine winner");
            return;
        }

        if (playersInHand.size() == 1) {
            // Остался только один игрок - он выигрывает
            GameState.PlayerState winner = playersInHand.get(0);
            winner.setChips(winner.getChips() + gameState.getPot());
            log.info("🏆 Player {} wins the pot of {} by default", winner.getPlayerName(), gameState.getPot());
            gameState.setPot(0);
            return;
        }

        // Оцениваем комбинации всех игроков
        Map<String, HandEvaluator.HandEvaluation> evaluations = new HashMap<>();
        for (GameState.PlayerState player : playersInHand) {
            HandEvaluator.HandEvaluation evaluation = HandEvaluator.evaluateHand(
                    player.getHoleCards(),
                    gameState.getCommunityCards()
            );
            evaluations.put(player.getPlayerId(), evaluation);
            player.setHandRank(evaluation.getRank().getName());
            player.setHandValue(evaluation.getRank().getValue());
            log.info("Player {} hand: {} - {}", player.getPlayerName(),
                    evaluation.getRank().getName(), evaluation.getDescription());
        }

        // Находим победителя
        String winnerId = findWinner(evaluations);
        GameState.PlayerState winner = gameState.getPlayers().get(winnerId);

        if (winner != null) {
            winner.setChips(winner.getChips() + gameState.getPot());
            log.info("🏆 Player {} wins the pot of {} with {}",
                    winner.getPlayerName(), gameState.getPot(), evaluations.get(winnerId).getRank().getName());
        }

        gameState.setPot(0);
    }

    /**
     * Найти победителя среди комбинаций
     */
    private String findWinner(Map<String, HandEvaluator.HandEvaluation> evaluations) {
        return evaluations.entrySet().stream()
                .max((e1, e2) -> HandEvaluator.compareHands(e1.getValue(), e2.getValue()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Проверить, закончился ли раунд
     */
    public boolean isRoundComplete() {
        List<GameState.PlayerState> playersInHand = getPlayersInHandList();

        if (playersInHand.size() <= 1) {
            return true;
        }

        // Проверяем, что все игроки сделали хотя бы одно действие
        boolean allHaveActed = playersInHand.stream()
                .allMatch(p -> p.getLastAction() != null);

        if (!allHaveActed) {
            log.info("Round not complete: not all players have acted");
            return false;
        }

        // Находим максимальную ставку в этом раунде
        int maxBet = playersInHand.stream()
                .mapToInt(GameState.PlayerState::getCurrentBet)
                .max()
                .orElse(0);

        // Проверяем, все ли уравняли максимальную ставку или all-in
        boolean allBetsEqual = playersInHand.stream()
                .allMatch(p -> p.getCurrentBet() == maxBet || p.getIsAllIn());

        if (!allBetsEqual) {
            log.info("Round not complete: bets not equal, maxBet={}", maxBet);
        }

        return allBetsEqual;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Получить список активных игроков (с чипами)
     */
    private List<GameState.PlayerState> getActivePlayersList() {
        return gameState.getPlayers().values().stream()
                .filter(p -> p.getIsActive() && p.getChips() > 0)
                .sorted(Comparator.comparingInt(GameState.PlayerState::getSeatNumber))
                .collect(Collectors.toList());
    }

    /**
     * Получить список игроков в раздаче (не сбросивших)
     */
    private List<GameState.PlayerState> getPlayersInHandList() {
        return gameState.getPlayers().values().stream()
                .filter(p -> p.getIsActive() && !p.getIsFolded() && p.getChips() > 0)
                .sorted(Comparator.comparingInt(GameState.PlayerState::getSeatNumber))
                .collect(Collectors.toList());
    }

    /**
     * Получить количество игроков в раздаче
     */
    private int getPlayersInHand() {
        return (int) gameState.getPlayers().values().stream()
                .filter(p -> p.getIsActive() && !p.getIsFolded() && p.getChips() > 0)
                .count();
    }

    /**
     * Получить количество активных игроков
     */
    private int getActivePlayersCount() {
        return (int) gameState.getPlayers().values().stream()
                .filter(p -> p.getIsActive() && p.getChips() > 0)
                .count();
    }

    /**
     * Найти индекс игрока в списке
     */
    private int findPlayerIndex(String playerId, List<GameState.PlayerState> players) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getPlayerId().equals(playerId)) {
                return i;
            }
        }
        return 0;
    }
    /**
     * Получить подсказку для игрока на основе текущего состояния
     */
    public String getTipForPlayer(String playerId) {
        GameState.PlayerState player = gameState.getPlayers().get(playerId);
        if (player == null) return "Ошибка: игрок не найден";

        // Не ваш ход
        if (!playerId.equals(gameState.getCurrentPlayerId())) {
            return "⏳ Сейчас не ваш ход. Дождитесь своей очереди.";
        }

        int callAmount = gameState.getCurrentBet() - player.getCurrentBet();
        int myChips = player.getChips();
        int pot = gameState.getPot();
        String stage = gameState.getState();

        StringBuilder tip = new StringBuilder();

        // Оценка силы руки (если есть карты)
        if (!player.getHoleCards().isEmpty()) {
            HandEvaluator.HandEvaluation evaluation = HandEvaluator.evaluateHand(
                    player.getHoleCards(),
                    gameState.getCommunityCards()
            );
            String handStrength = getHandStrengthDescription(evaluation.getRank().getValue());
            tip.append("🎯 Ваша рука: ").append(evaluation.getRank().getName())
                    .append(" (").append(handStrength).append(")\n\n");
        }

        // Доступные действия
        tip.append("📋 Доступные действия:\n");

        if (callAmount <= 0) {
            // Нет активных ставок
            tip.append("✅ Check - пропустить ход (бесплатно)\n");
            tip.append("💰 Bet - сделать ставку (от ").append(gameState.getMinBet()).append(" до ").append(myChips).append(")\n");
            tip.append("❌ Fold - сбросить карты\n");

            // Дополнительный совет
            if (!player.getHoleCards().isEmpty()) {
                HandEvaluator.HandEvaluation evaluation = HandEvaluator.evaluateHand(
                        player.getHoleCards(),
                        gameState.getCommunityCards()
                );
                if (evaluation.getRank().getValue() < 5) {
                    tip.append("\n💡 Совет: у вас слабая рука, лучше сделать Check");
                } else {
                    tip.append("\n💡 Совет: у вас хорошая рука, можно сделать ставку");
                }
            }
        } else {
            // Есть активная ставка
            tip.append("💰 Call - уравнять ставку (нужно добавить ").append(callAmount).append(" фишек)\n");
            tip.append("📈 Raise - поднять ставку (минимум ").append(gameState.getCurrentBet() + gameState.getMinBet()).append(")\n");
            tip.append("❌ Fold - сбросить карты (потерять ").append(player.getCurrentBet()).append(" фишек)\n");
            tip.append("🔥 All-in - поставить все фишки (").append(myChips).append(")\n");

            // Расчет шансов банка
            double potOdds = (double) callAmount / (pot + callAmount) * 100;
            tip.append("\n📊 Шансы банка: ").append(String.format("%.1f", potOdds)).append("%\n");

            // Совет на основе силы руки
            if (!player.getHoleCards().isEmpty()) {
                HandEvaluator.HandEvaluation evaluation = HandEvaluator.evaluateHand(
                        player.getHoleCards(),
                        gameState.getCommunityCards()
                );
                int handValue = evaluation.getRank().getValue();

                if (handValue >= 8) {
                    tip.append("💡 Совет: у вас очень сильная рука! Рекомендуем Raise или Call");
                } else if (handValue >= 5) {
                    tip.append("💡 Совет: у вас средняя рука. Call - разумный выбор");
                } else {
                    tip.append("💡 Совет: у вас слабая рука. Рекомендуем Fold");
                }
            }
        }

        return tip.toString();
    }

    /**
     * Описание силы руки
     */
    private String getHandStrengthDescription(int handValue) {
        switch(handValue) {
            case 10: return "Роял-флеш - НЕВЕРОЯТНО! 🔥";
            case 9: return "Стрит-флеш - ОЧЕНЬ СИЛЬНО! 💪";
            case 8: return "Каре - ОЧЕНЬ СИЛЬНО! 💪";
            case 7: return "Фулл хаус - СИЛЬНО! 👍";
            case 6: return "Флеш - ХОРОШО! ✓";
            case 5: return "Стрит - ХОРОШО! ✓";
            case 4: return "Сет - НЕПЛОХО";
            case 3: return "Две пары - СРЕДНЕ";
            case 2: return "Одна пара - СЛАБОВАТО";
            default: return "Старшая карта - ПЛОХО";
        }
    }

    // ==================== GETTERS ====================

    public GameState getGameState() {
        return gameState;
    }

    public String getGameId() {
        return gameId;
    }

    public List<Card> getCommunityCards() {
        return gameState.getCommunityCards();
    }
}
package org.example.clickhouse.game;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Состояние игры в реальном времени (хранится в памяти, не в БД)
 * Отличается от PokerGame (который хранится в ClickHouse) тем, что содержит:
 * - актуальные карты игроков
 * - текущую колоду
 * - активные WebSocket сессии
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameState {

    // ==================== ОСНОВНАЯ ИНФОРМАЦИЯ ====================

    /**
     * ID игры (совпадает с PokerGame.gameId)
     */
    private String gameId;

    /**
     * Название игры
     */
    private String gameName;

    /**
     * ID создателя игры (кто может начать раздачу)
     */
    private String creatorPlayerId;

    /**
     * Текущее состояние игры:
     * WAITING - ожидание игроков
     * PRE_FLOP - префлоп
     * FLOP - флоп (3 карты)
     * TURN - терн (4 карта)
     * RIVER - ривер (5 карта)
     * SHOWDOWN - вскрытие
     * FINISHED - игра завершена
     */
    private String state;

    // ==================== ИГРОКИ ====================

    /**
     * Карта игроков за столом
     * Ключ: playerId, Значение: PlayerState
     */
    @Builder.Default
    private Map<String, PlayerState> players = new ConcurrentHashMap<>();

    /**
     * Очередность ходов (список playerId в порядке хода)
     */
    private List<String> turnOrder;

    /**
     * ID текущего игрока (чей ход)
     */
    private String currentPlayerId;

    /**
     * ID дилера
     */
    private String dealerId;

    // ==================== КАРТЫ ====================

    /**
     * Текущая колода (оставшиеся карты)
     */
    private List<Card> deck;

    /**
     * Общие карты на столе
     */
    @Builder.Default
    private List<Card> communityCards = new ArrayList<>();

    // ==================== СТАВКИ И БАНК ====================

    /**
     * Текущий банк (сумма всех ставок)
     */
    @Builder.Default
    private Integer pot = 0;

    /**
     * Текущая ставка, которую нужно уравнять
     */
    @Builder.Default
    private Integer currentBet = 0;

    /**
     * Минимальная ставка (обычно big blind)
     */
    private Integer minBet;

    /**
     * Малый блайнд
     */
    private Integer smallBlind;

    /**
     * Большой блайнд
     */
    private Integer bigBlind;

    /**
     * Кто сделал последний рейз (для определения окончания раунда)
     */
    private String lastRaisePlayerId;

    /**
     * Текущий раунд (1=PRE_FLOP, 2=FLOP, 3=TURN, 4=RIVER, 5=SHOWDOWN)
     */
    @Builder.Default
    private Integer currentRound = 0;

    // ==================== ВРЕМЯ ====================

    /**
     * Время начала текущего раунда (для таймера)
     */
    private Long roundStartTime;

    /**
     * Максимальное время на ход (секунды)
     */
    @Builder.Default
    private Integer turnTimeoutSeconds = 30;

    // ==================== WEB SOCKET ====================

    /**
     * WebSocket сессии игроков
     * Ключ: playerId, Значение: sessionId
     */
    @Builder.Default
    private Map<String, String> playerSessions = new ConcurrentHashMap<>();

    // ==================== ВСПОМОГАТЕЛЬНЫЕ ПОЛЯ ====================

    /**
     * Флаг, идет ли игра
     */
    @Builder.Default
    private Boolean isGameRunning = false;

    /**
     * Номер текущей раздачи
     */
    @Builder.Default
    private Integer handNumber = 0;

    /**
     * ID победителя последней раздачи
     */
    private String lastWinnerId;

    /**
     * Сумма последнего выигрыша
     */
    private Integer lastWinnerAmount;

    /**
     * Флаг, завершена ли раздача
     */
    private boolean handEnded = false;

    /**
     * Имя победителя последней раздачи
     */
    private String winnerName;

    /**
     * Сумма выигрыша
     */
    private int wonAmount;

    // ==================== УПРАВЛЕНИЕ НОВОЙ РАЗДАЧЕЙ ====================

    /**
     * ID игрока, предложившего новую раздачу
     */
    private String newHandRequesterId;

    /**
     * Множество игроков, согласившихся на новую раздачу
     */
    @Builder.Default
    private Set<String> readyForNewHand = new java.util.concurrent.ConcurrentHashMap<>().newKeySet();

    // ==================== ГЕТТЕРЫ И СЕТТЕРЫ ДЛЯ НОВЫХ ПОЛЕЙ ====================

    public String getCreatorPlayerId() {
        return creatorPlayerId;
    }

    public void setCreatorPlayerId(String creatorPlayerId) {
        this.creatorPlayerId = creatorPlayerId;
    }

    public String getNewHandRequesterId() {
        return newHandRequesterId;
    }

    public void setNewHandRequesterId(String newHandRequesterId) {
        this.newHandRequesterId = newHandRequesterId;
    }

    public Set<String> getReadyForNewHand() {
        return readyForNewHand;
    }

    public void setReadyForNewHand(Set<String> readyForNewHand) {
        this.readyForNewHand = readyForNewHand;
    }

    public boolean isHandEnded() {
        return handEnded;
    }

    public void setHandEnded(boolean handEnded) {
        this.handEnded = handEnded;
    }

    // ==================== МЕТОДЫ КОПИРОВАНИЯ ====================

    /**
     * Создает глубокую копию состояния игры
     */
    public GameState copy() {
        GameState copy = GameState.builder()
                .gameId(this.gameId)
                .gameName(this.gameName)
                .creatorPlayerId(this.creatorPlayerId)
                .state(this.state)
                .pot(this.pot)
                .currentBet(this.currentBet)
                .minBet(this.minBet)
                .smallBlind(this.smallBlind)
                .bigBlind(this.bigBlind)
                .currentRound(this.currentRound)
                .currentPlayerId(this.currentPlayerId)
                .dealerId(this.dealerId)
                .lastRaisePlayerId(this.lastRaisePlayerId)
                .roundStartTime(this.roundStartTime)
                .turnTimeoutSeconds(this.turnTimeoutSeconds)
                .isGameRunning(this.isGameRunning)
                .handNumber(this.handNumber)
                .lastWinnerId(this.lastWinnerId)
                .lastWinnerAmount(this.lastWinnerAmount)
                .winnerName(this.winnerName)
                .wonAmount(this.wonAmount)
                .handEnded(this.handEnded)
                .newHandRequesterId(this.newHandRequesterId)
                .build();

        // Копируем игроков
        Map<String, PlayerState> copiedPlayers = new ConcurrentHashMap<>();
        if (this.players != null) {
            for (Map.Entry<String, PlayerState> entry : this.players.entrySet()) {
                copiedPlayers.put(entry.getKey(), entry.getValue().copy());
            }
        }
        copy.setPlayers(copiedPlayers);

        // Копируем readyForNewHand
        if (this.readyForNewHand != null) {
            Set<String> copiedReady = java.util.concurrent.ConcurrentHashMap.newKeySet();
            copiedReady.addAll(this.readyForNewHand);
            copy.setReadyForNewHand(copiedReady);
        }

        // Копируем очередь ходов
        if (this.turnOrder != null) {
            copy.setTurnOrder(new ArrayList<>(this.turnOrder));
        }

        // Копируем общие карты
        if (this.communityCards != null) {
            List<Card> copiedCards = new ArrayList<>();
            for (Card card : this.communityCards) {
                if (card != null) {
                    copiedCards.add(card.copy());
                }
            }
            copy.setCommunityCards(copiedCards);
        }

        // Копируем колоду
        if (this.deck != null) {
            List<Card> copiedDeck = new ArrayList<>();
            for (Card card : this.deck) {
                if (card != null) {
                    copiedDeck.add(card.copy());
                }
            }
            copy.setDeck(copiedDeck);
        }

        // Копируем WebSocket сессии
        if (this.playerSessions != null) {
            copy.setPlayerSessions(new ConcurrentHashMap<>(this.playerSessions));
        }

        return copy;
    }

    // ==================== ВНУТРЕННИЙ КЛАСС СОСТОЯНИЯ ИГРОКА ====================

    /**
     * Состояние игрока за столом
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerState {

        /**
         * ID игрока
         */
        private String playerId;

        /**
         * Имя игрока
         */
        private String playerName;

        /**
         * Карты игрока на руках (2 карты)
         */
        @Builder.Default
        private List<Card> holeCards = new ArrayList<>();

        /**
         * Количество фишек у игрока
         */
        @Builder.Default
        private Integer chips = 1000;

        /**
         * Текущая ставка игрока в этой раздаче
         */
        @Builder.Default
        private Integer currentBet = 0;

        /**
         * Активен ли игрок (не выбыл)
         */
        @Builder.Default
        private Boolean isActive = true;

        /**
         * Сбросил ли игрок карты
         */
        @Builder.Default
        private Boolean isFolded = false;

        /**
         * Пошел ли игрок all-in
         */
        @Builder.Default
        private Boolean isAllIn = false;

        /**
         * Номер места за столом
         */
        private Integer seatNumber;

        /**
         * Является ли игрок дилером
         */
        @Builder.Default
        private Boolean isDealer = false;

        /**
         * Является ли игрок малым блайндом
         */
        @Builder.Default
        private Boolean isSmallBlind = false;

        /**
         * Является ли игрок большим блайндом
         */
        @Builder.Default
        private Boolean isBigBlind = false;

        /**
         * Последнее действие игрока
         */
        private String lastAction;

        /**
         * Сумма последней ставки
         */
        private Integer lastActionAmount;

        /**
         * Комбинация игрока (после вскрытия)
         */
        private String handRank;

        /**
         * Описание комбинации
         */
        private String handDescription;

        /**
         * Значение комбинации для сравнения
         */
        private Integer handValue;

        /**
         * Время последнего действия (для таймера)
         */
        private Long lastActionTime;

        // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

        /**
         * Проверить, может ли игрок сделать чек
         */
        public boolean canCheck() {
            return isActive && !isFolded && !isAllIn && currentBet == 0;
        }

        /**
         * Проверить, может ли игрок уравнять ставку
         */
        public boolean canCall(int callAmount) {
            return isActive && !isFolded && !isAllIn && chips >= callAmount;
        }

        /**
         * Проверить, может ли игрок поднять ставку
         */
        public boolean canRaise(int callAmount, int minRaise) {
            return isActive && !isFolded && !isAllIn && chips >= callAmount + minRaise;
        }

        /**
         * Проверить, может ли игрок сбросить карты
         */
        public boolean canFold() {
            return isActive && !isFolded;
        }

        /**
         * Проверить, может ли игрок пойти all-in
         */
        public boolean canAllIn() {
            return isActive && !isFolded && !isAllIn;
        }

        /**
         * Получить сумму для уравнивания
         */
        public int getCallAmount(int currentBet) {
            return Math.max(0, currentBet - this.currentBet);
        }

        /**
         * Получить строковое представление карт для UI
         */
        public String getHoleCardsString() {
            if (holeCards == null || holeCards.isEmpty()) {
                return "?? ??";
            }
            return holeCards.stream()
                    .map(Card::toString)
                    .reduce((a, b) -> a + " " + b)
                    .orElse("?? ??");
        }

        /**
         * Получить одну карту для UI (если нужно показать одну карту)
         */
        public String getFirstCardString() {
            if (holeCards == null || holeCards.isEmpty()) {
                return "??";
            }
            return holeCards.get(0).toString();
        }

        /**
         * Обновить время последнего действия
         */
        public void updateLastActionTime() {
            this.lastActionTime = System.currentTimeMillis();
        }

        /**
         * Проверить, истекло ли время хода
         */
        public boolean isTimeout(int timeoutSeconds) {
            if (lastActionTime == null) {
                return false;
            }
            return System.currentTimeMillis() - lastActionTime > timeoutSeconds * 1000L;
        }

        /**
         * Сбросить состояние для новой раздачи
         */
        public void resetForNewHand() {
            holeCards.clear();
            currentBet = 0;
            isFolded = false;
            isAllIn = false;
            lastAction = null;
            lastActionAmount = null;
            handRank = null;
            handDescription = null;
            handValue = null;
        }

        /**
         * Создает копию состояния игрока
         */
        public PlayerState copy() {
            PlayerState copy = PlayerState.builder()
                    .playerId(this.playerId)
                    .playerName(this.playerName)
                    .chips(this.chips)
                    .currentBet(this.currentBet)
                    .isActive(this.isActive)
                    .isFolded(this.isFolded)
                    .isAllIn(this.isAllIn)
                    .seatNumber(this.seatNumber)
                    .isDealer(this.isDealer)
                    .isSmallBlind(this.isSmallBlind)
                    .isBigBlind(this.isBigBlind)
                    .lastAction(this.lastAction)
                    .lastActionAmount(this.lastActionAmount)
                    .handRank(this.handRank)
                    .handDescription(this.handDescription)
                    .handValue(this.handValue)
                    .lastActionTime(this.lastActionTime)
                    .build();

            // Копируем карты на руках
            if (this.holeCards != null) {
                List<Card> copiedCards = new ArrayList<>();
                for (Card card : this.holeCards) {
                    if (card != null) {
                        copiedCards.add(card.copy());
                    }
                }
                copy.setHoleCards(copiedCards);
            } else {
                copy.setHoleCards(new ArrayList<>());
            }

            return copy;
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ GameState ====================

    /**
     * Получить количество активных игроков
     */
    public int getActivePlayersCount() {
        return (int) players.values().stream()
                .filter(p -> p.getIsActive() && !p.getIsFolded())
                .count();
    }

    /**
     * Получить количество игроков, которые еще в игре (не выбыли)
     */
    public int getPlayersInHandCount() {
        return (int) players.values().stream()
                .filter(p -> p.getIsActive() && !p.getIsFolded() && p.getChips() > 0)
                .count();
    }

    /**
     * Получить список активных игроков
     */
    public List<PlayerState> getActivePlayers() {
        return players.values().stream()
                .filter(p -> p.getIsActive() && !p.getIsFolded())
                .toList();
    }

    /**
     * Получить список игроков, которые еще в раздаче
     */
    public List<PlayerState> getPlayersInHand() {
        return players.values().stream()
                .filter(p -> p.getIsActive() && !p.getIsFolded() && p.getChips() > 0)
                .toList();
    }

    /**
     * Получить состояние игрока по ID
     */
    public PlayerState getPlayer(String playerId) {
        return players.get(playerId);
    }

    /**
     * Добавить или обновить игрока
     */
    public void addPlayer(PlayerState player) {
        players.put(player.getPlayerId(), player);
    }

    /**
     * Удалить игрока
     */
    public void removePlayer(String playerId) {
        players.remove(playerId);
    }

    /**
     * Проверить, все ли игроки сделали одинаковые ставки
     */
    public boolean areAllBetsEqual() {
        List<PlayerState> activePlayers = getPlayersInHand();
        if (activePlayers.isEmpty()) {
            return true;
        }

        int maxBet = activePlayers.stream()
                .mapToInt(PlayerState::getCurrentBet)
                .max()
                .orElse(0);

        return activePlayers.stream()
                .allMatch(p -> p.getCurrentBet() == maxBet || p.getIsAllIn());
    }

    /**
     * Получить максимальную ставку в текущем раунде
     */
    public int getMaxBet() {
        return players.values().stream()
                .filter(p -> p.getIsActive() && !p.getIsFolded())
                .mapToInt(PlayerState::getCurrentBet)
                .max()
                .orElse(0);
    }

    /**
     * Получить следующего игрока в очереди
     */
    public String getNextPlayer() {
        if (turnOrder == null || turnOrder.isEmpty()) {
            return null;
        }

        int currentIndex = turnOrder.indexOf(currentPlayerId);
        if (currentIndex == -1) {
            return turnOrder.get(0);
        }

        for (int i = 1; i <= turnOrder.size(); i++) {
            int nextIndex = (currentIndex + i) % turnOrder.size();
            String nextPlayerId = turnOrder.get(nextIndex);
            PlayerState player = players.get(nextPlayerId);

            if (player != null && player.getIsActive() && !player.getIsFolded() && !player.getIsAllIn()) {
                return nextPlayerId;
            }
        }
        return null;
    }

    /**
     * Получить общую сумму ставок в банке
     */
    public int calculateTotalPot() {
        return players.values().stream()
                .mapToInt(PlayerState::getCurrentBet)
                .sum();
    }

    /**
     * Сбросить ставки для нового раунда
     */
    public void resetBetsForNewRound() {
        players.values().forEach(player -> {
            player.setCurrentBet(0);
        });
        currentBet = 0;
        lastRaisePlayerId = null;
    }

    /**
     * Сбросить состояние игры для новой раздачи
     */
    public void resetForNewHand() {
        pot = 0;
        currentBet = 0;
        currentRound = 0;
        communityCards.clear();
        lastRaisePlayerId = null;

        players.values().forEach(PlayerState::resetForNewHand);
    }

    /**
     * Получить список ID всех игроков
     */
    public List<String> getAllPlayerIds() {
        return new ArrayList<>(players.keySet());
    }

    /**
     * Получить список активных ID игроков
     */
    public List<String> getActivePlayerIds() {
        return players.values().stream()
                .filter(p -> p.getIsActive())
                .map(PlayerState::getPlayerId)
                .toList();
    }

    /**
     * Проверить, есть ли еще активные игроки
     */
    public boolean hasActivePlayers() {
        return players.values().stream().anyMatch(p -> p.getIsActive() && p.getChips() > 0);
    }

    /**
     * Получить игрока по номеру места
     */
    public PlayerState getPlayerBySeat(int seatNumber) {
        return players.values().stream()
                .filter(p -> p.getSeatNumber() != null && p.getSeatNumber() == seatNumber)
                .findFirst()
                .orElse(null);
    }

    /**
     * Обновить время последнего действия игрока
     */
    public void updatePlayerActionTime(String playerId) {
        PlayerState player = players.get(playerId);
        if (player != null) {
            player.updateLastActionTime();
        }
    }

    /**
     * Проверить, истекло ли время хода у игрока
     */
    public boolean isPlayerTimeout(String playerId) {
        PlayerState player = players.get(playerId);
        if (player == null) {
            return false;
        }
        return player.isTimeout(turnTimeoutSeconds);
    }

    /**
     * Получить следующего игрока для автоматического действия при таймауте
     */
    public String getNextPlayerAfterTimeout(String currentPlayerId) {
        PlayerState currentPlayer = players.get(currentPlayerId);
        if (currentPlayer != null) {
            currentPlayer.setIsFolded(true);
            currentPlayer.setLastAction("timeout_fold");
        }
        return getNextPlayer();
    }

    /**
     * Преобразовать в JSON для отправки клиенту
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"gameId\":\"").append(gameId).append("\",");
        sb.append("\"state\":\"").append(state).append("\",");
        sb.append("\"pot\":").append(pot).append(",");
        sb.append("\"currentBet\":").append(currentBet).append(",");
        sb.append("\"creatorPlayerId\":\"").append(creatorPlayerId != null ? creatorPlayerId : "").append("\",");
        sb.append("\"isGameRunning\":").append(isGameRunning).append(",");
        sb.append("\"communityCards\":[");

        if (communityCards != null) {
            for (int i = 0; i < communityCards.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(communityCards.get(i).toString()).append("\"");
            }
        }

        sb.append("],");
        sb.append("\"players\":{");

        boolean first = true;
        for (PlayerState player : players.values()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(player.getPlayerId()).append("\":{");
            sb.append("\"name\":\"").append(player.getPlayerName()).append("\",");
            sb.append("\"chips\":").append(player.getChips()).append(",");
            sb.append("\"currentBet\":").append(player.getCurrentBet()).append(",");
            sb.append("\"isActive\":").append(player.getIsActive()).append(",");
            sb.append("\"isFolded\":").append(player.getIsFolded()).append(",");
            sb.append("\"cards\":\"").append(player.getHoleCardsString()).append("\"");
            sb.append("}");
        }

        sb.append("},");
        sb.append("\"currentPlayerId\":\"").append(currentPlayerId != null ? currentPlayerId : "").append("\"");
        sb.append("}");

        return sb.toString();
    }
}
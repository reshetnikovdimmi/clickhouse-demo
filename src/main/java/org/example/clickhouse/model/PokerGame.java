package org.example.clickhouse.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Модель игры в покер
 * Содержит всю информацию о текущей игре: состояние, банк, ставки
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PokerGame {

    /**
     * Уникальный идентификатор игры
     * Генерируется из timestamp
     */
    private Long gameId;

    /**
     * Название игры
     * Пример: "Texas Hold'em", "Cash Game #123"
     */
    private String gameName;

    /**
     * Текущее состояние игры
     * WAITING - ожидание игроков
     * PRE_FLOP - префлоп (до открытия общих карт)
     * FLOP - флоп (первые 3 общие карты)
     * TURN - терн (4-я общая карта)
     * RIVER - ривер (5-я общая карта)
     * SHOWDOWN - вскрытие карт
     * FINISHED - игра завершена
     */
    private String gameState;

    /**
     * Общая сумма ставок в текущей раздаче
     * Суммируется из ставок всех игроков
     */
    private Integer pot;

    /**
     * Текущая ставка, которую нужно уравнять
     * Для продолжения игры нужно поставить не меньше этой суммы
     */
    private Integer currentBet;

    /**
     * Минимальная ставка
     * Обычно равна big blind
     */
    private Integer minBet;

    /**
     * Максимальное количество игроков за столом
     * Обычно 6 или 9
     */
    private Integer maxPlayers;

    /**
     * Малый блайнд (маленькая обязательная ставка)
     * Первый игрок слева от дилера
     */
    private Integer smallBlind;

    /**
     * Большой блайнд (большая обязательная ставка)
     * Второй игрок слева от дилера
     */
    private Integer bigBlind;

    /**
     * Общие карты на столе
     * Список из 5 карт: флоп (3), терн (1), ривер (1)
     * Пример: ["2♠", "7♥", "K♣", "A♦", "5♠"]
     */
    private List<String> communityCards;

    /**
     * Ставки каждого игрока в текущей раздаче
     * Map: playerId -> сумма ставки
     */
    private Map<String, Integer> playerBets;

    /**
     * ID игрока, который сейчас ходит
     */
    private String currentPlayerId;

    /**
     * Количество колод в игре
     * Стандартно 1 колода (52 карты)
     */
    private Integer deckCount;

    /**
     * ID победителя раздачи
     */
    private String winnerId;

    /**
     * Сумма выигрыша
     */
    private Integer winnerAmount;

    /**
     * Время начала игры
     */
    private LocalDateTime startedAt;

    /**
     * Время завершения игры
     */
    private LocalDateTime finishedAt;
}

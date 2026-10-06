package org.example.clickhouse.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Модель раздачи в покер
 * Сохраняет информацию о каждой раздаче для статистики и аналитики
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PokerHand {

    /**
     * Уникальный идентификатор раздачи
     */
    private Long handId;

    /**
     * ID игры, в которой произошла раздача
     */
    private Long gameId;

    /**
     * Номер раздачи в игре
     * Нумеруется с 1
     */
    private Integer handNumber;

    /**
     * Карты игрока на руках (2 карты)
     * Пример: ["A♠", "K♠"]
     */
    private List<String> holeCards;

    /**
     * Общие карты на столе
     * Пример: ["2♠", "7♥", "K♣", "A♦", "5♠"]
     */
    private List<String> communityCards;

    /**
     * ID игрока
     */
    private String playerId;

    /**
     * Ранг комбинации (текстовое описание)
     * HIGH_CARD - старшая карта
     * ONE_PAIR - одна пара
     * TWO_PAIR - две пары
     * THREE_OF_KIND - сет (три одинаковые карты)
     * STRAIGHT - стрит
     * FLUSH - флеш
     * FULL_HOUSE - фулл-хаус
     * FOUR_OF_KIND - каре
     * STRAIGHT_FLUSH - стрит-флеш
     * ROYAL_FLUSH - роял-флеш
     */
    private String handRank;

    /**
     * Описание комбинации для UI
     * Пример: "Pair of Aces", "Flush of Hearts"
     */
    private String handDescription;

    /**
     * Числовое значение комбинации (0-1000)
     * Используется для сравнения рук
     */
    private Integer handValue;

    /**
     * Является ли игрок победителем в этой раздаче
     */
    private Boolean isWinner;

    /**
     * Сумма выигрыша
     */
    private Integer winnings;

    /**
     * Время раздачи
     */
    private LocalDateTime handTime;
}

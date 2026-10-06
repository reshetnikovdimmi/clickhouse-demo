package org.example.clickhouse.game;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Ранги покерных комбинаций от старшей к младшей
 * Каждый ранг имеет числовое значение для сравнения
 */
@Getter
@AllArgsConstructor
public enum HandRank {

    HIGH_CARD(1, "High Card", "Старшая карта"),
    ONE_PAIR(2, "One Pair", "Одна пара"),
    TWO_PAIR(3, "Two Pair", "Две пары"),
    THREE_OF_KIND(4, "Three of a Kind", "Сет (три одинаковые карты)"),
    STRAIGHT(5, "Straight", "Стрит"),
    FLUSH(6, "Flush", "Флеш"),
    FULL_HOUSE(7, "Full House", "Фулл-хаус"),
    FOUR_OF_KIND(8, "Four of a Kind", "Каре"),
    STRAIGHT_FLUSH(9, "Straight Flush", "Стрит-флеш"),
    ROYAL_FLUSH(10, "Royal Flush", "Роял-флеш");

    private final int value;
    private final String name;
    private final String description;

    /**
     * Получить ранг по числовому значению
     */
    public static HandRank fromValue(int value) {
        for (HandRank rank : HandRank.values()) {
            if (rank.value == value) {
                return rank;
            }
        }
        return HIGH_CARD;
    }

    /**
     * Проверить, является ли комбинация выше другой
     */
    public boolean isHigherThan(HandRank other) {
        return this.value > other.value;
    }
}

package org.example.clickhouse.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Модель игральной карты
 * Используется в логике игры для работы с картами
 */
@Data                      // Генерирует getters, setters, toString, equals, hashCode
@AllArgsConstructor        // Конструктор со всеми полями
@NoArgsConstructor         // Конструктор без аргументов
public class Card {

    /**
     * Ранг карты (достоинство)
     * Возможные значения: 2,3,4,5,6,7,8,9,10,J,Q,K,A
     */
    private String rank;

    /**
     * Масть карты
     * Возможные значения: SPADES (пики), HEARTS (червы), CLUBS (трефы), DIAMONDS (бубны)
     */
    private String suit;

    /**
     * Получить код карты для отображения
     * Пример: "A♠", "K♥", "10♣"
     * @return строка с рангом и символом масти
     */
    public String getCardCode() {
        return rank + getSuitSymbol();
    }

    /**
     * Получить числовое значение ранга для сравнения
     * @return числовое значение: 2-10, 11 для J, 12 для Q, 13 для K, 14 для A
     */
    public int getRankValue() {
        switch (rank) {
            case "J": return 11;
            case "Q": return 12;
            case "K": return 13;
            case "A": return 14;
            default: return Integer.parseInt(rank);
        }
    }

    /**
     * Получить символ масти для отображения в UI
     * @return символ масти: ♠, ♥, ♣, ♦
     */
    public String getSuitSymbol() {
        switch (suit) {
            case "SPADES": return "♠";
            case "HEARTS": return "♥";
            case "CLUBS": return "♣";
            case "DIAMONDS": return "♦";
            default: return suit;
        }
    }

    /**
     * Проверить, являются ли две карты одной масти
     * @param other другая карта
     * @return true если масти совпадают
     */
    public boolean sameSuit(Card other) {
        return this.suit.equals(other.suit);
    }

    /**
     * Строковое представление карты для UI
     * Пример: "A♠", "10♥"
     */
    @Override
    public String toString() {
        return rank + getSuitSymbol();
    }

    /**
     * Создает глубокую копию карты
     */
    public Card copy() {
        return new Card(this.rank, this.suit);
    }
}
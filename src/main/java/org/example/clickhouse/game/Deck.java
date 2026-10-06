package org.example.clickhouse.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Колода игральных карт
 * Управляет 52 картами, перетасовкой и раздачей
 */
public class Deck {

    /**
     * Список карт в колоде
     */
    private List<Card> cards;

    /**
     * Все возможные ранги карт (от 2 до ТУЗ)
     */
    private static final String[] RANKS = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};

    /**
     * Все возможные масти
     */
    private static final String[] SUITS = {"SPADES", "HEARTS", "CLUBS", "DIAMONDS"};

    /**
     * Конструктор - создает новую колоду из 52 карт
     */
    public Deck() {
        cards = new ArrayList<>();
        // Для каждой масти создаем карты всех рангов
        for (String suit : SUITS) {
            for (String rank : RANKS) {
                cards.add(new Card(rank, suit));
            }
        }
    }

    /**
     * Перетасовать колоду
     * Использует алгоритм Fisher-Yates через Collections.shuffle()
     */
    public void shuffle() {
        Collections.shuffle(cards);
    }

    /**
     * Выдать одну карту из колоды
     * @return верхнюю карту колоды или null если колода пуста
     */
    public Card dealCard() {
        if (cards.isEmpty()) {
            return null;
        }
        return cards.remove(0);
    }

    /**
     * Выдать несколько карт
     * @param count количество карт для выдачи
     * @return список выданных карт
     */
    public List<Card> dealCards(int count) {
        List<Card> dealtCards = new ArrayList<>();
        for (int i = 0; i < count && !cards.isEmpty(); i++) {
            dealtCards.add(dealCard());
        }
        return dealtCards;
    }

    /**
     * Получить количество оставшихся карт
     * @return размер колоды
     */
    public int size() {
        return cards.size();
    }

    /**
     * Сбросить колоду (создать новую)
     * Создает новую колоду из 52 карт и перемешивает
     */
    public void reset() {
        cards.clear();
        for (String suit : SUITS) {
            for (String rank : RANKS) {
                cards.add(new Card(rank, suit));
            }
        }
        shuffle();
    }

    /**
     * Получить все карты (для отладки)
     * @return копия списка карт
     */
    public List<Card> getCards() {
        return new ArrayList<>(cards);
    }
}

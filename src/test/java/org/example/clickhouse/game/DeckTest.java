package org.example.clickhouse.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Тесты колоды карт")
class DeckTest {

    private Deck deck;

    @BeforeEach
    void setUp() {
        deck = new Deck();
    }

    @Test
    @DisplayName("Колода должна содержать 52 карты")
    void deckShouldHave52Cards() {
        assertThat(deck.size()).isEqualTo(52);
    }

    @Test
    @DisplayName("После раздачи карты удаляются из колоды")
    void cardIsRemovedAfterDeal() {
        Card card = deck.dealCard();
        assertThat(card).isNotNull();
        assertThat(deck.size()).isEqualTo(51);
    }

    @Test
    @DisplayName("Раздача нескольких карт работает корректно")
    void dealMultipleCards() {
        int cardsToDeal = 5;
        var cards = deck.dealCards(cardsToDeal);

        assertThat(cards).hasSize(cardsToDeal);
        assertThat(deck.size()).isEqualTo(52 - cardsToDeal);
    }

    @Test
    @DisplayName("После перетасовки порядок карт меняется")
    void shuffleChangesOrder() {
        Deck deck2 = new Deck();

        // Сохраняем порядок первой колоды
        var firstDeckCards = deck.getCards();

        // Перетасовываем
        deck.shuffle();
        var shuffledCards = deck.getCards();

        // Порядок должен измениться (хотя теоретически может остаться таким же)
        // Проверяем, что не все карты на тех же позициях
        boolean anyDifferent = false;
        for (int i = 0; i < 52; i++) {
            if (!firstDeckCards.get(i).getCardCode().equals(shuffledCards.get(i).getCardCode())) {
                anyDifferent = true;
                break;
            }
        }
        assertThat(anyDifferent).isTrue();
    }

    @Test
    @DisplayName("Сброс колоды создает новую полную колоду")
    void resetCreatesNewFullDeck() {
        // Раздаем несколько карт
        deck.dealCards(10);
        assertThat(deck.size()).isEqualTo(42);

        // Сбрасываем
        deck.reset();
        assertThat(deck.size()).isEqualTo(52);
    }

    @Test
    @DisplayName("Все 52 карты уникальны")
    void allCardsAreUnique() {
        var cards = deck.getCards();
        long uniqueCount = cards.stream()
                .map(Card::getCardCode)
                .distinct()
                .count();

        assertThat(uniqueCount).isEqualTo(52);
    }
}
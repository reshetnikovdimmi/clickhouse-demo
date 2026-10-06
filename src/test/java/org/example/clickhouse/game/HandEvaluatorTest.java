package org.example.clickhouse.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Тесты оценки покерных комбинаций")
class HandEvaluatorTest {

    private List<Card> communityCards;
    private List<Card> holeCards;

    @BeforeEach
    void setUp() {
        communityCards = new ArrayList<>();
        holeCards = new ArrayList<>();
    }

    @Test
    @DisplayName("Старшая карта - туз")
    void highCardAce() {
        holeCards.add(new Card("A", "SPADES"));
        holeCards.add(new Card("2", "HEARTS"));
        communityCards.add(new Card("3", "CLUBS"));
        communityCards.add(new Card("5", "DIAMONDS"));
        communityCards.add(new Card("7", "SPADES"));
        communityCards.add(new Card("9", "HEARTS"));
        communityCards.add(new Card("J", "CLUBS"));

        var result = HandEvaluator.evaluateHand(holeCards, communityCards);

        assertThat(result.getRank()).isEqualTo(HandRank.HIGH_CARD);
        assertThat(result.getRankValues().get(0)).isEqualTo(14); // Туз
    }

    @Test
    @DisplayName("Одна пара")
    void onePair() {
        holeCards.add(new Card("A", "SPADES"));
        holeCards.add(new Card("A", "HEARTS"));
        communityCards.add(new Card("2", "CLUBS"));
        communityCards.add(new Card("3", "DIAMONDS"));
        communityCards.add(new Card("5", "SPADES"));
        communityCards.add(new Card("7", "HEARTS"));
        communityCards.add(new Card("9", "CLUBS"));

        var result = HandEvaluator.evaluateHand(holeCards, communityCards);

        assertThat(result.getRank()).isEqualTo(HandRank.ONE_PAIR);
        assertThat(result.getRankValues().get(0)).isEqualTo(14); // Пара тузов
    }

    @Test
    @DisplayName("Две пары")
    void twoPair() {
        holeCards.add(new Card("A", "SPADES"));
        holeCards.add(new Card("A", "HEARTS"));
        communityCards.add(new Card("K", "CLUBS"));
        communityCards.add(new Card("K", "DIAMONDS"));
        communityCards.add(new Card("2", "SPADES"));
        communityCards.add(new Card("3", "HEARTS"));
        communityCards.add(new Card("5", "CLUBS"));

        var result = HandEvaluator.evaluateHand(holeCards, communityCards);

        assertThat(result.getRank()).isEqualTo(HandRank.TWO_PAIR);
        assertThat(result.getRankValues().get(0)).isEqualTo(14); // Старшая пара тузов
        assertThat(result.getRankValues().get(1)).isEqualTo(13); // Младшая пара королей
    }

    @Test
    @DisplayName("Сет (три одинаковые карты)")
    void threeOfKind() {
        holeCards.add(new Card("A", "SPADES"));
        holeCards.add(new Card("A", "HEARTS"));
        communityCards.add(new Card("A", "CLUBS"));
        communityCards.add(new Card("2", "DIAMONDS"));
        communityCards.add(new Card("3", "SPADES"));
        communityCards.add(new Card("5", "HEARTS"));
        communityCards.add(new Card("7", "CLUBS"));

        var result = HandEvaluator.evaluateHand(holeCards, communityCards);

        assertThat(result.getRank()).isEqualTo(HandRank.THREE_OF_KIND);
        assertThat(result.getRankValues().get(0)).isEqualTo(14);
    }

    @Test
    @DisplayName("Стрит (5 последовательных карт)")
    void straight() {
        holeCards.add(new Card("5", "SPADES"));
        holeCards.add(new Card("6", "HEARTS"));
        communityCards.add(new Card("7", "CLUBS"));
        communityCards.add(new Card("8", "DIAMONDS"));
        communityCards.add(new Card("9", "SPADES"));
        communityCards.add(new Card("J", "HEARTS"));
        communityCards.add(new Card("2", "CLUBS"));

        var result = HandEvaluator.evaluateHand(holeCards, communityCards);

        assertThat(result.getRank()).isEqualTo(HandRank.STRAIGHT);
        assertThat(result.getRankValues().get(0)).isEqualTo(9);
        assertThat(result.getRankValues().get(1)).isEqualTo(8);
        assertThat(result.getRankValues().get(2)).isEqualTo(7);
        assertThat(result.getRankValues().get(3)).isEqualTo(6);
        assertThat(result.getRankValues().get(4)).isEqualTo(5);
    }

    @Test
    @DisplayName("Флеш (5 карт одной масти)")
    void flush() {
        holeCards.add(new Card("A", "SPADES"));
        holeCards.add(new Card("K", "SPADES"));
        communityCards.add(new Card("2", "SPADES"));
        communityCards.add(new Card("4", "SPADES"));
        communityCards.add(new Card("6", "SPADES"));
        communityCards.add(new Card("8", "HEARTS"));
        communityCards.add(new Card("10", "CLUBS"));

        var result = HandEvaluator.evaluateHand(holeCards, communityCards);

        assertThat(result.getRank()).isEqualTo(HandRank.FLUSH);
    }

    @Test
    @DisplayName("Фулл-хаус (сет + пара)")
    void fullHouse() {
        holeCards.add(new Card("A", "SPADES"));
        holeCards.add(new Card("A", "HEARTS"));
        communityCards.add(new Card("A", "CLUBS"));
        communityCards.add(new Card("K", "DIAMONDS"));
        communityCards.add(new Card("K", "SPADES"));
        communityCards.add(new Card("2", "HEARTS"));
        communityCards.add(new Card("3", "CLUBS"));

        var result = HandEvaluator.evaluateHand(holeCards, communityCards);

        assertThat(result.getRank()).isEqualTo(HandRank.FULL_HOUSE);
        assertThat(result.getRankValues().get(0)).isEqualTo(14);
        assertThat(result.getRankValues().get(1)).isEqualTo(13);
    }

    @Test
    @DisplayName("Каре (4 одинаковые карты)")
    void fourOfKind() {
        holeCards.add(new Card("A", "SPADES"));
        holeCards.add(new Card("A", "HEARTS"));
        communityCards.add(new Card("A", "CLUBS"));
        communityCards.add(new Card("A", "DIAMONDS"));
        communityCards.add(new Card("K", "SPADES"));
        communityCards.add(new Card("2", "HEARTS"));
        communityCards.add(new Card("3", "CLUBS"));

        var result = HandEvaluator.evaluateHand(holeCards, communityCards);

        assertThat(result.getRank()).isEqualTo(HandRank.FOUR_OF_KIND);
        assertThat(result.getRankValues().get(0)).isEqualTo(14);
    }

    @Test
    @DisplayName("Стрит-флеш")
    void straightFlush() {
        holeCards.add(new Card("5", "SPADES"));
        holeCards.add(new Card("6", "SPADES"));
        communityCards.add(new Card("7", "SPADES"));
        communityCards.add(new Card("8", "SPADES"));
        communityCards.add(new Card("9", "SPADES"));
        communityCards.add(new Card("J", "HEARTS"));
        communityCards.add(new Card("2", "CLUBS"));

        var result = HandEvaluator.evaluateHand(holeCards, communityCards);

        assertThat(result.getRank()).isEqualTo(HandRank.STRAIGHT_FLUSH);
    }

    @Test
    @DisplayName("Роял-флеш")
    void royalFlush() {
        holeCards.add(new Card("A", "SPADES"));
        holeCards.add(new Card("K", "SPADES"));
        communityCards.add(new Card("Q", "SPADES"));
        communityCards.add(new Card("J", "SPADES"));
        communityCards.add(new Card("10", "SPADES"));
        communityCards.add(new Card("2", "HEARTS"));
        communityCards.add(new Card("3", "CLUBS"));

        var result = HandEvaluator.evaluateHand(holeCards, communityCards);

        assertThat(result.getRank()).isEqualTo(HandRank.ROYAL_FLUSH);
    }

    @Test
    @DisplayName("Сравнение рук - более сильная рука побеждает")
    void compareHandsHigherWins() {
        // Пара тузов
        List<Card> holeCards1 = List.of(
                new Card("A", "SPADES"),
                new Card("A", "HEARTS")
        );
        List<Card> communityCards1 = List.of(
                new Card("2", "CLUBS"),
                new Card("3", "DIAMONDS"),
                new Card("5", "SPADES"),
                new Card("7", "HEARTS"),
                new Card("9", "CLUBS")
        );

        // Две пары
        List<Card> holeCards2 = List.of(
                new Card("K", "SPADES"),
                new Card("K", "HEARTS")
        );
        List<Card> communityCards2 = List.of(
                new Card("Q", "CLUBS"),
                new Card("Q", "DIAMONDS"),
                new Card("5", "SPADES"),
                new Card("7", "HEARTS"),
                new Card("9", "CLUBS")
        );

        var hand1 = HandEvaluator.evaluateHand(holeCards1, communityCards1);
        var hand2 = HandEvaluator.evaluateHand(holeCards2, communityCards2);

        // Две пары должны быть сильнее одной пары
        assertThat(HandEvaluator.compareHands(hand2, hand1)).isGreaterThan(0);
    }
}
package org.example.clickhouse.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Оценщик покерных комбинаций
 * Определяет лучшую комбинацию из 7 карт (2 на руках + 5 на столе)
 */
@Slf4j
public class HandEvaluator {

    /**
     * Результат оценки комбинации
     */
    @Data
    @AllArgsConstructor
    public static class HandEvaluation {
        private HandRank rank;
        private List<Card> bestCards;
        private List<Integer> rankValues;
        private String description;

        public HandEvaluation(HandRank rank, List<Card> bestCards, List<Integer> rankValues) {
            this.rank = rank;
            this.bestCards = bestCards;
            this.rankValues = rankValues;
            this.description = String.format("%s - %s", rank.getName(), formatCards(bestCards));
        }

        private String formatCards(List<Card> cards) {
            return cards.stream()
                    .map(Card::toString)
                    .collect(Collectors.joining(", "));
        }
    }

    /**
     * Оценить комбинацию игрока
     * @param holeCards 2 карты на руках
     * @param communityCards 5 общих карт на столе
     * @return результат оценки
     */
    public static HandEvaluation evaluateHand(List<Card> holeCards, List<Card> communityCards) {
        if (holeCards == null || communityCards == null) {
            return new HandEvaluation(HandRank.HIGH_CARD, new ArrayList<>(), new ArrayList<>());
        }

        // Объединяем все 7 карт
        List<Card> allCards = new ArrayList<>();
        allCards.addAll(holeCards);
        allCards.addAll(communityCards);

        // Проверяем комбинации от старшей к младшей
        HandEvaluation result = checkRoyalFlush(allCards);
        if (result != null) return result;

        result = checkStraightFlush(allCards);
        if (result != null) return result;

        result = checkFourOfKind(allCards);
        if (result != null) return result;

        result = checkFullHouse(allCards);
        if (result != null) return result;

        result = checkFlush(allCards);
        if (result != null) return result;

        result = checkStraight(allCards);
        if (result != null) return result;

        result = checkThreeOfKind(allCards);
        if (result != null) return result;

        result = checkTwoPair(allCards);
        if (result != null) return result;

        result = checkOnePair(allCards);
        if (result != null) return result;

        return checkHighCard(allCards);
    }

    /**
     * Проверка роял-флеша (A, K, Q, J, 10 одной масти)
     */
    private static HandEvaluation checkRoyalFlush(List<Card> cards) {
        Map<String, List<Card>> cardsBySuit = groupBySuit(cards);

        for (List<Card> suitedCards : cardsBySuit.values()) {
            if (suitedCards.size() >= 5) {
                Set<String> royalRanks = new HashSet<>(Arrays.asList("10", "J", "Q", "K", "A"));
                List<Card> royalCards = suitedCards.stream()
                        .filter(c -> royalRanks.contains(c.getRank()))
                        .collect(Collectors.toList());

                if (royalCards.size() >= 5) {
                    List<Card> bestCards = royalCards.stream()
                            .sorted((c1, c2) -> Integer.compare(c2.getRankValue(), c1.getRankValue()))
                            .limit(5)
                            .collect(Collectors.toList());
                    return new HandEvaluation(HandRank.ROYAL_FLUSH, bestCards, List.of(14));
                }
            }
        }
        return null;
    }

    /**
     * Проверка стрит-флеша
     */
    private static HandEvaluation checkStraightFlush(List<Card> cards) {
        Map<String, List<Card>> cardsBySuit = groupBySuit(cards);

        for (List<Card> suitedCards : cardsBySuit.values()) {
            if (suitedCards.size() >= 5) {
                List<Integer> values = suitedCards.stream()
                        .map(Card::getRankValue)
                        .distinct()
                        .sorted(Collections.reverseOrder())
                        .collect(Collectors.toList());

                List<Integer> straight = findStraight(values);
                if (straight != null && straight.size() >= 5) {
                    List<Card> bestCards = suitedCards.stream()
                            .filter(c -> straight.contains(c.getRankValue()))
                            .sorted((c1, c2) -> Integer.compare(c2.getRankValue(), c1.getRankValue()))
                            .limit(5)
                            .collect(Collectors.toList());
                    return new HandEvaluation(HandRank.STRAIGHT_FLUSH, bestCards, straight);
                }
            }
        }
        return null;
    }

    /**
     * Проверка каре
     */
    private static HandEvaluation checkFourOfKind(List<Card> cards) {
        Map<Integer, List<Card>> cardsByValue = groupByValue(cards);

        for (Map.Entry<Integer, List<Card>> entry : cardsByValue.entrySet()) {
            if (entry.getValue().size() >= 4) {
                List<Card> fourCards = entry.getValue().subList(0, 4);
                // Добавляем самую старшую кикер карту
                Card kicker = cards.stream()
                        .filter(c -> c.getRankValue() != entry.getKey())
                        .max(Comparator.comparingInt(Card::getRankValue))
                        .orElse(null);

                List<Card> bestCards = new ArrayList<>(fourCards);
                if (kicker != null) bestCards.add(kicker);

                return new HandEvaluation(HandRank.FOUR_OF_KIND, bestCards,
                        List.of(entry.getKey(), kicker != null ? kicker.getRankValue() : 0));
            }
        }
        return null;
    }

    /**
     * Проверка фулл-хауса (сет + пара)
     */
    private static HandEvaluation checkFullHouse(List<Card> cards) {
        Map<Integer, List<Card>> cardsByValue = groupByValue(cards);

        Integer threeValue = null;
        Integer pairValue = null;
        List<Card> threeCards = null;
        List<Card> pairCards = null;

        for (Map.Entry<Integer, List<Card>> entry : cardsByValue.entrySet()) {
            if (entry.getValue().size() >= 3) {
                if (threeValue == null || entry.getKey() > threeValue) {
                    threeValue = entry.getKey();
                    threeCards = entry.getValue().subList(0, 3);
                }
            }
        }

        if (threeValue != null) {
            for (Map.Entry<Integer, List<Card>> entry : cardsByValue.entrySet()) {
                if (entry.getKey() != threeValue && entry.getValue().size() >= 2) {
                    if (pairValue == null || entry.getKey() > pairValue) {
                        pairValue = entry.getKey();
                        pairCards = entry.getValue().subList(0, 2);
                    }
                }
            }
        }

        if (threeValue != null && pairValue != null) {
            List<Card> bestCards = new ArrayList<>();
            bestCards.addAll(threeCards);
            bestCards.addAll(pairCards);
            return new HandEvaluation(HandRank.FULL_HOUSE, bestCards, List.of(threeValue, pairValue));
        }
        return null;
    }

    /**
     * Проверка флеша
     */
    private static HandEvaluation checkFlush(List<Card> cards) {
        Map<String, List<Card>> cardsBySuit = groupBySuit(cards);

        for (List<Card> suitedCards : cardsBySuit.values()) {
            if (suitedCards.size() >= 5) {
                List<Card> bestCards = suitedCards.stream()
                        .sorted((c1, c2) -> Integer.compare(c2.getRankValue(), c1.getRankValue()))
                        .limit(5)
                        .collect(Collectors.toList());

                List<Integer> values = bestCards.stream()
                        .map(Card::getRankValue)
                        .collect(Collectors.toList());

                return new HandEvaluation(HandRank.FLUSH, bestCards, values);
            }
        }
        return null;
    }

    /**
     * Проверка стрита
     */
    private static HandEvaluation checkStraight(List<Card> cards) {
        List<Integer> values = cards.stream()
                .map(Card::getRankValue)
                .distinct()
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());

        // Специальная обработка для A-5-4-3-2
        if (values.contains(14) && values.contains(5) && values.contains(4) && values.contains(3) && values.contains(2)) {
            return new HandEvaluation(HandRank.STRAIGHT,
                    getCardsByValues(cards, List.of(5, 4, 3, 2, 14)),
                    List.of(5, 4, 3, 2, 14));
        }

        List<Integer> straight = findStraight(values);
        if (straight != null && straight.size() >= 5) {
            List<Card> straightCards = getCardsByValues(cards, straight.subList(0, 5));
            return new HandEvaluation(HandRank.STRAIGHT, straightCards, straight);
        }
        return null;
    }

    /**
     * Проверка сета
     */
    private static HandEvaluation checkThreeOfKind(List<Card> cards) {
        Map<Integer, List<Card>> cardsByValue = groupByValue(cards);

        for (Map.Entry<Integer, List<Card>> entry : cardsByValue.entrySet()) {
            if (entry.getValue().size() >= 3) {
                List<Card> threeCards = entry.getValue().subList(0, 3);

                // Добавляем две самые старшие кикер карты
                List<Card> kickers = cards.stream()
                        .filter(c -> c.getRankValue() != entry.getKey())
                        .sorted((c1, c2) -> Integer.compare(c2.getRankValue(), c1.getRankValue()))
                        .limit(2)
                        .collect(Collectors.toList());

                List<Card> bestCards = new ArrayList<>(threeCards);
                bestCards.addAll(kickers);

                List<Integer> values = new ArrayList<>();
                values.add(entry.getKey());
                values.addAll(kickers.stream().map(Card::getRankValue).collect(Collectors.toList()));

                return new HandEvaluation(HandRank.THREE_OF_KIND, bestCards, values);
            }
        }
        return null;
    }

    /**
     * Проверка двух пар
     */
    private static HandEvaluation checkTwoPair(List<Card> cards) {
        Map<Integer, List<Card>> cardsByValue = groupByValue(cards);
        List<Integer> pairs = new ArrayList<>();
        List<List<Card>> pairCardsList = new ArrayList<>();

        for (Map.Entry<Integer, List<Card>> entry : cardsByValue.entrySet()) {
            if (entry.getValue().size() >= 2) {
                pairs.add(entry.getKey());
                pairCardsList.add(entry.getValue().subList(0, 2));
            }
        }

        if (pairs.size() >= 2) {
            pairs.sort(Collections.reverseOrder());
            pairCardsList.sort((a, b) -> Integer.compare(b.get(0).getRankValue(), a.get(0).getRankValue()));

            List<Card> bestCards = new ArrayList<>();
            bestCards.addAll(pairCardsList.get(0));
            bestCards.addAll(pairCardsList.get(1));

            // Добавляем кикер
            Card kicker = cards.stream()
                    .filter(c -> c.getRankValue() != pairs.get(0) && c.getRankValue() != pairs.get(1))
                    .max(Comparator.comparingInt(Card::getRankValue))
                    .orElse(null);

            if (kicker != null) bestCards.add(kicker);

            List<Integer> values = new ArrayList<>();
            values.add(pairs.get(0));
            values.add(pairs.get(1));
            if (kicker != null) values.add(kicker.getRankValue());

            return new HandEvaluation(HandRank.TWO_PAIR, bestCards, values);
        }
        return null;
    }

    /**
     * Проверка одной пары
     */
    private static HandEvaluation checkOnePair(List<Card> cards) {
        Map<Integer, List<Card>> cardsByValue = groupByValue(cards);

        for (Map.Entry<Integer, List<Card>> entry : cardsByValue.entrySet()) {
            if (entry.getValue().size() >= 2) {
                List<Card> pairCards = entry.getValue().subList(0, 2);

                // Добавляем три самые старшие кикер карты
                List<Card> kickers = cards.stream()
                        .filter(c -> c.getRankValue() != entry.getKey())
                        .sorted((c1, c2) -> Integer.compare(c2.getRankValue(), c1.getRankValue()))
                        .limit(3)
                        .collect(Collectors.toList());

                List<Card> bestCards = new ArrayList<>(pairCards);
                bestCards.addAll(kickers);

                List<Integer> values = new ArrayList<>();
                values.add(entry.getKey());
                values.addAll(kickers.stream().map(Card::getRankValue).collect(Collectors.toList()));

                return new HandEvaluation(HandRank.ONE_PAIR, bestCards, values);
            }
        }
        return null;
    }

    /**
     * Старшая карта
     */
    private static HandEvaluation checkHighCard(List<Card> cards) {
        List<Card> bestCards = cards.stream()
                .sorted((c1, c2) -> Integer.compare(c2.getRankValue(), c1.getRankValue()))
                .limit(5)
                .collect(Collectors.toList());

        List<Integer> values = bestCards.stream()
                .map(Card::getRankValue)
                .collect(Collectors.toList());

        return new HandEvaluation(HandRank.HIGH_CARD, bestCards, values);
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private static Map<String, List<Card>> groupBySuit(List<Card> cards) {
        Map<String, List<Card>> result = new HashMap<>();
        for (Card card : cards) {
            result.computeIfAbsent(card.getSuit(), k -> new ArrayList<>()).add(card);
        }
        return result;
    }

    private static Map<Integer, List<Card>> groupByValue(List<Card> cards) {
        Map<Integer, List<Card>> result = new HashMap<>();
        for (Card card : cards) {
            result.computeIfAbsent(card.getRankValue(), k -> new ArrayList<>()).add(card);
        }
        // Сортируем карты по убыванию для каждого значения
        for (List<Card> list : result.values()) {
            list.sort((c1, c2) -> Integer.compare(c2.getRankValue(), c1.getRankValue()));
        }
        return result;
    }

    private static List<Integer> findStraight(List<Integer> values) {
        List<Integer> straight = new ArrayList<>();
        int consecutive = 1;
        straight.add(values.get(0));

        for (int i = 1; i < values.size() && consecutive < 5; i++) {
            if (values.get(i - 1) - values.get(i) == 1) {
                consecutive++;
                straight.add(values.get(i));
            } else if (values.get(i - 1) != values.get(i)) {
                consecutive = 1;
                straight.clear();
                straight.add(values.get(i));
            }
        }

        return consecutive >= 5 ? straight : null;
    }

    private static List<Card> getCardsByValues(List<Card> cards, List<Integer> values) {
        List<Card> result = new ArrayList<>();
        for (Integer value : values) {
            cards.stream()
                    .filter(c -> c.getRankValue() == value)
                    .findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }

    /**
     * Сравнить две комбинации
     * @return положительное число если hand1 > hand2, отрицательное если hand1 < hand2, 0 если равны
     */
    public static int compareHands(HandEvaluation hand1, HandEvaluation hand2) {
        if (hand1.getRank().getValue() != hand2.getRank().getValue()) {
            return Integer.compare(hand1.getRank().getValue(), hand2.getRank().getValue());
        }

        List<Integer> values1 = hand1.getRankValues();
        List<Integer> values2 = hand2.getRankValues();

        for (int i = 0; i < Math.min(values1.size(), values2.size()); i++) {
            if (!values1.get(i).equals(values2.get(i))) {
                return Integer.compare(values1.get(i), values2.get(i));
            }
        }
        return 0;
    }
}

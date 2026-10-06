package org.example.clickhouse.game;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Этапы покерной раздачи
 */
@Getter
@AllArgsConstructor
public enum GameStage {

    WAITING(0, "WAITING", "Ожидание игроков"),
    PRE_FLOP(1, "PRE_FLOP", "Префлоп"),
    FLOP(2, "FLOP", "Флоп (3 карты)"),
    TURN(3, "TURN", "Терн (4-я карта)"),
    RIVER(4, "RIVER", "Ривер (5-я карта)"),
    SHOWDOWN(5, "SHOWDOWN", "Вскрытие"),
    FINISHED(6, "FINISHED", "Завершено");

    private final int order;
    private final String code;
    private final String description;

    /**
     * Получить следующий этап
     */
    public GameStage next() {
        switch (this) {
            case PRE_FLOP: return FLOP;
            case FLOP: return TURN;
            case TURN: return RIVER;
            case RIVER: return SHOWDOWN;
            default: return this;
        }
    }

    /**
     * Получить количество карт на столе для этого этапа
     */
    public int getCommunityCardsCount() {
        switch (this) {
            case PRE_FLOP: return 0;
            case FLOP: return 3;
            case TURN: return 4;
            case RIVER: return 5;
            default: return 0;
        }
    }

    public static GameStage fromCode(String code) {
        for (GameStage stage : values()) {
            if (stage.code.equals(code)) {
                return stage;
            }
        }
        return WAITING;
    }
}

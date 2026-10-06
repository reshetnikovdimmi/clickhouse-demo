package org.example.clickhouse.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Модель действия игрока
 * Логирует каждое действие для аналитики и воспроизведения
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PokerAction {

    /**
     * Уникальный идентификатор действия
     * Генерируется из timestamp
     */
    private Long actionId;

    /**
     * ID игры, в которой произошло действие
     * Связь с PokerGame
     */
    private Long gameId;

    /**
     * ID игрока, совершившего действие
     * Связь с PokerPlayer
     */
    private String playerId;

    /**
     * Тип действия
     * FOLD - сброс карт (игрок выходит из раздачи)
     * CHECK - проверка (пропуск хода, когда нет ставки)
     * CALL - уравнивание ставки
     * RAISE - повышение ставки
     * ALL_IN - ставка на все фишки
     * BET - ставка (если до этого не было ставок)
     */
    private String actionType;

    /**
     * Сумма ставки (для CALL, RAISE, BET, ALL_IN)
     */
    private Integer betAmount;

    /**
     * Количество фишек после действия
     * Позволяет отслеживать изменения баланса
     */
    private Integer stackAfter;

    /**
     * Размер банка после действия
     */
    private Integer potAfter;

    /**
     * Номер раунда
     * 1 - PRE_FLOP (префлоп)
     * 2 - FLOP (флоп)
     * 3 - TURN (терн)
     * 4 - RIVER (ривер)
     * 5 - SHOWDOWN (вскрытие)
     */
    private Integer roundNumber;

    /**
     * Время совершения действия
     * Используется для анализа скорости игры
     */
    private LocalDateTime actionTime;
}

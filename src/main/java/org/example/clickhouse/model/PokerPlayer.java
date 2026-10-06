package org.example.clickhouse.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Модель игрока в покер
 * Хранит всю информацию о игроке: статистику, текущее состояние, баланс
 */
@Data                      // Lombok: генерирует getters, setters, toString, equals, hashCode
@Builder                   // Lombok: паттерн Builder для удобного создания объектов
@NoArgsConstructor         // Lombok: конструктор без аргументов
@AllArgsConstructor        // Lombok: конструктор со всеми аргументами
public class PokerPlayer {

    /**
     * Уникальный идентификатор игрока
     * Формат: player_{timestamp}_{random}
     * Пример: player_1700000000000_123
     */
    private String playerId;

    /**
     * Отображаемое имя игрока
     * Вводится при входе в игру
     */
    private String playerName;

    /**
     * Количество фишек у игрока
     * Начальное значение: 1000
     */
    private Integer chips;

    /**
     * Номер места за столом (0-5 для 6-местного стола)
     * Используется для визуального расположения на UI
     */
    private Integer seatNumber;

    /**
     * Активен ли игрок в текущей игре
     * true - участвует, false - вышел или выбыл
     */
    private Boolean isActive;

    /**
     * Является ли игрок дилером в текущей раздаче
     * Дилер определяет порядок ходов
     */
    private Boolean isDealer;

    /**
     * Количество сыгранных раздач
     * Статистика для профиля игрока
     */
    private Integer handsPlayed;

    /**
     * Количество выигранных раздач
     * Статистика для профиля игрока
     */
    private Integer handsWon;

    /**
     * Процент выигрышей (handsWon / handsPlayed * 100)
     * Рассчитывается автоматически
     */
    private Double winRate;

    /**
     * Время присоединения к игре
     */
    private LocalDateTime joinedAt;

    /**
     * Время последнего действия
     * Используется для тайм-аутов
     */
    private LocalDateTime lastActionAt;
}
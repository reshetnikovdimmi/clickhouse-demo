// ==================== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ДЛЯ КАРТ ====================

/**
 * Получить символ масти
 * @param {string} suit - масть (SPADES, HEARTS, CLUBS, DIAMONDS)
 * @returns {string} символ масти (♠, ♥, ♣, ♦)
 */
function getSuitSymbol(suit) {
    const symbols = {
        'SPADES': '♠',
        'HEARTS': '♥',
        'CLUBS': '♣',
        'DIAMONDS': '♦',
        's': '♠',
        'h': '♥',
        'c': '♣',
        'd': '♦'
    };
    return symbols[suit] || suit;
}

/**
 * Получить отображаемый ранг карты
 * @param {string} rank - ранг (2-10, J, Q, K, A)
 * @returns {string} отображаемый ранг
 */
function getRankDisplay(rank) {
    const ranks = {
        '11': 'J',
        '12': 'Q',
        '13': 'K',
        '14': 'A'
    };
    return ranks[rank] || rank;
}

/**
 * Форматировать карту для отображения
 * @param {string|object} card - карта в формате "A♠" или объект Card
 * @returns {string} отформатированная карта
 */
function formatCard(card) {
    if (!card || card === 'back') return '?';

    // Если карта в формате "A♠" или "10♥"
    if (typeof card === 'string' && card.length >= 2) {
        const rank = card.slice(0, -1);
        const suit = card.slice(-1);
        const rankDisplay = getRankDisplay(rank);
        return rankDisplay + suit;
    }

    // Если карта в формате "AS" или "10H"
    if (typeof card === 'string' && card.length >= 2) {
        const rank = card.slice(0, -1);
        const suit = card.slice(-1);
        const rankDisplay = getRankDisplay(rank);
        const suitSymbol = getSuitSymbol(suit);
        return rankDisplay + suitSymbol;
    }

    // Если карта - объект
    if (typeof card === 'object') {
        if (card.cardCode) return card.cardCode;
        if (card.rank && card.suitSymbol) return card.rank + card.suitSymbol;
        if (card.rank && card.suit) return card.rank + getSuitSymbol(card.suit);
    }

    return card;
}

/**
 * Получить CSS класс для масти
 * @param {string} cardCode - код карты (например, "A♠")
 * @returns {string} CSS класс ('red' для ♥♦, 'black' для ♠♣)
 */
function getSuitClass(cardCode) {
    if (!cardCode) return '';
    const suit = cardCode.slice(-1);
    if (suit === '♥' || suit === '♦') {
        return 'red';
    }
    return 'black';
}

/**
 * Получить цвет масти (для inline стилей)
 * @param {string} cardCode - код карты
 * @returns {string} цвет (#ff4444 для красных, #000000 для чёрных)
 */
function getSuitColor(cardCode) {
    if (!cardCode) return '#000000';
    const suit = cardCode.slice(-1);
    if (suit === '♥' || suit === '♦') {
        return '#ff4444';
    }
    return '#000000';
}

/**
 * Создать HTML элемент карты
 * @param {string} cardCode - код карты
 * @param {boolean} isFaceUp - открыта ли карта (true - лицом, false - рубашкой)
 * @returns {HTMLElement} div элемент карты
 */
function createCardElement(cardCode, isFaceUp = true) {
    const div = document.createElement('div');
    div.className = 'card';

    if (isFaceUp && cardCode && cardCode !== '??') {
        div.classList.add('front');
        div.classList.add(getSuitClass(cardCode));
        div.textContent = formatCard(cardCode);
    } else {
        div.classList.add('back');
        div.textContent = '?';
    }

    return div;
}

/**
 * Создать HTML элемент маленькой карты (для игрока)
 * @param {string} cardCode - код карты
 * @param {boolean} isFaceUp - открыта ли карта
 * @returns {HTMLElement} div элемент маленькой карты
 */
function createMiniCardElement(cardCode, isFaceUp = true) {
    const div = document.createElement('div');
    div.className = 'mini-card';

    if (isFaceUp && cardCode && cardCode !== '??') {
        div.classList.add(getSuitClass(cardCode));
        div.textContent = formatCard(cardCode);
    } else {
        div.classList.add('back');
        div.textContent = '?';
    }

    return div;
}

/**
 * Проверить, являются ли две карты одной масти
 * @param {string} card1 - первая карта
 * @param {string} card2 - вторая карта
 * @returns {boolean} true если масти совпадают
 */
function isSameSuit(card1, card2) {
    if (!card1 || !card2) return false;
    const suit1 = card1.slice(-1);
    const suit2 = card2.slice(-1);
    return suit1 === suit2;
}

/**
 * Получить значение карты для сравнения
 * @param {string} rank - ранг карты
 * @returns {number} числовое значение (2-14)
 */
function getCardValue(rank) {
    const values = {
        '2': 2, '3': 3, '4': 4, '5': 5, '6': 6, '7': 7, '8': 8, '9': 9, '10': 10,
        'J': 11, 'Q': 12, 'K': 13, 'A': 14
    };
    return values[rank] || parseInt(rank) || 0;
}

/**
 * Сортировать карты по рангу
 * @param {string[]} cards - массив кодов карт
 * @returns {string[]} отсортированный массив
 */
function sortCardsByRank(cards) {
    return cards.sort((a, b) => {
        const rankA = a.slice(0, -1);
        const rankB = b.slice(0, -1);
        return getCardValue(rankA) - getCardValue(rankB);
    });
}

/**
 * Форматировать время
 * @param {number} timestamp - timestamp в миллисекундах
 * @returns {string} отформатированное время
 */
function formatTime(timestamp) {
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
}

/**
 * Звук уведомления (опционально)
 */
function playNotificationSound() {
    try {
        const audio = new Audio('/sounds/notification.mp3');
        audio.volume = 0.3;
        audio.play().catch(e => console.log('Audio play failed:', e));
    } catch(e) {
        console.log('Audio not supported');
    }
}
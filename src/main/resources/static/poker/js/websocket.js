// ==================== WEBSOCKET МОДУЛЬ ====================

let stompClient = null;
let currentGameIdWs = null;
let currentPlayerIdWs = null;
let currentPlayerNameWs = null;

/**
 * Подключение к WebSocket
 */
function connectWebSocket(gameId, playerId, playerName, onEventReceived, onError) {
    currentGameIdWs = gameId;
    currentPlayerIdWs = playerId;
    currentPlayerNameWs = playerName;

    const socket = new SockJS('/poker-websocket');
    stompClient = Stomp.over(socket);

    // Настройка heartbeat
    stompClient.heartbeat.outgoing = 20000;
    stompClient.heartbeat.incoming = 20000;

    stompClient.connect({}, function(frame) {
        console.log('WebSocket connected');

        // Подписываемся на игровые события
        stompClient.subscribe(`/topic/poker/${gameId}`, function(msg) {
            const event = JSON.parse(msg.body);
            if (onEventReceived) onEventReceived(event);
        });

        // Подписываемся на приватные сообщения
        stompClient.subscribe(`/user/queue/poker/${gameId}`, function(msg) {
            const event = JSON.parse(msg.body);
            if (onEventReceived) onEventReceived(event);
        });

        // Подписываемся на подсказки
        stompClient.subscribe(`/user/queue/poker/${gameId}/tip`, function(msg) {
            const data = JSON.parse(msg.body);
            if (data.type === 'TIP' && onEventReceived) {
                onEventReceived({ type: 'TIP', message: data.message });
            }
        });


        // Отправляем запрос на присоединение
        const chips = parseInt(document.getElementById('startingChips')?.value) || 1000;
        sendJoin(playerId, playerName, chips);

        // Запрашиваем текущее состояние
        setTimeout(() => {
            sendStateRequest();
        }, 500);

    }, function(error) {
        console.error('WebSocket connection error:', error);
        if (onError) onError(error);
    });
}

/**
 * Отправить действие игрока
 */
function sendActionWs(action, betAmount = 0) {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket not connected');
        return false;
    }

    stompClient.send(`/app/poker/${currentGameIdWs}/action`, {}, JSON.stringify({
        playerId: currentPlayerIdWs,
        action: action,
        betAmount: betAmount
    }));

    return true;
}

/**
 * Отправить сообщение в чат
 */
function sendChatMessageWs(message) {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket not connected');
        return false;
    }

    stompClient.send(`/app/poker/${currentGameIdWs}/chat`, {}, JSON.stringify({
        playerId: currentPlayerIdWs,
        playerName: currentPlayerNameWs,
        message: message
    }));

    return true;
}

/**
 * Запросить состояние игры
 */
function sendStateRequest() {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket not connected');
        return false;
    }

    stompClient.send(`/app/poker/${currentGameIdWs}/state`, {}, JSON.stringify({}));
    return true;
}

/**
 * Запросить подсказку
 */
function sendTipRequest() {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket not connected');
        return false;
    }

    stompClient.send(`/app/poker/${currentGameIdWs}/tip`, {}, JSON.stringify({
        playerId: currentPlayerIdWs
    }));
}

/**
 * Отправить запрос на присоединение
 */
function sendJoin(playerId, playerName, chips) {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket not connected');
        return false;
    }

    stompClient.send(`/app/poker/${currentGameIdWs}/join`, {}, JSON.stringify({
        playerId: playerId,
        playerName: playerName,
        chips: chips
    }));

    return true;
}

/**
 * Отключиться от WebSocket
 */
function disconnectWebSocket() {
    if (stompClient) {
        stompClient.disconnect();
        stompClient = null;
    }
}

/**
 * Проверить, подключен ли WebSocket
 */
function isWebSocketConnected() {
    return stompClient !== null && stompClient.connected;
}

/**
 * Получить текущий Game ID
 */
function getCurrentGameIdWs() {
    return currentGameIdWs;
}
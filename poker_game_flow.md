# Полная схема работы покерной игры

## 📋 Цепочка вызовов при нажатии кнопки
Пользователь нажимает кнопку
↓

sendAction(action)
↓

stompClient.send() → WebSocket → Сервер
↓

Сервер обрабатывает действие
↓

Сервер отправляет событие обратно
↓

handleGameEvent(event)
↓

Обработка конкретного типа события
## 🔍 Детально по каждому шагу

### Шаг 1: Нажатие кнопки в HTML

```html
<button onclick="sendAction('fold')" class="btn-action fold" id="foldBtn">✗ Fold</button>
<button onclick="sendAction('check')" class="btn-action check" id="checkBtn">✓ Check</button>
<button onclick="sendAction('call')" class="btn-action call" id="callBtn">💰 Call</button>
<button onclick="sendAction('raise')" class="btn-action raise" id="raiseBtn">📈 Raise</button>
<button onclick="sendAction('all_in')" class="btn-action allin" id="allinBtn">🔥 All-in</button>

### Шаг 2: Функция sendAction(action)

function sendAction(action) {
    if (!stompClient?.connected) {
        alert('Not connected');
        return;
    }

    let betAmount = 0;
    if (action === 'raise') {
        betAmount = parseInt(document.getElementById('raiseAmount').value);
        const minBet = parseInt(document.getElementById('currentBet')?.textContent) || 10;
        if (isNaN(betAmount) || betAmount < minBet) {
            alert(`Min bet: ${minBet}`);
            return;
        }
    }

    enableActionButtons(false);

    stompClient.send(`/app/poker/${currentGameId}/action`, {}, JSON.stringify({
        playerId: currentPlayerId,
        action: action,
        betAmount: betAmount
    }));

    addToLog(`You ${action}${betAmount ? ' ' + betAmount : ''}`);

    if (action === 'raise') {
        document.getElementById('raiseAmount').value = '';
    }
}

### Шаг 3: WebSocket отправка на сервер

{
    "playerId": "player_xxx",
    "action": "fold|check|call|raise|all_in",
    "betAmount": 0
}

### Шаг 4: Клиент получает событие

stompClient.subscribe(`/topic/poker/${gameId}`, function(msg) {
    const event = JSON.parse(msg.body);
    handleGameEvent(event);
});

### Шаг 5: Функция handleGameEvent(event)

function handleGameEvent(event) {
    switch(event.type) {
        case 'GAME_STATE':
            updateGameUI(event);
            break;
        case 'PLAYER_JOINED':
            handlePlayerJoined(event);
            break;
        case 'PLAYER_ACTION':
            handlePlayerAction(event);
            break;
        case 'CHAT_MESSAGE':
            addChatMessage(event.playerName, event.message);
            break;
        case 'GAME_STARTED':
            addToLog('🎉 Game started!');
            break;
        case 'STAGE_CHANGED':
            handleStageChanged(event);
            break;
        case 'HAND_ENDED':
            handleHandEnded(event);
            break;
    }
    updateTips();
}

### 6. Обработка PLAYER_ACTION (подтверждение действия от сервера)

```javascript
function handlePlayerAction(event) {
    console.log('=== PLAYER ACTION ===');
    console.log('Player:', event.playerId, 'Action:', event.action);
    
    // 1. Логируем действие
    addToLog(`🎲 ${event.playerId === currentPlayerId ? 'You' : event.playerId} ${event.action}`);
    
    // 2. Обновляем отображение игроков (балансы, ставки, статусы)
    if (event.players) {
        players = event.players;
        updatePlayersDisplay(event.players);
    }
    
    // 3. Обновляем пот и текущую ставку
    if (event.pot !== undefined) {
        document.getElementById('potAmount').textContent = event.pot;
    }
    if (event.currentBet !== undefined) {
        document.getElementById('currentBet').textContent = event.currentBet;
    }
    
    // 4. Обновляем общие карты (если пришли)
    if (event.communityCards) {
        updateCommunityCards(event.communityCards);
    }
    
    // 5. Проверяем, чей теперь ход
    const isMyTurn = (event.currentPlayerId === currentPlayerId);
    enableActionButtons(isMyTurn);
    if (isMyTurn) addToLog('🎯 YOUR TURN!');
}

### 7. Обработка GAME_STATE (полное обновление состояния)

function updateGameUI(event) {
    // 1. Обновляем пот
    if (event.pot) document.getElementById('potAmount').textContent = event.pot;
    
    // 2. Обновляем текущую ставку
    if (event.currentBet) document.getElementById('currentBet').textContent = event.currentBet;
    
    // 3. Обновляем всех игроков
    if (event.players) {
        players = event.players;
        updatePlayersDisplay(event.players);
    }
    
    // 4. Обновляем общие карты
    if (event.communityCards) updateCommunityCards(event.communityCards);
    
    // 5. Включаем/выключаем кнопки
    const isMyTurn = (event.currentPlayerId === currentPlayerId);
    enableActionButtons(isMyTurn);
    if (isMyTurn) addToLog('🎯 YOUR TURN!');
}

### 8. Обработка STAGE_CHANGED (смена этапа игры)

function handleStageChanged(event) {
    console.log('=== STAGE CHANGED ===');
    console.log('Stage:', event.stage);
    
    addToLog(`🎴 Stage: ${event.stage}`);
    
    // Обновляем общие карты (новые карты на столе)
    if (event.communityCards) {
        updateCommunityCards(event.communityCards);
    }
}

### 9. Обработка HAND_ENDED (завершение раздачи)

function handleHandEnded(event) {
    console.log('=== HAND ENDED ===');
    console.log('Winner:', event.winnerName);
    
    addToLog(`🏆 ${event.winnerName} won ${event.wonAmount || event.pot} chips!`);
    addChatMessage('System', `${event.winnerName} won!`, true);
    
    // Обнуляем пот и ставки
    document.getElementById('potAmount').textContent = '0';
    document.getElementById('currentBet').textContent = '0';
    
    // Обновляем балансы игроков
    if (event.players) {
        players = event.players;
        updatePlayersDisplay(event.players);
    }
    
    // Очищаем общие карты
    updateCommunityCards([]);
    
    // Запрашиваем новое состояние
    setTimeout(() => {
        if (stompClient && stompClient.connected) {
            stompClient.send(`/app/poker/${currentGameId}/state`, {}, JSON.stringify({}));
        }
    }, 1000);
}

### 10. Обработка PLAYER_JOINED (новый игрок)

function handlePlayerJoined(event) {
    addToLog(`👤 ${event.playerName} joined`);
    
    // Обновляем список игроков
    if (event.players) {
        players = event.players;
        updatePlayersDisplay(event.players);
    }
    
    updateTips();
}

### 🎯 Что происходит при каждом действии

Действие	Что делает клиент	Что делает сервер	Что получает клиент
Fold	Отправляет fold	Помечает игрока как folded	PLAYER_ACTION → HAND_ENDED
Check	Отправляет check	Проверяет возможность check	PLAYER_ACTION → GAME_STATE
Call	Отправляет call	Списывает фишки, обновляет пот	PLAYER_ACTION → GAME_STATE
Raise	Отправляет raise + сумму	Списывает фишки, обновляет ставку	PLAYER_ACTION → GAME_STATE
All-in	Отправляет all_in	Списывает все фишки	PLAYER_ACTION → GAME_STATE

### 📋 Список всех функций

Основные функции
Функция	Назначение
sendAction(action)	Отправляет действие игрока на сервер
handleGameEvent(event)	Главный обработчик всех событий
updateGameUI(event)	Обновляет интерфейс при GAME_STATE
handlePlayerAction(event)	Обрабатывает подтверждение действий
handleStageChanged(event)	Обрабатывает смену этапа
handleHandEnded(event)	Обрабатывает завершение раздачи
updateTips()	Обновляет динамические подсказки

### UI функции

Функция	Назначение
updatePlayersDisplay(players)	Отображает игроков
createPlayerCardElement(player)	Создает карточку игрока
updateCommunityCards(cards)	Отображает общие карты
enableActionButtons(enabled)	Управляет кнопками
addToLog(message)	Добавляет в лог
addChatMessage(sender, message)	Добавляет в чат

Отправка действий

Функция	Назначение	Вызывается
sendAction(action)	Отправляет действие игрока на сервер	При нажатии кнопки

Обработка событий от сервера

Функция	Назначение	Тип события
handleGameEvent(event)	Главный диспетчер событий	Все типы
updateGameUI(event)	Обновление интерфейса	GAME_STATE
handlePlayerAction(event)	Подтверждение действия	PLAYER_ACTION
handlePlayerJoined(event)	Новый игрок	PLAYER_JOINED
handleStageChanged(event)	Смена этапа	STAGE_CHANGED
handleHandEnded(event)	Завершение раздачи	HAND_ENDED

Вспомогательные функции

Функция	Назначение
addToLog(message)	Добавляет сообщение в лог игры
addChatMessage(sender, message, isSystem)	Добавляет сообщение в чат
getCardColor(cardCode)	Определяет цвет масти карты
escapeHtml(text)	Экранирует HTML для безопасности

### 📊 Полная диаграмма потока с обработчиками

┌─────────────────────────────────────────────────────────────────────┐
│                         КЛИЕНТ (БРАУЗЕР)                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Нажатие кнопки → sendAction() → stompClient.send()                 │
│                           ↓                                          │
│                    WebSocket отправка                                │
│                           ↓                                          │
├─────────────────────────────────────────────────────────────────────┤
│                         СЕРВЕР                                       │
├─────────────────────────────────────────────────────────────────────┤
│                           ↓                                          │
│              PokerWebSocketController.playerAction()                 │
│                           ↓                                          │
│              PokerGameEngine.processAction()                         │
│                           ↓                                          │
│              Обработка действия (fold/check/call/raise/all_in)       │
│                           ↓                                          │
│              Обновление состояния игры                               │
│                           ↓                                          │
│              Отправка события клиентам                               │
│                           ↓                                          │
├─────────────────────────────────────────────────────────────────────┤
│                         КЛИЕНТ (БРАУЗЕР)                             │
├─────────────────────────────────────────────────────────────────────┤
│                           ↓                                          │
│         stompClient.subscribe() получает событие                     │
│                           ↓                                          │
│              handleGameEvent(event)                                  │
│                           ↓                                          │
│         switch(event.type) - выбор обработчика                       │
│         ├── GAME_STATE → updateGameUI()                              │
│         ├── PLAYER_ACTION → handlePlayerAction()                     │
│         ├── PLAYER_JOINED → handlePlayerJoined()                     │
│         ├── STAGE_CHANGED → handleStageChanged()                     │
│         └── HAND_ENDED → handleHandEnded()                           │
│                           ↓                                          │
│         updatePlayersDisplay() → обновление карточек игроков         │
│         updateCommunityCards() → обновление карт на столе            │
│         enableActionButtons() → обновление состояния кнопок          │
│         updateTips() → обновление подсказок                          │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘

### 🔄 Порядок вызовов при разных действиях

При Fold (сброс карт)
text
1. sendAction('fold')
2. Сервер обрабатывает fold
3. handleGameEvent(GAME_STATE) - обновление состояния
4. updateGameUI() - обновление UI
5. updatePlayersDisplay() - игрок помечается как folded
6. enableActionButtons(false) - кнопки отключаются
7. updateTips() - обновление подсказки

При Fold (сброс карт)
text
1. sendAction('fold')
2. Сервер обрабатывает fold
3. handleGameEvent(GAME_STATE) - обновление состояния
4. updateGameUI() - обновление UI
5. updatePlayersDisplay() - игрок помечается как folded
6. enableActionButtons(false) - кнопки отключаются
7. updateTips() - обновление подсказки

При завершении раздачи (HAND_ENDED)
text
1. Сервер отправляет HAND_ENDED
2. handleHandEnded()
3. addToLog() - сообщение о победителе
4. addChatMessage() - системное сообщение
5. Обнуление pot и currentBet
6. updatePlayersDisplay() - обновление балансов
7. updateCommunityCards([]) - очистка карт
8. Запрос нового состояния через 1 секунду







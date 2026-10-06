// Глобальные переменные
let currentGameId = null;
let currentPlayerId = null;
let currentPlayerName = null;
let players = {};
let gamesRefreshInterval = null;
let pendingNewHand = false;
let newHandRequesterId = null;
let newHandRequesterName = null;

// ==================== ЗАГРУЗКА ====================

document.addEventListener('DOMContentLoaded', function() {
    console.log('Page loaded');
    loadActiveGames();
    gamesRefreshInterval = setInterval(loadActiveGames, 5000);

    const raiseAmount = document.getElementById('raiseAmount');
    if (raiseAmount) {
        raiseAmount.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') sendAction('raise');
        });
    }
});

// ==================== ПОДСКАЗКИ ====================

function updateTips() {
    const tipCard = document.querySelector('.tip-card.current-situation');
    if (!tipCard) return;

    const tipTitle = tipCard.querySelector('.tip-title');
    const tipText = tipCard.querySelector('.tip-text');

    if (!currentGameId) {
        const gamesList = document.getElementById('gamesList');
        const hasGames = gamesList && gamesList.children.length > 0 &&
                        !gamesList.innerHTML.includes('No active') &&
                        !gamesList.innerHTML.includes('Loading');

        if (!hasGames) {
            tipTitle.textContent = '🚀 Нет активных игр';
            tipText.innerHTML = '1️⃣ Введите имя<br>2️⃣ Нажмите "Create Game"<br>3️⃣ Пригласите друга или откройте вторую вкладку';
        } else {
            tipTitle.textContent = '🎮 Есть активные игры';
            tipText.innerHTML = '✅ Нажмите на игру в списке<br>🆕 Или создайте новую<br>🔗 Или введите ID игры в поле ниже';
        }
        return;
    }

    const playersCount = Object.keys(players).length;
    const startBtn = document.getElementById('startGameBtn');
    const isGameRunning = startBtn && startBtn.style.display === 'none' && !startBtn.disabled;

    // Если игра ещё не началась
    if (!isGameRunning && playersCount < 2) {
        tipTitle.textContent = '👥 Ожидание игроков';
        tipText.innerHTML = `ID игры: <strong>${currentGameId}</strong><br>Поделитесь ID с другом<br>Или откройте вторую вкладку<br><br>Нужно минимум 2 игрока для старта`;
        return;
    }

    if (!isGameRunning && playersCount >= 2) {
        tipTitle.textContent = '🎮 Игра не начата';
        tipText.innerHTML = `За столом ${playersCount} игроков.<br>Создатель игры может нажать "Начать раздачу"`;
        return;
    }

    const currentBet = parseInt(document.getElementById('currentBet')?.textContent) || 0;
    const isMyTurn = !document.getElementById('foldBtn')?.disabled;

    if (isMyTurn) {
        if (currentBet === 0) {
            tipTitle.textContent = '✅ Ваш ход! Нет ставок';
            tipText.innerHTML = 'Check - пропустить<br>Bet - сделать ставку<br>Fold - сбросить карты';
        } else {
            tipTitle.textContent = `⚠️ Ставка ${currentBet}`;
            tipText.innerHTML = `Call - уравнять ${currentBet}<br>Raise - поднять<br>Fold - сбросить<br>All-in - все фишки`;
        }
    } else {
        tipTitle.textContent = '⏳ Ожидание';
        tipText.innerHTML = 'Сейчас не ваш ход<br>Следите за игрой';
    }
}

// ==================== ЛОББИ ====================

async function loadActiveGames() {
    try {
        const response = await fetch('/api/poker/games');
        const data = await response.json();
        const gamesList = document.getElementById('gamesList');

        if (!gamesList) return;

        if (data.success && data.games && data.games.length > 0) {
            gamesList.innerHTML = '';
            data.games.forEach(gameId => {
                const card = document.createElement('div');
                card.className = 'game-card';
                card.onclick = () => {
                    const name = document.getElementById('playerName').value.trim();
                    if (!name) {
                        alert('Enter your name first');
                        return;
                    }
                    joinGame(gameId, name);
                };
                card.innerHTML = `<h3>Game ${gameId.slice(-8)}</h3><div class="game-details">ID: ${gameId}</div><div class="game-status">🟢 Active</div>`;
                gamesList.appendChild(card);
            });
        } else {
            gamesList.innerHTML = '<div class="loading">No active games. Create one!</div>';
        }
        updateTips();
    } catch (error) {
        console.error('Error loading games:', error);
        const gamesList = document.getElementById('gamesList');
        if (gamesList) gamesList.innerHTML = '<div class="loading error">Failed to load games</div>';
    }
}

async function createGame() {
    const playerName = document.getElementById('playerName').value.trim();
    if (!playerName) {
        alert('Please enter your name');
        return;
    }

    currentPlayerName = playerName;
    currentPlayerId = 'player_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);

    const gameData = {
        gameName: document.getElementById('gameName').value || 'Texas Hold\'em',
        minBet: parseInt(document.getElementById('minBet').value),
        maxPlayers: parseInt(document.getElementById('maxPlayers').value),
        startingChips: parseInt(document.getElementById('startingChips').value),
        smallBlind: parseInt(document.getElementById('smallBlind').value),
        bigBlind: parseInt(document.getElementById('bigBlind').value),
        creatorPlayerId: currentPlayerId  // ✅ Добавляем ID создателя
    };

    try {
        const response = await fetch('/api/poker/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(gameData)
        });
        const data = await response.json();
        if (data.success) {
            currentGameId = data.gameId;
            joinGameRoom(currentGameId);
        } else {
            alert('Failed to create game');
        }
    } catch (error) {
        console.error('Error:', error);
        alert('Failed to create game');
    }
}
async function joinGame(gameId, playerName) {
    currentPlayerName = playerName;
    currentPlayerId = 'player_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    currentGameId = gameId;
    joinGameRoom(gameId);
}

async function joinByGameId() {
    const gameId = document.getElementById('joinGameId').value.trim();
    const playerName = document.getElementById('playerName').value.trim();
    if (!gameId) {
        alert('Enter Game ID');
        return;
    }
    if (!playerName) {
        alert('Enter your name');
        return;
    }
    joinGame(gameId, playerName);
}

function joinGameRoom(gameId) {
    document.getElementById('lobbyScreen').style.display = 'none';
    document.getElementById('gameScreen').style.display = 'block';
    document.getElementById('gameIdDisplay').textContent = 'Game ID: ' + gameId;
    document.getElementById('currentPlayerName').textContent = currentPlayerName;

    // Используем WebSocket модуль
    connectWebSocket(gameId, currentPlayerId, currentPlayerName, onWebSocketEvent, onWebSocketError);
}

function showHandResultModal(event) {
    const modal = document.getElementById('handResultModal');
    document.getElementById('winnerName').textContent = event.winnerName;
    document.getElementById('winnerAmount').textContent = `+${event.wonAmount} chips`;

    const statsContainer = document.getElementById('playersStats');
    statsContainer.innerHTML = '<h3>📊 Результаты игроков</h3>';

    for (const [id, player] of Object.entries(event.players)) {
        const statDiv = document.createElement('div');
        statDiv.className = 'player-stat';
        statDiv.innerHTML = `
            <span class="name">${player.playerName}${player.playerId === currentPlayerId ? ' (You)' : ''}</span>
            <span class="chips">💰 ${player.chips}</span>
        `;
        statsContainer.appendChild(statDiv);
    }

    const handSummary = document.getElementById('handSummary');
    if (event.winnerHand) {
        handSummary.innerHTML = `🎴 Выигрышная комбинация: ${event.winnerHand}`;
    } else {
        handSummary.innerHTML = '💡 Игрок сбросил карты, победа без показа';
    }

    // Сбрасываем состояние
    newHandRequesterId = null;
    newHandRequesterName = null;
    updateModalButtons();

    modal.style.display = 'flex';
    pendingNewHand = true;
}
function updateModalButtons() {
    const modal = document.getElementById('handResultModal');
    const footer = modal.querySelector('.modal-footer');

    if (newHandRequesterId === null) {
        // Никто не запрашивал - показываем кнопку "Запросить новую раздачу"
        footer.innerHTML = `
            <button onclick="requestNewHand()" class="btn-primary">🔄 Запросить новую раздачу</button>
            <button onclick="leaveToLobby()" class="btn-leave">🚪 Выйти в лобби</button>
        `;
    } else if (newHandRequesterId === currentPlayerId) {
        // Я запросил - ждём согласия других
        footer.innerHTML = `
            <div class="waiting-message">⏳ Ожидание согласия других игроков...</div>
            <button onclick="leaveToLobby()" class="btn-leave">🚪 Выйти в лобби</button>
        `;
    } else {
        // Кто-то другой запросил - показываем кнопку подтверждения
        footer.innerHTML = `
            <div class="request-message">🎮 Игрок ${newHandRequesterName} предлагает начать новую раздачу</div>
            <button onclick="confirmNewHand()" class="btn-primary">✅ Согласен, начать новую раздачу</button>
            <button onclick="leaveToLobby()" class="btn-leave">🚪 Выйти в лобби</button>
        `;
    }
}

function startNewHand() {
    console.log('Starting new hand...');

    // Скрываем модальное окно
    document.getElementById('handResultModal').style.display = 'none';

    // Отправляем запрос на новую раздачу
    if (stompClient && stompClient.connected) {
        stompClient.send(`/app/poker/${currentGameId}/newHand`, {}, JSON.stringify({
            playerId: currentPlayerId
        }));
    }

    pendingNewHand = false;
}

function leaveToLobby() {
    console.log('Leaving to lobby from modal...');

    // Закрываем модальное окно результатов
    const resultModal = document.getElementById('handResultModal');
    if (resultModal) resultModal.style.display = 'none';

    // Выходим из игры
    leaveGame();
}

// Обновляем leaveGame для очистки флага
function leaveGame() {
    console.log('Leaving game...');

    // Отправляем событие о выходе на сервер
    if (stompClient && stompClient.connected && currentGameId && currentPlayerId) {
        stompClient.send(`/app/poker/${currentGameId}/leave`, {}, JSON.stringify({
            playerId: currentPlayerId
        }));

        // Небольшая задержка перед отключением, чтобы сообщение ушло
        setTimeout(() => {
            disconnectAndReturnToLobby();
        }, 200);
    } else {
        disconnectAndReturnToLobby();
    }
}
function disconnectAndReturnToLobby() {
    if (stompClient) {
        try {
            stompClient.disconnect();
        } catch(e) {
            console.error('Error disconnecting:', e);
        }
        stompClient = null;
    }

    // Очищаем все глобальные переменные
    currentGameId = null;
    currentPlayerId = null;
    currentPlayerName = null;
    players = {};
    pendingNewHand = false;
    newHandRequesterId = null;
    newHandRequesterName = null;

    // Переключаем экраны
    document.getElementById('lobbyScreen').style.display = 'block';
    document.getElementById('gameScreen').style.display = 'none';

    // Очищаем игровую область
    const playersArea = document.getElementById('playersArea');
    if (playersArea) playersArea.innerHTML = '';

    const communityCards = document.getElementById('communityCards');
    if (communityCards) {
        communityCards.innerHTML = '';
        for (let i = 0; i < 5; i++) {
            const div = document.createElement('div');
            div.className = 'card back';
            div.textContent = '?';
            communityCards.appendChild(div);
        }
    }

    // Обновляем список игр
    loadActiveGames();

    // Закрываем все модальные окна
    const modals = ['handResultModal', 'gameEndedModal'];
    modals.forEach(modalId => {
        const modal = document.getElementById(modalId);
        if (modal) modal.style.display = 'none';
    });

    addToLog('Вы вернулись в лобби');
}
// ==================== УПРАВЛЕНИЕ ИГРОЙ ====================

function startGame() {
    if (!stompClient || !stompClient.connected) {
        alert('Not connected to game server');
        return;
    }

    const playerCount = Object.keys(players).length;
    if (playerCount < 2) {
        alert(`Need at least 2 players to start. Currently: ${playerCount}`);
        return;
    }

    console.log('Starting game by creator request...');
    stompClient.send(`/app/poker/${currentGameId}/start`, {}, JSON.stringify({
        playerId: currentPlayerId
    }));
}
// ==================== ОБРАБОТКА ВЫХОДА ИГРОКА ====================

function handlePlayerLeft(event) {
    console.log(`Player ${event.playerName} left the game`);
    addToLog(`👋 ${event.playerName} покинул игру`);
    addChatMessage('System', `👋 ${event.playerName} покинул игру`, true);

    // Обновляем список игроков
    if (event.players) {
        players = event.players;
        updatePlayersDisplay(event.players);
    }

    // ✅ ИСПРАВЛЕНИЕ: Завершаем игру ТОЛЬКО если остался 1 игрок
    if (event.remainingPlayers === 1) {
        addToLog(`⚠️ Остался только один игрок! Игра не может продолжаться.`);
        addChatMessage('System', `⚠️ Остался только один игрок. Для продолжения нужен соперник.`, true);

        enableActionButtons(false);
        updateGameEndedTip();

        const resultModal = document.getElementById('handResultModal');
        if (resultModal) resultModal.style.display = 'none';

        showGameEndedModal("Игра не может продолжаться", "Недостаточно игроков для игры. Требуется минимум 2 игрока.");
    } else if (event.remainingPlayers >= 2) {
        // ✅ Игра продолжается!
        addToLog(`✅ Игра продолжается с ${event.remainingPlayers} игроками`);
        addChatMessage('System', `✅ Игра продолжается с ${event.remainingPlayers} игроками.`, true);

        // Обновляем состояние игры
        if (event.currentPlayerId === currentPlayerId) {
            enableActionButtons(true);
            addToLog('🎯 Ваш ход!');
        }
    }
}

function handleGameEnded(event) {
    console.log('Game ended:', event.reason);
    addToLog(`🏁 Игра завершена: ${event.reason}`);
    addChatMessage('System', `🏁 Игра завершена: ${event.reason}`, true);

    // Отключаем кнопки действий
    enableActionButtons(false);

    // Закрываем модальное окно результатов, если оно открыто
    const resultModal = document.getElementById('handResultModal');
    if (resultModal) resultModal.style.display = 'none';

    // Показываем сообщение
    let message = event.reason;
    if (event.remainingPlayer) {
        message = `${event.remainingPlayer} остался единственным игроком. Игра не может продолжаться без соперника.`;
        showGameEndedModal("Игра завершена", message, event.remainingPlayer);
    } else {
        showGameEndedModal("Игра завершена", message);
    }
}

function showGameEndedModal(title, message, remainingPlayer = null) {
    const modal = document.getElementById('gameEndedModal');
    if (!modal) {
        console.error('Game ended modal not found');
        return;
    }

    // Обновляем заголовок если нужно
    const header = modal.querySelector('.modal-header h2');
    if (header) header.textContent = title ? `🏁 ${title} 🏁` : '🏁 ИГРА ЗАВЕРШЕНА 🏁';

    // Обновляем сообщение
    const messageElement = document.getElementById('gameEndedMessage');
    if (messageElement) messageElement.textContent = message;

    // Показываем информацию об оставшемся игроке
    const remainingInfo = document.getElementById('remainingPlayerInfo');
    const remainingNameSpan = document.getElementById('remainingPlayerName');

    if (remainingPlayer && remainingNameSpan) {
        remainingNameSpan.textContent = remainingPlayer;
        if (remainingInfo) remainingInfo.style.display = 'block';
    } else {
        if (remainingInfo) remainingInfo.style.display = 'none';
    }

    modal.style.display = 'flex';
}

function closeGameEndedModalAndLeave() {
    const modal = document.getElementById('gameEndedModal');
    if (modal) modal.style.display = 'none';
    leaveGame();
}


// ==================== WEBSOCKET ОБРАБОТЧИКИ ====================

function onWebSocketEvent(event) {
    console.log('Event received:', event.type, event);

    switch(event.type) {
        case 'GAME_STATE':
            updateGameUI(event);
            break;
        case 'PLAYER_JOINED':
           // Если это сообщение с ошибкой
              if (event.error) {
                  // Если ошибка для текущего игрока - показываем уведомление
                  if (event.playerId === currentPlayerId) {
                      console.log('Join error for current player:', event.error);
                      handleJoinError(event);
                  } else {
                      // Ошибка для другого игрока - игнорируем
                      console.log('Ignoring join error for another player:', event.playerName);
                      addToLog(`⚠️ ${event.playerName} не смог присоединиться: ${event.error}`);

                  }
                  return;
              }

              // Успешное присоединение
              addToLog(`👤 ${event.playerName} joined`);
              if (event.players) updatePlayersDisplay(event.players);
              updateTips();
              break;

        case 'PLAYER_LEFT':
            handlePlayerLeft(event);
            break;
        case 'GAME_ENDED':
            handleGameEnded(event);
            break;
        case 'GAME_STARTED':
            handleGameStarted(event);
            break;
        case 'PLAYER_ACTION':
            handlePlayerAction(event);
            break;
        case 'CHAT_MESSAGE':
            addChatMessage(event.playerName, event.message);
            break;
        case 'STAGE_CHANGED':
            addToLog(`🎴 Stage: ${event.stage}`);
            if (event.communityCards) updateCommunityCards(event.communityCards);
            break;
        case 'HAND_ENDED':
            handleHandEnded(event);
            break;
        case 'NEW_HAND_REQUEST':
            handleNewHandRequest(event);
            break;
        case 'NEW_HAND_CONFIRM':
            handleNewHandConfirm(event);
            break;
        case 'NEW_HAND_STARTED':
            handleNewHandStarted(event);
            break;
        case 'TIP':
            updateDynamicTipFromServer(event.message);
            break;
        default:
            console.log('Unknown event type:', event.type);
    }
    updateTips();
}
// ==================== ОБРАБОТЧИКИ СОБЫТИЙ ====================
function handleJoinError(event) {
    console.log('Join error:', event);

    let errorMessage = '';
    let errorTitle = '❌ НЕ УДАЛОСЬ ПРИСОЕДИНИТЬСЯ';

    if (event.error === 'Failed to join game') {
        errorMessage = '❌ Игра заполнена!\n\nДостигнуто максимальное количество игроков (2).\n\nПожалуйста, создайте новую игру или выберите другую.';
    } else if (event.error === 'Game not found') {
        errorMessage = '❌ Игра не найдена!\n\nПроверьте правильность Game ID и попробуйте снова.';
    } else if (event.error === 'Game already started') {
        errorMessage = '❌ Игра уже началась!\n\nК сожалению, нельзя присоединиться к уже идущей игре.\nДождитесь следующей раздачи или создайте новую игру.';
    } else {
        errorMessage = `❌ Ошибка: ${event.error}`;
    }

    addToLog(`⚠️ ${event.playerName} не смог присоединиться: ${errorMessage}`);

    showJoinErrorModal(errorTitle, errorMessage);
}

function showJoinErrorModal(title, message) {
    let modal = document.getElementById('joinErrorModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'joinErrorModal';
        modal.className = 'modal';
        modal.innerHTML = `
            <div class="modal-content">
                <div class="modal-header">
                    <h2>${title}</h2>
                </div>
                <div class="modal-body">
                    <p id="joinErrorMessage"></p>
                </div>
                <div class="modal-footer">
                    <button onclick="closeJoinErrorModal()" class="btn-primary">Понятно</button>
                    <button onclick="closeJoinErrorModalAndReturn()" class="btn-leave">Вернуться в лобби</button>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
    }

    document.getElementById('joinErrorMessage').innerHTML = message.replace(/\n/g, '<br>');
    modal.style.display = 'flex';
}

function closeJoinErrorModal() {
    const modal = document.getElementById('joinErrorModal');
    if (modal) modal.style.display = 'none';
}

function closeJoinErrorModalAndReturn() {
    closeJoinErrorModal();
    leaveGame();
}

function handleGameStarted(event) {
    console.log('=== GAME STARTED ===');
    addToLog('🎉 Game started!');

    // Обновляем всех игроков
    if (event.players) {
        players = event.players;
        updatePlayersDisplay(event.players);
    }

    // Обновляем банк и ставку
    if (event.pot !== undefined) {
        document.getElementById('potAmount').textContent = event.pot;
    }
    if (event.currentBet !== undefined) {
        document.getElementById('currentBet').textContent = event.currentBet;
    }

    // Обновляем общие карты
    if (event.communityCards) {
        updateCommunityCards(event.communityCards);
    }

    // Проверяем, чей ход
    if (event.currentPlayerId) {
        const isMyTurn = (event.currentPlayerId === currentPlayerId);
        enableActionButtons(isMyTurn);
        if (isMyTurn) {
            addToLog('🎯 YOUR TURN!');
            // Запрашиваем подсказку
            if (isWebSocketConnected()) {
                setTimeout(() => sendTipRequest(), 100);
            }
        }
    }

    // Скрываем кнопку "Начать раздачу"
    const startBtn = document.getElementById('startGameBtn');
    if (startBtn) {
        startBtn.style.display = 'none';
    }

    updateTips();
}
function handlePlayerAction(event) {
    console.log('=== PLAYER ACTION ===');
    addToLog(`🎲 ${event.playerId} ${event.action} ${event.betAmount ? '(' + event.betAmount + ')' : ''}`);

    // Обновляем игроков
    if (event.players) {
        players = event.players;
        updatePlayersDisplay(event.players);
    }

    // Обновляем банк и ставку
    if (event.pot !== undefined) {
        document.getElementById('potAmount').textContent = event.pot;
    }
    if (event.currentBet !== undefined) {
        document.getElementById('currentBet').textContent = event.currentBet;
    }

    // Проверяем, чей теперь ход
    if (event.currentPlayerId) {
        const isMyTurn = (event.currentPlayerId === currentPlayerId);
        console.log('Is my turn?', isMyTurn);
        enableActionButtons(isMyTurn);
        if (isMyTurn) {
            addToLog('🎯 YOUR TURN!');
            if (isWebSocketConnected()) {
                setTimeout(() => sendTipRequest(), 100);
            }
        }
    }
}

function onWebSocketError(error) {
    console.error('WebSocket error:', error);
    addToLog('Failed to connect to game server');
    alert('Connection failed. Please try again.');
    leaveGame();
}

function updateDynamicTipFromServer(tipMessage) {
    const tipCard = document.querySelector('.tip-card.current-situation');
    if (!tipCard) return;

    const tipTitle = tipCard.querySelector('.tip-title');
    const tipText = tipCard.querySelector('.tip-text');

    tipTitle.textContent = '🎯 Персональный совет';
    tipText.innerHTML = tipMessage.replace(/\n/g, '<br>');
}
function requestNewHand() {
    console.log('Requesting new hand...');

    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket not connected');
        return;
    }

    stompClient.send(`/app/poker/${currentGameId}/requestNewHand`, {}, JSON.stringify({
        playerId: currentPlayerId
    }));

    newHandRequesterId = currentPlayerId;
    updateModalButtons();
}


function handleHandEnded(event) {
    console.log('=== HAND ENDED ===');
    console.log('Winner:', event.winnerName);
    console.log('Won amount:', event.wonAmount);
    console.log('Players:', event.players);

    addToLog(`🏆 ${event.winnerName} won ${event.wonAmount} chips!`);
    addChatMessage('System', `🏆 ${event.winnerName} won ${event.wonAmount} chips!`, true);

    // 1. Сохраняем игроков
    if (event.players) {
        players = event.players;
        updatePlayersDisplay(event.players);
    }
    // 2. Обнуляем пот и ставку
    document.getElementById('potAmount').textContent = '0';
    document.getElementById('currentBet').textContent = '0';
    // 3. Очищаем общие карты
    updateCommunityCards([]);
    // 4. Отключаем кнопки
    enableActionButtons(false);

    // 5. ✅ Показываем модальное окно с результатами
    showHandResultModal(event);
}

function updateGameUI(event) {
    if (event.pot) document.getElementById('potAmount').textContent = event.pot;
    if (event.currentBet) document.getElementById('currentBet').textContent = event.currentBet;
    if (event.players) {
        players = event.players;
        updatePlayersDisplay(event.players);
    }
    if (event.communityCards) updateCommunityCards(event.communityCards);

    // ✅ Показываем кнопку Start только создателю и если игра не началась
    const startBtn = document.getElementById('startGameBtn');
    if (startBtn && event.creatorPlayerId === currentPlayerId && !event.isGameRunning) {
        startBtn.style.display = 'inline-block';
        const playerCount = Object.keys(players).length;
        startBtn.disabled = (playerCount < 2);
        startBtn.textContent = playerCount < 2 ? `🎮 Ждём игроков (${playerCount}/2)` : '🎮 Начать раздачу';
    } else if (startBtn) {
        startBtn.style.display = 'none';
    }

    const isMyTurn = (event.currentPlayerId === currentPlayerId);
    enableActionButtons(isMyTurn);
    if (isMyTurn) addToLog('🎯 YOUR TURN!');

    // Запрашиваем персональную подсказку если это наш ход
    if (isMyTurn && isWebSocketConnected()) {
        setTimeout(() => sendTipRequest(), 100);
    }
}
function updateGameEndedTip() {
    const tipCard = document.querySelector('.tip-card.current-situation');
    if (!tipCard) return;

    const tipTitle = tipCard.querySelector('.tip-title');
    const tipText = tipCard.querySelector('.tip-text');

    tipTitle.textContent = '🏁 ИГРА ЗАВЕРШЕНА';
    tipText.innerHTML = 'Игрок покинул игру.<br>Недостаточно игроков для продолжения.<br><br>Нажмите "Выйти в лобби", чтобы присоединиться к другой игре.';
}

// ==================== UI ФУНКЦИИ ====================
function confirmNewHand() {
    console.log('Confirming new hand...');

    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket not connected');
        return;
    }

    stompClient.send(`/app/poker/${currentGameId}/confirmNewHand`, {}, JSON.stringify({
        playerId: currentPlayerId
    }));
}
function handleNewHandStarted(event) {
    console.log('New hand started!');
    document.getElementById('handResultModal').style.display = 'none';
    pendingNewHand = false;
    newHandRequesterId = null;
    newHandRequesterName = null;
    updateTips();
}
function handleNewHandRequest(event) {
    console.log('New hand request from:', event.requesterName);
    newHandRequesterId = event.requesterId;
    newHandRequesterName = event.requesterName;
    updateModalButtons();
}
function handleNewHandConfirm(event) {
    console.log('Player confirmed:', event.playerName);
    console.log(`Ready: ${event.readyCount}/${event.totalPlayers}`);

    // Обновляем сообщение в модальном окне, если я запрашивал
    if (newHandRequesterId === currentPlayerId) {
        const footer = document.querySelector('#handResultModal .modal-footer');
        if (footer) {
            footer.innerHTML = `
                <div class="waiting-message">⏳ Ожидание согласия других игроков... (${event.readyCount}/${event.totalPlayers})</div>
                <button onclick="leaveToLobby()" class="btn-leave">🚪 Выйти в лобби</button>
            `;
        }
    }
}

function updatePlayersDisplay(playersList) {
    const container = document.getElementById('playersArea');
    if (!container) return;
    container.innerHTML = '';

    const playersArray = Object.values(playersList);
    const playerCount = playersArray.length;

    console.log('=== UPDATE PLAYERS DISPLAY ===');
    console.log('Player count:', playerCount);
    console.log('Current player ID:', currentPlayerId);

    container.className = `players-area players-${playerCount}`;

    if (playerCount === 2) {
        const opponent = playersArray.find(p => p.playerId !== currentPlayerId);
        const current = playersArray.find(p => p.playerId === currentPlayerId);

        if (opponent) {
            const opponentDiv = createPlayerCardElement(opponent);
            container.appendChild(opponentDiv);
        }
        if (current) {
            const currentDiv = createPlayerCardElement(current);
            currentDiv.classList.add('current-player');
            container.appendChild(currentDiv);
        }
    } else {
        playersArray.forEach(player => {
            const playerDiv = createPlayerCardElement(player);
            if (player.playerId === currentPlayerId) {
                playerDiv.classList.add('current-player');
            }
            container.appendChild(playerDiv);
        });
    }
}

function createPlayerCardElement(player) {
    const div = document.createElement('div');
    div.className = 'player-card';

    const isCurrent = (player.playerId === currentPlayerId);

    if (isCurrent) div.classList.add('active');
    if (player.isDealer) div.classList.add('dealer');
    if (player.isFolded) div.classList.add('folded');
    if (player.isAllIn) div.classList.add('allin');

    let card1 = '??', card2 = '??';
    if (isCurrent) {
        if (player.holeCardsString && player.holeCardsString !== '?? ??') {
            const cards = player.holeCardsString.split(' ');
            if (cards.length >= 2) {
                card1 = cards[0];
                card2 = cards[1];
                console.log(`✅ Cards for ${player.playerName}: ${card1} ${card2}`);
            }
        } else if (player.holeCards && player.holeCards.length >= 2) {
            const firstCard = player.holeCards[0];
            const secondCard = player.holeCards[1];
            if (typeof firstCard === 'object' && firstCard.cardCode) {
                card1 = firstCard.cardCode;
                card2 = secondCard.cardCode;
                console.log(`✅ Cards from holeCards: ${card1} ${card2}`);
            }
        }
    }

    const chips = player.chips || 0;
    const currentBet = player.currentBet || 0;

    const card1Color = getSuitClass(card1);
    const card2Color = getSuitClass(card2);

    div.innerHTML = `
        <div class="player-name">
            ${player.isDealer ? '🎯 ' : ''}${escapeHtml(player.playerName)}${isCurrent ? ' (You)' : ''}
        </div>
        <div class="player-chips">💰 ${chips}</div>
        ${currentBet > 0 ? `<div class="player-bet">Bet: ${currentBet}</div>` : ''}
        <div class="player-cards">
            <div class="mini-card ${card1Color}">${card1}</div>
            <div class="mini-card ${card2Color}">${card2}</div>
        </div>
        ${player.isAllIn ? '<div class="player-status">ALL-IN</div>' : ''}
    `;

    return div;
}

function updateCommunityCards(cards) {
    const container = document.getElementById('communityCards');
    if (!container) {
        console.error('Community cards container not found!');
        return;
    }
    container.innerHTML = '';

    console.log('=== UPDATE COMMUNITY CARDS ===');
    console.log('Received cards:', JSON.stringify(cards));

    if (!cards || cards.length === 0) {
        console.log('No cards, showing 5 backs');
        for (let i = 0; i < 5; i++) {
            const div = document.createElement('div');
            div.className = 'card back';
            div.textContent = '?';
            container.appendChild(div);
        }
        return;
    }

    for (let i = 0; i < 5; i++) {
        const div = document.createElement('div');
        if (i < cards.length) {
            let card = cards[i];
            let cardCode = '?';

            if (typeof card === 'object') {
                cardCode = card.cardCode;
                if (!cardCode && card.rank && card.suitSymbol) {
                    cardCode = card.rank + card.suitSymbol;
                }
                if (!cardCode && card.rank && card.suit) {
                    cardCode = card.rank + card.suit;
                }
                if (!cardCode) {
                    cardCode = card.toString();
                }
            } else if (typeof card === 'string') {
                cardCode = card;
            }

            const colorClass = getSuitClass(cardCode);
            div.className = `card front ${colorClass}`;
            div.textContent = cardCode;
            console.log(`Card ${i}: ${cardCode} (${colorClass})`);
        } else {
            div.className = 'card back';
            div.textContent = '?';
        }
        container.appendChild(div);
    }
}

function enableActionButtons(enabled) {
    const btns = ['foldBtn', 'checkBtn', 'callBtn', 'raiseBtn', 'allinBtn'];
    btns.forEach(id => {
        const btn = document.getElementById(id);
        if (btn) btn.disabled = !enabled;
    });
    const raise = document.getElementById('raiseAmount');
    if (raise) raise.disabled = !enabled;
}

function sendAction(action) {
    if (!isWebSocketConnected()) {
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
    sendActionWs(action, betAmount);

    if (action === 'raise') {
        document.getElementById('raiseAmount').value = '';
    }
}

function sendChat() {
    if (!isWebSocketConnected()) {
        alert('Not connected');
        return;
    }
    const input = document.getElementById('chatInput');
    const msg = input.value.trim();
    if (msg) {
        sendChatMessageWs(msg);
        input.value = '';
    }
}

function handleChatKeyPress(e) {
    if (e.key === 'Enter') sendChat();
}

function addToLog(msg) {
    const log = document.getElementById('gameLog');
    if (!log) return;
    const entry = document.createElement('div');
    entry.className = 'game-log-entry';
    entry.textContent = `[${new Date().toLocaleTimeString()}] ${msg}`;
    log.appendChild(entry);
    log.scrollTop = log.scrollHeight;
}

function addChatMessage(sender, msg, isSystem = false) {
    const chat = document.getElementById('chatMessages');
    if (!chat) return;
    const div = document.createElement('div');
    div.className = 'chat-message' + (isSystem ? ' system' : '');
    div.innerHTML = isSystem ? `🔔 ${escapeHtml(msg)}` : `<strong>${escapeHtml(sender)}:</strong> ${escapeHtml(msg)}`;
    chat.appendChild(div);
    chat.scrollTop = chat.scrollHeight;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ==================== ПАНЕЛЬ ПОДСКАЗОК ====================

function toggleTipsPanel() {
    document.getElementById('tipsPanel').classList.toggle('open');
}

function showRules() {
    document.getElementById('rulesModal').style.display = 'flex';
}

function closeRules() {
    document.getElementById('rulesModal').style.display = 'none';
}

window.onclick = function(e) {
    const modal = document.getElementById('rulesModal');
    if (e.target === modal) modal.style.display = 'none';
}

window.addEventListener('beforeunload', function() {
    if (gamesRefreshInterval) clearInterval(gamesRefreshInterval);
    if (stompClient) disconnectWebSocket();
});
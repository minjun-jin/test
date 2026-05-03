// ==UserScript==
// @name         EnterJoy Point Clock
// @namespace    http://tampermonkey.net/
// @updateURL    https://raw.githubusercontent.com/minjun-jin/test/refs/heads/main/tampermonkey_enterjoy_point_clock_v121.user.js
// @downloadURL  https://raw.githubusercontent.com/minjun-jin/test/refs/heads/main/tampermonkey_enterjoy_point_clock_v121.user.js
// @version      1.21
// @description  Displays a point clock with target-time alarms
// @author       AnonymousEnjo
// @match        *://enterjoy.day/*
// @icon         https://www.google.com/s2/favicons?sz=64&domain=enterjoy.day
// @grant        GM_setValue
// @grant        GM_getValue
// @run-at       document-end
// ==/UserScript==

(function () {
    'use strict';

    // Point links array
    const POINT_LINKS = [
        'https://enterjoy.day/bbs/board.php?bo_table=point&wr_id=5',
        'https://enterjoy.day/bbs/board.php?bo_table=point&wr_id=11426'
    ];

    // Debug flags
    const SHOW_DEBUG = false;
    const SHOW_BORDER = false;

    // Compact mode configuration
    const COMPACT_MODE = false;
    const STEP_HEIGHT_COMPACT = 25;

    // Initialization
    const audioContext = new (window.AudioContext || window.webkitAudioContext)();
    const channelName = 'synchronized-pclock-channel';

    // Dynamic state for hasClock and hasTimer
    let hasClock = document.querySelector('#tampermonkey-synced-clock') !== null;
    let hasTimer = document.querySelector('#tampermonkey-synced-timer') !== null;

    // Prevent execution inside iframes
    if (window.self !== window.top) return;

    // Calculate base bottom position depending on hasClock/hasTimer
    function getBaseBottom() {
        return (hasClock && hasTimer) ? 80 : (hasClock || hasTimer) ? 45 : 10;
    }
    let BASE_BOTTOM = getBaseBottom();
    const STEP_HEIGHT = 35;

    // Function to get step height depending on compact mode
    function getStepHeight() {
        return COMPACT_MODE ? STEP_HEIGHT_COMPACT : STEP_HEIGHT;
    }

    // BroadcastChannel setup
    const syncChannel = new BroadcastChannel(channelName);
    syncChannel.onmessage = (event) => {
        if (event.data?.type === 'ej_point_update') {
            const wr_id = event.data.wr_id;
            const newTime = event.data.targetTime;
            const timerObj = timers.find(t => t.wr_id === wr_id);

            if (newTime) {
                // normal update
                GM_setValue(`ej_point_target_time_${wr_id}`, newTime);
                if (timerObj) {
                    startCountdown(timerObj, newTime);
                    if (timerObj.debugTimeLabel) {
                        timerObj.debugTimeLabel.textContent = newTime;
                    }
                }
            } else {
                // treat as delete
                GM_setValue(`ej_point_target_time_${wr_id}`, '');
                if (timerObj) {
                    clearInterval(timerObj.countdownInterval);
                    timerObj.clockDisplay.textContent = '--:--:--';
                    timerObj.clockDisplay.style.background = 'rgba(0, 0, 0, 1)';
                    if (timerObj.debugTimeLabel) {
                        timerObj.debugTimeLabel.textContent = '';
                    }
                }
            }
        }
    };

    // Timer objects array
    const timers = [];

    // MutationObserver to detect changes in DOM
    const observer = new MutationObserver(() => {
        hasClock = document.querySelector('#tampermonkey-synced-clock') !== null;
        hasTimer = document.querySelector('#tampermonkey-synced-timer') !== null;
        BASE_BOTTOM = getBaseBottom();

        // Reposition all timers
        timers.forEach((timerObj, index) => {
            if (COMPACT_MODE) {
                // Use row calculation in compact mode
                const row = Math.floor(index / 2);
                const newBottom = BASE_BOTTOM + (row * STEP_HEIGHT_COMPACT);
                const rowContainer = document.querySelector(`#row-container-${row}`);
                if (rowContainer) {
                    rowContainer.style.bottom = `${newBottom}px`;
                }
            } else {
                // Normal mode: index-based positioning
                const newBottom = BASE_BOTTOM + (index * STEP_HEIGHT);
                timerObj.clockDisplay.style.bottom = `${newBottom}px`;

                const debugContainer = document.querySelector(`#debug-container-${timerObj.wr_id}`);
                if (debugContainer) {
                    debugContainer.style.bottom = `${newBottom}px`;
                }
            }
        });
    });
    observer.observe(document.body, { childList: true, subtree: true });

    // Function to apply clock styles
    function applyClockStyles(element, bottomValue) {
        element.style.position = COMPACT_MODE ? 'relative' : 'fixed';
        if (!COMPACT_MODE) {
            element.style.bottom = `${bottomValue}px`;
            element.style.right = '10px';
        }
        element.style.fontFamily = 'Arial, sans-serif';
        element.style.fontSize = COMPACT_MODE ? '12px' : '18px';
        element.style.color = '#FFFFFF';
        element.style.backgroundColor = 'rgba(0, 0, 0, 1)';
        if (SHOW_BORDER) {
            element.style.border = '1px solid rgba(255, 255, 255, 1)';
        }
        element.style.padding = '5px 8px';
        element.style.borderRadius = '5px';
        element.style.zIndex = '9999';
        element.style.width = COMPACT_MODE ? '57.5px' : '120px';
        element.style.height = COMPACT_MODE ? '20px' : '30px';
        element.style.boxSizing = 'border-box';
        element.style.display = 'flex';
        element.style.justifyContent = 'center';
        element.style.alignItems = 'center';
        element.style.textAlign = 'center';
    }

    // Function to apply link wrapper styles
    function applyLinkWrapperStyles(element, bottomValue) {
        element.style.textDecoration = 'none';
        element.style.display = 'inline-block';
        element.style.position = 'fixed';
        element.style.bottom = `${bottomValue}px`;
        element.style.right = '10px';
        element.style.zIndex = '9999';
    }

    // Function to get or create row container for compact mode
    function getOrCreateRowContainer(row, bottomValue) {
        let rowContainer = document.querySelector(`#row-container-${row}`);
        if (!rowContainer) {
            rowContainer = document.createElement('div');
            rowContainer.id = `row-container-${row}`;
            rowContainer.style.position = 'fixed';
            rowContainer.style.bottom = `${bottomValue}px`;
            rowContainer.style.right = '10px';
            rowContainer.style.display = 'flex';
            rowContainer.style.flexDirection = 'row-reverse';
            rowContainer.style.alignItems = 'center'; // ensure same line alignment
            rowContainer.style.gap = '5px';
            rowContainer.style.zIndex = '9999';

            // Create debug group and timer group
            const debugGroup = document.createElement('div');
            debugGroup.className = 'debug-group';
            debugGroup.style.display = 'flex';
            debugGroup.style.flexDirection = 'row';
            debugGroup.style.alignItems = 'center';
            debugGroup.style.gap = '5px';

            const timerGroup = document.createElement('div');
            timerGroup.className = 'timer-group';
            timerGroup.style.display = 'flex';
            timerGroup.style.flexDirection = 'row';
            timerGroup.style.alignItems = 'center';
            timerGroup.style.gap = '5px';

            rowContainer.appendChild(timerGroup);
            rowContainer.appendChild(debugGroup);

            document.body.appendChild(rowContainer);
        }
        return rowContainer;
    }

    // Function to play beep
    function playBeep(frequency, duration, volume) {
        if (audioContext.state === 'suspended') {
            audioContext.resume();
        }
        const oscillator = audioContext.createOscillator();
        const gainNode = audioContext.createGain();
        oscillator.connect(gainNode);
        gainNode.connect(audioContext.destination);
        oscillator.type = 'sine';
        oscillator.frequency.setValueAtTime(frequency, audioContext.currentTime);
        gainNode.gain.setValueAtTime(volume, audioContext.currentTime);
        gainNode.gain.exponentialRampToValueAtTime(0.001, audioContext.currentTime + duration / 1000);
        oscillator.start(audioContext.currentTime);
        oscillator.stop(audioContext.currentTime + duration / 1000);
    }

    // Function to play beep low
    function playBeepLow(frequency = 880, duration = 200, volume = 0.5) {
        playBeep(frequency, duration, volume);
    }

    // Function to play beep high
    function playBeepHigh(frequency = 880, duration = 1500, volume = 0.8) {
        playBeep(frequency, duration, volume);
    }

    // Countdown alarm logic (independent per timer)
    function checkAndTriggerCountdownAlarms(timerObj, diff) {
        const millisecondsLeft = diff;
        const secondsLeft = millisecondsLeft / 1000;

        // Pre-alarm
        const isBeepPreTime = (Math.floor(secondsLeft) <= 60 && (Math.floor(secondsLeft) > 30));
        if (isBeepPreTime) {
            if (!timerObj.beepPreTriggered) {
                timerObj.beepPreTriggered = true;
            }
            timerObj.clockDisplay.style.background = (Math.floor(secondsLeft) % 2 === 0)
                ? 'rgba(255, 0, 0, 1)'
                : 'rgba(0, 0, 0, 1)';
        } else {
            timerObj.beepPreTriggered = false;
        }

        // Low alarm
        const isBeepLowTime = Math.floor(secondsLeft) <= 30 && Math.floor(secondsLeft) > 0;
        if (isBeepLowTime) {
            if (Math.floor(secondsLeft) % 2 === 0) {
                if (!timerObj.beepLowTriggered) {
                    playBeepLow();
                    timerObj.beepLowTriggered = true;
                }
            } else {
                timerObj.beepLowTriggered = false;
            }
            timerObj.clockDisplay.style.background = (Math.floor(secondsLeft) % 2 === 0)
                ? 'rgba(255, 0, 0, 1)'
                : 'rgba(0, 0, 0, 1)';
        } else {
            timerObj.beepLowTriggered = false;
        }

        // High alarm
        const isBeepHighTime = Math.floor(secondsLeft) <= 0;
        if (isBeepHighTime) {
            if (!timerObj.beepHighTriggered) {
                timerObj.beepHighTriggered = true;
                playBeepHigh();
            }
            timerObj.clockDisplay.style.background = 'rgba(255, 0, 0, 1)';
            return;
        } else {
            timerObj.beepHighTriggered = false;
        }

        // Reset background if not in alarm range
        if (Math.floor(secondsLeft) > 60) {
            timerObj.clockDisplay.style.background = 'rgba(0, 0, 0, 1)';
        }
    }

    // Function to update countdown
    function updateCountdown(timerObj, targetTime) {
        const now = new Date();
        const diff = new Date(targetTime) - now;

        // If current tab is last visible tab (exclusive) or null/undefined (global)
        const lastVisibleTab = GM_getValue("lastVisibleTab");
        if (lastVisibleTab === tabId || lastVisibleTab === null || lastVisibleTab === undefined) {
            checkAndTriggerCountdownAlarms(timerObj, diff);
        }

        if (diff <= 0) {
            timerObj.clockDisplay.textContent = '00:00:00';
            return false; // countdown finished
        } else {
            const hours = Math.floor(diff / 3600000).toString().padStart(2, '0');
            const minutes = Math.floor((diff % 3600000) / 60000).toString().padStart(2, '0');
            const seconds = Math.floor((diff % 60000) / 1000).toString().padStart(2, '0');
            timerObj.clockDisplay.textContent = `${hours}:${minutes}:${seconds}`;
            return true; // countdown continues
        }
    }

    // Function to start countdown
    function startCountdown(timerObj, targetTime) {
        clearInterval(timerObj.countdownInterval);

        // Initial update
        updateCountdown(timerObj, targetTime);

        // Periodic update
        timerObj.countdownInterval = setInterval(() => {
            const stillRunning = updateCountdown(timerObj, targetTime);
            if (!stillRunning) {
                clearInterval(timerObj.countdownInterval);
            }
        }, 200);
    }

    // Function to extract target time from text
    function extractTargetTimeFromText(text) {
        if (text.includes('다음 가능:')) {
            const match = text.match(/다음 가능:\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})/);
            if (match) {
                return match[1]; // Return matched time string
            }
        }
        // Fallback: use current time + 1 day in "YYYY-MM-DD HH:MM:SS" format
        const now = new Date();
        now.setDate(now.getDate() + 1);
        const year = now.getFullYear();
        const month = (now.getMonth() + 1).toString().padStart(2, '0');
        const day = now.getDate().toString().padStart(2, '0');
        const hours = now.getHours().toString().padStart(2, '0');
        const minutes = now.getMinutes().toString().padStart(2, '0');
        const seconds = now.getSeconds().toString().padStart(2, '0');
        return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
    }

    // Function to create clock display
    function createClockDisplay(wr_id, index, link) {
        const row = COMPACT_MODE ? Math.floor(index / 2) : index;
        const bottomValue = BASE_BOTTOM + (row * getStepHeight());
        const rowContainer = COMPACT_MODE ? getOrCreateRowContainer(row, bottomValue) : null;

        const linkWrapper = document.createElement('a');
        linkWrapper.href = link;
        linkWrapper.target = '_blank';
        if (!COMPACT_MODE) {
            applyLinkWrapperStyles(linkWrapper, bottomValue);
        }

        const clockDisplay = document.createElement('div');
        clockDisplay.id = `tampermonkey-synced-pclock-${wr_id}`;
        applyClockStyles(clockDisplay, bottomValue);

        linkWrapper.appendChild(clockDisplay);

        if (COMPACT_MODE) {
            const timerGroup = rowContainer.querySelector('.timer-group');
            timerGroup.appendChild(linkWrapper);
        } else {
            document.body.appendChild(linkWrapper);
        }

        // Timer object
        const timerObj = {
            wr_id,
            clockDisplay,
            countdownInterval: null,
            beepPreTriggered: false,
            beepLowTriggered: false,
            beepHighTriggered: false,
            debugTimeLabel: null
        };
        timers.push(timerObj);

        if (SHOW_DEBUG) {
            if (COMPACT_MODE) {
                createDebugUI(timerObj, rowContainer);
            } else {
                createDebugUI(timerObj, bottomValue);
            }
        }

        return timerObj;
    }

    // Function to create debug UI
    function createDebugUI(timerObj, rowContainerOrBottom) {
        let debugContainer = document.createElement('div');
        debugContainer.id = `debug-container-${timerObj.wr_id}`;
        debugContainer.style.display = 'flex';
        debugContainer.style.flexDirection = 'row-reverse';
        debugContainer.style.gap = '5px';
        debugContainer.style.alignItems = 'center';

        if (COMPACT_MODE) {
            const debugGroup = rowContainerOrBottom.querySelector('.debug-group');
            debugGroup.appendChild(debugContainer);
        } else {
            debugContainer.style.position = 'fixed';
            debugContainer.style.bottom = `${rowContainerOrBottom}px`;
            debugContainer.style.right = '135px';
            document.body.appendChild(debugContainer);
        }

        // Delete button
        const deleteButton = document.createElement('button');
        deleteButton.textContent = '🗑️';
        deleteButton.title = 'Delete saved target time';
        deleteButton.style.fontSize = COMPACT_MODE ? '11px' : '14px';
        deleteButton.style.padding = COMPACT_MODE ? 'none' : '2px 6px';
        deleteButton.style.height = COMPACT_MODE ? '20px' : '30px';
        deleteButton.style.cursor = 'pointer';
        deleteButton.onclick = () => {
            GM_setValue(`ej_point_target_time_${timerObj.wr_id}`, '');
            timerObj.clockDisplay.textContent = '--:--:--';
            timerObj.clockDisplay.style.background = 'rgba(0, 0, 0, 1)';
            debugTimeLabel.textContent = '';
            clearInterval(timerObj.countdownInterval);
            // Sync deletion across tabs
            syncChannel.postMessage({ type: 'ej_point_update', wr_id: timerObj.wr_id, targetTime: '' });
        };

        // Input button
        const inputButton = document.createElement('button');
        inputButton.textContent = '⏱️';
        inputButton.title = 'Manually set target time';
        inputButton.style.fontSize = COMPACT_MODE ? '11px' : '14px';
        inputButton.style.padding = COMPACT_MODE ? 'none' : '2px 6px';
        inputButton.style.height = COMPACT_MODE ? '20px' : '30px';
        inputButton.style.cursor = 'pointer';
        inputButton.onclick = () => {
            const currentSaved = GM_getValue(`ej_point_target_time_${timerObj.wr_id}`) || '';
            const input = prompt('Enter target time (YYYY-MM-DD HH:MM:SS)', currentSaved);
            if (input && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(input)) {
                GM_setValue(`ej_point_target_time_${timerObj.wr_id}`, input);
                syncChannel.postMessage({ type: 'ej_point_update', wr_id: timerObj.wr_id, targetTime: input });
                debugTimeLabel.textContent = input;
                startCountdown(timerObj, input);
            } else if (input) {
                alert('Invalid format. Use YYYY-MM-DD HH:MM:SS');
            }
        };

        // Debug time label
        const debugTimeLabel = document.createElement('div');
        debugTimeLabel.style.fontSize = '11px';
        debugTimeLabel.style.color = '#ccc';
        debugTimeLabel.style.fontFamily = 'Arial, sans-serif';
        debugTimeLabel.style.backgroundColor = 'rgba(0, 0, 0, 1)';
        debugTimeLabel.style.padding = '2px 6px';
        debugTimeLabel.style.borderRadius = '3px';
        debugTimeLabel.style.width = '120px';
        debugTimeLabel.style.height = '20px';
        debugTimeLabel.style.boxSizing = 'border-box';
        debugTimeLabel.style.display = 'flex';
        debugTimeLabel.style.justifyContent = 'center';
        debugTimeLabel.style.alignItems = 'center';
        debugTimeLabel.style.textAlign = 'center';
        const savedTime = GM_getValue(`ej_point_target_time_${timerObj.wr_id}`);
        if (savedTime) {
            debugTimeLabel.textContent = savedTime;
        }

        timerObj.debugTimeLabel = debugTimeLabel;

        debugContainer.appendChild(deleteButton);
        debugContainer.appendChild(inputButton);
        debugContainer.appendChild(debugTimeLabel);
    }

    // Function to initialize countdown if saved
    function initializeCountdownIfSaved(timerObj) {
        const saved = GM_getValue(`ej_point_target_time_${timerObj.wr_id}`);
        if (saved && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(saved)) {
            const now = new Date();
            const target = new Date(saved);
            const diff = target - now;
            if (diff > 0) {
                startCountdown(timerObj, saved);
            } else {
                timerObj.clockDisplay.textContent = '00:00:00';
                timerObj.clockDisplay.style.background = 'rgba(255, 0, 0, 1)';
            }
        } else {
            timerObj.clockDisplay.textContent = '--:--:--';
            timerObj.clockDisplay.style.background = 'rgba(0, 0, 0, 1)';
        }
    }

    // Function to observe reward div creation
    function observeRewardDiv(timerObj) {
        const observer = new MutationObserver((mutations) => {
            for (const mutation of mutations) {
                for (const node of mutation.addedNodes) {
                    if (node.nodeType === 1 && node.id && node.id.startsWith('ej_reward_')) {
                        const text = node.textContent;
                        const targetTime = extractTargetTimeFromText(text);
                        if (targetTime) {
                            GM_setValue(`ej_point_target_time_${timerObj.wr_id}`, targetTime);
                            // Sync update across tabs
                            syncChannel.postMessage({ type: 'ej_point_update', wr_id: timerObj.wr_id, targetTime });
                            if (timerObj.debugTimeLabel) {
                                timerObj.debugTimeLabel.textContent = targetTime;
                            }
                            startCountdown(timerObj, targetTime);
                        }
                    }
                }
            }
        });
        observer.observe(document.body, { childList: true, subtree: true });
    }

    // Generate unique tab id
    const tabId = window.location.href;

    // Initial visibility check
    if (document.visibilityState === "visible") {
        GM_setValue("lastVisibleTab", tabId);
    }

    // Listen for visibility changes
    document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "visible") {
            // Record this tab as last visible
            GM_setValue("lastVisibleTab", tabId);
        }
    });

    // Handle tab close event
    window.addEventListener("beforeunload", () => {
        // If this tab was the last visible, reset to null
        if (GM_getValue("lastVisibleTab") === tabId) {
            GM_setValue("lastVisibleTab", null);
        }
    });

    // Kickstart process: create timers for each POINT_LINKS
    POINT_LINKS.forEach((link, index) => {
        const wr_idMatch = link.match(/wr_id=(\d+)/);
        if (!wr_idMatch) return;
        const wr_id = wr_idMatch[1];
        const timerObj = createClockDisplay(wr_id, index, link);
        initializeCountdownIfSaved(timerObj);

        if (location.href.includes('bo_table=point') && location.href.includes(`wr_id=${wr_id}`)) {

            // Observe reward div for updates
            document.body.addEventListener('click', (e) => {
                const target = e.target.closest('a.reward-link');
                if (target && target.querySelector('.ej-reward-text')) {
                    observeRewardDiv(timerObj);
                }
            });
        }
    });
})();

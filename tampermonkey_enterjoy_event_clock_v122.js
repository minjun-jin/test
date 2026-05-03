// ==UserScript==
// @name         EnterJoy Event Clock
// @namespace    http://tampermonkey.net/
// @version      1.22
// @description  Displays an event clock with periodic alarms
// @author       AnonymousEnjo
// @match        *://enterjoy.day/*
// @icon         https://www.google.com/s2/favicons?sz=64&domain=enterjoy.day
// @grant        GM_setValue
// @grant        GM_getValue
// @run-at       document-end
// ==/UserScript==

(function () {
    'use strict';

    // Log switcher
    let consoleLogs = false;

    if (!consoleLogs) {
        console.log = function() {};
    }

    // Link URL
    const LINK_URL = 'https://enterjoy.day/bbs/board.php?bo_table=free1';

    // Debug flags
    const USE_24HCLOCK = false;
    const SHOW_BORDER = false;

    // Initialization
    const audioContext = new (window.AudioContext || window.webkitAudioContext)();
    const clockDisplayId = 'tampermonkey-synced-clock';
    let clockDisplay;
    let beepPreTriggered = false;
    let beepLowTriggered = false;
    let beepHighTriggered = false;
    let autoplayAllowed = null;

    // Prevent execution inside iframes
    if (window.self !== window.top) return;

    // Function to apply clock styles
    function applyClockStyles(element) {
        element.style.position = 'fixed';
        element.style.bottom = '10px';
        element.style.right = '10px';
        element.style.fontFamily = 'Arial, sans-serif';
        element.style.fontSize = '18px';
        element.style.color = '#FFFFFF';
        element.style.backgroundColor = 'rgba(0, 0, 0, 1)';
        if (SHOW_BORDER) {
            element.style.border = '1px solid rgba(255, 255, 255, 1)';
        }
        element.style.padding = '5px 8px';
        element.style.borderRadius = '5px';
        element.style.zIndex = '9999';
        element.style.width = '120px';
        element.style.height = '30px';
        element.style.boxSizing = 'border-box';
        element.style.display = 'flex';
        element.style.justifyContent = 'center';
        element.style.alignItems = 'center';
        element.style.textAlign = 'center';
    }

    // Function to apply link wrapper styles
    function applyLinkWrapperStyles(element) {
        element.style.textDecoration = 'none';
        element.style.display = 'inline-block';
        element.style.position = 'fixed';
        element.style.bottom = '10px';
        element.style.right = '10px';
        element.style.zIndex = '9999';
    }

    // Function to create clock display
    function createClockDisplay() {

        const linkWrapper = document.createElement('a');
        linkWrapper.href = LINK_URL;
        linkWrapper.target = '_blank';
        applyLinkWrapperStyles(linkWrapper);

        clockDisplay = document.createElement('div');
        clockDisplay.id = clockDisplayId;
        applyClockStyles(clockDisplay);

        linkWrapper.appendChild(clockDisplay);
        document.body.appendChild(linkWrapper);

        // Immediately update the clock once
        updateClock(Date.now());
    }

    // Update the displayed time
    function updateClock(timestamp) {
        if (!clockDisplay) {
            createClockDisplay();
        }
        const date = new Date(timestamp);
        let hours = date.getHours();
        const minutes = date.getMinutes().toString().padStart(2, '0');
        const seconds = date.getSeconds().toString().padStart(2, '0');

        if (USE_24HCLOCK) {
            // Display in 24-hour format
            const hours24 = hours.toString().padStart(2, '0');
            clockDisplay.textContent = `${hours24}:${minutes}:${seconds}`;
        } else {
            // Display in 12-hour format
            const meridiem = hours >= 12 ? 'PM' : 'AM';
            hours = hours % 12;
            hours = hours ? hours : 12; /* The hour '0' should be '12' for midnight */
            clockDisplay.textContent = `${hours}:${minutes}:${seconds} ${meridiem}`;
        }
    }

    // Function to test autoplay permission
    function testAutoplay() {
        const audioUrl = "data:audio/mpeg;base64,SUQzBAAAAAAAI1RTU0UAAAAPAAADTGF2ZjYwLjE2LjEwMAAAAAAAAAAAAAAA/+M4wAAAAAAAAAAAAEluZm8AAAAPAAAAEAAABVgANTU1NTU1Q0NDQ0NDUFBQUFBQXl5eXl5ea2tra2tra3l5eXl5eYaGhoaGhpSUlJSUlKGhoaGhoaGvr6+vr6+8vLy8vLzKysrKysrX19fX19fX5eXl5eXl8vLy8vLy////////AAAAAExhdmM2MC4zMQAAAAAAAAAAAAAAACQCgAAAAAAAAAVYCAAeqQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/+MYxAAAAANIAAAAAExBTUUzLjEwMFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVMQU1FMy4xMDBVVVVVVVVVVVVV";
        const testAudio = new Audio(audioUrl);
        return testAudio.play().then(() => true).catch(() => false);
    }

    // Function to attach user gesture listener for recheck autoplay permission
    function attachUserGestureListener() {
        document.addEventListener("click", () => {
            if (autoplayAllowed === false) {
                testAutoplay().then(result => {
                    if (result) {
                        // Autoplay allowed after user gesture -> set current tab as last visible
                        console.log("[APD] 🔄 Autoplay allowed after user gesture");
                        GM_setValue("lastVisibleTab", tabId);
                        autoplayAllowed = true;
                    } else {
                        // Still blocked after user gesture -> reset last visible to null
                        console.log("[APD] ❌ Still failed after user gesture");
                        GM_setValue("lastVisibleTab", null);
                    }
                });
            }
        }, { once: true }); // Detect only the first click
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

    // Function to play beep low (Default A4 note)
    function playBeepLow(frequency = 440, duration = 200, volume = 0.5) {
        playBeep(frequency, duration, volume);
    }

    // Function to play beep high
    function playBeepHigh(frequency = 880, duration = 1500, volume = 0.5) {
        playBeep(frequency, duration, volume);
    }

    // Clock alarm Logic
    function checkAndTriggerAlarms(timestamp) {
        const date = new Date(timestamp);
        const minutes = date.getMinutes();
        const seconds = date.getSeconds();

       // Pre-alarm
        const isBeepPreTime = minutes == 29 || minutes == 59;
        if (isBeepPreTime) {
            if (!beepPreTriggered) {
                beepPreTriggered = true;
            }
            clockDisplay.style.background = (seconds % 2 === 0)
                ? 'rgba(255, 0, 0, 1)'
                : 'rgba(0, 0, 0, 1)';
        } else {
            beepPreTriggered = false;
        }

        // Low alarm
        const isBeepLowTime = (seconds >= 30 && (minutes == 29 || minutes == 59));
        if (isBeepLowTime) {
            if (seconds % 2 === 0) {
                if (!beepLowTriggered) {
                    playBeepLow();
                    beepLowTriggered = true;
                }
            } else {
                beepLowTriggered = false;
            }
            clockDisplay.style.background = (seconds % 2 === 0)
                ? 'rgba(255, 0, 0, 1)'
                : 'rgba(0, 0, 0, 1)';
        } else {
            beepLowTriggered = false;
        }

        // High alarm
        const isBeepHighTime = (seconds == 0 && (minutes == 0 || minutes == 30));
        if (isBeepHighTime) {
            if (!beepHighTriggered) {
                beepHighTriggered = true;
                playBeepHigh();
            }
            clockDisplay.style.background = 'rgba(255, 0, 0, 1)';
            return;
        } else {
            beepHighTriggered = false;
        }

        // Reset background if not in alarm range
        if (
            (minutes !== 29 && minutes !== 59) ||
            ((minutes === 0 || minutes === 30) && seconds !== 0)
        ) {
            clockDisplay.style.background = 'rgba(0, 0, 0, 1)';
        }
    }

    // Kickstart the process
    createClockDisplay();

    // Generate unique tab id
    const tabId = window.location.href;

    // Initial visibility and autoplay permission check
    if (document.visibilityState === "visible") {
        testAutoplay().then(result => {
            autoplayAllowed = result;
            if (result) {
                // Autoplay allowed -> set current tab as last visible
                console.log("[APD] ✅ Autoplay allowed");
                GM_setValue("lastVisibleTab", tabId);
            } else {
                // Autoplay blocked -> reset last visible to null
                console.log("[APD] ❌ Autoplay blocked");
                GM_setValue("lastVisibleTab", null);
                attachUserGestureListener(); // Attach listener for user gesture recheck
            }
        });
    }

    // Listen for visibility changes
    document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "visible") {
            testAutoplay().then(result => {
                autoplayAllowed = result;
                if (result) {
                    // Autoplay allowed -> set current tab as last visible
                    GM_setValue("lastVisibleTab", tabId);
                } else {
                    // Autoplay blocked -> reset last visible to null
                    GM_setValue("lastVisibleTab", null);
                    attachUserGestureListener(); // Attach listener for user gesture recheck
                }
            });
        }
    });

    // Handle tab close event
    window.addEventListener("beforeunload", () => {
        // If this tab was the last visible, reset to null
        if (GM_getValue("lastVisibleTab") === tabId) {
            GM_setValue("lastVisibleTab", null);
        }
    });

    // Periodically update local clock and trigger alarms
    setInterval(() => {
        const timestamp = Date.now();
        updateClock(timestamp);

        // If current tab is last visible tab (exclusive) or null/undefined (global)
        const lastVisibleTab = GM_getValue("lastVisibleTab");
        if (lastVisibleTab === tabId || lastVisibleTab === null || lastVisibleTab === undefined) {
            checkAndTriggerAlarms(timestamp);
        }
    }, 200);
})();
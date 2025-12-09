// AI Chatbot JavaScript - Enhanced Version

const chatMessages = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const typingIndicator = document.getElementById('typingIndicator');
const voiceBtn = document.getElementById('voiceBtn');

// Chat history management
let chatHistory = [];
const CHAT_HISTORY_KEY = 'ai_chat_history';
const MAX_HISTORY_ITEMS = 50;

// Voice recognition
let recognition = null;
let isListening = false;

// Initialize on load
document.addEventListener('DOMContentLoaded', function() {
    loadChatHistory();
    initVoiceRecognition();
    messageInput.focus();
});

// ============= VOICE INPUT =============
function initVoiceRecognition() {
    if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        recognition = new SpeechRecognition();
        recognition.lang = 'vi-VN';
        recognition.continuous = false;
        recognition.interimResults = false;

        recognition.onresult = function(event) {
            const transcript = event.results[0][0].transcript;
            messageInput.value = transcript;
            stopVoiceInput();
        };

        recognition.onerror = function(event) {
            console.error('Speech recognition error:', event.error);
            stopVoiceInput();
            showToast('Lỗi nhận diện giọng nói', 'error');
        };

        recognition.onend = function() {
            stopVoiceInput();
        };
    } else {
        // Hide voice button if not supported
        if (voiceBtn) {
            voiceBtn.style.display = 'none';
        }
    }
}

function toggleVoiceInput() {
    if (!recognition) {
        showToast('Trình duyệt không hỗ trợ nhận diện giọng nói', 'warning');
        return;
    }

    if (isListening) {
        stopVoiceInput();
    } else {
        startVoiceInput();
    }
}

function startVoiceInput() {
    if (!recognition) return;

    isListening = true;
    voiceBtn.classList.add('active');
    recognition.start();
    showToast('Đang lắng nghe...', 'info');
}

function stopVoiceInput() {
    if (!recognition) return;

    isListening = false;
    voiceBtn.classList.remove('active');
    if (recognition) {
        recognition.stop();
    }
}

// ============= CHAT HISTORY =============
function loadChatHistory() {
    try {
        const saved = localStorage.getItem(CHAT_HISTORY_KEY);
        if (saved) {
            chatHistory = JSON.parse(saved);
            // Restore messages to UI (optional)
            // chatHistory.forEach(item => {
            //     addMessage(item.message, item.type, false);
            // });
        }
    } catch (error) {
        console.error('Error loading chat history:', error);
    }
}

function saveChatHistory() {
    try {
        // Keep only last MAX_HISTORY_ITEMS
        if (chatHistory.length > MAX_HISTORY_ITEMS) {
            chatHistory = chatHistory.slice(-MAX_HISTORY_ITEMS);
        }
        localStorage.setItem(CHAT_HISTORY_KEY, JSON.stringify(chatHistory));
    } catch (error) {
        console.error('Error saving chat history:', error);
    }
}

function clearChat() {
    if (confirm('Bạn có chắc muốn xóa toàn bộ lịch sử chat?')) {
        chatMessages.innerHTML = '';
        chatHistory = [];
        localStorage.removeItem(CHAT_HISTORY_KEY);

        // Add welcome message back
        addWelcomeMessage();
        showToast('Đã xóa lịch sử chat', 'success');
    }
}

function addWelcomeMessage() {
    const welcomeDiv = document.createElement('div');
    welcomeDiv.className = 'message ai';
    welcomeDiv.innerHTML = `
        <div class="message-icon">
            <i class="fas fa-robot"></i>
        </div>
        <div>
            <div class="message-content">
                <strong>Xin chào! 👋</strong><br><br>
                Tôi là <strong>Trợ lý AI thời trang</strong> của cửa hàng. Tôi có thể giúp bạn:<br><br>
                <div style="display: grid; gap: 8px;">
                    <div>✨ <strong>Tìm kiếm sản phẩm</strong> phù hợp với phong cách của bạn</div>
                    <div>💰 <strong>Tư vấn về giá cả</strong> và chất lượng</div>
                    <div>👔 <strong>Gợi ý cách phối đồ</strong> cho nhiều dịp khác nhau</div>
                    <div>📏 <strong>Hướng dẫn chọn size</strong> chính xác</div>
                    <div>🎁 <strong>Thông tin khuyến mãi</strong> mới nhất</div>
                </div>
                <br>
                <em>Hãy hỏi tôi bất cứ điều gì bạn cần! 😊</em>
            </div>
            <div class="message-time">Vừa xong</div>
        </div>
    `;
    chatMessages.appendChild(welcomeDiv);
}

// ============= MESSAGING =============
function sendQuickMessage(message) {
    messageInput.value = message;
    sendMessage();
}

async function sendMessage() {
    const message = messageInput.value.trim();

    if (!message) {
        messageInput.focus();
        return;
    }

    // Add user message to UI
    addMessage(message, 'user');

    // Add to history
    chatHistory.push({ message, type: 'user', timestamp: new Date().toISOString() });
    saveChatHistory();

    // Clear input
    messageInput.value = '';
    messageInput.focus();

    // Show typing indicator
    typingIndicator.classList.add('active');

    try {
        // Call API
        const response = await fetch('/api/ai/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(message)
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        const data = await response.json();

        // Add AI response
        addMessage(data.response || data, 'ai');

        // Add to history
        chatHistory.push({
            message: data.response || data,
            type: 'ai',
            timestamp: new Date().toISOString()
        });
        saveChatHistory();

    } catch (error) {
        console.error('Error:', error);
        const errorMsg = 'Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại sau. 😔';
        addMessage(errorMsg, 'ai');
        showToast('Lỗi kết nối đến AI', 'error');
    } finally {
        typingIndicator.classList.remove('active');
    }
}

function addMessage(text, type, saveToHistory = false) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${type}`;

    const time = getCurrentTime();
    const messageId = 'msg_' + Date.now();

    if (type === 'ai') {
        messageDiv.innerHTML = `
            <div class="message-icon">
                <i class="fas fa-robot"></i>
            </div>
            <div>
                <div class="message-content" id="${messageId}">
                    ${formatMessage(text)}
                </div>
                <div class="message-actions">
                    <button class="action-btn" onclick="copyMessage('${messageId}')" title="Sao chép">
                        <i class="fas fa-copy"></i> Sao chép
                    </button>
                    <button class="action-btn" onclick="speakMessage('${messageId}')" title="Đọc to">
                        <i class="fas fa-volume-up"></i> Đọc
                    </button>
                </div>
                <div class="message-time">${time}</div>
            </div>
        `;
    } else {
        messageDiv.innerHTML = `
            <div>
                <div class="message-content" id="${messageId}">
                    ${escapeHtml(text)}
                </div>
                <div class="message-time">${time}</div>
            </div>
            <div class="message-icon">
                <i class="fas fa-user"></i>
            </div>
        `;
    }

    chatMessages.appendChild(messageDiv);
    scrollToBottom();

    if (saveToHistory) {
        chatHistory.push({ message: text, type, timestamp: new Date().toISOString() });
        saveChatHistory();
    }
}

function formatMessage(text) {
    if (typeof text !== 'string') {
        text = String(text);
    }

    // Escape HTML first
    let formatted = escapeHtml(text);

    // Format line breaks
    formatted = formatted.replace(/\n/g, '<br>');

    // Format bold **text**
    formatted = formatted.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

    // Format italic *text*
    formatted = formatted.replace(/\*(.+?)\*/g, '<em>$1</em>');

    // Format code `code`
    formatted = formatted.replace(/`(.+?)`/g, '<code style="background: #f4f4f4; padding: 2px 6px; border-radius: 3px;">$1</code>');

    // Highlight prices
    formatted = formatted.replace(/(\d{1,3}(?:\.\d{3})*(?:,\d+)?)\s*đ/gi,
        '<strong style="color: #764ba2; font-size: 1.1em;">$1đ</strong>');

    // Format bullet points
    formatted = formatted.replace(/^[•\-]\s+(.+)$/gm, '• $1');

    // Format links
    formatted = formatted.replace(/(https?:\/\/[^\s]+)/g,
        '<a href="$1" target="_blank" style="color: #667eea; text-decoration: underline;">$1</a>');

    return formatted;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ============= UTILITY FUNCTIONS =============
function getCurrentTime() {
    const now = new Date();
    const hours = now.getHours().toString().padStart(2, '0');
    const minutes = now.getMinutes().toString().padStart(2, '0');
    return `${hours}:${minutes}`;
}

function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function handleKeyPress(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
}

// ============= MESSAGE ACTIONS =============
function copyMessage(messageId) {
    const messageElement = document.getElementById(messageId);
    if (!messageElement) return;

    const text = messageElement.innerText || messageElement.textContent;

    navigator.clipboard.writeText(text).then(() => {
        showToast('Đã sao chép tin nhắn', 'success');
    }).catch(err => {
        console.error('Failed to copy:', err);
        showToast('Không thể sao chép', 'error');
    });
}

function speakMessage(messageId) {
    const messageElement = document.getElementById(messageId);
    if (!messageElement) return;

    const text = messageElement.innerText || messageElement.textContent;

    if ('speechSynthesis' in window) {
        // Stop any ongoing speech
        window.speechSynthesis.cancel();

        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'vi-VN';
        utterance.rate = 1.0;
        utterance.pitch = 1.0;

        window.speechSynthesis.speak(utterance);
        showToast('Đang đọc tin nhắn...', 'info');
    } else {
        showToast('Trình duyệt không hỗ trợ đọc văn bản', 'warning');
    }
}

// ============= TOAST NOTIFICATIONS =============
function showToast(message, type = 'info') {
    const toastContainer = document.getElementById('toastContainer');
    if (!toastContainer) return;

    const toast = document.createElement('div');
    toast.className = `custom-toast toast-${type}`;

    const icons = {
        success: 'fa-check-circle',
        error: 'fa-exclamation-circle',
        warning: 'fa-exclamation-triangle',
        info: 'fa-info-circle'
    };

    const colors = {
        success: '#10b981',
        error: '#ef4444',
        warning: '#f59e0b',
        info: '#667eea'
    };

    toast.innerHTML = `
        <div style="display: flex; align-items: center; gap: 10px;">
            <i class="fas ${icons[type]}" style="color: ${colors[type]}; font-size: 20px;"></i>
            <span style="color: #333; font-weight: 500;">${message}</span>
        </div>
    `;

    toastContainer.appendChild(toast);

    // Auto remove after 3 seconds
    setTimeout(() => {
        toast.style.animation = 'slideOutRight 0.3s ease';
        setTimeout(() => {
            toast.remove();
        }, 300);
    }, 3000);
}

// ============= DARK MODE =============
let isDarkMode = false;

function toggleDarkMode() {
    isDarkMode = !isDarkMode;
    document.body.classList.toggle('dark-mode', isDarkMode);

    const icon = document.querySelector('.btn-icon i.fa-moon');
    if (icon) {
        icon.className = isDarkMode ? 'fas fa-sun' : 'fas fa-moon';
    }

    showToast(isDarkMode ? 'Đã bật chế độ tối' : 'Đã tắt chế độ tối', 'info');

    // Save preference
    localStorage.setItem('darkMode', isDarkMode);
}

// Load dark mode preference
if (localStorage.getItem('darkMode') === 'true') {
    toggleDarkMode();
}

// Focus input when clicking anywhere in chat
chatMessages.addEventListener('click', () => {
    messageInput.focus();
});

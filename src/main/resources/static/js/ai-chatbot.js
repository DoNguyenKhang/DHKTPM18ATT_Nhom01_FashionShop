// AI Chatbot JavaScript - Simple Version

const chatMessages = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const loading = document.getElementById('loading');
const typingIndicator = document.getElementById('typingIndicator');

// Hàm gửi tin nhắn nhanh
function sendQuickMessage(message) {
    messageInput.value = message;
    sendMessage();
}

// Hàm gửi tin nhắn
async function sendMessage() {
    const message = messageInput.value.trim();

    if (!message) {
        return;
    }

    // Hiển thị tin nhắn của user
    addMessage(message, 'user');

    // Xóa input
    messageInput.value = '';

    // Hiển thị typing indicator
    typingIndicator.classList.add('active');
    loading.classList.add('active');

    try {
        // Gọi API
        const response = await fetch('/api/ai/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(message)
        });

        if (!response.ok) {
            throw new Error('Lỗi kết nối - vui lòng thử lại');
        }

        const data = await response.json();

        // Hiển thị phản hồi từ AI
        addMessage(data.response, 'ai');

    } catch (error) {
        console.error('Error:', error);
        addMessage('Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại sau.', 'ai');
    } finally {
        typingIndicator.classList.remove('active');
        loading.classList.remove('active');
    }
}

// Hàm thêm tin nhắn vào chat
function addMessage(text, type) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${type}`;

    if (type === 'ai') {
        messageDiv.innerHTML = `
            <div class="message-icon">
                <i class="fas fa-robot"></i>
            </div>
            <div class="message-content">${formatMessage(text)}</div>
        `;
    } else {
        messageDiv.innerHTML = `
            <div class="message-content">${escapeHtml(text)}</div>
            <div class="message-icon">
                <i class="fas fa-user"></i>
            </div>
        `;
    }

    chatMessages.appendChild(messageDiv);

    // Scroll xuống cuối
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// Hàm format tin nhắn với hỗ trợ hiển thị sản phẩm
function formatMessage(text) {
    // Escape HTML trước
    let formatted = escapeHtml(text);

    // Thay thế line breaks
    formatted = formatted.replace(/\n/g, '<br>');

    // Highlight giá tiền
    formatted = formatted.replace(/(\d{1,3}(?:\.\d{3})*(?:,\d+)?)\s*đ/g, '<strong style="color: #667eea;">$1đ</strong>');

    // Highlight tên sản phẩm trong ngoặc kép
    formatted = formatted.replace(/"([^"]+)"/g, '<strong>"$1"</strong>');

    return formatted;
}

// Hàm escape HTML để tránh XSS
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Xử lý phím Enter
function handleKeyPress(event) {
    if (event.key === 'Enter') {
        sendMessage();
    }
}

// Focus vào input khi load trang
window.addEventListener('load', () => {
    messageInput.focus();
});

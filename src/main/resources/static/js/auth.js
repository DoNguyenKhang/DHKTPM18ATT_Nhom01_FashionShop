// Authentication utilities
const AUTH_API = '/api/auth';

// Get access token from localStorage
function getAccessToken() {
    return localStorage.getItem('accessToken');
}

// Get refresh token from localStorage
function getRefreshToken() {
    return localStorage.getItem('refreshToken');
}

// Get user info from localStorage
function getUser() {
    const userStr = localStorage.getItem('user');
    return userStr ? JSON.parse(userStr) : null;
}

// Set authentication data
function setAuthData(data) {
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    localStorage.setItem('user', JSON.stringify(data.user));
}

// Clear authentication data
function clearAuthData() {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
}

// Check if user is authenticated
function isAuthenticated() {
    return !!getAccessToken();
}

// Check authentication and redirect if needed
function checkAuth(redirectToLogin = true) {
    if (!isAuthenticated()) {
        if (redirectToLogin) {
            window.location.href = '/login';
        }
        return false;
    }
    return true;
}

// Refresh access token
async function refreshAccessToken() {
    const refreshToken = getRefreshToken();

    if (!refreshToken) {
        return false;
    }

    try {
        const response = await fetch(`${AUTH_API}/refresh`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'ngrok-skip-browser-warning': 'true'
            },
            body: JSON.stringify({ refreshToken })
        });

        if (response.ok) {
            const data = await response.json();
            setAuthData(data);
            return true;
        }

        return false;
    } catch (error) {
        console.error('Error refreshing token:', error);
        return false;
    }
}

// Make authenticated API request with auto token refresh
async function fetchWithAuth(url, options = {}) {
    const token = getAccessToken();

    if (!token) {
        throw new Error('No access token found');
    }

    // Only set Content-Type to application/json if body is not FormData
    const headers = {
        'Authorization': `Bearer ${token}`,
        'ngrok-skip-browser-warning': 'true',
        ...options.headers
    };

    // Add Content-Type only if not uploading files
    if (!(options.body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    // If token expired, try to refresh
    if (response.status === 401) {
        const refreshed = await refreshAccessToken();
        if (refreshed) {
            // Retry request with new token
            headers['Authorization'] = `Bearer ${getAccessToken()}`;
            return fetch(url, { ...options, headers });
        } else {
            // Refresh failed, redirect to login
            clearAuthData();
            window.location.href = '/login';
            throw new Error('Session expired');
        }
    }

    return response;
}

// Proactively refresh token before it expires
// Call this periodically or when app initializes
async function checkAndRefreshToken() {
    const token = getAccessToken();
    if (!token) return false;

    try {
        // Decode JWT to check expiration (simple base64 decode)
        const payload = JSON.parse(atob(token.split('.')[1]));
        const expirationTime = payload.exp * 1000; // Convert to milliseconds
        const currentTime = Date.now();
        const timeUntilExpiry = expirationTime - currentTime;

        // If token expires in less than 5 minutes, refresh it
        if (timeUntilExpiry < 5 * 60 * 1000) {
            console.log('Token expiring soon, refreshing...');
            return await refreshAccessToken();
        }

        return true;
    } catch (error) {
        console.error('Error checking token expiration:', error);
        return false;
    }
}

// Auto-refresh token every 4 minutes
let tokenRefreshInterval = null;

function startTokenRefreshTimer() {
    // Clear any existing interval
    if (tokenRefreshInterval) {
        clearInterval(tokenRefreshInterval);
    }

    // Check immediately
    checkAndRefreshToken();

    // Then check every 4 minutes
    tokenRefreshInterval = setInterval(() => {
        checkAndRefreshToken();
    }, 4 * 60 * 1000); // 4 minutes
}

function stopTokenRefreshTimer() {
    if (tokenRefreshInterval) {
        clearInterval(tokenRefreshInterval);
        tokenRefreshInterval = null;
    }
}

// Start auto-refresh when user is authenticated
if (isAuthenticated()) {
    startTokenRefreshTimer();
}

// Logout user
async function logout() {
    stopTokenRefreshTimer();
    const refreshToken = getRefreshToken();

    try {
        if (refreshToken) {
            await fetch(`${AUTH_API}/logout`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'ngrok-skip-browser-warning': 'true'
                },
                body: JSON.stringify({ refreshToken })
            });
        }
    } catch (error) {
        console.error('Error during logout:', error);
    } finally {
        clearAuthData();
        window.location.href = '/login';
    }
}

// Check user role
function hasRole(role) {
    const user = getUser();
    return user && user.roles && user.roles.includes(role);
}

// Check if user is admin
function isAdmin() {
    return hasRole('ADMIN');
}

// Check if user is customer
function isCustomer() {
    return hasRole('CUSTOMER');
}

// Check if user is staff
function isStaff() {
    return hasRole('STAFF_PRODUCT') || hasRole('STAFF_SALES');
}

// Check admin access and redirect if needed
function checkAdminAccess() {
    if (!isAuthenticated()) {
        window.location.href = '/login';
        return false;
    }
    if (!isAdmin()) {
        alert('Bạn không có quyền truy cập trang này!');
        window.location.href = '/';
        return false;
    }
    return true;
}

// Redirect user based on role after login
function redirectBasedOnRole() {
    const user = getUser();
    if (!user) {
        window.location.href = '/login';
        return;
    }

    if (hasRole('ADMIN')) {
        window.location.href = '/dashboard';
    } else if (hasRole('STAFF_PRODUCT') || hasRole('STAFF_SALES')) {
        window.location.href = '/dashboard';
    } else {
        window.location.href = '/';
    }
}

// Format currency VND
function formatCurrency(amount) {
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND'
    }).format(amount);
}

// Format date
function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

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
        console.warn('No refresh token found, cannot refresh access token');
        return false;
    }

    try {
        console.log('Attempting to refresh access token...');
        const response = await fetch(`${AUTH_API}/refresh`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'ngrok-skip-browser-warning': 'true'
            },
            body: JSON.stringify({ refreshToken })
        });

        const data = await response.json().catch(() => ({}));

        if (response.ok) {
            setAuthData(data);
            console.log('Access token refreshed successfully');
            return true;
        }

        // Log error details
        console.error('Failed to refresh token:', response.status, data);

        // If refresh token is invalid/expired or server error, clear auth data
        if (response.status === 401 || response.status === 400 || response.status === 500) {
            console.warn('Refresh token failed, clearing auth data');
            clearAuthData();
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
    if (!token) {
        console.warn('No access token found');
        return false;
    }

    try {
        // Decode JWT to check expiration (simple base64 decode)
        const payload = JSON.parse(atob(token.split('.')[1]));
        const expirationTime = payload.exp * 1000; // Convert to milliseconds
        const currentTime = Date.now();
        const timeUntilExpiry = expirationTime - currentTime;

        // If token is already expired or expires in less than 5 minutes, refresh it
        if (timeUntilExpiry <= 0) {
            console.log('Token already expired, refreshing...');
            return await refreshAccessToken();
        } else if (timeUntilExpiry < 5 * 60 * 1000) {
            console.log('Token expiring soon, refreshing...');
            return await refreshAccessToken();
        }

        console.log('Token still valid, expires in', Math.floor(timeUntilExpiry / 60000), 'minutes');
        return true;
    } catch (error) {
        console.error('Error checking token expiration:', error);
        // If we can't decode the token, try to refresh it
        console.log('Attempting to refresh invalid token...');
        return await refreshAccessToken();
    }
}

// Get token expiration info for debugging
function getTokenInfo() {
    const token = getAccessToken();
    if (!token) {
        return { valid: false, message: 'No access token found' };
    }

    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        const expirationTime = payload.exp * 1000;
        const currentTime = Date.now();
        const timeUntilExpiry = expirationTime - currentTime;
        const isExpired = timeUntilExpiry <= 0;

        return {
            valid: !isExpired,
            email: payload.sub,
            issuedAt: new Date(payload.iat * 1000).toLocaleString('vi-VN'),
            expiresAt: new Date(expirationTime).toLocaleString('vi-VN'),
            timeUntilExpiry: isExpired ? 'Expired' : `${Math.floor(timeUntilExpiry / 60000)} minutes`,
            isExpired
        };
    } catch (error) {
        return { valid: false, message: 'Invalid token format' };
    }
}

// Force refresh token (for manual refresh)
async function forceRefreshToken() {
    console.log('Force refreshing token...');
    const result = await refreshAccessToken();
    if (result) {
        console.log('Token refreshed successfully!', getTokenInfo());
    } else {
        console.error('Failed to refresh token');
    }
    return result;
}

// Auto-refresh token every 4 minutes
let tokenRefreshInterval = null;
let isRefreshing = false; // Prevent multiple refresh attempts

function startTokenRefreshTimer() {
    // Clear any existing interval
    if (tokenRefreshInterval) {
        clearInterval(tokenRefreshInterval);
    }

    // Then check every 4 minutes (don't check immediately to avoid race conditions)
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

// Check and refresh token - with lock to prevent multiple simultaneous refreshes
async function checkAndRefreshTokenSafe() {
    if (isRefreshing) {
        console.log('Token refresh already in progress, skipping...');
        return true; // Assume it will succeed
    }

    isRefreshing = true;
    try {
        const result = await checkAndRefreshToken();
        return result;
    } finally {
        isRefreshing = false;
    }
}

// Initialize auth - call this once when page loads
async function initAuth() {
    if (!isAuthenticated()) {
        return false;
    }

    // Check and refresh token if needed
    const tokenValid = await checkAndRefreshTokenSafe();
    if (tokenValid) {
        // Start the refresh timer only after successful validation
        startTokenRefreshTimer();
    }
    return tokenValid;
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

// Check if user is staff product
function isStaffProduct() {
    return hasRole('STAFF_PRODUCT');
}

// Check if user is staff sales
function isStaffSales() {
    return hasRole('STAFF_SALES');
}

// Check admin access and redirect if needed
function checkAdminAccess() {
    if (!isAuthenticated()) {
        window.location.href = '/login';
        return false;
    }
    if (!isAdmin() && !isStaff()) {
        alert('Bạn không có quyền truy cập trang này!');
        window.location.href = '/';
        return false;
    }
    return true;
}

// Get user role display name
function getUserRoleDisplay() {
    if (isAdmin()) return 'Quản trị viên';
    if (isStaffProduct()) return 'Nhân viên Sản phẩm';
    if (isStaffSales()) return 'Nhân viên Bán hàng';
    if (isCustomer()) return 'Khách hàng';
    return 'Người dùng';
}

// Check if user can manage products (ADMIN or STAFF_PRODUCT only)
function checkProductAccess() {
    if (!isAuthenticated()) {
        window.location.href = '/login';
        return false;
    }
    if (!isAdmin() && !isStaffProduct()) {
        alert('Bạn không có quyền quản lý sản phẩm!');
        window.location.href = '/dashboard';
        return false;
    }
    return true;
}

// Check if user can manage orders (ADMIN or STAFF_SALES only)
function checkOrderAccess() {
    if (!isAuthenticated()) {
        window.location.href = '/login';
        return false;
    }
    if (!isAdmin() && !isStaffSales()) {
        alert('Bạn không có quyền quản lý đơn hàng!');
        window.location.href = '/dashboard';
        return false;
    }
    return true;
}

// Check if user can manage users (ADMIN only)
function checkUserManagementAccess() {
    if (!isAuthenticated()) {
        window.location.href = '/login';
        return false;
    }
    if (!isAdmin()) {
        alert('Bạn không có quyền quản lý người dùng!');
        window.location.href = '/dashboard';
        return false;
    }
    return true;
}

// Check if user can manage categories (ADMIN or STAFF_PRODUCT only)
function checkCategoryAccess() {
    if (!isAuthenticated()) {
        window.location.href = '/login';
        return false;
    }
    if (!isAdmin() && !isStaffProduct()) {
        alert('Bạn không có quyền quản lý danh mục!');
        window.location.href = '/dashboard';
        return false;
    }
    return true;
}

// Check if user can manage brands (ADMIN or STAFF_PRODUCT only)
function checkBrandAccess() {
    if (!isAuthenticated()) {
        window.location.href = '/login';
        return false;
    }
    if (!isAdmin() && !isStaffProduct()) {
        alert('Bạn không có quyền quản lý thương hiệu!');
        window.location.href = '/dashboard';
        return false;
    }
    return true;
}

// Check if user can manage coupons (ADMIN or STAFF_SALES only)
function checkCouponAccess() {
    if (!isAuthenticated()) {
        window.location.href = '/login';
        return false;
    }
    if (!isAdmin() && !isStaffSales()) {
        alert('Bạn không có quyền quản lý mã giảm giá!');
        window.location.href = '/dashboard';
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

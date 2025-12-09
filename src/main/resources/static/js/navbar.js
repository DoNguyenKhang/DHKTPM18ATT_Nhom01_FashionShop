/**
 * Navbar Component - Role-based Menu Visibility
 * Fashion Shop - Navigation Control for All Pages
 */

// Initialize navbar when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    initializeNavbar();
});

/**
 * Initialize navbar with role-based menu visibility
 */
function initializeNavbar() {
    const user = getUser();

    if (user && isAuthenticated()) {
        showAuthenticatedMenu(user);
    } else {
        showGuestMenu();
    }
}

/**
 * Show menu for authenticated users
 */
function showAuthenticatedMenu(user) {
    // Hide guest menus
    hideElement('loginMenu');
    hideElement('registerMenu');

    // Show authenticated menus
    showElement('userMenu');
    showElement('userDropdown');

    // Update user name in dropdown
    const userNameElement = document.getElementById('userName');
    if (userNameElement) {
        userNameElement.textContent = user.fullName || user.username || user.email;
    }

    // Show Dashboard menu for Admin and Staff
    if (isAdmin() || isStaff()) {
        showElement('adminMenu');

        // Update dashboard link text based on role
        const dashboardLink = document.querySelector('#adminMenu a');
        if (dashboardLink) {
            if (isAdmin()) {
                dashboardLink.innerHTML = '<i class="bi bi-speedometer2"></i> Dashboard';
            } else if (isStaffProduct()) {
                dashboardLink.innerHTML = '<i class="bi bi-speedometer2"></i> Quản lý SP';
            } else if (isStaffSales()) {
                dashboardLink.innerHTML = '<i class="bi bi-speedometer2"></i> Quản lý ĐH';
            }
        }
    }

    // Add role badge to user dropdown (optional)
    addRoleBadgeToDropdown();
}

/**
 * Show menu for guest users
 */
function showGuestMenu() {
    // Show guest menus
    showElement('loginMenu');
    showElement('registerMenu');

    // Hide authenticated menus
    hideElement('userMenu');
    hideElement('adminMenu');
    hideElement('userDropdown');
}

/**
 * Add role badge to user dropdown
 */
function addRoleBadgeToDropdown() {
    const dropdown = document.querySelector('#userDropdown .dropdown-menu');
    if (!dropdown) return;

    // Check if badge already added
    if (dropdown.querySelector('.role-badge-item')) return;

    let badgeColor = 'primary';
    let roleIcon = 'person-fill';
    let roleText = getUserRoleDisplay();

    if (isAdmin()) {
        badgeColor = 'danger';
        roleIcon = 'shield-fill-check';
    } else if (isStaffProduct()) {
        badgeColor = 'success';
        roleIcon = 'box-seam-fill';
    } else if (isStaffSales()) {
        badgeColor = 'info';
        roleIcon = 'cart-check-fill';
    } else if (isCustomer()) {
        badgeColor = 'warning';
        roleIcon = 'person-badge-fill';
    }

    // Create badge element
    const badgeItem = document.createElement('li');
    badgeItem.className = 'role-badge-item';
    badgeItem.innerHTML = `
        <div class="dropdown-header text-center">
            <span class="badge bg-${badgeColor}">
                <i class="bi bi-${roleIcon}"></i> ${roleText}
            </span>
        </div>
    `;

    // Insert at the beginning of dropdown
    dropdown.insertBefore(badgeItem, dropdown.firstChild);
}

/**
 * Helper function to show element
 */
function showElement(elementId) {
    const element = document.getElementById(elementId);
    if (element) {
        element.style.display = '';
    }
}

/**
 * Helper function to hide element
 */
function hideElement(elementId) {
    const element = document.getElementById(elementId);
    if (element) {
        element.style.display = 'none';
    }
}

/**
 * Logout function for navbar
 */
function logout() {
    if (typeof window.logout === 'function') {
        window.logout();
    } else {
        // Fallback logout
        localStorage.removeItem('token');
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        window.location.href = '/login';
    }
}


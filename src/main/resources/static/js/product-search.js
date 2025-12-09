// Product Search and Filter JavaScript

let currentPage = 0;
let totalPages = 0;
let searchTimeout = null;
let currentFilters = {
    keyword: '',
    categoryId: '',
    brandId: '',
    minPrice: '',
    maxPrice: '',
    sortBy: 'id',
    sortDirection: 'DESC'
};

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    updateNavigation();
    loadFilterOptions();
    loadProducts();
    initializeSearchAutocomplete();
});

// Update navigation based on user
function updateNavigation() {
    const user = getUser();
    if (user) {
        document.getElementById('loginNav').style.display = 'none';
        document.getElementById('registerNav').style.display = 'none';
        document.getElementById('userDropdown').style.display = 'block';
        document.getElementById('userName').textContent = user.fullName || user.email;

        if (user.role === 'USER') {
            document.getElementById('cartNav').style.display = 'block';
            document.getElementById('ordersNav').style.display = 'block';
            updateCartCount();
        }

        if (isAdmin() || isStaff()) {
            document.getElementById('dashboardNav').style.display = 'block';
        }
    }
}

// Update cart count
function updateCartCount() {
    const cart = JSON.parse(localStorage.getItem('cart') || '[]');
    const cartCountEl = document.getElementById('cartCount');
    if (cart.length > 0) {
        const totalItems = cart.reduce((sum, item) => sum + item.quantity, 0);
        cartCountEl.textContent = totalItems;
        cartCountEl.style.display = 'inline';
    } else {
        cartCountEl.style.display = 'none';
    }
}

// Logout function
function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    localStorage.removeItem('cart');
    window.location.href = '/';
}

// Load filter options (categories and brands)
async function loadFilterOptions() {
    try {
        // Load categories
        const categoriesResponse = await fetch('/api/categories');
        if (categoriesResponse.ok) {
            const categories = await categoriesResponse.json();
            const categorySelect = document.getElementById('categoryFilter');
            categories.forEach(cat => {
                categorySelect.innerHTML += `<option value="${cat.id}">${cat.name}</option>`;
            });
        }

        // Load brands
        const brandsResponse = await fetch('/api/brands');
        if (brandsResponse.ok) {
            const brands = await brandsResponse.json();
            const brandSelect = document.getElementById('brandFilter');
            brands.forEach(brand => {
                brandSelect.innerHTML += `<option value="${brand.id}">${brand.name}</option>`;
            });
        }
    } catch (error) {
        console.error('Error loading filter options:', error);
    }
}

// Search autocomplete functionality
function initializeSearchAutocomplete() {
    const searchInput = document.getElementById('mainSearchInput');
    const searchBox = document.getElementById('mainSearchBox');
    const suggestionsDiv = document.getElementById('searchSuggestions');

    searchInput.addEventListener('input', function() {
        const query = this.value.trim();

        if (query.length > 0) {
            searchBox.classList.add('has-value');
        } else {
            searchBox.classList.remove('has-value');
        }

        if (query.length >= 2) {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                fetchSearchSuggestions(query);
            }, 300);
        } else {
            suggestionsDiv.classList.remove('show');
        }
    });

    searchInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            performSearch();
        }
    });

    // Close suggestions when clicking outside
    document.addEventListener('click', function(e) {
        if (!searchBox.contains(e.target)) {
            suggestionsDiv.classList.remove('show');
        }
    });
}

// Fetch search suggestions
async function fetchSearchSuggestions(query) {
    try {
        const response = await fetch(`/api/products/search?keyword=${encodeURIComponent(query)}&page=0&size=5`);
        if (response.ok) {
            const data = await response.json();
            displaySearchSuggestions(data.content || []);
        }
    } catch (error) {
        console.error('Error fetching suggestions:', error);
    }
}

// Display search suggestions
function displaySearchSuggestions(products) {
    const suggestionsDiv = document.getElementById('searchSuggestions');

    if (products.length === 0) {
        suggestionsDiv.innerHTML = `
            <div class="no-results">
                <i class="bi bi-search"></i>
                <p>Không tìm thấy sản phẩm phù hợp</p>
            </div>
        `;
    } else {
        suggestionsDiv.innerHTML = `
            <div class="suggestion-header">Sản phẩm gợi ý</div>
            ${products.map(product => {
                const imageUrl = product.images && product.images.length > 0
                    ? product.images[0].url
                    : 'https://via.placeholder.com/60x60?text=No+Image';
                    
                let price = 'Liên hệ';
                if (product.variants && product.variants.length > 0) {
                    const prices = product.variants
                        .filter(v => v.isActive && v.price)
                        .map(v => parseFloat(v.price));
                    if (prices.length > 0) {
                        price = formatCurrency(Math.min(...prices));
                    }
                }
                
                return `
                    <div class="suggestion-item" onclick="window.location.href='/products/${product.slug}'">
                        <img src="${imageUrl}" class="product-img" alt="${product.name}" 
                             onerror="this.src='https://via.placeholder.com/60x60?text=No+Image'">
                        <div class="product-details">
                            <div class="product-name">${product.name}</div>
                            <div class="product-category">${product.brand ? product.brand.name : ''}</div>
                            <div class="product-price">${price}</div>
                        </div>
                    </div>
                `;
            }).join('')}
        `;
    }

    suggestionsDiv.classList.add('show');
}

// Clear search
function clearSearch() {
    document.getElementById('mainSearchInput').value = '';
    document.getElementById('mainSearchBox').classList.remove('has-value');
    document.getElementById('searchSuggestions').classList.remove('show');
    currentFilters.keyword = '';
    loadProducts();
}

// Perform search
function performSearch() {
    const query = document.getElementById('mainSearchInput').value.trim();
    currentFilters.keyword = query;
    document.getElementById('searchSuggestions').classList.remove('show');
    loadProducts(0);
    updateActiveFilters();
}

// Quick search
function quickSearch(keyword) {
    document.getElementById('mainSearchInput').value = keyword;
    document.getElementById('mainSearchBox').classList.add('has-value');
    performSearch();
}

// Apply filters
function applyFilters() {
    const categoryId = document.getElementById('categoryFilter').value;
    const brandId = document.getElementById('brandFilter').value;
    const sortValue = document.getElementById('sortSelect').value;
    const [sortBy, sortDirection] = sortValue.split('-');

    currentFilters.categoryId = categoryId;
    currentFilters.brandId = brandId;
    currentFilters.sortBy = sortBy;
    currentFilters.sortDirection = sortDirection;

    // Sync mobile sort
    document.getElementById('sortSelectMobile').value = sortValue;

    loadProducts(0);
    updateActiveFilters();
}

// Apply price filter
function applyPriceFilter() {
    currentFilters.minPrice = document.getElementById('minPrice').value;
    currentFilters.maxPrice = document.getElementById('maxPrice').value;
    loadProducts(0);
    updateActiveFilters();
}

// Clear all filters
function clearAllFilters() {
    document.getElementById('categoryFilter').value = '';
    document.getElementById('brandFilter').value = '';
    document.getElementById('minPrice').value = '';
    document.getElementById('maxPrice').value = '';
    document.getElementById('sortSelect').value = 'id-DESC';
    document.getElementById('sortSelectMobile').value = 'id-DESC';

    currentFilters = {
        keyword: currentFilters.keyword, // Keep search keyword
        categoryId: '',
        brandId: '',
        minPrice: '',
        maxPrice: '',
        sortBy: 'id',
        sortDirection: 'DESC'
    };

    loadProducts(0);
    updateActiveFilters();
}

// Update active filters display
function updateActiveFilters() {
    const container = document.getElementById('activeFiltersContainer');
    const filters = [];

    if (currentFilters.keyword) {
        filters.push({
            label: `Tìm kiếm: "${currentFilters.keyword}"`,
            action: () => { clearSearch(); }
        });
    }

    if (currentFilters.categoryId) {
        const select = document.getElementById('categoryFilter');
        const text = select.options[select.selectedIndex].text;
        filters.push({
            label: `Danh mục: ${text}`,
            action: () => {
                document.getElementById('categoryFilter').value = '';
                currentFilters.categoryId = '';
                applyFilters();
            }
        });
    }

    if (currentFilters.brandId) {
        const select = document.getElementById('brandFilter');
        const text = select.options[select.selectedIndex].text;
        filters.push({
            label: `Thương hiệu: ${text}`,
            action: () => {
                document.getElementById('brandFilter').value = '';
                currentFilters.brandId = '';
                applyFilters();
            }
        });
    }

    if (currentFilters.minPrice || currentFilters.maxPrice) {
        const min = currentFilters.minPrice || '0';
        const max = currentFilters.maxPrice || '∞';
        filters.push({
            label: `Giá: ${formatCurrency(min)} - ${max === '∞' ? max : formatCurrency(max)}`,
            action: () => {
                document.getElementById('minPrice').value = '';
                document.getElementById('maxPrice').value = '';
                currentFilters.minPrice = '';
                currentFilters.maxPrice = '';
                applyFilters();
            }
        });
    }

    if (filters.length > 0) {
        container.style.display = 'flex';
        container.innerHTML = filters.map((filter, index) => `
            <div class="filter-badge">
                ${filter.label}
                <span class="remove-filter" onclick="event.stopPropagation(); (${filter.action})()">×</span>
            </div>
        `).join('');
    } else {
        container.style.display = 'none';
    }
}

// Load products with current filters
async function loadProducts(page = 0) {
    try {
        let url = '/api/products?';
        const params = new URLSearchParams();

        params.append('page', page);
        params.append('size', 12);
        params.append('sortBy', currentFilters.sortBy);
        params.append('sortDirection', currentFilters.sortDirection);

        if (currentFilters.keyword) {
            url = '/api/products/search?';
            params.append('keyword', currentFilters.keyword);
        } else if (currentFilters.categoryId) {
            url = `/api/products/category/${currentFilters.categoryId}?`;
        } else if (currentFilters.brandId) {
            url = `/api/products/brand/${currentFilters.brandId}?`;
        }

        const response = await fetch(url + params.toString());
        if (response.ok) {
            const data = await response.json();
            let products = data.content || [];

            // Client-side price filtering
            if (currentFilters.minPrice || currentFilters.maxPrice) {
                products = filterProductsByPrice(products);
            }

            displayProducts(products);
            updatePagination(data);
            currentPage = page;
        } else {
            showError('Không thể tải danh sách sản phẩm');
        }
    } catch (error) {
        console.error('Error loading products:', error);
        showError('Có lỗi xảy ra khi tải sản phẩm');
    }
}

// Filter products by price
function filterProductsByPrice(products) {
    const minPrice = parseFloat(currentFilters.minPrice) || 0;
    const maxPrice = parseFloat(currentFilters.maxPrice) || Infinity;

    return products.filter(product => {
        if (product.variants && product.variants.length > 0) {
            const prices = product.variants
                .filter(v => v.isActive && v.price)
                .map(v => parseFloat(v.price));

            if (prices.length > 0) {
                const productMinPrice = Math.min(...prices);
                return productMinPrice >= minPrice && productMinPrice <= maxPrice;
            }
        }
        return false;
    });
}

// Display products
function displayProducts(products) {
    const container = document.getElementById('productsContainer');
    document.getElementById('totalProducts').textContent = products.length;

    if (!products || products.length === 0) {
        container.innerHTML = `
            <div class="col-12 text-center py-5">
                <i class="bi bi-inbox" style="font-size: 4rem; color: #ddd;"></i>
                <p class="text-muted mt-3">Không tìm thấy sản phẩm phù hợp</p>
                <button class="btn btn-dark mt-2" onclick="clearAllFilters(); clearSearch();">
                    Xóa bộ lọc
                </button>
            </div>
        `;
        return;
    }

    container.innerHTML = products.map(product => {
        const imageUrl = product.images && product.images.length > 0
            ? product.images[0].url
            : 'https://via.placeholder.com/400x533?text=No+Image';

        let price = 'Liên hệ';
        let compareAtPrice = null;
        let totalStock = 0;
        let hasStock = false;

        if (product.variants && product.variants.length > 0) {
            product.variants.forEach(v => {
                if (v.isActive && v.stock) {
                    totalStock += v.stock;
                }
            });
            hasStock = totalStock > 0;

            const activePrices = product.variants
                .filter(v => v.isActive && v.price)
                .map(v => parseFloat(v.price));

            if (activePrices.length > 0) {
                const minPrice = Math.min(...activePrices);
                const maxPrice = Math.max(...activePrices);

                if (minPrice !== maxPrice) {
                    price = `${formatCurrency(minPrice)} - ${formatCurrency(maxPrice)}`;
                } else {
                    price = formatCurrency(minPrice);
                }
            }

            const variantWithCompare = product.variants.find(v => v.compareAtPrice);
            if (variantWithCompare) {
                compareAtPrice = parseFloat(variantWithCompare.compareAtPrice);
            }
        }

        let badge = '';
        if (!product.isActive) {
            badge = '<span class="badge bg-secondary">Ngừng bán</span>';
        } else if (!hasStock) {
            badge = '<span class="badge bg-danger">Hết hàng</span>';
        } else if (compareAtPrice) {
            badge = '<span class="badge bg-danger">SALE</span>';
        }

        return `
            <div class="col-lg-4 col-md-6 col-sm-6">
                <div class="modern-product-card" onclick="window.location.href='/products/${product.slug}'">
                    <div class="product-image-wrapper">
                        ${badge ? `<div class="product-badge">${badge}</div>` : ''}
                        <img src="${imageUrl}" alt="${product.name}"
                             onerror="this.src='https://via.placeholder.com/400x533?text=No+Image'">
                    </div>
                    <div class="product-info">
                        ${product.brand ? `<div class="product-brand">${product.brand.name}</div>` : ''}
                        <div class="product-name">${product.name}</div>
                        <div>
                            ${compareAtPrice ? `<span class="product-price-old">${formatCurrency(compareAtPrice)}</span>` : ''}
                            <div class="product-price">${price}</div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

// Update pagination
function updatePagination(data) {
    totalPages = data.totalPages || 0;
    const paginationEl = document.getElementById('pagination');

    if (totalPages <= 1) {
        paginationEl.innerHTML = '';
        return;
    }

    let html = '';

    // Previous button
    html += `
        <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="event.preventDefault(); loadProducts(${currentPage - 1})">
                <i class="bi bi-chevron-left"></i>
            </a>
        </li>
    `;

    // Page numbers
    for (let i = 0; i < totalPages; i++) {
        if (i === 0 || i === totalPages - 1 || (i >= currentPage - 2 && i <= currentPage + 2)) {
            html += `
                <li class="page-item ${i === currentPage ? 'active' : ''}">
                    <a class="page-link" href="#" onclick="event.preventDefault(); loadProducts(${i})">
                        ${i + 1}
                    </a>
                </li>
            `;
        } else if (i === currentPage - 3 || i === currentPage + 3) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
    }

    // Next button
    html += `
        <li class="page-item ${currentPage === totalPages - 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="event.preventDefault(); loadProducts(${currentPage + 1})">
                <i class="bi bi-chevron-right"></i>
            </a>
        </li>
    `;

    paginationEl.innerHTML = html;
}

// Toggle mobile filters
function toggleMobileFilters() {
    const filterSection = document.getElementById('filterSection');
    filterSection.classList.toggle('show');
}

// Show error message
function showError(message) {
    const container = document.getElementById('productsContainer');
    container.innerHTML = `
        <div class="col-12 text-center py-5">
            <i class="bi bi-exclamation-triangle" style="font-size: 4rem; color: #dc3545;"></i>
            <p class="text-danger mt-3">${message}</p>
            <button class="btn btn-dark mt-2" onclick="loadProducts()">Thử lại</button>
        </div>
    `;
}


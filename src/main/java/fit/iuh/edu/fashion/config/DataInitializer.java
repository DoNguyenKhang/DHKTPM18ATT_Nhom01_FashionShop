package fit.iuh.edu.fashion.config;

import fit.iuh.edu.fashion.models.Permission;
import fit.iuh.edu.fashion.models.Role;
import fit.iuh.edu.fashion.repositories.PermissionRepository;
import fit.iuh.edu.fashion.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    public void run(String... args) throws Exception {
        initializeRolesAndPermissions();
    }

    private void initializeRolesAndPermissions() {
        if (roleRepository.count() > 0) {
            log.info("Roles and permissions already initialized");
            return;
        }

        log.info("Initializing roles and permissions...");

        // Create permissions
        List<Permission> permissions = Arrays.asList(
                Permission.builder().code("PRODUCT_CREATE").name("Tạo sản phẩm").build(),
                Permission.builder().code("PRODUCT_UPDATE").name("Sửa sản phẩm").build(),
                Permission.builder().code("PRODUCT_DELETE").name("Xóa sản phẩm").build(),
                Permission.builder().code("PRODUCT_VIEW").name("Xem sản phẩm").build(),
                Permission.builder().code("ORDER_VIEW").name("Xem đơn hàng").build(),
                Permission.builder().code("ORDER_UPDATE").name("Cập nhật đơn hàng").build(),
                Permission.builder().code("ORDER_DELETE").name("Xóa đơn hàng").build(),
                Permission.builder().code("COUPON_MANAGE").name("Quản lý mã giảm giá").build(),
                Permission.builder().code("USER_MANAGE").name("Quản lý người dùng").build(),
                Permission.builder().code("ROLE_MANAGE").name("Quản lý vai trò").build()
        );
        permissionRepository.saveAll(permissions);

        // Create roles
        Role adminRole = Role.builder()
                .code("ADMIN")
                .name("Quản lý (toàn quyền)")
                .description("Có toàn quyền trên hệ thống")
                .permissions(new HashSet<>(permissions))
                .build();

        Set<Permission> staffProductPermissions = new HashSet<>();
        staffProductPermissions.add(permissionRepository.findByCode("PRODUCT_CREATE").orElseThrow());
        staffProductPermissions.add(permissionRepository.findByCode("PRODUCT_UPDATE").orElseThrow());
        staffProductPermissions.add(permissionRepository.findByCode("PRODUCT_DELETE").orElseThrow());
        staffProductPermissions.add(permissionRepository.findByCode("PRODUCT_VIEW").orElseThrow());

        Role staffProductRole = Role.builder()
                .code("STAFF_PRODUCT")
                .name("Nhân viên quản lý sản phẩm")
                .description("Quản lý sản phẩm và tồn kho")
                .permissions(staffProductPermissions)
                .build();

        Set<Permission> staffSalesPermissions = new HashSet<>();
        staffSalesPermissions.add(permissionRepository.findByCode("ORDER_VIEW").orElseThrow());
        staffSalesPermissions.add(permissionRepository.findByCode("ORDER_UPDATE").orElseThrow());
        staffSalesPermissions.add(permissionRepository.findByCode("PRODUCT_VIEW").orElseThrow());

        Role staffSalesRole = Role.builder()
                .code("STAFF_SALES")
                .name("Nhân viên bán hàng")
                .description("Xử lý đơn hàng và chăm sóc khách hàng")
                .permissions(staffSalesPermissions)
                .build();

        Role customerRole = Role.builder()
                .code("CUSTOMER")
                .name("Khách hàng")
                .description("Khách hàng mua sắm")
                .permissions(new HashSet<>())
                .build();

        roleRepository.saveAll(Arrays.asList(adminRole, staffProductRole, staffSalesRole, customerRole));

        log.info("Roles and permissions initialized successfully");
    }
}

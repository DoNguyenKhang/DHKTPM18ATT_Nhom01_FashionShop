package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.request.BrandRequest;
import fit.iuh.edu.fashion.dto.response.BrandResponse;
import fit.iuh.edu.fashion.models.Brand;
import fit.iuh.edu.fashion.repositories.BrandRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandRepository brandRepository;

    @GetMapping
    public ResponseEntity<List<BrandResponse>> getAllBrands() {
        List<BrandResponse> brands = brandRepository.findAll().stream()
                .map(this::mapToBrandResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(brands);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BrandResponse> getBrandById(@PathVariable Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Brand not found"));
        return ResponseEntity.ok(mapToBrandResponse(brand));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_PRODUCT')")
    public ResponseEntity<BrandResponse> createBrand(@Valid @RequestBody BrandRequest request) {
        Brand brand = new Brand();
        brand.setName(request.getName());
        brand.setSlug(request.getSlug());
        brand.setDescription(request.getDescription());
        brand.setLogo(request.getLogoUrl());
        brand.setIsActive(request.getIsActive());
        Brand savedBrand = brandRepository.save(brand);
        return ResponseEntity.ok(mapToBrandResponse(savedBrand));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_PRODUCT')")
    public ResponseEntity<BrandResponse> updateBrand(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Brand not found"));
        brand.setName(request.getName());
        brand.setSlug(request.getSlug());
        brand.setDescription(request.getDescription());
        brand.setLogo(request.getLogoUrl());
        brand.setIsActive(request.getIsActive());
        Brand updatedBrand = brandRepository.save(brand);
        return ResponseEntity.ok(mapToBrandResponse(updatedBrand));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBrand(@PathVariable Long id) {
        brandRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    private BrandResponse mapToBrandResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .slug(brand.getSlug())
                .description(brand.getDescription())
                .logo(brand.getLogo())
                .build();
    }
}

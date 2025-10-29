package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.request.SizeRequest;
import fit.iuh.edu.fashion.models.Size;
import fit.iuh.edu.fashion.repositories.SizeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sizes")
@RequiredArgsConstructor
public class SizeController {

    private final SizeRepository sizeRepository;

    @GetMapping
    public ResponseEntity<List<Size>> getAllSizes() {
        return ResponseEntity.ok(sizeRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Size> getSizeById(@PathVariable Long id) {
        Size size = sizeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Size not found"));
        return ResponseEntity.ok(size);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_PRODUCT')")
    public ResponseEntity<Size> createSize(@Valid @RequestBody SizeRequest request) {
        Size size = new Size();
        size.setName(request.getName());
        size.setNote(request.getDescription());
        size.setIsActive(request.getIsActive());
        Size saved = sizeRepository.save(size);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_PRODUCT')")
    public ResponseEntity<Size> updateSize(@PathVariable Long id, @Valid @RequestBody SizeRequest request) {
        Size size = sizeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Size not found"));
        size.setName(request.getName());
        size.setNote(request.getDescription());
        size.setIsActive(request.getIsActive());
        Size updated = sizeRepository.save(size);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSize(@PathVariable Long id) {
        sizeRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}

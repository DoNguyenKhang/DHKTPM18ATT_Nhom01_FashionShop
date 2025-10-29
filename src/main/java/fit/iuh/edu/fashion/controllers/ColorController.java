package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.request.ColorRequest;
import fit.iuh.edu.fashion.models.Color;
import fit.iuh.edu.fashion.repositories.ColorRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/colors")
@RequiredArgsConstructor
public class ColorController {

    private final ColorRepository colorRepository;

    @GetMapping
    public ResponseEntity<List<Color>> getAllColors() {
        return ResponseEntity.ok(colorRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Color> getColorById(@PathVariable Long id) {
        Color color = colorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Color not found"));
        return ResponseEntity.ok(color);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_PRODUCT')")
    public ResponseEntity<Color> createColor(@Valid @RequestBody ColorRequest request) {
        Color color = new Color();
        color.setName(request.getName());
        color.setHex(request.getHexCode());
        color.setIsActive(request.getIsActive());
        Color saved = colorRepository.save(color);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_PRODUCT')")
    public ResponseEntity<Color> updateColor(@PathVariable Long id, @Valid @RequestBody ColorRequest request) {
        Color color = colorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Color not found"));
        color.setName(request.getName());
        color.setHex(request.getHexCode());
        color.setIsActive(request.getIsActive());
        Color updated = colorRepository.save(color);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteColor(@PathVariable Long id) {
        colorRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}

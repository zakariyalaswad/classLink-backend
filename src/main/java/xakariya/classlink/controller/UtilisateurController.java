package xakariya.classlink.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xakariya.classlink.domain.model.PreferenceNotification;
import xakariya.classlink.domain.model.Utilisateur;
import xakariya.classlink.dto.Dtos.*;
import xakariya.classlink.service.AuthService;
import xakariya.classlink.service.UtilisateurService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UtilisateurController {

    private final UtilisateurService utilisateurService;
    private final AuthService authService;

    public UtilisateurController(UtilisateurService utilisateurService, AuthService authService) {
        this.utilisateurService = utilisateurService;
        this.authService = authService;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal(expression = "subject") String userId) {
        return authService.getUtilisateurCourant(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal(expression = "subject") String userId,
                                           @RequestBody UpdateProfileRequest req) {
        try {
            Utilisateur updated = utilisateurService.gererProfil(userId, req.nom, req.prenom, req.photoUrl);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/preferences")
    public ResponseEntity<List<PreferenceNotification>> getPreferences(
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(utilisateurService.getPreferences(userId));
    }

    @PutMapping("/preferences")
    public ResponseEntity<?> updatePreference(
            @AuthenticationPrincipal(expression = "subject") String userId,
            @RequestBody UpdatePreferenceRequest req) {
        try {
            PreferenceNotification updated = utilisateurService.modifierPreference(
                    userId, req.type,
                    Boolean.TRUE.equals(req.dansApplication),
                    Boolean.TRUE.equals(req.parEmail)
            );
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

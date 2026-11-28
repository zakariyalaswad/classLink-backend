package xakariya.classlink.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import xakariya.classlink.domain.model.Utilisateur;
import xakariya.classlink.dto.Dtos.RegisterRequest;
import xakariya.classlink.service.AuthService;

import java.util.Map;

/** Login/password handling is done by Firebase Auth; the API only receives Firebase ID tokens. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Called once after a Firebase sign-up to create the application profile. */
    @PostMapping("/register")
    public ResponseEntity<?> register(@AuthenticationPrincipal Jwt jwt, @RequestBody RegisterRequest req) {
        try {
            Utilisateur u = authService.inscrire(jwt.getSubject(), req.nom, req.prenom, jwt.getClaimAsString("email"));
            return ResponseEntity.ok(u);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        String tokenEmail = jwt.getClaimAsString("email");
        return authService.getUtilisateurCourant(userId, tokenEmail)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

package xakariya.classlink.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xakariya.classlink.domain.enums.TypePublication;
import xakariya.classlink.domain.model.Publication;
import xakariya.classlink.dto.Dtos.*;
import xakariya.classlink.service.PublicationService;

import java.util.List;
import java.util.Map;

@RestController
public class PublicationController {

    private final PublicationService publicationService;

    public PublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @GetMapping("/api/classes/{classId}/publications")
    public ResponseEntity<List<Publication>> getPublications(
            @PathVariable String classId,
            @RequestParam(required = false) TypePublication type,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        try {
            if (type != null) {
                return ResponseEntity.ok(publicationService.consulterPublicationsParType(classId, type, userId));
            }
            return ResponseEntity.ok(publicationService.consulterPublications(classId, userId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/api/classes/{classId}/publications")
    public ResponseEntity<?> createPublication(
            @PathVariable String classId,
            @AuthenticationPrincipal(expression = "subject") String userId,
            @RequestBody CreatePublicationRequest req) {
        try {
            Publication p;
            if (req.type == TypePublication.DEVOIR) {
                p = publicationService.creerDevoir(classId, userId, req.titre, req.contenu,
                        req.dateLimite, req.bareme, req.piecesJointes);
            } else if (req.type == TypePublication.COURS) {
                p = publicationService.publierCours(classId, userId, req.titre, req.contenu, req.piecesJointes);
            } else {
                p = publicationService.publierAnnonce(classId, userId, req.titre, req.contenu, req.piecesJointes);
            }
            return ResponseEntity.ok(p);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/publications/{id}")
    public ResponseEntity<?> getPublicationById(@PathVariable String id) {
        try {
            return ResponseEntity.ok(publicationService.getPublicationById(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/api/classes/{classId}/check-announcement-permission")
    public ResponseEntity<?> checkAnnouncementPermission(
            @PathVariable String classId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        try {
            boolean allowed = publicationService.verifierDroitPublierAnnonce(classId, userId);
            return ResponseEntity.ok(Map.of("allowed", allowed));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("allowed", false, "error", e.getMessage()));
        }
    }
}

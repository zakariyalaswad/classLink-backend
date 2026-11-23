package xakariya.classlink.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xakariya.classlink.domain.model.Commentaire;
import xakariya.classlink.dto.Dtos.CreateCommentRequest;
import xakariya.classlink.service.CommentaireService;

import java.util.List;
import java.util.Map;

@RestController
public class CommentaireController {

    private final CommentaireService commentaireService;

    public CommentaireController(CommentaireService commentaireService) {
        this.commentaireService = commentaireService;
    }

    @GetMapping("/api/publications/{pubId}/comments")
    public ResponseEntity<List<Commentaire>> getComments(@PathVariable String pubId) {
        return ResponseEntity.ok(commentaireService.getCommentaires(pubId));
    }

    @PostMapping("/api/publications/{pubId}/comments")
    public ResponseEntity<?> addComment(
            @PathVariable String pubId,
            @AuthenticationPrincipal(expression = "subject") String userId,
            @RequestBody CreateCommentRequest req) {
        try {
            Commentaire c = commentaireService.commenterPublication(pubId, userId, req.contenu);
            return ResponseEntity.ok(c);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/publications/{pubId}/check-comment-permission")
    public ResponseEntity<?> checkCommentPermission(
            @PathVariable String pubId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        try {
            boolean allowed = commentaireService.verifierDroitCommenter(pubId, userId);
            return ResponseEntity.ok(Map.of("allowed", allowed));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("allowed", false, "error", e.getMessage()));
        }
    }
}

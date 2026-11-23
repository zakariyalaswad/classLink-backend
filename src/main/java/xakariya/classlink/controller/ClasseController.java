package xakariya.classlink.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xakariya.classlink.domain.model.Classe;
import xakariya.classlink.domain.model.Inscription;
import xakariya.classlink.domain.model.ParametresClasse;
import xakariya.classlink.dto.Dtos.*;
import xakariya.classlink.service.ClasseService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/classes")
public class ClasseController {

    private final ClasseService classeService;

    public ClasseController(ClasseService classeService) {
        this.classeService = classeService;
    }

    @GetMapping
    public ResponseEntity<List<Classe>> getMyClasses(
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(classeService.getClassesPourUtilisateur(userId));
    }

    @PostMapping
    public ResponseEntity<?> createClass(
            @AuthenticationPrincipal(expression = "subject") String userId,
            @RequestBody CreateClassRequest req) {
        try {
            Classe c = classeService.creerClasse(req.nom, req.description, req.matiere, userId);
            return ResponseEntity.ok(c);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getClassById(@PathVariable String id,
                                          @AuthenticationPrincipal(expression = "subject") String userId) {
        classeService.verifierMembre(id, userId);
        return classeService.getClasseById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinClass(
            @AuthenticationPrincipal(expression = "subject") String userId,
            @RequestBody JoinClassRequest req) {
        try {
            Inscription ins = classeService.rejoindreClasseParCode(req.codeAcces, userId);
            return ResponseEntity.ok(ins);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/settings")
    public ResponseEntity<?> updateSettings(
            @PathVariable String id,
            @AuthenticationPrincipal(expression = "subject") String userId,
            @RequestBody UpdateSettingsRequest req) {
        try {
            ParametresClasse p = new ParametresClasse();
            p.setPublicationAnnonces(req.publicationAnnonces);
            p.setCommentaires(req.commentaires);
            p.setCodeAccesActif(req.codeAccesActif);
            Classe updated = classeService.parametrerClasse(id, userId, p);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/regenerate-code")
    public ResponseEntity<?> regenerateCode(
            @PathVariable String id,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        try {
            String newCode = classeService.regenererCodeAcces(id, userId);
            return ResponseEntity.ok(Map.of("codeAcces", newCode));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<?> setArchive(
            @PathVariable String id,
            @AuthenticationPrincipal(expression = "subject") String userId,
            @RequestParam(defaultValue = "true") boolean archivee) {
        try {
            Classe updated = classeService.archiverClasse(id, userId, archivee);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClass(
            @PathVariable String id,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        try {
            classeService.supprimerClasse(id, userId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<?> leaveClass(
            @PathVariable String id,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        try {
            classeService.quitterClasse(id, userId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<Inscription>> getMembers(@PathVariable String id,
                                                        @AuthenticationPrincipal(expression = "subject") String userId) {
        classeService.verifierMembre(id, userId);
        return ResponseEntity.ok(classeService.getMembresClasse(id));
    }
}

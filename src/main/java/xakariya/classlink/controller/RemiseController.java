package xakariya.classlink.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xakariya.classlink.domain.model.Remise;
import xakariya.classlink.dto.Dtos.GradeSubmissionRequest;
import xakariya.classlink.dto.Dtos.SubmitAssignmentRequest;
import xakariya.classlink.service.ClasseService;
import xakariya.classlink.service.PublicationService;
import xakariya.classlink.service.RemiseService;

import java.util.List;
import java.util.Map;

@RestController
public class RemiseController {

    private final RemiseService remiseService;
    private final PublicationService publicationService;
    private final ClasseService classeService;

    public RemiseController(RemiseService remiseService,
                            PublicationService publicationService,
                            ClasseService classeService) {
        this.remiseService = remiseService;
        this.publicationService = publicationService;
        this.classeService = classeService;
    }

    @GetMapping("/api/assignments/{devoirId}/submissions")
    public ResponseEntity<List<Remise>> getSubmissionsForAssignment(
            @PathVariable String devoirId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        classeService.verifierRoleEnseignant(publicationService.getPublicationById(devoirId).getClasseId(), userId);
        return ResponseEntity.ok(remiseService.getRemisesPourDevoir(devoirId));
    }

    @GetMapping("/api/assignments/{devoirId}/my-submission")
    public ResponseEntity<?> getMySubmission(
            @PathVariable String devoirId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        Remise r = remiseService.getRemisePourDevoirEtEtudiant(devoirId, userId);
        return r != null ? ResponseEntity.ok(r) : ResponseEntity.noContent().build();
    }

    @PostMapping("/api/assignments/{devoirId}/submissions")
    public ResponseEntity<?> submitAssignment(
            @PathVariable String devoirId,
            @AuthenticationPrincipal(expression = "subject") String userId,
            @RequestBody SubmitAssignmentRequest req) {
        try {
            Remise remise = remiseService.soumettreRemise(devoirId, userId, req.texte, req.piecesJointes);
            return ResponseEntity.ok(remise);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/submissions/{id}/grade")
    public ResponseEntity<?> gradeSubmission(
            @PathVariable String id,
            @AuthenticationPrincipal(expression = "subject") String teacherId,
            @RequestBody GradeSubmissionRequest req) {
        try {
            Remise remise = remiseService.corrigerEtNoterRemise(id, teacherId, req.note, req.correction);
            return ResponseEntity.ok(remise);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/students/grades")
    public ResponseEntity<List<Remise>> getMyGrades(
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(remiseService.consulterNotesEtCorrections(userId));
    }
}

package xakariya.classlink.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xakariya.classlink.domain.model.Notification;
import xakariya.classlink.service.NotificationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(
            @AuthenticationPrincipal(expression = "subject") String userId,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        if (unreadOnly) {
            return ResponseEntity.ok(notificationService.consulterNotificationsNonLues(userId));
        }
        return ResponseEntity.ok(notificationService.consulterNotifications(userId));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable String id,
                                        @AuthenticationPrincipal(expression = "subject") String userId) {
        notificationService.marquerCommeLu(id, userId);
        return ResponseEntity.ok(Map.of("message", "Notification marquée comme lue"));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<?> markAllAsRead(
            @AuthenticationPrincipal(expression = "subject") String userId) {
        notificationService.marquerToutesCommeLues(userId);
        return ResponseEntity.ok(Map.of("message", "Toutes les notifications ont été marquées comme lues"));
    }
}


package xakariya.classlink.repository;

import xakariya.classlink.domain.model.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    Notification save(Notification notification);
    Optional<Notification> findById(String id);
    List<Notification> findByUtilisateurId(String utilisateurId);
    List<Notification> findByUtilisateurIdAndDateLectureIsNull(String utilisateurId);
    void markAllAsReadForUser(String utilisateurId);
    void deleteById(String id);
}

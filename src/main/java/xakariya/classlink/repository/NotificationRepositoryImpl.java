package xakariya.classlink.repository;

import org.springframework.stereotype.Repository;
import xakariya.classlink.domain.model.Notification;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class NotificationRepositoryImpl extends AbstractRepository<Notification> implements NotificationRepository {

    public NotificationRepositoryImpl(DocumentStore store) {
        super(store, "notifications", Notification.class, Notification::getId, Notification::setId);
    }

    @Override
    public List<Notification> findByUtilisateurId(String utilisateurId) {
        return where("utilisateurId", utilisateurId);
    }

    @Override
    public List<Notification> findByUtilisateurIdAndDateLectureIsNull(String utilisateurId) {
        return findByUtilisateurId(utilisateurId).stream().filter(n -> n.getDateLecture() == null).toList();
    }

    @Override
    public void markAllAsReadForUser(String utilisateurId) {
        LocalDateTime now = LocalDateTime.now();
        for (Notification n : findByUtilisateurIdAndDateLectureIsNull(utilisateurId)) {
            n.setDateLecture(now);
            save(n);
        }
    }
}

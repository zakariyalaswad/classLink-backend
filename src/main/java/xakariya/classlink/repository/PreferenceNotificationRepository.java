package xakariya.classlink.repository;

import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.model.PreferenceNotification;

import java.util.List;
import java.util.Optional;

public interface PreferenceNotificationRepository {
    PreferenceNotification save(PreferenceNotification preference);
    List<PreferenceNotification> findByUtilisateurId(String utilisateurId);
    Optional<PreferenceNotification> findByUtilisateurIdAndType(String utilisateurId, TypeNotification type);
    void deleteById(String id);
}

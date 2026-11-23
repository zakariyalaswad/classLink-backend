package xakariya.classlink.repository;

import org.springframework.stereotype.Repository;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.model.PreferenceNotification;

import java.util.List;
import java.util.Optional;

@Repository
public class PreferenceNotificationRepositoryImpl extends AbstractRepository<PreferenceNotification> implements PreferenceNotificationRepository {

    public PreferenceNotificationRepositoryImpl(DocumentStore store) {
        super(store, "preferences_notification", PreferenceNotification.class, PreferenceNotification::getId, PreferenceNotification::setId);
    }

    @Override
    public List<PreferenceNotification> findByUtilisateurId(String utilisateurId) {
        return where("utilisateurId", utilisateurId);
    }

    @Override
    public Optional<PreferenceNotification> findByUtilisateurIdAndType(String utilisateurId, TypeNotification type) {
        return where("utilisateurId", utilisateurId, "type", type.name()).stream().findFirst();
    }
}

package xakariya.classlink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.model.Notification;
import xakariya.classlink.domain.model.PreferenceNotification;
import xakariya.classlink.repository.NotificationRepository;
import xakariya.classlink.repository.PreferenceNotificationRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final PreferenceNotificationRepository preferenceRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               PreferenceNotificationRepository preferenceRepository) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Dispatch notification respecting user preferences
     */
    public void envoyerNotification(String utilisateurId, String classeId, String nomClasse,
                                    TypeNotification type, String titre, String message, String lienCible) {
        if (utilisateurId == null) return;

        boolean dansApplication = true;
        boolean parEmail = false;

        var prefOpt = preferenceRepository.findByUtilisateurIdAndType(utilisateurId, type);
        if (prefOpt.isPresent()) {
            PreferenceNotification pref = prefOpt.get();
            dansApplication = Boolean.TRUE.equals(pref.getDansApplication());
            parEmail = Boolean.TRUE.equals(pref.getParEmail());
        }

        if (dansApplication) {
            Notification notif = new Notification();
            notif.setUtilisateurId(utilisateurId);
            notif.setClasseId(classeId);
            notif.setNomClasse(nomClasse);
            notif.setType(type);
            notif.setTitre(titre);
            notif.setMessage(message);
            notif.setLienCible(lienCible);
            notif.setDateCreation(LocalDateTime.now());
            notificationRepository.save(notif);
        }

        if (parEmail) {
            log.info("[SIMULATION EMAIL] Vers utilisateur [{}]: Sujet: '{}' - Contenu: '{}'",
                    utilisateurId, titre, message);
        }
    }

    /**
     * Use Case: Consulter ses notifications
     */
    public List<Notification> consulterNotifications(String utilisateurId) {
        return notificationRepository.findByUtilisateurId(utilisateurId);
    }

    public List<Notification> consulterNotificationsNonLues(String utilisateurId) {
        return notificationRepository.findByUtilisateurIdAndDateLectureIsNull(utilisateurId);
    }

    public void marquerCommeLu(String notificationId, String utilisateurId) {
        notificationRepository.findById(notificationId).filter(n -> utilisateurId.equals(n.getUtilisateurId())).ifPresent(n -> {
            n.setDateLecture(LocalDateTime.now());
            notificationRepository.save(n);
        });
    }

    public void marquerToutesCommeLues(String utilisateurId) {
        notificationRepository.markAllAsReadForUser(utilisateurId);
    }
}


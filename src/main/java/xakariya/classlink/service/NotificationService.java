package xakariya.classlink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xakariya.classlink.domain.enums.EmailStatus;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.model.Notification;
import xakariya.classlink.domain.model.PreferenceNotification;
import xakariya.classlink.repository.NotificationRepository;
import xakariya.classlink.repository.PreferenceNotificationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final PreferenceNotificationRepository preferenceRepository;
    private final UserEmailResolver userEmailResolver;
    private final EmailTemplateService emailTemplateService;
    private final SmtpEmailSender smtpEmailSender;
    private final String frontendUrl;
    private final int maxEmailRetries;
    private final String mailTestTo;

    /** Guards against concurrent or duplicate email sends for the same notification ID. */
    private final Set<String> inFlightOrSentIds = ConcurrentHashMap.newKeySet();

    @Autowired
    public NotificationService(NotificationRepository notificationRepository,
                               PreferenceNotificationRepository preferenceRepository,
                               UserEmailResolver userEmailResolver,
                               EmailTemplateService emailTemplateService,
                               SmtpEmailSender smtpEmailSender,
                               @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl,
                               @Value("${app.mail.max-retries:2}") int maxEmailRetries) {
        this(notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, smtpEmailSender, frontendUrl, maxEmailRetries, "");
    }

    public NotificationService(NotificationRepository notificationRepository,
                               PreferenceNotificationRepository preferenceRepository,
                               UserEmailResolver userEmailResolver,
                               EmailTemplateService emailTemplateService,
                               SmtpEmailSender smtpEmailSender,
                               String frontendUrl,
                               int maxEmailRetries,
                               String mailTestTo) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.userEmailResolver = userEmailResolver;
        this.emailTemplateService = emailTemplateService;
        this.smtpEmailSender = smtpEmailSender;
        this.frontendUrl = frontendUrl != null ? frontendUrl.trim() : "http://localhost:4200";
        this.maxEmailRetries = Math.max(1, maxEmailRetries);
        this.mailTestTo = mailTestTo != null ? mailTestTo.trim() : "";
    }

    /**
     * Dispatches an in-app notification and automatically sends an email to the recipient.
     * In-app notifications are persisted in Firebase Realtime Database first and are always
     * preserved even if email delivery fails.
     */
    public Notification envoyerNotification(String utilisateurId, String classeId, String nomClasse,
                                            TypeNotification type, String titre, String message, String lienCible) {
        if (utilisateurId == null || utilisateurId.isBlank()) {
            return null;
        }

        Optional<PreferenceNotification> prefOpt = preferenceRepository.findByUtilisateurIdAndType(utilisateurId, type);
        boolean dansApplication = prefOpt.map(p -> Boolean.TRUE.equals(p.getDansApplication())).orElse(true);
        boolean parEmail = prefOpt.map(p -> Boolean.TRUE.equals(p.getParEmail())).orElse(true);

        if (!dansApplication && !parEmail) {
            return null;
        }

        Notification notif = new Notification();
        notif.setUtilisateurId(utilisateurId);
        notif.setClasseId(classeId);
        notif.setNomClasse(nomClasse != null ? nomClasse : "");
        notif.setType(type);
        notif.setTitre(titre);
        notif.setMessage(message);
        notif.setLienCible(lienCible);
        notif.setDateCreation(LocalDateTime.now());
        notif.setEmailStatus(EmailStatus.PENDING);
        notif.setEmailAttempts(0);

        // 1. Always save the in-app notification in Firebase first
        Notification saved = notificationRepository.save(notif);

        // 2. Automatically trigger email delivery when enabled
        if (parEmail) {
            deliverEmailForNotification(saved.getId());
        }

        return notificationRepository.findById(saved.getId()).orElse(saved);
    }

    /**
     * Delivers (or retries delivering) the email for an existing notification without creating
     * a duplicate in-app notification or sending duplicate emails if already SENT.
     */
    public Notification deliverEmailForNotification(String notificationId) {
        if (notificationId == null || notificationId.isBlank()) {
            return null;
        }

        Optional<Notification> notifOpt = notificationRepository.findById(notificationId);
        if (notifOpt.isEmpty()) {
            return null;
        }

        Notification notif = notifOpt.get();
        if (notif.getEmailStatus() == EmailStatus.SENT) {
            log.debug("Email already SENT for notification [{}], skipping duplicate.", notificationId);
            return notif;
        }

        if (!inFlightOrSentIds.add(notificationId)) {
            log.debug("Email delivery already in progress or completed for notification [{}].", notificationId);
            return notif;
        }

        try {
            // Re-check persisted state inside guard
            notif = notificationRepository.findById(notificationId).orElse(notif);
            if (notif.getEmailStatus() == EmailStatus.SENT) {
                return notif;
            }

            int priorAttempts = notif.getEmailAttempts() != null ? notif.getEmailAttempts() : 0;

            Optional<String> emailOpt = userEmailResolver.resolveEmailByUid(notif.getUtilisateurId());
            if (emailOpt.isEmpty()) {
                notif.setEmailAttempts(priorAttempts + 1);
                notif.setEmailStatus(EmailStatus.FAILED);
                notif.setEmailError("Adresse email du destinataire introuvable ou invalide pour UID " + notif.getUtilisateurId());
                Notification updated = notificationRepository.save(notif);
                inFlightOrSentIds.remove(notificationId);
                log.warn("[EMAIL FAILED] Notification [{}] : destinataire UID [{}] sans adresse email valide.",
                        notificationId, notif.getUtilisateurId());
                return updated;
            }

            String toEmail = emailOpt.get();
            notif.setDestinataireEmail(toEmail);

            String recipientName = userEmailResolver.resolveDisplayNameByUid(notif.getUtilisateurId());
            String subject = emailTemplateService.buildSubject(notif);
            String htmlBody = emailTemplateService.buildHtmlEmail(notif, recipientName, frontendUrl);
            String textBody = emailTemplateService.buildPlainTextEmail(notif, recipientName, frontendUrl);

            Exception lastError = null;
            int attemptsMade = 0;

            for (int attempt = 1; attempt <= maxEmailRetries; attempt++) {
                attemptsMade = priorAttempts + attempt;
                try {
                    smtpEmailSender.sendHtmlEmail(toEmail, subject, htmlBody, textBody);
                    notif.setEmailAttempts(attemptsMade);
                    notif.setEmailStatus(EmailStatus.SENT);
                    notif.setEmailSentAt(LocalDateTime.now());
                    notif.setEmailError(null);
                    Notification sentNotif = notificationRepository.save(notif);
                    log.info("[EMAIL SENT] Notification [{}] envoyée à <{}> (tentative {})",
                            notificationId, toEmail, attemptsMade);
                    return sentNotif;
                } catch (Exception e) {
                    lastError = e;
                    // Do not retry if SMTP is simply unconfigured
                    if (e instanceof IllegalStateException && e.getMessage() != null && e.getMessage().contains("SMTP non configuré")) {
                        break;
                    }
                }
            }

            String sanitizedError = smtpEmailSender.sanitizeErrorMessage(lastError);
            notif.setEmailAttempts(attemptsMade);
            notif.setEmailStatus(EmailStatus.FAILED);
            notif.setEmailError(sanitizedError);
            Notification failedNotif = notificationRepository.save(notif);
            inFlightOrSentIds.remove(notificationId);
            log.warn("[EMAIL FAILED] Notification [{}] vers <{}> après {} tentative(s) : {}",
                    notificationId, toEmail, attemptsMade, sanitizedError);
            return failedNotif;
        } catch (RuntimeException e) {
            inFlightOrSentIds.remove(notificationId);
            throw e;
        }
    }

    /**
     * Retries sending an email for a failed notification belonging to the given user.
     */
    public Notification retryFailedEmail(String notificationId, String userId) {
        Notification notif = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification introuvable: " + notificationId));
        if (!userId.equals(notif.getUtilisateurId())) {
            throw new SecurityException("Accès refusé à cette notification");
        }
        return deliverEmailForNotification(notificationId);
    }

    public List<Notification> consulterNotifications(String utilisateurId) {
        return notificationRepository.findByUtilisateurId(utilisateurId);
    }

    public List<Notification> consulterNotificationsNonLues(String utilisateurId) {
        return notificationRepository.findByUtilisateurIdAndDateLectureIsNull(utilisateurId);
    }

    public void marquerCommeLu(String notificationId, String utilisateurId) {
        notificationRepository.findById(notificationId)
                .filter(n -> utilisateurId.equals(n.getUtilisateurId()))
                .ifPresent(n -> {
                    n.setDateLecture(LocalDateTime.now());
                    notificationRepository.save(n);
                });
    }

    public void marquerToutesCommeLues(String utilisateurId) {
        notificationRepository.markAllAsReadForUser(utilisateurId);
    }

    /**
     * Sends a single SMTP smoke-test email to MAIL_TEST_TO (or the caller's resolved email)
     * without broadcasting to other registered users.
     */
    public Notification sendSmokeTestEmail(String callerUid, String configuredTestTo) {
        String targetEmail = (configuredTestTo != null && !configuredTestTo.isBlank())
                ? configuredTestTo.trim()
                : (!this.mailTestTo.isEmpty() ? this.mailTestTo : userEmailResolver.resolveEmailByUid(callerUid).orElse(""));

        Notification probe = new Notification();
        probe.setUtilisateurId(callerUid);
        probe.setNomClasse("Vérification SMTP");
        probe.setType(TypeNotification.NOUVELLE_ANNONCE);
        probe.setTitre("Test de livraison SMTP ClassLink");
        probe.setMessage("Cet email confirme que la connexion et l'authentification SMTP de ClassLink fonctionnent.");
        probe.setLienCible("/notifications");
        probe.setDateCreation(LocalDateTime.now());
        probe.setDestinataireEmail(targetEmail);
        probe.setEmailAttempts(1);

        if (!userEmailResolver.isValidEmail(targetEmail)) {
            probe.setEmailStatus(EmailStatus.FAILED);
            probe.setEmailError("Destinataire MAIL_TEST_TO manquant ou invalide");
            return probe;
        }

        String recipientName = userEmailResolver.resolveDisplayNameByUid(callerUid);
        String subject = emailTemplateService.buildSubject(probe);
        String htmlBody = emailTemplateService.buildHtmlEmail(probe, recipientName, frontendUrl);
        String textBody = emailTemplateService.buildPlainTextEmail(probe, recipientName, frontendUrl);

        try {
            smtpEmailSender.sendHtmlEmail(targetEmail, subject, htmlBody, textBody);
            probe.setEmailStatus(EmailStatus.SENT);
            probe.setEmailSentAt(LocalDateTime.now());
            probe.setEmailError(null);
            log.info("[SMTP SMOKE TEST SENT] Email de test accepté par le serveur SMTP pour <{}>", targetEmail);
        } catch (Exception e) {
            String sanitized = smtpEmailSender.sanitizeErrorMessage(e);
            probe.setEmailStatus(EmailStatus.FAILED);
            probe.setEmailError(sanitized);
            log.warn("[SMTP SMOKE TEST FAILED] Échec d'envoi vers <{}> : {}", targetEmail, sanitized);
        }
        return probe;
    }
}

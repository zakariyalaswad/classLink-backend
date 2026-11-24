package xakariya.classlink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.model.Notification;
import xakariya.classlink.domain.model.PreferenceNotification;
import xakariya.classlink.domain.model.Utilisateur;
import xakariya.classlink.repository.NotificationRepository;
import xakariya.classlink.repository.PreferenceNotificationRepository;
import xakariya.classlink.repository.UtilisateurRepository;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final PreferenceNotificationRepository preferenceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final String smtpHost;
    private final int smtpPort;
    private final String mailUsername;
    private final String mailPassword;

    public NotificationService(NotificationRepository notificationRepository,
                               PreferenceNotificationRepository preferenceRepository,
                               UtilisateurRepository utilisateurRepository,
                               @Value("${spring.mail.host:smtp.gmail.com}") String smtpHost,
                               @Value("${spring.mail.port:587}") int smtpPort,
                               @Value("${spring.mail.username:}") String mailUsername,
                               @Value("${spring.mail.password:}") String mailPassword) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.smtpHost = smtpHost != null ? smtpHost.trim() : "smtp.gmail.com";
        this.smtpPort = smtpPort;
        this.mailUsername = mailUsername != null ? mailUsername.trim() : "";
        this.mailPassword = mailPassword != null ? mailPassword.trim().replace(" ", "") : "";
    }

    /**
     * Dispatches in-app and email notifications respecting user preferences.
     */
    public void envoyerNotification(String utilisateurId, String classeId, String nomClasse,
                                    TypeNotification type, String titre, String message, String lienCible) {
        if (utilisateurId == null) return;

        boolean dansApplication = true;
        boolean parEmail = type != TypeNotification.NOUVEAU_COMMENTAIRE;

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
            envoyerEmailAsync(utilisateurId, nomClasse, titre, message);
        }
    }

    private void envoyerEmailAsync(String utilisateurId, String nomClasse, String titre, String message) {
        Optional<Utilisateur> destOpt = utilisateurRepository.findById(utilisateurId);
        String toEmail = destOpt.map(Utilisateur::getEmail).orElse("");
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }

        if (mailUsername.isEmpty() || mailPassword.isEmpty()) {
            log.info("[EMAIL] Notification pour <{}> : '{}' — {} (renseignez mail.username et mail.password dans local.properties pour l'envoi SMTP réel)",
                    toEmail, titre, message);
            return;
        }

        String recipientName = destOpt.map(Utilisateur::getNomComplet).orElse("Utilisateur");
        String subject = "[ClassLink" + (nomClasse != null && !nomClasse.isBlank() ? " - " + nomClasse : "") + "] " + titre;
        String body = "Bonjour " + recipientName + ",\r\n\r\n"
                + message + "\r\n\r\n"
                + "Connectez-vous sur ClassLink pour consulter les détails.\r\n\r\n"
                + "— L'équipe ClassLink";

        CompletableFuture.runAsync(() -> {
            try {
                sendSmtpStartTls(toEmail, subject, body);
                log.info("[EMAIL ENVOYÉ] Vers <{}> : '{}'", toEmail, titre);
            } catch (Exception e) {
                log.warn("[EMAIL ÉCHEC] Impossible d'envoyer l'email à <{}> : {}", toEmail, e.getMessage());
            }
        });
    }

    private void sendSmtpStartTls(String toEmail, String subject, String body) throws Exception {
        try (Socket plain = new Socket()) {
            plain.connect(new InetSocketAddress(smtpHost, smtpPort), 6000);
            plain.setSoTimeout(6000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(plain.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(plain.getOutputStream(), StandardCharsets.UTF_8));

            expectReply(reader, "220");
            sendCmd(writer, reader, "EHLO classlink.local", "250");
            sendCmd(writer, reader, "STARTTLS", "220");

            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket tls = (SSLSocket) sslFactory.createSocket(plain, smtpHost, smtpPort, true)) {
                tls.startHandshake();
                BufferedReader tlsReader = new BufferedReader(new InputStreamReader(tls.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter tlsWriter = new BufferedWriter(new OutputStreamWriter(tls.getOutputStream(), StandardCharsets.UTF_8));

                sendCmd(tlsWriter, tlsReader, "EHLO classlink.local", "250");
                sendCmd(tlsWriter, tlsReader, "AUTH LOGIN", "334");
                sendCmd(tlsWriter, tlsReader, Base64.getEncoder().encodeToString(mailUsername.getBytes(StandardCharsets.UTF_8)), "334");
                sendCmd(tlsWriter, tlsReader, Base64.getEncoder().encodeToString(mailPassword.getBytes(StandardCharsets.UTF_8)), "235");
                sendCmd(tlsWriter, tlsReader, "MAIL FROM:<" + mailUsername + ">", "250");
                sendCmd(tlsWriter, tlsReader, "RCPT TO:<" + toEmail + ">", "250");
                sendCmd(tlsWriter, tlsReader, "DATA", "354");

                String encodedSubject = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
                String payload = "From: ClassLink <" + mailUsername + ">\r\n"
                        + "To: <" + toEmail + ">\r\n"
                        + "Subject: " + encodedSubject + "\r\n"
                        + "MIME-Version: 1.0\r\n"
                        + "Content-Type: text/plain; charset=UTF-8\r\n"
                        + "Content-Transfer-Encoding: 8bit\r\n\r\n"
                        + body + "\r\n.";
                sendCmd(tlsWriter, tlsReader, payload, "250");
                sendCmd(tlsWriter, tlsReader, "QUIT", "221");
            }
        }
    }

    private void sendCmd(BufferedWriter writer, BufferedReader reader, String cmd, String expectedPrefix) throws Exception {
        writer.write(cmd + "\r\n");
        writer.flush();
        expectReply(reader, expectedPrefix);
    }

    private void expectReply(BufferedReader reader, String expectedPrefix) throws Exception {
        String line;
        String lastLine = "";
        while ((line = reader.readLine()) != null) {
            lastLine = line;
            if (line.length() < 4 || line.charAt(3) != '-') {
                break;
            }
        }
        if (!lastLine.startsWith(expectedPrefix)) {
            throw new IllegalStateException("SMTP error (attendu " + expectedPrefix + ") : " + lastLine);
        }
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
}

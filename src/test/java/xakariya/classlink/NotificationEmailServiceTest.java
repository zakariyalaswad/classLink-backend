package xakariya.classlink;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import xakariya.classlink.domain.enums.EmailStatus;
import xakariya.classlink.domain.enums.RoleClasse;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.model.Inscription;
import xakariya.classlink.domain.model.Notification;
import xakariya.classlink.domain.model.PreferenceNotification;
import xakariya.classlink.domain.model.Utilisateur;
import xakariya.classlink.repository.*;
import xakariya.classlink.service.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit + embedded-SMTP integration tests covering all 14 notification & email requirements:
 * 1. Notification creation
 * 2. Firebase/DocumentStore persistence
 * 3. Correct recipient UID handling
 * 4. Firebase Authentication email lookup (DB, Inscription fallback, and Firebase Auth UID provider)
 * 5. Successful email sending (including real TCP SMTP server integration test)
 * 6. Missing recipient email
 * 7. Invalid recipient email
 * 8. SMTP authentication failure (and password masking)
 * 9. SMTP connection failure
 * 10. Duplicate email prevention
 * 11. Email status updates (PENDING -> SENT / FAILED)
 * 12. Retry behavior without duplicate in-app notifications
 * 13. Notification persistence when email sending fails
 * 14. Authorization and recipient validation
 */
class NotificationEmailServiceTest {

    private DocumentStore store;
    private NotificationRepository notificationRepository;
    private PreferenceNotificationRepository preferenceRepository;
    private UtilisateurRepository utilisateurRepository;
    private InscriptionRepository inscriptionRepository;
    private UserEmailResolver userEmailResolver;
    private EmailTemplateService emailTemplateService;

    @BeforeEach
    void setUp() {
        store = new TestSupport().documentStore();
        notificationRepository = new NotificationRepositoryImpl(store);
        preferenceRepository = new PreferenceNotificationRepositoryImpl(store);
        utilisateurRepository = new UtilisateurRepositoryImpl(store);
        inscriptionRepository = new InscriptionRepositoryImpl(store);
        userEmailResolver = new UserEmailResolver(utilisateurRepository, inscriptionRepository);
        emailTemplateService = new EmailTemplateService();
    }

    private Utilisateur saveUser(String uid, String prenom, String nom, String email) {
        Utilisateur u = new Utilisateur();
        u.setId(uid);
        u.setPrenom(prenom);
        u.setNom(nom);
        u.setEmail(email);
        return utilisateurRepository.save(u);
    }

    /** Test double for SmtpEmailSender that records sent messages or simulates configurable failures. */
    static class RecordingSmtpSender extends SmtpEmailSender {
        record SentMail(String to, String subject, String html, String text) {}
        final List<SentMail> sentMails = new CopyOnWriteArrayList<>();
        final AtomicInteger sendCalls = new AtomicInteger(0);
        RuntimeException failureToThrow = null;

        RecordingSmtpSender(String password) {
            super(Optional.empty(), "smtp.test.local", 587, "mailer@classlink.test", password, "mailer@classlink.test");
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public void sendHtmlEmail(String toEmail, String subject, String htmlBody, String plainTextBody) {
            sendCalls.incrementAndGet();
            if (failureToThrow != null) {
                throw failureToThrow;
            }
            sentMails.add(new SentMail(toEmail, subject, htmlBody, plainTextBody));
        }
    }

    @Test
    void createsNotificationPersistsInStoreAndSendsFormattedHtmlEmailToCorrectRecipient() {
        saveUser("teacher-1", "Alice", "Durand", "alice.teacher@classlink.test");
        saveUser("student-1", "Bob", "Martin", "bob.student@classlink.test");

        RecordingSmtpSender smtp = new RecordingSmtpSender("secret-app-pwd");
        NotificationService service = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, smtp, "https://classlink.onrender.com", 2
        );

        Notification created = service.envoyerNotification(
                "student-1",
                "class-101",
                "Architecture Logicielle",
                TypeNotification.NOUVEAU_DEVOIR,
                "Nouveau devoir : TP Spring <script>alert(1)</script>",
                "Veuillez rendre le TP avant vendredi & vérifier vos tests.",
                "/classes/class-101?tab=WORK"
        );

        // 1 & 2. Notification created and persisted in DocumentStore
        assertNotNull(created);
        assertNotNull(created.getId());
        Optional<Notification> fromDb = notificationRepository.findById(created.getId());
        assertTrue(fromDb.isPresent());

        // 3 & 5 & 11. Sent to student-1's email (not teacher-1) and status updated to SENT
        assertEquals("student-1", fromDb.get().getUtilisateurId());
        assertEquals(EmailStatus.SENT, fromDb.get().getEmailStatus());
        assertEquals("bob.student@classlink.test", fromDb.get().getDestinataireEmail());
        assertEquals(1, fromDb.get().getEmailAttempts());
        assertNotNull(fromDb.get().getEmailSentAt());
        assertNull(fromDb.get().getEmailError());

        assertEquals(1, smtp.sentMails.size());
        RecordingSmtpSender.SentMail mail = smtp.sentMails.get(0);
        assertEquals("bob.student@classlink.test", mail.to());
        assertEquals("[ClassLink - Architecture Logicielle] Nouveau devoir : TP Spring <script>alert(1)</script>", mail.subject());

        // HTML template checks: branding, escaped HTML, action button URL
        assertTrue(mail.html().contains("ClassLink"));
        assertTrue(mail.html().contains("Bob Martin"));
        assertTrue(mail.html().contains("Architecture Logicielle"));
        assertTrue(mail.html().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(mail.html().contains("<script>alert(1)</script>"));
        assertTrue(mail.html().contains("https://classlink.onrender.com/classes/class-101?tab=WORK"));
    }

    @Test
    void resolvesRecipientEmailViaInscriptionAndFirebaseAuthFallback() {
        // Case A: User record has blank email, but Inscription has emailUtilisateur
        saveUser("uid-ins", "Claire", "Petit", "");
        Inscription ins = new Inscription();
        ins.setUtilisateurId("uid-ins");
        ins.setClasseId("c1");
        ins.setRole(RoleClasse.ETUDIANT);
        ins.setEmailUtilisateur("claire.petit@univ.fr");
        inscriptionRepository.save(ins);

        Optional<String> fromIns = userEmailResolver.resolveEmailByUid("uid-ins");
        assertTrue(fromIns.isPresent());
        assertEquals("claire.petit@univ.fr", fromIns.get());
        // Verify email was backfilled into Utilisateur record
        assertEquals("claire.petit@univ.fr", utilisateurRepository.findById("uid-ins").orElseThrow().getEmail());

        // Case B: User record has no email in DB or Inscription, resolved via Firebase Auth UID lookup provider
        saveUser("uid-fb-auth", "David", "Moreau", null);
        userEmailResolver.setFirebaseAuthEmailProvider(uid ->
                "uid-fb-auth".equals(uid) ? Optional.of("david.moreau@firebase.test") : Optional.empty()
        );

        Optional<String> fromFbAuth = userEmailResolver.resolveEmailByUid("uid-fb-auth");
        assertTrue(fromFbAuth.isPresent());
        assertEquals("david.moreau@firebase.test", fromFbAuth.get());
        assertEquals("david.moreau@firebase.test", utilisateurRepository.findById("uid-fb-auth").orElseThrow().getEmail());
    }

    @Test
    void handlesMissingAndInvalidRecipientEmailWhilePreservingInAppNotification() {
        saveUser("uid-no-email", "Sans", "Email", "");
        saveUser("uid-bad-email", "Bad", "Email", "not-an-email@@bad..com");

        RecordingSmtpSender smtp = new RecordingSmtpSender("pwd");
        NotificationService service = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, smtp, "http://localhost:4200", 2
        );

        // 6. Missing recipient email
        Notification n1 = service.envoyerNotification(
                "uid-no-email", "c1", "Maths", TypeNotification.NOUVELLE_ANNONCE,
                "Annonce 1", "Contenu 1", "/classes/c1"
        );
        assertNotNull(n1);
        assertEquals(EmailStatus.FAILED, n1.getEmailStatus());
        assertNotNull(n1.getEmailError());
        assertEquals(1, notificationRepository.findByUtilisateurId("uid-no-email").size());

        // 7. Invalid recipient email
        Notification n2 = service.envoyerNotification(
                "uid-bad-email", "c1", "Maths", TypeNotification.NOUVELLE_ANNONCE,
                "Annonce 2", "Contenu 2", "/classes/c1"
        );
        assertNotNull(n2);
        assertEquals(EmailStatus.FAILED, n2.getEmailStatus());
        assertEquals(1, notificationRepository.findByUtilisateurId("uid-bad-email").size());
        assertEquals(0, smtp.sendCalls.get());
    }

    @Test
    void handlesSmtpAuthAndConnectionFailuresMasksPasswordAndSupportsRetryWithoutDuplicateNotifications() {
        saveUser("student-retry", "Lucas", "Bernard", "lucas@classlink.test");

        String secretPassword = "SuperSecretGmailAppPassword123";
        RecordingSmtpSender smtp = new RecordingSmtpSender(secretPassword);
        // 8 & 9. Simulate SMTP auth/connection failure containing password in exception message
        smtp.failureToThrow = new IllegalStateException("535 Authentication failed for password " + secretPassword);

        NotificationService service = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, smtp, "http://localhost:4200", 2
        );

        Notification failed = service.envoyerNotification(
                "student-retry", "c1", "Physique", TypeNotification.NOTE_PUBLIEE,
                "Note publiée", "Votre note est 18/20", "/classes/c1?tab=GRADES"
        );

        // 13. In-app notification is preserved even when SMTP fails
        assertNotNull(failed);
        assertEquals(EmailStatus.FAILED, failed.getEmailStatus());
        assertEquals(2, failed.getEmailAttempts()); // retried maxEmailRetries (2) times
        assertNotNull(failed.getEmailError());
        // Password must NEVER appear in stored error message
        assertFalse(failed.getEmailError().contains(secretPassword));
        assertTrue(failed.getEmailError().contains("***"));
        assertEquals(1, service.consulterNotifications("student-retry").size());

        // 14. Authorization check: another user cannot retry this user's notification email
        assertThrows(SecurityException.class, () -> service.retryFailedEmail(failed.getId(), "other-user-uid"));

        // 12. Now SMTP recovers -> retryFailedEmail succeeds without creating a second in-app notification
        smtp.failureToThrow = null;
        Notification retried = service.retryFailedEmail(failed.getId(), "student-retry");
        assertEquals(EmailStatus.SENT, retried.getEmailStatus());
        assertEquals(3, retried.getEmailAttempts());
        assertNull(retried.getEmailError());
        assertNotNull(retried.getEmailSentAt());
        assertEquals(1, smtp.sentMails.size());
        assertEquals(1, service.consulterNotifications("student-retry").size());

        // 10. Duplicate email prevention: calling deliverEmailForNotification again on a SENT notification does not send again
        service.deliverEmailForNotification(failed.getId());
        service.retryFailedEmail(failed.getId(), "student-retry");
        assertEquals(1, smtp.sentMails.size());
    }

    @Test
    void respectsDatabasePreferencesWhenUserDisablesEmailOrAppNotification() {
        saveUser("student-pref", "Emma", "Roux", "emma@classlink.test");
        preferenceRepository.save(new PreferenceNotification(
                null, "student-pref", TypeNotification.NOUVEAU_COMMENTAIRE, true, false
        ));

        RecordingSmtpSender smtp = new RecordingSmtpSender("pwd");
        NotificationService service = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, smtp, "http://localhost:4200", 1
        );

        Notification created = service.envoyerNotification(
                "student-pref", "c1", "Chimie", TypeNotification.NOUVEAU_COMMENTAIRE,
                "Nouveau commentaire", "Bravo !", "/classes/c1"
        );

        assertNotNull(created);
        assertEquals(1, service.consulterNotifications("student-pref").size());
        assertEquals(0, smtp.sentMails.size());
    }

    @Test
    void deliversRealMimeEmailOverEmbeddedTcpSmtpServer() throws Exception {
        saveUser("student-smtp", "Nadia", "Khelifi", "nadia@classlink.test");

        CountDownLatch messageReceived = new CountDownLatch(1);
        StringBuilder rawSmtpTranscript = new StringBuilder();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            Thread smtpServerThread = new Thread(() -> {
                try (Socket client = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
                    out.write("220 localhost ESMTP ClassLinkTestServer\r\n");
                    out.flush();
                    String line;
                    boolean inData = false;
                    while ((line = in.readLine()) != null) {
                        rawSmtpTranscript.append(line).append("\n");
                        if (inData) {
                            if (".".equals(line)) {
                                inData = false;
                                out.write("250 2.0.0 OK: message queued\r\n");
                                out.flush();
                                messageReceived.countDown();
                            }
                            continue;
                        }
                        String upper = line.toUpperCase(Locale.ROOT);
                        if (upper.startsWith("EHLO") || upper.startsWith("HELO")) {
                            out.write("250-localhost\r\n250 OK\r\n");
                            out.flush();
                        } else if (upper.startsWith("MAIL FROM") || upper.startsWith("RCPT TO")) {
                            out.write("250 OK\r\n");
                            out.flush();
                        } else if (upper.startsWith("DATA")) {
                            inData = true;
                            out.write("354 End data with <CR><LF>.<CR><LF>\r\n");
                            out.flush();
                        } else if (upper.startsWith("QUIT")) {
                            out.write("221 Bye\r\n");
                            out.flush();
                            break;
                        } else {
                            out.write("250 OK\r\n");
                            out.flush();
                        }
                    }
                } catch (Exception ignored) {
                }
            });
            smtpServerThread.setDaemon(true);
            smtpServerThread.start();

            JavaMailSenderImpl realJavaMailSender = new JavaMailSenderImpl();
            realJavaMailSender.setHost("localhost");
            realJavaMailSender.setPort(port);
            realJavaMailSender.setDefaultEncoding("UTF-8");

            SmtpEmailSender realSmtpEmailSender = new SmtpEmailSender(
                    Optional.of(realJavaMailSender), "localhost", port, "", "", "noreply@classlink.test"
            );

            NotificationService service = new NotificationService(
                    notificationRepository, preferenceRepository, userEmailResolver,
                    emailTemplateService, realSmtpEmailSender, "http://localhost:4200", 1
            );

            Notification sent = service.envoyerNotification(
                    "student-smtp", "class-42", "Informatique", TypeNotification.NOUVELLE_ANNONCE,
                    "Bienvenue en Informatique", "Le cours commence lundi à 9h.", "/classes/class-42"
            );

            assertTrue(messageReceived.await(5, TimeUnit.SECONDS), "Embedded SMTP server should receive the email");
            assertEquals(EmailStatus.SENT, sent.getEmailStatus());
            assertEquals("nadia@classlink.test", sent.getDestinataireEmail());

            String transcript = rawSmtpTranscript.toString();
            assertTrue(transcript.contains("RCPT TO:<nadia@classlink.test>"));
            assertTrue(transcript.contains("ClassLink"));
            assertTrue(transcript.contains("Bienvenue en Informatique"));
        }
    }
}

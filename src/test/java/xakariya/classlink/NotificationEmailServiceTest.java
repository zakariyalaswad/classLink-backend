package xakariya.classlink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xakariya.classlink.domain.enums.EmailStatus;
import xakariya.classlink.domain.enums.RoleClasse;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.model.Inscription;
import xakariya.classlink.domain.model.Notification;
import xakariya.classlink.domain.model.PreferenceNotification;
import xakariya.classlink.domain.model.Utilisateur;
import xakariya.classlink.repository.*;
import xakariya.classlink.service.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit, mocked-HTTPS, and embedded-HTTP integration tests for ClassLink notifications
 * and Resend HTTPS Email API delivery:
 * 1. Notification creation & DocumentStore persistence
 * 2. Recipient email resolution (Utilisateur DB, Inscription fallback, Firebase Auth UID provider)
 * 3. Successful HTTPS API delivery to https://api.resend.com/emails (port 443)
 * 4. Missing & invalid recipient email handling
 * 5. HTTP error handling: invalid API key (401/403), invalid sender address (local + 422), rate limits (429), 5xx, network/timeout
 * 6. API key / Bearer token sanitization (never leaked in errors or logs)
 * 7. Duplicate-send protection & retry behavior without duplicate notifications
 * 8. Notification persistence when email delivery fails
 * 9. Smoke-test email delivery via Resend (MAIL_TEST_TO)
 * 10. Real Java HttpClient integration test against an embedded HTTP server
 */
class NotificationEmailServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
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

    record CapturedRequest(URI uri, Map<String, String> headers, String jsonBody) {}

    /** Mocked HTTPS transport capturing every request sent to https://api.resend.com/emails. */
    static class MockResendHttpTransport implements ResendEmailSender.HttpTransport {
        final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
        final AtomicInteger callCount = new AtomicInteger(0);
        volatile ResendEmailSender.HttpTransport delegate =
                (uri, headers, body) -> new ResendEmailSender.HttpExchangeResult(200, "{\"id\":\"re_mock_12345\"}");

        @Override
        public ResendEmailSender.HttpExchangeResult execute(URI uri, Map<String, String> headers, String jsonBody)
                throws IOException, InterruptedException {
            callCount.incrementAndGet();
            requests.add(new CapturedRequest(uri, new LinkedHashMap<>(headers), jsonBody));
            return delegate.execute(uri, headers, jsonBody);
        }
    }

    @Test
    void enforcesHttpsEndpointForNonLoopbackHosts() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResendEmailSender("re_test_key_123456", "noreply@classlink.test", "http://api.resend.com/emails", null)
        );
    }

    @Test
    void createsNotificationPersistsInStoreAndDeliversEmailOverHttpsToResendApi() throws Exception {
        saveUser("teacher-1", "Alice", "Durand", "alice.teacher@classlink.test");
        saveUser("student-1", "Bob", "Martin", "bob.student@classlink.test");

        MockResendHttpTransport transport = new MockResendHttpTransport();
        ResendEmailSender sender = new ResendEmailSender(
                "re_live_secretKey123456",
                "noreply@classlink.edu",
                ResendEmailSender.DEFAULT_RESEND_API_URL,
                transport
        );
        NotificationService service = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, sender, "https://classlink.onrender.com", 2
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

        assertNotNull(created);
        assertNotNull(created.getId());
        Optional<Notification> fromDb = notificationRepository.findById(created.getId());
        assertTrue(fromDb.isPresent());

        assertEquals("student-1", fromDb.get().getUtilisateurId());
        assertEquals(EmailStatus.SENT, fromDb.get().getEmailStatus());
        assertEquals("bob.student@classlink.test", fromDb.get().getDestinataireEmail());
        assertEquals(1, fromDb.get().getEmailAttempts());
        assertNotNull(fromDb.get().getEmailSentAt());
        assertNull(fromDb.get().getEmailError());

        assertEquals(1, transport.requests.size());
        CapturedRequest req = transport.requests.get(0);
        assertEquals("https", req.uri().getScheme());
        assertEquals("api.resend.com", req.uri().getHost());
        assertEquals(-1, req.uri().getPort()); // Default HTTPS port 443
        assertEquals("/emails", req.uri().getPath());
        assertEquals("Bearer re_live_secretKey123456", req.headers().get("Authorization"));
        assertEquals("application/json", req.headers().get("Content-Type"));

        JsonNode payload = mapper.readTree(req.jsonBody());
        assertEquals("ClassLink <noreply@classlink.edu>", payload.get("from").asText());
        assertEquals("bob.student@classlink.test", payload.get("to").get(0).asText());
        assertEquals("[ClassLink - Architecture Logicielle] Nouveau devoir : TP Spring <script>alert(1)</script>",
                payload.get("subject").asText());

        String html = payload.get("html").asText();
        String text = payload.get("text").asText();
        assertTrue(html.contains("ClassLink"));
        assertTrue(html.contains("Bob Martin"));
        assertTrue(html.contains("Architecture Logicielle"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("https://classlink.onrender.com/classes/class-101?tab=WORK"));
        assertTrue(text.contains("https://classlink.onrender.com/classes/class-101?tab=WORK"));
    }

    @Test
    void resolvesRecipientEmailViaInscriptionAndFirebaseAuthFallback() {
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
        assertEquals("claire.petit@univ.fr", utilisateurRepository.findById("uid-ins").orElseThrow().getEmail());

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

        MockResendHttpTransport transport = new MockResendHttpTransport();
        ResendEmailSender sender = new ResendEmailSender(
                "re_test_12345678", "ClassLink <noreply@classlink.edu>", ResendEmailSender.DEFAULT_RESEND_API_URL, transport
        );
        NotificationService service = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, sender, "http://localhost:4200", 2
        );

        Notification n1 = service.envoyerNotification(
                "uid-no-email", "c1", "Maths", TypeNotification.NOUVELLE_ANNONCE,
                "Annonce 1", "Contenu 1", "/classes/c1"
        );
        assertNotNull(n1);
        assertEquals(EmailStatus.FAILED, n1.getEmailStatus());
        assertNotNull(n1.getEmailError());
        assertEquals(1, notificationRepository.findByUtilisateurId("uid-no-email").size());

        Notification n2 = service.envoyerNotification(
                "uid-bad-email", "c1", "Maths", TypeNotification.NOUVELLE_ANNONCE,
                "Annonce 2", "Contenu 2", "/classes/c1"
        );
        assertNotNull(n2);
        assertEquals(EmailStatus.FAILED, n2.getEmailStatus());
        assertEquals(1, notificationRepository.findByUtilisateurId("uid-bad-email").size());
        assertEquals(0, transport.callCount.get());
    }

    @Test
    void handlesInvalidApiKeyInvalidSenderRateLimitsAndMasksSecrets() {
        saveUser("student-err", "Sara", "Benali", "sara@classlink.test");

        String secretApiKey = "re_SuperSecretResendKey987654321";
        MockResendHttpTransport transport = new MockResendHttpTransport();

        // A. Invalid API key (HTTP 401) containing key echo in error response -> non-retryable + masked
        transport.delegate = (uri, headers, body) -> new ResendEmailSender.HttpExchangeResult(
                401,
                "{\"statusCode\":401,\"name\":\"invalid_api_key\",\"message\":\"API key " + secretApiKey + " is invalid\"}"
        );
        ResendEmailSender invalidKeySender = new ResendEmailSender(
                secretApiKey, "ClassLink <noreply@classlink.edu>", ResendEmailSender.DEFAULT_RESEND_API_URL, transport
        );
        NotificationService serviceWithBadKey = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, invalidKeySender, "http://localhost:4200", 3
        );

        Notification authFailed = serviceWithBadKey.envoyerNotification(
                "student-err", "c1", "Sécurité", TypeNotification.NOUVELLE_ANNONCE,
                "Annonce Sécurité", "Contenu", "/classes/c1"
        );
        assertEquals(EmailStatus.FAILED, authFailed.getEmailStatus());
        assertEquals(1, authFailed.getEmailAttempts()); // Non-retryable 401 stops after 1 attempt
        assertFalse(authFailed.getEmailError().contains(secretApiKey));
        assertTrue(authFailed.getEmailError().contains("***"));
        assertFalse(invalidKeySender.toString().contains(secretApiKey));

        // B. Invalid sender address (malformed MAIL_FROM) -> non-retryable, zero HTTP calls
        MockResendHttpTransport badFromTransport = new MockResendHttpTransport();
        ResendEmailSender invalidFromSender = new ResendEmailSender(
                secretApiKey, "invalid-sender-format", ResendEmailSender.DEFAULT_RESEND_API_URL, badFromTransport
        );
        NotificationService serviceWithBadFrom = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, invalidFromSender, "http://localhost:4200", 2
        );
        Notification badFromNotif = serviceWithBadFrom.envoyerNotification(
                "student-err", "c1", "Sécurité", TypeNotification.NOUVEAU_COURS,
                "Cours 1", "Support", "/classes/c1"
        );
        assertEquals(EmailStatus.FAILED, badFromNotif.getEmailStatus());
        assertEquals(1, badFromNotif.getEmailAttempts());
        assertEquals(0, badFromTransport.callCount.get());
        assertTrue(badFromNotif.getEmailError().contains("MAIL_FROM"));

        // C. HTTP 422 unverified sender domain from Resend -> non-retryable, notification still persisted
        MockResendHttpTransport unverifiedDomainTransport = new MockResendHttpTransport();
        unverifiedDomainTransport.delegate = (uri, headers, body) -> new ResendEmailSender.HttpExchangeResult(
                422,
                "{\"statusCode\":422,\"name\":\"validation_error\",\"message\":\"The gmail.com domain is not verified.\"}"
        );
        ResendEmailSender unverifiedSender = new ResendEmailSender(
                secretApiKey, "sender@gmail.com", ResendEmailSender.DEFAULT_RESEND_API_URL, unverifiedDomainTransport
        );
        NotificationService serviceUnverified = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, unverifiedSender, "http://localhost:4200", 2
        );
        Notification domainFailed = serviceUnverified.envoyerNotification(
                "student-err", "c1", "Sécurité", TypeNotification.NOUVEAU_COURS,
                "Cours 2", "Support", "/classes/c1"
        );
        assertEquals(EmailStatus.FAILED, domainFailed.getEmailStatus());
        assertEquals(1, domainFailed.getEmailAttempts());
        assertTrue(domainFailed.getEmailError().contains("422"));

        // D. HTTP 429 rate limit on attempt 1 -> automatic retry succeeds on attempt 2!
        MockResendHttpTransport rateLimitTransport = new MockResendHttpTransport();
        AtomicInteger rlAttempt = new AtomicInteger(0);
        rateLimitTransport.delegate = (uri, headers, body) -> {
            if (rlAttempt.incrementAndGet() == 1) {
                return new ResendEmailSender.HttpExchangeResult(
                        429,
                        "{\"statusCode\":429,\"name\":\"rate_limit_exceeded\",\"message\":\"Too many requests\"}"
                );
            }
            return new ResendEmailSender.HttpExchangeResult(200, "{\"id\":\"re_after_ratelimit\"}");
        };
        ResendEmailSender rlSender = new ResendEmailSender(
                secretApiKey, "ClassLink <noreply@classlink.edu>", ResendEmailSender.DEFAULT_RESEND_API_URL, rateLimitTransport
        );
        NotificationService rlService = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, rlSender, "http://localhost:4200", 2
        );
        Notification recoveredFrom429 = rlService.envoyerNotification(
                "student-err", "c1", "Sécurité", TypeNotification.NOTE_PUBLIEE,
                "Note", "19/20", "/classes/c1"
        );
        assertEquals(EmailStatus.SENT, recoveredFrom429.getEmailStatus());
        assertEquals(2, recoveredFrom429.getEmailAttempts());
        assertEquals(2, rateLimitTransport.callCount.get());

        // All 4 notifications remain persisted in the store
        assertEquals(4, notificationRepository.findByUtilisateurId("student-err").size());
    }

    @Test
    void handlesNetworkFailuresSupportsManualRetryAndPreventsDuplicateSends() {
        saveUser("student-retry", "Lucas", "Bernard", "lucas@classlink.test");

        MockResendHttpTransport transport = new MockResendHttpTransport();
        transport.delegate = (uri, headers, body) -> {
            throw new HttpTimeoutException("HTTPS connection timed out");
        };

        ResendEmailSender sender = new ResendEmailSender(
                "re_test_retryKey123", "ClassLink <noreply@classlink.edu>", ResendEmailSender.DEFAULT_RESEND_API_URL, transport
        );
        NotificationService service = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, sender, "http://localhost:4200", 2
        );

        Notification failed = service.envoyerNotification(
                "student-retry", "c1", "Physique", TypeNotification.NOTE_PUBLIEE,
                "Note publiée", "Votre note est 18/20", "/classes/c1?tab=GRADES"
        );

        assertNotNull(failed);
        assertEquals(EmailStatus.FAILED, failed.getEmailStatus());
        assertEquals(2, failed.getEmailAttempts()); // Retried maxEmailRetries (2) times
        assertEquals(2, transport.callCount.get());
        assertNotNull(failed.getEmailError());
        assertEquals(1, service.consulterNotifications("student-retry").size());

        // Authorization check: another user cannot retry this user's notification email
        assertThrows(SecurityException.class, () -> service.retryFailedEmail(failed.getId(), "other-user-uid"));

        // Network recovers (first ConnectException on attempt 1 of retry, then 200 OK on attempt 2!)
        AtomicInteger recoveryStep = new AtomicInteger(0);
        transport.delegate = (uri, headers, body) -> {
            if (recoveryStep.incrementAndGet() == 1) {
                throw new ConnectException("Transient network reset");
            }
            return new ResendEmailSender.HttpExchangeResult(200, "{\"id\":\"re_recovered_789\"}");
        };

        Notification retried = service.retryFailedEmail(failed.getId(), "student-retry");
        assertEquals(EmailStatus.SENT, retried.getEmailStatus());
        assertEquals(4, retried.getEmailAttempts()); // 2 prior + 2 during recovery
        assertNull(retried.getEmailError());
        assertNotNull(retried.getEmailSentAt());
        assertEquals(4, transport.callCount.get());
        assertEquals(1, service.consulterNotifications("student-retry").size());

        // Duplicate email prevention: calling deliverEmailForNotification or retryFailedEmail again does NOT send again
        service.deliverEmailForNotification(failed.getId());
        service.retryFailedEmail(failed.getId(), "student-retry");
        assertEquals(4, transport.callCount.get());
    }

    @Test
    void respectsDatabasePreferencesWhenUserDisablesEmailOrAppNotification() {
        saveUser("student-pref", "Emma", "Roux", "emma@classlink.test");
        preferenceRepository.save(new PreferenceNotification(
                null, "student-pref", TypeNotification.NOUVEAU_COMMENTAIRE, true, false
        ));

        MockResendHttpTransport transport = new MockResendHttpTransport();
        ResendEmailSender sender = new ResendEmailSender(
                "re_test_prefKey123", "noreply@classlink.edu", ResendEmailSender.DEFAULT_RESEND_API_URL, transport
        );
        NotificationService service = new NotificationService(
                notificationRepository, preferenceRepository, userEmailResolver,
                emailTemplateService, sender, "http://localhost:4200", 1
        );

        Notification created = service.envoyerNotification(
                "student-pref", "c1", "Chimie", TypeNotification.NOUVEAU_COMMENTAIRE,
                "Nouveau commentaire", "Bravo !", "/classes/c1"
        );

        assertNotNull(created);
        assertEquals(1, service.consulterNotifications("student-pref").size());
        assertEquals(0, transport.callCount.get());
    }

    @Test
    void deliversRealHttpRequestUsingJavaHttpClientAndSmokeTestEndpoint() throws Exception {
        saveUser("student-http", "Nadia", "Khelifi", "nadia@classlink.test");

        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        AtomicReference<String> capturedContentType = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicInteger serverHits = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            serverHits.incrementAndGet();
            capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] resp = "{\"id\":\"re_integration_ok_42\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            String localApiUrl = "http://127.0.0.1:" + port + "/emails";

            ResendEmailSender realHttpSender = new ResendEmailSender(
                    "re_integrationKey_abcdef",
                    "ClassLink <onboarding@resend.dev>",
                    localApiUrl
            );
            NotificationService service = new NotificationService(
                    notificationRepository, preferenceRepository, userEmailResolver,
                    emailTemplateService, realHttpSender, "https://classlink-app.example.com", 1, "qa@classlink.test"
            );

            // 1. Standard notification email over real HTTP socket
            Notification sent = service.envoyerNotification(
                    "student-http", "class-42", "Informatique", TypeNotification.NOUVELLE_ANNONCE,
                    "Bienvenue en Informatique", "Le cours commence lundi à 9h.", "/classes/class-42"
            );

            assertEquals(EmailStatus.SENT, sent.getEmailStatus());
            assertEquals("nadia@classlink.test", sent.getDestinataireEmail());
            assertEquals(1, serverHits.get());
            assertEquals("Bearer re_integrationKey_abcdef", capturedAuthHeader.get());
            assertEquals("application/json", capturedContentType.get());

            JsonNode firstJson = mapper.readTree(capturedBody.get());
            assertEquals("ClassLink <onboarding@resend.dev>", firstJson.get("from").asText());
            assertEquals("nadia@classlink.test", firstJson.get("to").get(0).asText());
            assertTrue(firstJson.get("html").asText().contains("Bienvenue en Informatique"));

            // 2. Smoke-test email (MAIL_TEST_TO) over real HTTP socket
            Notification smoke = service.sendSmokeTestEmail("student-http", "");
            assertEquals(EmailStatus.SENT, smoke.getEmailStatus());
            assertEquals("qa@classlink.test", smoke.getDestinataireEmail());
            assertEquals(2, serverHits.get());

            JsonNode smokeJson = mapper.readTree(capturedBody.get());
            assertEquals("qa@classlink.test", smokeJson.get("to").get(0).asText());
            assertTrue(smokeJson.get("subject").asText().contains("Resend"));
        } finally {
            server.stop(0);
        }
    }
}

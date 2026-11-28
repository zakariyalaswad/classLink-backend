package xakariya.classlink;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupport.class)
class ClassLinkApiTest {

    @Autowired MockMvc mvc;

    private static RequestPostProcessor as(String uid) {
        return jwt().jwt(j -> j.subject(uid).claim("email", uid + "@mail.test"));
    }

    private String json(String user, String method, String url, String body) throws Exception {
        var req = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        return mvc.perform(req.with(as(user)).contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/classes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/nope").with(as("x"))).andExpect(status().is4xxClientError());
    }

    @Test
    void fullClassroomFlow() throws Exception {
        json("teacher", "POST", "/api/auth/register", "{\"nom\":\"Durand\",\"prenom\":\"Alice\"}");
        json("student", "POST", "/api/auth/register", "{\"nom\":\"Martin\",\"prenom\":\"Bob\"}");
        json("outsider", "POST", "/api/auth/register", "{\"nom\":\"Zed\",\"prenom\":\"Eve\"}");

        mvc.perform(get("/api/auth/me").with(as("teacher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("teacher@mail.test"));

        // class creation + join by code
        String cls = json("teacher", "POST", "/api/classes", "{\"nom\":\"Maths\",\"matiere\":\"Math\"}");
        String classId = JsonPath.read(cls, "$.id");
        String code = JsonPath.read(cls, "$.codeAcces");
        mvc.perform(post("/api/classes/join").with(as("student")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codeAcces\":\"" + code + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ETUDIANT"));
        mvc.perform(post("/api/classes/join").with(as("student")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codeAcces\":\"ZZZZZZ\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/classes").with(as("student"))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/classes/" + classId + "/members").with(as("teacher"))).andExpect(jsonPath("$", hasSize(2)));

        // access control
        mvc.perform(get("/api/classes/" + classId).with(as("outsider"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/classes/" + classId + "/members").with(as("outsider"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/classes/" + classId + "/publications").with(as("outsider"))).andExpect(status().isBadRequest());

        // publications
        mvc.perform(post("/api/classes/" + classId + "/publications").with(as("student")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"DEVOIR\",\"titre\":\"x\",\"contenu\":\"y\"}")).andExpect(status().isBadRequest());
        json("teacher", "POST", "/api/classes/" + classId + "/publications",
                "{\"type\":\"ANNONCE\",\"titre\":\"Bienvenue\",\"contenu\":\"Hello\"}");
        String devoir = json("teacher", "POST", "/api/classes/" + classId + "/publications",
                "{\"type\":\"DEVOIR\",\"titre\":\"TP1\",\"contenu\":\"Exercices\",\"dateLimite\":\"2030-01-15T18:00:00\",\"bareme\":20}");
        String devoirId = JsonPath.read(devoir, "$.id");
        mvc.perform(get("/api/classes/" + classId + "/publications").with(as("student")))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/classes/" + classId + "/publications?type=DEVOIR").with(as("student")))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].dateLimite").value("2030-01-15T18:00:00"));

        // notifications for student (2 publications)
        mvc.perform(get("/api/notifications").with(as("student"))).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/notifications").with(as("teacher"))).andExpect(jsonPath("$", hasSize(0)));

        // comments
        json("student", "POST", "/api/publications/" + devoirId + "/comments", "{\"contenu\":\"Question ?\"}");
        mvc.perform(get("/api/publications/" + devoirId + "/comments").with(as("teacher")))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].nomAuteur", containsString("Martin")));

        // submission + grading
        String rem = json("student", "POST", "/api/assignments/" + devoirId + "/submissions", "{\"texte\":\"Ma reponse\"}");
        String remId = JsonPath.read(rem, "$.id");
        mvc.perform(get("/api/assignments/" + devoirId + "/submissions").with(as("student"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/assignments/" + devoirId + "/submissions").with(as("teacher"))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(put("/api/submissions/" + remId + "/grade").with(as("student")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":20}")).andExpect(status().isBadRequest());
        json("teacher", "PUT", "/api/submissions/" + remId + "/grade", "{\"note\":16.5,\"correction\":\"Bien\"}");
        mvc.perform(get("/api/assignments/" + devoirId + "/my-submission").with(as("student")))
                .andExpect(jsonPath("$.note").value(16.5));
        mvc.perform(get("/api/students/grades").with(as("student"))).andExpect(jsonPath("$", hasSize(1)));

        // notifications read + test-email endpoint (unconfigured Resend key in test profile -> graceful FAILED response, 200 OK)
        mvc.perform(post("/api/notifications/mark-all-read").with(as("student"))).andExpect(status().isOk());
        mvc.perform(get("/api/notifications?unreadOnly=true").with(as("student"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/notifications/test-email").with(as("teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinataireEmail").value("test-recipient@classlink.test"))
                .andExpect(jsonPath("$.emailStatus").value("FAILED"))
                .andExpect(jsonPath("$.emailError", containsString("RESEND_API_KEY")));

        // settings / archive / regenerate code
        json("teacher", "PUT", "/api/classes/" + classId + "/settings", "{\"publicationAnnonces\":\"ENSEIGNANTS\"}");
        mvc.perform(get("/api/classes/" + classId + "/check-announcement-permission").with(as("student")))
                .andExpect(jsonPath("$.allowed").value(false));
        String newCode = JsonPath.read(json("teacher", "POST", "/api/classes/" + classId + "/regenerate-code", null), "$.codeAcces");
        mvc.perform(post("/api/classes/join").with(as("outsider")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codeAcces\":\"" + code + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/classes/" + classId + "/archive").with(as("student"))).andExpect(status().isBadRequest());
        json("teacher", "PUT", "/api/classes/" + classId + "/archive?archivee=true", null);

        // profile + base64 photo + preferences
        json("student", "PUT", "/api/users/profile",
                "{\"prenom\":\"Robert\",\"photoUrl\":\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=\"}");
        mvc.perform(get("/api/users/profile").with(as("student")))
                .andExpect(jsonPath("$.prenom").value("Robert"))
                .andExpect(jsonPath("$.photoUrl", startsWith("data:image/png;base64,")));
        mvc.perform(get("/api/classes/" + classId + "/members").with(as("teacher")))
                .andExpect(jsonPath("$[?(@.utilisateurId=='student')].photoUrlUtilisateur",
                        contains(startsWith("data:image/png;base64,"))));
        mvc.perform(get("/api/users/preferences").with(as("student"))).andExpect(jsonPath("$", hasSize(greaterThan(0))));
        mvc.perform(put("/api/users/preferences").with(as("student")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"NOUVEAU_DEVOIR\",\"dansApplication\":false,\"parEmail\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dansApplication").value(false));

        // student leaves class -> removed from teacher's members list, class still exists for teacher
        mvc.perform(delete("/api/classes/" + classId + "/leave").with(as("teacher"))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/classes/" + classId + "/leave").with(as("student"))).andExpect(status().isOk());
        mvc.perform(get("/api/classes/" + classId + "/members").with(as("teacher"))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/classes").with(as("student"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/classes").with(as("teacher"))).andExpect(jsonPath("$", hasSize(1)));

        // teacher permanently deletes class
        mvc.perform(delete("/api/classes/" + classId).with(as("teacher"))).andExpect(status().isOk());
        mvc.perform(get("/api/classes").with(as("teacher"))).andExpect(jsonPath("$", hasSize(0)));
    }
}

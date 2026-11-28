package xakariya.classlink.service;

import org.springframework.stereotype.Service;
import xakariya.classlink.domain.enums.TypeNotification;
import xakariya.classlink.domain.model.Notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generates responsive, HTML-escaped ClassLink notification emails (HTML + plain-text fallback).
 */
@Service
public class EmailTemplateService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    public String buildSubject(Notification notif) {
        String title = notif.getTitre() != null && !notif.getTitre().isBlank()
                ? notif.getTitre().trim()
                : "Nouvelle notification";
        String className = notif.getNomClasse() != null ? notif.getNomClasse().trim() : "";
        if (!className.isEmpty()) {
            return "[ClassLink - " + className + "] " + title;
        }
        return "[ClassLink] " + title;
    }

    public String buildActionUrl(String frontendBaseUrl, Notification notif) {
        String base = (frontendBaseUrl == null || frontendBaseUrl.isBlank())
                ? "http://localhost:4200"
                : frontendBaseUrl.trim().replaceAll("/+$", "");
        String target = notif.getLienCible();
        if (target == null || target.isBlank()) {
            if (notif.getClasseId() != null && !notif.getClasseId().isBlank()) {
                target = "/classes/" + notif.getClasseId();
            } else {
                target = "/notifications";
            }
        } else if (!target.startsWith("/") && !target.startsWith("http")) {
            target = "/" + target;
        }
        return target.startsWith("http") ? target : (base + target);
    }

    public String buildHtmlEmail(Notification notif, String recipientName, String frontendBaseUrl) {
        String safeRecipient = escapeHtml(recipientName != null && !recipientName.isBlank() ? recipientName : "Utilisateur");
        String safeTitle = escapeHtml(notif.getTitre() != null && !notif.getTitre().isBlank() ? notif.getTitre() : "Notification ClassLink");
        String safeMessage = escapeHtml(notif.getMessage() != null ? notif.getMessage() : "").replace("\n", "<br/>");
        String safeClass = escapeHtml(notif.getNomClasse() != null ? notif.getNomClasse().trim() : "");
        String safeBadge = escapeHtml(typeLabel(notif.getType()));
        LocalDateTime created = notif.getDateCreation() != null ? notif.getDateCreation() : LocalDateTime.now();
        String safeDate = escapeHtml(created.format(DATE_FORMATTER));
        String safeActionUrl = escapeHtml(buildActionUrl(frontendBaseUrl, notif));

        String classBlock = safeClass.isEmpty()
                ? ""
                : "<p style=\"margin:0 0 8px 0;font-size:12px;font-weight:700;color:#2454d6;text-transform:uppercase;letter-spacing:0.6px;\">"
                  + "Classe : " + safeClass + "</p>";

        return "<!DOCTYPE html>"
                + "<html lang=\"fr\">"
                + "<head><meta charset=\"UTF-8\"/><meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"/>"
                + "<title>" + safeTitle + "</title></head>"
                + "<body style=\"margin:0;padding:0;background-color:#f5f7fb;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#182033;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background-color:#f5f7fb;padding:28px 14px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:580px;background-color:#ffffff;border-radius:18px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 4px 14px rgba(24,32,51,0.06);\">"
                // Header
                + "<tr><td style=\"background:linear-gradient(90deg,#2454d6,#3b6ef5);padding:22px 28px;color:#ffffff;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\"><tr>"
                + "<td><span style=\"display:inline-block;width:30px;height:30px;line-height:30px;text-align:center;border-radius:8px;background-color:#ffffff;color:#2454d6;font-weight:800;font-size:16px;margin-right:10px;\">C</span>"
                + "<span style=\"font-size:20px;font-weight:800;vertical-align:middle;letter-spacing:-0.3px;\">ClassLink</span></td>"
                + "<td align=\"right\"><span style=\"display:inline-block;background-color:rgba(255,255,255,0.18);color:#ffffff;font-size:11px;font-weight:700;padding:4px 10px;border-radius:999px;text-transform:uppercase;\">"
                + safeBadge + "</span></td>"
                + "</tr></table>"
                + "</td></tr>"
                // Body
                + "<tr><td style=\"padding:28px;\">"
                + "<p style=\"margin:0 0 14px 0;font-size:14px;color:#475569;\">Bonjour <strong>" + safeRecipient + "</strong>,</p>"
                + classBlock
                + "<h1 style=\"margin:0 0 14px 0;font-size:19px;line-height:1.35;font-weight:800;color:#182033;\">" + safeTitle + "</h1>"
                + "<div style=\"background-color:#f8faff;border-left:4px solid #2454d6;border-radius:10px;padding:16px 18px;margin:0 0 20px 0;font-size:14px;line-height:1.6;color:#334155;\">"
                + safeMessage
                + "</div>"
                + "<p style=\"margin:0 0 22px 0;font-size:12px;color:#64748b;\">Date : " + safeDate + "</p>"
                + "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\"><tr>"
                + "<td style=\"border-radius:12px;background-color:#2454d6;\">"
                + "<a href=\"" + safeActionUrl + "\" target=\"_blank\" rel=\"noopener\" "
                + "style=\"display:inline-block;padding:12px 24px;font-size:13px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:12px;\">"
                + "Ouvrir dans ClassLink</a>"
                + "</td></tr></table>"
                + "</td></tr>"
                // Footer
                + "<tr><td style=\"background-color:#f8fafc;border-top:1px solid #e2e8f0;padding:16px 28px;text-align:center;font-size:11px;color:#94a3b8;line-height:1.5;\">"
                + "Cet email a été envoyé automatiquement par ClassLink suite à une notification dans votre espace.<br/>"
                + "Vous pouvez gérer vos préférences de notification depuis les paramètres de votre compte ClassLink."
                + "</td></tr>"
                + "</table>"
                + "</td></tr></table>"
                + "</body></html>";
    }

    public String buildPlainTextEmail(Notification notif, String recipientName, String frontendBaseUrl) {
        String name = recipientName != null && !recipientName.isBlank() ? recipientName : "Utilisateur";
        String title = notif.getTitre() != null ? notif.getTitre() : "Notification ClassLink";
        String message = notif.getMessage() != null ? notif.getMessage() : "";
        String className = notif.getNomClasse() != null && !notif.getNomClasse().isBlank()
                ? "Classe : " + notif.getNomClasse().trim() + "\n"
                : "";
        LocalDateTime created = notif.getDateCreation() != null ? notif.getDateCreation() : LocalDateTime.now();
        String dateStr = created.format(DATE_FORMATTER);
        String actionUrl = buildActionUrl(frontendBaseUrl, notif);

        return "Bonjour " + name + ",\n\n"
                + className
                + title + "\n"
                + "----------------------------------------\n"
                + message + "\n"
                + "----------------------------------------\n"
                + "Date : " + dateStr + "\n\n"
                + "Ouvrir dans ClassLink : " + actionUrl + "\n\n"
                + "— L'équipe ClassLink";
    }

    public static String escapeHtml(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private String typeLabel(TypeNotification type) {
        if (type == null) return "Notification";
        return switch (type) {
            case NOUVELLE_ANNONCE -> "Annonce";
            case NOUVEAU_COURS -> "Cours";
            case NOUVEAU_DEVOIR -> "Devoir";
            case RAPPEL_ECHEANCE -> "Échéance";
            case NOUVEAU_COMMENTAIRE -> "Commentaire";
            case NOUVELLE_REMISE -> "Remise";
            case NOTE_PUBLIEE -> "Note publiée";
        };
    }
}

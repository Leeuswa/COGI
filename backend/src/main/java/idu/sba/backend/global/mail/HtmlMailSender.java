
package idu.sba.backend.global.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HtmlMailSender {

    private final JavaMailSender mailSender;
    @Value("${spring.mail.username}") private String from;

    // inner(본문 HTML)만 받아 공용 브랜드 껍데기로 감싸 발송
    public void send(String to, String subject, String innerHtml) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(wrap(innerHtml), true); // true = HTML
            mailSender.send(msg);
        } catch (MessagingException e) {
            // 호출부가 try/catch로 무시하므로 언체크로 던짐
            throw new IllegalStateException("메일 발송 실패", e);
        }
    }


    private String wrap(String inner) {
        return """
        <div style="margin:0;padding:32px 12px;background:#cfe8ff;font-family:'Courier New',monospace;">
          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;margin:0 auto;">
            <tr><td style="background:#1b2a4a;padding:18px 22px;">
              <span style="color:#ffd23f;font-size:22px;font-weight:bold;letter-spacing:2px;">🥚 COGI</span>
            </td></tr>
            <tr><td style="background:#fdfcf7;border:3px solid #1b2a4a;border-top:none;padding:28px 22px;box-shadow:5px 5px 0 #1b2a4a;">
              %s
            </td></tr>
            <tr><td style="padding:16px 4px;color:#40507a;font-size:11px;line-height:1.6;">
              이 메일은 COGI에서 발송되었습니다. 요청하지 않았다면 무시하세요.
            </td></tr>
          </table>
        </div>
        """.formatted(inner);
    }
}
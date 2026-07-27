package com.ecotrack.ecotrack_backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetLink = frontendBaseUrl + "/reset-password?token=" + token;

        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto;">
                    <h2>Reset your EcoTrack password</h2>
                    <p>We received a request to reset your password. Click the button below to choose a new one. This link expires in 15 minutes.</p>
                    <p style="margin: 24px 0;">
                        <a href="%s"
                           style="background-color: #2e7d32; color: #ffffff; padding: 12px 24px;
                                  text-decoration: none; border-radius: 4px; display: inline-block;">
                            Reset Password
                        </a>
                    </p>
                    <p>If the button doesn't work, copy and paste this link into your browser:</p>
                    <p><a href="%s">%s</a></p>
                    <p style="color: #777; font-size: 13px;">If you didn't request this, you can safely ignore this email.</p>
                </div>
                """.formatted(resetLink, resetLink, resetLink);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(toEmail);
            helper.setSubject("EcoTrack — Reset your password");
            helper.setText(html, true); // true = this is HTML, not plain text
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new RuntimeException("Failed to build password reset email", ex);
        }
    }
}
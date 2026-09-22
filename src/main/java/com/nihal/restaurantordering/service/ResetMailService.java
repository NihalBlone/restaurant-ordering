package com.nihal.restaurantordering.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResetMailService {
    private final ObjectProvider<JavaMailSender> mailSender;
    @Value("${app.mail.enabled:false}") private boolean enabled;
    @Value("${app.mail.from}") private String from;
    @Value("${app.customer-base-url}") private String baseUrl;

    public void send(String email, String token) {
        if (!enabled) return;
        var mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(email);
        mail.setSubject("Set your Tableside password");
        mail.setText("Use this single-use link to set your password:\n" + baseUrl.replaceAll("/+$", "")
                + "/admin/reset-password?token=" + token + "\nIf you did not request this, ignore this email.");
        mailSender.getObject().send(mail);
    }
}

package santediagnosticsltd;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Properties;

public class EmailService {

    /**
     * WHY CONFIG FILE?
     * Hardcoding credentials in source code is bad practice — anyone who
     * reads the code sees your password. Storing them in config.properties
     * means you can change them without touching the code, and you can
     * exclude the file from version control (e.g. .gitignore).
     *
     * HOW IT WORKS:
     * getResourceAsStream() looks for config.properties on the classpath.
     * NetBeans copies src/config.properties into the build/classes folder
     * automatically, so it's always available at runtime.
     */
    private static Properties loadConfig() {
        Properties config = new Properties();
        try (InputStream input = EmailService.class
                .getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input != null) {
                config.load(input);
            } else {
                System.err.println("[MAIL SYSTEM] config.properties not found on classpath.");
            }
        } catch (Exception e) {
            System.err.println("[MAIL SYSTEM] Failed to load config: " + e.getMessage());
        }
        return config;
    }

    /**
     * Core send method — used by both sendResultReadyEmail and sendWelcomeEmail.
     * Reads SMTP settings from config.properties each time it's called,
     * so changes to the file take effect without recompiling.
     */
    private static boolean sendEmail(String to, String subject, String body) {
        Properties config = loadConfig();

        String senderEmail    = config.getProperty("mail.sender",   "your-lab-email@gmail.com");
        String senderPassword = config.getProperty("mail.password", "your-app-password");
        String smtpHost       = config.getProperty("mail.host",     "smtp.gmail.com");
        String smtpPort       = config.getProperty("mail.port",     "587");
        
      

        Properties mailProps = new Properties();
        mailProps.put("mail.smtp.auth",            "true");
        mailProps.put("mail.smtp.starttls.enable", "true");
        mailProps.put("mail.smtp.host",            smtpHost);
        mailProps.put("mail.smtp.port",            smtpPort);
        mailProps.put("mail.smtp.ssl.protocols",   "TLSv1.2");

        jakarta.mail.Session mailSession = jakarta.mail.Session.getInstance(mailProps,
            new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(senderEmail, senderPassword);
                }
            });

        try {
            Message message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(senderEmail, "Sante Diagnostics"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);
            System.out.println("[MAIL SYSTEM] ✔ Email sent to: " + to);
            return true;
        } catch (Exception e) {
            System.out.println("[MAIL SYSTEM] ❌ Email failed: " + e.getMessage());
            System.out.println("[MAIL SYSTEM] [DEVELOPER MODE FALLBACK] Printing to terminal:");
            System.out.println("   >>> To:      " + to);
            System.out.println("   >>> Subject: " + subject);
            return false;
        }
    }

    /**
     * Sent to patients when their validated lab result is ready.
     * Called by LabAttendantDashboardController after result validation.
     */
    public static boolean sendResultReadyEmail(String recipientEmail,
                                                String customerName,
                                                String testName) {
        String subject = "Your Sante Diagnostics Lab Report is Ready! 🎉";
        String body =
            "Dear " + customerName + ",\n\n" +
            "Good news! Your laboratory results for '" + testName +
            "' are complete and have been validated.\n\n" +
            "Please log into your Sante Diagnostics portal dashboard " +
            "to view details or download your PDF report.\n\n" +
            "Best regards,\n" +
            "Sante Diagnostics Team.";
        return sendEmail(recipientEmail, subject, body);
    }

    /**
     * Sent to newly created staff or customer accounts.
     * Called by SuperAdminDashboardController and LabAttendantDashboardController.
     */
    public static boolean sendWelcomeEmail(String recipientEmail,
                                            String firstName,
                                            String tempPassword) {
        String subject = "Welcome to Sante Diagnostics — Your Account is Ready";
        String body =
            "Dear " + firstName + ",\n\n" +
            "An account has been created for you on the " +
            "Sante Diagnostics Laboratory Information System.\n\n" +
            "Your login credentials:\n" +
            "  Email:              " + recipientEmail + "\n" +
            "  Temporary Password: " + tempPassword   + "\n\n" +
            "IMPORTANT: You will be required to change your password on your first login.\n\n" +
            "Please keep this information confidential.\n\n" +
            "Best regards,\n" +
            "Sante Diagnostics Administration.";
        return sendEmail(recipientEmail, subject, body);
    }
}

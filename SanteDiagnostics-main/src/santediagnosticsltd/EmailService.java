package santediagnosticsltd;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

public class EmailService {

    // Configure your laboratory SMTP credentials here
    private static final String SMTP_HOST = "smtp.gmail.com"; 
    private static final String SMTP_PORT = "587";
    private static final String SENDER_EMAIL = "your-lab-email@gmail.com";
    private static final String SENDER_PASSWORD = "your-app-password"; // App Password if using Gmail 2FA

    /**
     * Dispatches an automatic email notification to patients when their reports are validated.
     */
    public static boolean sendResultReadyEmail(String recipientEmail, String customerName, String testName) {
        
        // Setup configuration parameters for the mail delivery server
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");

        // Use fully qualified naming for jakarta.mail.Session to avoid conflict with santediagnosticsltd.Session
        jakarta.mail.Session mailSession = jakarta.mail.Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(SENDER_EMAIL, "Sante Diagnostics"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject("Your Sante Diagnostics Lab Report is Ready! 🎉");

            String body = "Dear " + customerName + ",\n\n"
                    + "Good news! Your laboratory results for '" + testName + "' are complete and have been validated.\n\n"
                    + "Please log into your Sante Diagnostics portal dashboard to view details or download your PDF report.\n\n"
                    + "Best regards,\n"
                    + "Sante Diagnostics Team.";
            
            message.setText(body);

            // Execute delivery over internet protocols
            Transport.send(message);
            System.out.println("[MAIL SYSTEM] ✔ Email notification successfully delivered to: " + recipientEmail);
            return true;

        } catch (Exception e) {
            // Graceful fallback logging so your local program doesn't crash if the PC is offline during testing
            System.out.println("[MAIL SYSTEM] ❌ Jakarta Mail delivery skipped/failed: " + e.getMessage());
            System.out.println("[MAIL SYSTEM] [DEVELOPER MODE FALLBACK] Printing output details to terminal:");
            System.out.println("   >>> Notification target user: " + recipientEmail);
            System.out.println("   >>> Target Report completed: " + testName);
            return false;
        }
    }
}
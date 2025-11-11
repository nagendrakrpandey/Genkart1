package Tech_Nagendra.Certificates_genration.Service;

import Tech_Nagendra.Certificates_genration.Entity.Report;
import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import Tech_Nagendra.Certificates_genration.Repository.ReportRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmailSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(EmailSchedulerService.class);

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${mail.test.mode:true}")
    private boolean testMode;

    @Value("${mail.test.recipient:}")
    private String testRecipient;

    @Value("${mail.from:no-reply@example.com}")
    private String mailFrom;

    private volatile LocalDateTime lastRunAt;
    private volatile String lastRunStatus;
    private volatile String lastRunError;

    // Runs every month on 13th at 9:01 AM
    @Scheduled(cron = "0 1 9 13 * *")
    public void sendMonthlyReports() {
        lastRunAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        lastRunStatus = "STARTED";
        lastRunError = null;
        try {
            LocalDate startDate = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusMonths(1).withDayOfMonth(1);
            LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(endDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

            List<Report> allReports = reportRepository.findAll();

            Map<UserProfile, List<Report>> grouped = allReports.stream()
                    .filter(r -> r.getGeneratedOn() != null
                            && !r.getGeneratedOn().before(start)
                            && !r.getGeneratedOn().after(end)
                            && r.getGeneratedBy() != null)
                    .collect(Collectors.groupingBy(Report::getGeneratedBy));

            if (grouped.isEmpty()) {
                lastRunStatus = "NO_DATA";
                log.info("No reports found between {} and {}", startDate, endDate);
                return;
            }

            for (Map.Entry<UserProfile, List<Report>> entry : grouped.entrySet()) {
                UserProfile user = entry.getKey();
                List<Report> reports = entry.getValue();
                int count = reports.size();

                try {
                    Long userId = user.getId();
                    String username = user.getUsername() != null ? user.getUsername() : "User_" + userId;
                    String email = user.getEmail() != null ? user.getEmail() : ("user" + userId + "@example.com");

                    String filePath = generateInvoice(userId, count, startDate, endDate);
                    boolean sent = sendEmailWithAttachment(email, username, count, filePath);

                    if (sent)
                        log.info("Email sent successfully for userId={} ({})", userId, email);
                    else
                        log.info("Test mode: email not sent for userId={} ({})", userId, email);


                    try {
                        File f = new File(filePath);
                        if (f.exists()) f.delete();
                    } catch (Exception ignore) {}

                } catch (Exception e) {
                    log.error("Error sending mail to user {} : {}", user.getId(), e.getMessage(), e);
                }
            }

            lastRunStatus = "SUCCESS";
        } catch (Exception e) {
            lastRunStatus = "FAILED";
            lastRunError = e.getMessage();
            log.error("Monthly report failed at {} : {}", lastRunAt, e.getMessage(), e);
        }
    }


    private String generateInvoice(Long userId, int count, LocalDate start, LocalDate end) throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        DateTimeFormatter fileFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = now.format(fileFormatter);
        String monthYear = start.getMonthValue() + "_" + start.getYear();
        String folderPath = "C:/certificate_storage/invoices/";
        File folder = new File(folderPath);
        if (!folder.exists()) folder.mkdirs();

        String fileName = folderPath + "invoice_" + userId + "_" + monthYear + "_" + timestamp + ".pdf";

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(fileName));
        document.open();

        document.add(new Paragraph("Certificate Generation Invoice"));
        document.add(new Paragraph("Generated On: " + now.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")) + " (IST)"));
        document.add(new Paragraph("User ID: " + userId));
        document.add(new Paragraph("Period: " + start + " to " + end));
        document.add(new Paragraph("Certificates Generated: " + count));
        document.add(new Paragraph("Amount: ₹" + (count * 5)));
        document.add(new Paragraph("\nThank you for using our service!"));

        document.close();
        return fileName;
    }

    private boolean sendEmailWithAttachment(String toEmail, String username, int count, String filePath) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        String monthYear = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                .minusMonths(1)
                .format(DateTimeFormatter.ofPattern("MMMM yyyy"));

        helper.setFrom(mailFrom);
        helper.setSubject("Monthly Certificate Summary & Invoice - " + monthYear);

        String body = "<p>Dear " + username + ",</p>"
                + "<p>We hope this message finds you well.</p>"
                + "<p>You generated <strong>" + count + "</strong> certificates during <strong>" + monthYear + "</strong>.</p>"
                + "<p>Please find the attached invoice for your records.</p>"
                + "<br><p>Warm regards,<br><strong>Certificate Generation Team</strong></p>";

        helper.setText(body, true);

        File attachment = new File(filePath);
        if (attachment.exists()) {
            helper.addAttachment(attachment.getName(), attachment);
        } else {
            log.warn("Attachment not found: {}", filePath);
        }

        if (testMode) {
            if (testRecipient != null && !testRecipient.isBlank()) {
                helper.setTo(testRecipient);
                mailSender.send(message);
                return true;
            } else {
                log.info("Test mode: email not sent to {}", toEmail);
                return false;
            }
        } else {
            helper.setTo(toEmail);
            mailSender.send(message);
            return true;
        }
    }
}

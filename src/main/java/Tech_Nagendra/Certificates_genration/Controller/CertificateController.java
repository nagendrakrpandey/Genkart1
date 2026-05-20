package Tech_Nagendra.Certificates_genration.Controller;
import Tech_Nagendra.Certificates_genration.Dto.ProgressDTO;
import Tech_Nagendra.Certificates_genration.Entity.CandidateDTO;
import Tech_Nagendra.Certificates_genration.Entity.Report;
import Tech_Nagendra.Certificates_genration.Entity.Template;
import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import Tech_Nagendra.Certificates_genration.Repository.TemplateRepository;
import Tech_Nagendra.Certificates_genration.Security.UserPrincipal;
import Tech_Nagendra.Certificates_genration.Service.CertificateService;
import Tech_Nagendra.Certificates_genration.Service.DynamicFontService;
import Tech_Nagendra.Certificates_genration.Service.ReportService;
import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
import Tech_Nagendra.Certificates_genration.Utility.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/certificates")
@CrossOrigin(origins = "http://localhost:8081", allowCredentials = "true")
public class CertificateController {
    private static final Logger logger = LoggerFactory.getLogger(CertificateController.class);
    private final CertificateService certificateService;
    private final ReportService reportService;
    private final ProfileRepository profileRepository;
    private final JwtUtil jwtUtil;
    private final DynamicFontService dynamicFontService;
    TemplateRepository templateRepository;

    @Value("${certificate.template.path:${user.dir}/templates/}")
    private String tempPath;

    public CertificateController(
            CertificateService certificateService,
            ReportService reportService,
            ProfileRepository profileRepository,
            JwtUtil jwtUtil,
            DynamicFontService dynamicFontService,
            TemplateRepository templateRepository
    ) {
        this.certificateService = certificateService;
        this.reportService = reportService;
        this.profileRepository = profileRepository;
        this.jwtUtil = jwtUtil;
        this.dynamicFontService = dynamicFontService;
        this.templateRepository = templateRepository;
    }


    @GetMapping("/preview/{templateId}")
    public ResponseEntity<byte[]> previewCertificate(
            @PathVariable Long templateId,
            @RequestHeader("Authorization") String tokenHeader,
            @RequestParam(value = "generatedByUserId", required = false) Long generatedByUserId
    ) {
        try {

            // ================= TOKEN EXTRACT =================
            String token = tokenHeader.startsWith("Bearer ")
                    ? tokenHeader.substring(7)
                    : tokenHeader;

            Long loggedInUserId = jwtUtil.extractUserId(token);

            UserProfile loggedInUser = profileRepository
                    .findById(loggedInUserId)
                    .orElseThrow(() -> new RuntimeException("Logged-in user not found"));

            // =================  SECURITY CHECK =================
            if (generatedByUserId != null &&
                    !"ADMIN".equalsIgnoreCase(loggedInUser.getRole())) {
                throw new RuntimeException("Unauthorized access");
            }

            // =================  FINAL USER DECISION =================
            UserProfile finalUser;

            if (generatedByUserId != null) {
                finalUser = profileRepository.findById(generatedByUserId)
                        .orElseThrow(() -> new RuntimeException("Selected user not found"));
            } else {
                finalUser = loggedInUser;
            }

            // ================= CREATE USER PRINCIPAL =================
            UserPrincipal currentUser = new UserPrincipal(finalUser);

            // ================= GENERATE PREVIEW =================
            byte[] pdfBytes = certificateService.previewTemplate(templateId, currentUser);

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=preview.pdf")
                    .body(pdfBytes);

        } catch (Exception e) {
            logger.error("Preview generation failed", e);
            String rootCause = getRootCauseMessage(e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Preview failed: " + rootCause).getBytes());
        }
    }

    // ======================= GENERATE ZIP =======================

    @PostMapping(
            value = "/generate-zip/{templateId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )

    public ResponseEntity<?> generateCertificatesZip(
            @PathVariable Long templateId,
            @RequestPart("excel") MultipartFile excelFile,
            @RequestParam(value = "generatedByUserId", required = false) Long generatedByUserId,
            @RequestPart(value = "zipImage", required = false) MultipartFile zipImage,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestPart(value = "sign", required = false) MultipartFile sign,
            @RequestPart(value = "sign2", required = false) MultipartFile sign2,
            @RequestPart(value = "sign3", required = false) MultipartFile sign3,
            @RequestPart(value = "sign4", required = false) MultipartFile sign4,
            @RequestPart(value = "img1", required = false) MultipartFile img1,
            @RequestPart(value = "img2", required = false) MultipartFile img2,
            @RequestPart(value = "img3", required = false) MultipartFile img3,
            @RequestPart(value = "img4", required = false) MultipartFile img4,
            @RequestPart(value = "img5", required = false) MultipartFile img5,
            @RequestPart(value = "img6", required = false) MultipartFile img6,
            @RequestPart(value = "img7", required = false) MultipartFile img7,
            @RequestPart(value = "img8", required = false) MultipartFile img8,
            @RequestPart(value = "img9", required = false) MultipartFile img9,
            @RequestPart(value = "img10", required = false) MultipartFile img10,
            @RequestPart(value = "img11", required = false) MultipartFile img11,
            @RequestPart(value = "img12", required = false) MultipartFile img12,
            @RequestPart(value = "img13", required = false) MultipartFile img13,
            @RequestPart(value = "img14", required = false) MultipartFile img14,
            @RequestPart(value = "img15", required = false) MultipartFile img15,
            @RequestHeader("Authorization") String tokenHeader
    ) {
        File tempExcel = null;
        Map<String, File> uploadedFiles = new HashMap<>();
        File dir = new File(tempPath);
        try {
            if (excelFile == null || excelFile.isEmpty()) {
                throw new RuntimeException("Excel file is required");
            }

            // ================= AUTH =================
            String token = tokenHeader.startsWith("Bearer ")
                    ? tokenHeader.substring(7)
                    : tokenHeader;

            Long loggedInUserId = jwtUtil.extractUserId(token);

            UserProfile loggedInUser = profileRepository.findById(loggedInUserId)
                    .orElseThrow();
            UserProfile finalUser;

            if (generatedByUserId != null) {
                // Admin selected user
                finalUser = profileRepository.findById(generatedByUserId)
                        .orElseThrow(() -> new RuntimeException("Selected user not found"));
            } else {
                // No user selected → use admin
                finalUser = loggedInUser;
            }

            UserPrincipal currentUser = new UserPrincipal(finalUser);

            // ================= TEMP DIR =================
            if (!dir.exists()) dir.mkdirs();

            // ================= SAVE EXCEL =================
            tempExcel = new File(
                    dir,
                    System.currentTimeMillis() + "_" + excelFile.getOriginalFilename()
            );
            excelFile.transferTo(tempExcel);


            Template template = templateRepository
                    .findById(templateId)
                    .orElseThrow(() -> new RuntimeException("Template not found"));

            int imageType = template.getImageType();

// ================= TYPE 1 VALIDATION =================
            if (imageType == 1) {

                if (zipImage == null || zipImage.isEmpty()) {
                    throw new RuntimeException("ZIP image file is required for Image Type 1");
                }

                boolean hasImages =     img1 != null || img2 != null || img3 != null ||
                                        img4 != null || img5 != null || img6 != null ||
                                        img7 != null || img8 != null || img9 != null ||
                                        img10 != null || img11 != null || img12 != null ||
                                        img13 != null || img14 != null || img15 != null;
                if (hasImages) {
                    throw new RuntimeException("PDF/Image uploads not allowed for Image Type 1");
                }
            }



// ================= TYPE 5 VALIDATION =================
            if (imageType == 5) {

                if (zipImage == null || zipImage.isEmpty()) {
                    throw new RuntimeException("PDF ZIP file is required for Image Type 5");
                }

                boolean hasImages = img1 != null || img2 != null || img3 != null || img4 != null || img5 != null || img6 != null ||
                                img7 != null || img8 != null || img9 != null ||
                                img10 != null || img11 != null || img12 != null ||
                                img13 != null || img14 != null || img15 != null;

                if (hasImages) {
                    throw new RuntimeException("Images not allowed for Image Type 5");
                }

                //  SAVE ZIP PROPERLY (IMPORTANT FIX)
                File uploadedZip = new File(dir, System.currentTimeMillis() + "_" + zipImage.getOriginalFilename());
                zipImage.transferTo(uploadedZip);
                if (!uploadedZip.exists()) {
                    throw new RuntimeException("Uploaded ZIP file could not be saved");
                }

                byte[] mergedZip = certificateService.generateType5Certificates(template, tempExcel, uploadedZip, currentUser);
                if (mergedZip == null) {
                    throw new RuntimeException("Merged ZIP generation failed");
                }

                return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=merged_certificates.zip")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).body(mergedZip);
            }


            // ================= SAVE OPTIONAL FILES =================

            saveTempFile(uploadedFiles, img1, dir, "img1");
            saveTempFile(uploadedFiles, img2, dir, "img2");
            saveTempFile(uploadedFiles, img3, dir, "img3");
            saveTempFile(uploadedFiles, img4, dir, "img4");
            saveTempFile(uploadedFiles, img5, dir, "img5");
            saveTempFile(uploadedFiles, img6, dir, "img6");
            saveTempFile(uploadedFiles, img7, dir, "img7");
            saveTempFile(uploadedFiles, img8, dir, "img8");
            saveTempFile(uploadedFiles, img9, dir, "img9");
            saveTempFile(uploadedFiles, img10, dir, "img10");
            saveTempFile(uploadedFiles, img11, dir, "img11");
            saveTempFile(uploadedFiles, img12, dir, "img12");
            saveTempFile(uploadedFiles, img13, dir, "img13");
            saveTempFile(uploadedFiles, img14, dir, "img14");
            saveTempFile(uploadedFiles, img15, dir, "img15");
            saveTempFile(uploadedFiles, zipImage, dir, "zipImage");
            saveTempFile(uploadedFiles, logo, dir, "logo");
            saveTempFile(uploadedFiles, sign, dir, "signature");
            saveTempFile(uploadedFiles, sign2, dir, "signature2");
            saveTempFile(uploadedFiles, sign3, dir, "signature3");
            saveTempFile(uploadedFiles, sign4, dir, "signature4");

            // =====================================================
            //  GENERATE ZIP COMPLETELY IN MEMORY
            // =====================================================

            String jobId = UUID.randomUUID().toString();
            certificateService.initializeProgress(jobId);
            certificateService.startGenerationAsync(jobId, templateId, tempExcel, uploadedFiles.isEmpty() ? null : uploadedFiles, currentUser );
            return ResponseEntity.ok(Map.of("jobId", jobId, "message", "Generation started"
            ));

        } catch (Exception e) {
            logger.error("ZIP generation failed", e);
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(Map.of("error", true, "message", e.getMessage()));
        }
    }

    private void saveTempFile(Map<String, File> uploadedFiles, MultipartFile file, File dir, String key) throws IOException {
        if (file == null || file.isEmpty()) return;
        if (!dir.exists()) dir.mkdirs();
        File temp = new File(dir, System.currentTimeMillis() + "_" + file.getOriginalFilename());
        try (InputStream in = file.getInputStream(); FileOutputStream fos = new FileOutputStream(temp)) {
           in.transferTo(fos);
        }
        uploadedFiles.put(key, temp);
        logger.info("Saved temp file: {}", temp.getAbsolutePath());
    }

    private void cleanupTempFiles(File excelFile, Map<String, File> uploadedFiles) {
        try {
            if (excelFile != null && excelFile.exists()) excelFile.delete();
            if (uploadedFiles != null) {
                for (File f : uploadedFiles.values()) {
                    if (f != null && f.exists()) f.delete();
                }
            }
        } catch (Exception e) {
            logger.warn("Temp cleanup failed: {}", e.getMessage());
        }
    }

    // ======================= REPORT / FONT APIs (UNCHANGED) =======================

    @GetMapping("/reports/all")
    public ResponseEntity<?> getAllReports(@RequestHeader("Authorization") String tokenHeader) {
        try {
            String token = tokenHeader.startsWith("Bearer ") ? tokenHeader.substring(7) : tokenHeader;
            Long userId = jwtUtil.extractUserId(token);
            UserProfile userProfile = profileRepository.findById(userId).orElseThrow();
            UserPrincipal currentUser = new UserPrincipal(userProfile);
            List<Report> reports = reportService.getAllReports(currentUser);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "UP", "timestamp", new Date(), "tempPath", tempPath));
    }

    @GetMapping("/progress/{jobId}")
    public ResponseEntity<?> getProgress(@PathVariable String jobId) {
        ProgressDTO progress = certificateService.getProgress(jobId);
        if (progress == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid Job ID"));
        }

        return ResponseEntity.ok(progress);
    }

    @GetMapping("/download/{jobId}")
    public ResponseEntity<byte[]> downloadZip(@PathVariable String jobId) {
        byte[] zipBytes = certificateService.getGeneratedZip(jobId);
        if (zipBytes == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=certificates_" + jobId + ".zip").body(zipBytes);
    }

    private String getRootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

}

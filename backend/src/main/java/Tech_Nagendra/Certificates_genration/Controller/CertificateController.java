
package Tech_Nagendra.Certificates_genration.Controller;
import Tech_Nagendra.Certificates_genration.Entity.CandidateDTO;
import Tech_Nagendra.Certificates_genration.Entity.Report;
import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import Tech_Nagendra.Certificates_genration.Security.UserPrincipal;
import Tech_Nagendra.Certificates_genration.Service.CertificateService;
import Tech_Nagendra.Certificates_genration.Service.DynamicFontService;
import Tech_Nagendra.Certificates_genration.Service.ReportService;
import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
import Tech_Nagendra.Certificates_genration.Utility.JwtUtil;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Value("${certificate.template.path:${user.dir}/templates/}")
    private String tempPath;

    public CertificateController(CertificateService certificateService,
                                 ReportService reportService,
                                 ProfileRepository profileRepository,
                                 JwtUtil jwtUtil,
                                 DynamicFontService dynamicFontService) {
        this.certificateService = certificateService;
        this.reportService = reportService;
        this.profileRepository = profileRepository;
        this.jwtUtil = jwtUtil;
        this.dynamicFontService = dynamicFontService;
    }

    @PostMapping(value = "/generate-zip/{templateId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> generateCertificatesZip(
            @PathVariable Long templateId,
            @RequestPart("excel") MultipartFile excelFile,
            @RequestPart(value = "zipImage", required = false) MultipartFile zipImage,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestPart(value = "sign", required = false) MultipartFile sign,
            @RequestHeader("Authorization") String tokenHeader) {

        Map<String, File> uploadedFiles = new HashMap<>();
        File tempExcel = null;
        File dir = new File(tempPath);

        try {
            if (excelFile == null || excelFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Excel file is required"));
            }

            String token = tokenHeader.startsWith("Bearer ") ? tokenHeader.substring(7) : tokenHeader;
            Long userId = jwtUtil.extractUserId(token);

            UserProfile userProfile = profileRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

            UserPrincipal currentUser = new UserPrincipal(userProfile);

            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("Failed to create directory: " + tempPath);
            }

            tempExcel = new File(dir, System.currentTimeMillis() + "_" + excelFile.getOriginalFilename());
            try (InputStream in = excelFile.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tempExcel)) {
                in.transferTo(fos);
            }

            saveTempFile(uploadedFiles, zipImage, dir, "zipImage");
            saveTempFile(uploadedFiles, logo, dir, "logo");
            saveTempFile(uploadedFiles, sign, dir, "sign");

            Map<String, Object> result = certificateService.generateCertificatesAndReports(
                    templateId,
                    tempExcel,
                    uploadedFiles.isEmpty() ? null : uploadedFiles,
                    tempPath,
                    currentUser
            );

            if (result.containsKey("error") && (Boolean) result.get("error")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Certificate generation failed", "message", result.get("message")));
            }

            List<File> pdfFiles = (List<File>) result.getOrDefault("pdfFiles", new ArrayList<>());
            List<CandidateDTO> candidates = (List<CandidateDTO>) result.getOrDefault("candidates", new ArrayList<>());

            if (pdfFiles.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "No PDF files generated"));
            }

            for (CandidateDTO candidate : candidates) {
                try {
                    reportService.saveCandidateReport(candidate, templateId, userId, currentUser);
                } catch (Exception ignored) { }
            }

            File outputFolder = new File(tempPath);
            List<File> finalPdfList = performMergeIfNeeded(outputFolder, candidates, pdfFiles);

            byte[] zipBytes = createZipBytesFromCandidates(finalPdfList, candidates);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename("certificates_" + System.currentTimeMillis() + ".zip")
                    .build());
            headers.setContentLength(zipBytes.length);

            return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Certificate generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Certificate generation failed", "message", e.getMessage()));
        } finally {
            cleanupTempFiles(tempExcel, uploadedFiles);
        }
    }

    private List<File> performMergeIfNeeded(File outputFolder, List<CandidateDTO> candidates, List<File> generatedPdfs) {
        try {
            logger.info("Checking for Type 4 and Type 5 certificates to merge...");

            Map<String, CandidateDTO> sidToCandidate = new HashMap<>();
            for (CandidateDTO candidate : candidates) {
                if (candidate.getSid() != null && !candidate.getSid().trim().isEmpty()) {
                    sidToCandidate.put(candidate.getSid().trim(), candidate);
                }
            }

            File[] allPdfs = outputFolder.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
            if (allPdfs == null || allPdfs.length == 0) {
                logger.info("No PDF files found in output folder for merging");
                return generatedPdfs;
            }
            Map<String, List<File>> groupedBySid = new HashMap<>();
            for (File pdfFile : allPdfs) {
                String sid = extractSidFromFilename(pdfFile.getName());
                if (sid != null && !sid.trim().isEmpty()) {
                    groupedBySid.computeIfAbsent(sid, k -> new ArrayList<>()).add(pdfFile);
                }
            }

            logger.info("Found {} unique SIDs with PDF files", groupedBySid.size());
            Map<String, File> mergedFilesMap = new HashMap<>();
            int mergeCount = 0;

            for (Map.Entry<String, List<File>> entry : groupedBySid.entrySet()) {
                String sid = entry.getKey();
                List<File> pdfList = entry.getValue();
                boolean hasType4 = false;
                boolean hasType5 = false;
                File type4File = null;
                File type5File = null;

                for (File pdf : pdfList) {
                    String fileName = pdf.getName().toLowerCase();
                    if (fileName.contains("type4")) {
                        hasType4 = true;
                        type4File = pdf;
                    } else if (fileName.contains("type5")) {
                        hasType5 = true;
                        type5File = pdf;
                    }
                }

                if (hasType4 && hasType5 && type4File != null && type5File != null) {
                    try {
                        File mergedFile = mergeType4AndType5Certificates(type4File, type5File, sid, sidToCandidate.get(sid), outputFolder);
                        if (mergedFile != null) {
                            mergedFilesMap.put(sid, mergedFile);
                            mergeCount++;
                            logger.info("Successfully merged Type 4 and Type 5 for SID: {}", sid);
                            type4File.delete();
                            type5File.delete();
                        }
                    } catch (Exception e) {
                        logger.error("Failed to merge certificates for SID {}: {}", sid, e.getMessage());
                    }
                }
            }

            logger.info("Successfully merged {} pairs of Type 4 and Type 5 certificates", mergeCount);


            List<File> finalPdfList = new ArrayList<>();

            for (CandidateDTO candidate : candidates) {
                String sid = candidate.getSid();
                if (sid == null || sid.trim().isEmpty()) continue;

                if (mergedFilesMap.containsKey(sid)) {
                    finalPdfList.add(mergedFilesMap.get(sid));
                } else {
                    File individualFile = findPdfForCandidate(outputFolder, candidate);
                    if (individualFile != null && individualFile.exists()) {
                        finalPdfList.add(individualFile);
                    }
                }
            }

            logger.info("Final PDF list contains {} files", finalPdfList.size());
            return finalPdfList;

        } catch (Exception e) {
            logger.error("Error during certificate merging process: {}", e.getMessage(), e);
            return generatedPdfs; // Return original list if merging fails
        }
    }

    private File mergeType4AndType5Certificates(File type4File, File type5File, String sid, CandidateDTO candidate, File outputFolder) {
        try {
            String safeName = "Unknown";
            if (candidate != null && candidate.getCandidateName() != null) {
                safeName = candidate.getCandidateName().replaceAll("[^a-zA-Z0-9\\-_ ]", "_").trim();
            }

            String mergedFileName = safeName + "_" + sid + ".pdf";
            File mergedFile = new File(outputFolder, mergedFileName);
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.addSource(type4File);
            merger.addSource(type5File);
            merger.setDestinationFileName(mergedFile.getAbsolutePath());

            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());

            logger.info("Created merged PDF: {}", mergedFile.getName());
            return mergedFile;

        } catch (Exception e) {
            logger.error("Failed to merge PDFs for SID {}: {}", sid, e.getMessage(), e);
            return null;
        }
    }


    private String extractSidFromFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return null;
        }

        try {
            // Remove file extension
            String nameWithoutExt = filename;
            if (nameWithoutExt.toLowerCase().endsWith(".pdf")) {
                nameWithoutExt = nameWithoutExt.substring(0, nameWithoutExt.length() - 4);
            }

            String[] parts = nameWithoutExt.split("_");


            for (String part : parts) {
                if (part.matches("^[A-Za-z0-9-]{3,50}$")) {
                    if (!part.toLowerCase().matches("type4|type5|merged|certificate|cert")) {
                        return part;
                    }
                }
            }


            if (parts.length > 0) {
                String firstPart = parts[0];
                if (!firstPart.toLowerCase().matches("type4|type5|merged|certificate|cert|unknown")) {
                    return firstPart;
                }
            }

            return null;

        } catch (Exception e) {
            logger.warn("Error extracting SID from filename '{}': {}", filename, e.getMessage());
            return null;
        }
    }


    private File findPdfForCandidate(File folder, CandidateDTO candidate) {
        if (candidate == null || candidate.getSid() == null) {
            return null;
        }

        String sid = candidate.getSid().trim();
        File[] pdfFiles = folder.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));

        if (pdfFiles == null) {
            return null;
        }

        for (File pdfFile : pdfFiles) {
            String fileName = pdfFile.getName();

            if (fileName.contains("_merged.")) {
                continue;
            }


            if (fileName.contains(sid)) {
                return pdfFile;
            }

            String extractedSid = extractSidFromFilename(fileName);
            if (sid.equals(extractedSid)) {
                return pdfFile;
            }
        }

        return null;
    }

    private byte[] createZipBytesFromCandidates(List<File> pdfFiles, List<CandidateDTO> candidates) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            Map<String, CandidateDTO> sidToCandidate = new HashMap<>();
            for (CandidateDTO candidate : candidates) {
                if (candidate.getSid() != null) {
                    sidToCandidate.put(candidate.getSid(), candidate);
                }
            }

            for (File pdfFile : pdfFiles) {
                if (pdfFile != null && pdfFile.exists()) {
                    String sid = extractSidFromFilename(pdfFile.getName());
                    CandidateDTO candidate = sidToCandidate.get(sid);

                    String candidateName = "Certificate";
                    if (candidate != null && candidate.getCandidateName() != null) {
                        candidateName = candidate.getCandidateName().replaceAll("[^a-zA-Z0-9.-]", "_");
                    }

                    String zipEntryName;
                    if (sid != null) {
                        zipEntryName = candidateName + "_" + sid + ".pdf";
                    } else {
                        zipEntryName = candidateName + "_" + System.currentTimeMillis() + ".pdf";
                    }

                    zos.putNextEntry(new ZipEntry(zipEntryName));
                    Files.copy(pdfFile.toPath(), zos);
                    zos.closeEntry();

                    logger.debug("Added to ZIP: {}", zipEntryName);
                }
            }
        }
        logger.info("Created ZIP file with {} PDF entries", pdfFiles.size());
        return baos.toByteArray();
    }

    private void saveTempFile(Map<String, File> uploadedFiles, MultipartFile file, File dir, String key) throws IOException {
        if (file != null && !file.isEmpty()) {
            File temp = new File(dir, System.currentTimeMillis() + "_" + file.getOriginalFilename());
            try (InputStream in = file.getInputStream();
                 FileOutputStream fos = new FileOutputStream(temp)) {
                in.transferTo(fos);
            }
            uploadedFiles.put(key, temp);
            logger.info("Saved temporary file: {}", temp.getName());
        }
    }

    private void cleanupTempFiles(File excelFile, Map<String, File> uploadedFiles) {
        try {
            int deletedCount = 0;

            if (excelFile != null && excelFile.exists()) {
                if (excelFile.delete()) {
                    deletedCount++;
                }
            }

            for (File file : uploadedFiles.values()) {
                if (file != null && file.exists()) {
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
            }

            logger.info("Cleaned up {} temporary files", deletedCount);

        } catch (Exception e) {
            logger.warn("Error during temporary file cleanup: {}", e.getMessage());
        }
    }

    @GetMapping("/reports/all")
    public ResponseEntity<?> getAllReports(@RequestHeader("Authorization") String tokenHeader) {
        try {
            String token = tokenHeader.startsWith("Bearer ") ? tokenHeader.substring(7) : tokenHeader;
            Long userId = jwtUtil.extractUserId(token);
            UserProfile userProfile = profileRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            UserPrincipal currentUser = new UserPrincipal(userProfile);
            List<Report> reports = reportService.getAllReports(currentUser);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Error fetching reports: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/reports/count/month")
    public ResponseEntity<?> countReportsThisMonth() {
        try {
            Long count = reportService.countCertificatesThisMonth();
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            logger.error("Error counting monthly reports: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/fonts/status")
    public ResponseEntity<?> getFontStatus() {
        try {
            return ResponseEntity.ok(dynamicFontService.getFontInfo());
        } catch (Exception e) {
            logger.error("Error getting font status: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fonts/reload")
    public ResponseEntity<?> reloadFonts() {
        try {
            dynamicFontService.reloadFonts();
            return ResponseEntity.ok(Map.of("message", "Fonts reloaded successfully"));
        } catch (Exception e) {
            logger.error("Error reloading fonts: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/fonts/available")
    public ResponseEntity<?> getAvailableFonts() {
        try {
            Set<String> available = dynamicFontService.getAvailableFontFamilies();
            return ResponseEntity.ok(Map.of("availableFonts", available, "totalFonts", available.size()));
        } catch (Exception e) {
            logger.error("Error getting available fonts: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/fonts/check/{fontName}")
    public ResponseEntity<?> checkFont(@PathVariable String fontName) {
        try {
            boolean available = dynamicFontService.isFontFamilyAvailable(fontName);
            return ResponseEntity.ok(Map.of("fontName", fontName, "available", available));
        } catch (Exception e) {
            logger.error("Error checking font {}: {}", fontName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        File tempDir = new File(tempPath);
        health.put("status", "UP");
        health.put("service", "Certificate Generation");
        health.put("timestamp", new Date());
        health.put("tempDirectoryExists", tempDir.exists());
        health.put("tempDirectoryWritable", tempDir.canWrite());
        health.put("tempDirectoryPath", tempDir.getAbsolutePath());
        return ResponseEntity.ok(health);
    }

    @GetMapping("/status/{templateId}")
    public ResponseEntity<?> getGenerationStatus(@PathVariable Long templateId) {
        return ResponseEntity.ok(Map.of(
                "templateId", templateId,
                "status", "ready",
                "message", "Certificate generation service is active",
                "timestamp", new Date()
        ));
    }


    @PostMapping("/merge-certificates")
    public ResponseEntity<?> mergeCertificates(@RequestParam String outputFolderPath) {
        try {
            File outputFolder = new File(outputFolderPath);
            if (!outputFolder.exists() || !outputFolder.isDirectory()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Output folder does not exist"));
            }

            List<File> mergedFiles = performManualMerge(outputFolder);

            return ResponseEntity.ok(Map.of(
                    "message", "Certificate merging completed",
                    "mergedCount", mergedFiles.size(),
                    "mergedFiles", mergedFiles.stream().map(File::getName).toList()
            ));

        } catch (Exception e) {
            logger.error("Manual certificate merging failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Certificate merging failed", "message", e.getMessage()));
        }
    }


    private List<File> performManualMerge(File outputFolder) {
        List<File> mergedFiles = new ArrayList<>();

        try {
            File[] allPdfs = outputFolder.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
            if (allPdfs == null) return mergedFiles;

            Map<String, List<File>> groupedBySid = new HashMap<>();
            for (File pdf : allPdfs) {
                String sid = extractSidFromFilename(pdf.getName());
                if (sid != null) {
                    groupedBySid.computeIfAbsent(sid, k -> new ArrayList<>()).add(pdf);
                }
            }


            for (Map.Entry<String, List<File>> entry : groupedBySid.entrySet()) {
                String sid = entry.getKey();
                List<File> pdfList = entry.getValue();

                File type4 = null, type5 = null;
                for (File pdf : pdfList) {
                    if (pdf.getName().contains("type4")) type4 = pdf;
                    else if (pdf.getName().contains("type5")) type5 = pdf;
                }

                if (type4 != null && type5 != null) {
                    File merged = mergeType4AndType5Certificates(type4, type5, sid, null, outputFolder);
                    if (merged != null) {
                        mergedFiles.add(merged);
                        type4.delete();
                        type5.delete();
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Manual merge failed: {}", e.getMessage(), e);
        }

        return mergedFiles;
    }
}



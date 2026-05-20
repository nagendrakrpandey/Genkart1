package Tech_Nagendra.Certificates_genration.Service;
import Tech_Nagendra.Certificates_genration.Dto.ProgressDTO;
import Tech_Nagendra.Certificates_genration.Entity.CandidateDTO;
import Tech_Nagendra.Certificates_genration.Entity.Report;
import Tech_Nagendra.Certificates_genration.Entity.Template;
import Tech_Nagendra.Certificates_genration.Repository.ReportRepository;
import Tech_Nagendra.Certificates_genration.Repository.TemplateImageRepository;
import Tech_Nagendra.Certificates_genration.Repository.TemplateRepository;
import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
import Tech_Nagendra.Certificates_genration.Security.UserPrincipal;
import jakarta.transaction.Transactional;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfExporterConfiguration;
import net.sf.jasperreports.export.SimplePdfReportConfiguration;
import net.sf.jasperreports.export.type.PdfaConformanceEnum;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.File;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import static org.apache.batik.anim.values.AnimatableValue.formatNumber;

@Service
public class CertificateService {
    private final Map<String, JasperReport> jasperReportCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> jrxmlParamCache = new ConcurrentHashMap<>();
    private static final Map<String, ProgressDTO> progressMap = new ConcurrentHashMap<>();
    public void clearTemplateCache(Long templateId) {
        jasperReportCache.keySet().removeIf(key -> key.startsWith(templateId + "_type_"));
    }

    private static final Logger logger = LoggerFactory.getLogger(CertificateService.class);
    @Autowired
    private TemplateRepository templateRepository;
    @Autowired
    private  ReportRepository  reportRepository;
    @Autowired
    private TemplateImageRepository templateImageRepository;
    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private ReportService reportService;
    @Value("${certificate.template.path:${user.dir}/templates/}")
    private String baseTemplateFolder;
    @Value("${custom.fonts.lib:lib}")
    private String libsFolder;
    @Value("${custom.fonts.dir:src/main/resources/fonts}")
    private String classpathFontsDir;
    private static volatile boolean fontsLoaded = false;
    public Map<String, Object> generateCertificatesAndReports(Long templateId, File excelFile, Map<String, File> uploadedFiles, String outputFolderPath,UserPrincipal currentUser) {
        try {
            Template template = templateRepository.findById(templateId).orElseThrow(() -> new RuntimeException("Template not found with id: " + templateId));
            File outputFolder = null;
            if (outputFolderPath != null && !outputFolderPath.trim().isEmpty()) {
                outputFolder = new File(outputFolderPath);
                if (!outputFolder.exists() && !outputFolder.mkdirs()) {
                    throw new RuntimeException("Failed to create output dir: " + outputFolderPath);
                }
            }
            logger.info("Starting certificate generation for template: {} (Type: {})", template.getTemplateName(), template.getImageType());
            Map<String, Object> result = generateCertificatesByType(template, excelFile, uploadedFiles,outputFolder, currentUser);
            result.put("error", false);
            result.put("templateName", template.getTemplateName());
            return result;
        } catch (Exception e) {
            logger.error("Generation failed", e);
            throw new RuntimeException("Certificate generation failed: " + e.getMessage(),e);
        }

    }

    public void generateCertificatesZipStream(Long templateId, File excelFile, Map<String, File> uploadedFiles, UserPrincipal currentUser, OutputStream outputStream) throws Exception {
        Template template = templateRepository.findById(templateId).orElseThrow(() -> new RuntimeException("Template not found"));
        List<CandidateDTO> candidates = parseExcel(excelFile, template);
        List<Report> reportBatch = Collections.synchronizedList(new ArrayList<>());
        List<CandidateDTO> missingCandidates = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean hasError = new AtomicBoolean(false);
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(outputStream));
        List<File> templateImages = loadStaticImages(template.getTemplateFolder());
        List<File> baseImages = loadStaticImages(baseTemplateFolder);
        final File extractedZipFolder;
        if (template.getImageType() == 1) {
            if (uploadedFiles == null || !uploadedFiles.containsKey("zipImage")) {
                throw new RuntimeException("ZIP file is required for Image Type 1");
            }
            extractedZipFolder =
                    extractZipImages(uploadedFiles, new File(baseTemplateFolder));
        } else {
            extractedZipFolder = null;
        }

        // ================= PARALLEL EXECUTION =================
        int threads = Math.min(8, Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService executor = new ThreadPoolExecutor(threads, threads, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        List<Future<Map.Entry<String, byte[]>>> futures = new ArrayList<>();
        for (CandidateDTO candidate : candidates) {
            futures.add(executor.submit(() -> {
                String sid = candidate.getSid();
                if (sid == null || sid.isBlank()) return null;

                try {
                    if (template.getImageType() == 1) {
                        File candidateImage = findCandidateImage(extractedZipFolder, sid);
                        if (candidateImage == null) {
                            logger.warn("Photo missing for SID: {}", sid);
                            missingCandidates.add(candidate);
                            return null;
                        }
                    }

                    Report report = createReport(candidate, currentUser);
                    reportService.saveOrUpdateBySid(report, currentUser);

                    byte[] pdfBytes = generateCertificateForCandidateBytes(
                            template,
                            candidate,
                            templateImages,
                            baseImages,
                            extractedZipFolder,
                            template.getImageType(),
                            uploadedFiles
                    );

                    if (pdfBytes == null || pdfBytes.length == 0) {
                        throw new RuntimeException("Empty PDF generated for SID " + sid);
                    }

                    String fileName = buildPdfFileName(candidate, template.getImageType());
                    return new AbstractMap.SimpleEntry<>(fileName, pdfBytes);

                } catch (Exception e) {
                    hasError.set(true);
                    logger.error("Failed for SID {}", sid, e);
                    throw new RuntimeException("Certificate generation failed for SID " + sid, e);
                }
            }));
        }

        // ================= WRITE ZIP SEQUENTIALLY =================

        for (Future<Map.Entry<String, byte[]>> future : futures) {
            Map.Entry<String, byte[]> entry;
            try {
                entry = future.get();
            } catch (ExecutionException ee) {
                executor.shutdownNow();
                Throwable cause = ee.getCause();

                throw new RuntimeException(
                        "ZIP generation failed: " +
                                (cause != null ? getRootCauseMessage(cause) : "Unknown error"),
                        cause
                );
            }

            if (entry != null) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.MINUTES);
        if (!reportBatch.isEmpty()) {
            reportRepository.saveAll(reportBatch);
        }

        // ================= MISSING PHOTO REPORT =================

        if (!missingCandidates.isEmpty()) {

            byte[] reportPdf = generateMissingCandidatesReport(missingCandidates);
            zos.putNextEntry(new ZipEntry("Missing_Photos_Report.pdf"));
            zos.write(reportPdf);
            zos.closeEntry();
            StringBuilder sb = new StringBuilder();
            sb.append("===== MISSING PHOTO REPORT =====\n\n");
            for (CandidateDTO c : missingCandidates) {
                sb.append("SID: ").append(c.getSid()).append("  Name: ").append(c.getCandidateName()).append("  PHOTO MISSING\n");
            }
            zos.putNextEntry(new ZipEntry("Missing_Photos_Report.txt"));
            zos.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        if (hasError.get()) {
            executor.shutdownNow();
            zos.close();

        }
        zos.finish();
        zos.close();
    }

    private byte[] generateMissingCandidatesReport(List<CandidateDTO> missingCandidates) {
        try {
            JasperDesign design = new JasperDesign();
            design.setName("MissingPhotoReport");
            design.setPageWidth(595);
            design.setPageHeight(842);
            design.setColumnWidth(555);
            design.setLeftMargin(20);
            design.setRightMargin(20);
            design.setTopMargin(20);
            design.setBottomMargin(20);
            JRDesignBand band = new JRDesignBand();
            band.setHeight(800);
            int y = 20;
            for (CandidateDTO c : missingCandidates) {
                JRDesignStaticText text = new JRDesignStaticText();
                text.setX(20);
                text.setY(y);
                text.setWidth(500);
                text.setHeight(20);
                text.setText("SID:    " + c.getSid() + "     Name: " + c.getCandidateName() + "      PHOTO MISSING");
                band.addElement(text);
                y += 25;
            }
            design.setTitle(band);
            JasperReport report = JasperCompileManager.compileReport(design);
            JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(), new JREmptyDataSource());
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate missing report", e);
        }
    }

    private byte[] generateMissingImagePdf(String sid) {
        try {
            JasperDesign design = new JasperDesign();
            design.setName("MissingImageReport");
            design.setPageWidth(595);
            design.setPageHeight(842);
            design.setColumnWidth(555);
            design.setLeftMargin(20);
            design.setRightMargin(20);
            design.setTopMargin(20);
            design.setBottomMargin(20);
            JRDesignBand band = new JRDesignBand();
            band.setHeight(200);
            JRDesignStaticText text = new JRDesignStaticText();
            text.setX(50);
            text.setY(80);
            text.setWidth(500);
            text.setHeight(80);
            text.setText("Certificate Not Generated\n\nMissing image for SID: " + sid);
            text.setFontSize(18f);
            band.addElement(text);
            design.setTitle(band);
            JasperReport report = JasperCompileManager.compileReport(design);
            JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(), new JREmptyDataSource()
            );
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Missing Image PDF for SID: " + sid, e);
        }
    }
    public Map<String, Object> generateCertificatesByType(Template template, File excelFile, Map<String, File> uploadedFiles, File outputFolder, UserPrincipal currentUser) throws Exception {
        validateJrxmlFieldsWithDTO(template.getJrxmlPath(), CandidateDTO.class);

        int imageType = template.getImageType();
        logger.info("Processing Image Type: {}", imageType);
        switch (imageType) {
            case 1:
                return generateType1Certificates(template, excelFile, uploadedFiles, outputFolder, currentUser);
            case 2:
                return generateType2Certificates(template, excelFile, uploadedFiles, outputFolder, currentUser);
            case 3:
                return generateType3Certificates(template, excelFile, uploadedFiles, outputFolder, currentUser);
            case 4:
                return generateType4Certificates(template, excelFile, outputFolder, currentUser);
            case 5:
                throw new RuntimeException("Use Type5 merge endpoint instead");
            default:
                return generateType0Certificates(template, excelFile, outputFolder, currentUser);
            case 6:
                return generateType6Certificates(template, excelFile, uploadedFiles, outputFolder, currentUser);
        }
    }

    private Map<String, Object> generateType0Certificates(Template template, File excelFile, File outputFolder, UserPrincipal currentUser ) throws Exception {
        return generateWithStaticImages(template, excelFile, null, outputFolder, 0, null, currentUser);
    }

    private Map<String, Object> generateType1Certificates(Template template, File excelFile, Map<String, File> uploadedFiles, File outputFolder, UserPrincipal currentUser) throws Exception {
        File extracted = extractZipImages(uploadedFiles, outputFolder);
        return generateWithStaticImages(template, excelFile, extracted, outputFolder, 1, uploadedFiles, currentUser);
    }

    private Map<String, Object> generateType2Certificates(Template template, File excelFile, Map<String, File> uploadedFiles, File outputFolder, UserPrincipal currentUser) throws Exception {
        File extracted = extractZipImages(uploadedFiles, outputFolder);
        return generateWithStaticImages(template, excelFile, extracted, outputFolder, 2, uploadedFiles, currentUser);
    }

    private Map<String, Object> generateType3Certificates(Template template, File excelFile, Map<String, File> uploadedFiles, File outputFolder, UserPrincipal currentUser) throws Exception {
        File extracted = extractZipImages(uploadedFiles, outputFolder);
        return generateWithStaticImages(template, excelFile, extracted, outputFolder, 3, uploadedFiles, currentUser);
    }

    private Map<String, Object> generateType4Certificates(Template template, File excelFile, File outputFolder, UserPrincipal currentUser) throws Exception {
        logger.info("Generating Type 4 Certificates");
        return generateWithStaticImages(template, excelFile, null, outputFolder, 4, null, currentUser);
    }

    public byte[] generateType5Certificates(Template template, File excelFile, File uploadedZip, UserPrincipal user) throws Exception {
    validatePdfZipOnly(uploadedZip);
    Map<String, byte[]> type4PdfMap = new HashMap<>();
    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(uploadedZip))) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;
            String fileName = new File(entry.getName()).getName();
            if (!fileName.toLowerCase().endsWith(".pdf")) continue;
            String rawSid = fileName.replace(".pdf", "").trim();
            String normalizedSid = normalizeSid(rawSid);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            zis.transferTo(baos);
            type4PdfMap.put(normalizedSid, baos.toByteArray());
        }
    }

    List<CandidateDTO> candidates = parseExcel(excelFile, template);
    ByteArrayOutputStream finalZipBaos = new ByteArrayOutputStream();
//    ZipOutputStream zos = new ZipOutputStream(finalZipBaos);
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(finalZipBaos));
    List<File> templateImages = loadStaticImages(template.getTemplateFolder());
    List<File> baseImages = loadStaticImages(baseTemplateFolder);
    for (CandidateDTO candidate : candidates) {
        String sid = candidate.getSid();
        if (sid == null || sid.isBlank()) continue;
        String normalizedExcelSid = normalizeSid(sid);
        byte[] type4Pdf = type4PdfMap.get(normalizedExcelSid);
        if (type4Pdf == null) continue;
        byte[] type5Pdf = generateCertificateForCandidateBytes(template, candidate, templateImages, baseImages, null, 5, null);
        byte[] merged = mergePdfBytes(type4Pdf, type5Pdf);
        String fileName = buildPdfFileName(candidate, 5);
        zos.putNextEntry(new ZipEntry(fileName));
        zos.write(merged);
        zos.closeEntry();
    }
    zos.close();
    return finalZipBaos.toByteArray();
    }
    private Map<String, Object> generateType6Certificates(Template template, File excelFile, Map<String, File> uploadedFiles, File outputFolder, UserPrincipal currentUser) throws Exception {
        if (uploadedFiles == null || uploadedFiles.isEmpty()) {
            throw new RuntimeException("Dynamic images are required for Image Type 6");
        }
        return generateWithStaticImages(template, excelFile, null, outputFolder, 6, uploadedFiles, currentUser);
    }
    private File extractZipImages(Map<String, File> uploadedFiles, File outputFolder) throws IOException {
        if (uploadedFiles != null && uploadedFiles.containsKey("zipImage")) {

            File parentFolder = outputFolder != null
                    ? outputFolder
                    : new File(System.getProperty("java.io.tmpdir"));

            File extracted = new File(
                    parentFolder,
                    "unzippedImages_" + System.currentTimeMillis() + "_" + UUID.randomUUID()
            );

            if (!extracted.mkdirs()) {
                throw new IOException("Failed to create unzip folder: " + extracted.getAbsolutePath());
            }

            unzipAndRenameImages(uploadedFiles.get("zipImage"), extracted);

            return extracted;
        }

        return null;
    }
    private Map<String, Object> generateWithStaticImages(Template template, File excelFile, File extractedZipFolder, File outputFolder, int imageType, Map<String, File> uploadedFiles, UserPrincipal currentUser) throws Exception {
        Map<String, byte[]> pdfByteMap = new ConcurrentHashMap<>();
        List<CandidateDTO> missingCandidates = Collections.synchronizedList(new ArrayList<>());
        Map<String, CandidateDTO> uniqueBySid = new ConcurrentHashMap<>();
        List<File> currentPdfFiles = Collections.synchronizedList(new ArrayList<>());
        List<CandidateDTO> candidates = parseExcel(excelFile, template);
        if (candidates == null || candidates.isEmpty()) {
            throw new Exception("No candidates found");
        }
        logger.info("Processing {} candidates from Excel", candidates.size());
        List<File> templateStaticImages = loadStaticImages(template.getTemplateFolder());
        List<File> baseStaticImages = loadStaticImages(baseTemplateFolder);

        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newWorkStealingPool(threads);
        List<Future<Void>> futures = new ArrayList<>();
        for (CandidateDTO candidate : candidates) {
            futures.add(executor.submit(() -> {
                String sid = candidate.getSid();
                if (sid == null || sid.trim().isEmpty()) {
                    return null;
                }
                try {
                    if (imageType == 1) {
                        if (extractedZipFolder == null) {
                            return null;
                        }
                        File candidateImage = findCandidateImage(extractedZipFolder, sid);
                        if (candidateImage == null) {
                            logger.warn(" Photo missing for SID: {}", sid);
                            missingCandidates.add(candidate);
                            return null;
                        }
                    }

                    // Generate both Type 4 and Type 5 certificates when imageType is 5 (for merging)
                   else {
                        byte[] pdfBytes = generateCertificateForCandidateBytes(template, candidate, templateStaticImages, baseStaticImages, extractedZipFolder, imageType, uploadedFiles);
                        if (pdfBytes != null && pdfBytes.length > 0) {
                            String safeSid = sid.replaceAll("[^a-zA-Z0-9_-]", "_");
                            String safeName = "Unknown";
                            if (candidate.getCandidateName() != null) {
                                safeName = candidate.getCandidateName().replaceAll("[^a-zA-Z0-9\\-_]", "_").trim();
                            }
                            String fileName = buildPdfFileName(candidate, imageType);
                            pdfByteMap.put(fileName, pdfBytes);
//                            uniqueBySid.putIfAbsent(sid, candidate);
                            uniqueBySid.put(sid, candidate);
                            uniqueBySid.put(sid, candidate);
                        }
                    }

                } catch (Exception e) {
                    logger.error("Failed for SID {}", sid, e);
                }
                return null;
            }));
        }

        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                logger.warn("Error waiting for PDF task: {}", e.getMessage());
            }
        }
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.MINUTES);
        if (!missingCandidates.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("===== MISSING PHOTO REPORT =====\n\n");
            for (CandidateDTO c : missingCandidates) {
                sb.append("SID:     ").append(c.getSid()).append("   | Name: ").append(c.getCandidateName()).append("      | PHOTO MISSING\n");
            }
            byte[] txtReport = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            pdfByteMap.put("Missing_Photos_Report.txt", txtReport);
        }
        logger.info("All certificates generated in memory for image type {}", imageType);
        Map<String, Object> result = new HashMap<>();
        result.put("error", false);
        result.put("message", "Certificates generated in memory");
        result.put("candidates", new ArrayList<>(uniqueBySid.values()));
        result.put("totalGenerated", uniqueBySid.size());
        result.put("templateName", template.getTemplateName());
        result.put("pdfByteMap", pdfByteMap);

        return result;
    }

    private Report createReport(CandidateDTO candidate, UserPrincipal currentUser) {
        Report report = new Report();
        report.setSid(candidate.getSid());
        report.setCandidateName(candidate.getCandidateName());
        report.setGrade(candidate.getGrade());
        report.setBatchId(candidate.getBatchId());
   report.setTemplateName(candidate.getTemplate() != null ? candidate.getTemplate().getTemplateName() : null);
   report.setJobrole(candidate.getJobRole());
        report.setLevel(candidate.getLevel());
        report.setTemplate(candidate.getTemplate());
        return report;
    }

    private byte[] generateCertificateForCandidateBytes(Template template, CandidateDTO candidate, List<File> templateStaticImages, List<File> baseStaticImages, File extractedZipFolder,
            int imageType,
            Map<String, File> uploadedFiles
    ) throws Exception {
        if (template.getJrxmlPath() == null || template.getJrxmlPath().trim().isEmpty()) {
            throw new IllegalArgumentException("JRXML path missing");
        }
        JasperPrint jasperPrint = generateSingleJasperPrint(template, candidate, templateStaticImages, baseStaticImages, extractedZipFolder, imageType, uploadedFiles);
        return exportToPdfBytes(jasperPrint);
    }

    private byte[] mergePdfBytes(byte[] pdf1, byte[] pdf2) throws Exception {
        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        merger.addSource(new ByteArrayInputStream(pdf1));
        merger.addSource(new ByteArrayInputStream(pdf2));
        merger.setDestinationStream(output);
        merger.mergeDocuments(org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly());
        return output.toByteArray();
    }
    private JasperPrint generateSingleJasperPrint(Template template, CandidateDTO candidate, List<File> templateImages, List<File> baseImages, File extractedZipFolder, int imageType, Map<String, File> uploadedFiles) throws Exception {
        if (template.getJrxmlPath() == null || template.getJrxmlPath().isBlank()) {
            throw new IllegalArgumentException("JRXML path is missing for template ID: " + template.getId());
        }

//        File jrxmlFile = new File(template.getJrxmlPath());

        File baseFile = new File(template.getJrxmlPath());
        String baseName = baseFile.getName().replace(".jrxml", "");
        String folderPath = baseFile.getParent();
        File jrxmlFile;
        if (imageType == 4) {
            jrxmlFile = new File(folderPath, baseName + ".jrxml");
        } else if (imageType == 5) {
            jrxmlFile = new File(folderPath, baseName + ".jrxml");
        } else {
            jrxmlFile = baseFile;
        }
        if (!jrxmlFile.exists()) {
            throw new FileNotFoundException("JRXML file not found: " + jrxmlFile.getAbsolutePath());
        }

        if (!jrxmlFile.exists()) {
            throw new FileNotFoundException("JRXML file not found at: " + jrxmlFile.getAbsolutePath());
        }

        // ================= SMART CACHE KEY =================
        long lastModified = jrxmlFile.lastModified();
        String cacheKey = template.getId()
                + "_type_" + imageType + "_ts_" + lastModified;

        jasperReportCache.keySet().removeIf(key ->
                key.startsWith(template.getId() + "_type_" + imageType + "_ts_")
                        && !key.equals(cacheKey)
        );
        JasperReport jasperReport = jasperReportCache.computeIfAbsent(
                cacheKey,
                key -> {
                    try (InputStream in = new FileInputStream(jrxmlFile)) {
                        return JasperCompileManager.compileReport(in);
                    } catch (Exception e) {
                        throw new RuntimeException("JRXML compile failed for file: " + jrxmlFile.getAbsolutePath(), e);
                    }
                }
        );

        // ================= PARAMETERS =================
        Map<String, Object> parameters = createJasperParameters();
        setupImageParameters(parameters, templateImages, baseImages, extractedZipFolder, imageType, uploadedFiles, candidate, template);
        CandidateDTO dataCandidate = createModifiedCandidateForHtml(candidate);

        // ================= FILL REPORT =================

        return JasperFillManager.fillReport(jasperReport, parameters,
                new JRBeanCollectionDataSource(Collections.singletonList(dataCandidate), false));
    }
private byte[] exportToPdfBytes(JasperPrint jasperPrint) throws JRException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024 * 150);
    JRPdfExporter exporter = new JRPdfExporter();
    exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
    exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));
    exporter.exportReport();
    return baos.toByteArray();
}
    private CandidateDTO createModifiedCandidateForHtml(CandidateDTO original) {
        CandidateDTO m = new CandidateDTO();
        m.setSalutation(original.getSalutation());
        m.setCandidateName(original.getCandidateName());
        m.setJobRole(original.getJobRole());
        m.setGuardianType(original.getGuardianType());
        m.setFatherORHusbandName(original.getFatherORHusbandName());
        m.setSectorSkillCouncil(original.getSectorSkillCouncil());
        m.setDateOfIssuance(original.getDateOfIssuance());
        m.setLevel(original.getLevel());
        m.setAadhaarNumber(original.getAadhaarNumber());
        m.setSector(original.getSector());
        m.setGrade(original.getGrade());
        m.setDateOfStart(original.getDateOfStart());
        m.setDateOfEnd(original.getDateOfEnd());
        m.setMarks(original.getMarks());
        m.setMarks1(original.getMarks1());
        m.setMarks2(original.getMarks2());
        m.setMarks3(original.getMarks3());
        m.setMarks4(original.getMarks4());
        m.setMarks5(original.getMarks5());
        m.setMarks6(original.getMarks6());
        m.setMarks7(original.getMarks7());
        m.setMarks8(original.getMarks8());
        m.setMarks9(original.getMarks9());
        m.setMarks10(original.getMarks10());
        m.setBatchId(original.getBatchId());
        m.setState(original.getState());
        m.setDistrict(original.getDistrict());
        m.setCourseName(original.getCourseName());
        m.setDuration(original.getDuration());
        m.setPlace(original.getPlace());
        m.setTemplate(original.getTemplate());
        m.setSid(original.getSid());
        m.setEarning(original.getEarning());
        m.setCredit(original.getCredit());
        m.setTrainingCenter(original.getTrainingCenter());
        return m;
    }
    private Map<String, Object> createJasperParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(JRParameter.REPORT_LOCALE, Locale.ENGLISH);
        parameters.put("net.sf.jasperreports.default.font.name", "Times New Roman");
        parameters.put("net.sf.jasperreports.default.pdf.encoding", "Identity-H");
        parameters.put("net.sf.jasperreports.default.pdf.embedded", true);
        parameters.put("net.sf.jasperreports.export.pdf.font.embedded", true);
        parameters.put("net.sf.jasperreports.print.keep.full.text", true);
        parameters.put("net.sf.jasperreports.export.pdf.force.linebreak.policy", true);
        parameters.put("net.sf.jasperreports.text.truncate.at.char", false);
        parameters.put("net.sf.jasperreports.text.truncate.suffix", "");
        parameters.put("net.sf.jasperreports.text.markup.html", "html");
        parameters.put("net.sf.jasperreports.markup.parser.html.enabled", true);
        parameters.put("net.sf.jasperreports.awt.ignore.missing.font", "true");
        return parameters;
    }
    private void setupImageParameters(Map<String, Object> parameters, List<File> templateStaticImages, List<File> baseStaticImages, File extractedZipFolder, int imageType, Map<String, File> uploadedFiles, CandidateDTO candidate, Template template
    ) {
//        Set<String> jrxmlParams = extractImageParameters(template.getJrxmlPath());
        Set<String> jrxmlParams = jrxmlParamCache.computeIfAbsent(template.getJrxmlPath(), this::extractImageParameters);
        List<File> all = new ArrayList<>();
        if (templateStaticImages != null)
            all.addAll(templateStaticImages);
        if (baseStaticImages != null)
            all.addAll(baseStaticImages);

        for (File image : all) {
            String fileName = image.getName();
            int dot = fileName.lastIndexOf('.');
            if (dot <= 0) continue;
            String paramName = fileName.substring(0, dot);
            if (jrxmlParams.contains(paramName)) {
                parameters.put(paramName, image.getAbsolutePath());
            }
        }

        File bg = all.stream().filter(f -> f.getName().toLowerCase().contains("bg")).findFirst().orElse(null);
        if (bg != null && jrxmlParams.contains("imgParamBG")) {
            parameters.put("imgParamBG", bg.getAbsolutePath());
        }

        for (File image : all) {
            String fileName = image.getName().toLowerCase();
            int dot = fileName.lastIndexOf('.');
            if (dot <= 0) continue;

            String nameWithoutExt = fileName.substring(0, dot);
            if (nameWithoutExt.startsWith("img")) {
                String number = nameWithoutExt.replaceAll("[^0-9]", "");

                if (!number.isEmpty()) {
                    String paramName = "imgParam" + number;
                    if (jrxmlParams.contains(paramName)) {
                        parameters.put(paramName, image.getAbsolutePath());
                    }
                }
            }

            // CASE 2: Any BG image → imgParamBG
            if (nameWithoutExt.contains("bg")
                    && jrxmlParams.contains("imgParamBG")) {
                parameters.put("imgParamBG", image.getAbsolutePath());
            }
        }
        if (imageType >= 1 && extractedZipFolder != null) {
            File candidateImg = findCandidateImage(extractedZipFolder, candidate.getSid());
            if (candidateImg != null && jrxmlParams.contains("imgParam3")) {
                parameters.put("imgParam3", candidateImg.getAbsolutePath());
            }
        }

        if (imageType >= 2 && uploadedFiles != null && uploadedFiles.containsKey("logo") && jrxmlParams.contains("imgParam4")) {
            parameters.put("imgParam4", uploadedFiles.get("logo").getAbsolutePath());
        }

        if (imageType >= 3 && uploadedFiles != null) {
            if (uploadedFiles.containsKey("logo") && jrxmlParams.contains("imgParam4")) {
                parameters.put("imgParam4", uploadedFiles.get("logo").getAbsolutePath());
            }

            if (uploadedFiles.containsKey("signature") && jrxmlParams.contains("imgParam5")) {
                parameters.put("imgParam5", uploadedFiles.get("signature").getAbsolutePath()
                );
            }
        }
        if (imageType == 6 && uploadedFiles != null) {
            for (int i = 1; i <= 15; i++) {
                String paramName = "imgParam" + i;
                if (jrxmlParams.contains(paramName)) {
                    String uploadKey = "img" + i;
                    if (uploadedFiles.containsKey(uploadKey)) {
                        File dynamicImage = uploadedFiles.get(uploadKey);
                        if (dynamicImage != null && dynamicImage.exists()) {
                            parameters.put(paramName, dynamicImage.getAbsolutePath());
                        }
                    }
                }
            }
            if (uploadedFiles.containsKey("bg") && jrxmlParams.contains("imgParamBG")) {
                parameters.put("imgParamBG", uploadedFiles.get("bg").getAbsolutePath());
            }
        }
    }
    private List<File> loadStaticImages(String folderPath) {
        List<File> images = new ArrayList<>();
        if (folderPath == null) return images;
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) return images;
        File[] files = folder.listFiles();
        if (files == null) return images;
        for (File f : files) if (f.isFile() && isImageFile(f.getName())) images.add(f);
        return images;
    }
    private void unzipAndRenameImages(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String entryName = new File(entry.getName()).getName();
                int dot = entryName.lastIndexOf('.');
                String ext = dot > 0 ? entryName.substring(dot).toLowerCase() : "";
                if (!isImageFile(entryName)) {
                    throw new RuntimeException("Invalid file inside ZIP: " + entryName + ". Only image files are allowed for Image Type 1.");
                }
                String name = dot > 0 ? entryName.substring(0, dot) : entryName;
                File newFile = new File(destDir, name + ext);

                if (newFile.exists()) {
                    newFile.delete();
                }

                try (FileOutputStream fos = new FileOutputStream(newFile, false)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = zis.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                }

                newFile.setLastModified(System.currentTimeMillis());
                zis.closeEntry();
            }
        }
    }

    private File findCandidateImage(File folder, String sid) {
        if (folder == null || sid == null) return null;

        File[] files = folder.listFiles();
        if (files == null) return null;

        String cleanSid = sid.trim().toLowerCase();

        // First priority: exact SID match, example 1001.jpg
        Optional<File> exactMatch = Arrays.stream(files)
                .filter(File::isFile)
                .filter(f -> isImageFile(f.getName()))
                .filter(f -> {
                    String fileName = f.getName().toLowerCase();
                    int dot = fileName.lastIndexOf('.');
                    String nameWithoutExt = dot > 0 ? fileName.substring(0, dot) : fileName;
                    return nameWithoutExt.equals(cleanSid);
                })
                .max(Comparator.comparingLong(File::lastModified));

        if (exactMatch.isPresent()) {
            logger.info("Latest exact image selected for SID {}: {}", sid, exactMatch.get().getAbsolutePath());
            return exactMatch.get();
        }

        // Second priority: contains SID, example candidate_1001.jpg
        File latestImage = Arrays.stream(files)
                .filter(File::isFile)
                .filter(f -> isImageFile(f.getName()))
                .filter(f -> {
                    String fileName = f.getName().toLowerCase();
                    int dot = fileName.lastIndexOf('.');
                    String nameWithoutExt = dot > 0 ? fileName.substring(0, dot) : fileName;
                    return nameWithoutExt.contains(cleanSid);
                })
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);

        if (latestImage != null) {
            logger.info("Latest contains-match image selected for SID {}: {}", sid, latestImage.getAbsolutePath());
        }

        return latestImage;
    }
    private boolean isImageFile(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    private List<CandidateDTO> parseExcel(File excelFile, Template template) throws Exception {

        List<CandidateDTO> candidates = new ArrayList<>();

        if (excelFile == null || !excelFile.exists()) {
            throw new FileNotFoundException("Excel file missing");
        }

        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            if (sheet == null) {
                throw new Exception("No sheet found in Excel");
            }

            // ================= HEADER ROW =================

            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new Exception("Excel header row missing");
            }

            // ================= CREATE HEADER MAP =================
            Map<String, Integer> headerMap = new HashMap<>();

            for (Cell cell : headerRow) {

                if (cell == null) continue;

                String rawHeader = cell.toString();

                String normalizedHeader = normalizeHeader(rawHeader);

                if (!normalizedHeader.isEmpty()) {
                    headerMap.put(normalizedHeader, cell.getColumnIndex());
                }
            }

            logger.info("Excel headers detected: {}", headerMap.keySet());

            // ================= READ DATA ROWS =================
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);

                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                CandidateDTO candidate = createCandidateFromRow(row, headerMap, template);

                if (isValidCandidate(candidate)) {
                    candidates.add(candidate);
                }
            }
        }

        logger.info("Total candidates parsed: {}", candidates.size());

        return candidates;
    }

    private CandidateDTO createCandidateFromRow(Row row, Map<String, Integer> headerMap, Template template) {
        CandidateDTO candidate = new CandidateDTO();
        candidate.setSalutation(getValue(row, headerMap, "salutation", 0));
        candidate.setCandidateName(getValue(row, headerMap, "candidateName", 1));
        candidate.setSid(getValue(row, headerMap, "sid", 2));
        candidate.setJobRole(getValue(row, headerMap, "jobRole", 3));
        candidate.setGuardianType(getValue(row, headerMap, "guardianType", 4));
        candidate.setFatherORHusbandName(getValue(row, headerMap, "fatherORHusbandName", 5));
        candidate.setSectorSkillCouncil(getValue(row, headerMap, "sectorSkillCouncil", 6));
        candidate.setDateOfIssuance(getValue(row, headerMap, "dateOfIssuance", 7));
        candidate.setLevel(getValue(row, headerMap, "level", 8));
        candidate.setAadhaarNumber(getValue(row, headerMap, "aadhaarNumber", 9));
        candidate.setSector(getValue(row, headerMap, "sector", 10));
        candidate.setGrade(getValue(row, headerMap, "grade", 11));
        candidate.setDateOfStart(getValue(row, headerMap, "dateOfStart", 12));
        candidate.setDateOfEnd(getValue(row, headerMap, "dateOfEnd", 13));
        candidate.setMarks(getValue(row, headerMap, "marks", 14));
        candidate.setMarks1(getValue(row, headerMap, "marks1", 15));
        candidate.setMarks2(getValue(row, headerMap, "marks2", 16));
        candidate.setMarks3(getValue(row, headerMap, "marks3", 17));
        candidate.setMarks4(getValue(row, headerMap, "marks4", 18));
        candidate.setMarks5(getValue(row, headerMap, "marks5", 19));
        candidate.setMarks6(getValue(row, headerMap, "marks6", 20));
        candidate.setMarks7(getValue(row, headerMap, "marks7", 21));
        candidate.setMarks8(getValue(row, headerMap, "marks8",22));
        candidate.setMarks9(getValue(row, headerMap, "marks9",23));
        candidate.setMarks10(getValue(row, headerMap, "marks10",24));
        candidate.setBatchId(getValue(row, headerMap, "batchId", 25));
        candidate.setState(getValue(row, headerMap, "state", 26));
        candidate.setDistrict(getValue(row, headerMap, "district", 27));
        candidate.setPlace(getValue(row, headerMap, "place", 28));
        candidate.setDuration(getValue(row, headerMap, "duration", 29));
        candidate.setEarning(getValue(row, headerMap, "earning", 30));
        candidate.setCredit(getValue(row, headerMap, "credit", 31));
        candidate.setTrainingCenter(getValue(row, headerMap, "trainingCenter", 32));
        candidate.setCourseName(getValue(row, headerMap, "courseName", 33));
        candidate.setDateofbirth(getValue(row, headerMap, "dateofbirth", 34));
        candidate.setRegistrationNo(getValue(row, headerMap, "registrationNo", 35));
        candidate.setCandidateId(getValue(row, headerMap, "candidateId", 36));
        candidate.setRollno(getValue(row, headerMap, "rollno", 37));
        candidate.setSchoolName(getValue(row, headerMap, "SchoolName",38));
        candidate.setClassName(getValue(row, headerMap, "className", 39));
        candidate.setCertificateNo(getValue(row, headerMap, "certificateNo", 40));
        candidate.setTemplate(template);
        return candidate;
    }

    private String normalizeHeader(String header) {
        if (header == null) return "";
        return header.toLowerCase().replaceAll("[^a-z0-9]", "");
    }


    private String getValue(Row row, Map<String, Integer> headerMap, String fieldName, int columnIndex) {
        if (columnIndex >= 0) {
            Cell cell = row.getCell(columnIndex);
            String value = getSafeCellValue(cell);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        Integer headerIndex = headerMap.get(fieldName.toLowerCase().replaceAll("[^a-z0-9]", ""));
        if (headerIndex != null) {
            Cell cell = row.getCell(headerIndex);
            return getSafeCellValue(cell);
        }

        return "";
    }

//    private String getSafeCellValue(Cell cell) {
//        if (cell == null) return "";
//        try {
//            switch (cell.getCellType()) {
//                case STRING:
//                    return cell.getStringCellValue().trim();
//                case NUMERIC:
//                    if (DateUtil.isCellDateFormatted(cell)) {
//                        return new SimpleDateFormat("dd-MMM-yyyy").format(cell.getDateCellValue());
//                    }
//                    double val = cell.getNumericCellValue();
//                    if (val == Math.floor(val)) return String.valueOf((long) val);
//                    return String.valueOf(val);
//                case BOOLEAN:
//                    return String.valueOf(cell.getBooleanCellValue());
//                case FORMULA:
//                    try {
//                        FormulaEvaluator evaluator = cell.getSheet().getWorkbook()
//                                .getCreationHelper().createFormulaEvaluator();
//                        CellValue cv = evaluator.evaluate(cell);
//                        switch (cv.getCellType()) {
//                            case STRING:
//                                return cv.getStringValue().trim();
//                            case NUMERIC:
//                                if (DateUtil.isCellDateFormatted(cell)) {
//                                    return new SimpleDateFormat("dd-MMM-yyyy").format(cell.getDateCellValue());
//                                }
//                                double fv = cv.getNumberValue();
//                                if (fv == Math.floor(fv)) return String.valueOf((long) fv);
//                                return String.valueOf(fv);
//                            case BOOLEAN:
//                                return String.valueOf(cv.getBooleanValue());
//                            default:
//                                return "";
//                        }
//                    } catch (Exception e) {
//                        return cell.toString().trim();
//                    }
//                default:
//                    return cell.toString().trim();
//            }
//        } catch (Exception e) {
//            return "";
//        }
//    }


    private String getSafeCellValue(Cell cell) {
        if (cell == null) return "";

        try {
            switch (cell.getCellType()) {

                case STRING:
                    return cell.getStringCellValue().trim();

                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return new SimpleDateFormat("dd-MMM-yyyy").format(cell.getDateCellValue());
                    }

                    double val = cell.getNumericCellValue();

                    // Excel stores 66.667% as 0.66667
                    if (isPercentageCell(cell)) {
                        return formatPercentage(val);
                    }

                    return formatNormalNumber(val);

                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());

                case FORMULA:
                    try {
                        FormulaEvaluator evaluator = cell.getSheet()
                                .getWorkbook()
                                .getCreationHelper()
                                .createFormulaEvaluator();

                        CellValue cv = evaluator.evaluate(cell);

                        switch (cv.getCellType()) {

                            case STRING:
                                return cv.getStringValue().trim();

                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    return new SimpleDateFormat("dd-MMM-yyyy").format(cell.getDateCellValue());
                                }

                                double fv = cv.getNumberValue();

                                // Formula percentage value
                                if (isPercentageCell(cell)) {
                                    return formatPercentage(fv);
                                }

                                return formatNormalNumber(fv);

                            case BOOLEAN:
                                return String.valueOf(cv.getBooleanValue());

                            default:
                                return "";
                        }

                    } catch (Exception e) {
                        return cell.toString().trim();
                    }

                default:
                    return cell.toString().trim();
            }

        } catch (Exception e) {
            return "";
        }
    }


    private boolean isPercentageCell(Cell cell) {
        if (cell == null || cell.getCellStyle() == null) {
            return false;
        }

        String format = cell.getCellStyle().getDataFormatString();

        return format != null && format.contains("%");
    }

    private String formatPercentage(double excelValue) {
        // Example: 0.66667 -> 66.667
        double percentValue = excelValue * 100;

        BigDecimal bd = BigDecimal.valueOf(percentValue)
                .setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros();

        return bd.toPlainString();
    }

    private String formatNormalNumber(double value) {
        BigDecimal bd = BigDecimal.valueOf(value)
                .setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros();

        return bd.toPlainString();
    }



    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK && cell.toString().trim().length() > 0) {
                return false;
            }
        }
        return true;
    }
    // ================= PREVIEW DEFAULT IMAGES =================
    private void setupPreviewImages(Map<String, Object> parameters, Template template, CandidateDTO candidate) {
        File previewDir = new File("src/main/resources/preview");
        File defaultPhoto = new File(previewDir, "default_photo.jpg");
        File defaultLogo = new File(previewDir, "default_logo.png");
        File defaultSign = new File(previewDir, "default_signature.png");
        int imageType = template.getImageType();
        if (imageType >= 1 && defaultPhoto.exists()) {
            parameters.put("imgParam3", defaultPhoto.getAbsolutePath());
        }

        if (imageType >= 2 && defaultLogo.exists()) {
            parameters.put("imgParam4", defaultLogo.getAbsolutePath());
        }

        if (imageType >= 3 && defaultSign.exists()) {
            parameters.put("imgParam4", defaultLogo.getAbsolutePath());
            parameters.put("imgParam5", defaultSign.getAbsolutePath());
        }
    }

    //=========================Preview==============================

    public byte[] previewTemplate(Long templateId, UserPrincipal currentUser) {
        try {
            Template template = templateRepository.findById(templateId).orElseThrow(() -> new RuntimeException("Template not found"));
            CandidateDTO dummy = new CandidateDTO();
            dummy.setSalutation("Mr/Ms");
            dummy.setCandidateName("Dummy");
            dummy.setSid("1234");
            dummy.setJobRole("jobRole");
            dummy.setGrade("A");
            dummy.setLevel("Level");
            dummy.setDateOfIssuance("Date ");
            dummy.setTemplate(template);
            dummy.setFatherORHusbandName("Dummy");
            dummy.setGuardianType("S/o"+" "+"D/o");
            List<File> templateImages = loadStaticImages(template.getTemplateFolder());
            List<File> baseImages = loadStaticImages(baseTemplateFolder);
            JasperReport jasperReport;
            try (InputStream jrxmlStream = new FileInputStream(template.getJrxmlPath())) {
                jasperReport = JasperCompileManager.compileReport(jrxmlStream);
                }
            Map<String, Object> parameters = createJasperParameters();
            setupImageParameters(parameters, templateImages, baseImages, null, template.getImageType(), null, dummy, template);
            setupPreviewImages(parameters, template, dummy);
            JasperPrint print = JasperFillManager.fillReport(jasperReport, parameters, new JRBeanCollectionDataSource(Collections.singletonList(dummy)));
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new RuntimeException(getRootCauseMessage(e), e);
        }
    }
    private boolean isValidCandidate(CandidateDTO c) {
        return c.getSid() != null && !c.getSid().trim().isEmpty() && c.getCandidateName() != null && !c.getCandidateName().trim().isEmpty();
    }
    public byte[] generateCertificatesZipBytes(String jobId, Long templateId, File excelFile, Map<String, File> uploadedFiles, UserPrincipal currentUser) {
        try {
            Map<String, Object> result = generateCertificatesAndReports(templateId, excelFile, uploadedFiles, null, currentUser);
            if (Boolean.TRUE.equals(result.get("error"))) {
                throw new RuntimeException(String.valueOf(result.get("message"))
                );
            }
            Map<String, byte[]> pdfMap = (Map<String, byte[]>) result.get("pdfByteMap");
            if (pdfMap == null || pdfMap.isEmpty()) {
                throw new RuntimeException("No certificates generated. ZIP export aborted.");
            }
            ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(zipBaos);
            for (Map.Entry<String, byte[]> entry : pdfMap.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
            zos.finish();
            zos.close();
            return zipBaos.toByteArray();
        } catch (Exception e) {
            logger.error("ZIP generation aborted", e);
            throw new RuntimeException("ZIP export failed: " + e.getMessage(), e);
        }
    }
    private Set<String> extractFieldsFromJrxml(String jrxmlPath) {
        try {
            JasperDesign design = JRXmlLoader.load(jrxmlPath);
            return Arrays.stream(design.getFields()).map(JRField::getName).collect(Collectors.toSet());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read JRXML fields", e);
        }
    }
    private void validateJrxmlFieldsWithDTO(String jrxmlPath, Class<?> dtoClass) {
        Set<String> jrxmlFields = extractFieldsFromJrxml(jrxmlPath);

        Set<String> dtoFields = Arrays.stream(dtoClass.getMethods()).filter(m -> m.getName().startsWith("get") && m.getParameterCount() == 0)
                .map(m -> {
                    String name = m.getName().substring(3);
                    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
                })
                .collect(Collectors.toSet());

        List<String> missing = new ArrayList<>();
        for (String field : jrxmlFields) {
            if (!dtoFields.contains(field)) {
                missing.add(field);
            }
        }

        if (!missing.isEmpty()) {
            throw new RuntimeException("JRXML ↔ CandidateDTO mismatch. Missing fields in DTO: " + String.join(", ", missing)
            );
        }
    }
    @Async
    @Transactional
    public void startGenerationAsync(String jobId, Long templateId, File excelFile,
                                     Map<String, File> uploadedFiles, UserPrincipal user) {

        ProgressDTO progress = new ProgressDTO();
        ByteArrayOutputStream baos = null;
        ZipOutputStream zos = null;

        try {
            Template template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new RuntimeException("Template not found"));

            int imageType = template.getImageType();

            List<CandidateDTO> candidates = parseExcel(excelFile, template);

            if (candidates == null || candidates.isEmpty()) {
                throw new RuntimeException("No candidates found in Excel");
            }

            logger.info("Candidates parsed from Excel: {}", candidates.size());

            progress.setTotal(candidates.size());
            progress.setCompleted(0);
            progress.setFinished(false);
            progress.setMessage("Processing started");
            progressMap.put(jobId, progress);

            List<File> templateImages = loadStaticImages(template.getTemplateFolder());
            List<File> baseImages = loadStaticImages(baseTemplateFolder);

            File extractedZipFolder = null;

            /*
             * Type 1 = candidate image ZIP
             * Type 2 = candidate image ZIP + logo
             * Type 3 = candidate image ZIP + logo + signature
             */
            if (imageType == 1) {
                if (uploadedFiles == null || !uploadedFiles.containsKey("zipImage")) {
                    throw new RuntimeException("Candidate image ZIP file is required for Image Type 1");
                }

                extractedZipFolder = extractZipImages(uploadedFiles, new File(baseTemplateFolder));
            }

            if (imageType == 2) {
                if (uploadedFiles == null || !uploadedFiles.containsKey("logo")) {
                    throw new RuntimeException("Logo image is required for Image Type 2");
                }

                extractedZipFolder = null;
            }

            if (imageType == 3) {
                if (uploadedFiles == null || !uploadedFiles.containsKey("logo")) {
                    throw new RuntimeException("Logo image is required for Image Type 3");
                }

                if (!uploadedFiles.containsKey("signature")) {
                    throw new RuntimeException("Signature image is required for Image Type 3");
                }

                extractedZipFolder = null;
            }

            if (imageType == 5) {
                if (uploadedFiles == null ||
                        (!uploadedFiles.containsKey("pdfZip")
                                && !uploadedFiles.containsKey("uploadedZip")
                                && !uploadedFiles.containsKey("zipFile"))) {
                    throw new RuntimeException("PDF ZIP file is required for Image Type 5");
                }
            }

            baos = new ByteArrayOutputStream();
            zos = new ZipOutputStream(new BufferedOutputStream(baos));

            List<CandidateDTO> missingCandidates = new ArrayList<>();
            int completed = 0;

            /*
             * Type 5 अलग merge flow है.
             * Agar aap Type 5 async me use kar rahe ho, neeche helper method bhi add karo.
             */
            if (imageType == 5) {
                completed = processType5DirectToZip(
                        template,
                        candidates,
                        uploadedFiles,
                        user,
                        zos,
                        progress
                );
            } else {

                for (CandidateDTO candidate : candidates) {

                    String sid = candidate.getSid();

                    if (sid == null || sid.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        logger.info("Generating certificate for SID: {}", sid);

                        if (imageType == 1) {
                            File candidateImage = findCandidateImage(extractedZipFolder, sid);

                            if (candidateImage == null) {
                                logger.warn("Photo missing for SID: {}", sid);
                                missingCandidates.add(candidate);
                                continue;
                            }
                        }

                        byte[] pdfBytes = generateCertificateForCandidateBytes(
                                template,
                                candidate,
                                templateImages,
                                baseImages,
                                extractedZipFolder,
                                imageType,
                                uploadedFiles
                        );

                        if (pdfBytes == null || pdfBytes.length == 0) {
                            logger.warn("Empty PDF generated for SID: {}", sid);
                            continue;
                        }

                        Report report = createReport(candidate, user);
                        reportService.saveOrUpdateBySid(report, user);

                        String fileName = buildPdfFileName(candidate, imageType);
                        String zipPath = buildZipPath(template, candidate, fileName);

                        // Certificate generate hote hi ZIP me insert
                        zos.putNextEntry(new ZipEntry(zipPath));
                        zos.write(pdfBytes);
                        zos.closeEntry();

                        completed++;
                        progress.setCompleted(completed);
                        progress.setMessage("Generated " + completed + " of " + candidates.size());

                        logger.info("Certificate added to ZIP: {}", zipPath);

                    } catch (Exception e) {
                        logger.error("Certificate generation failed for SID: {}", sid, e);

                        String rootCause = getRootCauseMessage(e);
                        progress.markAsFailed("Failed for SID " + sid + ": " + rootCause);

                        zipStorage.remove(jobId);

                        try {
                            if (zos != null) {
                                zos.close();
                            }
                        } catch (Exception ignore) {}

                        throw new RuntimeException(rootCause, e);
                    }
                }

                if (!missingCandidates.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("===== MISSING PHOTO REPORT =====\n\n");

                    for (CandidateDTO c : missingCandidates) {
                        sb.append("SID: ")
                                .append(c.getSid())
                                .append(" | Name: ")
                                .append(c.getCandidateName())
                                .append(" | PHOTO MISSING\n");
                    }

                    zos.putNextEntry(new ZipEntry("Missing_Photos_Report.txt"));
                    zos.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }

            // Final ZIP complete
            zos.finish();
            zos.close();

            logger.info("ZIP generated successfully, size: {} bytes", baos.size());

            if (baos.size() == 0) {
                throw new RuntimeException("ZIP generation failed. No certificates generated.");
            }

            zipStorage.put(jobId, baos.toByteArray());

            progress.setCompleted(completed);
            progress.setFinished(true);

            if (imageType != 5 && !missingCandidates.isEmpty()) {
                progress.setMessage("Completed with " + missingCandidates.size() + " missing photos");
            } else {
                progress.setMessage("Completed");
            }

        } catch (Exception e) {
            logger.error("Async generation failed", e);

            progress.markAsFailed(e.getMessage());
            zipStorage.remove(jobId);

            try {
                if (zos != null) {
                    zos.close();
                }
            } catch (Exception ignore) {}

        } finally {
            try {
                if (excelFile != null && excelFile.exists()) {
                    excelFile.delete();
                }

                if (uploadedFiles != null) {
                    for (File f : uploadedFiles.values()) {
                        if (f != null && f.exists()) {
                            f.delete();
                        }
                    }
                }

            } catch (Exception cleanupEx) {
                logger.warn("Cleanup failed", cleanupEx);
            }
        }
    }

    private int processType5DirectToZip(Template template,
                                        List<CandidateDTO> candidates,
                                        Map<String, File> uploadedFiles,
                                        UserPrincipal user,
                                        ZipOutputStream zos,
                                        ProgressDTO progress) throws Exception {

        if (uploadedFiles == null) {
            throw new RuntimeException("PDF ZIP is required for Image Type 5");
        }

        File uploadedPdfZip = null;

        if (uploadedFiles.containsKey("pdfZip")) {
            uploadedPdfZip = uploadedFiles.get("pdfZip");
        } else if (uploadedFiles.containsKey("uploadedZip")) {
            uploadedPdfZip = uploadedFiles.get("uploadedZip");
        } else if (uploadedFiles.containsKey("zipFile")) {
            uploadedPdfZip = uploadedFiles.get("zipFile");
        }

        if (uploadedPdfZip == null || !uploadedPdfZip.exists()) {
            throw new RuntimeException("PDF ZIP file is required for Image Type 5");
        }

        validatePdfZipOnly(uploadedPdfZip);

        Map<String, byte[]> type4PdfMap = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(uploadedPdfZip))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String fileName = new File(entry.getName()).getName();

                if (!fileName.toLowerCase().endsWith(".pdf")) {
                    continue;
                }

                String rawSid = fileName.replace(".pdf", "").trim();
                String normalizedSid = normalizeSid(rawSid);

                ByteArrayOutputStream tempBaos = new ByteArrayOutputStream();
                zis.transferTo(tempBaos);

                type4PdfMap.put(normalizedSid, tempBaos.toByteArray());

                zis.closeEntry();
            }
        }

        List<File> templateImages = loadStaticImages(template.getTemplateFolder());
        List<File> baseImages = loadStaticImages(baseTemplateFolder);

        int completed = 0;

        for (CandidateDTO candidate : candidates) {

            String sid = candidate.getSid();

            if (sid == null || sid.trim().isEmpty()) {
                continue;
            }

            String normalizedSid = normalizeSid(sid);
            byte[] existingPdf = type4PdfMap.get(normalizedSid);

            if (existingPdf == null) {
                logger.warn("Type 5 base PDF missing for SID: {}", sid);
                continue;
            }

            byte[] generatedPdf = generateCertificateForCandidateBytes(
                    template,
                    candidate,
                    templateImages,
                    baseImages,
                    null,
                    5,
                    null
            );

            if (generatedPdf == null || generatedPdf.length == 0) {
                logger.warn("Generated Type 5 PDF empty for SID: {}", sid);
                continue;
            }

            byte[] mergedPdf = mergePdfBytes(existingPdf, generatedPdf);

            String fileName = buildPdfFileName(candidate, 5);
            String zipPath = buildZipPath(template, candidate, fileName);

            zos.putNextEntry(new ZipEntry(zipPath));
            zos.write(mergedPdf);
            zos.closeEntry();

            Report report = createReport(candidate, user);
            reportService.saveOrUpdateBySid(report, user);

            completed++;
            progress.setCompleted(completed);
            progress.setMessage("Generated " + completed + " of " + candidates.size());

            logger.info("Type 5 merged certificate added to ZIP: {}", zipPath);
        }

        return completed;
    }


    private static final Map<String, byte[]> zipStorage = new ConcurrentHashMap<>();
    public ProgressDTO getProgress(String jobId) {
        return progressMap.get(jobId);
    }
    public byte[] getGeneratedZip(String jobId) {
        return zipStorage.get(jobId);
    }
    public void initializeProgress(String jobId) {
        ProgressDTO progress = new ProgressDTO();
        progress.setTotal(0);
        progress.setCompleted(0);
        progress.setFinished(false);
        progress.setMessage("Started");
        progressMap.put(jobId, progress);
    }
    private Set<String> extractImageParameters(String jrxmlPath) {
        try {
            JasperDesign design = JRXmlLoader.load(jrxmlPath);
            return Arrays.stream(design.getParameters()).map(p -> p.getName()).filter(name -> name != null && !name.trim().isEmpty()).collect(Collectors.toSet());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read JRXML parameters", e);
        }
    }
    private void validatePdfZipOnly(File zipFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String entryName = entry.getName().toLowerCase();
                if (!entryName.endsWith(".pdf")) {
                    throw new RuntimeException("Invalid file inside ZIP: " + entry.getName() + ". Only PDF files are allowed for Image Type 5.");
                }
                zis.closeEntry();
            }
        }
    }
    private String buildPdfFileName(CandidateDTO candidate, int imageType) {
        String safeSid = candidate.getSid().replaceAll("[^a-zA-Z0-9_-]", "");
        if (imageType == 4) {
            return safeSid + ".pdf";
        }
        String safeName = "Unknown";
        if (candidate.getCandidateName() != null) {
            safeName = candidate.getCandidateName().replaceAll("[^a-zA-Z0-9\\-_]", " ").trim();
        }
        return safeSid + "_" + safeName + ".pdf";
    }

    private String normalizeSid(String sid) {
        if (sid == null) return "";
        return sid.replaceAll("[^a-zA-Z0-9]", " ").toLowerCase().trim();
    }
        private void saveReport(CandidateDTO candidate, Template template, UserPrincipal currentUser) {
        Report report = new Report();
        report.setSid(candidate.getSid());
        report.setCandidateName(candidate.getCandidateName());
        report.setTemplate(template);
        report.setTemplateName(template.getTemplateName());
        reportService.saveOrUpdateBySid(report, currentUser);
    }

    private String buildZipPath(Template template, CandidateDTO candidate, String fileName) {

        if (!template.isHierarchyRequired()) {
            return fileName;
        }
        String district = clean(candidate.getDistrict());
        String SchoolName = clean(candidate.getSchoolName());
        String ClassName= clean(candidate.getClassName());
        return ClassName + "/" + district + "/" + SchoolName +  "/" + fileName;
    }

    private String clean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Unknown";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9]", " ");
    }
    private String getRootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
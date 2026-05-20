package Tech_Nagendra.Certificates_genration.Service;
import Tech_Nagendra.Certificates_genration.Dto.TemplateDto;
import Tech_Nagendra.Certificates_genration.Entity.Template;
import Tech_Nagendra.Certificates_genration.Entity.TemplateImage;
import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
import Tech_Nagendra.Certificates_genration.Repository.TemplateImageRepository;
import Tech_Nagendra.Certificates_genration.Repository.TemplateRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateImageRepository templateImageRepository;
    private final ProfileRepository profileRepository;
    private final CertificateService certificateService;

    //    private static final String TEMPLATE_BASE_PATH =
//            System.getProperty("user.dir") + File.separator + "templates";
    @Value("${app.storage.base-path}")
    private String TEMPLATE_BASE_PATH;

    /* ================= SAVE TEMPLATE ================= */

    public TemplateDto saveTemplate(Long userId,
                                    String templateName,
                                    Integer imageType,
                                    boolean hierarchyRequired,
                                    MultipartFile[] jrxmlFiles,
                                    MultipartFile[] images,
                                    MultipartFile excelFile) throws IOException {

        templateName = templateName.trim();

        UserProfile user = profileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        File folder = new File(TEMPLATE_BASE_PATH, templateName);
        if (!folder.exists()) folder.mkdirs();

        String jrxmlPath = null;
        if (jrxmlFiles != null && jrxmlFiles.length > 0 && !jrxmlFiles[0].isEmpty()) {
            File jrxml = new File(folder, templateName + ".jrxml");
            jrxmlFiles[0].transferTo(jrxml);


            jrxmlPath = jrxml.getAbsolutePath();

        }

        String excelPath = null;
        if (excelFile != null && !excelFile.isEmpty()) {
            File excel = new File(folder, templateName + ".xlsx");
            excelFile.transferTo(excel);
            excelPath = "templates/" + templateName + "/" + excel.getName();
        }

        Template template = new Template();
        template.setTemplateName(templateName);
        template.setImageType(imageType);
        template.setTemplateFolder(folder.getAbsolutePath());
        template.setJrxmlPath(jrxmlPath);
        template.setExcelPath(excelPath);
        template.setCreatedBy(user);
        template.setModifiedBy(user);
        template.setCreatedAt(LocalDateTime.now());
        template.setModifiedAt(LocalDateTime.now());
        template.setStatus(true);
        template.setHierarchyRequired(hierarchyRequired);

        Template saved = templateRepository.save(template);

        List<TemplateImage> imgs =
                saveImageFiles(saved, folder, images, null, imageType);
        saved.setImages(imgs);

        return mapToDto(saved, extractImagePaths(imgs));
    }

    /* ================= UPDATE TEMPLATE ================= */
    @Transactional
    public TemplateDto updateTemplate(Long templateId,
                                      Long updatedBy,
                                      String templateName,
                                      Integer imageType,
                                      Boolean hierarchyRequired,
                                      List<Long> imageIds,
                                      Boolean status,
                                      MultipartFile[] jrxmlFiles,
                                      MultipartFile[] images,
                                      MultipartFile excelFile) throws IOException {

        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        // 🔥 ADD HERE
        if (hierarchyRequired != null) {
            template.setHierarchyRequired(hierarchyRequired);
        }

        UserProfile modifier = profileRepository.findById(updatedBy)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Path oldFolder = Paths.get(template.getTemplateFolder());
        Path newFolder = oldFolder;

        boolean nameChanged = templateName != null
                && !templateName.trim().equals(template.getTemplateName());

        /* ================= RENAME FOLDER ================= */

        if (nameChanged) {
            newFolder = Paths.get(TEMPLATE_BASE_PATH, templateName);


            Files.createDirectories(newFolder.getParent());

            if (Files.exists(oldFolder)) {
                Files.move(oldFolder, newFolder, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.createDirectories(newFolder);
            }

            template.setTemplateName(templateName);
            template.setTemplateFolder(newFolder.toString());

            /* ===== Rename existing JRXML if present ===== */

            if (template.getJrxmlPath() != null) {
                Path oldJrxml = newFolder.resolve(
                        Paths.get(template.getJrxmlPath()).getFileName()
                );

                Path newJrxml = newFolder.resolve(templateName + ".jrxml");

                if (Files.exists(oldJrxml)) {
                    Files.move(oldJrxml, newJrxml, StandardCopyOption.REPLACE_EXISTING);
                }

                template.setJrxmlPath(
                        "templates/" + templateName + "/" + newJrxml.getFileName()
                );
            }

            /* ===== Rename existing Excel if present ===== */

            if (template.getExcelPath() != null) {
                Path oldExcel = newFolder.resolve(
                        Paths.get(template.getExcelPath()).getFileName()
                );

                Path newExcel = newFolder.resolve(templateName + ".xlsx");

                if (Files.exists(oldExcel)) {
                    Files.move(oldExcel, newExcel, StandardCopyOption.REPLACE_EXISTING);
                }

                template.setExcelPath(
                        "templates/" + templateName + "/" + newExcel.getFileName()
                );
            }
        }

        /* ================= UPDATE JRXML ================= */

        if (jrxmlFiles != null && jrxmlFiles.length > 0 && !jrxmlFiles[0].isEmpty()) {
            Files.createDirectories(newFolder);

            File jrxml = new File(newFolder.toFile(),
                    template.getTemplateName() + ".jrxml");

            jrxmlFiles[0].transferTo(jrxml);

            template.setJrxmlPath(
                    "templates/" + template.getTemplateName() + "/" + jrxml.getName()
            );
        }

        /* ================= UPDATE EXCEL ================= */

        if (excelFile != null && !excelFile.isEmpty()) {
            Files.createDirectories(newFolder);

            File excel = new File(newFolder.toFile(),
                    template.getTemplateName() + ".xlsx");

            excelFile.transferTo(excel);

            template.setExcelPath(
                    "templates/" + template.getTemplateName() + "/" + excel.getName()
            );
        }

        /* ================= OTHER UPDATES ================= */

        if (imageType != null) template.setImageType(imageType);
        if (status != null) template.setStatus(status);

        template.setModifiedBy(modifier);
        template.setModifiedAt(LocalDateTime.now());

        List<TemplateImage> imgs =
                saveImageFiles(template, newFolder.toFile(), images, imageIds, imageType);

        template.setImages(imgs);

        templateRepository.save(template);

        return mapToDto(template, extractImagePaths(imgs));
    }

    /* ================= ENABLE / DISABLE ================= */

    @Transactional
    public void enableDisableTemplate(Long templateId, boolean enable) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        template.setStatus(enable);
        template.setModifiedAt(LocalDateTime.now());
        templateRepository.save(template);
    }

    /* ================= GET TEMPLATE BY ID ================= */

    public TemplateDto getTemplateByIdForUser(Long templateId, Long userId, String role) {
        Template template = templateRepository.findById(templateId).orElseThrow(() -> new RuntimeException("Template not found"));
        if (!"ADMIN".equalsIgnoreCase(role) && !template.getCreatedBy().getId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        return mapToDto(template, extractImagePaths(template.getImages()));
    }

    /* ================= GET ALL ================= */

    public List<TemplateDto> getAllTemplates(Long userId, String role) {
        List<Template> templates =
                "ADMIN".equalsIgnoreCase(role) ? templateRepository.findAll() : templateRepository.findByCreatedBy_Id(userId);

        return templates.stream()
                .map(t -> mapToDto(t, extractImagePaths(t.getImages())))
                .toList();
    }

    public List<TemplateDto> getAllTemplatesByUser(Long userId) {
        return templateRepository.findByCreatedBy_Id(userId).stream().map(t -> mapToDto(t, extractImagePaths(t.getImages()))).toList();
    }

    public Long getTotalTemplates(Long userId, String role) {
        return "ADMIN".equalsIgnoreCase(role)
                ? templateRepository.count()
                : templateRepository.countByCreatedBy_Id(userId);
    }

    /* ================= DOWNLOAD EXCEL ================= */

    public Resource downloadExcel(Long templateId) throws IOException {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        if (template.getExcelPath() == null)
            throw new RuntimeException("Excel not uploaded");

        File file = new File(System.getProperty("user.dir")
                + File.separator + template.getExcelPath());

        return new UrlResource(file.toURI());
    }

    /* ================= HELPERS ================= */

//        private List<TemplateImage> saveImageFiles(Template template,
//                                                   File folder,
//                                                   MultipartFile[] images,
//                                                   Integer type) throws IOException {
//
//            List<TemplateImage> saved = new ArrayList<>();
//            if (images == null) return saved;
//
//            for (MultipartFile img : images) {
//                if (img.isEmpty()) continue;
//
//                File f = new File(folder, img.getOriginalFilename());
//                img.transferTo(f);
//
//                TemplateImage ti = new TemplateImage();
//                ti.setTemplate(template);
//                ti.setImagePath(f.getAbsolutePath());
//                ti.setImageType(type);
//
//                saved.add(templateImageRepository.save(ti));
//            }
//            return saved;
//        }

    private List<TemplateImage> saveImageFiles(
            Template template,
            File folder,
            MultipartFile[] images,
            List<Long> imageIds,
            Integer type
    ) throws IOException {

        List<TemplateImage> updatedImages =
                templateImageRepository.findByTemplate_Id(template.getId());

        if (images == null || images.length == 0) {
            return updatedImages;
        }

        for (int i = 0; i < images.length; i++) {

            MultipartFile img = images[i];

            if (img.isEmpty()) continue;

            String fileName = img.getOriginalFilename();
            File file = new File(folder, fileName);

            img.transferTo(file);

            // 🔹 CASE 1 → New template images
            if (imageIds == null || imageIds.size() <= i) {

                TemplateImage newImage = new TemplateImage();
                newImage.setTemplate(template);
                newImage.setImagePath(file.getAbsolutePath());
                newImage.setImageType(type);

                updatedImages.add(templateImageRepository.save(newImage));
            }

            // 🔹 CASE 2 → Replace existing image
            else {

                Long imageId = imageIds.get(i);

                TemplateImage existing =
                        templateImageRepository.findById(imageId)
                                .orElseThrow();

                existing.setImagePath(file.getAbsolutePath());
                existing.setImageType(type);

                templateImageRepository.save(existing);
            }
        }

        return templateImageRepository.findByTemplate_Id(template.getId());
    }





    public Resource downloadTemplateAsZip(Long templateId) throws IOException {

            Template template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new RuntimeException("Template not found"));

            File templateFolder = new File(template.getTemplateFolder());

            if (!templateFolder.exists() || !templateFolder.isDirectory()) {
                throw new RuntimeException("Template folder not found");
            }

            File zipFile = File.createTempFile(
                    template.getTemplateName() + "_",
                    ".zip"
            );

            try (ZipOutputStream zos =
                         new ZipOutputStream(new FileOutputStream(zipFile))) {

                Path basePath = templateFolder.toPath();

                Files.walk(basePath)
                        .filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            try {
                                String zipEntryName = basePath.relativize(path).toString();
                                zos.putNextEntry(new ZipEntry(template.getTemplateName() + "/" + zipEntryName));
                                Files.copy(path, zos);
                                zos.closeEntry();

                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }

            return new UrlResource(zipFile.toURI());
        }

    private List<String> extractImagePaths(List<TemplateImage> images) {
        if (images == null) return Collections.emptyList();
        return images.stream()
                .map(TemplateImage::getImagePath)
                .toList();
    }

    //========================enable templates============

    public Long getEnabledTemplatesCount(Long userId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return templateRepository.countByStatusTrue();
        }
        return templateRepository.countByCreatedBy_IdAndStatusTrue(userId);
    }

//========================Disable templates============

    public Long getDisabledTemplatesCount(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new RuntimeException("Access denied");
        }
        return templateRepository.countByStatusFalse();
    }

    //========================Get jrxml file====================

    public String getJrxmlContent(Long templateId) throws IOException {
        Template template = templateRepository.findById(templateId).orElseThrow(() -> new RuntimeException("Template not found"));
        if (template.getJrxmlPath() == null || template.getJrxmlPath().isBlank()) {
            throw new RuntimeException("JRXML not uploaded");
        }
        Path jrxmlPath = Paths.get(template.getJrxmlPath());
        if (!Files.exists(jrxmlPath)) {
            throw new RuntimeException("JRXML missing at: " + jrxmlPath);
        }
        return Files.readString(jrxmlPath);
    }

    //============update jrml1==============

    @Transactional
    public void updateJrxmlContent(Long templateId, String content) throws IOException {

        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        if (template.getJrxmlPath() == null) {
            throw new RuntimeException("JRXML file path not found in database");
        }

        Path jrxmlPath = Paths.get(template.getJrxmlPath()).normalize();

        if (!Files.exists(jrxmlPath)) {
            throw new RuntimeException("JRXML file does not exist on disk: " + jrxmlPath);
        }

        Files.writeString(jrxmlPath, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        template.setModifiedAt(LocalDateTime.now());
        templateRepository.save(template);
    }

    /* ================= DELETE OLD IMAGES IF NEW UPLOADED ================= */

    private void deleteOldImages(Template template) {
        List<TemplateImage> oldImages = template.getImages();
        if (oldImages == null || oldImages.isEmpty()) return;
        for (TemplateImage img : oldImages) {
            File file = new File(img.getImagePath());
            if (file.exists()) {
                file.delete();
            }

            templateImageRepository.delete(img);
        }

        template.getImages().clear();
    }



//    private TemplateDto mapToDto(Template template, List<String> imagePaths) {
//        return new TemplateDto(
//                template.getId(),
//                template.getTemplateName(),
//                template.getImageType(),
//                imageIds,
//                template.getJrxmlPath(),
//                template.getTemplateFolder(),
//                template.getCreatedAt(),
//                template.getModifiedAt(),
//                imagePaths,
//                template.isStatus()
//        );
//    }
private TemplateDto mapToDto(Template template, List<String> imagePaths) {

    List<Long> imageIds = new ArrayList<>();

    if (template.getImages() != null) {
        for (TemplateImage img : template.getImages()) {
            imageIds.add(img.getId());
        }
    }

    return new TemplateDto(
            template.getId(),
            template.getTemplateName(),
            template.getImageType(),
            imageIds,
            template.getJrxmlPath(),
            template.getTemplateFolder(),
            template.getCreatedAt(),
            template.getModifiedAt(),
            imagePaths,
            template.isStatus()
    );
}
}

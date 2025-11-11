//package Tech_Nagendra.Certificates_genration.Service;
//
//import Tech_Nagendra.Certificates_genration.Dto.TemplateDto;
//import Tech_Nagendra.Certificates_genration.Entity.Template;
//import Tech_Nagendra.Certificates_genration.Entity.TemplateImage;
//import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
//import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
//import Tech_Nagendra.Certificates_genration.Repository.TemplateImageRepository;
//import Tech_Nagendra.Certificates_genration.Repository.TemplateRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.File;
//import java.io.IOException;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Service
//@RequiredArgsConstructor
//public class TemplateService {
//
//    private final TemplateRepository templateRepository;
//    private final TemplateImageRepository templateImageRepository;
//    private final ProfileRepository profileRepository;
//
//    private static final String TEMPLATE_BASE_PATH = "C:/certificate_storage/templates/";
//
//    public TemplateDto saveTemplate(Long userId,
//                                    String templateName,
//                                    Integer imageType,
//                                    MultipartFile[] jrxmlFiles,
//                                    MultipartFile[] images) throws IOException {
//
//        // --- Step 1: Validate and sanitize input
//        if (templateName == null || templateName.trim().isEmpty()) {
//            throw new IllegalArgumentException("Template name cannot be empty");
//        }
//        templateName = templateName.trim();
//
//        UserProfile currentUser = profileRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("Logged-in user not found"));
//
//        // --- Step 2: Ensure base folder exists
//        File baseDir = new File(TEMPLATE_BASE_PATH);
//        if (!baseDir.exists()) {
//            baseDir.mkdirs();
//        }
//
//        // --- Step 3: Create template folder
//        File templateFolder = new File(baseDir, templateName);
//        if (!templateFolder.exists()) {
//            templateFolder.mkdirs();
//        }
//
//        // --- Step 4: Save JRXML files
//        List<String> jrxmlPaths = new ArrayList<>();
//        if (jrxmlFiles != null) {
//            for (MultipartFile jrxml : jrxmlFiles) {
//                if (jrxml.isEmpty()) continue;
//
//                File jrxmlFile = new File(templateFolder, jrxml.getOriginalFilename());
//                jrxml.transferTo(jrxmlFile);
//                jrxmlPaths.add(jrxmlFile.getAbsolutePath());
//            }
//        }
//
//        // --- Step 5: Save template entity
//        Template template = new Template();
//        template.setTemplateName(templateName);
//        template.setImageType(imageType);
//        template.setTemplateFolder(templateFolder.getAbsolutePath());
//        template.setJrxmlPath(String.join(",", jrxmlPaths));
//        template.setCreatedBy(currentUser);
//        template.setModifiedBy(currentUser);
//        template.setCreatedAt(LocalDateTime.now());
//        template.setModifiedAt(LocalDateTime.now());
//
//        Template savedTemplate = templateRepository.save(template);
//
//        // --- Step 6: Save images if provided
//        List<TemplateImage> savedImages = new ArrayList<>();
//        if (images != null) {
//            for (MultipartFile image : images) {
//                if (image.isEmpty()) continue;
//
//                File imgFile = new File(templateFolder, image.getOriginalFilename());
//                image.transferTo(imgFile);
//
//                TemplateImage ti = new TemplateImage();
//                ti.setTemplate(savedTemplate);
//                ti.setImagePath(imgFile.getAbsolutePath());
//                ti.setImageType(imageType);
//                savedImages.add(templateImageRepository.save(ti));
//            }
//        }
//
//        savedTemplate.setImages(savedImages);
//        List<String> imagePaths = savedImages.stream()
//                .map(TemplateImage::getImagePath)
//                .collect(Collectors.toList());
//
//        return mapToDto(savedTemplate, imagePaths);
//    }
//
//    public TemplateDto getTemplateByIdForUser(Long templateId, Long userId, String role) throws Exception {
//        Template template = templateRepository.findById(templateId)
//                .orElseThrow(() -> new RuntimeException("Template not found"));
//
//        if (!"ADMIN".equalsIgnoreCase(role) && !template.getCreatedBy().getId().equals(userId)) {
//            throw new RuntimeException("You do not have access to this template");
//        }
//
//        List<String> imagePaths = template.getImages().stream()
//                .map(TemplateImage::getImagePath)
//                .collect(Collectors.toList());
//
//        return mapToDto(template, imagePaths);
//    }
//
//    public List<TemplateDto> getAllTemplates(Long userId, String role) {
//        List<Template> templates = "ADMIN".equalsIgnoreCase(role)
//                ? templateRepository.findAll()
//                : templateRepository.findByCreatedBy_Id(userId);
//
//        return templates.stream()
//                .map(template -> mapToDto(template,
//                        template.getImages().stream()
//                                .map(TemplateImage::getImagePath)
//                                .collect(Collectors.toList())))
//                .collect(Collectors.toList());
//    }
//
//    public Long getTotalTemplates(Long userId, String role) {
//        return "ADMIN".equalsIgnoreCase(role)
//                ? templateRepository.count()
//                : templateRepository.countByCreatedBy_Id(userId);
//    }
//
//    private TemplateDto mapToDto(Template template, List<String> imagePaths) {
//        return new TemplateDto(
//                template.getId(),
//                template.getTemplateName(),
//                template.getImageType(),
//                template.getJrxmlPath(),
//                template.getTemplateFolder(),
//                template.getCreatedAt(),
//                template.getModifiedAt(),
//                imagePaths
//        );
//    }
//}

package Tech_Nagendra.Certificates_genration.Service;

import Tech_Nagendra.Certificates_genration.Dto.TemplateDto;
import Tech_Nagendra.Certificates_genration.Entity.Template;
import Tech_Nagendra.Certificates_genration.Entity.TemplateImage;
import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
import Tech_Nagendra.Certificates_genration.Repository.TemplateImageRepository;
import Tech_Nagendra.Certificates_genration.Repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateImageRepository templateImageRepository;
    private final ProfileRepository profileRepository;

    private static final String TEMPLATE_BASE_PATH = System.getProperty("user.dir") + "/templates/";

    public TemplateDto saveTemplate(Long userId,
                                    String templateName,
                                    Integer imageType,
                                    MultipartFile[] jrxmlFiles,
                                    MultipartFile[] images) throws IOException {

        if (templateName == null || templateName.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name cannot be empty");
        }
        templateName = templateName.trim();

        UserProfile currentUser = profileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Logged-in user not found"));

        File baseDir = new File(TEMPLATE_BASE_PATH);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        File templateFolder = new File(baseDir, templateName);
        if (!templateFolder.exists()) {
            templateFolder.mkdirs();
        }

        List<String> jrxmlPaths = new ArrayList<>();
        if (jrxmlFiles != null) {
            for (MultipartFile jrxml : jrxmlFiles) {
                if (jrxml.isEmpty()) continue;

                File jrxmlFile = new File(templateFolder, jrxml.getOriginalFilename());
                jrxml.transferTo(jrxmlFile);
                jrxmlPaths.add(jrxmlFile.getAbsolutePath());
            }
        }
        Template template = new Template();
        template.setTemplateName(templateName);
        template.setImageType(imageType);
        template.setTemplateFolder(templateFolder.getAbsolutePath());
        template.setJrxmlPath(String.join(",", jrxmlPaths));
        template.setCreatedBy(currentUser);
        template.setModifiedBy(currentUser);
        template.setCreatedAt(LocalDateTime.now());
        template.setModifiedAt(LocalDateTime.now());

        Template savedTemplate = templateRepository.save(template);

        List<TemplateImage> savedImages = new ArrayList<>();
        if (images != null) {
            for (MultipartFile image : images) {
                if (image.isEmpty()) continue;

                File imgFile = new File(templateFolder, image.getOriginalFilename());
                image.transferTo(imgFile);

                TemplateImage ti = new TemplateImage();
                ti.setTemplate(savedTemplate);
                ti.setImagePath(imgFile.getAbsolutePath());
                ti.setImageType(imageType);
                savedImages.add(templateImageRepository.save(ti));
            }
        }

        savedTemplate.setImages(savedImages);
        List<String> imagePaths = savedImages.stream()
                .map(TemplateImage::getImagePath)
                .collect(Collectors.toList());

        return mapToDto(savedTemplate, imagePaths);
    }

    public TemplateDto getTemplateByIdForUser(Long templateId, Long userId, String role) throws Exception {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        if (!"ADMIN".equalsIgnoreCase(role) && !template.getCreatedBy().getId().equals(userId)) {
            throw new RuntimeException("You do not have access to this template");
        }

        List<String> imagePaths = template.getImages().stream()
                .map(TemplateImage::getImagePath)
                .collect(Collectors.toList());

        return mapToDto(template, imagePaths);
    }

    public List<TemplateDto> getAllTemplates(Long userId, String role) {
        List<Template> templates = "ADMIN".equalsIgnoreCase(role)
                ? templateRepository.findAll()
                : templateRepository.findByCreatedBy_Id(userId);

        return templates.stream()
                .map(template -> mapToDto(template,
                        template.getImages().stream()
                                .map(TemplateImage::getImagePath)
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    public Long getTotalTemplates(Long userId, String role) {
        return "ADMIN".equalsIgnoreCase(role)
                ? templateRepository.count()
                : templateRepository.countByCreatedBy_Id(userId);
    }

    private TemplateDto mapToDto(Template template, List<String> imagePaths) {
        return new TemplateDto(
                template.getId(),
                template.getTemplateName(),
                template.getImageType(),
                template.getJrxmlPath(),
                template.getTemplateFolder(),
                template.getCreatedAt(),
                template.getModifiedAt(),
                imagePaths
        );
    }
}

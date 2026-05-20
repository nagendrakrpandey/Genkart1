package Tech_Nagendra.Certificates_genration.Controller;
import Tech_Nagendra.Certificates_genration.Dto.TemplateDto;
import Tech_Nagendra.Certificates_genration.Service.ProfileService;
import Tech_Nagendra.Certificates_genration.Service.TemplateService;
import Tech_Nagendra.Certificates_genration.Utility.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;

@RestController
@RequestMapping("/templates")
public class TemplateController {

    private final TemplateService templateService;
    private final JwtUtil jwtUtil;
    private final ProfileService profileService;

    @Value("${certificate.template.path:${user.dir}/templates/}")
    private String templateBasePath;

    @Value("${server.port:8787}")
    private String serverPort;

    public TemplateController(TemplateService templateService,
                              JwtUtil jwtUtil,
                              ProfileService profileService) {
        this.templateService = templateService;
        this.jwtUtil = jwtUtil;
        this.profileService = profileService;
    }

    /* ================= TOKEN ================= */

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7).trim();
    }

    /* ================= SAVE TEMPLATE ================= */

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadTemplate(
            @RequestParam("templateName") String templateName,
            @RequestParam("imageType") Integer imageType,
            @RequestParam("createdBy") Long createdBy,
            @RequestParam("hierarchyRequired") boolean hierarchyRequired,
            @RequestPart("jrxml") MultipartFile[] jrxmlFiles,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            @RequestPart(value = "excel", required = false) MultipartFile excelFile,
            HttpServletRequest request
    ) {
        try {
            String token = extractToken(request);
            jwtUtil.validateToken(token, jwtUtil.extractUsername(token));

            TemplateDto saved = templateService.saveTemplate(
                    createdBy,
                    templateName,
                    imageType,
                    hierarchyRequired,
                    jrxmlFiles,
                    images,
                    excelFile
            );

            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Failed to upload template: " + e.getMessage());
        }
    }

    /* ================= UPDATE TEMPLATE ================= */

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateTemplateController(
            @PathVariable("id") Long id,
            @RequestParam("updatedBy") Long updatedBy,
            @RequestParam(value = "templateName", required = false) String templateName,
            @RequestParam(value = "imageType", required = false) Integer imageType,
            @RequestParam(value = "hierarchyRequired", required = false) Boolean hierarchyRequired,
            @RequestParam(value = "status", required = false) Boolean status,
            @RequestParam(value = "imageIds", required = false) List<Long> imageIds,
            @RequestPart(value = "jrxml", required = false) MultipartFile[] jrxmlFiles,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            @RequestPart(value = "excel", required = false) MultipartFile excelFile
    ) {
        try {
            TemplateDto updatedTemplate = templateService.updateTemplate(id, updatedBy, templateName, imageType, hierarchyRequired,   imageIds, status,  jrxmlFiles, images, excelFile);
            return ResponseEntity.ok(Map.of("success", true, "message", "Template updated successfully", "template", updatedTemplate));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }


    /* ================= ENABLE / DISABLE ================= */

    @PutMapping("/{id}/status")
    public ResponseEntity<?> enableDisableTemplate(
            @PathVariable Long id,
            @RequestParam boolean status
    ) {
        templateService.enableDisableTemplate(id, status);
        return ResponseEntity.ok(Map.of(
                "id", id,
                "status", status,
                "message", status ? "Template Enabled" : "Template Disabled"
        ));
    }

    /* ================= GET BY ID ================= */

    @GetMapping("/{id}")
    public ResponseEntity<?> getTemplateById(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        try {
            String token = extractToken(request);
            Long userId = jwtUtil.extractUserId(token);
            String role = jwtUtil.extractRole(token);

            TemplateDto template =
                    templateService.getTemplateByIdForUser(id, userId, role);

            if (!template.isStatus() && !"ADMIN".equalsIgnoreCase(role)) {
                return ResponseEntity.status(403).body("Template is disabled");
            }

            return ResponseEntity.ok(template);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /* ================= GET ALL ================= */

    @GetMapping
    public ResponseEntity<?> getAllTemplates(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            Long userId = jwtUtil.extractUserId(token);
            String role = jwtUtil.extractRole(token);

            return ResponseEntity.ok(
                    templateService.getAllTemplates(userId, role)
            );

        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /* ================= COUNT ================= */
    @GetMapping("/count/enabled")
    public ResponseEntity<Long> enabledCount(HttpServletRequest request) {

        String token = extractToken(request);
        Long userId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);

        return ResponseEntity.ok(
                templateService.getEnabledTemplatesCount(userId, role)
        );
    }
    @GetMapping("/count/disabled")
    public ResponseEntity<Long> disabledCount(HttpServletRequest request) {

        String token = extractToken(request);
        String role = jwtUtil.extractRole(token);

        return ResponseEntity.ok(
                templateService.getDisabledTemplatesCount(role)
        );
    }

    //==================Get jrxml file=============
    @GetMapping("/{id}/jrxml")
    public ResponseEntity<String> getJrxml(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        try {
            String token = extractToken(request);
            String role = jwtUtil.extractRole(token);

            if (!role.contains("ADMIN")) {
                return ResponseEntity.status(403).body("Access denied");
            }

            return ResponseEntity.ok(
                    templateService.getJrxmlContent(id)
            );

        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // =============Put jrxml file=============
    @PutMapping("/{id}/jrxml")
    public ResponseEntity<?> updateJrxml(
            @PathVariable Long id,
            @RequestBody String content,
            HttpServletRequest request
    ) {
        try {
            String token = extractToken(request);
            String role = jwtUtil.extractRole(token);

            if (!role.contains("ADMIN")) {
                return ResponseEntity.status(403).body("Access denied");
            }

            templateService.updateJrxmlContent(id, content);

            return ResponseEntity.ok("JRXML updated successfully");

        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @GetMapping("/{id}/download-excel")
    public ResponseEntity<Resource> downloadExcel(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        try {
            String token = extractToken(request);
            Long userId = jwtUtil.extractUserId(token);
            String role = jwtUtil.extractRole(token);

            templateService.getTemplateByIdForUser(id, userId, role);


            Resource excel = templateService.downloadExcel(id);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"template_" + id + ".xlsx\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excel);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
    //============================show template by_user {userId}==============
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<?> getTemplatesBySelectedUser(
            @PathVariable Long userId,
            HttpServletRequest request
    ) {
        String token = extractToken(request);
        String role = jwtUtil.extractRole(token);
        if ("ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.ok(templateService.getAllTemplatesByUser(userId));
        }
        Long requesterId = jwtUtil.extractUserId(token);
        if (!requesterId.equals(userId)) {
            return ResponseEntity.status(403).body("Access denied");
        }
        return ResponseEntity.ok(
                templateService.getAllTemplatesByUser(userId)
        );
    }

    /* ================= DOWNLOAD TEMPLATE ZIP ================= */

    @GetMapping("/{id}/download-template")
    public ResponseEntity<Resource> downloadTemplateZip(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        try {
            String token = extractToken(request);
            Long userId = jwtUtil.extractUserId(token);
            String role = jwtUtil.extractRole(token);

            templateService.getTemplateByIdForUser(id, userId, role);

            Resource zip = templateService.downloadTemplateAsZip(id);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"template_" + id + ".zip\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zip);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}

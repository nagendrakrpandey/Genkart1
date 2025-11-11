package Tech_Nagendra.Certificates_genration.Controller;

import Tech_Nagendra.Certificates_genration.Dto.ReportDTO;
import Tech_Nagendra.Certificates_genration.Entity.Report;
import Tech_Nagendra.Certificates_genration.Security.UserPrincipal;
import Tech_Nagendra.Certificates_genration.Service.ReportService;
import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/reports")
@CrossOrigin(origins = "http://localhost:8081", allowCredentials = "true")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ProfileRepository userProfileRepository;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private UserPrincipal getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal) return (UserPrincipal) principal;
        return null;
    }

    private boolean isAdmin(UserPrincipal user) {
        return user != null && user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ADMIN") || a.getAuthority().equalsIgnoreCase("ROLE_ADMIN"));
    }

    private ReportDTO convertToDTO(Report report) {
        ReportDTO dto = new ReportDTO();
        dto.setId(report.getId());
        dto.setSid(report.getSid());
        dto.setCourseName(report.getCandidateName());
        dto.setGrade(report.getGrade());
        dto.setTemplateName(report.getTemplateName());
        dto.setJobrole(report.getJobrole());
        dto.setLevel(report.getLevel());
        dto.setBatchId(report.getBatchId());
        dto.setTrainingPartner(report.getTrainingPartner());
        dto.setGeneratedById(report.getGeneratedBy() != null ? report.getGeneratedBy().getId() : null);
        dto.setUserProfileId(report.getUserProfile() != null ? report.getUserProfile().getId() : null);
        dto.setTemplateId(report.getTemplate() != null ? report.getTemplate().getId() : null);
        dto.setStatus(report.getStatus());
        dto.setGeneratedOn(report.getGeneratedOn() != null ? report.getGeneratedOn().toString() : null);
        return dto;
    }

    @GetMapping("/all")
    public ResponseEntity<List<ReportDTO>> getAllReports() {
        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<Report> reports = reportService.getAllReports(currentUser);
        List<ReportDTO> dtos = reports.stream().map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportDTO> getReportById(@PathVariable Long id) {
        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Report report = reportService.getReportById(id);
        if (report == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        if (!isAdmin(currentUser) && !report.getGeneratedBy().getId().equals(currentUser.getId()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(convertToDTO(report));
    }

    @GetMapping("/filter")
    public ResponseEntity<List<ReportDTO>> getReportsByFilter(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {

        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Date from = null;
        Date to = null;
        try {
            if (fromDate != null && !fromDate.isEmpty()) from = dateFormat.parse(fromDate);
            if (toDate != null && !toDate.isEmpty()) to = dateFormat.parse(toDate);
        } catch (ParseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        List<ReportDTO> reports = reportService.getReportsByMultipleFilters(from, to, status, searchTerm, currentUser);
        if (reports == null) reports = new ArrayList<>();
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<ReportDTO>> getReportsByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Date start = null;
        Date end = null;
        try {
            start = dateFormat.parse(startDate);
            end = dateFormat.parse(endDate);
        } catch (ParseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyList());
        }

        List<ReportDTO> reports = reportService.getReportDTOsByDateRange(start, end, currentUser);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/stats/date-range")
    public ResponseEntity<Map<String, Object>> getCertificateStatsByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Date start = null;
        Date end = null;
        try {
            start = dateFormat.parse(startDate);
            end = dateFormat.parse(endDate);
        } catch (ParseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyMap());
        }

        Map<String, Object> stats = reportService.getCertificateStatsByDateRange(start, end, currentUser);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/save")
    public ResponseEntity<ReportDTO> saveReport(@RequestBody Report report) {
        if (report == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Report savedReport = reportService.saveOrUpdateBySid(report, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(savedReport));
    }

    @PostMapping("/saveAll")
    public ResponseEntity<Void> saveAllReports(@RequestBody List<Report> reports) {
        if (reports == null || reports.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        reportService.saveOrUpdateAllBySid(reports, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/candidate/{templateId}/{userId}")
    public ResponseEntity<ReportDTO> saveCandidateReport(
            @RequestBody Object candidateDTO,
            @PathVariable Long templateId,
            @PathVariable Long userId) {
        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Report report = reportService.saveCandidateReport(candidateDTO, templateId, userId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(report));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> countReports() {
        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Long count = reportService.getTotalCertificatesCount(currentUser);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/count/month")
    public ResponseEntity<Long> countReportsThisMonth() {
        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Long count = isAdmin(currentUser)
                ? reportService.countCertificatesThisMonth()
                : reportService.countCertificatesThisMonthByUser(currentUser.getId());
        return ResponseEntity.ok(count);
    }

    @GetMapping("/monthly")
    public ResponseEntity<Map<String, Long>> getMonthlyCertificateStats() {
        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Map<String, Long> stats = isAdmin(currentUser)
                ? reportService.getMonthlyCertificateStats()
                : reportService.getMonthlyCertificateStatsByUser(currentUser.getId());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/types")
    public ResponseEntity<Map<String, Long>> getCertificateTypesDistribution() {
        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Map<String, Long> distribution = reportService.getCertificateTypesDistribution(currentUser);
        return ResponseEntity.ok(distribution);
    }

    @PostMapping("/certificates/generate")
    public ResponseEntity<List<ReportDTO>> generateCertificates(@RequestBody Map<String, Object> request) {
        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<Integer> templateIdsInt = (List<Integer>) request.get("templateIds");
        String sid = (String) request.get("sid");
        List<Long> templateIds = templateIdsInt.stream().map(Long::valueOf).collect(Collectors.toList());

        List<Report> generated = reportService.generateCertificates(currentUser, templateIds, sid);
        List<ReportDTO> dtos = generated.stream().map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(dtos);
    }

    @GetMapping("/templates/total")
    public ResponseEntity<Map<String, Long>> getTotalTemplatesCount() {
        UserPrincipal currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Map<String, Long> result = reportService.getTotalTemplatesCount(currentUser);
        return ResponseEntity.ok(result);
    }
}
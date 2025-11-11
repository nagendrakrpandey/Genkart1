package Tech_Nagendra.Certificates_genration.Service;

import Tech_Nagendra.Certificates_genration.Dto.ReportDTO;
import Tech_Nagendra.Certificates_genration.Entity.Report;
import Tech_Nagendra.Certificates_genration.Entity.Template;
import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import Tech_Nagendra.Certificates_genration.Repository.ProfileRepository;
import Tech_Nagendra.Certificates_genration.Repository.ReportRepository;
import Tech_Nagendra.Certificates_genration.Repository.TemplateRepository;
import Tech_Nagendra.Certificates_genration.Security.UserPrincipal;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private TemplateRepository templateRepository;

    private boolean isAdmin(UserPrincipal user) {
        return user != null && user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ADMIN") || a.getAuthority().equalsIgnoreCase("ROLE_ADMIN"));
    }

    private boolean hasRollId1(UserPrincipal user) {
        return user != null && user.getUserProfile() != null && user.getUserProfile().getRollid() != null
                && user.getUserProfile().getRollid() == 1;
    }

    public List<Report> getAllReports(UserPrincipal currentUser) {
        if (currentUser == null) return Collections.emptyList();
        if (isAdmin(currentUser) || hasRollId1(currentUser)) {
            return reportRepository.findAll();
        } else {
            return reportRepository.findByGeneratedBy_Id(currentUser.getId());
        }
    }

    public List<Report> getReportsByDateRange(Date startDate, Date endDate, UserPrincipal currentUser) {
        if (currentUser == null) return Collections.emptyList();

        Date adjustedStartDate = adjustStartDate(startDate);
        Date adjustedEndDate = adjustEndDate(endDate);

        if (isAdmin(currentUser) || hasRollId1(currentUser)) {
            return reportRepository.findByGeneratedOnBetween(adjustedStartDate, adjustedEndDate);
        } else {
            return reportRepository.findByGeneratedBy_IdAndGeneratedOnBetween(
                    currentUser.getId(), adjustedStartDate, adjustedEndDate);
        }
    }

    public List<ReportDTO> getReportDTOsByDateRange(Date startDate, Date endDate, UserPrincipal currentUser) {
        List<Report> reports = getReportsByDateRange(startDate, endDate, currentUser);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        return reports.stream()
                .map(r -> convertToReportDTO(r, sdf))
                .collect(Collectors.toList());
    }

    public List<ReportDTO> getReportsByMultipleFilters(Date startDate, Date endDate, String status,
                                                       String searchTerm, UserPrincipal currentUser) {
        List<Report> reports;

        if (startDate != null && endDate != null) {
            reports = getReportsByDateRange(startDate, endDate, currentUser);
        } else {
            reports = getAllReports(currentUser);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        return reports.stream()
                .filter(r -> status == null || (r.getStatus() != null && r.getStatus().equalsIgnoreCase(status)))
                .filter(r -> searchTerm == null ||
                        (r.getSid() != null && r.getSid().toLowerCase().contains(searchTerm.toLowerCase())) ||
                        (r.getCandidateName() != null && r.getCandidateName().toLowerCase().contains(searchTerm.toLowerCase())) ||
                        (r.getTemplateName() != null && r.getTemplateName().toLowerCase().contains(searchTerm.toLowerCase())) ||
                        (r.getBatchId() != null && r.getBatchId().toLowerCase().contains(searchTerm.toLowerCase())) ||
                        (r.getTrainingPartner() != null && r.getTrainingPartner().toLowerCase().contains(searchTerm.toLowerCase())))
                .map(r -> convertToReportDTO(r, sdf))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getCertificateStatsByDateRange(Date startDate, Date endDate, UserPrincipal currentUser) {
        List<Report> reports = getReportsByDateRange(startDate, endDate, currentUser);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCertificates", reports.size());
        stats.put("successfulCertificates", reports.stream().filter(r -> "GENERATED".equals(r.getStatus())).count());
        stats.put("failedCertificates", reports.stream().filter(r -> "FAILED".equals(r.getStatus())).count());

        Map<String, Long> templateDistribution = reports.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getTemplateName() != null ? r.getTemplateName() : "Unknown",
                        Collectors.counting()));
        stats.put("templateDistribution", templateDistribution);

        Map<String, Long> dailyStats = reports.stream()
                .filter(r -> r.getGeneratedOn() != null)
                .collect(Collectors.groupingBy(
                        r -> {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                            return sdf.format(r.getGeneratedOn());
                        }, Collectors.counting()));
        stats.put("dailyStats", dailyStats);

        return stats;
    }

    private Date adjustStartDate(Date startDate) {
        if (startDate == null) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -30);
            return cal.getTime();
        }

        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private Date adjustEndDate(Date endDate) {
        if (endDate == null) {
            return new Date();
        }

        Calendar cal = Calendar.getInstance();
        cal.setTime(endDate);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }

    private ReportDTO convertToReportDTO(Report report, SimpleDateFormat sdf) {
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
        dto.setGeneratedOn(report.getGeneratedOn() != null ? sdf.format(report.getGeneratedOn()) : null);
        return dto;
    }

    public Report getReportById(Long id) {
        return reportRepository.findById(id).orElse(null);
    }

    @Transactional
    public Report saveOrUpdateBySid(Report report, UserPrincipal currentUser) {
        if (currentUser == null) return null;

        Long templateId = getTemplateIdFromReport(report);
        List<Report> existingReports = new ArrayList<>();
        if (report.getSid() != null && templateId != null) {
            existingReports = reportRepository.findAllBySidAndTemplateId(report.getSid(), templateId);
        }

        if (!existingReports.isEmpty()) {
            Report existing = existingReports.get(0);
            existing.setCandidateName(report.getCandidateName());
            existing.setGrade(report.getGrade());
            existing.setJobrole(report.getJobrole());
            existing.setLevel(report.getLevel());
            existing.setBatchId(report.getBatchId());
            existing.setTrainingPartner(report.getTrainingPartner());
            existing.setTemplateName(report.getTemplateName());
            existing.setSid(report.getSid());
            existing.setGeneratedOn(new Date());
            existing.setGeneratedBy(currentUser.getUserProfile());
            existing.setStatus(report.getStatus() != null ? report.getStatus() : "GENERATED");
            existing.setActive(true);

            if (report.getTemplate() != null) {
                existing.setTemplate(report.getTemplate());
            }

            if (existingReports.size() > 1) {
                System.out.println("Warning: " + (existingReports.size() - 1) +
                        " duplicate reports exist for SID=" + report.getSid() +
                        " TemplateID=" + templateId);
            }

            return reportRepository.save(existing);
        } else {
            if (report.getUserProfile() == null) {
                report.setUserProfile(currentUser.getUserProfile());
            }
            report.setGeneratedBy(currentUser.getUserProfile());
            report.setGeneratedOn(new Date());
            report.setStatus(report.getStatus() != null ? report.getStatus() : "GENERATED");
            report.setActive(true);

            if (report.getTemplate() == null && templateId != null) {
                Template template = templateRepository.findById(templateId).orElse(null);
                report.setTemplate(template);
            }

            return reportRepository.save(report);
        }
    }

    private Long getTemplateIdFromReport(Report report) {
        if (report.getTemplate() != null && report.getTemplate().getId() != null) {
            return report.getTemplate().getId();
        }
        return null;
    }

    @Transactional
    public void saveOrUpdateAllBySid(List<Report> reports, UserPrincipal currentUser) {
        if (currentUser == null) return;
        for (Report report : reports) {
            saveOrUpdateBySid(report, currentUser);
        }
    }

    @Transactional
    public List<Report> generateCertificates(UserPrincipal currentUser, List<Long> templateIds, String sid) {
        if (currentUser == null || templateIds == null) return Collections.emptyList();
        List<Report> reports = new ArrayList<>();

        for (Long templateId : templateIds) {
            Report report = new Report();
            Template template = templateRepository.findById(templateId).orElse(null);
            if (template != null) {
                report.setTemplate(template);
                report.setTemplateName(template.getTemplateName() != null ? template.getTemplateName() : "Template ID: " + templateId);
            } else {
                report.setTemplateName("Template ID: " + templateId);
            }
            report.setSid(sid);
            report.setUserProfile(currentUser.getUserProfile());
            reports.add(saveOrUpdateBySid(report, currentUser));
        }
        return reports;
    }

    @Transactional
    public Report saveCandidateReport(Object candidateDTO, Long templateId, Long userId, UserPrincipal currentUser) {
        if (currentUser == null) return null;
        UserProfile targetUserProfile = profileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        String sid = extractSidFromCandidateDTO(candidateDTO);
        List<Report> existingReports = reportRepository.findAllBySidAndTemplateId(sid, templateId);

        if (!existingReports.isEmpty()) {
            Report existing = existingReports.get(0);
            existing.setGeneratedOn(new Date());
            existing.setGeneratedBy(currentUser.getUserProfile());
            existing.setStatus("GENERATED");
            return reportRepository.save(existing);
        } else {
            Report report = new Report();
            Template template = templateRepository.findById(templateId).orElse(null);
            if (template != null) {
                report.setTemplate(template);
                report.setTemplateName(template.getTemplateName() != null ? template.getTemplateName() : "Template ID: " + templateId);
            } else {
                report.setTemplateName("Template ID: " + templateId);
            }
            report.setUserProfile(targetUserProfile);
            report.setGeneratedBy(currentUser.getUserProfile());
            report.setGeneratedOn(new Date());
            report.setStatus("GENERATED");
            return reportRepository.save(report);
        }
    }

    private String extractSidFromCandidateDTO(Object candidateDTO) {
        if (candidateDTO == null) return null;
        try {
            return candidateDTO.getClass().getMethod("getSid").invoke(candidateDTO).toString();
        } catch (Exception e) {
            return candidateDTO.toString();
        }
    }

    public Long getTotalCertificatesCount(UserPrincipal currentUser) {
        if (currentUser == null) return 0L;
        return (isAdmin(currentUser) || hasRollId1(currentUser))
                ? reportRepository.count()
                : reportRepository.countByGeneratedBy_Id(currentUser.getId());
    }

    public Long countCertificatesThisMonth() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date start = cal.getTime();
        Date end = new Date();
        return reportRepository.countByGeneratedOnBetween(start, end);
    }

    public Long countCertificatesThisMonthByUser(Long userId) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date start = cal.getTime();
        Date end = new Date();
        return reportRepository.countByGeneratedBy_IdAndGeneratedOnBetween(userId, start, end);
    }

    public Map<String, Long> getMonthlyCertificateStats() {
        List<Report> allReports = reportRepository.findAll();
        return allReports.stream()
                .filter(r -> r.getGeneratedOn() != null)
                .collect(Collectors.groupingBy(
                        r -> {
                            Calendar c = Calendar.getInstance();
                            c.setTime(r.getGeneratedOn());
                            return c.get(Calendar.YEAR) + "-" + String.format("%02d", c.get(Calendar.MONTH) + 1);
                        }, Collectors.counting()));
    }

    public Map<String, Long> getMonthlyCertificateStatsByUser(Long userId) {
        List<Report> reports = reportRepository.findByGeneratedBy_Id(userId);
        return reports.stream()
                .filter(r -> r.getGeneratedOn() != null)
                .collect(Collectors.groupingBy(
                        r -> {
                            Calendar c = Calendar.getInstance();
                            c.setTime(r.getGeneratedOn());
                            return c.get(Calendar.YEAR) + "-" + String.format("%02d", c.get(Calendar.MONTH) + 1);
                        }, Collectors.counting()));
    }

    public Map<String, Long> getCertificateTypesDistribution(UserPrincipal currentUser) {
        List<Report> reports = (isAdmin(currentUser) || hasRollId1(currentUser))
                ? reportRepository.findAll()
                : reportRepository.findByGeneratedBy_Id(currentUser.getId());
        return reports.stream().collect(Collectors.groupingBy(
                r -> r.getTemplateName() != null ? r.getTemplateName() : "Unknown",
                Collectors.counting()));
    }

    public Map<String, Long> getTotalTemplatesCount(UserPrincipal currentUser) {
        return getCertificateTypesDistribution(currentUser);
    }

    public List<ReportDTO> getReportsByFilter(String status, String searchTerm, Date from, Date to, UserPrincipal currentUser) {
        List<Report> reports;

        if (from != null && to != null) {
            reports = getReportsByDateRange(from, to, currentUser);
        } else {
            reports = getAllReports(currentUser);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        return reports.stream()
                .filter(r -> status == null || (r.getStatus() != null && r.getStatus().equalsIgnoreCase(status)))
                .filter(r -> searchTerm == null ||
                        (r.getSid() != null && r.getSid().toLowerCase().contains(searchTerm.toLowerCase())) ||
                        (r.getCandidateName() != null && r.getCandidateName().toLowerCase().contains(searchTerm.toLowerCase())) ||
                        (r.getTemplateName() != null && r.getTemplateName().toLowerCase().contains(searchTerm.toLowerCase())))
                .filter(r -> from == null || (r.getGeneratedOn() != null && !r.getGeneratedOn().before(from)))
                .filter(r -> to == null || (r.getGeneratedOn() != null && !r.getGeneratedOn().after(to)))
                .map(r -> convertToReportDTO(r, sdf))
                .collect(Collectors.toList());
    }
}
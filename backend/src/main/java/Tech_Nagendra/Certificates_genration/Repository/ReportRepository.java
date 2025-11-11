package Tech_Nagendra.Certificates_genration.Repository;

import Tech_Nagendra.Certificates_genration.Entity.Report;
import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    long countByGeneratedBy_Id(Long userId);
    long countByGeneratedBy_IdAndGeneratedOnBetween(Long userId, Date startDate, Date endDate);
    long countByGeneratedBy_IdAndStatus(Long userId, String status);
    Long countByGeneratedBy(UserProfile generatedBy);
    Long countByGeneratedByAndGeneratedOnBetween(UserProfile generatedBy, Date start, Date end);
    Long countByGeneratedOnBetween(Date start, Date end);
    Long countByStatus(String status);

    List<Report> findByGeneratedBy_Id(Long userId);

    @Query("SELECT r FROM Report r WHERE r.generatedOn BETWEEN :startDate AND :endDate ORDER BY r.generatedOn DESC")
    List<Report> findByGeneratedOnBetween(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

    @Query("SELECT r FROM Report r WHERE r.generatedBy.id = :userId AND r.generatedOn BETWEEN :startDate AND :endDate ORDER BY r.generatedOn DESC")
    List<Report> findByGeneratedBy_IdAndGeneratedOnBetween(@Param("userId") Long userId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    Optional<Report> findTopByGeneratedBy_IdOrderByGeneratedOnDesc(Long userId);
    Optional<Report> findTopByGeneratedBy_IdAndStatusOrderByGeneratedOnDesc(Long userId, String status);
    List<Report> findByGeneratedBy(UserProfile generatedBy);
    List<Report> findBySid(String sid);
    Optional<Report> findBySidAndTemplateName(String sid, String templateName);

    @Query("SELECT r FROM Report r WHERE r.userProfile.id = :userId AND r.template.id = :templateId")
    List<Report> findByUserProfile_IdAndTemplateId(@Param("userId") Long userId, @Param("templateId") Long templateId);

    @Query("SELECT r FROM Report r WHERE r.sid = :sid AND r.template.id = :templateId")
    List<Report> findAllBySidAndTemplateId(@Param("sid") String sid, @Param("templateId") Long templateId);

    @Query("SELECT r FROM Report r WHERE r.sid = :sid AND r.template.id = :templateId")
    List<Report> findAllBySidAndTemplateID(@Param("sid") String sid, @Param("templateId") Long templateId);

    @Query("SELECT r FROM Report r WHERE r.userProfile.id = :userId AND r.template.id = :templateId")
    List<Report> findAllByUserProfile_IdAndTemplateId(@Param("userId") Long userId, @Param("templateId") Long templateId);

    @Query("SELECT COUNT(r) FROM Report r WHERE MONTH(r.generatedOn) = MONTH(CURRENT_DATE) AND YEAR(r.generatedOn) = YEAR(CURRENT_DATE)")
    Long countCertificatesThisMonth();

    @Query("SELECT COUNT(r) FROM Report r WHERE r.generatedBy.id = :userId AND MONTH(r.generatedOn) = MONTH(CURRENT_DATE) AND YEAR(r.generatedOn) = YEAR(CURRENT_DATE)")
    Long countCertificatesThisMonthByUser(Long userId);

    @Query("SELECT FUNCTION('YEAR', r.generatedOn), FUNCTION('MONTH', r.generatedOn), COUNT(r) FROM Report r GROUP BY FUNCTION('YEAR', r.generatedOn), FUNCTION('MONTH', r.generatedOn) ORDER BY FUNCTION('YEAR', r.generatedOn), FUNCTION('MONTH', r.generatedOn)")
    List<Object[]> getMonthlyCertificateStats();

    @Query("SELECT r FROM Report r WHERE r.template.id = :templateId")
    List<Report> findByTemplateId(@Param("templateId") Long templateId);

    @Query("SELECT COUNT(r) FROM Report r WHERE r.template.id = :templateId")
    Long countByTemplateId(@Param("templateId") Long templateId);

    @Query("SELECT r FROM Report r JOIN FETCH r.generatedBy WHERE r.generatedBy.id = :userId")
    List<Report> findByGeneratedBy_IdWithUser(@Param("userId") Long userId);

}
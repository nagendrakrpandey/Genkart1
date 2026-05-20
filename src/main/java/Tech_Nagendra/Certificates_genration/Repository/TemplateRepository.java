package Tech_Nagendra.Certificates_genration.Repository;

import Tech_Nagendra.Certificates_genration.Entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

    Optional<Template> findByTemplateName(String templateName);
    List<Template> findByCreatedBy_Id(Long userId);
    Long countByCreatedBy_Id(Long userId);
    List<Template> findByCreatedBy_IdAndStatusTrue(Long userId);
    Long countByCreatedBy_IdAndStatusTrue(Long userId);
    Long countByStatusTrue();
    Long countByStatusFalse();
    Optional<Template> findFirstByImageType(int imageType);



//    boolean existsByTemplateNameIgnoreCaseAndCreatedBy_Id(
//            String templateName,
//            Long createdById
//    );



    @Modifying
    @Transactional
    @Query("UPDATE Template t SET t.status = :status WHERE t.id = :templateId")
    void enableDisableTemplate(@Param("templateId") Long templateId, @Param("status") boolean status);
}

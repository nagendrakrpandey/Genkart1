package Tech_Nagendra.Certificates_genration.Repository;


import Tech_Nagendra.Certificates_genration.Entity.TemplateImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateImageRepository extends JpaRepository<TemplateImage, Long> {
    List<TemplateImage> findByTemplateId(Long templateId);
}

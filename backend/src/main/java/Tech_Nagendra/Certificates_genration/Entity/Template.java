package Tech_Nagendra.Certificates_genration.Entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@EntityListeners(AuditingEntityListener.class)
@Table(name = "templates")
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_name")
    private String templateName;

    @Column(name = "image_type")
    private Integer imageType;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "jrxml_path")
    private String jrxmlPath;

    @Column(name = "template_folder")
    private String templateFolder;

    @LastModifiedDate
    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @JsonBackReference
    private UserProfile createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modified_by")
    @JsonBackReference
    private UserProfile modifiedBy;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<TemplateImage> images;


    @Override
    public String toString() {
        return "Template{" +
                "id=" + id +
                ", templateName='" + templateName + '\'' +
                ", imageType=" + imageType +
                ", createdAt=" + createdAt +
                ", jrxmlPath='" + jrxmlPath + '\'' +
                ", templateFolder='" + templateFolder + '\'' +
                ", modifiedAt=" + modifiedAt +
                ", createdById=" + (createdBy != null ? createdBy.getId() : null) +
                ", modifiedById=" + (modifiedBy != null ? modifiedBy.getId() : null) +
                // Exclude images collection to prevent circular references
                '}';
    }
}
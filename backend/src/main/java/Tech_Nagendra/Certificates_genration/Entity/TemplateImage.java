package Tech_Nagendra.Certificates_genration.Entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "template_images")
public class TemplateImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "image_type")
    private Integer imageType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    @JsonBackReference
    private Template template;


    @Override
    public String toString() {
        return "TemplateImage{" +
                "id=" + id +
                ", imagePath='" + imagePath + '\'' +
                ", imageType=" + imageType +
                ", templateId=" + (template != null ? template.getId() : null) +
                '}';
    }
}
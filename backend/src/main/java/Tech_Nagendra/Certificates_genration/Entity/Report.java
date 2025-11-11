package Tech_Nagendra.Certificates_genration.Entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

@Entity
@Data
@EntityListeners(AuditingEntityListener.class)
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id")
    @JsonBackReference
    private UserProfile userProfile;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by")
    @JsonBackReference
    private UserProfile generatedBy;

    @CreatedDate
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "generated_on")
    private Date generatedOn;

    @Column(name = "job_role")
    private String jobrole;

    @Column(name = "candidate_name")
    private String candidateName;

    private String level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    @JsonIgnore
    private Template template;

    @Column(name = "training_partner")
    private String trainingPartner;

    @Column(name = "batch_id")
    private String batchId;

    private String grade;

    private String templateName;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modified_by")
    @JsonBackReference
    private UserProfile modifiedBy;

    @LastModifiedDate
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "modified_on")
    private Date modifiedOn;

    @Column(name = "certificates_count")
    private Long certificatesCount;

    private String status;

    private Boolean active;

    @PrePersist
    protected void onCreate() {
        if (active == null) active = true;
        if (status == null) status = "GENERATED";
    }
    @Override
    public String toString() {
        return "Report{" +
                "id=" + id +
                ", sid='" + sid + '\'' +
                ", userProfileId=" + (userProfile != null ? userProfile.getId() : null) +
                ", generatedById=" + (generatedBy != null ? generatedBy.getId() : null) +
                ", generatedOn=" + generatedOn +
                ", jobrole='" + jobrole + '\'' +
                ", courseName='" +candidateName + '\'' +
                ", level='" + level + '\'' +
                ", templateId=" + (template != null ? template.getId() : null) +
                ", trainingPartner='" + trainingPartner + '\'' +
                ", batchId='" + batchId + '\'' +
                ", grade='" + grade + '\'' +
                ", templateName='" + templateName + '\'' +
                ", modifiedById=" + (modifiedBy != null ? modifiedBy.getId() : null) +
                ", modifiedOn=" + modifiedOn +
                ", certificatesCount=" + certificatesCount +
                ", status='" + status + '\'' +
                ", active=" + active +
                '}';
    }
}
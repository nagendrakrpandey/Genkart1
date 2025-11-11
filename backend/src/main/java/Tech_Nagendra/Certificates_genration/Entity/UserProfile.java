package Tech_Nagendra.Certificates_genration.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Data
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_profile")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(name = "rollid")
    private Long rollid;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    private String password;

    private String role;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @JsonIgnore
    private UserProfile createdBy;

    @Column(name = "login_token")
    private String loginToken;

    @LastModifiedDate
    private LocalDateTime modifiedAt;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modified_by")
    @JsonIgnore
    private UserProfile modifiedBy;

    @OneToMany(mappedBy = "createdBy", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Template> createdTemplates;

    @OneToMany(mappedBy = "modifiedBy", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Template> modifiedTemplates;

    @OneToMany(mappedBy = "userProfile", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Report> reports;

    @OneToMany(mappedBy = "generatedBy", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Report> generatedReports;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfile)) return false;
        UserProfile that = (UserProfile) o;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", rollid=" + rollid +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                ", createdAt=" + createdAt +
                ", createdBy=" + (createdBy != null ? createdBy.getId() : null) +
                ", modifiedAt=" + modifiedAt +
                ", modifiedBy=" + (modifiedBy != null ? modifiedBy.getId() : null) +
                '}';
    }
}

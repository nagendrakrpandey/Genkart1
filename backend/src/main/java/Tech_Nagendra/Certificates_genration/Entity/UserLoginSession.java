package Tech_Nagendra.Certificates_genration.Entity;


import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Data
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_login_session")
public class UserLoginSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserProfile user;

    @Column(nullable = false, unique = true)
    private String jwtToken;

    private String deviceInfo;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime loginTime;

    private LocalDateTime logoutTime;

    private boolean active = true;
}


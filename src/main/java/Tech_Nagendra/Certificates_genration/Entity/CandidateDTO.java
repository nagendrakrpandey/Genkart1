package Tech_Nagendra.Certificates_genration.Entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "candidates")
public class
CandidateDTO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String salutation;
    private String candidateName;
    private String sid;
    private String jobRole;
    private String guardianType;
    private String fatherORHusbandName;
    private String sectorSkillCouncil;
    private String dateOfIssuance;
    private String level;
    private String aadhaarNumber;
    private String sector;
    private String grade;
    private String dateOfStart;
    private String dateOfEnd;
    private String marks;
    private String marks1;
    private String marks2;
    private String marks3;
    private String marks4;
    private String marks5;
    private String marks6;
    private String marks7;
    private String marks8;
    private String marks9;
    private String marks10;
    private String batchId;
    private String district;
    private String state;
    private String place;
    private String  courseName;
    private String duration;
    private String earning;
    private String credit;
    private String trainingCenter;
    private String dateofbirth;
    private String registrationNo;
    private String CandidateId;
    private String Rollno;
    private String SchoolName;
    private String className;
    private String CertificateNo;
    @ManyToOne
    @JoinColumn(name="template_id")
    private Template template;
}

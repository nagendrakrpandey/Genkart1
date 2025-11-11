package Tech_Nagendra.Certificates_genration.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportDTO {
    private Long id;
    private String sid;
    private String courseName;
    private String grade;
    private String templateName;
    private String jobrole;
    private String level;
    private String batchId;
    private String trainingPartner;
    private Long generatedById;
    private Long userProfileId;
    private Long templateId;
    private String status;
    private String generatedOn;
}


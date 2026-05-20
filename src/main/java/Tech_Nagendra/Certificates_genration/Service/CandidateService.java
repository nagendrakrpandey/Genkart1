package Tech_Nagendra.Certificates_genration.Service;
import Tech_Nagendra.Certificates_genration.Entity.CandidateDTO;
import Tech_Nagendra.Certificates_genration.Entity.Template;
import Tech_Nagendra.Certificates_genration.Repository.CandidateRepository;
import Tech_Nagendra.Certificates_genration.Repository.TemplateRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Service
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final TemplateRepository templateRepository;

    public CandidateService(CandidateRepository candidateRepository, TemplateRepository templateRepository) {
        this.candidateRepository = candidateRepository;
        this.templateRepository = templateRepository;
    }

    public void saveCandidatesFromExcel(File excelFile, Long templateId) throws Exception {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        try (InputStream is = new FileInputStream(excelFile)) {
            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                CandidateDTO candidate = new CandidateDTO();
                candidate.setSalutation(getCellValue(row, 0));
                candidate.setCandidateName(getCellValue(row, 1));
                candidate.setSid(getCellValue(row, 2));
                candidate.setJobRole(getCellValue(row, 3));
                candidate.setGuardianType(getCellValue(row, 4));
                candidate.setFatherORHusbandName(getCellValue(row, 5));
                candidate.setSectorSkillCouncil(getCellValue(row, 6));
                Cell dateCell = row.getCell(7, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String formattedDate = "";
                if (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                    Date date = dateCell.getDateCellValue();
                    LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    formattedDate = localDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                } else {
                    String rawDate = getCellValue(row, 7);
                    try {
                        LocalDate localDate = LocalDate.parse(rawDate);
                        formattedDate = localDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    } catch (Exception e) {
                        formattedDate = rawDate;
                    }
                }
                candidate.setDateOfIssuance(formattedDate);

                candidate.setLevel(getCellValue(row, 8));
                candidate.setAadhaarNumber(getCellValue(row, 9));
                candidate.setSector(getCellValue(row, 10));
                candidate.setGrade(getCellValue(row, 11));
                candidate.setDateOfStart(getCellValue(row, 12));
                candidate.setDateOfEnd(getCellValue(row, 13));
                candidate.setMarks(getCellValue(row, 14));
                candidate.setMarks1(getCellValue(row, 15));
                candidate.setMarks2(getCellValue(row, 16));
                candidate.setMarks3(getCellValue(row, 17));
                candidate.setBatchId(getCellValue(row, 18));
                candidate.setTemplate(template);

                candidateRepository.save(candidate);
            }
        }
    }

    private String getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}

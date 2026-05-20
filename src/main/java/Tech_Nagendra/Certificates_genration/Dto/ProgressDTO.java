package Tech_Nagendra.Certificates_genration.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProgressDTO {

    private String jobId;
    private int total;
    private int completed;
    private int percentage;
    private boolean finished;
    private boolean readyForDownload;
    private String message;
    private String status;
    private boolean error;
    private byte[] zipData;  // 🔥 ADD THIS FIELD

    // Thread-safe increment
    public synchronized void incrementCompleted() {
        this.completed++;
        calculatePercentage();
    }

    public synchronized void increment() {
        this.completed++;
        calculatePercentage();
    }

    public synchronized void increment(int count) {
        this.completed += count;
        calculatePercentage();
    }

    // Calculate percentage safely
    public int getPercentage() {
        if (total == 0) {
            return 0;
        }
        return (completed * 100) / total;
    }

    public void setPercentage(int percentage) {
        this.percentage = percentage;
    }

    // Safe setters
    public void setTotal(int total) {
        this.total = Math.max(total, 0);
        calculatePercentage();
    }

    public void setCompleted(int completed) {
        this.completed = Math.max(completed, 0);
        calculatePercentage();
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }


    private void calculatePercentage() {
        if (total > 0) {
            this.percentage = (completed * 100) / total;
        } else {
            this.percentage = 0;
        }
    }

    // Status check methods
    public boolean isProcessing() {
        return !finished;
    }

    public boolean isCompleted() {
        return finished && readyForDownload;
    }

    public boolean isFailed() {
        return finished && !readyForDownload;
    }

    // State management methods
    public void markAsProcessing() {
        this.finished = false;
        this.readyForDownload = false;
        this.status = "PROCESSING";
        this.message = "Processing...";
    }

    public void markAsCompleted() {
        this.finished = true;
        this.readyForDownload = true;
        this.status = "COMPLETED";
        this.percentage = 100;
        this.message = "Completed successfully!";
    }

    public void markAsFailed(String errorMessage) {
        this.finished = true;
        this.readyForDownload = false;
        this.status = "FAILED";
        this.error = true;
        this.message = "Failed: " + errorMessage;
    }

    public void markAsReadyForDownload() {
        this.readyForDownload = true;
        this.status = "READY";
        this.message = "Ready for download";
    }

    // Reset method
    public void reset() {
        this.jobId = null;
        this.total = 0;
        this.completed = 0;
        this.percentage = 0;
        this.finished = false;
        this.readyForDownload = false;
        this.message = "";
        this.status = "PENDING";
        this.zipData = null;
    }

    // 🔥 ZIP Data methods
    public byte[] getZipData() {
        return zipData;
    }

    public void setZipData(byte[] zipData) {
        this.zipData = zipData;
        if (zipData != null) {
            this.readyForDownload = true;
            this.status = "READY";
        }
    }

    // Constructors
    public ProgressDTO(String jobId, int total) {
        this.jobId = jobId;
        this.total = Math.max(total, 0);
        this.completed = 0;
        this.percentage = 0;
        this.finished = false;
        this.readyForDownload = false;
        this.message = "Started";
        this.status = "PROCESSING";
        this.zipData = null;
    }

    public ProgressDTO(String jobId, int total, int completed, boolean finished,
                       boolean readyForDownload, String message, String status, byte[] zipData) {
        this.jobId = jobId;
        this.total = Math.max(total, 0);
        this.completed = Math.max(completed, 0);
        this.finished = finished;
        this.readyForDownload = readyForDownload;
        this.message = message;
        this.status = status;
        this.zipData = zipData;
        calculatePercentage();
    }

    @Override
    public String toString() {
        return "ProgressDTO{" +
                "jobId='" + jobId + '\'' +
                ", total=" + total +
                ", completed=" + completed +
                ", percentage=" + getPercentage() +
                ", finished=" + finished +
                ", readyForDownload=" + readyForDownload +
                ", message='" + message + '\'' +
                ", status='" + status + '\'' +
                ", zipData=" + (zipData != null ? zipData.length + " bytes" : "null") +
                '}';
    }
}
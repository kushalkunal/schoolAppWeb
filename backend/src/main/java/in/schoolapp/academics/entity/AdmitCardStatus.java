package in.schoolapp.academics.entity;

public enum AdmitCardStatus {
    /** Student has unpaid fee dues — card generation is blocked. */
    BLOCKED,
    /** Student is eligible but card has not been generated yet. */
    PENDING,
    /** PDF has been generated and is available for download. */
    GENERATED,
    /** Card has been downloaded at least once. */
    DOWNLOADED
}

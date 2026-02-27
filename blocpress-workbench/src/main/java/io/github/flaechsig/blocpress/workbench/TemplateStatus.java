package io.github.flaechsig.blocpress.workbench;

public enum TemplateStatus {
    DRAFT,      // Initial state after upload
    SUBMITTED,  // Submitted for approval
    APPROVED,   // Approved (Epic 4)
    REJECTED    // Rejected (Epic 4)
}

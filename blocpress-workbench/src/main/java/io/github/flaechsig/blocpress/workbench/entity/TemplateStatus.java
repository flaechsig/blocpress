package io.github.flaechsig.blocpress.workbench.entity;

public enum TemplateStatus {
    DRAFT,      // Initial state after upload
    SUBMITTED,  // Submitted for approval
    APPROVED,   // Approved (Epic 4)
    REJECTED    // Rejected (Epic 4)
}

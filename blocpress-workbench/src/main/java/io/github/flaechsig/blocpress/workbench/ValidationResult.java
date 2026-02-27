package io.github.flaechsig.blocpress.workbench;

import java.util.List;

public record ValidationResult(
    boolean isValid,
    List<ValidationMessage> errors,
    List<ValidationMessage> warnings,
    List<UserFieldInfo> userFields,
    List<RepetitionGroupInfo> repetitionGroups,
    List<ConditionInfo> conditions
) {
    public record ValidationMessage(String code, String message) {}

    public record UserFieldInfo(String name, String type) {}

    public record RepetitionGroupInfo(
        String name,
        String arrayPath,
        String type  // "section" or "table-row"
    ) {}

    public record ConditionInfo(
        String expression,
        String elementType,
        boolean syntaxValid,
        String errorMessage
    ) {}
}

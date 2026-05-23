package io.github.flaechsig.blocpress.core.odt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JexlConditionEvaluatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void stringLiteralTrueNotNormalized() {
        JsonNode data = mapper.valueToTree(Map.of("flag", "TRUE"));
        assertTrue(JexlConditionEvaluator.evaluate("flag == \"TRUE\"", data));
    }

    @Test
    void stringLiteralFalseNotNormalized() {
        JsonNode data = mapper.valueToTree(Map.of("flag", "FALSE"));
        assertTrue(JexlConditionEvaluator.evaluate("flag == \"FALSE\"", data));
    }

    @Test
    void stringLiteralAndNotNormalized() {
        JsonNode data = mapper.valueToTree(Map.of("op", "AND"));
        assertTrue(JexlConditionEvaluator.evaluate("op == \"AND\"", data));
    }

    @Test
    void stringLiteralOrNotNormalized() {
        JsonNode data = mapper.valueToTree(Map.of("op", "OR"));
        assertTrue(JexlConditionEvaluator.evaluate("op == \"OR\"", data));
    }

    @Test
    void stringLiteralNotNotNormalized() {
        JsonNode data = mapper.valueToTree(Map.of("op", "NOT"));
        assertTrue(JexlConditionEvaluator.evaluate("op == \"NOT\"", data));
    }

    @Test
    void keywordReplacementOutsideStrings() {
        JsonNode data = mapper.valueToTree(Map.of("a", true, "b", true));
        assertTrue(JexlConditionEvaluator.evaluate("a AND b", data));
    }

    @Test
    void keywordTrueOutsideStrings() {
        JsonNode data = mapper.valueToTree(Map.of("flag", true));
        assertTrue(JexlConditionEvaluator.evaluate("flag == TRUE", data));
    }

    @Test
    void keywordFalseOutsideStrings() {
        JsonNode data = mapper.valueToTree(Map.of("flag", false));
        assertTrue(JexlConditionEvaluator.evaluate("flag == FALSE", data));
    }

    @Test
    void booleanCoercionAgainstStringTrue() {
        JsonNode data = mapper.valueToTree(Map.of("flag", true));
        assertTrue(JexlConditionEvaluator.evaluate("flag == true", data));
    }

    @Test
    void mixedKeywordsAndStringLiterals() {
        JsonNode data = mapper.valueToTree(Map.of("status", "TRUE", "active", true));
        assertTrue(JexlConditionEvaluator.evaluate("status == \"TRUE\" AND active == TRUE", data));
    }

    @Test
    void inequalityWithStringLiteralTrue() {
        JsonNode data = mapper.valueToTree(Map.of("flag", "TRUE"));
        assertFalse(JexlConditionEvaluator.evaluate("flag != \"TRUE\"", data));
    }

    @Test
    void ooowPrefixWithStringLiteral() {
        JsonNode data = mapper.valueToTree(Map.of("kunde", Map.of("anrede", "FRAU")));
        assertTrue(JexlConditionEvaluator.evaluate("ooow:kunde.anrede == \"FRAU\"", data));
    }

    @Test
    void htmlEntityQuotesWithStringLiteral() {
        JsonNode data = mapper.valueToTree(Map.of("flag", "TRUE"));
        assertTrue(JexlConditionEvaluator.evaluate("flag == &quot;TRUE&quot;", data));
    }
}

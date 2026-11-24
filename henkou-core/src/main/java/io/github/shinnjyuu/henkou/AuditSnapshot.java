package io.github.shinnjyuu.henkou;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** An immutable projection of fields that a business service allows into the audit log. */
public record AuditSnapshot(Map<String, ?> fields) {
    public AuditSnapshot {
        fields = copyFields(Objects.requireNonNull(fields, "fields"));
    }

    private static Map<String, Object> copyFields(Map<?, ?> fields) {
        var copy = new TreeMap<String, Object>();
        for (var field : fields.entrySet()) {
            if (!(field.getKey() instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("Audit field names must be nonblank strings");
            }
            copy.put(name, copyValue(field.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyValue(Object value) {
        return switch (value) {
            case null -> null;
            case String text -> text;
            case Boolean flag -> flag;
            case Byte number -> number;
            case Short number -> number;
            case Integer number -> number;
            case Long number -> number;
            case BigInteger number -> number;
            case BigDecimal number -> number;
            case Float number when Float.isFinite(number) -> number;
            case Double number when Double.isFinite(number) -> number;
            case List<?> values -> {
                var copy = new ArrayList<>(values.size());
                for (Object item : values) {
                    copy.add(copyValue(item));
                }
                yield Collections.unmodifiableList(copy);
            }
            case Map<?, ?> fields -> copyFields(fields);
            default -> throw new IllegalArgumentException(
                    "Audit fields must contain JSON values, found " + value.getClass().getName());
        };
    }
}

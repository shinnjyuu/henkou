package io.github.shinnjyuu.henkou.extra;

import io.github.shinnjyuu.henkou.Audit;
import io.github.shinnjyuu.henkou.AuditChange;
import io.github.shinnjyuu.henkou.spring.AuditRecorder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static io.github.shinnjyuu.henkou.AuditPolicy.SKIP;

@RestController
public class AdditionalController {
    private final AuditRecorder audit;

    public AdditionalController(AuditRecorder audit) {
        this.audit = audit;
    }

    @GetMapping("/additional-controller")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Audit(SKIP)
    void checkAuditContext() {
        audit.record(AuditChange.event("test", "1", "check_audit_context"));
    }
}

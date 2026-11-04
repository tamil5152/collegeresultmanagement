package com.college.result.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.UncategorizedSQLException;

/** Lives in the same package because the handler methods are package-private. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mysql8CheckConstraintViolationIsAConflictNotAServerError() {
        var ex = new UncategorizedSQLException("insert", "INSERT ...",
                new SQLException("Check constraint 'chk_marks_nonneg' is violated.", "HY000", 3819));
        var response = handler.handleUncategorizedSql(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("DATA_INTEGRITY");
    }

    @Test
    void otherUncategorizedSqlErrorsStayInternalErrorsWithoutLeakingDetails() {
        var ex = new UncategorizedSQLException("insert", "INSERT ...", new SQLException("secret detail", "HY000", 9999));
        var response = handler.handleUncategorizedSql(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).doesNotContain("secret");
    }
}

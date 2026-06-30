package com.ai_kids_care.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Test-only Hibernate {@link StatementInspector} that counts prepared statements into a
 * <strong>thread-local</strong> counter instead of the process-wide
 * {@code SessionFactory.getStatistics().getPrepareStatementCount()} global counter.
 *
 * <p><b>Why this exists.</b> {@code DetectionEventEntityGraphNPlusOneTest} proves the {@code @EntityGraph}
 * fix keeps the SQL statement count flat regardless of row count (no N+1). It originally measured that
 * count via the Hibernate global statistics counter, which is shared across every thread in the JVM.
 * The ingest path fires {@code @Async} staff-alert listeners that execute their own JDBC on executor
 * threads; under CI load such background SQL occasionally landed inside the measured window and inflated
 * the global counter, pushing {@code largeSql - smallSql} past the slack budget and flaking the test.
 *
 * <p>Hibernate invokes {@link #inspect(String)} on the exact thread that executes the SQL. Because the
 * measured workload runs synchronously on the test thread (via {@code TransactionTemplate.execute},
 * which is same-thread), async tasks running on executor threads increment <em>their own</em>
 * thread-local counter and can no longer pollute the measurement. The count is therefore deterministic
 * by construction.
 *
 * <p>Hibernate instantiates this by class name (configured via
 * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector} in
 * {@code application-test.yml}), so it must have a public no-arg constructor.
 */
public class ThreadLocalStatementInspector implements StatementInspector {

    private static final ThreadLocal<long[]> COUNT = ThreadLocal.withInitial(() -> new long[1]);

    public ThreadLocalStatementInspector() {
        // Required public no-arg constructor: Hibernate instantiates this inspector by class name.
    }

    @Override
    public String inspect(String sql) {
        COUNT.get()[0]++;
        return sql;
    }

    /** Reset the calling thread's statement counter to zero. */
    public static void reset() {
        COUNT.get()[0] = 0;
    }

    /** Number of statements inspected on the calling thread since the last {@link #reset()}. */
    public static long count() {
        return COUNT.get()[0];
    }
}

package org.dave.observability;

import org.slf4j.MDC;

public final class CorrelationContext {

    public static final String MDC_KEY = "correlationId";

    private CorrelationContext() {
    }

    public static void run(String correlationId, Runnable task) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, correlationId);
        try {
            task.run();
        } finally {
            if (previous != null) {
                MDC.put(MDC_KEY, previous);
            } else {
                MDC.remove(MDC_KEY);
            }
        }
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}

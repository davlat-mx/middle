package org.dave.middle.domain.model;

public class IllegalStateTransitionException extends RuntimeException {

    private final TransferStatus from;
    private final TransferStatus to;

    public IllegalStateTransitionException(TransferStatus from, TransferStatus to) {
        super("Недопустимый переход статуса: " + from + " -> " + to
                + ". Разрешено: " + from.allowedNext());
        this.from = from;
        this.to = to;
    }

    public TransferStatus from() {
        return from;
    }

    public TransferStatus to() {
        return to;
    }
}

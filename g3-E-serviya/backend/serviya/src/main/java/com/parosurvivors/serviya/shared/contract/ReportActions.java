package com.parosurvivors.serviya.shared.contract;

/**
 * Acciones de moderacion que cruzan fronteras de modulo.
 */
public final class ReportActions {

    public static final String WARN = "WARN";
    public static final String BAN = "BAN";
    public static final String REVERT_FEEDBACK = "REVERT_FEEDBACK";
    public static final String MARK_REQUEST_NOT_PROVIDED = "MARK_REQUEST_NOT_PROVIDED";
    public static final String CLOSE = "CLOSE";

    private ReportActions() {
    }
}

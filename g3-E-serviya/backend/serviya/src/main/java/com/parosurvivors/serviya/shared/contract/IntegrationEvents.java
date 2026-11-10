package com.parosurvivors.serviya.shared.contract;

/**
 * Nombres de exchanges y routing keys de integracion entre el monolito y el chat.
 */
public final class IntegrationEvents {

    public static final String REQUESTS_EXCHANGE = "serviya.requests";
    public static final String USERS_EXCHANGE = "serviya.users";
    public static final String CHAT_EXCHANGE = "serviya.chat";

    public static final String REQUEST_CREATED = "requests.service_request.created";
    public static final String REQUEST_STATUS_CHANGED = "requests.service_request.status_changed";

    public static final String USER_REGISTERED = "users.user.registered";
    public static final String USER_UPDATED = "users.user.updated";
    public static final String USER_SUSPENDED = "users.user.suspended";
    public static final String USER_REACTIVATED = "users.user.reactivated";
    public static final String USER_DELETED = "users.user.deleted";

    public static final int EVENT_SCHEMA_VERSION = 1;
    public static final String PRODUCER = "serviya-monolith";

    private IntegrationEvents() {
    }
}

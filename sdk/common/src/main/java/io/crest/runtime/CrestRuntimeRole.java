package io.crest.runtime;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;

import java.util.Locale;

public enum CrestRuntimeRole {
    ALL("all"),
    API("api"),
    SCHEDULER("scheduler"),
    WORKER("worker");

    private final String code;

    CrestRuntimeRole(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public boolean servesApi() {
        return this == ALL || this == API;
    }

    public boolean runsScheduler() {
        return this == ALL || this == SCHEDULER;
    }

    public boolean runsWorker() {
        return this == ALL || this == WORKER;
    }

    public static CrestRuntimeRole from(Environment environment) {
        return fromCode(environment.getProperty("crest.runtime.role",
                environment.getProperty("CREST_RUNTIME_ROLE", ALL.code)));
    }

    public static CrestRuntimeRole fromCode(String value) {
        String normalized = StringUtils.defaultIfBlank(value, ALL.code)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
        for (CrestRuntimeRole role : values()) {
            if (role.code.equals(normalized)) {
                return role;
            }
        }
        throw new IllegalArgumentException("CREST_RUNTIME_ROLE must be one of all, api, scheduler, worker");
    }
}

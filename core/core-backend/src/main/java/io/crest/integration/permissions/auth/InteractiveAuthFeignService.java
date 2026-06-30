package io.crest.integration.permissions.auth;

import io.crest.api.permissions.auth.api.InteractiveAuthApi;
import io.crest.feign.CrestFeign;

@CrestFeign(value = "permissions", path = "/interactive")
public interface InteractiveAuthFeignService extends InteractiveAuthApi {
}

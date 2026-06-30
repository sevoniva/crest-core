package io.crest.integration.permissions.user;

import io.crest.api.permissions.user.api.UserApi;
import io.crest.feign.CrestFeign;

@CrestFeign(value = "permissions", path = "/user")
public interface UserFeignService extends UserApi {
}

package io.crest.integration.permissions.auth;

import io.crest.feign.CrestFeign;
import io.crest.api.permissions.auth.api.AuthApi;


@CrestFeign(value = "permissions", path = "/auth")
public interface PermissionFeignService extends AuthApi {

}

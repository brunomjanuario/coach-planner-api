package com.coachplanner.api.common

import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

/**
 * Resolves the authenticated caller's id from the JWT subject directly into
 * a controller parameter — `fun me(@CurrentUser userId: UUID)` instead of
 * `@AuthenticationPrincipal jwt: Jwt` plus a manual `UUID.fromString(...)`
 * at every call site (the pattern T14-T20 used inline before this existed).
 * No endpoint accepts an owner id from the request itself — this is the
 * only source of truth for "who is calling."
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUser

@Component
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUser::class.java) && parameter.parameterType == UUID::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): UUID {
        val jwt = SecurityContextHolder.getContext().authentication?.principal as? Jwt
            ?: error("@CurrentUser used on an endpoint with no JWT principal — check it's actually behind the resource-server chain")
        return UUID.fromString(jwt.subject)
    }
}

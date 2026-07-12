package com.bingchunmoli.security.jwt;

/**
 * JWT integration mode.
 *
 * @author MoLi
 */
public enum JwtMode {

    /**
     * Token contains only identity ids. Login data and session state are stored server side.
     */
    SIMPLE,

    /**
     * Token contains login data, while session state is still checked server side.
     */
    MIXIN,

    /**
     * Token contains all login data and does not require server side session state.
     */
    STATELESS
}

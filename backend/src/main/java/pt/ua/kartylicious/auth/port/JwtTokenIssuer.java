package pt.ua.kartylicious.auth.port;

import pt.ua.kartylicious.registration.UserAccount;

public interface JwtTokenIssuer {

  IssuedToken issue(UserAccount account);

  record IssuedToken(String token, long expiresInSeconds) {}
}

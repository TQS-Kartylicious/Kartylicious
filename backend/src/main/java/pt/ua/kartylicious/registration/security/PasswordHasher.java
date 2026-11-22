package pt.ua.kartylicious.registration.security;

public interface PasswordHasher {

  String hash(String password);
}

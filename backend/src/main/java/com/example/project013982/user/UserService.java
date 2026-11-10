package com.example.project013982.user;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class UserService {
    private static final int PASSWORD_ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username, String password, String nickname) {
        requireValue(username, "아이디", 50);
        requireValue(password, "비밀번호", 128);
        requireValue(nickname, "닉네임", 50);
        String normalizedUsername = username.strip();
        String normalizedNickname = nickname.strip();
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        String passwordHash = hashPassword(password);
        try {
            return userRepository.save(normalizedUsername, passwordHash, normalizedNickname);
        } catch (DuplicateKeyException exception) {
            // The database UNIQUE constraint also rejects concurrent duplicate signups.
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
    }

    public User authenticate(String username, String password) {
        requireValue(username, "아이디", 50);
        requireValue(password, "비밀번호", 128);
        String normalizedUsername = username.strip();
        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(UserService::invalidLogin);
        if (!matchesPassword(password, user.passwordHash())) {
            throw invalidLogin();
        }
        return user;
    }

    private static void requireValue(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
    }

    private static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PASSWORD_ITERATIONS, 256);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return "pbkdf2_sha256$" + PASSWORD_ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("비밀번호를 안전하게 저장할 수 없습니다.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean matchesPassword(String password, String storedHash) {
        try {
            String[] parts = storedHash.split("\\$", -1);
            if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            if (iterations <= 0) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, expectedHash.length * 8);
            try {
                byte[] actualHash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
                return MessageDigest.isEqual(expectedHash, actualHash);
            } finally {
                spec.clearPassword();
            }
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            return false;
        }
    }

    private static IllegalArgumentException invalidLogin() {
        return new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}

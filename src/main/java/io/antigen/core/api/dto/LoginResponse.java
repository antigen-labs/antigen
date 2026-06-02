package io.antigen.core.api.dto;

import lombok.Data;
import lombok.Getter;

import java.util.UUID;

@Data
public class LoginResponse {

    private String token;
    private String tokenType;
    private UserInfo user;

    @Data
    public static class UserInfo {
        private UUID id;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
    }
}

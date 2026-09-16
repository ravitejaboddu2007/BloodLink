package com.bloodlink.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedUserService {

    public String getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getPrincipal() != null ? auth.getPrincipal().toString() : null;
    }

    public String getAuthenticatedRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", "").toLowerCase())
                .orElse(null);
    }

    public String requireAuthenticatedUserId() {
        String userId = getAuthenticatedUserId();
        if (userId == null || userId.trim().isEmpty()) {
            throw new AccessDeniedException("Forbidden: Caller is not authenticated");
        }
        return userId;
    }

    public void requireCurrentUser(String targetUserId) {
        String authUserId = getAuthenticatedUserId();
        if (authUserId != null && targetUserId != null && !authUserId.equals(targetUserId)) {
            throw new AccessDeniedException("Forbidden: You do not own this resource");
        }
    }

    public boolean isCurrentUser(String targetUserId) {
        String authUserId = getAuthenticatedUserId();
        return authUserId != null && authUserId.equals(targetUserId);
    }
}

package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.AdminDashboardSummaryResponse;
import com.ecotrack.ecotrack_backend.dto.AdminUserResponse;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Admin Portal business logic. Dashboard summary + user management (list/
 * deactivate) live here. Challenge management is a separate, later piece
 * of work per the Milestone 3 task split, not built here yet.
 */
@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AdminDashboardSummaryResponse getDashboardSummary() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByEnabled(true);
        long disabledUsers = userRepository.countByEnabled(false);
        long totalAdmins = userRepository.countByRole_Name("ROLE_ADMIN");
        long newUsersThisWeek = userRepository.countByCreatedAtAfter(LocalDateTime.now().minusDays(7));

        return new AdminDashboardSummaryResponse(totalUsers, activeUsers, disabledUsers, totalAdmins, newUsersThisWeek);
    }

    // Newest-first — matches how "New This Week" is framed on the dashboard,
    // and is generally the most useful default order for an admin skimming
    // the table for recent signups.
    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    /**
     * Soft-deactivates a user by flipping `enabled` to false — the same flag
     * CustomUserDetailsService already reads via UserDetails.disabled(), so a
     * deactivated user is immediately locked out on their next request/login
     * with no separate blacklist mechanism needed.
     *
     * requestingAdminEmail guards against an admin deactivating their own
     * account by mistake mid-session (which would otherwise be an easy way
     * to accidentally lock yourself out with no other admin around).
     */
    public void deactivateUser(Long userId, String requestingAdminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user found with id: " + userId));

        if (user.getEmail().equalsIgnoreCase(requestingAdminEmail)) {
            throw new IllegalStateException("You cannot deactivate your own admin account.");
        }

        if (!user.isEnabled()) {
            return; // already deactivated — idempotent, no error needed
        }

        user.setEnabled(false);
        userRepository.save(user);
    }

    /**
     * Reverses deactivateUser — flips `enabled` back to true so the user
     * can log in again. No self-check needed here (an admin reactivating
     * their own already-enabled account is a no-op, not a footgun), but
     * kept idempotent the same way deactivate is.
     */
    public void reactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user found with id: " + userId));

        if (user.isEnabled()) {
            return; // already active — idempotent, no error needed
        }

        user.setEnabled(true);
        userRepository.save(user);
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().getName(),
                user.isEnabled(),
                user.getCurrentXp(),
                user.getCreatedAt()
        );
    }
}
package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.AdminDashboardSummaryResponse;
import com.ecotrack.ecotrack_backend.dto.AdminPendingDeletionResponse;
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
    private final UserProfileService userProfileService;

    public AdminService(UserRepository userRepository, UserProfileService userProfileService) {
        this.userRepository = userRepository;
        this.userProfileService = userProfileService;
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
     *
     * This is the moderation path — deletionRequestedAt is left untouched
     * (stays NULL for a user who never asked to be deleted), which is what
     * distinguishes an ordinary moderation deactivation from a user's own
     * pending deletion request over in getPendingDeletions().
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
     *
     * Also clears deletionRequestedAt: if a user changed their mind after
     * requesting deletion (e.g. contacted support), reactivating them should
     * pull them fully out of the pending-deletion queue, not leave a stale
     * timestamp that would make them look like they're still awaiting purge.
     */
    public void reactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user found with id: " + userId));

        user.setEnabled(true);
        user.setDeletionRequestedAt(null);
        userRepository.save(user);
    }

    /**
     * Admin "pending account deletions" queue — every user who hit Delete
     * Account on their own profile and is now soft-deleted, waiting for
     * tech support to review and permanently purge.
     */
    public List<AdminPendingDeletionResponse> getPendingDeletions() {
        return userRepository.findByDeletionRequestedAtIsNotNullOrderByDeletionRequestedAtAsc().stream()
                .map(u -> new AdminPendingDeletionResponse(
                        u.getId(),
                        u.getName(),
                        u.getEmail(),
                        u.getDeletionRequestedAt()
                ))
                .toList();
    }

    /**
     * Permanently purges a user who requested deletion — the real hard
     * delete, cascading to every dependent row at the DB level. Only meant
     * to be called against accounts already in the pending-deletion queue,
     * after tech support has reviewed them; nothing here enforces that by
     * itself, so this is a manual, deliberate action on the admin side.
     */
    public void permanentlyDeleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user found with id: " + userId));
        userProfileService.deleteAccountByEmail(user.getEmail());
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
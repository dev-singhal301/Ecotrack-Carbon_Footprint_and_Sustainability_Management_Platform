package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.AdminDashboardSummaryResponse;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Admin Portal business logic. Starts with the dashboard summary only —
 * user management (list/deactivate) and challenge management are separate,
 * later pieces of work per the Milestone 3 task split, not built here yet.
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
}
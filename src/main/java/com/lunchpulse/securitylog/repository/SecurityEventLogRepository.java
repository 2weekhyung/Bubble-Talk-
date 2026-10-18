package com.lunchpulse.securitylog.repository;

import com.lunchpulse.securitylog.entity.SecurityEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SecurityEventLogRepository extends JpaRepository<SecurityEventLog, Long>, JpaSpecificationExecutor<SecurityEventLog> {
}

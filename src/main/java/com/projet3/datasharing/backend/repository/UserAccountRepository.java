package com.projet3.datasharing.backend.repository;

import com.projet3.datasharing.backend.entity.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

  boolean existsByEmail(String email);

  Optional<UserAccount> findByEmail(String email);
}

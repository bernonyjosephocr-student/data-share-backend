package com.projet3.datasharing.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.projet3.datasharing.backend.dto.user.UserAccountDTO;
import com.projet3.datasharing.backend.entity.UserAccount;
import com.projet3.datasharing.backend.exception.EmailAlreadyUsedException;
import com.projet3.datasharing.backend.mapper.user.UserAccountMapper;
import com.projet3.datasharing.backend.repository.UserAccountRepository;
import com.projet3.datasharing.backend.service.user.UserAccountService;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

  @Mock
  private UserAccountRepository userAccountRepository;

  @Mock
  private UserAccountMapper userAccountMapper;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JWTService jwtService;

  @InjectMocks
  private UserAccountService userAccountService;

  @Test
  void registerPersistsAccountWithEncodedPassword() {
    UserAccountDTO userAccountDTO = new UserAccountDTO(null, "user@example.com", "securePassword123", null);
    UserAccount userAccount = new UserAccount();
    when(userAccountRepository.existsByEmail(userAccountDTO.email())).thenReturn(false);
    when(userAccountMapper.toEntity(userAccountDTO)).thenReturn(userAccount);
    when(passwordEncoder.encode(userAccountDTO.password())).thenReturn("encoded-password");

    userAccountService.register(userAccountDTO);

    verify(userAccountRepository).save(userAccount);
    assertEquals("encoded-password", userAccount.getPasswordHash());
  }

  @Test
  void registerRejectsAnEmailAlreadyInUse() {
    UserAccountDTO userAccountDTO = new UserAccountDTO(null, "user@example.com", "securePassword123", null);
    when(userAccountRepository.existsByEmail(userAccountDTO.email())).thenReturn(true);

    assertThrows(EmailAlreadyUsedException.class, () -> userAccountService.register(userAccountDTO));

    verify(userAccountRepository, never()).save(any(UserAccount.class));
    verify(passwordEncoder, never()).encode(any());
  }

  @Test
  void loginReturnsTokenWhenCredentialsAreValid() {
    UserAccountDTO userAccountDTO = new UserAccountDTO(null, "user@example.com", "securePassword123", null);
    UserAccount userAccount = new UserAccount();
    userAccount.setEmail(userAccountDTO.email());
    userAccount.setPasswordHash("encoded-password");
    when(userAccountRepository.findByEmail(userAccountDTO.email())).thenReturn(java.util.Optional.of(userAccount));
    when(passwordEncoder.matches(userAccountDTO.password(), userAccount.getPasswordHash())).thenReturn(true);
    when(jwtService.generateToken(userAccount.getEmail())).thenReturn("signed-jwt");

    String token = userAccountService.login(userAccountDTO);

    assertEquals("signed-jwt", token);
    verify(jwtService).generateToken(userAccount.getEmail());
  }

  @Test
  void loginRejectsUnknownEmailOrInvalidPassword() {
    UserAccountDTO userAccountDTO = new UserAccountDTO(null, "user@example.com", "wrong-password", null);
    when(userAccountRepository.findByEmail(userAccountDTO.email())).thenReturn(java.util.Optional.empty());

    assertThrows(BadCredentialsException.class, () -> userAccountService.login(userAccountDTO));

    verify(jwtService, never()).generateToken(any());
  }
}

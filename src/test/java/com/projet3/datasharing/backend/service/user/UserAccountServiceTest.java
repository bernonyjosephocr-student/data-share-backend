package com.projet3.datasharing.backend.service.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

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
import com.projet3.datasharing.backend.service.JWTService;

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

  // Verifies that a new account is saved with an encoded password.
  @Test
  void shouldRegisterAccountWithEncodedPassword() {
    UserAccountDTO userAccountDTO = new UserAccountDTO(null, "user@example.com", "securePassword123", null);
    UserAccount userAccount = new UserAccount();

    when(userAccountRepository.existsByEmail(userAccountDTO.email())).thenReturn(false);
    when(userAccountMapper.toEntity(userAccountDTO)).thenReturn(userAccount);
    when(passwordEncoder.encode(userAccountDTO.password())).thenReturn("encoded-password");

    userAccountService.register(userAccountDTO);

    verify(userAccountRepository).save(userAccount);
    assertEquals("encoded-password", userAccount.getPasswordHash());
  }

  // Verifies that registration is rejected when the email is already in use.
  @Test
  void shouldRejectRegistrationWithExistingEmail() {
    UserAccountDTO userAccountDTO = new UserAccountDTO(null, "user@example.com", "securePassword123", null);

    when(userAccountRepository.existsByEmail(userAccountDTO.email())).thenReturn(true);

    assertThrows(EmailAlreadyUsedException.class, () -> userAccountService.register(userAccountDTO));

    verify(userAccountRepository, never()).save(any(UserAccount.class));
    verify(passwordEncoder, never()).encode(any());
  }

  // Verifies that a JWT is generated when the login credentials are valid.
  @Test
  void shouldReturnTokenWhenCredentialsAreValid() {
    UserAccountDTO userAccountDTO = new UserAccountDTO(null, "user@example.com", "securePassword123", null);
    UserAccount userAccount = new UserAccount();
    userAccount.setEmail(userAccountDTO.email());
    userAccount.setPasswordHash("encoded-password");

    when(userAccountRepository.findByEmail(userAccountDTO.email())).thenReturn(Optional.of(userAccount));
    when(passwordEncoder.matches(userAccountDTO.password(), userAccount.getPasswordHash())).thenReturn(true);
    when(jwtService.generateToken(userAccount.getEmail())).thenReturn("signed-jwt");

    String token = userAccountService.login(userAccountDTO);

    assertEquals("signed-jwt", token);
    verify(jwtService).generateToken(userAccount.getEmail());
  }

  // Verifies that login is rejected when the email does not exist.
  @Test
  void shouldRejectLoginWithUnknownEmail() {
    UserAccountDTO userAccountDTO = new UserAccountDTO(null, "unknown@example.com", "securePassword123", null);

    when(userAccountRepository.findByEmail(userAccountDTO.email())).thenReturn(Optional.empty());

    assertThrows(BadCredentialsException.class, () -> userAccountService.login(userAccountDTO));

    verify(passwordEncoder, never()).matches(any(), any());
    verify(jwtService, never()).generateToken(any());
  }

  // Verifies that login is rejected when the password is incorrect.
  @Test
  void shouldRejectLoginWithIncorrectPassword() {
    UserAccountDTO userAccountDTO = new UserAccountDTO(null, "user@example.com", "wrong-password", null);
    UserAccount userAccount = new UserAccount();
    userAccount.setEmail(userAccountDTO.email());
    userAccount.setPasswordHash("encoded-password");

    when(userAccountRepository.findByEmail(userAccountDTO.email())).thenReturn(Optional.of(userAccount));
    when(passwordEncoder.matches(userAccountDTO.password(), userAccount.getPasswordHash())).thenReturn(false);

    assertThrows(BadCredentialsException.class, () -> userAccountService.login(userAccountDTO));

    verify(jwtService, never()).generateToken(any());
  }
}
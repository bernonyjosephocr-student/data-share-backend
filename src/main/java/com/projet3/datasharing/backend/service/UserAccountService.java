package com.projet3.datasharing.backend.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projet3.datasharing.backend.dto.UserAccountDTO;
import com.projet3.datasharing.backend.entity.UserAccount;
import com.projet3.datasharing.backend.exception.EmailAlreadyUsedException;
import com.projet3.datasharing.backend.mapper.UserAccountMapper;
import com.projet3.datasharing.backend.repository.UserAccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;

    @Transactional
    public void register(UserAccountDTO userAccountDTO) {
        if (userAccountRepository.existsByEmail(userAccountDTO.email())) {
            throw new EmailAlreadyUsedException(userAccountDTO.email());
        }

        UserAccount userAccount = userAccountMapper.toEntity(userAccountDTO);
        userAccount.setPasswordHash(passwordEncoder.encode(userAccountDTO.password()));
        userAccountRepository.save(userAccount);
    }

    /**
     * Validates user credentials and emit a session token for the user account.
     */
    @Transactional(readOnly = true)
    public String login(UserAccountDTO userAccountDTO) {
        UserAccount userAccount = userAccountRepository.findByEmail(userAccountDTO.email())
                .filter(account -> passwordEncoder.matches(userAccountDTO.password(), account.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        return jwtService.generateToken(userAccount.getEmail());
    }
}

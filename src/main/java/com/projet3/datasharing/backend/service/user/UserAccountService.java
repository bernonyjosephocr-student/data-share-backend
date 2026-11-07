package com.projet3.datasharing.backend.service.user;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projet3.datasharing.backend.dto.user.UserAccountDTO;
import com.projet3.datasharing.backend.entity.UserAccount;
import com.projet3.datasharing.backend.exception.EmailAlreadyUsedException;
import com.projet3.datasharing.backend.mapper.user.UserAccountMapper;
import com.projet3.datasharing.backend.repository.UserAccountRepository;
import com.projet3.datasharing.backend.service.JWTService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;

    /**
     * Register a user account
     * 
     * @param userAccountDTO
     */
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
     * 
     * @param userAccountDTO
     * @return
     */
    @Transactional(readOnly = true)
    public String login(UserAccountDTO userAccountDTO) {
        UserAccount userAccount = userAccountRepository.findByEmail(userAccountDTO.email())
                .filter(account -> passwordEncoder.matches(userAccountDTO.password(), account.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Email ou password incorrect!"));

        return jwtService.generateToken(userAccount.getEmail());
    }
}

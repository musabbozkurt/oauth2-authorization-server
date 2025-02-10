package mb.oauth2authorizationserver.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.repository.AuthorityRepository;
import mb.oauth2authorizationserver.data.repository.UserRepository;
import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import mb.oauth2authorizationserver.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Page<SecurityUser> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    public SecurityUser createUser(SecurityUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setAuthorities(authorityRepository.findAllByDefaultAuthorityIsTrue());
        return userRepository.save(user);
    }

    @Override
    public SecurityUser getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(OAuth2AuthorizationServerServiceErrorCode.USER_NOT_FOUND, Set.of(userId)));
    }

    @Override
    public SecurityUser updateUserById(SecurityUser updatedUser) {
        return userRepository.save(updatedUser);
    }

    @Override
    public void deleteUserById(Long userId) {
        SecurityUser user = getUserById(userId);
        userRepository.deleteById(user.getId());
    }
}

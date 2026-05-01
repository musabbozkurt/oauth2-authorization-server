package mb.oauth2authorizationserver.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.repository.AuthorityRepository;
import mb.oauth2authorizationserver.data.repository.UserRepository;
import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import mb.oauth2authorizationserver.service.UserService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Page<SecurityUser> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    public Page<SecurityUser> findAllByEnabledTrue(Pageable pageable) {
        return userRepository.findByEnabledTrue(pageable);
    }

    @Override
    public Page<SecurityUser> findByNameAndLastName(String firstName, String lastName, Pageable pageable) {
        return userRepository.findByFirstNameContainingIgnoreCaseAndLastNameContainingIgnoreCase(firstName, lastName, pageable);
    }

    @Override
    public Page<SecurityUser> findByNameAndLastNameAndEnabledTrue(String firstName, String lastName, Pageable pageable) {
        return userRepository.findByFirstNameContainingIgnoreCaseAndLastNameContainingIgnoreCaseAndEnabledTrue(firstName, lastName, pageable);
    }

    @Override
    public SecurityUser findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BaseException(OAuth2AuthorizationServerServiceErrorCode.USER_NOT_FOUND, Set.of(id)));
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    @Override
    @Transactional
    public SecurityUser save(SecurityUser user) {
        if (StringUtils.isNotBlank(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        if (CollectionUtils.isEmpty(user.getAuthorities())) {
            user.setAuthorities(authorityRepository.findAllByDefaultAuthorityIsTrue());
        }
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void update(SecurityUser oldUser, SecurityUser newUser) {
        userRepository.save(newUser);
    }

    @Override
    @Transactional
    public void delete(SecurityUser user) {
        userRepository.delete(user);
    }

    // Legacy methods for compatibility
    @Override
    public SecurityUser createUser(SecurityUser user) {
        return userRepository.save(user);
    }

    @Override
    public SecurityUser getUserById(Long userId) {
        return findById(userId);
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

package mb.oauth2authorizationserver.config.security.service.impl;

import lombok.RequiredArgsConstructor;
import mb.oauth2authorizationserver.config.security.service.UserLoginAttemptService;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.entity.UserLoginAttempt;
import mb.oauth2authorizationserver.data.repository.UserLoginAttemptRepository;
import mb.oauth2authorizationserver.model.enums.LoginStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserLoginAttemptServiceImpl implements UserLoginAttemptService {

    private final UserLoginAttemptRepository userLoginAttemptRepository;

    @Override
    public void addToUserLoginAttempt(SecurityUser user, LoginStatus loginStatus) {
        UserLoginAttempt userLoginAttempt = new UserLoginAttempt();
        userLoginAttempt.setUserId(user.getId());
        userLoginAttempt.setLoginStatus(loginStatus);
        userLoginAttempt.setLoginDate(LocalDateTime.now());
        userLoginAttemptRepository.save(userLoginAttempt);
    }

    @Override
    public Page<UserLoginAttempt> findAllOrderByLoginDateDesc(Pageable pageable) {
        return userLoginAttemptRepository.findAllByOrderByLoginDateDesc(pageable);
    }

    @Override
    public Page<UserLoginAttempt> searchByUserId(Long userId, Pageable pageable) {
        return userLoginAttemptRepository.findByUserId(userId, pageable);
    }
}

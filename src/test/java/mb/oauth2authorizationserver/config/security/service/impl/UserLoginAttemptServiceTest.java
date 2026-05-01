package mb.oauth2authorizationserver.config.security.service.impl;

import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.entity.UserLoginAttempt;
import mb.oauth2authorizationserver.data.repository.UserLoginAttemptRepository;
import mb.oauth2authorizationserver.model.enums.LoginStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLoginAttemptServiceTest {

    @InjectMocks
    private UserLoginAttemptServiceImpl userLoginAttemptService;

    @Mock
    private UserLoginAttemptRepository userLoginAttemptRepository;

    private SecurityUser user;
    private LoginStatus loginStatus;

    @BeforeEach
    void setUp() {
        userLoginAttemptService = new UserLoginAttemptServiceImpl(userLoginAttemptRepository);

        user = new SecurityUser();
        user.setId(1L);

        loginStatus = LoginStatus.SUCCESS;
    }

    @Test
    void addToUserLoginAttempt_ShouldSaveLoginAttempt_WhenPropertiesAreSet() {
        userLoginAttemptService.addToUserLoginAttempt(user, loginStatus);

        verify(userLoginAttemptRepository, times(1)).save(any(UserLoginAttempt.class));
    }

    @Test
    void addToUserLoginAttempt_ShouldSetCorrectLoginAttemptProperties_WhenPropertiesAreSet() {
        UserLoginAttempt userLoginAttempt = new UserLoginAttempt();
        userLoginAttempt.setUserId(user.getId());
        userLoginAttempt.setLoginStatus(loginStatus);
        userLoginAttempt.setLoginDate(LocalDateTime.now());

        userLoginAttemptService.addToUserLoginAttempt(user, loginStatus);

        verify(userLoginAttemptRepository, times(1)).save(any(UserLoginAttempt.class));
    }

    @Test
    void findAllOrderByLoginDateDesc_ShouldReturnAllAttempts_WhenAttemptsExist() {
        Page<UserLoginAttempt> page = new PageImpl<>(List.of(new UserLoginAttempt()), PageRequest.of(0, 20), 1);
        when(userLoginAttemptRepository.findAllByOrderByLoginDateDesc(any(Pageable.class))).thenReturn(page);

        Page<UserLoginAttempt> result = userLoginAttemptService.findAllOrderByLoginDateDesc(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        verify(userLoginAttemptRepository).findAllByOrderByLoginDateDesc(any(Pageable.class));
    }

    @Test
    void searchByUserId_ShouldReturnFilteredAttempts_WhenUserIdProvided() {
        Page<UserLoginAttempt> page = new PageImpl<>(List.of(new UserLoginAttempt()), PageRequest.of(0, 20), 1);
        when(userLoginAttemptRepository.findByUserId(eq(1L), any(Pageable.class))).thenReturn(page);

        Page<UserLoginAttempt> result = userLoginAttemptService.searchByUserId(1L, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        verify(userLoginAttemptRepository).findByUserId(eq(1L), any(Pageable.class));
    }
}

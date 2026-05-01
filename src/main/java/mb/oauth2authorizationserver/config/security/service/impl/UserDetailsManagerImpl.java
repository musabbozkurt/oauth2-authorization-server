package mb.oauth2authorizationserver.config.security.service.impl;

import lombok.RequiredArgsConstructor;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsManagerImpl implements UserDetailsManager {

    private final UserRepository userRepository;

    @Override
    public SecurityUser loadUserByUsername(String username) throws UsernameNotFoundException {
        SecurityUser user = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        if (!user.getUsername().equals(username)) {
            throw new UsernameNotFoundException("Access Denied");
        }
        return user;
    }

    @Override
    public void createUser(UserDetails user) {
        // default implementation ignored
    }

    @Override
    public void updateUser(UserDetails user) {
        // default implementation ignored
    }

    @Override
    public void deleteUser(String username) {
        // default implementation ignored
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        // default implementation ignored
    }

    @Override
    public boolean userExists(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username)).getUsername().equals(username);
    }
}

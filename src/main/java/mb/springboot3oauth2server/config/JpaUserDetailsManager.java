package mb.springboot3oauth2server.config;

import java.util.Collection;
import java.util.HashSet;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mb.springboot3oauth2server.data.entity.SecurityUser;
import mb.springboot3oauth2server.data.repository.UserRepository;

@RequiredArgsConstructor
@Service
public class JpaUserDetailsManager implements UserDetailsManager {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SecurityUser user = userRepository.findByUsername(username);
        if (!user.getUsername().equals(username)) {
            throw new UsernameNotFoundException("Access Denied");
        }
        Collection<GrantedAuthority> authoriies = new HashSet<>();
        user.getAuthorities().forEach(auth -> authoriies.add(new SimpleGrantedAuthority(auth.getAuthority())));
        return new User(user.getUsername(), user.getPassword(), user.getEnabled(), user.getAccountNonExpired(),
                user.getCredentialsNonExpired(), user.getAccountNonLocked(), authoriies);
    }

    @Override
    public void createUser(UserDetails user) {
    }

    @Override
    public void updateUser(UserDetails user) {
    }

    @Override
    public void deleteUser(String username) {
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
    }

    @Override
    public boolean userExists(String username) {
        SecurityUser user = userRepository.findByUsername(username);
        if (user.getUsername().equals(username)) {
            return true;
        }
        return false;
    }

}

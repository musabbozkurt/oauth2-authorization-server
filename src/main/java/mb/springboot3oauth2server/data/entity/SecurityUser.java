package mb.springboot3oauth2server.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Set;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class SecurityUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NonNull
    @Column(unique = true)
    private String username;

    @NonNull
    private String password;

    @ManyToMany(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    @JoinTable(name = "users_authorities", joinColumns = {
            @JoinColumn(name = "USERS_ID", referencedColumnName = "ID")}, inverseJoinColumns = {
            @JoinColumn(name = "AUTHORITIES_ID", referencedColumnName = "ID")})
    private Set<Authority> authorities;

    private Boolean accountNonExpired;
    private Boolean accountNonLocked;
    private Boolean credentialsNonExpired;
    private Boolean enabled;

}

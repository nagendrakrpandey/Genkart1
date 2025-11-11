package Tech_Nagendra.Certificates_genration.Security;

import Tech_Nagendra.Certificates_genration.Entity.UserProfile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class UserPrincipal implements UserDetails {

    private Long id;
    private String username;
    private String password;
    private String role;
    private Collection<? extends GrantedAuthority> authorities;
    private UserProfile userProfile;

    public UserPrincipal(Long id,
                         String username,
                         String password,
                         String role,
                         Collection<? extends GrantedAuthority> authorities,
                         UserProfile userProfile) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.authorities = authorities;
        this.userProfile = userProfile;
    }

    public UserPrincipal(Long id,
                         String username,
                         String password,
                         String role,
                         Collection<? extends GrantedAuthority> authorities) {
        this(id, username, password, role, authorities, null);
    }

    public UserPrincipal(UserProfile userProfile) {
        this.id = userProfile.getId();
        this.username = userProfile.getUsername();
        this.password = userProfile.getPassword();
        this.role = userProfile.getRole();
        this.userProfile = userProfile;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority(role));
    }


    public UserProfile getUserProfile() {
        return userProfile;
    }

    public void setUserProfile(UserProfile userProfile) {
        this.userProfile = userProfile;
    }

    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

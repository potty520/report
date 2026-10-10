package com.reportsys.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * JWT 用户详情
 */
public class JwtUserDetails implements UserDetails {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private List<GrantedAuthority> authorities;

    public JwtUserDetails(Long userId, String username) {
        this.userId = userId;
        this.username = username;
        this.authorities = new ArrayList<>();
    }

    public JwtUserDetails(Long userId, String username, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.authorities = new ArrayList<>();
        if (permissions != null) {
            for (String perm : permissions) {
                authorities.add(new SimpleGrantedAuthority(perm));
            }
        }
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
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

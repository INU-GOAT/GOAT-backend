package com.capstone.goat.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "users")
public class User implements UserDetails {
    @Id
    private Long id;

    @Column
    private String nickname;

    @Column
    private Integer age;

    @Column
    private String gender;

    private String prefer_sport;

    private Integer soccer_tier;
    private Integer badminton_tier;
    private Integer basketball_tier;
    private Integer tableTennis_tier;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Builder
    public User(String nickname,Long id, int age,String gender,String prefer_sport, int soccer_tier,int badminton_tier, int basketball_tier,int tableTennis_tier, List<String> roles){
        this.nickname = nickname;
        this.gender = gender;
        this.id = id;
        this.age = age;
        this.prefer_sport = prefer_sport;
        this.soccer_tier = soccer_tier;
        this.tableTennis_tier = tableTennis_tier;
        this.badminton_tier = badminton_tier;
        this.basketball_tier = basketball_tier;
        this.roles = roles;
    }

    public void join(int age, String gender, String prefer_sport, int soccer_tier,int badminton_tier, int basketball_tier,int tableTennis_tier){
        this.age = age;
        this.gender = gender;
        this.prefer_sport = prefer_sport;
        this.soccer_tier = soccer_tier;
        this.badminton_tier = badminton_tier;
        this.basketball_tier = basketball_tier;
        this.tableTennis_tier = tableTennis_tier;
    }

    public void update(String nickname, int age, String gender, String prefer_sport, int soccer_tier,int badminton_tier, int basketball_tier,int tableTennis_tier){
        this.age = age;
        this.nickname = nickname;
        this.gender = gender;
        this.prefer_sport = prefer_sport;
        this.soccer_tier = soccer_tier;
        this.badminton_tier = badminton_tier;
        this.basketball_tier = basketball_tier;
        this.tableTennis_tier = tableTennis_tier;
    }

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> roles;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return this.id.toString();
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

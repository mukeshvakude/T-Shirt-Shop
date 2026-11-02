package com.shop.mapper;

import java.util.Set;
import java.util.TreeSet;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.shop.dto.auth.UserResponse;
import com.shop.entity.Role;
import com.shop.entity.User;

@Mapper
public interface UserMapper {

    @Mapping(target = "roles", expression = "java(roleNames(user.getRoles()))")
    UserResponse toResponse(User user);

    default Set<String> roleNames(Set<Role> roles) {
        Set<String> names = new TreeSet<>();
        roles.forEach(r -> names.add(r.getName()));
        return names;
    }
}

package com.example.demo.mapper;

import com.example.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper  // 告诉 MyBatis 这是一个 Mapper 接口
public interface UserMapper {

    int deleteUser(int id);

    List<User> getAllUsers();

    User getUserById(int id);

    int addUser(User user);

    int updateUser(User user);

    User getByUsername(String username);

    List<User> getUsersByPage(@Param("offset") int offset,
                              @Param("size") int size,
                              @Param("username") String username);

    int countUsers(@Param("username") String username);
}

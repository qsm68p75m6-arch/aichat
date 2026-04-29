package com.example.demo.service;
import com.example.demo.entity.User;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface UserService {
    boolean deleteUser(int id);

    List<User> getAllUsers();

    User getUserById(int id);

    boolean addUser(User user);

    boolean updateUser(User user);

    User login(String username, String password);

    Map<String, Object> getUsersByPage(int page,int size,String username);

    Object getUserByPage(int page, int size, String username);
}


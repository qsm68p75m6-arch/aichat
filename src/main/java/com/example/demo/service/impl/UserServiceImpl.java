//业务层
//负责实现业务
package com.example.demo.service.impl;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.demo.dto.UserDTO;
import com.example.demo.common.BusinessException;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    // 注入密码编码器（全局可用）
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(int id) {
        return userMapper.deleteUser(id) > 0;
    }

    @Override
    public User getUserById(int id) {
        return userMapper.getUserById(id);
    }

    @Override
    public List<User> getAllUsers() {
            return userMapper.getAllUsers();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addUser(User user) {
        // 加密密码后再存储
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
        return userMapper.addUser(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(User user) {
        // 如果密码被修改，需要重新加密
        if (user.getPassword() != null && !user.getPassword().startsWith("$2a$")) {
            String encodedPassword = passwordEncoder.encode(user.getPassword());
            user.setPassword(encodedPassword);
        }
        return userMapper.updateUser(user) > 0;
    }

    @Override
    public User login(String username, String password) {
        // 根据用户名查询用户
        User loginUser = userMapper.getByUsername(username);

        // 用户不存在
        if (loginUser == null) {
            return null;
        }

        // 使用注入的 passwordEncoder 验证密码
        if (!passwordEncoder.matches(password, loginUser.getPassword())) {
            return null; // 密码错误
        }

        return loginUser; // 登录成功
    }
    @Override
    public Map<String, Object> getUsersByPage(int page, int size, String username) {
        // 参数校验（不合法直接报错）
        if (page < 1) throw new BusinessException("页码必须大于等于1");
        if (size < 1) throw new BusinessException("每页数量必须大于等于1");
        if (size > 100) throw new BusinessException("每页数量不能超过100");

        int offset = (page - 1) * size;


        List<User> list = userMapper.getUsersByPage(offset, size, username);
        int total = userMapper.countUsers(username);

        List<UserDTO> dtoList = list.stream().map(u -> {
            UserDTO dto = new UserDTO();
            BeanUtils.copyProperties(u, dto);
            return dto;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("list", dtoList);

        return result;
    }

    @Override
    public Object getUserByPage(int page, int size, String username) {
        return getUsersByPage(page, size, username);
    }

}

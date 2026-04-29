//这是控制层
//负责接受请求，
package com.example.demo.controller;
import com.example.demo.common.BusinessException;
import com.example.demo.common.JwtUtil;
import com.example.demo.dto.PageRequest;
import com.example.demo.dto.UserDTO;
import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import com.example.demo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.beans.BeanUtils;

@Validated
@RestController

/**
 * 用户管理控制器
 * 提供用户的增删改查、登录认证等功能
 */
@Tag(name="用户管理",description = "用户注册，登录，信息管理接口")
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private  JwtUtil jwtUtil;
    @Operation(summary = "获取用户列表")
    //查询全部
    @GetMapping
    public Result getAllUsers() {
        List<User> list = userService.getAllUsers();

        List<UserDTO> dtoList = list.stream().map(user -> {
            UserDTO dto = new UserDTO();
            BeanUtils.copyProperties(user, dto);
            return dto;
        }).toList();

        return Result.ok(dtoList);
    }

    //查询单个
    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Result getUser(@PathVariable int id) {
        User user = userService.getUserById(id);
        if(user==null){
            throw new BusinessException("用户不存在");
        }

        UserDTO dto=new UserDTO();
        BeanUtils.copyProperties(user, dto);

        return  Result.ok(dto);
    }

    //新增
    @Operation(summary = "用户注册")
    @PostMapping
    public Result addUser(@RequestBody User user) {
        return userService.addUser(user) ? Result.ok(null) : Result.fail("新增失败");
    }

    //修改
    @Operation(summary = "修改用户信息")
    @PutMapping("/{id}")
    public Result updateUser(@RequestBody User user, @PathVariable int id) {
        user.setId(id);
        return userService.updateUser(user) ? Result.ok(null) : Result.fail("修改失败");
    }
    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result deleteUser(@PathVariable int id) {
        return userService.deleteUser(id) ? Result.ok(null) : Result.fail("用户不存在");
    }
    @Operation(summary = "用户登录",
        description = "传入用户密码，验证后返回JWT Token")
    @PostMapping("/login")
    public Result getByusername(@RequestBody User loginUser) {
        User user = userService.login(loginUser.getUsername(), loginUser.getPassword());
        if (user != null) {
            String token=jwtUtil.generateToken(user.getId(),user.getUsername());
            return Result.ok(token);
        } else {
            throw  new BusinessException("用户名或密码错误");
        }
    }
    @Operation(summary ="分页查询用户" )
    @GetMapping("/page")
    public Result getUsersByPage(@Valid PageRequest pageRequest) {
        // 第三层防御：Controller 层兜底校验
        if (pageRequest.getPage() == null || pageRequest.getPage() < 1) {
            throw new BusinessException("页码必须大于等于1");
        }
        if (pageRequest.getSize() == null || pageRequest.getSize() < 1) {
            throw new BusinessException("每页数量必须大于等于1");
        }
        if (pageRequest.getSize() > 100) {
            throw new BusinessException("每页数量不能超过100");
        }

        return Result.ok(userService.getUsersByPage(
                pageRequest.getPage(),
                pageRequest.getSize(),
                pageRequest.getUsername()
        ));
    }


}














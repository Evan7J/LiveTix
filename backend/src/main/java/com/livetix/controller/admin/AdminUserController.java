package com.livetix.controller.admin;

import com.livetix.common.Result;
import com.livetix.entity.User;
import com.livetix.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Admin: User management
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return userService.listUsers(page, pageSize, keyword);
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        user.setPassword(null);
        return Result.ok(user);
    }

    @PutMapping("/{id}/status")
    public Result<?> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        user.setStatus(status);
        userService.updateById(user);
        return Result.ok("更新成功");
    }
}

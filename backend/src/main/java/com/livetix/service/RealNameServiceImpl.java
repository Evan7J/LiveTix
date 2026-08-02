package com.livetix.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livetix.common.Result;
import com.livetix.common.util.AesUtil;
import com.livetix.entity.RealNameInfo;
import com.livetix.mapper.RealNameInfoMapper;
import org.springframework.stereotype.Service;

/**
 * 实名信息服务
 *
 * 安全加固：证件号码 AES 加密存储
 * 存储流程：用户输入明文 → AES-256-CBC 加密 → Base64 编码 → 存入数据库
 * 读取流程：数据库密文 → Base64 解码 → AES-256-CBC 解密 → 脱敏显示
 */
@Service
public class RealNameServiceImpl extends ServiceImpl<RealNameInfoMapper, RealNameInfo> implements RealNameService {

    private static final int MAX_COUNT = 6;

    @Override
    public Result<?> listMyRealNames() {
        long userId = StpUtil.getLoginIdAsLong();
        var list = this.list(new LambdaQueryWrapper<RealNameInfo>()
                .eq(RealNameInfo::getUserId, userId)
                .orderByDesc(RealNameInfo::getIsDefault)
                .orderByDesc(RealNameInfo::getCreateTime));

        list.forEach(info -> {
            try {
                String decrypted = AesUtil.decrypt(info.getIdCardNumber());
                info.setIdCardNumber(maskIdCard(decrypted));
            } catch (Exception e) {
                info.setIdCardNumber("****");
            }
        });

        return Result.ok(list);
    }

    @Override
    public Result<?> addRealName(RealNameInfo info) {
        long userId = StpUtil.getLoginIdAsLong();

        long count = this.count(new LambdaQueryWrapper<RealNameInfo>()
                .eq(RealNameInfo::getUserId, userId));
        if (count >= MAX_COUNT) {
            return Result.fail("最多添加" + MAX_COUNT + "位观演人");
        }

        info.setUserId(userId);

        if (info.getIsDefault() != null && info.getIsDefault() == 1) {
            clearOtherDefaults(userId);
        }

        info.setIdCardNumber(AesUtil.encrypt(info.getIdCardNumber()));

        this.save(info);

        try {
            String decrypted = AesUtil.decrypt(info.getIdCardNumber());
            info.setIdCardNumber(maskIdCard(decrypted));
        } catch (Exception e) {
            info.setIdCardNumber("****");
        }

        return Result.ok("添加成功", info);
    }

    @Override
    public Result<?> updateRealName(Long id, RealNameInfo info) {
        long userId = StpUtil.getLoginIdAsLong();
        RealNameInfo existing = this.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.fail("实名信息不存在");
        }

        info.setId(id);
        info.setUserId(userId);

        if (info.getIsDefault() != null && info.getIsDefault() == 1) {
            clearOtherDefaults(userId);
        }

        if (info.getIdCardNumber() != null && !info.getIdCardNumber().contains("*")) {
            info.setIdCardNumber(AesUtil.encrypt(info.getIdCardNumber()));
        } else if (info.getIdCardNumber() != null && info.getIdCardNumber().contains("*")) {
            info.setIdCardNumber(existing.getIdCardNumber());
        }

        this.updateById(info);

        return Result.ok("更新成功");
    }

    @Override
    public Result<?> deleteRealName(Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        RealNameInfo existing = this.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.fail("实名信息不存在");
        }

        this.removeById(id);
        return Result.ok("删除成功");
    }

    private void clearOtherDefaults(Long userId) {
        var update = new LambdaUpdateWrapper<RealNameInfo>()
                .set(RealNameInfo::getIsDefault, 0)
                .eq(RealNameInfo::getUserId, userId)
                .eq(RealNameInfo::getIsDefault, 1);
        this.update(update);
    }

    private String maskIdCard(String idNumber) {
        if (idNumber == null || idNumber.isEmpty()) {
            return idNumber;
        }
        int length = idNumber.length();
        if (length <= 6) {
            return length <= 2 ? "**" : idNumber.charAt(0) + "****" + idNumber.charAt(length - 1);
        }
        String prefix = idNumber.substring(0, Math.min(6, length));
        String suffix = idNumber.substring(Math.max(6, length - 4));
        return prefix + "********" + suffix;
    }
}
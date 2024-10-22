package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWoker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWoker redisIdWoker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        if(voucher == null){
            return Result.fail("优惠券编号有误！");
        }

        // 2. 秒杀未开始或已经结束，返回异常结果
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("活动还未开始！");
        }

        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("活动已经结束！");
        }

        // 3、 库存不充足，返回异常结果
        if(voucher.getStock() == 0){
            return Result.fail("秒杀券已经售光！");
        }

        // 4. 一人一单的实现
        // select count(*) from tb_voucher_order where voucher_id = voucherId and user_id = userId
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        Integer count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        if(count > 0){
            return Result.fail("已经购买过！");
        }

        // 5. 扣减库存
        // update tb_seckill_voucher set stock = voucher.getStock() -1 where voucher_id = voucherId and stock > 0
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // 设置新的库存值
                .eq("voucher_id", voucherId) // 根据voucherId来更新记录
                .gt("stock", 0)
                .update();

        if (!success) {
            return Result.fail("库存扣减失败！");
        }

        // 6. 创建订单
        VoucherOrder order = new VoucherOrder();

        long seckillVoucher_id = redisIdWoker.nextId("SeckillVoucher");
        order.setId(seckillVoucher_id);

        order.setUserId(userId);
        order.setVoucherId(voucherId);

        save(order); // 储存到数据库中

        // 7. 返回订单id
        return Result.ok(seckillVoucher_id);
    }
}

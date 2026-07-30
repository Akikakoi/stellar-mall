package com.stellar.controller.user;

import com.stellar.context.BaseContext;
import com.stellar.dto.WalletRechargeDTO;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.WalletService;
import com.stellar.vo.WalletVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * C 端钱包接口。
 */
@RestController
@RequestMapping("/user/wallet")
@RequiredArgsConstructor
@Api(tags = "C端：钱包��理")
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    @ApiOperation("获取钱包余额")
    public Result<WalletVO> getWallet() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(walletService.getOrCreateWallet(userId));
    }

    @PostMapping("/recharge")
    @ApiOperation("模拟充值")
    public Result<WalletVO> recharge(@RequestBody WalletRechargeDTO dto) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(walletService.recharge(userId, dto));
    }

    @GetMapping("/transactions")
    @ApiOperation("交易流水")
    public Result<PageResult> transactions(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(walletService.pageTransactions(userId, page, pageSize));
    }
}

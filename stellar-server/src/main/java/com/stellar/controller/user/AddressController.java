package com.stellar.controller.user;

import com.stellar.context.BaseContext;
import com.stellar.dto.AddressSaveDTO;
import com.stellar.dto.AddressUpdateDTO;
import com.stellar.result.Result;
import com.stellar.service.AddressService;
import com.stellar.vo.AddressVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * C 端收货地址接口。
 * <p>
 * 路径保持 {@code /user/address/**} 不变，前端无需调整调用。
 */
@RestController
@RequestMapping("/user/address")
@RequiredArgsConstructor
@Api(tags = "C端：收货地址")
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    @ApiOperation("新增地址")
    public Result<Long> save(@RequestBody @Valid AddressSaveDTO dto) {
        Long id = addressService.save(dto);
        return Result.success(id);
    }

    @PutMapping
    @ApiOperation("更新地址")
    public Result<String> update(@RequestBody @Valid AddressUpdateDTO dto) {
        addressService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除地址")
    public Result<String> delete(@PathVariable Long id) {
        addressService.deleteById(id, BaseContext.getCurrentId());
        return Result.success();
    }

    @GetMapping("/list")
    @ApiOperation("地址列表")
    public Result<List<AddressVO>> list() {
        return Result.success(addressService.listByUser(BaseContext.getCurrentId()));
    }

    @GetMapping("/{id}")
    @ApiOperation("地址详情")
    public Result<AddressVO> detail(@PathVariable Long id) {
        return Result.success(addressService.getById(id, BaseContext.getCurrentId()));
    }

    @PostMapping("/{id}/default")
    @ApiOperation("设为默认地址")
    public Result<String> setDefault(@PathVariable Long id) {
        addressService.setDefault(id, BaseContext.getCurrentId());
        return Result.success();
    }
}
